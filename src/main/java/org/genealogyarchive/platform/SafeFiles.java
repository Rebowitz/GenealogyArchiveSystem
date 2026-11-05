package org.genealogyarchive.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Operating-system-aware primitives for archival file storage (Phase 2, ADR-0009). Everything that
 * differs between Windows and Linux file handling lives here; callers never branch on the host.
 * <ul>
 *   <li><b>No overwrite.</b> {@link #createExclusive} creates a file only if nothing exists at that
 *       name ({@code O_CREAT|O_EXCL} on Linux, {@code CREATE_NEW} on Windows; SMB and NFSv3+ honour
 *       exclusive create). There is no rename-based promotion: Java's atomic move replaces an
 *       existing target on both hosts.</li>
 *   <li><b>No link following.</b> {@link #requirePlainDirectoryChain} checks every component below a
 *       volume root without following links and refuses symbolic links, Windows junctions and other
 *       reparse points. Files are opened with {@code NOFOLLOW_LINKS}.</li>
 *   <li><b>Durability.</b> {@link FileChannel#force} for file content; {@link #flushDirectory} also
 *       flushes the directory entry where the host allows it (Linux; Java cannot on Windows).</li>
 * </ul>
 * Residual risk (documented in ADR-0009): Java 21 has no handle-relative open, so a principal that
 * can already write inside a managed directory could substitute a link between a check and an
 * open. Directory permissions restrict that to the worker account, administrators and SYSTEM/root.
 */
public final class SafeFiles {

    private static final HostPlatform HOST = HostPlatform.current();
    private static final int WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT = 0x400;
    private static final int RETRIES = 6;

    private SafeFiles() {
    }

    /** The host this process runs on (exposed for diagnostics and tests only). */
    public static HostPlatform host() {
        return HOST;
    }

    /**
     * Creates {@code file}, failing with {@link FileAlreadyExistsException} if anything exists at
     * that name, and returns a channel open for writing. Never follows a link at the final name.
     * On POSIX hosts the file is created owner read/write only.
     */
    public static FileChannel createExclusive(Path file) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        if (posix(file)) {
            FileAttribute<Set<PosixFilePermission>> ownerOnly =
                    PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return FileChannel.open(file, options, ownerOnly);
        }
        return FileChannel.open(file, options);
    }

    /** Opens an existing regular file for reading without following a link at the final name. */
    public static FileChannel openForRead(Path file) throws IOException {
        requirePlainFile(file);
        return FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Flushes a directory's entries to stable storage where the host supports it (Linux). On Windows
     * Java cannot open a directory for flushing; NTFS journals metadata and the recovery protocol
     * re-checks state after a power loss.
     */
    public static void flushDirectory(Path directory) throws IOException {
        if (HOST != HostPlatform.WINDOWS) {
            try (FileChannel dir = FileChannel.open(directory, StandardOpenOption.READ)) {
                dir.force(true);
            }
        }
    }

    /**
     * Applies the preservation protection for a stored original: read-only attribute on Windows,
     * owner/group read-only permissions on POSIX. Verifies that the protection is in place.
     *
     * @throws IOException if the protection could not be applied or verified
     */
    public static void makeReadOnly(Path file) throws IOException {
        requirePlainFile(file);
        if (posix(file)) {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ));
        } else {
            Files.setAttribute(file, "dos:readonly", true, LinkOption.NOFOLLOW_LINKS);
        }
        if (!isReadOnly(file)) {
            throw new IOException("Read-only protection could not be verified on the stored file");
        }
    }

    /** True if the file carries the preservation read-only protection. */
    public static boolean isReadOnly(Path file) throws IOException {
        if (posix(file)) {
            Set<PosixFilePermission> p = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
            return !p.contains(PosixFilePermission.OWNER_WRITE) && !p.contains(PosixFilePermission.GROUP_WRITE)
                    && !p.contains(PosixFilePermission.OTHERS_WRITE);
        }
        return Boolean.TRUE.equals(Files.getAttribute(file, "dos:readonly", LinkOption.NOFOLLOW_LINKS));
    }

    /** Removes the read-only protection so that an authorised destruction can delete the file. */
    public static void clearReadOnly(Path file) throws IOException {
        requirePlainFile(file);
        if (posix(file)) {
            Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } else {
            Files.setAttribute(file, "dos:readonly", false, LinkOption.NOFOLLOW_LINKS);
        }
    }

    /**
     * Requires that {@code root} is a directory and that every component of {@code target}'s parent
     * chain below {@code root} is an existing plain directory: not a symbolic link, junction or other
     * reparse point. The root itself may be reached through a configured link (Phase 0 decision).
     *
     * @throws UnsafePathException if a component is a link, reparse point or not a directory, or
     *                             {@code target} is not lexically inside {@code root}
     */
    public static void requirePlainDirectoryChain(Path root, Path target) throws IOException {
        Path absRoot = root.toAbsolutePath().normalize();
        Path absTarget = target.toAbsolutePath().normalize();
        if (!absTarget.startsWith(absRoot) || absTarget.equals(absRoot)) {
            throw new UnsafePathException("path is outside its volume root");
        }
        if (!Files.isDirectory(absRoot)) {
            throw new NoSuchFileException("volume root is not available");
        }
        Path current = absRoot;
        Path parentRelative = absRoot.relativize(absTarget).getParent();
        if (parentRelative == null) {
            return;
        }
        for (Path name : parentRelative) {
            current = current.resolve(name);
            requirePlainDirectory(current);
        }
    }

    /** Requires an existing plain directory (no link, junction or other reparse point). */
    public static void requirePlainDirectory(Path dir) throws IOException {
        BasicFileAttributes a = Files.readAttributes(dir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (a.isSymbolicLink() || a.isOther() || !a.isDirectory() || isWindowsReparsePoint(dir)) {
            throw new UnsafePathException("a managed directory is a link, junction or not a directory");
        }
    }

    /** Requires an existing plain regular file (no link or reparse point). */
    public static void requirePlainFile(Path file) throws IOException {
        BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (a.isSymbolicLink() || a.isOther() || !a.isRegularFile() || isWindowsReparsePoint(file)) {
            throw new UnsafePathException("a managed file is a link, reparse point or not a regular file");
        }
    }

    /** True if {@code path} exists as a link or reparse point (checked without following it). */
    public static boolean isLinkOrReparsePoint(Path path) throws IOException {
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return a.isSymbolicLink() || (a.isOther() && !a.isRegularFile()) || isWindowsReparsePoint(path);
    }

    /** Creates one directory (not parents) if missing and requires it to be a plain directory. */
    public static void ensurePlainDirectory(Path dir) throws IOException {
        try {
            Files.createDirectory(dir);
            flushDirectory(dir.getParent());
        } catch (FileAlreadyExistsException e) {
            // existed already: verified below
        }
        requirePlainDirectory(dir);
    }

    /**
     * Problems that leave {@code directory} writable by principals other than its owner,
     * administrators and the service accounts. POSIX: group/other write or any other access.
     * Windows: write-allowing entries for well-known broad groups, where their names resolve on this
     * system (best effort; the installer sets the authoritative permissions by SID).
     */
    public static List<String> broadAccessProblems(Path directory) throws IOException {
        List<String> problems = new ArrayList<>();
        if (posix(directory)) {
            Set<PosixFilePermission> p = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS);
            for (PosixFilePermission perm : List.of(PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE)) {
                if (p.contains(perm)) {
                    problems.add("permission " + perm.name().toLowerCase(Locale.ROOT) + " is granted");
                }
            }
            return problems;
        }
        AclFileAttributeView view = Files.getFileAttributeView(directory, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            return problems;
        }
        Set<String> broad = Set.of("everyone", "builtin\\users", "nt authority\\authenticated users");
        for (AclEntry entry : view.getAcl()) {
            String who = entry.principal().getName().toLowerCase(Locale.ROOT);
            if (entry.type() == AclEntryType.ALLOW && broad.contains(who)
                    && (entry.permissions().contains(AclEntryPermission.WRITE_DATA)
                    || entry.permissions().contains(AclEntryPermission.APPEND_DATA)
                    || entry.permissions().contains(AclEntryPermission.READ_DATA))) {
                problems.add(entry.principal().getName() + " has access");
            }
        }
        return problems;
    }

    /** An I/O action that may be retried. */
    @FunctionalInterface
    public interface IoAction<T> {
        T run() throws IOException;
    }

    /**
     * Runs {@code action}, retrying briefly when Windows reports that another process (antivirus,
     * indexer, backup) holds the file. Definite outcomes - missing file, existing file, access denied -
     * are never retried. On other hosts the action runs once.
     */
    public static <T> T withSharingRetry(IoAction<T> action) throws IOException {
        IOException last = null;
        int attempts = HOST == HostPlatform.WINDOWS ? RETRIES : 1;
        for (int i = 0; i < attempts; i++) {
            try {
                return action.run();
            } catch (NoSuchFileException | FileAlreadyExistsException | AccessDeniedException e) {
                throw e;
            } catch (FileSystemException e) {
                last = e;
                if (i + 1 < attempts) {
                    sleep(100L << i);
                }
            }
        }
        throw last;
    }

    private static boolean isWindowsReparsePoint(Path path) throws IOException {
        if (HOST != HostPlatform.WINDOWS) {
            return false;
        }
        try {
            Object attributes = Files.getAttribute(path, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
            return attributes instanceof Integer bits && (bits & WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT) != 0;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // Raw attributes not exposed by this JDK: BasicFileAttributes.isOther() still flags reparse points.
            return false;
        }
    }

    private static boolean posix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a file held by another process", e);
        }
    }

    /** A managed path component is a link, junction, reparse point or the wrong kind of object. */
    public static final class UnsafePathException extends IOException {
        public UnsafePathException(String message) {
            super(message);
        }
    }
}
