package org.genealogyarchive.jobs;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One attempt at running a job, as seen by its handler. Ownership is held while the runner keeps
 * renewing the lease; it is lost when a renewal is refused (another worker took over, or the lease
 * had already expired) or when the locally known lease end passes without a successful renewal
 * (for example the database is unreachable). Lost ownership is never regained by this attempt.
 */
public final class JobExecution {

    private final Job job;
    private final Clock clock;
    private final AtomicReference<Instant> leaseUntil;
    private final AtomicBoolean lost = new AtomicBoolean(false);

    /**
     * @param localLeaseEnd a conservative local deadline: the local time just before the claim (or
     *                      last successful renewal) was requested, plus the lease duration
     */
    JobExecution(Job job, Clock clock, Instant localLeaseEnd) {
        this.job = job;
        this.clock = clock;
        this.leaseUntil = new AtomicReference<>(localLeaseEnd);
    }

    public Job job() {
        return job;
    }

    /** True while this attempt still owns the job, as far as this worker can know. */
    public boolean leaseHeld() {
        if (lost.get()) {
            return false;
        }
        if (clock.instant().isAfter(leaseUntil.get())) {
            lost.set(true);
            return false;
        }
        return true;
    }

    /** Call before any consequential side effect; throws if ownership has been lost. */
    public void requireLease() {
        if (!leaseHeld()) {
            throw new LeaseLostException(job.jobId());
        }
    }

    void renewed(Instant newLocalLeaseEnd) {
        if (!lost.get()) {
            leaseUntil.set(newLocalLeaseEnd);
        }
    }

    void markLost() {
        lost.set(true);
    }

    /** Thrown when an attempt that no longer owns its job tries to continue. */
    public static final class LeaseLostException extends RuntimeException {
        public LeaseLostException(UUID jobId) {
            super("Job " + jobId + " is no longer owned by this attempt (lease lost)");
        }
    }
}
