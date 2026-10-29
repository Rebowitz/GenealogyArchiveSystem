package org.genealogyarchive.jobs;

/**
 * Performs one kind of background job. Handlers must be idempotent where practical (BUILD-SPEC
 * s.36, s.47): after a genuine crash the same job may run again (no universal exactly-once promise).
 * Before a consequential side effect a handler should call {@link JobExecution#requireLease()}, so an
 * attempt that has lost ownership stops instead of acting. Phase 1 registers no production handlers.
 */
public interface JobHandler {

    /** The job kind this handler performs, e.g. {@code integrity.full-scan} (later phases). */
    String kind();

    /** Performs the job. Throwing marks the attempt failed (retried while attempts remain). */
    void run(JobExecution execution) throws Exception;
}
