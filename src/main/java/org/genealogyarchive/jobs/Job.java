package org.genealogyarchive.jobs;

import java.time.Instant;
import java.util.UUID;

/**
 * A background job (BUILD-SPEC s.47): technical scheduling state for work done by the worker.
 * Not Archival Operation History; archival operations that need durable provenance record that
 * provenance in their own explicit tables in later phases.
 */
public record Job(UUID jobId, String kind, JobState state, String payloadJson, int attempts, int maxAttempts,
                  Instant runAfter, UUID leaseOwner, Instant leaseUntil, String lastError,
                  Instant createdAt, Instant updatedAt, Instant finishedAt) {
}
