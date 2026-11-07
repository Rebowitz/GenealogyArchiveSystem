package org.genealogyarchive.preservation;

import org.genealogyarchive.platform.SafeFiles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/** Streaming hash, copy and compare of stored content, never holding a whole file in memory. */
final class ContentIo {

    static final int CHUNK = 1 << 20;

    private ContentIo() {
    }

    /** Size and SHA-256 of a file, plus its first bytes (for media-type detection). */
    record Measurement(long size, String sha256, byte[] head) {
    }

    static Measurement measure(Path file) throws IOException {
        MessageDigest digest = Sha256.digest();
        byte[] head = new byte[0];
        long size = 0;
        try (FileChannel in = SafeFiles.openForRead(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(CHUNK);
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                buffer.flip();
                if (head.length < 32) {
                    byte[] chunk = new byte[Math.min(32 - head.length, buffer.remaining())];
                    buffer.duplicate().get(chunk);
                    byte[] merged = Arrays.copyOf(head, head.length + chunk.length);
                    System.arraycopy(chunk, 0, merged, head.length, chunk.length);
                    head = merged;
                }
                digest.update(buffer);
                size += n;
                buffer.clear();
            }
        }
        return new Measurement(size, Sha256.hex(digest.digest()), head);
    }

    /**
     * Copies {@code source} into the already-created {@code target} channel, hashing the source bytes
     * as they are read, then forces the target to stable storage.
     *
     * @return size and SHA-256 of the bytes read from {@code source}
     */
    static Measurement copy(Path source, FileChannel target, FaultPoints faults, UUID operationId) throws IOException {
        MessageDigest digest = Sha256.digest();
        long size = 0;
        boolean first = true;
        try (FileChannel in = SafeFiles.openForRead(source)) {
            ByteBuffer buffer = ByteBuffer.allocate(CHUNK);
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                buffer.flip();
                digest.update(buffer.duplicate());
                while (buffer.hasRemaining()) {
                    target.write(buffer);
                }
                size += n;
                buffer.clear();
                if (first) {
                    first = false;
                    faults.reach(FaultPoints.Point.DURING_COPY, operationId);
                }
            }
        }
        target.force(true);
        return new Measurement(size, Sha256.hex(digest.digest()), new byte[0]);
    }

    /** Byte-for-byte comparison (BUILD-SPEC s.32: required before omitting incoming content). */
    static boolean identical(Path a, Path b) throws IOException {
        try (FileChannel x = SafeFiles.openForRead(a); FileChannel y = SafeFiles.openForRead(b)) {
            if (x.size() != y.size()) {
                return false;
            }
            ByteBuffer bx = ByteBuffer.allocate(CHUNK);
            ByteBuffer by = ByteBuffer.allocate(CHUNK);
            while (true) {
                bx.clear();
                by.clear();
                int nx = readFully(x, bx);
                int ny = readFully(y, by);
                if (nx != ny) {
                    return false;
                }
                if (nx <= 0) {
                    return true;
                }
                bx.flip();
                by.flip();
                if (!bx.equals(by)) {
                    return false;
                }
            }
        }
    }

    /**
     * Whether every byte of {@code candidate} equals the byte at the same position of {@code full}, and
     * {@code candidate} is no longer than {@code full}: the object is a (possibly complete) prefix of the bytes
     * an operation was copying from {@code full}.
     */
    static boolean isPrefixOf(Path candidate, Path full) throws IOException {
        try (FileChannel c = SafeFiles.openForRead(candidate); FileChannel f = SafeFiles.openForRead(full)) {
            if (c.size() > f.size()) {
                return false;
            }
            ByteBuffer bc = ByteBuffer.allocate(CHUNK);
            ByteBuffer bf = ByteBuffer.allocate(CHUNK);
            while (true) {
                bc.clear();
                int nc = readFully(c, bc);
                if (nc <= 0) {
                    // Every byte of the candidate was read and matched (bytes it gained while being read included).
                    return true;
                }
                bf.clear();
                bf.limit(nc);
                int nf = readFully(f, bf);
                if (nf != nc) {
                    return false;
                }
                bc.flip();
                bf.flip();
                if (!bc.equals(bf)) {
                    return false;
                }
            }
        }
    }

    private static int readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        int total = 0;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer);
            if (n < 0) {
                return total == 0 ? -1 : total;
            }
            total += n;
        }
        return total;
    }
}
