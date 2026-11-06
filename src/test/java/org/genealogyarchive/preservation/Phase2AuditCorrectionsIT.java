package org.genealogyarchive.preservation;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.config.InstallationConfig.FileSettings;
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
        Repaired f = corruptAndRepair("bible.tif", b("family bible register"));
        FileOperation repair = repairStoppedBeforeCommit(f);
        FileOperation op = destroy(f.file());

        try (var busy = fx.preservation().context().locks().tryLock("file-operation", repair.operationId()).orElseThrow()) {
            fx.preservation().destruction().process(op.operationId());
            FileOperation waiting = fx.operation(op.operationId());
            assertThat(waiting.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(waiting.message()).contains("waits for operation " + repair.operationId());
            assertThat(onVolume(repair.targetPath())).exists();
            assertThat(fx.operation(repair.operationId()).state()).isEqualTo(OperationState.PROMOTED);
        }
        fx.preservation().recovery().runOnce();
        assertDestroyedWithoutTrace(f, op.operationId(), repair);
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
}
