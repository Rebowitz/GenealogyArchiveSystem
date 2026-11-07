package org.genealogyarchive.access;

/** Operations authorized at the service boundary (Phase 1, extended in Phase 2). */
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
    INITIALIZE_ARCHIVE,
    /** Phase 2: upload Files (and derivatives) for ingestion; mark one's uploads Restricted. */
    INGEST_FILES,
    /** Phase 2: correct File attributes (display filename, rotation), raise restriction, delete/restore. */
    EDIT_FILES,
    /** Phase 2: permanently destroy a deleted File (Administrator-controlled, SEC-002). */
    DESTROY_FILES,
    /** Phase 2: integrity scans, re-verification, verified repair, retry/cancel of blocked operations. */
    MANAGE_PRESERVATION,
    /** Phase 2: see and download Restricted Files and lower a restriction (Administrators, decision B2). */
    VIEW_RESTRICTED_FILES
}
