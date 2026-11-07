package org.genealogyarchive.files;

import org.genealogyarchive.platform.SafeFiles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The identity marker at the root of an initialized Storage Volume (Phase 2 decision P2-D). It names
 * the Archive and the Volume the directory belongs to, so the application never reads from or writes
 * to the wrong disk: a USB drive that came back under another letter, a different archive's volume,
 * or an unmounted mount point (which on Linux is an empty local directory).
 * <p>
 * Plain text, one {@code key=value} per line, so an administrator can read it without the
 * application. Written once with exclusive create and never rewritten.
 */
public record VolumeMarker(int format, UUID archiveId, UUID volumeId, Instant createdAt) {

    public static final String FILE_NAME = ".genealogy-archive-volume";
    public static final int CURRENT_FORMAT = 1;
    private static final int MAX_BYTES = 4096;

    public VolumeMarker {
        Objects.requireNonNull(archiveId, "archiveId");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Path location(Path root) {
        return root.resolve(FILE_NAME);
    }

    /** Text form written to disk. */
    public String text() {
        return "# Genealogy Archive System Storage Volume marker. Do not edit, copy to another volume or delete.\n"
                + "format=" + format + "\n"
                + "archive-id=" + archiveId + "\n"
                + "volume-id=" + volumeId + "\n"
                + "created-at=" + createdAt + "\n";
    }

    /** Writes the marker with exclusive create; refuses if any marker (or other object) is present. */
    public void writeTo(Path root) throws IOException {
        SafeFiles.requirePlainDirectory(root);
        Path file = location(root);
        byte[] bytes = text().getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = SafeFiles.createExclusive(file)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        SafeFiles.flushDirectory(root);
    }

    /**
     * Reads the marker at {@code root}.
     *
     * @throws NoSuchFileException  if there is no marker
     * @throws InvalidMarkerException if the marker is unreadable, oversized, a link or malformed
     */
    public static VolumeMarker readFrom(Path root) throws IOException {
        Path file = location(root);
        byte[] bytes;
        try (FileChannel channel = SafeFiles.openForRead(file)) {
            if (channel.size() > MAX_BYTES) {
                throw new InvalidMarkerException("volume marker is larger than expected");
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // read fully
            }
            bytes = buffer.array();
        } catch (SafeFiles.UnsafePathException e) {
            throw new InvalidMarkerException("volume marker is a link or not a regular file");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    static VolumeMarker parse(String text) throws InvalidMarkerException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || values.put(line.substring(0, eq), line.substring(eq + 1)) != null) {
                throw new InvalidMarkerException("volume marker is malformed");
            }
        }
        try {
            int format = Integer.parseInt(required(values, "format"));
            if (format != CURRENT_FORMAT) {
                throw new InvalidMarkerException("volume marker format " + format + " is not supported by this version");
            }
            return new VolumeMarker(format, UUID.fromString(required(values, "archive-id")),
                    UUID.fromString(required(values, "volume-id")), Instant.parse(required(values, "created-at")));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidMarkerException("volume marker is malformed");
        }
    }

    private static String required(Map<String, String> values, String key) throws InvalidMarkerException {
        String v = values.get(key);
        if (v == null) {
            throw new InvalidMarkerException("volume marker lacks " + key);
        }
        return v;
    }

    /** The marker exists but cannot be trusted. */
    public static final class InvalidMarkerException extends IOException {
        public InvalidMarkerException(String message) {
            super(message);
        }
    }
}
