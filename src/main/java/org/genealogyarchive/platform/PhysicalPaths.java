package org.genealogyarchive.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Snapshot checks of existing filesystem objects. These are NOT an authorization to open/create a
 * file later: Phase 2 must enforce containment at access time and handle link/mount replacement races.
 */
public final class PhysicalPaths {
    private PhysicalPaths() { }

    /**
     * Resolve the nearest existing ancestor and append the nonexistent suffix. Missing roots remain
     * configurable; permission errors and dangling links fail visibly rather than looking absent.
     */
    public static Optional<Path> existingAncestorPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (ancestor != null) {
            try {
                return Optional.of(ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize());
            } catch (NoSuchFileException e) {
                if (Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Storage path has an unresolved link: " + ancestor, e);
                }
                ancestor = ancestor.getParent();
            }
        }
        return Optional.empty(); // e.g. an unavailable Windows drive: configuration is still meaningful
    }

    /** Fail on observable link escape; no I/O is performed by PortableRelativePath itself. */
    public static void requireContained(Path root, Path candidate) throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        if (!candidate.toAbsolutePath().normalize().startsWith(absoluteRoot)) {
            throw new IOException("Storage path is outside its configured root");
        }
        Path physicalRoot = existingAncestorPath(absoluteRoot)
                .orElseThrow(() -> new IOException("Storage root is unavailable for physical validation"));
        Path physicalCandidate = existingAncestorPath(candidate)
                .orElseThrow(() -> new IOException("Storage target is unavailable for physical validation"));
        if (!physicalCandidate.startsWith(physicalRoot)) {
            throw new IOException("Storage path resolves outside its configured root");
        }
    }
}
