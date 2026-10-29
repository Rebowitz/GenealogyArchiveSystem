package org.genealogyarchive.jobs;

import org.genealogyarchive.data.BackgroundJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executes background jobs for one worker process.
 * <ul>
 *   <li>Claims use FOR UPDATE SKIP LOCKED, so two workers never claim the same job at once.</li>
 *   <li>Each claim carries a fresh lease token. While the handler runs, a separate renewal thread
 *       extends the lease every {@code renewInterval}, so a healthy worker keeps ownership of a
 *       long-running job and competing workers cannot reclaim it (GAS-P1-001).</li>
 *   <li>If the worker dies, renewals stop, the lease expires and another worker reclaims the job
 *       (OP-001). A genuine crash may therefore replay a job; handlers should be idempotent.</li>
 *   <li>Renewal and completion are accepted only for the current token and an unexpired lease. Once
 *       an attempt has lost its lease it never regains it and cannot record success or failure.</li>
 *   <li>Failures are kept with their error for inspection and retry (OP-002).</li>
 * </ul>
 */
public final class JobRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    static final int MAX_ERROR_LENGTH = 1000;

    private final BackgroundJobRepository jobs;
    private final Clock clock;
    private final UUID workerId;
    private final Duration lease;
    private final Duration renewInterval;
    private final Map<String, JobHandler> handlers;
    private final ScheduledExecutorService renewals;

    /** Production form: the lease is renewed every third of its duration. */
    public JobRunner(BackgroundJobRepository jobs, Clock clock, UUID workerId, Duration lease, List<JobHandler> handlers) {
        this(jobs, clock, workerId, lease, lease.dividedBy(3), handlers);
    }

    public JobRunner(BackgroundJobRepository jobs, Clock clock, UUID workerId, Duration lease, Duration renewInterval,
                     List<JobHandler> handlers) {
        if (lease.isNegative() || lease.isZero() || renewInterval.isNegative() || renewInterval.isZero()) {
            throw new IllegalArgumentException("Job lease and renewal interval must be positive");
        }
        this.jobs = jobs;
        this.clock = clock;
        this.workerId = workerId;
        this.lease = lease;
        this.renewInterval = renewInterval;
        Map<String, JobHandler> byKind = new LinkedHashMap<>();
        for (JobHandler h : handlers) {
            if (byKind.put(h.kind(), h) != null) {
                throw new IllegalStateException("Two handlers registered for job kind " + h.kind());
            }
        }
        this.handlers = Map.copyOf(byKind);
        this.renewals = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gas-job-lease-renewal");
            t.setDaemon(true);
            return t;
        });
    }

    public UUID workerId() {
        return workerId;
    }

    /**
     * Claims and runs at most one job.
     *
     * @return true if a job was claimed (whether its handler ran, succeeded, failed, or was not
     *         started because the lease had already ended before delivery)
     */
    public boolean runOne() {
        Instant now = clock.instant();
        int expired = jobs.failExpiredWithoutAttemptsLeft(now);
        if (expired > 0) {
            log.warn("{} job(s) failed because their worker stopped or lost its lease and no attempts remain", expired);
        }
        Instant requestedAt = clock.instant();
        Optional<Job> claimed = jobs.claim(handlers.keySet(), workerId, requestedAt, lease);
        if (claimed.isEmpty()) {
            return false;
        }
        Job job = claimed.get();
        JobExecution execution = new JobExecution(job, clock, requestedAt.plus(lease));
        // Safeguard (GAS-P1-R01): if the claim result reached this point only after the lease could
        // already have expired (e.g. a long pause), another worker may own the job: never start it.
        if (!execution.leaseHeld()) {
            log.warn("Job {} ({}) attempt {} was delivered after its lease ended; the handler was not started",
                    job.jobId(), job.kind(), job.attempts());
            return true;
        }
        ScheduledFuture<?> renewal = renewals.scheduleWithFixedDelay(() -> renew(execution),
                renewInterval.toMillis(), renewInterval.toMillis(), TimeUnit.MILLISECONDS);
        Exception failure = null;
        try {
            handlers.get(job.kind()).run(execution);
        } catch (Exception e) {
            failure = e;
        } finally {
            renewal.cancel(false);
        }
        complete(execution, failure);
        return true;
    }

    private void renew(JobExecution execution) {
        if (!execution.leaseHeld()) {
            return;
        }
        Job job = execution.job();
        Instant requestedAt = clock.instant();
        try {
            if (jobs.renew(job.jobId(), job.leaseToken(), lease, requestedAt)) {
                execution.renewed(requestedAt.plus(lease));
            } else {
                execution.markLost();
                log.warn("Job {} lease could not be renewed: ownership has passed to another attempt", job.jobId());
            }
        } catch (RuntimeException e) {
            // The database may be briefly unreachable; ownership lapses locally when the known lease ends.
            log.warn("Job {} lease renewal failed: {}", job.jobId(), e.getMessage());
        }
    }

    private void complete(JobExecution execution, Exception failure) {
        Job job = execution.job();
        if (!execution.leaseHeld()) {
            log.warn("Job {} ({}) attempt {} lost its lease before finishing; it records no outcome",
                    job.jobId(), job.kind(), job.attempts());
            return;
        }
        Instant now = clock.instant();
        if (failure == null) {
            if (!jobs.succeed(job.jobId(), job.leaseToken(), now)) {
                log.warn("Job {} finished after its lease was lost; it records no outcome", job.jobId());
            }
            return;
        }
        String error = describe(failure);
        if (!jobs.fail(job.jobId(), job.leaseToken(), error, now, now.plus(backoff(job.attempts())))) {
            log.warn("Job {} failed after its lease was lost; it records no outcome", job.jobId());
            return;
        }
        log.warn("Job {} ({}) attempt {} of {} failed: {}", job.jobId(), job.kind(), job.attempts(),
                job.maxAttempts(), error);
    }

    /** Runs jobs until none is runnable. @return number of jobs run */
    public int runAvailable(int limit) {
        int n = 0;
        while (n < limit && runOne()) {
            n++;
        }
        return n;
    }

    /** Stops the renewal thread. Call after the last {@link #runOne()} has returned. */
    @Override
    public void close() {
        renewals.shutdownNow();
    }

    static Duration backoff(int attempts) {
        long seconds = Math.min(3600, 10L * (1L << Math.min(attempts, 12)));
        return Duration.ofSeconds(seconds);
    }

    static String describe(Exception e) {
        String text = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
