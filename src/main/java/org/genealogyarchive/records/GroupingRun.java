package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/**
 * One application of a Capture Session's worksheet (plan section 10.4). A run has a durable identity; each group
 * it applies is recorded with the Record it created or extended, in the same transaction, so a run that stops
 * part-way shows exactly what it completed and a later run applies only what remains.
 */
public record GroupingRun(UUID runId, UUID captureSessionId, State state, Instant startedAt, UUID startedBy,
                          Instant finishedAt, int rowsPlanned, int rowsApplied, int rowsUngrouped, int rowsConflicted,
                          int recordsCreated, int recordsExtended) {

    public enum State { RUNNING, COMPLETED, INTERRUPTED }
}
