package org.genealogyarchive.jobs;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.BackgroundJobRepository;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persistent PostgreSQL-backed job queue (BUILD-SPEC s.47). No broker; survives restarts. */
public final class JobQueue {

    private static final Logger log = LoggerFactory.getLogger(JobQueue.class);
    private static final Pattern KIND = Pattern.compile("[a-z][a-z0-9.-]{0,99}");

    private final BackgroundJobRepository jobs;
    private final Clock clock;

    public JobQueue(BackgroundJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    /**
     * Queues work for the worker. Called by services inside their own transaction, so the job is
     * queued if and only if the surrounding change commits.
     *
     * @param payloadJson a JSON object with the job's parameters (never secrets)
     */
    public UUID enqueue(String kind, String payloadJson, int maxAttempts) {
        if (kind == null || !KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("Job kinds are lower-case codes such as 'integrity.full-scan'");
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be 1-100");
        }
        UUID id = GlobalIds.newId();
        jobs.insert(id, kind, payloadJson == null ? "{}" : payloadJson, maxAttempts, clock.instant());
        return id;
    }

    public List<Job> recent(Actor actor, int limit) {
        AccessPolicy.require(actor, Permission.VIEW_OPERATIONS);
        return jobs.recent(Math.max(1, Math.min(limit, 500)));
    }

    public Job get(Actor actor, UUID jobId) {
        AccessPolicy.require(actor, Permission.VIEW_OPERATIONS);
        return jobs.findById(jobId).orElseThrow(() -> new NotFoundException("The job"));
    }

    /** Counts by state, for status reporting. */
    public Map<JobState, Long> counts() {
        return jobs.countsByState();
    }

    /** Re-queues a FAILED job for one more attempt (OP-002). */
    public void retry(Actor actor, UUID jobId) {
        AccessPolicy.require(actor, Permission.RETRY_JOBS);
        Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("The job"));
        if (!jobs.retry(jobId, clock.instant())) {
            throw new RuleViolationException("Only a failed job can be retried (this job is " + job.state() + ").");
        }
        log.info("Job {} ({}) re-queued by {}", jobId, job.kind(), actor.label());
    }
}
