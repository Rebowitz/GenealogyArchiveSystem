package org.genealogyarchive.storage;

/** Whether a registered volume can currently be reached on this installation. */
public enum VolumeAvailability {
    /** The installation maps a root and it is an existing directory. */
    AVAILABLE,
    /** The installation maps a root but it is not reachable (unmounted, disconnected, offline). */
    UNAVAILABLE,
    /** This installation has no root for the volume in its installation configuration. */
    NOT_CONFIGURED
}
