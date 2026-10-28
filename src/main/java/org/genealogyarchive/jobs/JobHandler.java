package org.genealogyarchive.jobs;

/**
 * Performs one kind of background job. Handlers must be idempotent where practical (BUILD-SPEC
 * s.36, s.47): after a crash the same job may run again. Phase 1 registers no production handlers.
 */
public interface JobHandler {

    /** The job kind this handler performs, e.g. {@code integrity.full-scan} (later phases). */
    String kind();

    /** Performs the job. Throwing marks the attempt failed (retried while attempts remain). */
    void run(Job job) throws Exception;
}
