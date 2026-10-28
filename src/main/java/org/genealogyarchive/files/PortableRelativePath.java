package org.genealogyarchive.files;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A location relative to a Storage Volume root, in a host-independent form that is safe to persist
 * and to exchange between Windows and Linux Archive Instances (BUILD-SPEC s.6, s.27, UP-010).
 * <p>
 * Canonical form: segments joined by '/', no leading or trailing separator. Every rule below is
 * enforced on every host, so a path accepted on Linux is also valid on Windows and vice versa:
 * <ul>
 *   <li>no absolute forms: no leading '/', no drive letter, no UNC prefix;</li>
 *   <li>no backslash and no ':' anywhere (rules out drive letters and NTFS alternate streams);</li>
 *   <li>no '.' or '..' segments and no empty segments (no traversal outside the volume);</li>
 *   <li>no characters illegal on Windows ({@code < > " | ? *}) and no control characters;</li>
 *   <li>no Windows reserved device names (CON, PRN, AUX, NUL, COM0-9, LPT0-9, with or without
 *       an extension, any case);</li>
 *   <li>no segment beginning or ending with a space, or ending with '.';</li>
 *   <li>Unicode NFC normalised (rejected, never silently rewritten);</li>
 *   <li>each segment at most 255 UTF-8 bytes; whole path at most 1024 characters.</li>
 * </ul>
 * Equality is exact and case-sensitive. Because some hosts' filesystems are case-insensitive,
 * {@link #caseFoldKey()} supports detecting names that would collide there.
 * <p>
 * A relative path is NOT File identity: a File is identified by its immutable global ID. This type
 * only describes where bytes are stored inside a volume.
 */
public final class PortableRelativePath implements Comparable<PortableRelativePath> {

    public static final char SEPARATOR = '/';
    public static final int MAX_LENGTH = 1024;
    public static final int MAX_SEGMENT_UTF8_BYTES = 255;

    private static final String ILLEGAL_CHARACTERS = "<>:\"\\|?*";
    private static final Set<String> RESERVED_DEVICE_NAMES = reservedNames();

    private final String value;
    private final List<String> segments;

    private PortableRelativePath(String value, List<String> segments) {
        this.value = value;
        this.segments = segments;
    }

    /**
     * Parses a canonical portable path.
     *
     * @throws InvalidPortablePathException with the specific rule that was violated
     */
    public static PortableRelativePath of(String value) {
        if (value == null || value.isEmpty()) {
            throw new InvalidPortablePathException(String.valueOf(value), "path is empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidPortablePathException(value, "path longer than " + MAX_LENGTH + " characters");
        }
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
            throw new InvalidPortablePathException(value, "path is not Unicode NFC normalised");
        }
        if (value.charAt(0) == SEPARATOR) {
            throw new InvalidPortablePathException(value, "absolute path (leading '/') is not portable");
        }
        if (value.charAt(value.length() - 1) == SEPARATOR) {
            throw new InvalidPortablePathException(value, "trailing '/' is not allowed");
        }
        List<String> segments = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == SEPARATOR) {
                String segment = value.substring(start, i);
                validateSegment(value, segment);
                segments.add(segment);
                start = i + 1;
            }
        }
        return new PortableRelativePath(value, Collections.unmodifiableList(segments));
    }

    /** Builds a path from individual segments, validating each. */
    public static PortableRelativePath ofSegments(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String s : more) {
            sb.append(SEPARATOR).append(s);
        }
        return of(sb.toString());
    }

    /**
     * Converts a native path located under {@code root} into portable form. The native path must be
     * inside the root.
     */
    public static PortableRelativePath fromNative(Path root, Path nativePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = nativePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            throw new InvalidPortablePathException(nativePath.toString(), "is not inside volume root " + root);
        }
        Path relative = normalizedRoot.relativize(normalized);
        List<String> names = new ArrayList<>();
        for (Path name : relative) {
            names.add(name.toString());
        }
        return of(String.join(String.valueOf(SEPARATOR), names));
    }

    /** Returns a new path with one more segment appended. */
    public PortableRelativePath resolve(String segment) {
        return of(value + SEPARATOR + segment);
    }

    /**
     * Resolves this path onto a native volume root, segment by segment, using the host's own path
     * rules (never string concatenation with a separator). The result is guaranteed to lie inside
     * {@code root}.
     */
    public Path toNative(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot;
        for (String segment : segments) {
            result = result.resolve(segment);
        }
        result = result.normalize();
        if (!result.startsWith(normalizedRoot)) {
            // Unreachable given validation; kept as a fail-visible guard.
            throw new IllegalStateException("Portable path escaped its volume root: " + value);
        }
        return result;
    }

    /** Canonical persisted form, always using '/' regardless of host. */
    public String value() {
        return value;
    }

    public List<String> segments() {
        return segments;
    }

    public String fileName() {
        return segments.get(segments.size() - 1);
    }

    public Optional<PortableRelativePath> parent() {
        int idx = value.lastIndexOf(SEPARATOR);
        return idx < 0 ? Optional.empty() : Optional.of(of(value.substring(0, idx)));
    }

    /**
     * Key for detecting paths that would collide on a case-insensitive filesystem. Uses
     * locale-independent lower-casing.
     */
    public String caseFoldKey() {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public int compareTo(PortableRelativePath other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PortableRelativePath other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    private static void validateSegment(String path, String segment) {
        if (segment.isEmpty()) {
            throw new InvalidPortablePathException(path, "empty segment ('//')");
        }
        if (segment.equals(".") || segment.equals("..")) {
            throw new InvalidPortablePathException(path, "'.' and '..' segments are not allowed");
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new InvalidPortablePathException(path, "control character in segment '" + printable(segment) + "'");
            }
            if (ILLEGAL_CHARACTERS.indexOf(c) >= 0) {
                throw new InvalidPortablePathException(path,
                        "character '" + c + "' is not portable (illegal on Windows or a path separator)");
            }
        }
        if (segment.startsWith(" ") || segment.endsWith(" ")) {
            throw new InvalidPortablePathException(path, "segment '" + segment + "' begins or ends with a space");
        }
        if (segment.endsWith(".")) {
            throw new InvalidPortablePathException(path, "segment '" + segment + "' ends with '.' (stripped by Windows)");
        }
        int dot = segment.indexOf('.');
        String base = (dot < 0 ? segment : segment.substring(0, dot)).toUpperCase(Locale.ROOT);
        if (RESERVED_DEVICE_NAMES.contains(base)) {
            throw new InvalidPortablePathException(path, "segment '" + segment + "' is a reserved Windows device name");
        }
        if (segment.getBytes(StandardCharsets.UTF_8).length > MAX_SEGMENT_UTF8_BYTES) {
            throw new InvalidPortablePathException(path, "segment longer than " + MAX_SEGMENT_UTF8_BYTES + " UTF-8 bytes");
        }
    }

    private static String printable(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 0x20 || c == 0x7F ? String.format("\\u%04x", (int) c) : String.valueOf(c));
        }
        return sb.toString();
    }

    private static Set<String> reservedNames() {
        List<String> names = new ArrayList<>(List.of("CON", "PRN", "AUX", "NUL"));
        for (int i = 0; i <= 9; i++) {
            names.add("COM" + i);
            names.add("LPT" + i);
        }
        return Set.copyOf(names);
    }
}
