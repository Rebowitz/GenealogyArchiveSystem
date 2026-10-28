package org.genealogyarchive.files;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Host-local mapping from Storage Volume identity to native root directory, taken from installation
 * configuration. This is the single point where a portable {@link StorageLocation} becomes a native
 * {@link Path}; nothing else concatenates roots and relative paths.
 */
public final class VolumeRoots {

    private final Map<UUID, Path> roots;

    public VolumeRoots(Map<UUID, Path> roots) {
        this.roots = Map.copyOf(roots);
    }

    public Optional<Path> rootOf(UUID volumeId) {
        return Optional.ofNullable(roots.get(volumeId));
    }

    /**
     * Resolves a location to a native path on this host.
     *
     * @throws VolumeNotConfiguredException if this installation has no root for the volume -
     *         reported visibly rather than guessed
     */
    public Path resolve(StorageLocation location) {
        Path root = roots.get(location.volumeId());
        if (root == null) {
            throw new VolumeNotConfiguredException(location.volumeId());
        }
        return location.path().toNative(root);
    }

    /** No root configured for the requested Storage Volume on this installation. */
    public static final class VolumeNotConfiguredException extends RuntimeException {
        private final UUID volumeId;

        public VolumeNotConfiguredException(UUID volumeId) {
            super("Storage Volume " + volumeId + " has no root directory configured on this installation");
            this.volumeId = volumeId;
        }

        public UUID volumeId() {
            return volumeId;
        }
    }
}
