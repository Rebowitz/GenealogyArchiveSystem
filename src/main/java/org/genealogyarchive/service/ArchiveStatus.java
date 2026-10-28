package org.genealogyarchive.service;

import java.util.UUID;

/**
 * Whether this database holds an initialized Archive Instance (Phase 1). The instance is ready
 * only when it does (owner decision B).
 *
 * @param state     INITIALIZED, NOT_INITIALIZED, or UNKNOWN when it could not be determined
 * @param archiveId the Archive ID when initialized
 * @param message   administrator-facing explanation; never contains archive content
 */
public record ArchiveStatus(State state, UUID archiveId, String message) {

    public enum State { INITIALIZED, NOT_INITIALIZED, UNKNOWN }

    public static ArchiveStatus initialized(UUID archiveId) {
        return new ArchiveStatus(State.INITIALIZED, archiveId, "The archive is initialized.");
    }

    public static ArchiveStatus notInitialized() {
        return new ArchiveStatus(State.NOT_INITIALIZED, null,
                "The archive has not been initialized. Run the 'init-archive' command.");
    }

    public static ArchiveStatus unknown(String message) {
        return new ArchiveStatus(State.UNKNOWN, null, message);
    }

    public boolean initialized() {
        return state == State.INITIALIZED;
    }
}
