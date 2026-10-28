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

/**
 * Executes background jobs for one worker process. Claims use FOR UPDATE SKIP LOCKED, so several
 * workers never run the same job; a job whose worker died is reclaimed after its lease expires
 * (OP-001); failures are kept with their error for inspection and retry (OP-002).
 */
public final class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    static final int MAX_ERROR_LENGTH = 1000;

    private final BackgroundJobRepository jobs;
    private final Clock clock;
    private final UUID workerId;
    private final Duration lease;
    private final Map<String, JobHandler> handlers;

    public JobRunner(BackgroundJobRepository jobs, Clock clock, UUID workerId, Duration lease, List<JobHandler> handlers) {
        this.jobs = jobs;
        this.clock = clock;
        this.workerId = workerId;
        this.lease = lease;
        Map<String, JobHandler> byKind = new LinkedHashMap<>();
        for (JobHandler h : handlers) {
            if (byKind.put(h.kind(), h) != null) {
                throw new IllegalStateException("Two handlers registered for job kind " + h.kind());
            }
        }
        this.handlers = Map.copyOf(byKind);
    }

    public UUID workerId() {
        return workerId;
    }

    /**
     * Claims and runs at most one job.
     *
     * @return true if a job was run (successfully or not)
     */
    public boolean runOne() {
        Instant now = clock.instant();
        int expired = jobs.failExpiredWithoutAttemptsLeft(now);
        if (expired > 0) {
            log.warn("{} job(s) failed because their worker stopped and no attempts remain", expired);
        }
        Optional<Job> claimed = jobs.claim(handlers.keySet(), workerId, now, lease);
        if (claimed.isEmpty()) {
            return false;
        }
        Job job = claimed.get();
        try {
            handlers.get(job.kind()).run(job);
            if (!jobs.succeed(job.jobId(), workerId, clock.instant())) {
                log.warn("Job {} finished after its lease was lost; its final state is decided by the new owner", job.jobId());
            }
        } catch (Exception e) {
            String error = describe(e);
            Instant retryAt = clock.instant().plus(backoff(job.attempts()));
            jobs.fail(job.jobId(), workerId, error, clock.instant(), retryAt);
            log.warn("Job {} ({}) attempt {} of {} failed: {}", job.jobId(), job.kind(), job.attempts(),
                    job.maxAttempts(), error);
        }
        return true;
    }

    /** Runs jobs until none is runnable. @return number of jobs run */
    public int runAvailable(int limit) {
        int n = 0;
        while (n < limit && runOne()) {
            n++;
        }
        return n;
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
