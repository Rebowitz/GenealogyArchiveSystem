package org.genealogyarchive.access;

/** Operations that Phase 1 authorizes at the service boundary. */
public enum Permission {
    /** Read archive identity, configuration and change history. */
    VIEW_ARCHIVE,
    /** Change one's own password. */
    CHANGE_OWN_PASSWORD,
    /** Correct archive-level configuration (e.g. archive name). */
    CONFIGURE_ARCHIVE,
    /** Create users, change roles, enable/disable, reset passwords. */
    MANAGE_USERS,
    /** Register, correct and retire Storage Volumes. */
    MANAGE_STORAGE_VOLUMES,
    /** See operational status: background jobs, workers, volume availability. */
    VIEW_OPERATIONS,
    /** Re-queue a failed background job. */
    RETRY_JOBS,
    /** Create the Archive Instance. Granted to no role: only the command-line SYSTEM actor. */
    INITIALIZE_ARCHIVE
}
