package org.genealogyarchive.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUILD-SPEC s.63A: tests shall explicitly detect accidental assumptions about filename case
 * behaviour, file locking and atomic rename. These are characterisation tests of the CI hosts'
 * default filesystems (NTFS on Windows runners, ext4 on Linux runners). Each assertion states what
 * the host does, so the suite fails loudly if a supported host behaves differently than the code
 * expects. Phase 2 File preservation must use {@link FilesystemCapabilities#probe} rather than these
 * defaults.
 */
class HostFilesystemBehaviourTest {

    @TempDir
    Path dir;

    private static boolean windows() {
        return HostPlatform.current() == HostPlatform.WINDOWS;
    }

    @Test
    void hostPlatformIsASupportedServerHost() {
        assertThat(HostPlatform.current().isSupportedServerHost())
                .as("CI must run on Windows or Linux, the v1 supported hosts")
                .isTrue();
    }

    @Test
    void probeReportsCaseBehaviourOfTheDirectory() throws IOException {
        FilesystemCapabilities caps = FilesystemCapabilities.probe(dir);
        // NTFS default: case-insensitive. ext4: case-sensitive.
        assertThat(caps.caseSensitive()).isEqualTo(!windows());
        try (var leftovers = Files.list(dir)) {
            assertThat(leftovers).as("probe cleans up after itself").isEmpty();
        }
    }

    @Test
    void atomicRenameWithinOneDirectoryIsSupportedOnBothHosts() throws IOException {
        assertThat(FilesystemCapabilities.probe(dir).atomicMoveWithinVolume()).isTrue();
    }

    @Test
    void atomicReplaceOfExistingTargetWithinVolume() throws IOException {
        Path staged = Files.writeString(dir.resolve("staged.tmp"), "new");
        Path target = Files.writeString(dir.resolve("target.bin"), "old");
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        assertThat(Files.readString(target)).isEqualTo("new");
        assertThat(staged).doesNotExist();
    }

    @Test
    void deletingAFileHeldOpenDiffersBetweenHosts() throws IOException {
        Path file = Files.writeString(dir.resolve("held-open.bin"), "content");
        try (FileInputStream open = new FileInputStream(file.toFile())) {
            if (windows()) {
                // Windows: a file opened without FILE_SHARE_DELETE cannot be deleted.
                assertThatThrownBy(() -> Files.delete(file)).isInstanceOf(IOException.class);
            } else {
                // Linux: unlink succeeds while the descriptor stays readable.
                Files.delete(file);
                assertThat(file).doesNotExist();
                assertThat(open.read()).isEqualTo((int) 'c');
            }
        }
    }

    @Test
    void namesDifferingOnlyByCaseCollideOnlyOnCaseInsensitiveHosts() throws IOException {
        Files.writeString(dir.resolve("deed-227.tif"), "a");
        if (windows()) {
            assertThat(Files.exists(dir.resolve("DEED-227.TIF"))).isTrue();
        } else {
            assertThat(Files.exists(dir.resolve("DEED-227.TIF"))).isFalse();
            Files.writeString(dir.resolve("DEED-227.TIF"), "b");
            try (var files = Files.list(dir)) {
                assertThat(files).hasSize(2);
            }
        }
    }
}
