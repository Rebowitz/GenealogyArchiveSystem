package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/**
 * A grouping run as one reader may see it (GAS-P3-003). The stored run ({@link GroupingRun}) is permanent audit
 * accounting and is never rewritten. For a reader who cannot see restricted Files and Records, the counts here are
 * recomputed from what that reader can see now: Files the run applied that are visible, and visible Records it
 * created or extended. Counts that cannot be attributed to visible rows after the fact (held, conflicted,
 * ungrouped) are omitted ({@code null}) for such readers.
 */
public record GroupingRunView(UUID runId, GroupingRun.State state, Instant startedAt, Instant finishedAt,
                              int rowsApplied, int recordsCreated, int recordsExtended, Integer rowsHeld,
                              Integer rowsConflicted, Integer rowsUngrouped) {

    static GroupingRunView full(GroupingRun r) {
        return new GroupingRunView(r.runId(), r.state(), r.startedAt(), r.finishedAt(), r.rowsApplied(),
                r.recordsCreated(), r.recordsExtended(), r.rowsHeld(), r.rowsConflicted(), r.rowsUngrouped());
    }
}
