package org.genealogyarchive.history;

import org.genealogyarchive.access.Actor;

import java.util.List;

/**
 * Decides which Change History a reader may see. Applied when history is read, so it also covers
 * entries written before an object became restricted (Phase 2 GAS-P2-003). Implemented outside the
 * history package by the module that owns the restriction (preservation, for Archival Files).
 */
public interface HistoryVisibility {

    /** Shows everything (installations or tests without restricted objects). */
    HistoryVisibility ALL = new HistoryVisibility() {
        @Override
        public boolean seesEverything(Actor actor) {
            return true;
        }

        @Override
        public List<ChangeRecord> visibleTo(Actor actor, List<ChangeRecord> records) {
            return records;
        }
    };

    /** True if this reader may see every entry unchanged (then no filtering is needed). */
    boolean seesEverything(Actor actor);

    /**
     * The entries this reader may see: entries about objects hidden from the reader are removed, and
     * field values or reasons that refer to hidden objects are withheld from the remaining entries.
     */
    List<ChangeRecord> visibleTo(Actor actor, List<ChangeRecord> records);
}
