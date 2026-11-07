package org.genealogyarchive.preservation;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.genealogyarchive.testsupport.ScriptedFaults;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for the independent Phase 2 audit (GAS-P2-001, -003 … -011). Each test reproduces the
 * audit's failure scenario and asserts the corrected behaviour. GAS-P2-002 is WorkerProcessKillIT.
 */
class Phase2AuditCorrectionsIT {

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

    private static byte[] large(String head) {
        byte[] h = b(head);
        byte[] out = new byte[2_600_000];
        for (int i = 0; i < out.length; i++) {
            out[i] = i < h.length ? h[i] : (byte) (i * 13 + 7);
        }
        return out;
    }

    private ArchivalFile ingestAs(Actor actor, String name, byte[] bytes, Restriction restriction, UUID parent, String method) {
        UUID batch = fx.newBatch(actor);
        FileOperation op = fx.process(fx.receive(actor, batch, fx.nextKey(), name, bytes, restriction, parent, method).operationId());
        assertThat(op.state()).as(op.message()).isEqualTo(OperationState.COMPLETED);
        return fx.file(op.fileId());
    }

    private ArchivalFile restrict(ArchivalFile f) {
        ArchivalFile now = fx.file(f.fileId());
        return fx.preservation().files().correct(fx.admin, f.fileId(), now.version(), now.managedFilename(), 0,
                Restriction.RESTRICTED, "concerns a living person");
    }

    private List<ChangeRecord> recent(Actor actor) {
        return fx.services().history().recent(actor, 500);
    }

    private static String allText(List<ChangeRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (ChangeRecord r : records) {
            sb.append(r.objectId()).append(' ').append(r.reason()).append('\n');
            for (FieldChange f : r.fieldChanges()) {
                sb.append(f.field()).append('=').append(f.before()).append("->").append(f.after()).append('\n');
            }
        }
        return sb.toString();
    }

    private FileContent content(ArchivalFile f) {
        return fx.preservation().context().contents().find(f.contentId()).orElseThrow();
    }

    private Path onVolume(String relative) {
        return PortableRelativePath.of(relative).toNative(fx.volumeRoot);
    }

    private List<Path> filesUnder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted((x, y) -> y.getNameCount() - x.getNameCount()).toList()) {
                if (Files.isRegularFile(p)) {
                    SafeFiles.clearReadOnly(p);
                }
                Files.delete(p);
            }
        }
    }

    // ---- GAS-P2-003: Change History ---------------------------------------------------------------

    @Test
    void gasP2_003_aFileRestrictedAtIngestionNeverAppearsInGlobalHistoryForOthers() {
        ArchivalFile f = ingestAs(fx.admin, "living-person-birth.jpg", b("restricted at ingestion"), Restriction.RESTRICTED,
                null, null);
        for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
            List<ChangeRecord> seen = recent(reader);
            assertThat(seen).noneMatch(r -> f.fileId().equals(r.objectId()));
            assertThat(allText(seen)).doesNotContain(f.fileId().toString(), f.friendlyId(), "living-person-birth",
                    content(f).sha256());
            assertThat(fx.services().history().forObject(reader, "archival_file", f.fileId())).isEmpty();
        }
        assertThat(recent(fx.admin)).anyMatch(r -> f.fileId().equals(r.objectId()));
    }

    @Test
    void gasP2_003_restrictingAFileLaterHidesItsEarlierHistory() {
        ArchivalFile f = ingestAs(fx.editor, "aunt-mary-1990.jpg", b("restricted later"), Restriction.NORMAL, null, null);
        assertThat(recent(fx.viewer)).anyMatch(r -> f.fileId().equals(r.objectId()));
        restrict(f);
        List<ChangeRecord> seen = recent(fx.viewer);
        assertThat(seen).noneMatch(r -> f.fileId().equals(r.objectId()));
        assertThat(allText(seen)).doesNotContain("aunt-mary-1990", f.fileId().toString(), f.friendlyId());
        assertThat(fx.services().history().forObject(fx.editor, "archival_file", f.fileId())).isEmpty();
    }

    @Test
    void gasP2_003_visibleHistoryDoesNotReferToARestrictedFile() {
        byte[] bytes = b("one scan, two Files");
        ArchivalFile first = ingestAs(fx.editor, "first.jpg", bytes, Restriction.NORMAL, null, null);
        ArchivalFile second = ingestAs(fx.editor, "second.jpg", bytes, Restriction.NORMAL, null, null);
        assertThat(second.contentId()).isEqualTo(first.contentId());
        ArchivalFile spare = ingestAs(fx.editor, "spare.jpg", b("spare"), Restriction.NORMAL, null, null);
        ArchivalFile deleted = fx.preservation().files().delete(fx.editor, spare.fileId(), spare.version(), null);
        fx.preservation().files().destroy(fx.admin, spare.fileId(), deleted.version(),
                "duplicate of " + first.friendlyId(), first.fileId());

        restrict(first);

        String seen = allText(recent(fx.viewer));
        assertThat(seen).as("replacement reference, reason, and shared-content reference are withheld")
                .doesNotContain(first.fileId().toString(), first.friendlyId(), first.contentId().toString());
        assertThat(recent(fx.viewer)).anyMatch(r -> spare.fileId().equals(r.objectId()) && "DESTROYED".equals(r.changeKind()))
                .anyMatch(r -> second.fileId().equals(r.objectId()) && "CREATED".equals(r.changeKind()));
        assertThat(allText(recent(fx.admin))).contains(first.fileId().toString(), first.friendlyId());
    }

    @Test
    void gasP2_003_anEditorsOwnUploadStatusDoesNotPointAtAFileRestrictedSince() {
        UUID batch = fx.newBatch(fx.editor);
        FileOperation op = fx.process(fx.receive(fx.editor, batch, "k", "upload.jpg", b("mine"), Restriction.NORMAL,
                null, null).operationId());
        restrict(fx.file(op.fileId()));
        assertThat(fx.preservation().ingestion().operation(fx.editor, op.operationId()).fileId()).isNull();
        assertThat(fx.preservation().ingestion().batchItems(fx.editor, batch)).allMatch(o -> o.fileId() == null);
        assertThat(fx.preservation().ingestion().operation(fx.admin, op.operationId()).fileId()).isEqualTo(op.fileId());
    }

    // ---- GAS-P2-004: restriction inheritance ------------------------------------------------------

    @Test
    void gasP2_004_restrictingAParentRestrictsExistingDerivativesAtEveryLevel() {
        ArchivalFile parent = ingestAs(fx.editor, "deed.tif", b("deed"), Restriction.NORMAL, null, null);
        ArchivalFile child = ingestAs(fx.editor, "deed-crop.jpg", b("crop"), Restriction.NORMAL, parent.fileId(), "cropped");
        ArchivalFile grandchild = ingestAs(fx.editor, "deed-crop-small.jpg", b("small"), Restriction.NORMAL,
                child.fileId(), "resized");

        restrict(parent);

        for (ArchivalFile f : List.of(child, grandchild)) {
            assertThat(fx.file(f.fileId()).restriction()).isEqualTo(Restriction.RESTRICTED);
            assertThatThrownBy(() -> fx.preservation().files().get(fx.viewer, f.fileId())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> fx.preservation().files().download(fx.editor, f.fileId())).isInstanceOf(NotFoundException.class);
            assertThat(fx.services().history().forObject(fx.admin, "archival_file", f.fileId()))
                    .as("the inherited restriction is attributed")
                    .anyMatch(r -> "Inherited: a File it was derived from was restricted.".equals(r.reason()));
        }
        assertThat(fx.preservation().files().list(fx.editor, true, 50)).isEmpty();
    }

    @Test
    void gasP2_004_aDerivativeReceivedBeforeItsParentWasRestrictedIsCommittedRestricted() {
        ArchivalFile parent = ingestAs(fx.editor, "letter.tif", b("letter"), Restriction.NORMAL, null, null);
        UUID batch = fx.newBatch(fx.editor);
        FileOperation staged = fx.receive(fx.editor, batch, "d", "letter-crop.jpg", b("letter crop"), Restriction.NORMAL,
                parent.fileId(), "cropped");
        assertThat(staged.requestedRestriction()).isEqualTo(Restriction.NORMAL);

        restrict(parent);
        FileOperation done = fx.process(staged.operationId());

        assertThat(done.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(fx.file(done.fileId()).restriction()).isEqualTo(Restriction.RESTRICTED);
    }

    @Test
    void gasP2_004_removingAnInheritedRestrictionStaysDeliberate() throws SQLException {
        ArchivalFile parent = ingestAs(fx.admin, "p.tif", b("p"), Restriction.NORMAL, null, null);
        ArchivalFile child = ingestAs(fx.admin, "c.jpg", b("c"), Restriction.NORMAL, parent.fileId(), "crop");
        ArchivalFile grandchild = ingestAs(fx.admin, "g.jpg", b("g"), Restriction.NORMAL, child.fileId(), "resize");
        restrict(parent);

        ArchivalFile g = fx.file(grandchild.fileId());
        assertThatThrownBy(() -> fx.preservation().files().correct(fx.admin, g.fileId(), g.version(), "g.jpg", 0,
                Restriction.NORMAL, null)).isInstanceOf(RuleViolationException.class);
        try (Connection c = fx.db.connect(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.executeUpdate("UPDATE gas.archival_file SET restriction = 'NORMAL' WHERE file_id = '"
                    + grandchild.fileId() + "'")).as("the database refuses it too").isInstanceOf(SQLException.class);
        }

        // Lowering the parent leaves its derivatives restricted until each is lowered deliberately.
        ArchivalFile p = fx.file(parent.fileId());
        fx.preservation().files().correct(fx.admin, p.fileId(), p.version(), "p.tif", 0, Restriction.NORMAL, "cleared");
        assertThat(fx.file(child.fileId()).restricted()).isTrue();
        ArchivalFile c2 = fx.file(child.fileId());
        fx.preservation().files().correct(fx.admin, c2.fileId(), c2.version(), "c.jpg", 0, Restriction.NORMAL, "cleared");
        assertThat(fx.file(child.fileId()).restricted()).isFalse();
        assertThat(fx.file(grandchild.fileId()).restricted()).isTrue();
    }

    // ---- GAS-P2-005: duplicate sharing ------------------------------------------------------------

    @Test
    void gasP2_005_sharingAnUnprotectedOriginalRestoresProtectionBeforeReportingSuccess() throws Exception {
        byte[] bytes = b("census page, unprotected later");
        ArchivalFile first = ingestAs(fx.editor, "census.tif", bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(first);
        SafeFiles.clearReadOnly(stored);

        FileOperation second = fx.process(fx.receive(fx.editor, "census-again.tif", bytes).operationId());

        assertThat(second.outcome()).isEqualTo("SHARED_CONTENT");
        assertThat(second.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(SafeFiles.isReadOnly(stored)).isTrue();
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 10)).extracting(IntegrityFinding::kind)
                .containsExactly(FindingKind.NOT_PROTECTED);
    }

    @Test
    void gasP2_005_ifProtectionCannotBeRestoredTheUploadBlocksAndIsNotArchived() throws Exception {
        byte[] bytes = b("cannot be protected");
        ArchivalFile first = ingestAs(fx.editor, "a.tif", bytes, Restriction.NORMAL, null, null);
        SafeFiles.clearReadOnly(fx.storedPath(first));
        fx.faults.ioFailureAt(FaultPoints.Point.BEFORE_PROTECT);

        FileOperation second = fx.process(fx.receive(fx.editor, "b.tif", bytes).operationId());

        assertThat(second.state()).isEqualTo(OperationState.BLOCKED);
        assertThat(second.blockedReason()).isEqualTo(BlockedReason.PROTECTION);
        assertThat(second.fileId()).isNull();
        assertThat(fx.preservation().context().files().byContent(first.contentId())).hasSize(1);
    }

    // ---- GAS-P2-006: destruction ------------------------------------------------------------------

    private record Repaired(ArchivalFile file, UUID repairOperation) {
        UUID fileId() {
            return file.fileId();
        }

        UUID contentId() {
            return file.contentId();
        }
    }

    private Repaired corruptAndRepair(String name, byte[] bytes) throws Exception {
        ArchivalFile f = ingestAs(fx.editor, name, bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        byte[] rotten = bytes.clone();
        rotten[0] ^= 1;
        Files.write(stored, rotten);
        fx.scan(fx.services(), "FULL");
        FileOperation repair = fx.preservation().repair().receive(fx.admin, f.contentId(), "good.tif", bytes.length,
                new ByteArrayInputStream(bytes));
        return new Repaired(f, repair.operationId());
    }

    private FileOperation destroy(ArchivalFile f) {
        ArchivalFile now = fx.file(f.fileId());
        ArchivalFile deleted = fx.preservation().files().delete(fx.editor, f.fileId(), now.version(), null);
        return fx.preservation().files().destroy(fx.admin, f.fileId(), deleted.version(), "uploaded in error", null);
    }

    @Test
    void gasP2_006_destroyingAfterARepairRemovesTheQuarantinedGenerationToo() throws Exception {
        Repaired f = corruptAndRepair("will.tif", b("last will and testament"));
        fx.process(f.repairOperation());
        FileContentRepository.Generation damaged = fx.preservation().context().contents().generations(f.contentId())
                .stream().filter(g -> g.generation() == 1).findFirst().orElseThrow();
        assertThat(damaged.state()).isEqualTo(GenerationState.QUARANTINED);
        Path quarantined = onVolume(damaged.quarantinePath());
        assertThat(quarantined).exists();

        FileOperation op = destroy(f.file());
        fx.preservation().destruction().process(op.operationId());

        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(quarantined).doesNotExist();
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.preservation().context().contents().generations(f.contentId()))
                .allMatch(g -> g.state() == GenerationState.DESTROYED)
                .anyMatch(g -> damaged.quarantinePath().equals(g.quarantinePath()));
    }

    @Test
    void gasP2_006_destructionFinishesAndRemovesAQuarantineMoveThatWasInterrupted() throws Exception {
        Repaired f = corruptAndRepair("deed.tif", b("deed of sale 1802"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        List<QuarantineRepository.Entry> ledger = fx.preservation().context().quarantine().forContent(f.contentId());
        assertThat(ledger).singleElement().satisfies(e -> {
            assertThat(e.state()).isEqualTo(QuarantineRepository.State.INTENDED);
            assertThat(onVolume(e.quarantinePath())).exists();
        });

        FileOperation op = destroy(f.file());
        fx.preservation().destruction().process(op.operationId());

        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.preservation().context().quarantine().forContent(f.contentId()))
                .allMatch(e -> e.state() == QuarantineRepository.State.REMOVED);
    }

    @Test
    void gasP2_006_destructionRemovesQuarantinedPartialCopiesOfTheContent() throws Exception {
        byte[] bytes = large("partial then destroyed");
        UUID id = fx.receive(fx.editor, "p.tif", bytes).operationId();
        fx.faults.crashAt(FaultPoints.Point.DURING_COPY);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        fx.preservation().recovery().runOnce();
        FileOperation done = fx.operation(id);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).hasSize(1);

        FileOperation op = destroy(fx.file(done.fileId()));
        fx.preservation().destruction().process(op.operationId());

        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
    }

    // ---- GAS-P2-007: missing directories ----------------------------------------------------------

    @Test
    void gasP2_007_aLostShardDirectoryIsMissingContentAndCanBeRepaired() throws Exception {
        byte[] bytes = b("baptism register, 1788");
        ArchivalFile f = ingestAs(fx.editor, "baptism.tif", bytes, Restriction.NORMAL, null, null);
        deleteTree(fx.storedPath(f).getParent());

        assertThat(fx.scan(fx.services(), "QUICK")).extracting(IntegrityFinding::kind).containsExactly(FindingKind.MISSING);
        assertThat(content(f).state()).isEqualTo(ContentState.MISSING);

        FileOperation repair = fx.preservation().repair().receive(fx.admin, f.contentId(), "backup.tif", bytes.length,
                new ByteArrayInputStream(bytes));
        assertThat(fx.process(repair.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
        try (var d = fx.preservation().files().download(fx.editor, f.fileId())) {
            assertThat(d.stream().readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void gasP2_007_aLostOriginalsDirectoryIsMissingContentAndCanBeRepaired() throws Exception {
        byte[] bytes = b("marriage bond");
        ArchivalFile f = ingestAs(fx.editor, "bond.tif", bytes, Restriction.NORMAL, null, null);
        deleteTree(fx.volumeRoot.resolve("originals"));

        assertThatThrownBy(() -> fx.preservation().files().download(fx.editor, f.fileId()))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("missing");
        assertThat(content(f).state()).as("a download that finds it missing records it").isEqualTo(ContentState.MISSING);
        assertThat(fx.scan(fx.services(), "QUICK")).extracting(IntegrityFinding::kind).containsExactly(FindingKind.MISSING);

        FileOperation repair = fx.preservation().repair().receive(fx.admin, f.contentId(), "backup.tif", bytes.length,
                new ByteArrayInputStream(bytes));
        assertThat(fx.process(repair.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(Files.readAllBytes(fx.storedPath(f))).isEqualTo(bytes);
    }

    // ---- GAS-P2-008: partial-copy quarantine accounting -------------------------------------------

    private UUID partialCopy(String name, byte[] bytes) {
        UUID id = fx.receive(fx.editor, name, bytes).operationId();
        fx.faults.crashAt(FaultPoints.Point.DURING_COPY);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        return id;
    }

    private void assertEveryQuarantinedObjectIsAccounted(UUID operationId) throws IOException {
        List<QuarantineRepository.Entry> ledger = fx.preservation().context().quarantine().forOperation(operationId);
        List<Path> onDisk = filesUnder(fx.volumeRoot.resolve("quarantine"));
        assertThat(onDisk).hasSize(1);
        assertThat(ledger).singleElement().satisfies(e -> {
            assertThat(e.state()).isEqualTo(QuarantineRepository.State.QUARANTINED);
            assertThat(e.reason()).isEqualTo(QuarantineRepository.Reason.PARTIAL_COPY);
            assertThat(onVolume(e.quarantinePath())).isEqualTo(onDisk.get(0));
        });
    }

    @Test
    void gasP2_008_aStopAfterThePartialCopyMovedButBeforeItWasRecordedIsReconciled() throws Exception {
        byte[] bytes = large("move recorded only as intent");
        UUID id = partialCopy("m.tif", bytes);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.preservation().context().quarantine().forOperation(id)).singleElement()
                .satisfies(e -> assertThat(e.state()).isEqualTo(QuarantineRepository.State.INTENDED));

        fx.rebuild();
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.COMPLETED);
        assertEveryQuarantinedObjectIsAccounted(id);

        int events = fx.preservation().context().operations().events(id).size();
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertThat(fx.preservation().context().operations().events(id)).as("repeated recovery is idempotent").hasSize(events);
        assertEveryQuarantinedObjectIsAccounted(id);
        assertThat(fx.preservation().context().files().byBatch(fx.operation(id).batchId())).hasSize(1);
        assertThat(Files.readAllBytes(fx.storedPath(fx.file(fx.operation(id).fileId())))).isEqualTo(bytes);
    }

    @Test
    void gasP2_008_aStopAfterTheMoveWasRecordedButBeforeTheOperationWasReplannedIsReconciled() throws Exception {
        byte[] bytes = large("move recorded, operation not replanned");
        UUID id = partialCopy("r.tif", bytes);
        String oldTarget = fx.operation(id).targetPath();
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_RECORDED);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(id).targetPath()).as("the operation still points at the old target").isEqualTo(oldTarget);

        fx.rebuild();
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.COMPLETED);
        assertEveryQuarantinedObjectIsAccounted(id);
        assertThat(fx.scan(fx.services(), "FULL")).isEmpty();
    }

    @Test
    void gasP2_008_aDatabaseFailureAfterTheMoveIsRetriedWithoutLosingTheRecord() throws Exception {
        byte[] bytes = large("io failure after move");
        UUID id = partialCopy("f.tif", bytes);
        fx.faults.ioFailureAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(OperationProcessor.FileOperationFailedException.class);
        fx.process(id);
        fx.process(id);
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.COMPLETED);
        assertEveryQuarantinedObjectIsAccounted(id);
    }

    // ---- GAS-P2-009: cancellation -----------------------------------------------------------------

    @Test
    void gasP2_009_cancellingAWaitingOperationNeverMovesAnObjectItDidNotCreate() throws Exception {
        UUID id = fx.receive(fx.editor, "w.tif", b("waiting")).operationId();
        fx.servicesWithRoot(temp.resolve("away")).preservation().processor().process(id, () -> { });
        FileOperation waiting = fx.operation(id);
        assertThat(waiting.state()).isEqualTo(OperationState.WAITING_FOR_VOLUME);
        assertThat(waiting.attempts()).isZero();
        Path target = onVolume(waiting.targetPath());
        Files.createDirectories(target.getParent());
        Files.writeString(target, "unrelated object");

        fx.preservation().operations().cancel(fx.admin, id);
        assertThat(fx.preservation().operations().performCancel(id, "admin1")).isTrue();

        FileOperation cancelled = fx.operation(id);
        assertThat(cancelled.state()).isEqualTo(OperationState.CANCELLED);
        assertThat(Files.readString(target)).isEqualTo("unrelated object");
        assertThat(cancelled.quarantinePath()).isNull();
        assertThat(cancelled.message()).contains("left untouched");
        assertThat(fx.preservation().context().quarantine().forOperation(id)).isEmpty();
        assertThat(fx.scan(fx.services(), "QUICK")).extracting(IntegrityFinding::kind)
                .as("and it is reported").containsExactly(FindingKind.UNEXPECTED_OBJECT);
    }

    @Test
    void gasP2_009_aCollisionStaysUntouchedAcrossRetriesAndCancellation() throws Exception {
        UUID id = fx.receive(fx.editor, "c.tif", b("collision")).operationId();
        fx.servicesWithRoot(temp.resolve("away")).preservation().processor().process(id, () -> { });
        Path target = onVolume(fx.operation(id).targetPath());
        Files.createDirectories(target.getParent());
        Files.writeString(target, "foreign");
        fx.process(id);
        assertThat(fx.operation(id).blockedReason()).isEqualTo(BlockedReason.COLLISION);
        fx.process(id);
        assertThat(fx.operation(id).blockedReason()).isEqualTo(BlockedReason.COLLISION);
        fx.preservation().operations().cancel(fx.admin, id);
        fx.preservation().operations().performCancel(id, "admin1");
        assertThat(Files.readString(target)).isEqualTo("foreign");
    }

    // ---- GAS-P2-001: receiver liveness --------------------------------------------------------------

    /** An input stream that blocks for {@code pauseMillis} before its first byte and once more midway. */
    private static InputStream blocking(int total, long pauseMillis) {
        return new InputStream() {
            int sent;

            @Override
            public int read() throws IOException {
                if (sent >= total) {
                    return -1;
                }
                if (sent == 0 || sent == total / 2) {
                    try {
                        Thread.sleep(pauseMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                }
                sent++;
                return 'x';
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int c = read();
                if (c < 0) {
                    return -1;
                }
                b[off] = (byte) c;
                return 1;
            }
        };
    }

    @Test
    void gasP2_001_aLiveReceiverBlockedLongerThanTheTimeoutKeepsItsUpload() throws Exception {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1L << 30, Duration.ofSeconds(2)));
        UUID batch = fx.newBatch(fx.editor);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger abandoned = new AtomicInteger();
        CompletableFuture<Void> recovery = CompletableFuture.runAsync(() -> {
            while (running.get()) {
                abandoned.addAndGet(fx.preservation().recovery().runOnce().abandoned());
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        // Blocks 4.5 s before the first byte and 4.5 s midway: no byte progress for more than twice the timeout.
        FileOperation op = fx.preservation().ingestion().receive(fx.editor,
                new IngestionService.Upload(batch, "blocked", "blocked.txt", null, null, null, null, null), 10,
                blocking(10, 4500));
        running.set(false);
        recovery.get(10, TimeUnit.SECONDS);

        assertThat(abandoned.get()).isZero();
        assertThat(op.state()).isEqualTo(OperationState.STAGED);
        assertThat(fx.process(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
    }

    @Test
    void gasP2_001_aDeadReceiverIsAbandonedAndCleanedOnceItsLeaseLapses() throws Exception {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1L << 30, Duration.ofSeconds(2)));
        fx.faults.crashAt(FaultPoints.Point.AFTER_STAGING_WRITE);
        UUID batch = fx.newBatch(fx.editor);
        assertThatThrownBy(() -> fx.receive(fx.editor, batch, "dead", "d.txt", b("dead receiver"), Restriction.NORMAL,
                null, null)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        UUID id = fx.preservation().context().operations().findByBatchItem(batch, "dead").orElseThrow().operationId();
        assertThat(fx.preservation().recovery().runOnce().abandoned()).isZero();
        Thread.sleep(3000);
        assertThat(fx.preservation().recovery().runOnce().abandoned()).isEqualTo(1);
        FileOperation op = fx.operation(id);
        assertThat(op.state()).isEqualTo(OperationState.ABANDONED);
        assertThat(op.message()).contains("receiving this upload stopped");
        assertThat(filesUnder(fx.staging)).isEmpty();
    }

    // ---- GAS-P2-010: required volumes ---------------------------------------------------------------

    @Test
    void gasP2_010_anEmptyRequiredVolumeThatIsNotTheIngestionDestinationIsScanned() throws Exception {
        UUID spare = fx.services().volumes().register(Actor.commandLine(), "Spare", "primary", null, true).volumeId();
        fx.ingest(fx.editor, "a.tif", b("on the primary volume"));

        CoreServices unavailable = fx.servicesWithRoots(Map.of(fx.volumeId, fx.volumeRoot, spare, temp.resolve("unplugged")));
        assertThat(fx.scan(unavailable, "QUICK")).singleElement().satisfies(x -> {
            assertThat(x.kind()).isEqualTo(FindingKind.VOLUME_UNAVAILABLE);
            assertThat(x.volumeId()).isEqualTo(spare);
        });

        Path spareRoot = Files.createDirectories(temp.resolve("spare"));
        ManagedVolume.initialize(spareRoot, fx.services().archives().view(fx.admin).archiveId(), spare, java.time.Instant.now());
        Files.createDirectories(spareRoot.resolve("originals"));
        Files.writeString(spareRoot.resolve("originals").resolve("stray.bin"), "copied in by hand");
        CoreServices available = fx.servicesWithRoots(Map.of(fx.volumeId, fx.volumeRoot, spare, spareRoot));
        assertThat(fx.scan(available, "QUICK")).extracting(IntegrityFinding::kind)
                .contains(FindingKind.UNEXPECTED_OBJECT).doesNotContain(FindingKind.MISSING);
    }

    // ---- GAS-P2-011: repeated scans -----------------------------------------------------------------

    @Test
    void gasP2_011_aRepeatedScanReportsDamageThatIsStillUnresolved() throws Exception {
        byte[] bytes = b("probate inventory");
        ArchivalFile f = ingestAs(fx.editor, "p.tif", bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        byte[] rotten = bytes.clone();
        rotten[2] ^= 4;
        Files.write(stored, rotten);
        SafeFiles.makeReadOnly(stored);

        List<IntegrityFinding> first = fx.scan(fx.services(), "FULL");
        assertThat(first).extracting(IntegrityFinding::kind).containsExactly(FindingKind.HASH_MISMATCH);
        UUID findingId = first.get(0).findingId();

        for (String kind : List.of("QUICK", "FULL")) {
            IntegrityScan again = fx.preservation().integrity().requestScan(fx.admin, kind);
            fx.preservation().integrity().run(again.scanId());
            IntegrityScan result = fx.preservation().integrity().scan(fx.admin, again.scanId());
            assertThat(result.message()).as(kind).doesNotContain("No problems found").contains("still unresolved");
            assertThat(result.findingsCount()).isEqualTo(1);
            assertThat(fx.preservation().integrity().findingsOfScan(fx.admin, again.scanId()))
                    .extracting(IntegrityFinding::findingId).containsExactly(findingId);
        }
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 10)).as("no duplicate rows").hasSize(1);
    }

    // ---- GAS-P2-R01 (re-audit): destruction and uncommitted repair copies ------------------------------

    /** A repair whose copy was written, protected and verified, interrupted before its database commit. */
    private FileOperation repairStoppedBeforeCommit(Repaired f) {
        fx.faults.crashAt(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.state()).isEqualTo(OperationState.PROMOTED);
        assertThat(repair.targetCreated()).isTrue();
        assertThat(onVolume(repair.targetPath())).exists();
        assertThat(fx.preservation().context().contents().generations(f.contentId()))
                .noneMatch(g -> g.generation() == repair.targetGeneration());
        return repair;
    }

    private void assertDestroyedWithoutTrace(Repaired f, UUID destruction, FileOperation repair) throws IOException {
        FileOperation done = fx.operation(destruction);
        assertThat(done.state()).as(done.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(done.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(onVolume(repair.targetPath())).as("the uncommitted repair copy").doesNotExist();
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        FileOperation stopped = fx.operation(repair.operationId());
        assertThat(stopped.state()).isEqualTo(OperationState.CANCELLED);
        assertThat(stopped.message()).contains("permanently destroyed", repair.targetPath());
        assertThat(stopped.stagingCleaned()).isTrue();
        assertThat(content(f.file()).state()).isEqualTo(ContentState.DESTROYED);
        assertThat(fx.preservation().context().contents().generations(f.contentId()))
                .as("the repair never became a generation").noneMatch(g -> g.generation() == repair.targetGeneration())
                .allMatch(g -> g.state() == GenerationState.DESTROYED);
        assertThat(fx.preservation().context().quarantine().forContent(f.contentId()))
                .allMatch(e -> e.state() == QuarantineRepository.State.REMOVED
                        || e.state() == QuarantineRepository.State.SOURCE_ABSENT);
    }

    @Test
    void gasP2_R01_aRepairCopyVerifiedButNotCommittedIsRemovedByDestructionAndRecovery() throws Exception {
        Repaired f = corruptAndRepair("survey.tif", b("land survey, 1791"));
        FileOperation repair = repairStoppedBeforeCommit(f);

        FileOperation op = destroy(f.file());
        fx.preservation().recovery().runOnce();
        assertDestroyedWithoutTrace(f, op.operationId(), repair);

        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            fx.preservation().destruction().process(op.operationId());
            fx.process(repair.operationId());
        }
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
    }

    @Test
    void gasP2_R01_cleanupInterruptedBeforeAndDuringRemovalIsFinishedByRepeatedRecovery() throws Exception {
        Repaired f = corruptAndRepair("plat.tif", b("plat of the mill tract"));
        FileOperation repair = repairStoppedBeforeCommit(f);
        Path damaged = onVolume(fx.preservation().context().contents().generations(f.contentId()).get(0).storagePath());
        FileOperation op = destroy(f.file());

        // Stop after the repair was stopped, before anything was removed.
        fx.faults.crashAt(FaultPoints.Point.BEFORE_PHYSICAL_DELETE);
        assertThatThrownBy(() -> fx.preservation().destruction().process(op.operationId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(repair.operationId()).state()).isEqualTo(OperationState.CANCELLED);
        assertThat(onVolume(repair.targetPath())).exists();
        assertThat(damaged).exists();
        assertThat(fx.operation(op.operationId()).state()).isNotEqualTo(OperationState.COMPLETED);

        // Stop again after the first copy was removed, with the other still on the volume.
        fx.faults.crashAt(FaultPoints.Point.DURING_PHYSICAL_DELETE);
        assertThatThrownBy(() -> fx.preservation().destruction().process(op.operationId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(Stream.of(damaged, onVolume(repair.targetPath())).filter(Files::exists)).hasSize(1);
        assertThat(fx.operation(op.operationId()).state()).as("never complete while a known copy remains")
                .isNotEqualTo(OperationState.COMPLETED);
        assertThat(fx.operation(op.operationId()).outcome()).isNotEqualTo("BYTES_REMOVED");

        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
    }

    @Test
    void gasP2_R01_removalWaitsWhileTheRepairIsBusyElsewhere() throws Exception {
        // IV03: an unfinished repair is now stopped by the destruction intent itself, so removal can only meet
        // a busy repair that had already committed (finishing the quarantine of the damaged generation).
        Repaired f = corruptAndRepair("bible.tif", b("family bible register"));
        fx.faults.crashAt(FaultPoints.Point.BEFORE_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.state()).isEqualTo(OperationState.COMMITTED);
        FileOperation op = destroy(f.file());
        assertThat(fx.operation(repair.operationId()).state()).as("a committed repair is not cancelled")
                .isEqualTo(OperationState.COMMITTED);

        try (var busy = fx.preservation().context().locks().tryLock("file-operation", repair.operationId()).orElseThrow()) {
            fx.preservation().destruction().process(op.operationId());
            FileOperation waiting = fx.operation(op.operationId());
            assertThat(waiting.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(waiting.message()).contains("waits for operation " + repair.operationId());
            assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).hasSize(2);
        }
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        FileOperation done = fx.operation(op.operationId());
        assertThat(done.state()).as(done.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(done.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.operation(repair.operationId()).state()).isEqualTo(OperationState.COMPLETED);
    }

    @Test
    void gasP2_R01_anObjectTheRepairNeverCreatedIsLeftUntouched() throws Exception {
        Repaired f = corruptAndRepair("muster.tif", b("militia muster roll"));
        fx.faults.crashAt(FaultPoints.Point.BEFORE_TARGET_CREATE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.state()).isEqualTo(OperationState.PROMOTING);
        assertThat(repair.targetCreated()).isFalse();
        Path foreign = onVolume(repair.targetPath());
        Files.createDirectories(foreign.getParent());
        Files.write(foreign, b("not written by any operation"));

        FileOperation op = destroy(f.file());
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();

        FileOperation stopped = fx.operation(repair.operationId());
        assertThat(stopped.state()).isEqualTo(OperationState.CANCELLED);
        assertThat(stopped.message()).contains("left untouched");
        assertThat(foreign).exists().hasBinaryContent(b("not written by any operation"));
        FileOperation done = fx.operation(op.operationId());
        assertThat(done.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(foreign);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    // ---- GAS-P2-R02 (re-audit): history authorization and File identifier spellings -------------------

    private static String abbreviated(UUID id) {
        String[] groups = id.toString().split("-");
        StringBuilder sb = new StringBuilder();
        for (String g : groups) {
            String stripped = g.replaceFirst("^0+(?=.)", "");
            sb.append(sb.isEmpty() ? "" : "-").append(stripped.toUpperCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    @Test
    void gasP2_R02_historyOfOtherObjectsDoesNotLeakARestrictedFileByAnyResolvableSpelling() {
        ArchivalFile secret = ingestAs(fx.editor, "living-cousin.jpg", b("restricted later, by spelling"), Restriction.NORMAL,
                null, null);
        long number = Long.parseLong(secret.friendlyId().substring(1));
        List<String> spellings = List.of(
                secret.friendlyId().toLowerCase(java.util.Locale.ROOT),             // f00001
                "F" + number,                                                         // F1
                "f" + number,                                                         // f1
                "F0" + number,                                                        // F01
                "copy of f" + number + ", see notes",                                 // embedded
                "(" + secret.friendlyId() + ")",                                      // canonical, embedded
                secret.fileId().toString().toUpperCase(java.util.Locale.ROOT),        // upper-case UUID
                abbreviated(secret.fileId()),                                         // abbreviated UUID
                "ref-" + secret.fileId() + "-end",                                    // embedded UUID
                "same bytes as " + secret.contentId());                               // content it uses
        String ordinary = "routine clean-up of folder F-1990 for Fairfax County";
        for (String spelling : spellings) {
            if (!spelling.contains(" ") && !spelling.startsWith("(") && !spelling.startsWith("ref-")
                    && !spelling.contains("-")) {
                assertThat(fx.preservation().files().getByFriendlyId(fx.admin, spelling).fileId())
                        .as("'%s' is a spelling the lookup resolves", spelling).isEqualTo(secret.fileId());
            }
        }
        assertThat(FileIdentifiers.uuid(abbreviated(secret.fileId()))).as("the web layer accepts the abbreviated UUID")
                .hasValue(secret.fileId());

        java.util.Map<String, UUID> carriers = new java.util.LinkedHashMap<>();
        for (String reason : java.util.stream.Stream.concat(spellings.stream(), Stream.of(ordinary)).toList()) {
            ArchivalFile other = ingestAs(fx.editor, "other.jpg", b("carrier " + reason), Restriction.NORMAL, null, null);
            fx.preservation().files().delete(fx.editor, other.fileId(), other.version(), reason);
            carriers.put(reason, other.fileId());
        }
        for (String reason : carriers.keySet()) {
            assertThat(fx.services().history().forObject(fx.viewer, "archival_file", carriers.get(reason)))
                    .as("before restriction nothing is withheld: %s", reason).anyMatch(r -> reason.equals(r.reason()));
        }

        restrict(secret);

        for (String spelling : spellings) {
            UUID carrier = carriers.get(spelling);
            for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
                List<ChangeRecord> own = fx.services().history().forObject(reader, "archival_file", carrier);
                assertThat(own).as("the other object's history stays visible").isNotEmpty();
                assertThat(own).as("'%s' withheld from its object's history", spelling).noneMatch(r -> spelling.equals(r.reason()));
                assertThat(recent(reader)).as("'%s' withheld from global history", spelling)
                        .noneMatch(r -> spelling.equals(r.reason()));
            }
            assertThat(fx.services().history().forObject(fx.admin, "archival_file", carrier))
                    .as("Administrators still see '%s'", spelling).anyMatch(r -> spelling.equals(r.reason()));
        }
        assertThat(fx.services().history().forObject(fx.viewer, "archival_file", carriers.get(ordinary)))
                .as("ordinary text is not redacted").anyMatch(r -> ordinary.equals(r.reason()));
        assertThatThrownBy(() -> fx.preservation().files().getByFriendlyId(fx.viewer, "f" + number))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- GAS-P2-FV01 (final verification): physical-copy accounting ---------------------------------------

    /**
     * Invariant 1, checked independently of any scenario: every object on the volume under originals/ or
     * quarantine/ is accounted for by a generation, the quarantine ledger, or an operation that owns it
     * (target_created) - unless it is one of the named foreign objects.
     */
    private void assertEveryCopyAccounted(UUID contentId, Path... foreign) throws IOException {
        var ctx = fx.preservation().context();
        java.util.Set<Path> accounted = new java.util.HashSet<>(List.of(foreign));
        for (FileContentRepository.Generation g : ctx.contents().generations(contentId)) {
            accounted.add(onVolume(g.storagePath()));
            if (g.quarantinePath() != null) {
                accounted.add(onVolume(g.quarantinePath()));
            }
        }
        for (QuarantineRepository.Entry e : ctx.quarantine().forContent(contentId)) {
            if (e.state() == QuarantineRepository.State.INTENDED) {
                accounted.add(onVolume(e.sourcePath()));
                accounted.add(onVolume(e.quarantinePath()));
            } else if (e.state() == QuarantineRepository.State.QUARANTINED) {
                accounted.add(onVolume(e.quarantinePath()));
            }
        }
        for (FileOperation w : ctx.operations().writersOfContent(contentId)) {
            if (w.targetCreated()) {
                accounted.add(onVolume(w.targetPath()));
            }
        }
        List<Path> onDisk = new java.util.ArrayList<>(filesUnder(fx.volumeRoot.resolve("originals")));
        onDisk.addAll(filesUnder(fx.volumeRoot.resolve("quarantine")));
        assertThat(accounted).as("every copy on the volume is accounted for").containsAll(onDisk);
    }

    /** Invariant 5: a completed destruction leaves no copy on the volume except named foreign objects. */
    private void assertNotCompleteWhileACopyRemains(UUID destruction, Path... foreign) throws IOException {
        FileOperation d = fx.operation(destruction);
        if (d.state() == OperationState.COMPLETED || "BYTES_REMOVED".equals(d.outcome())) {
            List<Path> onDisk = new java.util.ArrayList<>(filesUnder(fx.volumeRoot.resolve("originals")));
            onDisk.addAll(filesUnder(fx.volumeRoot.resolve("quarantine")));
            assertThat(onDisk).as("BYTES_REMOVED recorded while copies remain").containsExactlyInAnyOrder(foreign);
        }
    }

    /**
     * The audit's sequence: a repair's partial copy is moved to quarantine (interrupted at {@code moveStop}),
     * the next attempt creates a new copy at the very location that move vacated, verifies it and stops
     * before its commit.
     */
    private FileOperation recreatedAtTheVacatedTarget(Repaired f, FaultPoints.Point moveStop) throws IOException {
        fx.faults.crashAt(FaultPoints.Point.DURING_COPY);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        String vacated = fx.operation(f.repairOperation()).targetPath();
        assertEveryCopyAccounted(f.contentId());

        fx.faults.crashAt(moveStop);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertEveryCopyAccounted(f.contentId());

        fx.faults.crashAt(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.state()).isEqualTo(OperationState.PROMOTED);
        assertThat(repair.targetPath()).as("the new copy is at the location the move vacated").isEqualTo(vacated);
        assertThat(repair.targetCreated()).as("and the operation owns it").isTrue();
        assertThat(onVolume(vacated)).exists();
        assertThat(fx.preservation().context().quarantine().forOperation(repair.operationId()))
                .anyMatch(e -> e.sourcePath().equals(vacated) && e.state() == QuarantineRepository.State.QUARANTINED);
        assertEveryCopyAccounted(f.contentId());
        return repair;
    }

    @Test
    void gasP2_FV01_aCopyRecreatedWhereARecordedMoveVacatedIsRemovedByDestruction() throws Exception {
        Repaired f = corruptAndRepair("census.tif", large("1850 census, recreated after a recorded move"));
        FileOperation repair = recreatedAtTheVacatedTarget(f, FaultPoints.Point.AFTER_QUARANTINE_RECORDED);

        FileOperation op = destroy(f.file());
        fx.preservation().recovery().runOnce();
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            fx.process(repair.operationId());
            assertDestroyedWithoutTrace(f, op.operationId(), repair);
        }
    }

    @Test
    void gasP2_FV01_aCopyRecreatedAfterAnUnconfirmedMoveWasSettledIsRemovedByDestruction() throws Exception {
        Repaired f = corruptAndRepair("tax.tif", large("tax list, recreated after an unconfirmed move"));
        FileOperation repair = recreatedAtTheVacatedTarget(f, FaultPoints.Point.AFTER_QUARANTINE_MOVE);

        FileOperation op = destroy(f.file());
        fx.preservation().destruction().process(op.operationId());
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
    }

    @Test
    void gasP2_FV01_anUnconfirmedMoveSettledByDestructionLeavesNothingAndNoRepairWritesAgain() throws Exception {
        Repaired f = corruptAndRepair("deed2.tif", large("deed, move unconfirmed when destroyed"));
        fx.faults.crashAt(FaultPoints.Point.DURING_COPY);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.targetCreated()).as("the move is not yet confirmed").isTrue();
        assertEveryCopyAccounted(f.contentId());

        FileOperation op = destroy(f.file());
        // Recovery settles the move, then meets the repair before the destruction: it must not write again.
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(d.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.operation(repair.operationId()).state()).isEqualTo(OperationState.CANCELLED);
        assertThat(fx.preservation().context().quarantine().forContent(f.contentId()))
                .allMatch(e -> e.state() == QuarantineRepository.State.REMOVED
                        || e.state() == QuarantineRepository.State.SOURCE_ABSENT);
    }

    @Test
    void gasP2_FV01_anUnknownObjectAtAVacatedTargetIsNeitherAdoptedNorRemoved() throws Exception {
        Repaired f = corruptAndRepair("roll.tif", large("tithe roll, foreign object at a vacated target"));
        fx.faults.crashAt(FaultPoints.Point.DURING_COPY);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_RECORDED);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation vacated = fx.operation(f.repairOperation());
        assertThat(vacated.targetCreated()).as("the confirmed move ended its ownership").isFalse();
        Path foreign = onVolume(vacated.targetPath());
        assertThat(foreign).doesNotExist();
        Files.write(foreign, b("placed here by hand"));
        assertEveryCopyAccounted(f.contentId(), foreign);

        FileOperation blocked = fx.process(f.repairOperation());
        assertThat(blocked.state()).isEqualTo(OperationState.BLOCKED);
        assertThat(blocked.blockedReason()).isEqualTo(BlockedReason.COLLISION);
        assertThat(foreign).hasBinaryContent(b("placed here by hand"));

        FileOperation op = destroy(f.file());
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            assertNotCompleteWhileACopyRemains(op.operationId(), foreign);
        }
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(foreign).hasBinaryContent(b("placed here by hand"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.operation(f.repairOperation()).message()).contains("left untouched");
    }

    @Test
    void gasP2_FV01_cleanupInterruptedAfterEachRemovalNeverReportsCompletionEarly() throws Exception {
        Repaired f = corruptAndRepair("will2.tif", large("will, interrupted at every removal"));
        FileOperation repair = recreatedAtTheVacatedTarget(f, FaultPoints.Point.AFTER_QUARANTINE_RECORDED);
        FileOperation op = destroy(f.file());
        // Known locations: the damaged generation, the quarantined partial copy and the recreated copy.
        for (int k = 1; k <= 3; k++) {
            // Stop right after the k-th location is removed.
            AtomicInteger reached = new AtomicInteger();
            int stopAt = k;
            fx.faults.armSticky(FaultPoints.Point.DURING_PHYSICAL_DELETE, id -> {
                if (reached.incrementAndGet() == stopAt) {
                    throw new ScriptedFaults.SimulatedCrash(FaultPoints.Point.DURING_PHYSICAL_DELETE);
                }
            });
            assertThatThrownBy(() -> fx.preservation().destruction().process(op.operationId()))
                    .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
            fx.faults.clear();
            assertThat(fx.operation(op.operationId()).state()).isNotEqualTo(OperationState.COMPLETED);
            assertNotCompleteWhileACopyRemains(op.operationId());
            assertEveryCopyAccounted(f.contentId());
        }
        fx.faults.clear();
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
    }

    // ---- GAS-P2-FV02 (final verification): no existence oracle through history --------------------------

    /** What a reader observes of a reason written on another object: shown or withheld, per-object and global. */
    private record Seen(boolean perObject, boolean global) {
    }

    private Seen seen(Actor reader, UUID object, String reason) {
        return new Seen(
                fx.services().history().forObject(reader, "archival_file", object).stream().anyMatch(r -> reason.equals(r.reason())),
                recent(reader).stream().anyMatch(r -> object.equals(r.objectId()) && reason.equals(r.reason())));
    }

    private UUID carrier(String reason) {
        ArchivalFile other = ingestAs(fx.editor, "carrier.jpg", b("carrier " + reason), Restriction.NORMAL, null, null);
        fx.preservation().files().delete(fx.editor, other.fileId(), other.version(), reason);
        return other.fileId();
    }

    private static String arabicDigits(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        }
        return sb.toString();
    }

    @Test
    void gasP2_FV02_aGuessedIdentifierLooksTheSameWhetherItsFileIsRestrictedOrDoesNotExist() {
        ArchivalFile visible = ingestAs(fx.editor, "public.jpg", b("ordinary visible file"), Restriction.NORMAL, null, null);
        ArchivalFile visible2 = ingestAs(fx.editor, "public2.jpg", b("second visible file"), Restriction.NORMAL, null, null);
        ArchivalFile secret = ingestAs(fx.admin, "secret.jpg", b("restricted file"), Restriction.RESTRICTED, null, null);
        long v = Long.parseLong(visible.friendlyId().substring(1));
        long v2 = Long.parseLong(visible2.friendlyId().substring(1));
        long r = Long.parseLong(secret.friendlyId().substring(1));
        long missing = r + 1000;

        java.util.Map<String, java.util.function.LongFunction<String>> friendlyForms = new java.util.LinkedHashMap<>();
        friendlyForms.put("canonical", ArchivalFileRepository::friendlyIdOf);
        friendlyForms.put("short lower-case", n -> "f" + n);
        friendlyForms.put("embedded", n -> "duplicate of F" + n + ", see notes");
        for (var form : friendlyForms.entrySet()) {
            String restrictedText = form.getValue().apply(r);
            String missingText = form.getValue().apply(missing);
            String visibleText = form.getValue().apply(v);
            UUID cr = carrier(restrictedText);
            UUID cm = carrier(missingText);
            UUID cv = carrier(visibleText);
            for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
                Seen ofRestricted = seen(reader, cr, restrictedText);
                assertThat(ofRestricted).as("%s: restricted and nonexistent look the same", form.getKey())
                        .isEqualTo(seen(reader, cm, missingText)).isEqualTo(new Seen(false, false));
                assertThat(seen(reader, cv, visibleText)).as("%s: an unrestricted File is shown", form.getKey())
                        .isEqualTo(new Seen(true, true));
            }
            assertThat(seen(fx.admin, cr, restrictedText)).isEqualTo(new Seen(true, true));
            assertThat(seen(fx.admin, cm, missingText)).isEqualTo(new Seen(true, true));
        }

        UUID missingId = UUID.randomUUID();
        java.util.Map<String, java.util.function.Function<UUID, String>> uuidForms = new java.util.LinkedHashMap<>();
        uuidForms.put("canonical", UUID::toString);
        uuidForms.put("upper-case", u -> u.toString().toUpperCase(java.util.Locale.ROOT));
        uuidForms.put("Arabic-Indic digits", u -> "see " + arabicDigits(u.toString()));
        for (var form : uuidForms.entrySet()) {
            String restrictedText = form.getValue().apply(secret.fileId());
            String missingText = form.getValue().apply(missingId);
            String visibleText = form.getValue().apply(visible.fileId());
            UUID cr = carrier(restrictedText);
            UUID cm = carrier(missingText);
            UUID cv = carrier(visibleText);
            for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
                assertThat(seen(reader, cr, restrictedText)).as("%s UUID", form.getKey())
                        .isEqualTo(seen(reader, cm, missingText)).isEqualTo(new Seen(false, false));
                assertThat(seen(reader, cv, visibleText)).as("%s UUID of an unrestricted File", form.getKey())
                        .isEqualTo(new Seen(true, true));
            }
        }

        // Several identifiers in one text.
        String bothVisible = "compare F" + v + " with F" + v2;
        String withRestricted = "compare F" + v + " with F" + r;
        String withMissing = "compare F" + v + " with F" + missing;
        UUID c1 = carrier(bothVisible);
        UUID c2 = carrier(withRestricted);
        UUID c3 = carrier(withMissing);
        for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
            assertThat(seen(reader, c1, bothVisible)).isEqualTo(new Seen(true, true));
            assertThat(seen(reader, c2, withRestricted)).isEqualTo(seen(reader, c3, withMissing)).isEqualTo(new Seen(false, false));
        }

        // A user-controlled field value (a corrected filename) behaves the same way.
        ArchivalFile renamedR = ingestAs(fx.editor, "a.jpg", b("rename r"), Restriction.NORMAL, null, null);
        ArchivalFile renamedM = ingestAs(fx.editor, "b.jpg", b("rename m"), Restriction.NORMAL, null, null);
        fx.preservation().files().correct(fx.editor, renamedR.fileId(), renamedR.version(), "F" + r + ".jpg", 0,
                Restriction.NORMAL, null);
        fx.preservation().files().correct(fx.editor, renamedM.fileId(), renamedM.version(), "F" + missing + ".jpg", 0,
                Restriction.NORMAL, null);
        for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
            boolean shownR = fx.services().history().forObject(reader, "archival_file", renamedR.fileId()).stream()
                    .flatMap(x -> x.fieldChanges().stream()).anyMatch(x -> ("F" + r + ".jpg").equals(x.after()));
            boolean shownM = fx.services().history().forObject(reader, "archival_file", renamedM.fileId()).stream()
                    .flatMap(x -> x.fieldChanges().stream()).anyMatch(x -> ("F" + missing + ".jpg").equals(x.after()));
            assertThat(shownR).isEqualTo(shownM).isFalse();
        }
    }

    @Test
    void gasP2_FV02_textWithoutIdentifiersAndReferencesToVisibleObjectsAreNotSuppressed() {
        ArchivalFile visible = ingestAs(fx.editor, "public.jpg", b("visible"), Restriction.NORMAL, null, null);
        ingestAs(fx.admin, "secret.jpg", b("secret"), Restriction.RESTRICTED, null, null);
        List<String> ordinary = List.of("Moved to the Fairfax box, folder F-1990", "scanned 12 deeds at 600 dpi",
                "see " + visible.friendlyId() + " and " + visible.fileId(), "content " + visible.contentId());
        for (String text : ordinary) {
            UUID c = carrier(text);
            assertThat(seen(fx.viewer, c, text)).as(text).isEqualTo(new Seen(true, true));
        }
        assertThat(fx.services().history().forObject(fx.viewer, org.genealogyarchive.storage.StorageVolumeService.OBJECT_TYPE, fx.volumeId))
                .as("non-File objects' identifiers stay visible")
                .anyMatch(x -> x.fieldChanges().stream().anyMatch(c -> fx.volumeId.toString().equals(c.after())));
    }

    // ---- GAS-P2-FV03 (final verification): Unicode spellings cannot bypass the filter -----------------------

    @Test
    void gasP2_FV03_unicodeDigitSpellingsOfARestrictedFileAreWithheld() {
        ArchivalFile secret = ingestAs(fx.editor, "later-restricted.jpg", b("unicode spelling"), Restriction.NORMAL, null, null);
        String id = secret.fileId().toString();
        StringBuilder fullWidth = new StringBuilder();
        for (char c : id.toCharArray()) {
            fullWidth.append(c >= '0' && c <= '9' ? (char) ('０' + (c - '0'))
                    : c >= 'a' && c <= 'f' ? (char) ('ａ' + (c - 'a')) : c);
        }
        List<String> spellings = List.of(arabicDigits(id), fullWidth.toString(),
                arabicDigits(id.substring(0, 18)) + id.substring(18), "ref:" + arabicDigits(id) + ";",
                "xx" + arabicDigits(id) + "ff");
        java.util.Map<String, UUID> carriers = new java.util.LinkedHashMap<>();
        for (String s : spellings) {
            if (!s.startsWith("ref") && !s.startsWith("xx")) {
                assertThat(FileIdentifiers.uuid(s)).as("the web layer resolves %s", s).hasValue(secret.fileId());
            }
            carriers.put(s, carrier(s));
        }
        for (String s : spellings.subList(0, 4)) {
            assertThat(seen(fx.viewer, carriers.get(s), s)).as("before restriction: %s", s).isEqualTo(new Seen(true, true));
        }
        restrict(secret);
        for (String s : spellings) {
            for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
                assertThat(seen(reader, carriers.get(s), s)).as(s).isEqualTo(new Seen(false, false));
            }
            assertThat(seen(fx.admin, carriers.get(s), s)).isEqualTo(new Seen(true, true));
        }
    }

    // ---- GAS-P2-IV01: historical locations never authorize deletion ---------------------------------------

    private ArchivalFile repairedCompletely(String name, byte[] bytes) throws Exception {
        Repaired f = corruptAndRepair(name, bytes);
        FileOperation done = fx.process(f.repairOperation());
        assertThat(done.state()).isEqualTo(OperationState.COMPLETED);
        return fx.file(f.fileId());
    }

    private FileContentRepository.Generation generation(UUID contentId, int n) {
        return fx.preservation().context().contents().generations(contentId).stream()
                .filter(g -> g.generation() == n).findFirst().orElseThrow();
    }

    @Test
    void gasP2_IV01_aForeignObjectAtAQuarantinedGenerationsVacatedPathSurvivesDestruction() throws Exception {
        ArchivalFile f = repairedCompletely("ledger.tif", b("store ledger, gen 1 quarantined"));
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).isEqualTo(GenerationState.QUARANTINED);
        assertThat(fx.preservation().context().operations().writersOfContent(f.contentId()))
                .as("the vacated location is owned by no operation").noneMatch(w -> g1.storagePath().equals(w.targetPath())
                        && w.targetCreated());
        Path vacated = onVolume(g1.storagePath());
        assertThat(vacated).doesNotExist();
        Files.write(vacated, b("someone else's file"));

        FileOperation op = destroy(f);
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(d.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(d.message()).contains("left untouched", g1.storagePath());
        assertThat(vacated).hasBinaryContent(b("someone else's file"));
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(vacated);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.preservation().context().contents().generations(f.contentId()))
                .allMatch(g -> g.state() == GenerationState.DESTROYED);
    }

    @Test
    void gasP2_IV01_destructionBeforeTheDamagedGenerationWasMovedRemovesItWhereItIs() throws Exception {
        Repaired f = corruptAndRepair("lease.tif", b("lease, stopped before the quarantine move"));
        fx.faults.crashAt(FaultPoints.Point.BEFORE_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.QUARANTINE_PENDING);
        assertEveryCopyAccounted(f.contentId());

        FileOperation op = destroy(f.file());
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
        }
        assertThat(fx.operation(f.repairOperation()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    @Test
    void gasP2_IV01_aMoveInterruptedBeforeItWasRecordedFailsVisiblyIfSomethingAppearedAtItsSource() throws Exception {
        Repaired f = corruptAndRepair("bond.tif", b("bond, move done but not recorded"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).isEqualTo(GenerationState.QUARANTINE_PENDING);
        Path source = onVolume(g1.storagePath());
        assertThat(source).doesNotExist();
        Files.write(source, b("appeared at the old location"));
        List<Path> quarantined = filesUnder(fx.volumeRoot.resolve("quarantine"));
        assertThat(quarantined).hasSize(1);

        FileOperation op = destroy(f.file());
        for (int i = 0; i < 2; i++) {
            fx.preservation().destruction().process(op.operationId());
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as("unsettled move with something at its source").isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(source).hasBinaryContent(b("appeared at the old location"));
            assertThat(quarantined.get(0)).exists();
        }
        // An Administrator removes the stray object; the move then settles and removal completes.
        Files.delete(source);
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    @Test
    void gasP2_IV01_aMoveSettledAfterAnInterruptionRetiresTheSourceSoAForeignObjectThereSurvives() throws Exception {
        Repaired f = corruptAndRepair("poll.tif", b("poll book, move settled by recovery"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        fx.preservation().recovery().runOnce();
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).as("settling retired the source in the same transaction").isEqualTo(GenerationState.QUARANTINED);
        assertThat(fx.preservation().context().operations().writersOfContent(f.contentId()))
                .noneMatch(w -> g1.storagePath().equals(w.targetPath()) && w.targetCreated());
        Path vacated = onVolume(g1.storagePath());
        Files.write(vacated, b("not a copy of this content"));
        assertEveryCopyAccounted(f.contentId(), vacated);

        FileOperation op = destroy(f.file());
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            assertNotCompleteWhileACopyRemains(op.operationId(), vacated);
        }
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(vacated).hasBinaryContent(b("not a copy of this content"));
    }

    @Test
    void gasP2_IV01_cleanupInterruptedAfterEachRemovalNeverTouchesHistoricalLocations() throws Exception {
        ArchivalFile f = repairedCompletely("deed3.tif", b("deed, interrupted cleanup with a foreign object"));
        Path vacated = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(vacated, b("foreign"));
        FileOperation op = destroy(f);
        // Known current copies: generation 2 and generation 1's quarantined copy.
        for (int k = 1; k <= 2; k++) {
            AtomicInteger reached = new AtomicInteger();
            int stopAt = k;
            fx.faults.armSticky(FaultPoints.Point.DURING_PHYSICAL_DELETE, id -> {
                if (reached.incrementAndGet() == stopAt) {
                    throw new ScriptedFaults.SimulatedCrash(FaultPoints.Point.DURING_PHYSICAL_DELETE);
                }
            });
            assertThatThrownBy(() -> fx.preservation().destruction().process(op.operationId()))
                    .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
            fx.faults.clear();
            assertThat(fx.operation(op.operationId()).state()).isNotEqualTo(OperationState.COMPLETED);
            assertThat(vacated).hasBinaryContent(b("foreign"));
        }
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(vacated).hasBinaryContent(b("foreign"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    // ---- GAS-P2-IV02: one visibility policy for whole and embedded identifiers ----------------------------

    @Test
    void gasP2_IV02_restrictedAndNonexistentUuidsLookTheSameInEveryContext() {
        ArchivalFile visible = ingestAs(fx.editor, "public.jpg", b("visible iv02"), Restriction.NORMAL, null, null);
        ArchivalFile secret = ingestAs(fx.admin, "secret.jpg", b("restricted iv02"), Restriction.RESTRICTED, null, null);
        UUID missing = UUID.randomUUID();
        java.util.function.Function<UUID, String> arabic = u -> arabicDigits(u.toString());
        java.util.function.Function<UUID, String> fullWidth = u -> {
            StringBuilder sb = new StringBuilder();
            for (char c : u.toString().toCharArray()) {
                sb.append(c >= '0' && c <= '9' ? (char) ('０' + (c - '0')) : c >= 'a' && c <= 'f' ? (char) ('ａ' + (c - 'a')) : c);
            }
            return sb.toString();
        };
        List<java.util.function.Function<UUID, String>> spellings = List.of(UUID::toString,
                u -> u.toString().toUpperCase(java.util.Locale.ROOT), arabic, fullWidth);
        List<String> contexts = List.of("%s", "see %s.", "ref-%s-end", "xx%sff", "deed-%s", "ab%s", "(%s)",
                "copy of %s, compare F00001");
        for (var spelling : spellings) {
            for (String context : contexts) {
                String r = context.formatted(spelling.apply(secret.fileId()));
                String m = context.formatted(spelling.apply(missing));
                UUID cr = carrier(r);
                UUID cm = carrier(m);
                for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
                    assertThat(seen(reader, cr, r)).as("'%s' vs '%s'", r, m).isEqualTo(seen(reader, cm, m))
                            .isEqualTo(new Seen(false, false));
                }
                assertThat(seen(fx.admin, cr, r)).isEqualTo(new Seen(true, true));
            }
            // Delimited references to an unrestricted File stay visible.
            for (String context : List.of("%s", "see %s.", "ref-%s-end", "deed-%s", "(%s)")) {
                String v = context.formatted(spelling.apply(visible.fileId()));
                assertThat(seen(fx.viewer, carrier(v), v)).as(v).isEqualTo(new Seen(true, true));
            }
        }
    }

    @Test
    void gasP2_IV02_filenamesAndIndirectReferencesFollowTheSamePolicy() {
        ArchivalFile secret = ingestAs(fx.admin, "secret.jpg", b("restricted iv02 names"), Restriction.RESTRICTED, null, null);
        UUID missing = UUID.randomUUID();
        UUID missingContent = UUID.randomUUID();
        java.util.Map<String, String> pairs = new java.util.LinkedHashMap<>();
        pairs.put("xx" + secret.fileId() + "ff.jpg", "xx" + missing + "ff.jpg");
        pairs.put(arabicDigits(secret.fileId().toString()) + ".tif", arabicDigits(missing.toString()) + ".tif");
        pairs.put("bytes-" + secret.contentId() + ".jpg", "bytes-" + missingContent + ".jpg");
        for (var pair : pairs.entrySet()) {
            ArchivalFile a = ingestAs(fx.editor, "a.jpg", b("name " + pair.getKey()), Restriction.NORMAL, null, null);
            ArchivalFile c = ingestAs(fx.editor, "c.jpg", b("name " + pair.getValue()), Restriction.NORMAL, null, null);
            fx.preservation().files().correct(fx.editor, a.fileId(), a.version(), pair.getKey(), 0, Restriction.NORMAL, null);
            fx.preservation().files().correct(fx.editor, c.fileId(), c.version(), pair.getValue(), 0, Restriction.NORMAL, null);
            for (Actor reader : new Actor[]{fx.viewer, fx.editor}) {
                boolean shownA = fx.services().history().forObject(reader, "archival_file", a.fileId()).stream()
                        .flatMap(x -> x.fieldChanges().stream()).anyMatch(x -> pair.getKey().equals(x.after()));
                boolean shownC = fx.services().history().forObject(reader, "archival_file", c.fileId()).stream()
                        .flatMap(x -> x.fieldChanges().stream()).anyMatch(x -> pair.getValue().equals(x.after()));
                assertThat(shownA).as(pair.getKey()).isEqualTo(shownC).isFalse();
                boolean globalA = recent(reader).stream().flatMap(x -> x.fieldChanges().stream())
                        .anyMatch(x -> pair.getKey().equals(x.after()));
                boolean globalC = recent(reader).stream().flatMap(x -> x.fieldChanges().stream())
                        .anyMatch(x -> pair.getValue().equals(x.after()));
                assertThat(globalA).isEqualTo(globalC).isFalse();
            }
        }
    }

    // ---- GAS-P2-IV03: no repair bytes after destruction intent -------------------------------------------

    @Test
    void gasP2_IV03_destructionIntentCannotCommitWhileARepairThatPassedItsCheckIsWriting() throws Exception {
        Repaired f = corruptAndRepair("plat2.tif", large("plat, repair writing during destruction request"));
        java.util.concurrent.atomic.AtomicReference<Throwable> attempt = new java.util.concurrent.atomic.AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        ArchivalFile deleted = fx.preservation().files().delete(fx.editor, f.fileId(), fx.file(f.fileId()).version(), null);
        // Codex's interleaving: the repair passed its content check; destruction intent is requested now.
        for (FaultPoints.Point point : List.of(FaultPoints.Point.BEFORE_TARGET_CREATE, FaultPoints.Point.DURING_COPY)) {
            fx.faults.armSticky(point, id -> {
                if (calls.incrementAndGet() <= 2 && attempt.get() == null) {
                    try {
                        fx.preservation().files().destroy(fx.admin, f.fileId(), deleted.version(), "race", null);
                        attempt.set(new AssertionError("destruction intent committed while the repair was writing"));
                    } catch (RuntimeException e) {
                        attempt.set(e);
                    }
                }
            });
        }
        FileOperation repaired = fx.process(f.repairOperation());
        fx.faults.clear();
        assertThat(attempt.get()).isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("being written right now");
        assertThat(repaired.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(content(f.file()).state()).isEqualTo(ContentState.ACTIVE);
        assertThat(fx.file(f.fileId()).lifecycle()).isEqualTo(FileLifecycle.DELETED);

        // Once the repair is finished, destruction proceeds normally.
        FileOperation op = fx.preservation().files().destroy(fx.admin, f.fileId(), deleted.version(), "after repair", null);
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    @Test
    void gasP2_IV03_aRepairCreatesNothingOnceDestructionIntentHasCommitted() throws Exception {
        Repaired f = corruptAndRepair("plat3.tif", b("plat, intent committed between check and create"));
        // The intent commits after the repair's early check but before it creates its copy (as if the
        // destruction had not seen this repair yet): the create step re-reads the content under its lock.
        fx.faults.armSticky(FaultPoints.Point.BEFORE_TARGET_CREATE, id -> fx.preservation().context().contents()
                .setState(f.contentId(), ContentState.DAMAGED, ContentState.DESTROY_PENDING));
        fx.process(f.repairOperation());
        fx.faults.clear();
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.targetCreated()).isFalse();
        assertThat(onVolume(repair.targetPath())).doesNotExist();
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).hasSize(1);
        for (int i = 0; i < 2; i++) {
            fx.process(repair.operationId());
            assertThat(onVolume(repair.targetPath())).doesNotExist();
        }
    }

    @Test
    void gasP2_IV03_aConcurrentDestructionRequestIsRefusedNotBlockedWhileARepairCopies() throws Exception {
        Repaired f = corruptAndRepair("plat4.tif", large("plat, concurrent destroy request"));
        ArchivalFile deleted = fx.preservation().files().delete(fx.editor, f.fileId(), fx.file(f.fileId()).version(), null);
        java.util.concurrent.CountDownLatch copying = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger chunks = new AtomicInteger();
        fx.faults.armSticky(FaultPoints.Point.DURING_COPY, id -> {
            if (chunks.incrementAndGet() == 1) {
                copying.countDown();
                try {
                    release.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var repairing = worker.submit(() -> fx.process(f.repairOperation()));
            assertThat(copying.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            assertThatThrownBy(() -> fx.preservation().files().destroy(fx.admin, f.fileId(), deleted.version(), "race", null))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("being written right now");
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(10));
            assertThat(content(f.file()).state()).isEqualTo(ContentState.DAMAGED);
            release.countDown();
            assertThat(repairing.get(60, java.util.concurrent.TimeUnit.SECONDS).state()).isEqualTo(OperationState.COMPLETED);
        } finally {
            release.countDown();
            worker.shutdownNow();
            fx.faults.clear();
        }
    }

    @Test
    void gasP2_IV03_destructionIntentStopsARepairThatIsNotRunningSoItNeverWrites() throws Exception {
        Repaired f = corruptAndRepair("plat5.tif", b("plat, repair planned but idle"));
        fx.faults.crashAt(FaultPoints.Point.BEFORE_TARGET_CREATE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation planned = fx.operation(f.repairOperation());
        assertThat(planned.state()).isEqualTo(OperationState.PROMOTING);

        FileOperation op = destroy(f.file());
        FileOperation stopped = fx.operation(planned.operationId());
        assertThat(stopped.state()).as("stopped in the intent transaction").isEqualTo(OperationState.CANCELLED);
        assertThat(stopped.stagingCleaned()).isTrue();
        for (int i = 0; i < 2; i++) {
            fx.process(planned.operationId());
            fx.preservation().recovery().runOnce();
            assertThat(onVolume(planned.targetPath())).doesNotExist();
        }
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
    }

    // ---- IV01 (final outstanding): stale ownership after a missing-content repair ------------------------

    /** Ingests content and makes its stored original missing (recorded by a scan). */
    private ArchivalFile ingestedThenMissing(String name, byte[] bytes) throws Exception {
        ArchivalFile f = ingestAs(fx.editor, name, bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        fx.scan(fx.services(), "QUICK");
        assertThat(content(f).state()).isEqualTo(ContentState.MISSING);
        assertOwnershipConsistent(f.contentId());
        return f;
    }

    /**
     * The invariant, checked independently of the scenario: no operation owns the location of a retired
     * generation, nor the current location of content recorded as missing.
     */
    private void assertOwnershipConsistent(UUID contentId) {
        var ctx = fx.preservation().context();
        boolean missing = ctx.contents().find(contentId).map(c -> c.state() == ContentState.MISSING).orElse(false);
        for (FileContentRepository.Generation g : ctx.contents().generations(contentId)) {
            boolean historical = g.state() == GenerationState.QUARANTINED || g.state() == GenerationState.MISSING
                    || g.state() == GenerationState.DESTROYED || (missing && g.state() == GenerationState.CURRENT);
            if (historical) {
                assertThat(ctx.operations().writersOfContent(contentId))
                        .as("no operation owns %s (generation %s, %s)", g.storagePath(), g.generation(), g.state())
                        .noneMatch(w -> w.targetCreated() && g.storagePath().equals(w.targetPath()));
            }
        }
    }

    private FileOperation repairOf(ArchivalFile f, byte[] bytes) {
        return fx.preservation().repair().receive(fx.admin, f.contentId(), "good.tif", bytes.length,
                new ByteArrayInputStream(bytes));
    }

    @Test
    void gasP2_IV01_anUnrelatedObjectAtAMissingGenerationsOldPathSurvivesDestruction() throws Exception {
        byte[] bytes = b("parish register, missing then repaired");
        ArchivalFile f = ingestedThenMissing("register.tif", bytes);
        FileOperation repair = fx.process(repairOf(f, bytes).operationId());
        assertThat(repair.state()).isEqualTo(OperationState.COMPLETED);
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).isEqualTo(GenerationState.MISSING);
        assertOwnershipConsistent(f.contentId());
        Path old = onVolume(g1.storagePath());
        Files.write(old, b("unrelated object"));

        FileOperation op = destroy(f);
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            assertNotCompleteWhileACopyRemains(op.operationId(), old);
            assertOwnershipConsistent(f.contentId());
        }
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(d.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(d.message()).contains("left untouched", g1.storagePath());
        assertThat(old).hasBinaryContent(b("unrelated object"));
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(old);
    }

    @Test
    void gasP2_IV01_anUnrelatedObjectAtTheLocationOfContentRecordedMissingIsNotRemoved() throws Exception {
        byte[] bytes = b("tax roll, missing, repair stopped before commit");
        ArchivalFile f = ingestedThenMissing("taxroll.tif", bytes);
        fx.faults.crashAt(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        UUID repairId = repairOf(f, bytes).operationId();
        assertThatThrownBy(() -> fx.process(repairId)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(repairId);
        assertThat(repair.targetCreated()).isTrue();
        Path current = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(current, b("not the archived bytes"));
        assertOwnershipConsistent(f.contentId());

        FileOperation op = destroy(f);
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            assertNotCompleteWhileACopyRemains(op.operationId(), current);
        }
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(current).hasBinaryContent(b("not the archived bytes"));
        assertThat(onVolume(repair.targetPath())).as("the repair's own copy").doesNotExist();
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(current);
    }

    @Test
    void gasP2_IV01_theVerifiedOriginalBackAtAMissingLocationIsRemoved() throws Exception {
        byte[] bytes = b("muster roll, original put back without re-verification");
        ArchivalFile f = ingestedThenMissing("muster2.tif", bytes);
        Path current = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(current, bytes);

        FileOperation op = destroy(f);
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).as("verifiably the content's own bytes").isEmpty();
    }

    @Test
    void gasP2_IV01_aRepairStoppedAfterItsCommitLeavesAnUnrelatedObjectAtTheOldPathUntouched() throws Exception {
        byte[] bytes = b("bond, missing, stopped after commit");
        ArchivalFile f = ingestedThenMissing("bond2.tif", bytes);
        fx.faults.crashAt(FaultPoints.Point.AFTER_COMMIT_BEFORE_CLEANUP);
        UUID repairId = repairOf(f, bytes).operationId();
        assertThatThrownBy(() -> fx.process(repairId)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(repairId).state()).isEqualTo(OperationState.COMMITTED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.MISSING);
        assertOwnershipConsistent(f.contentId());
        Path old = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(old, b("unrelated"));

        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            assertNotCompleteWhileACopyRemains(op.operationId(), old);
        }
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(fx.operation(repairId).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(old).hasBinaryContent(b("unrelated"));
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(old);
    }

    @Test
    void gasP2_IV01_theDatabaseRefusesOwnershipOfARetiredLocation() throws Exception {
        byte[] bytes = b("deed, ownership of a retired path");
        ArchivalFile f = ingestedThenMissing("deed4.tif", bytes);
        fx.process(repairOf(f, bytes).operationId());
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        FileOperation ingest = fx.preservation().context().operations().writersOfContent(f.contentId()).stream()
                .filter(w -> w.kind() == OperationKind.INGEST).findFirst().orElseThrow();
        assertThat(ingest.targetPath()).isEqualTo(g1.storagePath());
        assertThat(ingest.targetCreated()).as("released when the content was recorded missing").isFalse();
        assertThatThrownBy(() -> fx.preservation().context().operations().markTargetCreated(ingest.operationId(), g1.storagePath()))
                .hasMessageContaining("retired generation");
    }
}
