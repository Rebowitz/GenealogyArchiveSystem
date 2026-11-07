package org.genealogyarchive.files;

import org.genealogyarchive.testsupport.DirectoryLinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2 WP1 / P2-D: volume identity marker, managed layout and link refusal. */
class ManagedVolumeTest {

    private static final UUID ARCHIVE = UUID.fromString("0192f0c4-0000-7000-8000-00000000000a");
    private static final UUID VOLUME = UUID.fromString("0192f0c4-1111-7000-8000-00000000abcd");

    @TempDir
    Path temp;

    private VolumeRoots roots(Path root) {
        return new VolumeRoots(Map.of(VOLUME, root));
    }

    private ManagedVolume.VolumeUnavailableException.Reason reasonFor(Path root, UUID archive) {
        try {
            ManagedVolume.open(roots(root), VOLUME, archive);
            return null;
        } catch (ManagedVolume.VolumeUnavailableException e) {
            return e.reason();
        }
    }

    @Test
    void initializedVolumeOpensOnlyForItsOwnArchiveAndVolume() throws IOException {
        Path root = Files.createDirectory(temp.resolve("primary"));
        ManagedVolume.initialize(root, ARCHIVE, VOLUME, Instant.parse("2026-11-05T12:00:00Z"));

        assertThat(ManagedVolume.open(roots(root), VOLUME, ARCHIVE).root()).isEqualTo(root);
        assertThat(reasonFor(root, UUID.randomUUID())).isEqualTo(ManagedVolume.VolumeUnavailableException.Reason.MARKER_MISMATCH);
        assertThat(Files.isDirectory(root.resolve("originals"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("quarantine"))).isTrue();
    }

    @Test
    void markerIsWrittenOnceAndNeverReplaced() throws IOException {
        Path root = Files.createDirectory(temp.resolve("primary"));
        ManagedVolume.initialize(root, ARCHIVE, VOLUME, Instant.now());
        String before = Files.readString(VolumeMarker.location(root));

        assertThatThrownBy(() -> ManagedVolume.initialize(root, UUID.randomUUID(), VOLUME, Instant.now()))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(VolumeMarker.location(root))).isEqualTo(before);
    }

    @Test
    void emptyDirectoryAtTheRootIsNotInitialized() throws IOException {
        // e.g. a Linux mount point whose NAS is not mounted
        Path root = Files.createDirectory(temp.resolve("mountpoint"));
        assertThat(reasonFor(root, ARCHIVE)).isEqualTo(ManagedVolume.VolumeUnavailableException.Reason.NOT_INITIALIZED);
        assertThat(reasonFor(temp.resolve("absent"), ARCHIVE)).isEqualTo(ManagedVolume.VolumeUnavailableException.Reason.ROOT_UNAVAILABLE);
        assertThatThrownBy(() -> ManagedVolume.open(new VolumeRoots(Map.of()), VOLUME, ARCHIVE))
                .isInstanceOf(ManagedVolume.VolumeUnavailableException.class)
                .hasMessageContaining("no root configured");
    }

    @Test
    void malformedOrMismatchedMarkersAreRefused() throws IOException {
        Path root = Files.createDirectory(temp.resolve("tampered"));
        Files.writeString(VolumeMarker.location(root), "format=1\narchive-id=not-a-uuid\n");
        assertThat(reasonFor(root, ARCHIVE)).isEqualTo(ManagedVolume.VolumeUnavailableException.Reason.MARKER_INVALID);

        Path other = Files.createDirectory(temp.resolve("other-volume"));
        ManagedVolume.initialize(other, ARCHIVE, UUID.randomUUID(), Instant.now());
        assertThat(reasonFor(other, ARCHIVE)).isEqualTo(ManagedVolume.VolumeUnavailableException.Reason.MARKER_MISMATCH);
    }

    @Test
    void markerTextIsHumanReadableAndRoundTrips() throws IOException {
        VolumeMarker marker = new VolumeMarker(1, ARCHIVE, VOLUME, Instant.parse("2026-11-05T12:00:00Z"));
        assertThat(marker.text()).contains("archive-id=" + ARCHIVE).contains("volume-id=" + VOLUME);
        assertThat(VolumeMarker.parse(marker.text())).isEqualTo(marker);
        assertThatThrownBy(() -> VolumeMarker.parse("format=2\narchive-id=" + ARCHIVE + "\nvolume-id=" + VOLUME
                + "\ncreated-at=2026-11-05T12:00:00Z\n")).isInstanceOf(VolumeMarker.InvalidMarkerException.class);
        assertThatThrownBy(() -> VolumeMarker.parse(marker.text() + "volume-id=" + VOLUME + "\n"))
                .as("duplicate keys").isInstanceOf(VolumeMarker.InvalidMarkerException.class);
    }

    @Test
    void physicalNamesComeOnlyFromContentIdentityAndAreSharded() {
        UUID content = UUID.fromString("0192f0c4-2222-7abc-8def-0123456789ab");
        assertThat(ManagedVolume.originalPath(content, 1).value())
                .isEqualTo("originals/89/ab/0192f0c4-2222-7abc-8def-0123456789ab");
        assertThat(ManagedVolume.originalPath(content, 2).value())
                .isEqualTo("originals/89/ab/0192f0c4-2222-7abc-8def-0123456789ab.g2");
        assertThat(ManagedVolume.originalPath(content, 1).value()).isEqualTo(ManagedVolume.originalPath(content, 1).caseFoldKey());
        assertThatThrownBy(() -> ManagedVolume.originalPath(content, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prepareCreatesPlainShardDirectoriesAndRefusesAPlantedJunctionOrLink() throws Exception {
        Path root = Files.createDirectory(temp.resolve("primary"));
        ManagedVolume.initialize(root, ARCHIVE, VOLUME, Instant.now());
        ManagedVolume volume = ManagedVolume.open(roots(root), VOLUME, ARCHIVE);

        UUID content = UUID.fromString("0192f0c4-2222-7abc-8def-0123456789ab");
        Path target = volume.prepare(ManagedVolume.originalPath(content, 1));
        assertThat(Files.isDirectory(target.getParent())).isTrue();

        Path outside = Files.createDirectory(temp.resolve("outside"));
        Path shard = root.resolve("originals").resolve("ff");
        Path link = DirectoryLinks.create(shard, outside);
        try {
            UUID redirected = UUID.fromString("0192f0c4-2222-7abc-8def-0123456789ff");
            assertThatThrownBy(() -> volume.prepare(PortableRelativePath.of("originals/ff/00/" + redirected)))
                    .isInstanceOf(IOException.class);
            assertThat(Files.list(outside).count()).as("nothing created through the link").isZero();
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void listingReportsFilesAndLinksWithoutFollowingThem() throws Exception {
        Path root = Files.createDirectory(temp.resolve("primary"));
        ManagedVolume.initialize(root, ARCHIVE, VOLUME, Instant.now());
        ManagedVolume volume = ManagedVolume.open(roots(root), VOLUME, ARCHIVE);
        Path stored = volume.prepare(PortableRelativePath.of("originals/aa/bb/stored"));
        Files.writeString(stored, "bytes");
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("secret"), "not ours");
        Path link = DirectoryLinks.create(root.resolve("originals").resolve("cc"), outside);
        try {
            var entries = volume.listOriginals();
            assertThat(entries).extracting(e -> e.path().value() + ":" + e.kind())
                    .contains("originals/aa/bb/stored:FILE", "originals/cc:LINK_OR_REPARSE")
                    .noneMatch(s -> s.contains("secret"));
        } finally {
            Files.delete(link);
        }
    }
}
