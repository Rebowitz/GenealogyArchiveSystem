package org.genealogyarchive.preservation;

import java.io.IOException;
import java.util.UUID;

/**
 * Test seam at named points of the file-operation protocol (Phase 2 plan section 9). Production uses
 * {@link #NONE}. A test implementation may throw (a simulated crash or I/O failure) or block (to
 * kill a real worker process mid-operation).
 */
public interface FaultPoints {

    /** Named protocol points. */
    enum Point {
        /** Receiver: after staged bytes are flushed, before the operation becomes STAGED. */
        AFTER_STAGING_WRITE,
        /** Worker: target planned and recorded, before the exclusive create. */
        BEFORE_TARGET_CREATE,
        /** Worker: after the first chunk has been copied into the target. */
        DURING_COPY,
        /** Worker: target written and flushed, before protection is applied. */
        BEFORE_PROTECT,
        /** Worker: target protected, before the operation becomes PROMOTED. */
        AFTER_PROTECT,
        /** Worker: stored copy verified, before the database commit. */
        AFTER_VERIFY_BEFORE_COMMIT,
        /** Worker: committed, before staged bytes are cleaned up. */
        AFTER_COMMIT_BEFORE_CLEANUP,
        /** Destruction: tombstone committed, before bytes are removed. */
        BEFORE_PHYSICAL_DELETE,
        /** Destruction: after each known copy is deleted, before the next (GAS-P2-R01). */
        DURING_PHYSICAL_DELETE,
        /** Repair: repaired generation committed, before the damaged copy is moved to quarantine. */
        BEFORE_QUARANTINE_MOVE,
        /** Any quarantine move: the object has been moved, before the move is recorded (GAS-P2-008). */
        /** A quarantine move's intent (with the object's evidence) is committed; nothing has moved yet. */
        AFTER_QUARANTINE_INTENT,
        /** A quarantine move verified its source and is about to rename it (the remaining check/use window). */
        BEFORE_QUARANTINE_RENAME,
        AFTER_QUARANTINE_MOVE,
        /** Integrity: inside the transaction recording content MISSING, after the finding, before the state. */
        AFTER_MISSING_FINDING,
        /** Partial copy: its quarantine move is recorded, before the operation is replanned (GAS-P2-008). */
        AFTER_QUARANTINE_RECORDED
    }

    FaultPoints NONE = (point, operationId) -> { };

    void reach(Point point, UUID operationId) throws IOException;
}
