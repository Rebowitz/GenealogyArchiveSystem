package org.genealogyarchive.platform;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Empirical probe of filesystem behaviour inside a directory, instead of assuming it from the
 * operating system. A NAS share mounted on Linux may be case-insensitive; a Windows directory can
 * be case-sensitive; rename atomicity depends on the filesystem, not the OS (PHASE-0 cross-platform
 * rules).
 * <p>
 * Phase 2 File preservation will use these results; Phase 0 provides and tests the probe.
 *
 * @param caseSensitive          true if names differing only by case are distinct files
 * @param atomicMoveWithinVolume true if {@code ATOMIC_MOVE} rename succeeds within this directory
 */
public record FilesystemCapabilities(boolean caseSensitive, boolean atomicMoveWithinVolume) {

    /**
     * Probes {@code directory}, which must exist and be writable. Temporary probe files are removed
     * before returning.
     */
    public static FilesystemCapabilities probe(Path directory) throws IOException {
        Path probeDir = Files.createDirectory(directory.resolve(".gas-probe-" + UUID.randomUUID()));
        try {
            Path lower = Files.writeString(probeDir.resolve("case-probe"), "probe");
            boolean caseSensitive = !Files.exists(probeDir.resolve("CASE-PROBE"));

            boolean atomicMove;
            Path target = probeDir.resolve("moved-probe");
            try {
                Files.move(lower, target, StandardCopyOption.ATOMIC_MOVE);
                atomicMove = true;
            } catch (AtomicMoveNotSupportedException e) {
                atomicMove = false;
            }
            return new FilesystemCapabilities(caseSensitive, atomicMove);
        } finally {
            deleteQuietly(probeDir);
        }
    }

    private static void deleteQuietly(Path dir) throws IOException {
        try (var children = Files.list(dir)) {
            for (Path child : children.toList()) {
                Files.deleteIfExists(child);
            }
        }
        Files.deleteIfExists(dir);
    }
}
