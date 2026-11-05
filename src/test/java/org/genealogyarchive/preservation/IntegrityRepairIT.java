package org.genealogyarchive.preservation;

import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.platform.HostPlatform;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.genealogyarchive.testsupport.ScriptedFaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integrity verification and verified repair (IV-001..IV-007, TX-006; P2-I; requirement 4). */
class IntegrityRepairIT {

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

    private List<IntegrityFinding> scan(String kind) {
        IntegrityScan s = fx.preservation().integrity().requestScan(fx.admin, kind);
        fx.preservation().integrity().run(s.scanId());
        assertThat(fx.preservation().integrity().scan(fx.admin, s.scanId()).state()).isEqualTo("COMPLETED");
        return fx.preservation().integrity().findingsOfScan(fx.admin, s.scanId());
    }

    private FileContent content(ArchivalFile f) {
        return fx.preservation().context().contents().find(f.contentId()).orElseThrow();
    }

    private FileOperation repair(ArchivalFile f, byte[] bytes) {
        FileOperation op = fx.preservation().repair().receive(fx.admin, f.contentId(), "backup copy.tif", bytes.length,
                new ByteArrayInputStream(bytes));
        return fx.process(op.operationId());
    }

    @Test
    void iv001_aHealthyArchivePassesQuickAndFullScans() {
        fx.ingest(fx.editor, "a.tif", b("alpha"));
        fx.ingest(fx.editor, "b.tif", b("beta"));
        assertThat(scan("QUICK")).isEmpty();
        assertThat(scan("FULL")).isEmpty();
    }

    @Test
    void iv002_aMissingOriginalIsReportedAndRepairedFromAVerifiedCopy() throws Exception {
        byte[] bytes = b("marriage register entry");
        ArchivalFile f = fx.ingest(fx.editor, "m.tif", bytes);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);

        assertThat(scan("QUICK")).extracting(IntegrityFinding::kind).containsExactly(FindingKind.MISSING);
        assertThat(content(f).state()).isEqualTo(ContentState.MISSING);
        assertThatThrownBy(() -> fx.preservation().files().download(fx.editor, f.fileId()))
                .isInstanceOf(RuleViolationException.class);

        FileOperation op = repair(f, bytes);
        assertThat(op.state()).isEqualTo(OperationState.COMPLETED);
        FileContent repaired = content(f);
        assertThat(repaired.state()).isEqualTo(ContentState.ACTIVE);
        assertThat(repaired.currentGeneration()).isEqualTo(2);
        assertThat(repaired.sha256()).as("authoritative hash unchanged").isEqualTo(content(f).sha256());
        assertThat(Files.readAllBytes(fx.storedPath(f))).isEqualTo(bytes);
        assertThat(SafeFiles.isReadOnly(fx.storedPath(f))).isTrue();
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 50)).isEmpty();
        assertThat(fx.preservation().context().contents().generations(f.contentId()))
                .extracting(FileContentRepository.Generation::state)
                .containsExactlyInAnyOrder(GenerationState.MISSING, GenerationState.CURRENT);
        try (var d = fx.preservation().files().download(fx.editor, f.fileId())) {
            assertThat(d.stream().readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void iv003_silentCorruptionIsFoundByAFullScanAndTheDamagedCopyIsKeptInQuarantine() throws Exception {
        byte[] bytes = b("probate inventory, page 2");
        ArchivalFile f = fx.ingest(fx.editor, "p.tif", bytes);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        byte[] rotten = bytes.clone();
        rotten[3] ^= 0x20;
        Files.write(stored, rotten);
        SafeFiles.makeReadOnly(stored);

        assertThat(scan("QUICK")).as("same size: a quick check cannot see it").isEmpty();
        assertThat(scan("FULL")).extracting(IntegrityFinding::kind).containsExactly(FindingKind.HASH_MISMATCH);
        assertThat(content(f).state()).isEqualTo(ContentState.DAMAGED);
        String authoritative = content(f).sha256();

        FileOperation wrong = repair(f, b("some other file entirely"));
        assertThat(wrong.state()).isEqualTo(OperationState.REJECTED);
        assertThat(content(f).state()).as("nothing changed").isEqualTo(ContentState.DAMAGED);
        assertThat(Files.readAllBytes(stored)).isEqualTo(rotten);

        FileOperation good = repair(f, bytes);
        assertThat(good.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(content(f).sha256()).isEqualTo(authoritative);
        var gens = fx.preservation().context().contents().generations(f.contentId());
        FileContentRepository.Generation damaged = gens.stream().filter(g -> g.generation() == 1).findFirst().orElseThrow();
        assertThat(damaged.state()).isEqualTo(GenerationState.QUARANTINED);
        Path quarantined = PortableRelativePath.of(damaged.quarantinePath()).toNative(fx.volumeRoot);
        assertThat(Files.readAllBytes(quarantined)).as("damaged copy retained for examination").isEqualTo(rotten);
        assertThat(Files.exists(stored)).isFalse();
        assertThat(fx.preservation().files().history(fx.admin, f.fileId(), fx.services().history()))
                .extracting(org.genealogyarchive.history.ChangeRecord::changeKind).contains("REPAIRED");
        assertThat(scan("FULL")).isEmpty();
    }

    @Test
    void requirement4_aCrashBeforeTheQuarantineMoveKeepsTheDamagedGenerationAccountedFor() throws Exception {
        byte[] bytes = b("census return, household 17");
        ArchivalFile f = fx.ingest(fx.editor, "h.tif", bytes);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.write(stored, b("census return, household 71"));
        scan("FULL");

        FileOperation op = fx.preservation().repair().receive(fx.admin, f.contentId(), "good.tif", bytes.length,
                new ByteArrayInputStream(bytes));
        fx.faults.crashAt(FaultPoints.Point.BEFORE_QUARANTINE_MOVE);
        assertThatThrownBy(() -> fx.process(op.operationId())).isInstanceOf(ScriptedFaults.SimulatedCrash.class);

        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMMITTED);
        var pending = fx.preservation().context().contents().generations(f.contentId()).stream()
                .filter(g -> g.generation() == 1).findFirst().orElseThrow();
        assertThat(pending.state()).isEqualTo(GenerationState.QUARANTINE_PENDING);
        assertThat(Files.exists(stored)).isTrue();
        assertThat(scan("QUICK")).as("the pending damaged copy is accounted for, not 'unexpected'")
                .noneMatch(x -> x.kind() == FindingKind.UNEXPECTED_OBJECT);

        fx.preservation().recovery().runOnce();
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(fx.preservation().context().contents().generations(f.contentId()).stream()
                .filter(g -> g.generation() == 1).findFirst().orElseThrow().state()).isEqualTo(GenerationState.QUARANTINED);
    }

    @Test
    void repairOfHealthyContentIsRefused() {
        byte[] bytes = b("fine");
        ArchivalFile f = fx.ingest(fx.editor, "f.tif", bytes);
        FileOperation op = repair(f, bytes);
        assertThat(op.state()).isIn(OperationState.REJECTED);
        assertThat(content(f).currentGeneration()).isEqualTo(1);
    }

    @Test
    void repairIsAdministratorOnly() {
        ArchivalFile f = fx.ingest(fx.editor, "f.tif", b("x"));
        assertThatThrownBy(() -> fx.preservation().repair().receive(fx.editor, f.contentId(), "x", 1,
                new ByteArrayInputStream(b("x")))).isInstanceOf(org.genealogyarchive.access.AccessDeniedException.class);
    }

    @Test
    void iv004_lostProtectionIsReportedAndRestoredByWorkerReverification() throws Exception {
        ArchivalFile f = fx.ingest(fx.editor, "u.tif", b("unprotected"));
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        assertThat(scan("QUICK")).extracting(IntegrityFinding::kind).containsExactly(FindingKind.NOT_PROTECTED);

        fx.preservation().integrity().requestReverify(fx.admin, f.contentId());
        assertThat(SafeFiles.isReadOnly(stored)).as("the web request changes nothing on disk").isFalse();
        assertThat(fx.preservation().integrity().reverify(f.contentId(), "admin1")).isTrue();
        assertThat(SafeFiles.isReadOnly(stored)).isTrue();
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 10)).isEmpty();
    }

    @Test
    void iv005_unexpectedObjectsAndStagingLeftoversAreReportedButNeverAdoptedOrDeleted() throws Exception {
        fx.ingest(fx.editor, "a.tif", b("a"));
        Path stray = fx.volumeRoot.resolve("originals").resolve("stray.txt");
        Files.writeString(stray, "copied in by hand");
        Path leftover = fx.staging.resolve(UUID.randomUUID() + ".part");
        Files.writeString(leftover, "orphan");

        assertThat(scan("QUICK")).extracting(IntegrityFinding::kind)
                .containsExactlyInAnyOrder(FindingKind.UNEXPECTED_OBJECT, FindingKind.STAGING_LEFTOVER);
        assertThat(Files.exists(stray)).isTrue();
        assertThat(Files.exists(leftover)).isTrue();
        assertThat(scan("QUICK")).as("an open finding is not duplicated").isEmpty();
    }

    @Test
    void iv006_aSymbolicLinkInManagedStorageIsReportedAndNotFollowed() throws Exception {
        Assumptions.assumeTrue(SafeFiles.host() != HostPlatform.WINDOWS,
                "creating symbolic links on Windows needs a privilege CI accounts do not hold; reparse detection is unit-tested");
        fx.ingest(fx.editor, "a.tif", b("a"));
        Path outside = Files.writeString(temp.resolve("outside.txt"), "outside");
        Files.createSymbolicLink(fx.volumeRoot.resolve("originals").resolve("link.txt"), outside);
        assertThat(scan("QUICK")).extracting(IntegrityFinding::kind).containsExactly(FindingKind.LINK_OR_REPARSE);
    }

    @Test
    void iv007_anUnavailableVolumeIsReportedNotTreatedAsMissingContent() {
        ArchivalFile f = fx.ingest(fx.editor, "a.tif", b("a"));
        var other = fx.servicesWithRoot(temp.resolve("unplugged")).preservation();
        IntegrityScan s = other.integrity().requestScan(fx.admin, "QUICK");
        other.integrity().run(s.scanId());
        assertThat(other.integrity().findingsOfScan(fx.admin, s.scanId())).extracting(IntegrityFinding::kind)
                .containsExactly(FindingKind.VOLUME_UNAVAILABLE);
        assertThat(content(f).state()).isEqualTo(ContentState.ACTIVE);
    }

    @Test
    void aDownloadOfAnOriginalWithTheWrongSizeIsRefusedAndRecorded() throws Exception {
        ArchivalFile f = fx.ingest(fx.editor, "s.tif", b("twelve bytes"));
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.writeString(stored, "short");
        assertThatThrownBy(() -> fx.preservation().files().download(fx.editor, f.fileId()))
                .isInstanceOf(RuleViolationException.class);
        assertThat(fx.preservation().integrity().openFindings(fx.admin, 10)).extracting(IntegrityFinding::kind)
                .containsExactly(FindingKind.SIZE_MISMATCH);
    }
}
