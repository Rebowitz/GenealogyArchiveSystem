package org.genealogyarchive.files;

import org.genealogyarchive.platform.SafeFiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The installation-local staging directory where uploads are received before the worker promotes
 * them into a Storage Volume (Phase 2 plan, deviation 1). Staged files are temporaries, never
 * archival originals: each is named by the operation that owns it ({@code <operation-id>.part}).
 * <p>
 * The directory must be a plain directory accessible only to the service accounts and
 * administrators. On POSIX hosts a missing directory is created owner-only; an existing directory
 * with group-write or any "other" access is refused, visibly.
 */
public final class StagingArea {

    private static final String SUFFIX = ".part";

    private final Path dir;

    public StagingArea(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
    }

    public Path directory() {
        return dir;
    }

    /**
     * Checks that staging is usable and protected, creating it (owner-only) if missing.
     *
     * @throws StagingUnavailableException describing the problem
     */
    public void verify() {
        try {
            if (!Files.exists(dir)) {
                Path parent = dir.getParent();
                if (parent == null || !Files.isDirectory(parent)) {
                    throw new StagingUnavailableException("the staging directory and its parent do not exist");
                }
                Files.createDirectory(dir);
                if (dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                    Files.setPosixFilePermissions(dir, EnumSet.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
                }
            }
            if (SafeFiles.isLinkOrReparsePoint(dir)) {
                throw new StagingUnavailableException("the staging directory is a link or junction");
            }
            SafeFiles.requirePlainDirectory(dir);
            List<String> problems = SafeFiles.broadAccessProblems(dir);
            if (!problems.isEmpty()) {
                throw new StagingUnavailableException("the staging directory is accessible to other users ("
                        + String.join("; ", problems) + ")");
            }
        } catch (IOException e) {
            throw new StagingUnavailableException("the staging directory cannot be used (" + e.getClass().getSimpleName() + ")");
        }
    }

    public String nameFor(UUID operationId) {
        return operationId + SUFFIX;
    }

    public Path fileFor(UUID operationId) {
        return dir.resolve(nameFor(operationId));
    }

    /** Creates the staged file for an operation, exclusively. */
    public FileChannel create(UUID operationId) throws IOException {
        return SafeFiles.createExclusive(fileFor(operationId));
    }

    /**
     * The staged file of an operation, after checking it is a plain regular file directly inside the
     * staging directory (the worker's re-validation before promotion).
     */
    public Optional<Path> existing(UUID operationId) throws IOException {
        Path file = fileFor(operationId);
        // V0019 audit: only "no such file" means no staged upload; an unreadable one is an error, never "gone".
        try {
            Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.NoSuchFileException e) {
            return Optional.empty();
        }
        SafeFiles.requirePlainDirectory(dir);
        SafeFiles.requirePlainFile(file);
        return Optional.of(file);
    }

    /** Deletes an operation's staged file if present. @return true if it is gone afterwards */
    public boolean delete(UUID operationId) throws IOException {
        Path file = fileFor(operationId);
        if (!Files.exists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        SafeFiles.withSharingRetry(() -> Files.deleteIfExists(file));
        try {
            Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return false;
        } catch (java.nio.file.NoSuchFileException e) {
            return true;
        }
    }

    /** Names of everything in the staging directory (for leftover detection). */
    public List<String> list() throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                names.add(p.getFileName().toString());
            }
        }
        return names;
    }

    /** Parses an operation ID from a staged file name, if it is one. */
    public static Optional<UUID> operationOf(String name) {
        if (!name.endsWith(SUFFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(name.substring(0, name.length() - SUFFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Staging cannot be used safely. */
    public static final class StagingUnavailableException extends RuntimeException {
        public StagingUnavailableException(String message) {
            super("File ingestion is unavailable: " + message + ".");
        }
    }
}
