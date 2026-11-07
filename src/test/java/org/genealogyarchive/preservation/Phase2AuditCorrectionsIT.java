package org.genealogyarchive.preservation;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.data.UnresolvedObjectRepository;
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

    /** Like {@link #corruptAndRepair}, but the original is lost (recorded MISSING) instead of damaged. */
    private Repaired lostAndRepair(String name, byte[] bytes) throws Exception {
        ArchivalFile f = ingestAs(fx.editor, name, bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        fx.scan(fx.services(), "QUICK");
        assertThat(fx.preservation().context().contents().find(f.contentId()).orElseThrow().state())
                .isEqualTo(ContentState.MISSING);
        FileOperation repair = fx.preservation().repair().receive(fx.admin, f.contentId(), "good.tif", bytes.length,
                new ByteArrayInputStream(bytes));
        return new Repaired(f, repair.operationId());
    }

    /** The open unresolved-object record at a location (V0019). */
    private UnresolvedObjectRepository.Entry openRecordAt(String path) {
        return fx.preservation().context().unresolved().openAt(fx.volumeId, path)
                .orElseThrow(() -> new AssertionError("no open unresolved record at " + path));
    }

    private boolean openRecordExistsAt(String path) {
        return fx.preservation().context().unresolved().openAt(fx.volumeId, path).isPresent();
    }

    /**
     * After a repair left the superseded damaged copy UNRESOLVED (V0018 audit), an Administrator inspects it and
     * attests that the recorded object is the archive's damaged copy.
     */
    private UnresolvedObjectRepository.Entry attestGeneration(UUID contentId, int generation) {
        FileContentRepository.Generation g = generation(contentId, generation);
        assertThat(g.state()).isEqualTo(GenerationState.UNRESOLVED);
        UnresolvedObjectRepository.Entry u = openRecordAt(g.storagePath());
        UnresolvedObjectRepository.Entry attested = fx.preservation().unresolved().attest(fx.admin, u.unresolvedId(),
                "inspected on the volume: this is the damaged copy");
        assertThat(attested.attested()).isTrue();
        return attested;
    }

    /** The attestation and the worker's quarantine move of the attested object, uninterrupted. */
    private void attestAndQuarantine(UUID contentId, int generation) {
        UnresolvedObjectRepository.Entry u = attestGeneration(contentId, generation);
        assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isTrue();
        assertThat(generation(contentId, generation).state()).isEqualTo(GenerationState.QUARANTINED);
    }

    @Test
    void gasP2_006_destroyingAfterARepairRemovesTheQuarantinedGenerationToo() throws Exception {
        Repaired f = corruptAndRepair("will.tif", b("last will and testament"));
        fx.process(f.repairOperation());
        attestAndQuarantine(f.contentId(), 1);
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
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
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
        Repaired f = lostAndRepair("survey.tif", b("land survey, 1791"));
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
        byte[] bytes = b("plat of the mill tract");
        Repaired f = lostAndRepair("plat.tif", bytes);
        FileOperation repair = repairStoppedBeforeCommit(f);
        // The verified original put back at its recorded location: a second known copy (authoritative bytes).
        Path damaged = onVolume(fx.preservation().context().contents().generations(f.contentId()).get(0).storagePath());
        Files.write(damaged, bytes);
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
        // a busy repair that had already committed (finishing its clean-up).
        Repaired f = lostAndRepair("bible.tif", b("family bible register"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_COMMIT_BEFORE_CLEANUP);
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
            assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).hasSize(1);
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
        Repaired f = lostAndRepair("muster.tif", b("militia muster roll"));
        fx.faults.crashAt(FaultPoints.Point.BEFORE_TARGET_CREATE);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.state()).isEqualTo(OperationState.PROMOTING);
        assertThat(repair.targetCreated()).isFalse();
        Path foreign = onVolume(repair.targetPath());
        Files.createDirectories(foreign.getParent());
        Files.write(foreign, b("not written by any operation"));

        FileOperation op = destroy(f.file());
        releasedThenCompleted(op, foreign, b("not written by any operation"));
        FileOperation stopped = fx.operation(repair.operationId());
        assertThat(stopped.state()).isEqualTo(OperationState.CANCELLED);
        assertThat(stopped.message()).contains("left untouched");
        assertThat(foreign).exists().hasBinaryContent(b("not written by any operation"));
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
            ctx.unresolved().openForOperation(w.operationId()).forEach(u -> accounted.add(onVolume(u.path())));
        }
        // V0019: an object the archive cannot identify is accounted for by its open unresolved record.
        ctx.unresolved().openForContent(contentId).forEach(u -> accounted.add(onVolume(u.path())));
        List<Path> onDisk = new java.util.ArrayList<>(filesUnder(fx.volumeRoot.resolve("originals")));
        onDisk.addAll(filesUnder(fx.volumeRoot.resolve("quarantine")));
        assertThat(accounted).as("every copy on the volume is accounted for").containsAll(onDisk);
    }

    /** Invariant 5: a completed destruction leaves no copy on the volume except named foreign objects. */
    /**
     * V0019 audit: an unidentified object at a former location of the content keeps its removal incomplete (it is
     * recorded as unresolved, never deleted) until an Administrator releases it; then removal completes and the
     * object is still there.
     */
    private FileOperation releasedThenCompleted(FileOperation op, Path object, byte[] bytes) throws IOException {
        String path = PortableRelativePath.fromNative(fx.volumeRoot, object).value();
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(d.message()).contains(path, "left untouched");
            assertThat(object).hasBinaryContent(bytes);
        }
        UnresolvedObjectRepository.Entry u = openRecordAt(path);
        fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "inspected: not a copy of this content");
        fx.preservation().recovery().runOnce();
        FileOperation done = fx.operation(op.operationId());
        assertThat(done.state()).as(done.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(done.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(object).as("released, never deleted").hasBinaryContent(bytes);
        return done;
    }

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
        Repaired f = lostAndRepair("census.tif", large("1850 census, recreated after a recorded move"));
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
        Repaired f = lostAndRepair("tax.tif", large("tax list, recreated after an unconfirmed move"));
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
        Repaired f = lostAndRepair("deed2.tif", large("deed, move unconfirmed when destroyed"));
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
        Repaired f = lostAndRepair("roll.tif", large("tithe roll, foreign object at a vacated target"));
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
        releasedThenCompleted(op, foreign, b("placed here by hand"));
        assertThat(foreign).hasBinaryContent(b("placed here by hand"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.operation(f.repairOperation()).message()).contains("left untouched");
    }

    @Test
    void gasP2_FV01_cleanupInterruptedAfterEachRemovalNeverReportsCompletionEarly() throws Exception {
        Repaired f = lostAndRepair("will2.tif", large("will, interrupted at every removal"));
        FileOperation repair = recreatedAtTheVacatedTarget(f, FaultPoints.Point.AFTER_QUARANTINE_RECORDED);
        FileOperation op = destroy(f.file());
        // Known locations: the recreated copy, the (lost) original's location and the quarantined partial copy.
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

    /** A damaged content repaired, and its superseded damaged copy attested and moved to quarantine. */
    private ArchivalFile repairedCompletely(String name, byte[] bytes) throws Exception {
        Repaired f = corruptAndRepair(name, bytes);
        FileOperation done = fx.process(f.repairOperation());
        assertThat(done.state()).isEqualTo(OperationState.COMPLETED);
        attestAndQuarantine(f.contentId(), 1);
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
        FileOperation d = releasedThenCompleted(op, vacated, b("someone else's file"));
        assertThat(d.message()).contains("left untouched", g1.storagePath());
        assertThat(vacated).hasBinaryContent(b("someone else's file"));
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(vacated);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(fx.preservation().context().contents().generations(f.contentId()))
                .allMatch(g -> g.state() == GenerationState.DESTROYED);
    }

    @Test
    void gasP2_IV01_destructionBeforeTheDamagedGenerationWasMovedRemovesItWhereItIs() throws Exception {
        // V0019: the damaged copy is attested by an Administrator but the worker stopped before moving it.
        Repaired f = corruptAndRepair("lease.tif", b("lease, stopped before the quarantine move"));
        assertThat(fx.process(f.repairOperation()).state()).isEqualTo(OperationState.COMPLETED);
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.BEFORE_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.UNRESOLVED);
        assertEveryCopyAccounted(f.contentId());

        FileOperation op = destroy(f.file());
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(fx.preservation().context().unresolved().find(u.unresolvedId()).orElseThrow().resolution())
                .isEqualTo(UnresolvedObjectRepository.Resolution.DESTROYED);
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isTrue();
        }
        assertThat(fx.operation(f.repairOperation()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    @Test
    void gasP2_IV01_aMoveInterruptedBeforeItWasRecordedFailsVisiblyIfSomethingAppearedAtItsSource() throws Exception {
        Repaired f = corruptAndRepair("bond.tif", b("bond, move done but not recorded"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).isEqualTo(GenerationState.UNRESOLVED);
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
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
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
        releasedThenCompleted(op, vacated, b("not a copy of this content"));
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
        Repaired f = lostAndRepair("plat2.tif", large("plat, repair writing during destruction request"));
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
        Repaired f = lostAndRepair("plat5.tif", b("plat, repair planned but idle"));
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
        FileOperation d = releasedThenCompleted(op, old, b("unrelated object"));
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
        // V0018 audit: present-but-unverified bytes at a current location are unresolved, never "removed".
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(current).hasBinaryContent(b("not the archived bytes"));
        }
        assertThat(onVolume(repair.targetPath())).as("the repair's own copy").doesNotExist();
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        assertThat(u.kind()).isEqualTo(UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION);
        // An Administrator inspects it and releases it as not the archive's: it stays, and removal completes.
        fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "a scanner test file, not ours");
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(current).hasBinaryContent(b("not the archived bytes"));
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
        releasedThenCompleted(op, old, b("unrelated"));
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
        // V0016 generalised this rule: no operation may own any location a generation records (single owner).
        assertThatThrownBy(() -> fx.preservation().context().operations().markTargetCreated(ingest.operationId(), g1.storagePath()))
                .hasMessageContaining("single owner");
    }

    // ---- Ownership consistency (V0016): single owner, vacated locations, evidence for moves ---------------

    private org.springframework.jdbc.core.simple.JdbcClient sql() {
        return org.springframework.jdbc.core.simple.JdbcClient.create(fx.ds);
    }

    /**
     * The ownership invariants, checked against the whole database independently of any scenario: no
     * operation owns a location a generation records; a vacated generation is never QUARANTINE_PENDING; the
     * current generation of MISSING content is vacated.
     */
    private void assertOwnershipInvariants() {
        assertThat(sql().sql("""
                SELECT count(*) FROM gas.file_operation o JOIN gas.file_content_generation g
                    ON g.volume_id = o.volume_id AND g.storage_path = o.target_path
                 WHERE o.target_created""").query(Long.class).single()).as("operations owning generation locations").isZero();
        assertThat(sql().sql("SELECT count(*) FROM gas.file_content_generation WHERE vacated AND state = 'QUARANTINE_PENDING'")
                .query(Long.class).single()).isZero();
        assertThat(sql().sql("""
                SELECT count(*) FROM gas.file_content c JOIN gas.file_content_generation g
                    ON g.content_id = c.content_id AND g.state = 'CURRENT'
                 WHERE c.state = 'MISSING' AND NOT g.vacated""").query(Long.class).single()).isZero();
    }

    private static final String SINGLE_OWNER = "single owner";

    @Test
    void v0016_theDatabaseRefusesEveryStatementThatWouldOwnARecordedLocation() throws Exception {
        Repaired f = corruptAndRepair("sql.tif", b("direct SQL ownership checks"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        assertThat(repair.targetCreated()).isTrue();
        String g1 = generation(f.contentId(), 1).storagePath();
        UUID ingest = fx.preservation().context().operations().writersOfContent(f.contentId()).stream()
                .filter(w -> w.kind() == OperationKind.INGEST).findFirst().orElseThrow().operationId();
        assertOwnershipInvariants();

        // UPDATE: claim a generation's location.
        assertThatThrownBy(() -> sql().sql("UPDATE gas.file_operation SET target_created = true WHERE operation_id = :id")
                .param("id", ingest).update()).hasMessageContaining(SINGLE_OWNER);
        // UPDATE: move an existing claim onto a generation's location.
        assertThatThrownBy(() -> sql().sql("UPDATE gas.file_operation SET target_path = :p WHERE operation_id = :id")
                .param("p", g1).param("id", repair.operationId()).update()).hasMessageContaining(SINGLE_OWNER);
        // INSERT: a new operation claiming a generation's location.
        try (var c = fx.ds.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("CREATE TEMP TABLE op_copy ON COMMIT DROP AS SELECT * FROM gas.file_operation WHERE operation_id = '"
                        + ingest + "'");
                st.execute("UPDATE op_copy SET operation_id = gen_random_uuid(), batch_id = NULL, item_key = NULL, "
                        + "target_created = true, target_path = '" + g1 + "'");
                // V0017: refused by the claim row the INSERT tries to create (immediately; V0016 also at commit).
                assertThatThrownBy(() -> {
                    st.execute("INSERT INTO gas.file_operation SELECT * FROM op_copy");
                    c.commit();
                }).hasMessageContaining(SINGLE_OWNER);
            } finally {
                c.rollback();
                c.setAutoCommit(true);
            }
        }
        // INSERT: a generation recording a location an operation still owns.
        assertThatThrownBy(() -> sql().sql("""
                INSERT INTO gas.file_content_generation (content_id, generation, volume_id, storage_path, state, operation_id)
                VALUES (:c, 99, :v, :p, 'MISSING', :op)""").param("c", f.contentId()).param("v", fx.volumeId)
                .param("p", repair.targetPath()).param("op", repair.operationId()).update()).hasMessageContaining(SINGLE_OWNER);
        // (The legitimate transfer of ownership within one transaction is covered by
        // v0017_ownershipTransfersAtomicallyFromOperationToGeneration.)
        // Content recorded missing cannot become "damaged" from whatever is found at its location.
        sql().sql("UPDATE gas.file_content SET state = 'MISSING' WHERE content_id = :c").param("c", f.contentId()).update();
        assertThat(generation(f.contentId(), 1).vacated()).as("recording MISSING vacates the location").isTrue();
        assertThatThrownBy(() -> sql().sql("UPDATE gas.file_content SET state = 'DAMAGED' WHERE content_id = :c")
                .param("c", f.contentId()).update()).hasMessageContaining("recorded missing");
        // A vacated generation can never be marked for quarantine.
        assertThatThrownBy(() -> sql().sql("""
                UPDATE gas.file_content_generation SET state = 'QUARANTINE_PENDING'
                 WHERE content_id = :c AND generation = 1""").param("c", f.contentId()).update())
                .hasMessageContaining("vacated_not_quarantined");
        sql().sql("UPDATE gas.file_content SET state = 'ACTIVE' WHERE content_id = :c").param("c", f.contentId()).update();
        assertThat(generation(f.contentId(), 1).vacated()).as("verified again: no longer vacated").isFalse();
        assertOwnershipInvariants();
    }

    @Test
    void v0016_ownershipPassesToTheGenerationWhenTheCopyIsCommitted() throws Exception {
        ArchivalFile f = ingestAs(fx.editor, "owner.tif", b("ownership passes at commit"), Restriction.NORMAL, null, null);
        assertThat(fx.preservation().context().operations().writersOfContent(f.contentId()))
                .allSatisfy(w -> assertThat(w.targetCreated()).isFalse());
        Repaired r = corruptAndRepair("owner2.tif", b("repair ownership passes at commit"));
        fx.process(r.repairOperation());
        assertThat(fx.operation(r.repairOperation()).targetCreated()).isFalse();
        assertOwnershipInvariants();
    }

    @Test
    void v0016_aForeignObjectAppearingAtAMissingLocationBeforeRepairIsNeverAdopted() throws Exception {
        byte[] bytes = b("will book, foreign object before repair");
        ArchivalFile f = ingestedThenMissing("willbook.tif", bytes);
        Path location = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(location, b("something else entirely, longer than the original bytes were"));
        for (String kind : List.of("QUICK", "FULL")) {
            fx.scan(fx.services(), kind);
            assertThat(content(f).state()).as("%s scan: still missing, not damaged", kind).isEqualTo(ContentState.MISSING);
            assertThat(fx.preservation().integrity().openFindings(fx.admin, 50))
                    .anyMatch(x -> x.kind() == FindingKind.UNEXPECTED_OBJECT && location.equals(onVolume(x.storagePath())));
            assertOwnershipInvariants();
        }
        FileOperation repair = fx.process(repairOf(f, bytes).operationId());
        assertThat(repair.state()).isEqualTo(OperationState.COMPLETED);
        // V0018 audit: the occupied former location is never "safely vacated": UNRESOLVED, untouched, recorded.
        assertThat(generation(f.contentId(), 1).state()).as("not quarantined, not MISSING").isEqualTo(GenerationState.UNRESOLVED);
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        assertThat(u.kind()).isEqualTo(UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION);
        assertThat(location).hasBinaryContent(b("something else entirely, longer than the original bytes were"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertOwnershipInvariants();

        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertNotCompleteWhileACopyRemains(op.operationId(), location);
            assertThat(openRecordAt(u.path()).unresolvedId()).isEqualTo(u.unresolvedId());
        }
        fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "inspected: not the archive's copy");
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(location).hasBinaryContent(b("something else entirely, longer than the original bytes were"));
        assertOwnershipInvariants();
    }

    @Test
    void v0016_anObjectSwappedInDuringAnInterruptedQuarantineMoveIsNotMovedOrAdopted() throws Exception {
        Repaired f = corruptAndRepair("swap.tif", b("land grant, swapped during quarantine"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).isEqualTo(GenerationState.UNRESOLVED);
        QuarantineRepository.Entry intent = fx.preservation().context().quarantine().forContent(f.contentId()).get(0);
        assertThat(intent.state()).isEqualTo(QuarantineRepository.State.INTENDED);
        assertThat(intent.hasEvidence()).isTrue();
        Path source = onVolume(g1.storagePath());
        SafeFiles.clearReadOnly(source);
        Files.delete(source);
        Files.write(source, b("a different object put here meanwhile"));

        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isFalse();
            assertThat(source).hasBinaryContent(b("a different object put here meanwhile"));
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.preservation().context().quarantine().forContent(f.contentId()).get(0).state())
                    .isEqualTo(QuarantineRepository.State.INTENDED);
            assertThat(openRecordAt(u.path()).attested()).as("still unresolved").isTrue();
        }
        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
        assertThat(source).hasBinaryContent(b("a different object put here meanwhile"));

        // Once an Administrator has removed the stray object, the move settles as SOURCE_ABSENT and all finish.
        Files.delete(source);
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
        }
        assertThat(fx.preservation().context().unresolved().find(u.unresolvedId()).orElseThrow().resolution())
                .isEqualTo(UnresolvedObjectRepository.Resolution.ABSENT);
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
        assertOwnershipInvariants();
    }

    @Test
    void v0016_anInterruptedQuarantineMoveOfTheRecordedObjectIsStillFinished() throws Exception {
        Repaired f = corruptAndRepair("same.tif", b("deed, same object after interruption"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertThat(fx.preservation().context().unresolved().find(u.unresolvedId()).orElseThrow().resolution())
                .isEqualTo(UnresolvedObjectRepository.Resolution.QUARANTINED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.QUARANTINED);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).hasSize(1);
        assertOwnershipInvariants();
    }

    @Test
    void v0016_recordingMissingContentIsAtomic() throws Exception {
        ArchivalFile f = ingestAs(fx.editor, "atomic.tif", b("atomic missing recording"), Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        fx.faults.crashAt(FaultPoints.Point.AFTER_MISSING_FINDING);
        assertThatThrownBy(() -> fx.scan(fx.services(), "QUICK")).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(content(f).state()).as("nothing recorded").isEqualTo(ContentState.ACTIVE);
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 50)).noneMatch(x -> x.kind() == FindingKind.MISSING);
        assertThat(generation(f.contentId(), 1).vacated()).isFalse();
        assertOwnershipInvariants();

        fx.scan(fx.services(), "QUICK");
        assertThat(content(f).state()).isEqualTo(ContentState.MISSING);
        assertThat(generation(f.contentId(), 1).vacated()).isTrue();
        assertOwnershipInvariants();
    }

    /** An interrupted or legacy recording: an open MISSING finding while the content is still ACTIVE. */
    private ArchivalFile recordedMissingButStillActive(String name, byte[] bytes, byte[] foreign) throws Exception {
        ArchivalFile f = ingestAs(fx.editor, name, bytes, Restriction.NORMAL, null, null);
        FileContent c = content(f);
        fx.preservation().context().integrity().insertFindingIfNew(null, new org.genealogyarchive.data.IntegrityRepository.NewFinding(
                FindingKind.MISSING, c.volumeId(), c.contentId(), c.currentGeneration(), c.currentPath().value(), null, null,
                null, "legacy recording"));
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        Files.write(stored, foreign);
        assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
        return f;
    }

    @Test
    void v0016_recoveryReconcilesAnInterruptedMissingRecordingBeforeAnythingIsDeleted() throws Exception {
        ArchivalFile f = recordedMissingButStillActive("legacy.tif", b("legacy recording"), b("unrelated legacy object"));
        Path stored = fx.storedPath(f);
        fx.preservation().recovery().runOnce();
        assertThat(content(f).state()).isEqualTo(ContentState.MISSING);
        assertThat(generation(f.contentId(), 1).vacated()).isTrue();
        assertOwnershipInvariants();

        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.operation(op.operationId()).state()).as("present but unverified at the current location")
                    .isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        }
        fx.preservation().unresolved().release(fx.admin, openRecordAt(generation(f.contentId(), 1).storagePath()).unresolvedId(),
                "inspected: unrelated");
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(stored).hasBinaryContent(b("unrelated legacy object"));
    }

    @Test
    void v0016_destructionReconcilesAnInterruptedMissingRecordingItself() throws Exception {
        ArchivalFile f = recordedMissingButStillActive("legacy2.tif", b("legacy recording 2"), b("unrelated object 2"));
        Path stored = fx.storedPath(f);
        FileOperation op = destroy(f);
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(d.message()).contains("left untouched");
        assertThat(stored).hasBinaryContent(b("unrelated object 2"));
        assertThat(sql().sql("SELECT vacated FROM gas.file_content_generation WHERE content_id = :c AND generation = 1")
                .param("c", f.contentId()).query(Boolean.class).single()).as("reconciled before removal").isTrue();
        fx.preservation().unresolved().release(fx.admin, openRecordAt(generation(f.contentId(), 1).storagePath()).unresolvedId(),
                "inspected: unrelated");
        fx.preservation().destruction().process(op.operationId());
        d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(d.message()).contains("left untouched");
        assertThat(stored).hasBinaryContent(b("unrelated object 2"));
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.DESTROYED);
    }

    // ---- V0017: structural audit corrections -----------------------------------------------------------

    private Repaired damagedThenGone(String name, byte[] bytes) throws Exception {
        Repaired f = corruptAndRepair(name, bytes);
        assertThat(content(f.file()).state()).isEqualTo(ContentState.DAMAGED);
        Path stored = onVolume(generation(f.contentId(), 1).storagePath());
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        return f;
    }

    @Test
    void v0017_damagedContentThatGoesMissingIsVacatedAndAForeignObjectThereIsNeverAdopted() throws Exception {
        byte[] bytes = b("survey book, damaged then missing");
        Repaired f = damagedThenGone("survey2.tif", bytes);
        fx.scan(fx.services(), "QUICK");
        assertThat(content(f.file()).state()).as("DAMAGED -> MISSING").isEqualTo(ContentState.MISSING);
        assertThat(generation(f.contentId(), 1).vacated()).isTrue();
        assertOwnershipInvariants();
        Path location = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(location, b("an unrelated object at the old location"));
        fx.scan(fx.services(), "FULL");
        assertThat(content(f.file()).state()).isEqualTo(ContentState.MISSING);

        FileOperation repaired = fx.process(f.repairOperation());
        assertThat(repaired.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).as("occupied: unresolved, not quarantined").isEqualTo(GenerationState.UNRESOLVED);
        assertThat(openRecordAt(generation(f.contentId(), 1).storagePath()).kind())
                .isEqualTo(UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION);
        assertThat(location).hasBinaryContent(b("an unrelated object at the old location"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();

        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertNotCompleteWhileACopyRemains(op.operationId(), location);
        }
        fx.preservation().unresolved().release(fx.admin, openRecordAt(generation(f.contentId(), 1).storagePath()).unresolvedId(),
                "inspected: unrelated");
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(location).hasBinaryContent(b("an unrelated object at the old location"));
        assertOwnershipInvariants();
    }

    @Test
    void v0017_aCrashWhileRecordingDamagedContentAsMissingRecordsNothingAndARescanRecordsIt() throws Exception {
        Repaired f = damagedThenGone("survey3.tif", b("survey book, crash while recording missing"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_MISSING_FINDING);
        assertThatThrownBy(() -> fx.scan(fx.services(), "QUICK")).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(content(f.file()).state()).isEqualTo(ContentState.DAMAGED);
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 50)).noneMatch(x -> x.kind() == FindingKind.MISSING);
        fx.scan(fx.services(), "QUICK");
        assertThat(content(f.file()).state()).isEqualTo(ContentState.MISSING);
        assertThat(generation(f.contentId(), 1).vacated()).isTrue();
        assertOwnershipInvariants();
    }

    @Test
    void v0017_recoveryReconcilesDamagedContentRecordedMissingBeforeTheRepairActs() throws Exception {
        Repaired f = damagedThenGone("survey4.tif", b("survey book, legacy damaged and missing"));
        FileContent c = content(f.file());
        fx.preservation().context().integrity().insertFindingIfNew(null, new org.genealogyarchive.data.IntegrityRepository.NewFinding(
                FindingKind.MISSING, c.volumeId(), c.contentId(), c.currentGeneration(), c.currentPath().value(), null, null,
                null, "legacy recording"));
        Path location = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(location, b("appeared after the bytes left"));
        assertThat(content(f.file()).state()).isEqualTo(ContentState.DAMAGED);

        fx.preservation().recovery().runOnce();
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(f.repairOperation()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.UNRESOLVED);
        assertThat(openRecordAt(generation(f.contentId(), 1).storagePath()).kind())
                .isEqualTo(UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION);
        assertThat(location).hasBinaryContent(b("appeared after the bytes left"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertOwnershipInvariants();
    }

    @Test
    void v0017_aDamagedCopyReplacedWithoutAScanIsNotQuarantinedByTheRepair() throws Exception {
        Repaired f = corruptAndRepair("deed5.tif", b("deed, damaged copy replaced before the repair"));
        Path location = onVolume(generation(f.contentId(), 1).storagePath());
        SafeFiles.clearReadOnly(location);
        Files.write(location, b("replacement put here after the damage was recorded"));

        FileOperation repaired = fx.process(f.repairOperation());
        assertThat(repaired.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.UNRESOLVED);
        assertThat(location).hasBinaryContent(b("replacement put here after the damage was recorded"));
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        assertThat(u.kind()).isEqualTo(UnresolvedObjectRepository.Kind.DAMAGED_COPY);
        assertThat(u.detail()).contains("left untouched");

        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(location).hasBinaryContent(b("replacement put here after the damage was recorded"));
        assertOwnershipInvariants();
    }

    @Test
    void v0017_anObjectWithTheSameSizeAndTimestampButDifferentBytesIsNotMoved() throws Exception {
        Repaired f = corruptAndRepair("grant2.tif", b("land grant, same size and time"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        QuarantineRepository.Entry intent = fx.preservation().context().quarantine().forContent(f.contentId()).get(0);
        assertThat(intent.hasEvidence()).isTrue();
        Path source = onVolume(intent.sourcePath());
        byte[] original = Files.readAllBytes(source);
        var modified = Files.getLastModifiedTime(source);
        byte[] lookalike = original.clone();
        lookalike[lookalike.length - 1] ^= 0x55;
        SafeFiles.clearReadOnly(source);
        Files.write(source, lookalike);
        Files.setLastModifiedTime(source, modified);
        assertThat(Files.size(source)).isEqualTo(intent.sourceSize());
        assertThat(Files.getLastModifiedTime(source).to(java.util.concurrent.TimeUnit.MICROSECONDS))
                .isEqualTo(intent.sourceModifiedMicros());

        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).as("the job retried").isFalse();
            assertThat(source).hasBinaryContent(lookalike);
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.preservation().context().quarantine().forContent(f.contentId()).get(0).state())
                    .isEqualTo(QuarantineRepository.State.INTENDED);
        }
        assertThat(fx.preservation().operations().events(fx.admin, f.repairOperation()))
                .anyMatch(e -> e.detail() != null && e.detail().contains("SHA-256"));
        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(source).hasBinaryContent(lookalike);
    }

    /**
     * The state a repair left before V0019: committed, its superseded damaged generation QUARANTINE_PENDING
     * (awaiting an automatic move) and no unresolved record. Built from a V0019 repair by closing its record and
     * restoring the legacy generation state in one transaction.
     */
    private FileContentRepository.Generation legacyQuarantinePending(Repaired f) throws SQLException {
        fx.faults.crashAt(FaultPoints.Point.AFTER_COMMIT_BEFORE_CLEANUP);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(f.repairOperation()).state()).isEqualTo(OperationState.COMMITTED);
        try (Connection c = fx.ds.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("UPDATE gas.unresolved_object SET status = 'RESOLVED', resolution = 'NOT_ARCHIVAL', "
                        + "resolution_detail = 'simulating a pre-V0019 database', resolved_at = now() WHERE content_id = '"
                        + f.contentId() + "' AND status = 'OPEN'");
                st.executeUpdate("UPDATE gas.file_content_generation SET state = 'QUARANTINE_PENDING' WHERE content_id = '"
                        + f.contentId() + "' AND generation = 1");
            }
            c.commit();
            c.setAutoCommit(true);
        }
        FileContentRepository.Generation g1 = generation(f.contentId(), 1);
        assertThat(g1.state()).isEqualTo(GenerationState.QUARANTINE_PENDING);
        return g1;
    }

    @Test
    void v0017_aLegacyIntentWithoutContentEvidenceIsNeverFinishedSilently() throws Exception {
        Repaired f = corruptAndRepair("legacy3.tif", b("legacy intent without evidence"));
        FileContentRepository.Generation g1 = legacyQuarantinePending(f);
        String q = org.genealogyarchive.files.ManagedVolume.quarantinePath(f.repairOperation(), "damaged-g1").value();
        // As written before V0017: no size, time or hash of the object.
        sql().sql("""
                INSERT INTO gas.quarantine_object (quarantine_id, volume_id, operation_id, content_id, source_path,
                                                   quarantine_path, reason, state)
                VALUES (gen_random_uuid(), :v, :op, :c, :src, :q, 'DAMAGED_GENERATION', 'INTENDED')""")
                .param("v", fx.volumeId).param("op", f.repairOperation()).param("c", f.contentId())
                .param("src", g1.storagePath()).param("q", q).update();
        Path source = onVolume(g1.storagePath());
        byte[] before = Files.readAllBytes(source);

        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(source).hasBinaryContent(before);
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.operation(f.repairOperation()).state()).isEqualTo(OperationState.COMMITTED);
        }
        assertThat(fx.preservation().operations().events(fx.admin, f.repairOperation()))
                .anyMatch(e -> e.detail() != null && e.detail().contains("without content evidence"));
    }

    @Test
    void v0017_anObjectSwappedInBetweenTheCheckAndTheRenameIsPutBackUntouched() throws Exception {
        Repaired f = corruptAndRepair("toctou.tif", b("deed, swapped between check and rename"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        Path source = onVolume(generation(f.contentId(), 1).storagePath());
        byte[] swapped = b("swapped in at the last moment");
        fx.faults.armSticky(FaultPoints.Point.BEFORE_QUARANTINE_RENAME, id -> {
            SafeFiles.clearReadOnly(source);
            Files.write(source, swapped);
        });
        assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isFalse();
        fx.faults.clear();
        assertThat(source).as("returned where it was").hasBinaryContent(swapped);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertThat(openRecordAt(u.path()).attested()).as("still unresolved").isTrue();
        assertThat(fx.preservation().operations().events(fx.admin, f.repairOperation()))
                .anyMatch(e -> e.detail() != null && e.detail().contains("returned to where it was"));
        fx.preservation().recovery().runOnce();
        assertThat(source).hasBinaryContent(swapped);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    @Test
    void v0017_restoredBytesThatAreWritableAreNotRestoredUntilProtected() throws Exception {
        byte[] bytes = b("baptism register, put back writable");
        ArchivalFile f = ingestedThenMissing("baptism.tif", bytes);
        Path location = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(location, bytes);
        assertThat(SafeFiles.isReadOnly(location)).isFalse();

        fx.scan(fx.services(), "FULL");
        assertThat(content(f).state()).as("verified bytes, but writable").isEqualTo(ContentState.MISSING);
        assertThat(generation(f.contentId(), 1).vacated()).isTrue();
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 50))
                .anyMatch(x -> x.kind() == FindingKind.NOT_PROTECTED && f.contentId().equals(x.contentId()));
        assertThatThrownBy(() -> fx.preservation().files().download(fx.editor, f.fileId()))
                .isInstanceOf(RuleViolationException.class);
        assertThat(SafeFiles.isReadOnly(location)).as("a scan never changes protection").isFalse();

        SafeFiles.makeReadOnly(location);
        fx.scan(fx.services(), "FULL");
        assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
        assertThat(generation(f.contentId(), 1).vacated()).isFalse();
        try (var d = fx.preservation().files().download(fx.editor, f.fileId())) {
            assertThat(d.stream().readAllBytes()).isEqualTo(bytes);
        }
        assertOwnershipInvariants();
    }

    @Test
    void v0017_reverificationProtectsOnlyVerifiedBytesAndNeverTouchesAnotherObject() throws Exception {
        byte[] bytes = b("burial register, re-verified");
        ArchivalFile f = ingestedThenMissing("burial.tif", bytes);
        Path location = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(location, b("not the archived bytes at all"));
        assertThat(fx.preservation().integrity().reverify(f.contentId(), "test")).isFalse();
        assertThat(SafeFiles.isReadOnly(location)).as("an unexplained object's protection is not changed").isFalse();
        assertThat(content(f).state()).isEqualTo(ContentState.MISSING);

        Files.write(location, bytes);
        assertThat(fx.preservation().integrity().reverify(f.contentId(), "test")).isTrue();
        assertThat(SafeFiles.isReadOnly(location)).isTrue();
        assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
        assertOwnershipInvariants();
    }

    @Test
    void v0017_ownershipTransfersAtomicallyFromOperationToGeneration() throws Exception {
        Repaired f = corruptAndRepair("transfer.tif", b("atomic transfer of ownership"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        assertThatThrownBy(() -> fx.process(f.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        FileOperation repair = fx.operation(f.repairOperation());
        try (var c = fx.ds.getConnection()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.executeUpdate("UPDATE gas.file_operation SET target_created = false WHERE operation_id = '"
                        + repair.operationId() + "'");
                st.executeUpdate("INSERT INTO gas.file_content_generation (content_id, generation, volume_id, storage_path, "
                        + "state, operation_id) VALUES ('" + f.contentId() + "', 98, '" + fx.volumeId + "', '"
                        + repair.targetPath() + "', 'MISSING', '" + repair.operationId() + "')");
            }
            c.commit();
            c.setAutoCommit(true);
        }
        assertThat(sql().sql("SELECT owner_kind FROM gas.physical_location_owner WHERE volume_id = :v AND path = :p")
                .param("v", fx.volumeId).param("p", repair.targetPath()).query(String.class).single()).isEqualTo("GENERATION");
        assertOwnershipInvariants();
    }

    /** Runs two transactions on separate connections: the first claims and holds, the second conflicts. */
    private Throwable concurrentClaims(String isolation, String first, String second, boolean firstCommits) throws Exception {
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var c1 = fx.ds.getConnection(); var c2 = fx.ds.getConnection()) {
            for (var c : List.of(c1, c2)) {
                c.setAutoCommit(false);
                try (var st = c.createStatement()) {
                    st.execute("SET TRANSACTION ISOLATION LEVEL " + isolation);
                    st.execute("SET LOCAL statement_timeout = '20s'");
                }
            }
            try (var st = c1.createStatement()) {
                st.executeUpdate(first);
            }
            var secondDone = pool.submit(() -> {
                try (var st = c2.createStatement()) {
                    st.executeUpdate(second);
                    c2.commit();
                    return null;
                } catch (java.sql.SQLException e) {
                    c2.rollback();
                    return (Throwable) e;
                }
            });
            Thread.sleep(500);
            assertThat(secondDone.isDone()).as("the second claimant waits for the first").isFalse();
            if (firstCommits) {
                c1.commit();
            } else {
                c1.rollback();
            }
            Throwable result = secondDone.get(30, java.util.concurrent.TimeUnit.SECONDS);
            for (var c : List.of(c1, c2)) {
                c.setAutoCommit(true);
            }
            return result;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void v0017_concurrentConflictingClaimsCannotBothCommit() throws Exception {
        ArchivalFile a = ingestAs(fx.editor, "a1.tif", b("concurrency a"), Restriction.NORMAL, null, null);
        ArchivalFile c = ingestAs(fx.editor, "c1.tif", b("concurrency c"), Restriction.NORMAL, null, null);
        UUID opA = fx.preservation().context().operations().writersOfContent(a.contentId()).get(0).operationId();
        assertThat(sql().sql("SHOW transaction_isolation").query(String.class).single())
                .as("the application's isolation level").isEqualTo("read committed");
        int n = 0;
        for (String isolation : List.of("READ COMMITTED", "REPEATABLE READ")) {
            for (boolean generationFirst : List.of(true, false)) {
                String path = "originals/zz/zz/concurrent-" + (n++);
                String claimGeneration = "INSERT INTO gas.file_content_generation (content_id, generation, volume_id, storage_path, "
                        + "state, operation_id) VALUES ('" + c.contentId() + "', " + (50 + n) + ", '" + fx.volumeId + "', '"
                        + path + "', 'MISSING', '" + opA + "')";
                String claimOperation = "UPDATE gas.file_operation SET target_path = '" + path + "', target_created = true "
                        + "WHERE operation_id = '" + opA + "'";
                Throwable refused = generationFirst
                        ? concurrentClaims(isolation, claimGeneration, claimOperation, true)
                        : concurrentClaims(isolation, claimOperation, claimGeneration, true);
                assertThat(refused).as("%s, %s first", isolation, generationFirst ? "generation" : "operation").isNotNull();
                assertThat(refused.getMessage()).containsAnyOf(SINGLE_OWNER, "could not serialize");
                assertThat(sql().sql("SELECT count(*) FROM gas.physical_location_owner WHERE path = :p").param("p", path)
                        .query(Long.class).single()).isEqualTo(1);
                // Leave operation A owning nothing again for the next round.
                sql().sql("UPDATE gas.file_operation SET target_created = false WHERE operation_id = :o").param("o", opA).update();
            }
        }
        // If the first claimant rolls back, the second succeeds.
        String path = "originals/zz/zz/concurrent-rollback";
        Throwable none = concurrentClaims("READ COMMITTED",
                "INSERT INTO gas.file_content_generation (content_id, generation, volume_id, storage_path, state, operation_id) "
                        + "VALUES ('" + c.contentId() + "', 90, '" + fx.volumeId + "', '" + path + "', 'MISSING', '" + opA + "')",
                "UPDATE gas.file_operation SET target_path = '" + path + "', target_created = true WHERE operation_id = '" + opA + "'",
                false);
        assertThat(none).isNull();
        sql().sql("UPDATE gas.file_operation SET target_created = false WHERE operation_id = :o").param("o", opA).update();
        assertOwnershipInvariants();
    }

    // ---- V0018: trusted evidence for every destructive step; no false BYTES_REMOVED --------------------

    private void assertStaysIncomplete(UUID destruction, Path untouched, byte[] bytes, ArchivalFile f) throws Exception {
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            fx.preservation().destruction().process(destruction);
            FileOperation d = fx.operation(destruction);
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(untouched).hasBinaryContent(bytes);
            assertThat(content(f).state()).as("still accounted: not destroyed").isEqualTo(ContentState.DESTROY_PENDING);
        }
    }

    @Test
    void v0018_aCorruptedCurrentCopyKeepsDestructionIncompleteUntilResolved() throws Exception {
        ArchivalFile f = ingestAs(fx.editor, "rotten.tif", b("baptisms 1802, corrupted, never scanned"), Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        byte[] corrupted = b("baptisms 1802, CORRUPTED, never scanned");
        Files.write(stored, corrupted);

        FileOperation op = destroy(f);
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(d.message()).contains(generation(f.contentId(), 1).storagePath(), "left untouched",
                "does not have the authoritative SHA-256");
        assertThat(d.message()).doesNotContain("rotten.tif");
        assertStaysIncomplete(op.operationId(), stored, corrupted, f);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.CURRENT);

        // An Administrator inspects and removes it; then removal completes.
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(fx.operation(op.operationId()).outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(content(f).state()).isEqualTo(ContentState.DESTROYED);
    }

    @Test
    void v0018_anInterruptedCleanupWithAnUnresolvedCopyNeverBecomesBytesRemoved() throws Exception {
        Repaired r = corruptAndRepair("parish.tif", b("parish book, interrupted cleanup with an unresolved copy"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        assertThatThrownBy(() -> fx.process(r.repairOperation())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        // The damaged current copy is replaced by something else after the damage was recorded.
        Path current = onVolume(generation(r.contentId(), 1).storagePath());
        SafeFiles.clearReadOnly(current);
        byte[] other = b("not the recorded damaged copy");
        Files.write(current, other);
        FileOperation op = destroy(fx.file(r.fileId()));
        fx.faults.crashAt(FaultPoints.Point.DURING_PHYSICAL_DELETE);
        assertThatThrownBy(() -> fx.preservation().destruction().process(op.operationId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(op.operationId()).outcome()).isNotEqualTo("BYTES_REMOVED");
        assertStaysIncomplete(op.operationId(), current, other, fx.file(r.fileId()));
        assertThat(onVolume(fx.operation(r.repairOperation()).targetPath())).as("the owned repair copy was removed").doesNotExist();
    }

    @Test
    void v0018_aLegacySettledQuarantineRecordNeverAuthorisesDeletingAReplacement() throws Exception {
        ArchivalFile f = ingestAs(fx.editor, "legacyq.tif", b("legacy settled quarantine record"), Restriction.NORMAL, null, null);
        UUID ingest = fx.preservation().context().operations().writersOfContent(f.contentId()).get(0).operationId();
        String q = org.genealogyarchive.files.ManagedVolume.quarantinePath(ingest, "partial-g1").value();
        // As settled before V0017: the ledger says the move completed, with no content evidence.
        sql().sql("""
                INSERT INTO gas.quarantine_object (quarantine_id, volume_id, operation_id, content_id, source_path,
                                                   quarantine_path, reason, state)
                VALUES (gen_random_uuid(), :v, :op, :c, :src, :q, 'PARTIAL_COPY', 'QUARANTINED')""")
                .param("v", fx.volumeId).param("op", ingest).param("c", f.contentId())
                .param("src", generation(f.contentId(), 1).storagePath() + ".old").param("q", q).update();
        Path atQ = onVolume(q);
        Files.createDirectories(atQ.getParent());
        byte[] replacement = b("an unrelated object now at the recorded quarantine path");
        Files.write(atQ, replacement);

        FileOperation op = destroy(f);
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(d.message()).contains(q, "no trusted hash of what was moved to quarantine identifies it");
        assertThat(fx.storedPath(f)).as("the verified original was removed").doesNotExist();
        assertStaysIncomplete(op.operationId(), atQ, replacement, f);

        Files.delete(atQ);
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
    }

    @Test
    void v0018_aQuarantinedCopyReplacedAfterItsMoveIsNotDeleted() throws Exception {
        ArchivalFile f = repairedCompletely("quar.tif", b("deed, quarantined copy replaced"));
        Path quarantined = onVolume(generation(f.contentId(), 1).quarantinePath());
        SafeFiles.clearReadOnly(quarantined);
        byte[] other = b("replacement in quarantine");
        Files.write(quarantined, other);
        FileOperation op = destroy(f);
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).message()).contains("no trusted hash of what was moved to quarantine identifies it");
        assertStaysIncomplete(op.operationId(), quarantined, other, f);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).as("the verified current copy was removed").isEmpty();
    }

    @Test
    void v0018_damageEvidenceIsKeptWhenAReplacementAppearsAndALaterScanRunsAndTheRepairDoesNotAdoptIt() throws Exception {
        Repaired r = corruptAndRepair("evidence.tif", b("marriage bond, trusted damage evidence"));
        FileContentRepository.Generation g1 = generation(r.contentId(), 1);
        assertThat(g1.hasDamageEvidence()).isTrue();
        Path location = onVolume(g1.storagePath());
        SafeFiles.clearReadOnly(location);
        byte[] replacement = b("an unrelated replacement of the damaged copy");
        Files.write(location, replacement);

        for (String kind : List.of("QUICK", "FULL")) {
            fx.scan(fx.services(), kind);
            FileContentRepository.Generation now = generation(r.contentId(), 1);
            assertThat(now.damagedSha256()).as("%s scan: evidence from detection kept", kind).isEqualTo(g1.damagedSha256());
            assertThat(now.damagedSize()).isEqualTo(g1.damagedSize());
        }
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 50))
                .anyMatch(x -> x.kind() == FindingKind.UNEXPECTED_OBJECT && location.equals(onVolume(x.storagePath())));
        assertThatThrownBy(() -> sql().sql("""
                UPDATE gas.file_content_generation SET damaged_sha256 = repeat('0', 64)
                 WHERE content_id = :c AND generation = 1""").param("c", r.contentId()).update())
                .hasMessageContaining("never replaced");

        FileOperation repaired = fx.process(r.repairOperation());
        assertThat(repaired.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(r.contentId(), 1).state()).as("unresolved, not quarantined").isEqualTo(GenerationState.UNRESOLVED);
        UnresolvedObjectRepository.Entry u = openRecordAt(g1.storagePath());
        assertThat(u.observedSha256()).as("what is there now is only observed").isNotEqualTo(g1.damagedSha256());
        assertThat(u.attested()).isFalse();
        assertThat(location).hasBinaryContent(replacement);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        assertOwnershipInvariants();
    }

    @Test
    void v0018_quarantineIntentsCarryTheAttestedEvidenceNotAFreshHash() throws Exception {
        // V0019: the only move of a damaged copy is the one an Administrator attested; its intent records the
        // attested size and SHA-256 with the ADMINISTRATOR_ATTESTED basis.
        Repaired r = corruptAndRepair("intent.tif", b("will, intent evidence"));
        fx.process(r.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(r.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        QuarantineRepository.Entry intent = fx.preservation().context().quarantine().forContent(r.contentId()).get(0);
        assertThat(intent.sourceSha256()).isEqualTo(u.observedSha256());
        assertThat(intent.sourceSize()).isEqualTo(u.observedSize());
        assertThat(intent.basis()).isEqualTo(QuarantineRepository.Basis.ADMINISTRATOR_ATTESTED);
        assertThat(intent.reason()).isEqualTo(QuarantineRepository.Reason.ATTESTED_OBJECT);
        fx.preservation().recovery().runOnce();
        assertThat(generation(r.contentId(), 1).state()).isEqualTo(GenerationState.QUARANTINED);
        assertThat(fx.preservation().context().unresolved().find(u.unresolvedId()).orElseThrow().resolution())
                .isEqualTo(UnresolvedObjectRepository.Resolution.QUARANTINED);
    }

    // ---- V0019 (V0018 audit): trusted identity evidence and durable unresolved presence -----------------------

    private static String sha256(byte[] bytes) {
        return Sha256.hex(Sha256.digest().digest(bytes));
    }

    private void assertUntouchedAndUnresolved(Path location, byte[] bytes, UUID recordId) throws IOException {
        assertThat(location).as("left exactly where it is").hasBinaryContent(bytes);
        UnresolvedObjectRepository.Entry u = fx.preservation().context().unresolved().find(recordId).orElseThrow();
        assertThat(u.open()).as("still unresolved").isTrue();
    }

    /** Finding 1: an operation's claimed partial copy is replaced (by an unrelated object) after it was created. */
    @Test
    void v0019_aReplacementAtAnOperationsClaimedTargetIsNeitherMovedNorAdoptedAndStaysUnresolved() throws Exception {
        byte[] bytes = large("census, replaced at the claimed target");
        UUID id = partialCopy("rep.tif", bytes);
        FileOperation claimed = fx.operation(id);
        assertThat(claimed.targetCreated()).as("the interrupted creation left a claim").isTrue();
        Path target = onVolume(claimed.targetPath());
        byte[] replacement = b("an unrelated object put at the operation's target");
        Files.write(target, replacement);

        UUID recordId = null;
        for (int i = 0; i < 3; i++) {
            fx.rebuild();
            fx.preservation().recovery().runOnce();
            FileOperation now = fx.operation(id);
            assertThat(now.state()).isEqualTo(OperationState.BLOCKED);
            assertThat(now.blockedReason()).isEqualTo(BlockedReason.STATE_UNKNOWN);
            assertThat(now.message()).contains("not shown to be that copy", "left untouched");
            assertThat(now.targetCreated()).as("the claim became an unresolved record").isFalse();
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.preservation().context().quarantine().forOperation(id)).isEmpty();
            UnresolvedObjectRepository.Entry u = openRecordAt(claimed.targetPath());
            assertThat(u.kind()).isEqualTo(UnresolvedObjectRepository.Kind.OPERATION_COPY);
            assertThat(u.operationId()).isEqualTo(id);
            assertThat(u.observedSha256()).isEqualTo(sha256(replacement));
            if (recordId != null) {
                assertThat(u.unresolvedId()).as("one durable record, kept across restarts").isEqualTo(recordId);
            }
            recordId = u.unresolvedId();
            assertUntouchedAndUnresolved(target, replacement, recordId);
        }
        IntegrityScan scan = fx.preservation().integrity().requestScan(fx.admin, "FULL");
        fx.preservation().integrity().run(scan.scanId());
        assertThat(fx.preservation().integrity().scan(fx.admin, scan.scanId()).message()).contains("still unresolved");
        assertThat(fx.preservation().integrity().findingsOfScan(fx.admin, scan.scanId()))
                .as("recorded as unresolved, not as an unaccounted object").noneMatch(x -> x.kind() == FindingKind.UNEXPECTED_OBJECT);

        // Retrying meets an object the operation did not create: a collision; still untouched.
        fx.preservation().operations().retry(fx.admin, id);
        assertThat(fx.process(id).blockedReason()).isEqualTo(BlockedReason.COLLISION);
        assertUntouchedAndUnresolved(target, replacement, recordId);
    }

    @Test
    void v0019_withoutItsStagedUploadAClaimedPartialCopyCannotBeIdentifiedAndIsLeftUntouched() throws Exception {
        byte[] bytes = large("tax list, staged upload lost");
        UUID id = partialCopy("lost-staging.tif", bytes);
        Path target = onVolume(fx.operation(id).targetPath());
        byte[] partial = Files.readAllBytes(target);
        assertThat(fx.preservation().context().staging().orElseThrow().delete(id)).isTrue();

        FileOperation blocked = fx.process(id);
        assertThat(blocked.state()).isEqualTo(OperationState.BLOCKED);
        assertThat(blocked.message()).contains("staged upload it was copied from is gone");
        assertThat(target).hasBinaryContent(partial);
        assertThat(openRecordAt(blocked.targetPath()).kind()).isEqualTo(UnresolvedObjectRepository.Kind.OPERATION_COPY);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
    }

    @Test
    void v0019_aGenuinePartialCopyIsIdentifiedByItsStagedUploadAndMovedToQuarantine() throws Exception {
        byte[] bytes = large("will book, genuine partial copy");
        UUID id = partialCopy("genuine.tif", bytes);
        assertThat(fx.process(id).state()).isEqualTo(OperationState.COMPLETED);
        QuarantineRepository.Entry moved = fx.preservation().context().quarantine().forOperation(id).get(0);
        assertThat(moved.reason()).isEqualTo(QuarantineRepository.Reason.PARTIAL_COPY);
        assertThat(moved.basis()).isEqualTo(QuarantineRepository.Basis.OPERATION_COPY_VERIFIED);
        byte[] partial = Files.readAllBytes(onVolume(moved.quarantinePath()));
        assertThat(partial.length).isPositive().isLessThan(bytes.length);
        assertThat(java.util.Arrays.copyOf(bytes, partial.length)).as("a prefix of the staged upload").isEqualTo(partial);
        assertThat(moved.sourceSha256()).isEqualTo(sha256(partial));

        FileOperation op = destroy(fx.file(fx.operation(id).fileId()));
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).as("trusted evidence: removed").isEmpty();
    }

    /** Finding 1, cancellation: a move interrupted after its intent never moves an object put there since. */
    @Test
    void v0019_aCancellationInterruptedAfterItsIntentNeverMovesAReplacement() throws Exception {
        byte[] bytes = large("land grant, cancellation interrupted");
        UUID id = partialCopy("cancel.tif", bytes);
        fx.servicesWithRoot(temp.resolve("away")).preservation().processor().process(id, () -> { });
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.WAITING_FOR_VOLUME);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().operations().performCancel(id, "admin1"))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        QuarantineRepository.Entry intent = fx.preservation().context().quarantine().forOperation(id).get(0);
        assertThat(intent.state()).isEqualTo(QuarantineRepository.State.INTENDED);
        assertThat(intent.basis()).isEqualTo(QuarantineRepository.Basis.OPERATION_COPY_VERIFIED);
        Path target = onVolume(intent.sourcePath());
        byte[] replacement = b("put at the target while the cancellation was interrupted");
        Files.write(target, replacement);

        // The retried cancellation (the worker's job) refuses, visibly, and touches nothing.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> fx.preservation().operations().performCancel(id, "admin1"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(target).hasBinaryContent(replacement);
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        }
        assertThat(fx.preservation().operations().events(fx.admin, id))
                .anyMatch(e -> e.detail() != null && e.detail().contains("SHA-256"));
        // Recovery (settling the intent, resuming the operation) refuses as well.
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(target).hasBinaryContent(replacement);
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.preservation().context().quarantine().forOperation(id).get(0).state())
                    .isEqualTo(QuarantineRepository.State.INTENDED);
            assertThat(fx.operation(id).state()).isNotIn(OperationState.COMPLETED, OperationState.CANCELLED);
        }
    }

    /** Finding 1, destruction: a stopped repair's verified copy replaced before removal is never deleted. */
    @Test
    void v0019_destructionNeverDeletesAReplacementOfAStoppedRepairsCopy() throws Exception {
        byte[] bytes = b("tax roll, the repair's copy replaced");
        Repaired f = lostAndRepair("taxr.tif", bytes);
        FileOperation repair = repairStoppedBeforeCommit(f);
        Path copy = onVolume(repair.targetPath());
        SafeFiles.clearReadOnly(copy);
        byte[] replacement = b("replacement of the repair's verified copy");
        Files.write(copy, replacement);

        FileOperation op = destroy(f.file());
        fx.faults.crashAt(FaultPoints.Point.BEFORE_PHYSICAL_DELETE);
        assertThatThrownBy(() -> fx.preservation().destruction().process(op.operationId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(repair.operationId()).state()).isEqualTo(OperationState.CANCELLED);
        assertThat(fx.operation(repair.operationId()).stagingCleaned()).as("kept as the evidence of its copy").isFalse();
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            fx.preservation().destruction().process(op.operationId());
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(d.message()).contains(repair.targetPath(), "left untouched");
            assertThat(copy).hasBinaryContent(replacement);
            assertThat(content(f.file()).state()).isEqualTo(ContentState.DESTROY_PENDING);
        }
        UnresolvedObjectRepository.Entry u = openRecordAt(repair.targetPath());
        assertThat(u.kind()).isEqualTo(UnresolvedObjectRepository.Kind.OPERATION_COPY);
        assertThat(fx.operation(repair.operationId()).targetCreated()).isFalse();

        fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "inspected: not the repair's copy");
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(copy).as("released, never deleted").hasBinaryContent(replacement);
        assertThat(content(f.file()).state()).isEqualTo(ContentState.DESTROYED);
    }

    /**
     * Finding 2: the archival object is replaced before any scan has seen it. The first damage scan only
     * observes the replacement; nothing moves or deletes it on the strength of that observation.
     */
    @Test
    void v0019_anObjectReplacedBeforeTheFirstDamageScanIsNeverMovedOrDeleted() throws Exception {
        byte[] bytes = b("will, replaced before any scan saw it");
        ArchivalFile f = ingestAs(fx.editor, "first.tif", bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        byte[] replacement = b("an unrelated object, put in place of the original before any scan");
        Files.write(stored, replacement);

        fx.scan(fx.services(), "FULL");
        assertThat(content(f).state()).isEqualTo(ContentState.DAMAGED);
        assertThat(generation(f.contentId(), 1).damagedSha256()).as("an observation, diagnostic only")
                .isEqualTo(sha256(replacement));

        assertThat(fx.process(repairOf(f, bytes).operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.UNRESOLVED);
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        assertThat(u.kind()).isEqualTo(UnresolvedObjectRepository.Kind.DAMAGED_COPY);
        assertThat(u.attested()).isFalse();
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        for (int i = 0; i < 3; i++) {
            fx.rebuild();
            fx.preservation().recovery().runOnce();
            fx.scan(fx.services(), "QUICK");
            assertUntouchedAndUnresolved(stored, replacement, u.unresolvedId());
        }

        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertUntouchedAndUnresolved(stored, replacement, u.unresolvedId());
        }
        assertThat(onVolume(generation(f.contentId(), 2).storagePath())).as("the verified repaired copy was removed")
                .doesNotExist();
        fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "inspected: not the archive's will");
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(stored).hasBinaryContent(replacement);
    }

    @Test
    void v0019_anAttestedObjectThatChangedAfterwardsIsNeitherMovedNorDeleted() throws Exception {
        Repaired f = corruptAndRepair("changed.tif", b("deed, changed after the attestation"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        Path location = onVolume(u.path());
        byte[] changed = b("changed after an Administrator attested the object");
        Files.write(location, changed);

        assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isFalse();
        assertUntouchedAndUnresolved(location, changed, u.unresolvedId());
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().destruction().process(op.operationId());
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
        assertThat(d.message()).contains("no longer the object an Administrator attested");
        assertUntouchedAndUnresolved(location, changed, u.unresolvedId());
    }

    /**
     * Finding 3: a repair of missing content whose former location is occupied keeps that presence durably;
     * repeated recovery keeps it unresolved; destruction never reports BYTES_REMOVED while it is there; it closes
     * only when the object is verifiably gone.
     */
    @Test
    void v0019_anUnresolvedObjectAtAFormerGenerationLocationSurvivesRepeatedRecoveryUntilItIsGone() throws Exception {
        byte[] bytes = b("muster roll, object at the former location");
        ArchivalFile f = ingestedThenMissing("muster3.tif", bytes);
        Path former = onVolume(generation(f.contentId(), 1).storagePath());
        byte[] other = b("left there by a restore tool");
        Files.write(former, other);

        assertThat(fx.process(repairOf(f, bytes).operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).as("present but unverified, not vacated")
                .isEqualTo(GenerationState.UNRESOLVED);
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        for (int i = 0; i < 3; i++) {
            fx.rebuild();
            fx.preservation().recovery().runOnce();
            assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.UNRESOLVED);
            assertUntouchedAndUnresolved(former, other, u.unresolvedId());
        }
        IntegrityScan scan = fx.preservation().integrity().requestScan(fx.admin, "QUICK");
        fx.preservation().integrity().run(scan.scanId());
        assertThat(fx.preservation().integrity().scan(fx.admin, scan.scanId()).message())
                .doesNotContain("No problems found").contains("still unresolved");

        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 3; i++) {
            fx.preservation().recovery().runOnce();
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(d.message()).contains(u.path(), "left untouched");
            assertUntouchedAndUnresolved(former, other, u.unresolvedId());
        }
        // Removed outside the application after inspection: the next scan sees it gone and closes the record.
        Files.delete(former);
        fx.scan(fx.services(), "QUICK");
        UnresolvedObjectRepository.Entry closed = fx.preservation().context().unresolved().find(u.unresolvedId()).orElseThrow();
        assertThat(closed.resolution()).isEqualTo(UnresolvedObjectRepository.Resolution.ABSENT);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.MISSING);
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(d.outcome()).isEqualTo("BYTES_REMOVED");
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
    }

    @Test
    void v0019_onlyANamedAdministratorResolvesAnUnresolvedObjectAndAResolutionIsFinal() throws Exception {
        Repaired f = corruptAndRepair("resolve.tif", b("bond, resolution rules"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        assertThatThrownBy(() -> fx.preservation().unresolved().attest(fx.editor, u.unresolvedId(), "mine"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> fx.preservation().unresolved().release(fx.viewer, u.unresolvedId(), "mine"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> fx.preservation().unresolved().attest(Actor.commandLine(), u.unresolvedId(), "system"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), " "))
                .isInstanceOf(RuleViolationException.class);
        assertThat(openRecordAt(u.path()).attested()).isFalse();

        UnresolvedObjectRepository.Entry released = fx.preservation().unresolved().release(fx.admin, u.unresolvedId(),
                "inspected: a copy someone made, not the archive's");
        assertThat(released.resolution()).isEqualTo(UnresolvedObjectRepository.Resolution.NOT_ARCHIVAL);
        assertThat(released.resolvedBy()).isEqualTo(fx.admin.userId());
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.MISSING);
        assertThat(onVolume(u.path())).as("released objects stay where they are").exists();
        assertThatThrownBy(() -> fx.preservation().unresolved().attest(fx.admin, u.unresolvedId(), "again"))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> sql().sql("UPDATE gas.unresolved_object SET resolution = 'ABSENT' WHERE unresolved_id = :id")
                .param("id", u.unresolvedId()).update()).hasMessageContaining("never changed");
        // From now on the object is an unexpected object in managed storage, reported and never touched.
        assertThat(fx.scan(fx.services(), "QUICK"))
                .anyMatch(x -> x.kind() == FindingKind.UNEXPECTED_OBJECT && onVolume(u.path()).equals(onVolume(x.storagePath())));
    }

    /** No code path promotes an observed hash to deletion authority: the ledger says how each move's hash was known. */
    @Test
    void v0019_everyMoveRecordsTheBasisOfItsEvidence() throws Exception {
        UUID partial = partialCopy("basis.tif", large("basis of evidence"));
        fx.process(partial);
        assertThat(fx.preservation().context().quarantine().forOperation(partial))
                .allSatisfy(e -> assertThat(e.basis()).isEqualTo(QuarantineRepository.Basis.OPERATION_COPY_VERIFIED));
        ArchivalFile done = repairedCompletely("basis2.tif", b("basis, attested damaged copy"));
        assertThat(fx.preservation().context().quarantine().forContent(done.contentId()))
                .singleElement().satisfies(e -> {
                    assertThat(e.basis()).isEqualTo(QuarantineRepository.Basis.ADMINISTRATOR_ATTESTED);
                    assertThat(e.reason()).isEqualTo(QuarantineRepository.Reason.ATTESTED_OBJECT);
                });
        assertThat(sql().sql("SELECT count(*) FROM gas.quarantine_object WHERE evidence_basis = 'UNTRUSTED'")
                .query(Long.class).single()).as("nothing this version records is an observation").isZero();
    }

    // ---- V0019 audit: attestation lifecycle, inspection races, inconclusive filesystem access ----------------

    private long attestationJobs(UUID unresolvedId) {
        return sql().sql("SELECT count(*) FROM gas.background_job WHERE kind = 'file.quarantine-attested' "
                + "AND payload->>'id' = :id").param("id", unresolvedId.toString()).query(Long.class).single();
    }

    private UnresolvedObjectRepository.Entry record(UUID unresolvedId) {
        return fx.preservation().context().unresolved().find(unresolvedId).orElseThrow();
    }

    /** Finding 4: the attestation, its audit event and its job commit together, once. */
    @Test
    void v0020_attestationAuditEventAndJobAreOneTransactionAndRepeatsAreIdempotent() throws Exception {
        Repaired f = corruptAndRepair("atomic.tif", b("deed, attested atomically"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        int eventsBefore = fx.preservation().operations().events(fx.admin, u.operationId()).size();

        // The job cannot be written: nothing of the attestation may remain.
        try (Connection c = fx.ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE FUNCTION gas.test_refuse_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "RAISE EXCEPTION 'injected: job table unavailable'; END $$");
            st.execute("CREATE TRIGGER test_refuse_job BEFORE INSERT ON gas.background_job FOR EACH ROW "
                    + "EXECUTE FUNCTION gas.test_refuse_job()");
        }
        assertThatThrownBy(() -> fx.preservation().unresolved().attest(fx.admin, u.unresolvedId(), "inspected"))
                .hasMessageContaining("job table unavailable");
        assertThat(record(u.unresolvedId()).attested()).as("no authorization without its work").isFalse();
        assertThat(fx.preservation().operations().events(fx.admin, u.operationId())).as("no audit event either")
                .hasSize(eventsBefore);
        assertThat(attestationJobs(u.unresolvedId())).isZero();
        try (Connection c = fx.ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TRIGGER test_refuse_job ON gas.background_job");
            st.execute("DROP FUNCTION gas.test_refuse_job()");
        }

        UnresolvedObjectRepository.Entry first = fx.preservation().unresolved().attest(fx.admin, u.unresolvedId(), "inspected");
        assertThat(first.attested()).isTrue();
        assertThat(attestationJobs(u.unresolvedId())).isEqualTo(1);
        assertThat(fx.preservation().operations().events(fx.admin, u.operationId())).hasSize(eventsBefore + 1)
                .anyMatch(e -> e.detail() != null && e.detail().contains("attested by"));
        // Repeated (a double submit, a retried request): the same attestation, no second job or event.
        UnresolvedObjectRepository.Entry again = fx.preservation().unresolved().attest(fx.admin, u.unresolvedId(), "again");
        assertThat(again.attestedAt()).isEqualTo(first.attestedAt());
        assertThat(attestationJobs(u.unresolvedId())).isEqualTo(1);
        assertThat(fx.preservation().operations().events(fx.admin, u.operationId())).hasSize(eventsBefore + 1);
    }

    /** Finding 1: released before the queued worker ran - the worker never moves it, nor does any retry. */
    @Test
    void v0020_aReleaseBeforeExecutionMeansNoWorkerRetryOrRecoveryMovesTheObject() throws Exception {
        Repaired f = corruptAndRepair("rel1.tif", b("will, released before the worker ran"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        Path location = onVolume(u.path());
        byte[] object = Files.readAllBytes(location);
        fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "inspected again: not ours after all");
        assertThat(record(u.unresolvedId()).resolution()).isEqualTo(UnresolvedObjectRepository.Resolution.NOT_ARCHIVAL);
        assertThat(fx.preservation().operations().events(fx.admin, u.operationId()))
                .anyMatch(e -> e.detail() != null && e.detail().contains("released by") && e.detail().contains("withdrawn"));
        for (int i = 0; i < 3; i++) {
            fx.rebuild();
            assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).as("the queued job").isTrue();
            fx.preservation().recovery().runOnce();
            assertThat(location).hasBinaryContent(object);
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.preservation().context().quarantine().forContent(f.contentId())).isEmpty();
        }
        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(location).as("released objects are never deleted").hasBinaryContent(object);
    }

    /** Finding 1: a release attempted while the worker is executing is refused; the move finishes; then final. */
    @Test
    void v0020_aReleaseDuringExecutionIsRefusedAndCannotInvalidateTheMove() throws Exception {
        Repaired f = corruptAndRepair("rel2.tif", b("will, release attempted during the move"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        List<Throwable> refusals = new java.util.ArrayList<>();
        for (FaultPoints.Point during : List.of(FaultPoints.Point.BEFORE_QUARANTINE_MOVE, FaultPoints.Point.AFTER_QUARANTINE_INTENT,
                FaultPoints.Point.BEFORE_QUARANTINE_RENAME)) {
            fx.faults.arm(during, id -> refusals.add(org.assertj.core.api.Assertions.catchThrowable(() ->
                    fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "changed my mind"))));
        }
        assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isTrue();
        fx.faults.clear();
        assertThat(refusals).hasSize(3).allSatisfy(t -> assertThat(t).isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("being worked on right now"));
        assertThat(record(u.unresolvedId()).resolution()).isEqualTo(UnresolvedObjectRepository.Resolution.QUARANTINED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.QUARANTINED);
        assertThatThrownBy(() -> fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "too late"))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("already resolved (QUARANTINED)");
        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).as("an honoured attestation authorises removal").isEmpty();
    }

    /** Finding 1: worker crash after its intent; a release is refused; recovery (repeatedly) finishes the move. */
    @Test
    void v0020_afterAWorkerCrashTheRecordedMoveIsFinishedAndAReleaseIsRefusedWithItsDisposition() throws Exception {
        Repaired f = corruptAndRepair("rel3.tif", b("will, worker crashed after the intent"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        String destination = UnresolvedObjects.attestedDestination(u).value();
        assertThatThrownBy(() -> fx.preservation().unresolved().release(fx.admin, u.unresolvedId(), "after the crash"))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("already recorded").hasMessageContaining(destination);
        assertThat(record(u.unresolvedId()).open()).isTrue();
        for (int i = 0; i < 3; i++) {
            fx.rebuild();
            fx.preservation().recovery().runOnce();
            assertThat(fx.preservation().unresolved().quarantineAttested(u.unresolvedId())).isTrue();
        }
        assertThat(record(u.unresolvedId()).resolution()).isEqualTo(UnresolvedObjectRepository.Resolution.QUARANTINED);
        assertThat(onVolume(destination)).exists();
        assertThat(fx.preservation().context().quarantine().forContent(f.contentId())).singleElement()
                .satisfies(e -> assertThat(e.state()).isEqualTo(QuarantineRepository.State.QUARANTINED));
    }

    /**
     * Finding 1: a record resolved behind the lifecycle's back (as a pre-correction release could) never lets
     * recovery finish the move, and a move confirmed afterwards authorises no deletion.
     */
    @Test
    void v0020_staleAuthorizationIsNeverResurrectedByRecoveryNorInheritedByDestruction() throws Exception {
        Repaired f = corruptAndRepair("rel4.tif", b("will, stale authorization"));
        fx.process(f.repairOperation());
        UnresolvedObjectRepository.Entry u = attestGeneration(f.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(u.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        releasedBehindTheLifecycle(f.contentId(), u);
        Path location = onVolume(u.path());
        byte[] object = Files.readAllBytes(location);
        for (int i = 0; i < 3; i++) {
            fx.rebuild();
            fx.preservation().recovery().runOnce();
            assertThat(location).as("never moved under a released authorization").hasBinaryContent(object);
            assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).isEmpty();
            assertThat(fx.preservation().context().quarantine().forContent(f.contentId()).get(0).state())
                    .isEqualTo(QuarantineRepository.State.INTENDED);
        }

        // The same, with the rename already done before the release: the move is only confirmed (bookkeeping)
        // and its hash authorises no deletion.
        Repaired g = corruptAndRepair("rel5.tif", b("will, moved before a stale release"));
        fx.process(g.repairOperation());
        UnresolvedObjectRepository.Entry v = attestGeneration(g.contentId(), 1);
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.preservation().unresolved().quarantineAttested(v.unresolvedId()))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        releasedBehindTheLifecycle(g.contentId(), v);
        fx.preservation().recovery().runOnce();
        Path inQuarantine = onVolume(UnresolvedObjects.attestedDestination(v).value());
        byte[] moved = Files.readAllBytes(inQuarantine);
        FileOperation op = destroy(fx.file(g.fileId()));
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).as(d.message()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(inQuarantine).as("no deletion authority inherited").hasBinaryContent(moved);
        }
    }

    /** Simulates a release committed without the lifecycle's checks (the audited defect), in one transaction. */
    private void releasedBehindTheLifecycle(UUID contentId, UnresolvedObjectRepository.Entry u) throws SQLException {
        try (Connection c = fx.ds.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("UPDATE gas.unresolved_object SET status = 'RESOLVED', resolution = 'NOT_ARCHIVAL', "
                        + "resolution_detail = 'released without the lifecycle', resolved_at = now() "
                        + "WHERE unresolved_id = '" + u.unresolvedId() + "'");
                st.executeUpdate("UPDATE gas.file_content_generation SET state = 'MISSING' WHERE content_id = '"
                        + contentId + "' AND generation = 1 AND state = 'UNRESOLVED'");
            }
            c.commit();
            c.setAutoCommit(true);
        }
    }

    /** Finding 2: an object appears after the repair looked at the old location and before its commit. */
    @Test
    void v0020_anObjectAppearingBetweenTheRepairsInspectionAndCommitIsNeverExcludedFromCleanup() throws Exception {
        byte[] bytes = b("parish register, object appears at the commit boundary");
        ArchivalFile f = ingestedThenMissing("boundary.tif", bytes);
        Path old = onVolume(generation(f.contentId(), 1).storagePath());
        byte[] intruder = b("appeared between inspection and commit");
        fx.faults.arm(FaultPoints.Point.AFTER_SUPERSEDED_INSPECTION, id -> Files.write(old, intruder));
        FileOperation repaired = fx.process(repairOf(f, bytes).operationId());
        assertThat(repaired.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).as("the inspection saw nothing").isEqualTo(GenerationState.MISSING);
        assertThat(old).hasBinaryContent(intruder);
        // Scans keep reporting it (never silently accounted away).
        assertThat(fx.scan(fx.services(), "QUICK"))
                .anyMatch(x -> x.kind() == FindingKind.UNEXPECTED_OBJECT && old.equals(onVolume(x.storagePath())));

        FileOperation op = destroy(fx.file(f.fileId()));
        FileOperation d = releasedThenCompleted(op, old, intruder);
        assertThat(d.message()).contains("left untouched");
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(old);
    }

    /** Finding 2: the authoritative bytes put back at a former location are removed; nothing else is. */
    @Test
    void v0020_theAuthoritativeBytesAtAFormerLocationAreRemovedWithTheContent() throws Exception {
        byte[] bytes = b("parish register, original restored to its old place");
        ArchivalFile f = ingestedThenMissing("restored.tif", bytes);
        Path old = onVolume(generation(f.contentId(), 1).storagePath());
        assertThat(fx.process(repairOf(f, bytes).operationId()).state()).isEqualTo(OperationState.COMPLETED);
        Files.write(old, bytes);
        FileOperation op = destroy(fx.file(f.fileId()));
        fx.preservation().recovery().runOnce();
        FileOperation d = fx.operation(op.operationId());
        assertThat(d.state()).as(d.message()).isEqualTo(OperationState.COMPLETED);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).isEmpty();
    }

    /** Finding 3: an inaccessible original is not missing; recovery and scans retry once access returns. */
    @Test
    void v0020_anInaccessibleOriginalIsNeverRecordedMissing() throws Exception {
        byte[] bytes = b("census, inaccessible but present");
        ArchivalFile f = ingestAs(fx.editor, "denied.tif", bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        for (Path blocked : List.of(stored, stored.getParent())) {
            for (int mode = 0; mode < 2; mode++) {
                if (mode == 0) {
                    fx.faults.denyAccess(blocked);
                } else {
                    fx.faults.failIo(blocked);
                }
                List<IntegrityFinding> found = fx.scan(fx.services(), "QUICK");
                assertThat(found).noneMatch(x -> x.kind() == FindingKind.MISSING);
                assertThat(found).anyMatch(x -> x.kind() == FindingKind.UNREADABLE);
                assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
                assertThatThrownBy(() -> fx.preservation().files().download(fx.editor, f.fileId()))
                        .isInstanceOf(RuleViolationException.class).hasMessageContaining("cannot be read right now");
                assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
                fx.faults.restoreAccess(blocked);
            }
        }
        // A vanished mount (the marker gone with it) is not absence either.
        fx.faults.vanish(fx.volumeRoot.resolve("originals"));
        fx.faults.vanish(fx.volumeRoot.resolve(org.genealogyarchive.files.VolumeMarker.FILE_NAME));
        assertThat(fx.scan(fx.services(), "QUICK")).noneMatch(x -> x.kind() == FindingKind.MISSING);
        assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
        fx.faults.clear();
        try (var d = fx.preservation().files().download(fx.editor, f.fileId())) {
            assertThat(d).isNotNull();
        }
    }

    /** Finding 3: an unresolved record is closed only on verified absence, never on an inconclusive look. */
    @Test
    void v0020_anUnresolvedRecordIsNotClosedByAnInconclusiveInspection() throws Exception {
        byte[] bytes = b("muster roll, record kept while inaccessible");
        ArchivalFile f = ingestedThenMissing("keep.tif", bytes);
        Path former = onVolume(generation(f.contentId(), 1).storagePath());
        Files.write(former, b("an object at the former location"));
        assertThat(fx.process(repairOf(f, bytes).operationId()).state()).isEqualTo(OperationState.COMPLETED);
        UnresolvedObjectRepository.Entry u = openRecordAt(generation(f.contentId(), 1).storagePath());
        Files.delete(former);
        fx.faults.denyAccess(former.getParent());
        FileOperation op = destroy(fx.file(f.fileId()));
        for (int i = 0; i < 3; i++) {
            fx.scan(fx.services(), "QUICK");
            fx.preservation().recovery().runOnce();
            assertThat(record(u.unresolvedId()).open()).as("not closed on an inconclusive look").isTrue();
            assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.UNRESOLVED);
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(d.message()).contains("could not be determined");
        }
        fx.faults.restoreAccess(former.getParent());
        fx.preservation().recovery().runOnce();
        assertThat(record(u.unresolvedId()).resolution()).isEqualTo(UnresolvedObjectRepository.Resolution.ABSENT);
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
    }

    /** Finding 3: repair never retires an inaccessible location as MISSING; it commits after access returns. */
    @Test
    void v0020_aRepairDoesNotCommitWhileTheSupersededLocationCannotBeExamined() throws Exception {
        byte[] bytes = b("tax list, superseded location inaccessible");
        ArchivalFile f = ingestedThenMissing("repairdenied.tif", bytes);
        Path old = onVolume(generation(f.contentId(), 1).storagePath());
        UUID repairId = repairOf(f, bytes).operationId();
        // Only the superseded location is inaccessible (the new generation's location is not).
        fx.faults.denyAccess(old);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> fx.process(repairId)).isInstanceOf(RuntimeException.class);
            assertThat(fx.operation(repairId).state()).isNotIn(OperationState.COMMITTED, OperationState.COMPLETED);
            assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.CURRENT);
            assertThat(content(f).state()).isEqualTo(ContentState.MISSING);
        }
        fx.faults.restoreAccess(old);
        assertThat(fx.process(repairId).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(generation(f.contentId(), 1).state()).isEqualTo(GenerationState.MISSING);
    }

    /** Finding 3: an inconclusive look at a move's source never records SOURCE_ABSENT. */
    @Test
    void v0020_aQuarantineMoveIsNotSettledAsSourceAbsentWhileItsSourceCannotBeExamined() throws Exception {
        UUID id = partialCopy("settle-denied.tif", large("land grant, move settled after access returns"));
        fx.faults.crashAt(FaultPoints.Point.AFTER_QUARANTINE_INTENT);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        QuarantineRepository.Entry intent = fx.preservation().context().quarantine().forOperation(id).get(0);
        Path source = onVolume(intent.sourcePath());
        fx.faults.denyAccess(source);
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            assertThat(fx.preservation().context().quarantine().forOperation(id).get(0).state())
                    .isEqualTo(QuarantineRepository.State.INTENDED);
            assertThat(source).exists();
        }
        fx.faults.restoreAccess(source);
        fx.preservation().recovery().runOnce();
        assertThat(fx.preservation().context().quarantine().forOperation(id).get(0).state())
                .isEqualTo(QuarantineRepository.State.QUARANTINED);
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.COMPLETED);
    }

    /** Finding 3: destruction never counts an inaccessible copy as removed. */
    @Test
    void v0020_destructionNeverReportsBytesRemovedWhileACopyCannotBeExamined() throws Exception {
        byte[] bytes = b("deed, copy inaccessible during destruction");
        ArchivalFile f = ingestAs(fx.editor, "destroydenied.tif", bytes, Restriction.NORMAL, null, null);
        Path stored = fx.storedPath(f);
        FileOperation op = destroy(f);
        fx.faults.denyAccess(stored);
        for (int i = 0; i < 2; i++) {
            fx.preservation().recovery().runOnce();
            FileOperation d = fx.operation(op.operationId());
            assertThat(d.state()).isEqualTo(OperationState.CLEANUP_INCOMPLETE);
            assertThat(d.outcome()).isNotEqualTo("BYTES_REMOVED");
            assertThat(d.message()).contains("could not be determined").doesNotContain("destroydenied.tif");
            assertThat(stored).exists();
        }
        fx.faults.restoreAccess(stored);
        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(stored).doesNotExist();
    }
}
