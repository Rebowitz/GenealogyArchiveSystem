package org.genealogyarchive.history;

/** Change kinds used in Phase 1. Later phases add their own; values are stable stored codes. */
public final class ChangeKinds {
    public static final String CREATED = "CREATED";
    public static final String CORRECTED = "CORRECTED";
    public static final String RETIRED = "RETIRED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String DELETED = "DELETED";
    public static final String RESTORED = "RESTORED";
    public static final String DESTROYED = "DESTROYED";
    public static final String REPAIRED = "REPAIRED";
    public static final String INITIALIZED = "INITIALIZED";

    private ChangeKinds() {
    }
}
