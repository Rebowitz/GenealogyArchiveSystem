package org.genealogyarchive.files;

import org.genealogyarchive.testsupport.DirectoryLinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2 requirement 2: staging is isolated, protected and re-validated. */
class StagingAreaTest {

    @TempDir
    Path temp;

    private static boolean posix(Path p) {
        return p.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    @Test
    void missingStagingDirectoryIsCreatedPrivate() throws IOException {
        StagingArea staging = new StagingArea(temp.resolve("staging"));
        staging.verify();
        assertThat(Files.isDirectory(staging.directory())).isTrue();
        if (posix(temp)) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(staging.directory()))).isEqualTo("rwx------");
        }
    }

    @Test
    void stagingDirectoryAccessibleToOthersIsRefused() throws IOException {
        Path dir = Files.createDirectory(temp.resolve("open-staging"));
        if (posix(dir)) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
            assertThatThrownBy(() -> new StagingArea(dir).verify())
                    .isInstanceOf(StagingArea.StagingUnavailableException.class).hasMessageContaining("other users");
        } else {
            // Windows: the installer sets the authoritative ACL by SID; the temp directory is owner-only.
            new StagingArea(dir).verify();
        }
    }

    @Test
    void stagingDirectoryThatIsALinkOrJunctionIsRefused() throws Exception {
        Path real = Files.createDirectory(temp.resolve("real"));
        Path link = DirectoryLinks.create(temp.resolve("staging-link"), real);
        try {
            assertThatThrownBy(() -> new StagingArea(link).verify()).isInstanceOf(StagingArea.StagingUnavailableException.class);
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void stagedFilesAreExclusiveNamedByOperationAndRevalidated() throws IOException {
        StagingArea staging = new StagingArea(temp.resolve("staging"));
        staging.verify();
        UUID op = UUID.randomUUID();
        try (var channel = staging.create(op)) {
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {1, 2, 3}));
        }
        assertThatThrownBy(() -> staging.create(op)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(staging.existing(op)).contains(staging.fileFor(op));
        assertThat(StagingArea.operationOf(staging.nameFor(op))).contains(op);
        assertThat(StagingArea.operationOf("stray.tmp")).isEmpty();
        assertThat(staging.list()).containsExactly(op + ".part");

        assertThat(staging.delete(op)).isTrue();
        assertThat(staging.existing(op)).isEmpty();
    }

    @Test
    void aLinkPlantedAtAStagedNameIsNotAcceptedAsTheStagedFile() throws Exception {
        StagingArea staging = new StagingArea(temp.resolve("staging"));
        staging.verify();
        UUID op = UUID.randomUUID();
        Path elsewhere = Files.createDirectory(temp.resolve("elsewhere"));
        Path link = DirectoryLinks.create(staging.fileFor(op), elsewhere);
        try {
            assertThatThrownBy(() -> staging.existing(op)).isInstanceOf(IOException.class);
        } finally {
            Files.delete(link);
        }
    }
}
