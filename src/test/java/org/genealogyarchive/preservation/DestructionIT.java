package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.platform.HostPlatform;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Recoverable deletion, restore and permanent destruction (F-006, F-007, TX-007; requirement 4). */
class DestructionIT {

    @TempDir
    Path temp;
    PreservationFixture fx;

    @BeforeEach
    void setUp() throws IOException {
        fx = PreservationFixture.create(temp);
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private ArchivalFile deleted(ArchivalFile f) {
        return fx.preservation().files().delete(fx.editor, f.fileId(), f.version(), "duplicate scan");
    }

    @Test
    void f006_deletionIsRecoverableAndNeverTouchesTheStoredBytes() throws Exception {
        ArchivalFile f = fx.ingest(fx.editor, "a.jpg", b("photo of a gravestone"));
        Path stored = fx.storedPath(f);
        ArchivalFile d = deleted(f);
        assertThat(d.lifecycle()).isEqualTo(FileLifecycle.DELETED);
        assertThat(Files.readAllBytes(stored)).isEqualTo(b("photo of a gravestone"));
        assertThat(fx.preservation().files().list(fx.viewer, true, 50)).as("hidden from ordinary lists").isEmpty();

        ArchivalFile r = fx.preservation().files().restore(fx.editor, f.fileId(), d.version(), null);
        assertThat(r.lifecycle()).isEqualTo(FileLifecycle.ACTIVE);
        assertThat(fx.preservation().files().history(fx.admin, f.fileId(), fx.services().history()))
                .extracting(org.genealogyarchive.history.ChangeRecord::changeKind).contains("DELETED", "RESTORED");
    }

    @Test
    void onlyAdministratorsDestroyAndOnlyFromRecoverableDeletion() {
        ArchivalFile f = fx.ingest(fx.editor, "a.jpg", b("x"));
        assertThatThrownBy(() -> fx.preservation().files().destroy(fx.admin, f.fileId(), f.version(), "why", null))
                .isInstanceOf(RuleViolationException.class);
        ArchivalFile d = deleted(f);
        assertThatThrownBy(() -> fx.preservation().files().destroy(fx.editor, f.fileId(), d.version(), "why", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fx.preservation().files().destroy(fx.admin, f.fileId(), d.version(), " ", null))
                .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void f007_destroyingTheLastFileRemovesTheBytesAndLeavesAPermanentTombstone() throws Exception {
        ArchivalFile f = fx.ingest(fx.editor, "wrong.jpg", b("uploaded by mistake"));
        Path stored = fx.storedPath(f);
        ArchivalFile d = deleted(f);
        FileOperation op = fx.preservation().files().destroy(fx.admin, f.fileId(), d.version(), "uploaded in error", null);
        assertThat(op.state()).isEqualTo(OperationState.CLEANUP_PENDING);

        ArchivalFile tomb = fx.file(f.fileId());
        assertThat(tomb.lifecycle()).isEqualTo(FileLifecycle.DESTROYED);
        assertThat(tomb.friendlyId()).isEqualTo(f.friendlyId());
        assertThat(tomb.destructionReason()).isEqualTo("uploaded in error");
        assertThat(tomb.deletedAt()).as("deletion provenance kept").isNotNull();

        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(Files.exists(stored)).isFalse();
        assertThat(fx.preservation().context().contents().find(f.contentId()).orElseThrow().state())
                .isEqualTo(ContentState.DESTROYED);

        ArchivalFile next = fx.ingest(fx.editor, "next.jpg", b("another"));
        assertThat(next.friendlyId()).as("identifiers are never reused").isNotEqualTo(f.friendlyId());
    }

    @Test
    void requirement4_sharedContentIsKeptWhileAnyFileStillUsesIt() throws Exception {
        byte[] bytes = b("one scan, two provenance records");
        ArchivalFile a = fx.ingest(fx.editor, "a.jpg", bytes);
        ArchivalFile c = fx.ingest(fx.editor, "c.jpg", bytes);
        assertThat(c.contentId()).isEqualTo(a.contentId());
        Path stored = fx.storedPath(a);

        ArchivalFile da = deleted(a);
        FileOperation first = fx.preservation().files().destroy(fx.admin, a.fileId(), da.version(), "dup", c.fileId());
        assertThat(first.state()).as("still referenced by c").isEqualTo(OperationState.COMPLETED);
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
        assertThat(fx.preservation().context().contents().find(a.contentId()).orElseThrow().state())
                .isEqualTo(ContentState.ACTIVE);
        assertThat(fx.file(a.fileId()).replacementFileId()).isEqualTo(c.fileId());

        // A deleted (recoverable) File still holds the content.
        ArchivalFile dc = deleted(c);
        assertThat(Files.exists(stored)).isTrue();
        FileOperation last = fx.preservation().files().destroy(fx.admin, c.fileId(), dc.version(), "dup", null);
        assertThat(last.state()).isEqualTo(OperationState.CLEANUP_PENDING);
        fx.preservation().destruction().process(last.operationId());
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void requirement4_contentPendingDestructionIsNeverReusedByANewUpload() throws Exception {
        byte[] bytes = b("about to be destroyed");
        ArchivalFile a = fx.ingest(fx.editor, "a.jpg", bytes);
        ArchivalFile d = deleted(a);
        FileOperation destroy = fx.preservation().files().destroy(fx.admin, a.fileId(), d.version(), "error", null);

        ArchivalFile again = fx.ingest(fx.editor, "again.jpg", bytes);
        assertThat(again.contentId()).as("new content, not the destroy-pending one").isNotEqualTo(a.contentId());
        fx.preservation().destruction().process(destroy.operationId());
        assertThat(Files.readAllBytes(fx.storedPath(again))).isEqualTo(bytes);
    }

    @Test
    void tx007_aRemovalThatCannotCompleteIsVisibleAndRetriedLater() throws Exception {
        ArchivalFile f = fx.ingest(fx.editor, "locked.jpg", b("cannot be removed yet"));
        Path stored = fx.storedPath(f);
        ArchivalFile d = deleted(f);
        FileOperation op = fx.preservation().files().destroy(fx.admin, f.fileId(), d.version(), "error", null);

        AutoCloseable obstacle = obstruct(stored);
        try {
            fx.preservation().destruction().process(op.operationId());
            FileOperation incomplete = fx.operation(op.operationId());
            assertThat(incomplete.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(incomplete.message()).contains("incomplete");
            assertThat(fx.file(f.fileId()).lifecycle()).as("the File stays retired").isEqualTo(FileLifecycle.DESTROYED);
            assertThat(fx.preservation().operations().status(fx.admin, fx.preservation().volumes()).cleanupIncomplete())
                    .isEqualTo(1);
        } finally {
            obstacle.close();
        }
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(Files.exists(stored)).isFalse();
    }

    /**
     * Makes deletion fail the way it does in the field: on Windows an open handle without delete
     * sharing (FileInputStream); on POSIX a read-only parent directory.
     */
    private static AutoCloseable obstruct(Path file) throws IOException {
        if (SafeFiles.host() == HostPlatform.WINDOWS) {
            FileInputStream open = new FileInputStream(file.toFile());
            return open;
        }
        Path dir = file.getParent();
        var original = Files.getPosixFilePermissions(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        Assumptions.assumeFalse(Files.isWritable(dir),
                "running as a superuser ignores directory permissions; the Windows open-handle case still runs");
        return () -> Files.setPosixFilePermissions(dir, original);
    }
}
