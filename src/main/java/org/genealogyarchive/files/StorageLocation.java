package org.genealogyarchive.files;

import java.util.Objects;
import java.util.UUID;

/**
 * Persistable reference to stored bytes: Storage Volume identity plus portable relative path
 * (CLAUDE.md "v1 hosting requirement", BUILD-SPEC s.6 and s.28, UP-010).
 * <p>
 * Contains no host-specific information. The same StorageLocation resolves to
 * {@code D:\Archive\...} on one Windows host and {@code /srv/archive/...} on a Linux host, via each
 * installation's own {@link VolumeRoots}.
 * <p>
 * Phase 0 defines the reference shape only. The Storage Volume model (name, purpose, online
 * status) is Phase 1 and File records are Phase 2.
 */
public record StorageLocation(UUID volumeId, PortableRelativePath path) {

    public StorageLocation {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(path, "path");
    }

    /** Diagnostic text form, e.g. {@code volume:0192...:originals/ab/cd/file}. Not a native path. */
    @Override
    public String toString() {
        return "volume:" + volumeId + ":" + path.value();
    }
}
