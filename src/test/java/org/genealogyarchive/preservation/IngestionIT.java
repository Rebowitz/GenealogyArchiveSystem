package org.genealogyarchive.preservation;

import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2 ingestion (F-001, F-003..F-005, F-009, BI-008, owner decisions P2-A..P2-D, B3, B5). */
class IngestionIT {

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

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private long stagingFiles() throws IOException {
        if (!Files.exists(fx.staging)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(fx.staging)) {
            return s.count();
        }
    }

    @Test
    void f001_ingestionPreservesExactBytesProvenanceAndProtectionBeforeReportingSuccess() throws Exception {
        byte[] content = bytes("1851 census page, enumeration district 4");
        FileOperation received = fx.receive(fx.editor, "census 1851 p4.tif", content);

        assertThat(received.state()).as("received only; not yet archived").isEqualTo(OperationState.STAGED);
        assertThat(received.fileId()).isNull();
        assertThat(received.expectedSha256()).isEqualTo(sha(content));

        FileOperation done = fx.process(received.operationId());
        assertThat(done.state()).isEqualTo(OperationState.COMPLETED);
        ArchivalFile file = fx.file(done.fileId());
        assertThat(file.friendlyId()).matches("F\\d{5,}");
        assertThat(file.originalFilename()).isEqualTo("census 1851 p4.tif");
        assertThat(file.managedFilename()).isEqualTo("census 1851 p4.tif");
        assertThat(file.ingestedBy()).isEqualTo(fx.editor.userId());
        assertThat(file.clientLastModified()).isEqualTo(Instant.parse("2020-01-02T03:04:05Z"));
        assertThat(file.sourceNote()).isEqualTo("test source");
        assertThat(file.lifecycle()).isEqualTo(FileLifecycle.ACTIVE);

        Path stored = fx.storedPath(file);
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        assertThat(SafeFiles.isReadOnly(stored)).as("protected before success is reported").isTrue();
        assertThat(stored.getFileName().toString()).as("filename is not identity")
                .doesNotContain("census").startsWith(file.contentId().toString());
        assertThat(stagingFiles()).as("staged bytes cleaned after commit").isZero();

        var events = fx.preservation().context().operations().events(done.operationId());
        assertThat(events).extracting(OperationEvent::toState).containsSubsequence(
                "RECEIVING", "STAGED", "PROMOTING", "PROMOTED", "COMMITTED", "COMPLETED");
    }

    @Test
    void successIsReportedOnlyOnceTheStoredCopyIsVerifiedProtectedAndCommitted() throws Exception {
        byte[] content = bytes("protected before success");
        FileOperation op = fx.receive(fx.editor, "a.txt", content);
        boolean[] protectedAtPromoted = {false};
        fx.faults.arm(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT, id -> {
            FileOperation now = fx.operation(id);
            assertThat(now.state()).isEqualTo(OperationState.PROMOTED);
            assertThat(now.fileId()).as("no File exists before the commit").isNull();
            Path target = now.targetPath() == null ? null
                    : org.genealogyarchive.files.PortableRelativePath.of(now.targetPath()).toNative(fx.volumeRoot);
            protectedAtPromoted[0] = target != null && SafeFiles.isReadOnly(target);
        });
        fx.process(op.operationId());
        assertThat(protectedAtPromoted[0]).isTrue();
    }

    @Test
    void p2b_identicalContentIngestedTwiceGetsTwoFilesWithOwnProvenanceAndSharedBytes() throws Exception {
        byte[] content = bytes("same scan delivered twice");
        ArchivalFile first = fx.ingest(fx.editor, "first.jpg", content);
        FileOperation second = fx.process(fx.receive(fx.admin, "second-copy.jpg", content).operationId());

        assertThat(second.outcome()).isEqualTo("SHARED_CONTENT");
        ArchivalFile other = fx.file(second.fileId());
        assertThat(other.fileId()).isNotEqualTo(first.fileId());
        assertThat(other.friendlyId()).isNotEqualTo(first.friendlyId());
        assertThat(other.contentId()).as("physical content shared").isEqualTo(first.contentId());
        assertThat(other.originalFilename()).isEqualTo("second-copy.jpg");
        assertThat(other.ingestedBy()).isEqualTo(fx.admin.userId());
        try (Stream<Path> files = Files.walk(fx.volumeRoot.resolve("originals"))) {
            assertThat(files.filter(Files::isRegularFile).count()).as("one stored object").isEqualTo(1);
        }
    }

    @Test
    void f004_aDuplicateCandidateWhoseStoredBytesNoLongerMatchIsNeverShared() throws Exception {
        byte[] content = bytes("original bytes of a will");
        ArchivalFile first = fx.ingest(fx.editor, "will.pdf", content);
        Path stored = fx.storedPath(first);
        SafeFiles.clearReadOnly(stored);
        byte[] damaged = content.clone();
        damaged[0] ^= 1;
        Files.write(stored, damaged);

        FileOperation second = fx.process(fx.receive(fx.editor, "will-again.pdf", content).operationId());

        assertThat(second.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(second.outcome()).isNotEqualTo("SHARED_CONTENT");
        ArchivalFile again = fx.file(second.fileId());
        assertThat(again.contentId()).isNotEqualTo(first.contentId());
        assertThat(Files.readAllBytes(fx.storedPath(again))).isEqualTo(content);
    }

    @Test
    void bi008_resendingTheSameBatchItemNeverCreatesASecondFile() {
        UUID batch = fx.newBatch(fx.editor);
        byte[] content = bytes("letter from Ireland");
        FileOperation a = fx.receive(fx.editor, batch, "item-a", "letter.png", content, Restriction.NORMAL, null, null);
        FileOperation b = fx.receive(fx.editor, batch, "item-a", "letter.png", content, Restriction.NORMAL, null, null);
        assertThat(b.operationId()).isEqualTo(a.operationId());
        fx.process(a.operationId());
        fx.process(a.operationId());
        FileOperation c = fx.receive(fx.editor, batch, "item-a", "letter.png", content, Restriction.NORMAL, null, null);
        assertThat(c.operationId()).isEqualTo(a.operationId());
        assertThat(c.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(fx.preservation().context().files().byBatch(batch)).hasSize(1);
    }

    @Test
    void b5_uploadLimitIsEnforcedWhileStreamingEvenWithoutADeclaredLength() throws Exception {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1024, Duration.ofMinutes(15)));
        UUID batch = fx.newBatch(fx.editor);
        byte[] big = new byte[4096];
        assertThatThrownBy(() -> fx.preservation().ingestion().receive(fx.editor,
                new IngestionService.Upload(batch, "big", "big.bin", null, null, null, null, null), -1,
                new ByteArrayInputStream(big)))
                .isInstanceOf(IngestionService.UploadTooLargeException.class);
        FileOperation op = fx.preservation().context().operations().findByBatchItem(batch, "big").orElseThrow();
        assertThat(op.state()).isEqualTo(OperationState.ABANDONED);
        assertThat(op.receivedBytes()).isLessThanOrEqualTo(1024 + 65536);
        assertThat(stagingFiles()).as("partial upload removed").isZero();
        assertThat(fx.preservation().context().files().byBatch(batch)).isEmpty();
    }

    @Test
    void b5_aDeclaredLengthOverTheLimitIsRefusedBeforeReading() {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1024, Duration.ofMinutes(15)));
        UUID batch = fx.newBatch(fx.editor);
        InputStream untouchable = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("the body must not be read");
            }
        };
        assertThatThrownBy(() -> fx.preservation().ingestion().receive(fx.editor,
                new IngestionService.Upload(batch, "big", "big.bin", null, null, null, null, null), 5000, untouchable))
                .isInstanceOf(IngestionService.UploadTooLargeException.class);
        assertThat(fx.preservation().context().operations().findByBatchItem(batch, "big")).isEmpty();
    }

    @Test
    void anUploadThatEndsEarlyIsAbandonedAndNothingIsArchived() throws Exception {
        UUID batch = fx.newBatch(fx.editor);
        FileOperation op = fx.preservation().ingestion().receive(fx.editor,
                new IngestionService.Upload(batch, "short", "short.bin", null, null, null, null, null), 100,
                new ByteArrayInputStream(new byte[40]));
        assertThat(op.state()).isEqualTo(OperationState.ABANDONED);
        assertThat(op.message()).contains("40 of 100");
        assertThat(stagingFiles()).isZero();
    }

    @Test
    void aBrokenConnectionAbandonsTheUploadAndRemovesStagedBytes() throws Exception {
        UUID batch = fx.newBatch(fx.editor);
        InputStream failing = new InputStream() {
            int n;

            @Override
            public int read() throws IOException {
                if (n++ > 10) {
                    throw new IOException("connection reset");
                }
                return 7;
            }
        };
        FileOperation op = fx.preservation().ingestion().receive(fx.editor,
                new IngestionService.Upload(batch, "x", "x.bin", null, null, null, null, null), -1, failing);
        assertThat(op.state()).isEqualTo(OperationState.ABANDONED);
        assertThat(stagingFiles()).isZero();
    }

    @Test
    void f009_derivativeRecordsItsParentAndMethodAndInheritsRestriction() {
        ArchivalFile parent = fx.ingest(fx.admin, "deed.tif", bytes("deed master"));
        fx.preservation().files().correct(fx.admin, parent.fileId(), parent.version(), parent.managedFilename(), 0,
                Restriction.RESTRICTED, "living person named");
        UUID batch = fx.newBatch(fx.admin);
        FileOperation op = fx.process(fx.receive(fx.admin, batch, "d1", "deed-cropped.jpg", bytes("deed crop"),
                Restriction.NORMAL, parent.fileId(), "cropped in an external editor").operationId());
        ArchivalFile child = fx.file(op.fileId());
        assertThat(child.restriction()).as("restricted parent => restricted derivative").isEqualTo(Restriction.RESTRICTED);
        Derivation d = fx.preservation().context().files().derivation(child.fileId()).orElseThrow();
        assertThat(d.parentFileId()).isEqualTo(parent.fileId());
        assertThat(d.method()).isEqualTo("cropped in an external editor");
        assertThat(child.contentId()).as("the parent's bytes are untouched").isNotEqualTo(parent.contentId());
    }

    @Test
    void aDerivativeNeedsADescriptionOfHowItWasMade() {
        ArchivalFile parent = fx.ingest(fx.editor, "p.tif", bytes("p"));
        UUID batch = fx.newBatch(fx.editor);
        assertThatThrownBy(() -> fx.receive(fx.editor, batch, "d", "d.jpg", bytes("d"), Restriction.NORMAL,
                parent.fileId(), " ")).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void anEditorCannotDeriveFromARestrictedFileTheyCannotSee() {
        ArchivalFile parent = fx.ingest(fx.admin, "r.tif", bytes("restricted"));
        fx.preservation().files().correct(fx.admin, parent.fileId(), parent.version(), "r.tif", 0, Restriction.RESTRICTED, null);
        UUID batch = fx.newBatch(fx.editor);
        assertThatThrownBy(() -> fx.receive(fx.editor, batch, "d", "d.jpg", bytes("d"), Restriction.NORMAL,
                parent.fileId(), "crop")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void p2c_uploadsAreRefusedVisiblyWhenNoStagingIsConfigured() {
        fx.withSettings(new FileSettings(Optional.empty(), Optional.empty(), 1024, Duration.ofMinutes(15)));
        var readiness = fx.preservation().ingestion().readiness(fx.editor);
        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.explanation()).contains("stagingDirectory");
        assertThatThrownBy(() -> fx.preservation().ingestion().startBatch(fx.editor, null))
                .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void viewersCannotUpload() {
        assertThatThrownBy(() -> fx.preservation().ingestion().startBatch(fx.viewer, null))
                .isInstanceOf(org.genealogyarchive.access.AccessDeniedException.class);
    }

    @Test
    void originalFilenameKeepsOnlyTheFinalComponentAndRejectsControlCharacters() {
        assertThat(IngestionService.originalFilename("C:\\Users\\me\\scan 01.tif")).isEqualTo("scan 01.tif");
        assertThat(IngestionService.originalFilename("dir/sub/Ölgemälde.jpg")).isEqualTo("Ölgemälde.jpg");
        assertThatThrownBy(() -> IngestionService.originalFilename("bad\u0001.tif")).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> IngestionService.originalFilename("folder/")).isInstanceOf(RuleViolationException.class);
    }
}
