package org.genealogyarchive.platform;

import org.genealogyarchive.testsupport.DirectoryLinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2 WP1: native behaviour of the archival file primitives on this host (no skips). */
class SafeFilesTest {

    @TempDir
    Path temp;

    private static void write(FileChannel channel, String text) throws IOException {
        channel.write(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void exclusiveCreateNeverReplacesAnExistingFile() throws IOException {
        Path existing = Files.writeString(temp.resolve("original"), "authoritative bytes");

        assertThatThrownBy(() -> SafeFiles.createExclusive(existing)).isInstanceOf(FileAlreadyExistsException.class);

        assertThat(Files.readString(existing)).isEqualTo("authoritative bytes");
    }

    @Test
    void exclusiveCreateWritesANewFile() throws IOException {
        Path target = temp.resolve("new");
        try (FileChannel channel = SafeFiles.createExclusive(target)) {
            write(channel, "content");
            channel.force(true);
        }
        SafeFiles.flushDirectory(temp);
        assertThat(Files.readString(target)).isEqualTo("content");
    }

    @Test
    void exclusiveCreateRefusesAnExistingDirectoryLinkAtTheName() throws Exception {
        Path elsewhere = Files.createDirectory(temp.resolve("elsewhere"));
        Path link = DirectoryLinks.create(temp.resolve("planted"), elsewhere);
        try {
            assertThatThrownBy(() -> SafeFiles.createExclusive(link)).isInstanceOf(IOException.class);
            assertThat(Files.list(elsewhere).count()).as("nothing written through the link").isZero();
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void readOnlyProtectionIsAppliedVerifiedAndRemovable() throws IOException {
        Path file = Files.writeString(temp.resolve("stored"), "bytes");
        assertThat(SafeFiles.isReadOnly(file)).isFalse();

        SafeFiles.makeReadOnly(file);
        assertThat(SafeFiles.isReadOnly(file)).isTrue();

        SafeFiles.clearReadOnly(file);
        assertThat(SafeFiles.isReadOnly(file)).isFalse();
        Files.delete(file);
    }

    @Test
    void directoryChainWithALinkOrJunctionIsRefused() throws Exception {
        Path root = Files.createDirectory(temp.resolve("volume"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.createDirectories(root.resolve("originals"));
        Path link = DirectoryLinks.create(root.resolve("originals").resolve("ab"), outside);
        try {
            assertThatThrownBy(() -> SafeFiles.requirePlainDirectoryChain(root, link.resolve("cd").resolve("file")))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> SafeFiles.requirePlainDirectory(link)).isInstanceOf(SafeFiles.UnsafePathException.class);
            assertThat(SafeFiles.isLinkOrReparsePoint(link)).isTrue();
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void plainDirectoryChainIsAccepted() throws IOException {
        Path root = Files.createDirectory(temp.resolve("volume"));
        Files.createDirectories(root.resolve("originals/ab/cd"));
        SafeFiles.requirePlainDirectoryChain(root, root.resolve("originals/ab/cd/file"));
        assertThat(SafeFiles.isLinkOrReparsePoint(root.resolve("originals"))).isFalse();
    }

    @Test
    void pathOutsideTheRootIsRefusedLexically() throws IOException {
        Path root = Files.createDirectory(temp.resolve("volume"));
        assertThatThrownBy(() -> SafeFiles.requirePlainDirectoryChain(root, temp.resolve("other/file")))
                .isInstanceOf(SafeFiles.UnsafePathException.class);
    }

    @Test
    void ensurePlainDirectoryCreatesOnceAndRefusesALink() throws Exception {
        Path dir = temp.resolve("shard");
        SafeFiles.ensurePlainDirectory(dir);
        SafeFiles.ensurePlainDirectory(dir);
        assertThat(Files.isDirectory(dir)).isTrue();

        Path link = DirectoryLinks.create(temp.resolve("linked-shard"), dir);
        try {
            assertThatThrownBy(() -> SafeFiles.ensurePlainDirectory(link)).isInstanceOf(IOException.class);
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void sharingRetryReturnsTheResultAndDoesNotRetryDefiniteFailures() throws IOException {
        assertThat(SafeFiles.withSharingRetry(() -> "done")).isEqualTo("done");
        int[] calls = {0};
        assertThatThrownBy(() -> SafeFiles.withSharingRetry(() -> {
            calls[0]++;
            throw new java.nio.file.NoSuchFileException("gone");
        })).isInstanceOf(java.nio.file.NoSuchFileException.class);
        assertThat(calls[0]).isEqualTo(1);
    }
}
