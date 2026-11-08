package org.genealogyarchive.records;

import java.util.UUID;

/**
 * Test seam at named points of a grouping run (ADR-0011 s.4), like Phase 2's FaultPoints. Production uses
 * {@link #NONE}. A test implementation throws to simulate a crash at a transaction boundary.
 */
public interface GroupingFaults {

    enum Point {
        /** The run is recorded and planned; nothing has been applied. */
        AFTER_PLAN,
        /** Before a chunk's transaction begins. */
        BEFORE_CHUNK,
        /** Inside a chunk's transaction: Record, links and row marks written, not yet committed. */
        BEFORE_CHUNK_COMMIT,
        /** A chunk has committed; before the next chunk or group. */
        AFTER_CHUNK,
        /** Every group processed; before the run is recorded COMPLETED. */
        BEFORE_FINISH
    }

    GroupingFaults NONE = (point, runId, groupKey, chunk) -> { };

    void reach(Point point, UUID runId, String groupKey, int chunk);
}
