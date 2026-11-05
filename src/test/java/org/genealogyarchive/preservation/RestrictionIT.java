package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Restricted Files are Administrator-only and indistinguishable from nonexistent ones (P2-H, B2). */
class RestrictionIT {

    @TempDir
    Path temp;
    PreservationFixture fx;
    ArchivalFile restricted;

    @BeforeEach
    void setUp() throws IOException {
        fx = PreservationFixture.create(temp);
        ArchivalFile f = fx.ingest(fx.admin, "living.jpg", "a living person's record".getBytes(StandardCharsets.UTF_8));
        restricted = fx.preservation().files().correct(fx.admin, f.fileId(), f.version(), f.managedFilename(), 0,
                Restriction.RESTRICTED, "living person");
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void administratorsSeeAndDownloadRestrictedFiles() throws Exception {
        assertThat(fx.preservation().files().get(fx.admin, restricted.fileId()).restricted()).isTrue();
        assertThat(fx.preservation().files().list(fx.admin, false, 10)).hasSize(1);
        try (var d = fx.preservation().files().download(fx.admin, restricted.fileId())) {
            assertThat(d.stream().readAllBytes()).isNotEmpty();
        }
    }

    @Test
    void othersGetExactlyTheSameAnswerAsForAFileThatDoesNotExist() {
        UUID nonexistent = UUID.randomUUID();
        for (var actor : new org.genealogyarchive.access.Actor[]{fx.editor, fx.viewer}) {
            Throwable hidden = catchThrowable(() -> fx.preservation().files().get(actor, restricted.fileId()));
            Throwable absent = catchThrowable(() -> fx.preservation().files().get(actor, nonexistent));
            assertThat(hidden).isInstanceOf(NotFoundException.class);
            assertThat(hidden).hasMessage(absent.getMessage());
            assertThat(catchThrowable(() -> fx.preservation().files().getByFriendlyId(actor, restricted.friendlyId())))
                    .isInstanceOf(NotFoundException.class).hasMessage(absent.getMessage());
            assertThatThrownBy(() -> fx.preservation().files().download(actor, restricted.fileId()))
                    .isInstanceOf(NotFoundException.class).hasMessage(absent.getMessage());
            assertThatThrownBy(() -> fx.preservation().files().view(actor, restricted.fileId()))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> fx.preservation().files().thumbnail(actor, restricted.fileId()))
                    .isInstanceOf(NotFoundException.class).hasMessage(absent.getMessage());
            assertThat(fx.preservation().files().list(actor, true, 50)).isEmpty();
        }
    }

    @Test
    void uploadingIdenticalBytesDoesNotRevealThatARestrictedFileExists() {
        UUID id = fx.receive(fx.editor, "copy.jpg", "a living person's record".getBytes(StandardCharsets.UTF_8)).operationId();
        FileOperation op = fx.process(id);
        assertThat(op.outcome()).as("not reported as a duplicate of something the editor cannot see")
                .isEqualTo("NEW_CONTENT");
        ArchivalFile open = fx.file(op.fileId());
        assertThat(open.contentId()).isNotEqualTo(restricted.contentId());
        assertThat(fx.preservation().files().view(fx.editor, open.fileId()).sharingContent()).isEmpty();
    }

    @Test
    void aFileSharingContentWithALaterRestrictedFileDoesNotListIt() {
        ArchivalFile a = fx.ingest(fx.editor, "a.jpg", "shared bytes".getBytes(StandardCharsets.UTF_8));
        ArchivalFile b = fx.ingest(fx.editor, "b.jpg", "shared bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(b.contentId()).isEqualTo(a.contentId());
        fx.preservation().files().correct(fx.admin, a.fileId(), a.version(), "a.jpg", 0, Restriction.RESTRICTED, null);
        assertThat(fx.preservation().files().view(fx.editor, b.fileId()).sharingContent()).isEmpty();
        assertThat(fx.preservation().files().view(fx.admin, b.fileId()).sharingContent()).hasSize(1);
    }

    @Test
    void editorsCannotChangeOrDestroyARestrictedFileTheyCannotSee() {
        assertThatThrownBy(() -> fx.preservation().files().correct(fx.editor, restricted.fileId(), restricted.version(),
                "x.jpg", 0, Restriction.NORMAL, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> fx.preservation().files().delete(fx.editor, restricted.fileId(), restricted.version(), null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> fx.preservation().files().destroy(fx.editor, restricted.fileId(), restricted.version(),
                "x", null)).isInstanceOf(AccessDeniedException.class);
    }
}
