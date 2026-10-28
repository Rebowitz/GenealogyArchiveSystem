package org.genealogyarchive.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Foundation for UP-003 (different storage topology) and UP-010 (portable storage references):
 * one StorageLocation, two installations with different native roots.
 */
class VolumeRootsTest {

    private static final UUID PRIMARY = UUID.fromString("0192f0c4-1111-7000-8000-000000000001");

    @Test
    void sameLocationResolvesUnderEachInstallationsOwnRoot(@TempDir Path siteA, @TempDir Path siteB) {
        StorageLocation location = new StorageLocation(PRIMARY, PortableRelativePath.of("originals/01/file.tif"));

        Path a = new VolumeRoots(Map.of(PRIMARY, siteA)).resolve(location);
        Path b = new VolumeRoots(Map.of(PRIMARY, siteB)).resolve(location);

        assertThat(a.startsWith(siteA.toAbsolutePath().normalize())).isTrue();
        assertThat(b.startsWith(siteB.toAbsolutePath().normalize())).isTrue();
        assertThat(PortableRelativePath.fromNative(siteA, a)).isEqualTo(PortableRelativePath.fromNative(siteB, b));
    }

    @Test
    void storageLocationTextContainsNoHostPath(@TempDir Path root) {
        StorageLocation location = new StorageLocation(PRIMARY, PortableRelativePath.of("originals/01/file.tif"));
        assertThat(location.toString())
                .isEqualTo("volume:0192f0c4-1111-7000-8000-000000000001:originals/01/file.tif")
                .doesNotContain(root.toString())
                .doesNotContain("\\");
    }

    @Test
    void unconfiguredVolumeFailsVisibly() {
        StorageLocation location = new StorageLocation(PRIMARY, PortableRelativePath.of("originals/x"));
        assertThatThrownBy(() -> new VolumeRoots(Map.of()).resolve(location))
                .isInstanceOf(VolumeRoots.VolumeNotConfiguredException.class)
                .hasMessageContaining(PRIMARY.toString());
    }

    @Test
    void resolveRejectsObservableJunctionOrSymlinkEscape(@TempDir Path temp) throws Exception {
        Path root = java.nio.file.Files.createDirectory(temp.resolve("volume"));
        Path outside = java.nio.file.Files.createDirectory(temp.resolve("outside"));
        Path link = org.genealogyarchive.testsupport.DirectoryLinks.create(root.resolve("alias"), outside);
        try {
            StorageLocation location = new StorageLocation(PRIMARY, PortableRelativePath.of("alias/future/file"));
            assertThatThrownBy(() -> new VolumeRoots(Map.of(PRIMARY, root)).resolve(location))
                    .isInstanceOf(java.io.UncheckedIOException.class);
        } finally {
            java.nio.file.Files.delete(link);
        }
    }
}
