package org.genealogyarchive.preservation;

import java.util.EnumSet;
import java.util.Set;

/**
 * States of a persistent file operation (Phase 2 plan section 3). Recovery is decided from these
 * states plus a fresh check of the bytes on disk; never from filenames alone (TX-002).
 */
public enum OperationState {
    /** Upload bytes are arriving in staging; the receiver heartbeats while active. */
    RECEIVING,
    /** Fully staged and measured (size, SHA-256); waiting for the worker. */
    STAGED,
    /** Target planned and recorded; the worker may be writing it. */
    PROMOTING,
    /** Target written, flushed, closed and protected; awaiting verification and commit. */
    PROMOTED,
    /** Database records committed; final clean-up (staging, quarantine) may remain. */
    COMMITTED,
    /** Finished. */
    COMPLETED,
    /** The target volume is unavailable; resumes when its marker verifies again (TX-008). */
    WAITING_FOR_VOLUME,
    /** Stopped for a person to decide (collision, protection failure, mismatch, unknown state). */
    BLOCKED,
    /** Upload never completed; the temporary staged bytes are discarded. */
    ABANDONED,
    /** An Administrator cancelled a blocked operation; nothing was committed. */
    CANCELLED,
    /** Repair bytes did not match the authoritative size and SHA-256; nothing changed. */
    REJECTED,
    /** Destruction committed; physical removal pending. */
    CLEANUP_PENDING,
    /** Physical removal failed; reported, retryable (TX-007). */
    CLEANUP_INCOMPLETE;

    private static final Set<OperationState> FINAL = EnumSet.of(COMPLETED, ABANDONED, CANCELLED, REJECTED);

    public boolean isFinal() {
        return FINAL.contains(this);
    }
}
