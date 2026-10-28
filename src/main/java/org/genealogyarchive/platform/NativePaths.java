package org.genealogyarchive.platform;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Validation of host-native absolute paths that appear in INSTALLATION configuration (Storage
 * Volume roots, log directory, secret files). Such paths are machine-specific by definition and are
 * never persisted as archival data; see {@code org.genealogyarchive.files.PortableRelativePath} for
 * the portable form that is persisted.
 */
public final class NativePaths {

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern WINDOWS_UNC = Pattern.compile("^\\\\\\\\[^\\\\]+\\\\.*");

    private NativePaths() {
    }

    /** Result of validating a native absolute path. */
    public record Validation(Path path, String problem) {
        public boolean valid() {
            return problem == null;
        }

        static Validation ok(Path p) {
            return new Validation(p, null);
        }

        static Validation fail(String problem) {
            return new Validation(null, problem);
        }
    }

    /**
     * Validates that {@code text} is an absolute path according to the rules of the current host.
     * A Windows path on a Linux host (or vice versa) is rejected with an explanatory hint rather than
     * being silently interpreted as a relative path.
     */
    public static Validation validateAbsolute(String text) {
        return validateAbsolute(text, HostPlatform.current());
    }

    static Validation validateAbsolute(String text, HostPlatform host) {
        if (text == null || text.isBlank()) {
            return Validation.fail("path is empty");
        }
        Path path;
        try {
            path = Path.of(text);
        } catch (InvalidPathException e) {
            return Validation.fail("'" + text + "' is not a valid path on this host (" + e.getReason() + ")");
        }
        if (!path.isAbsolute()) {
            return Validation.fail("'" + text + "' must be an absolute path on this " + describe(host) + " host"
                    + hint(text, host));
        }
        return Validation.ok(path.normalize());
    }

    private static String describe(HostPlatform host) {
        return switch (host) {
            case WINDOWS -> "Windows";
            case LINUX -> "Linux";
            case OTHER -> "unsupported";
        };
    }

    private static String hint(String text, HostPlatform host) {
        boolean looksWindows = WINDOWS_DRIVE.matcher(text).matches() || WINDOWS_UNC.matcher(text).matches();
        boolean looksPosix = text.startsWith("/");
        if (host != HostPlatform.WINDOWS && looksWindows) {
            return " (this looks like a Windows path, but this host is not Windows; use a path such as /srv/genealogy-archive/primary)";
        }
        if (host == HostPlatform.WINDOWS && looksPosix) {
            return " (this looks like a POSIX path; on Windows use a drive or UNC path such as D:\\GenealogyArchive\\primary)";
        }
        return "";
    }
}
