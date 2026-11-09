package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/**
 * One application of a Capture Session's worksheet (plan section 10.4; ADR-0011 s.4). A run has a durable identity;
 * each bounded chunk it commits is recorded with the Record it created or extended, in the same transaction, so a
 * run that stops part-way shows exactly what it completed and a later run applies only what remains.
 * {@code restrictedView} records whether the person who started it could see restricted Files; such a run is shown
 * only to people who can (its counts could reveal restricted rows). Held rows belong to groups whose boundaries
 * depend on rows the starter could not see; they are left for an Administrator (owner safeguard 1).
 */
public record GroupingRun(UUID runId, UUID captureSessionId, State state, Instant startedAt, UUID startedBy,
                          boolean restrictedView, Instant finishedAt, int rowsPlanned, int rowsApplied,
                          int rowsUngrouped, int rowsConflicted, int rowsHeld, int groupsHeld, int recordsCreated,
                          int recordsExtended) {

    public enum State { RUNNING, COMPLETED, INTERRUPTED }
}
