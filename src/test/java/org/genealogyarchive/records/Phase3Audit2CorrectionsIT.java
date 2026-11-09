package org.genealogyarchive.records;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the second independent Phase 3 audit (audited head 12cdd70): GAS-P3-003 (run summaries
 * attribute each contribution to its File and its Record), GAS-P3-004 (a later-chunk classification source is
 * validated where the Record is created) and GAS-P3-010 (upload-batch metadata follows the Phase 2 creator /
 * MANAGE_PRESERVATION rule). Each reproduces the audit's scenario and fails against 12cdd70.
 */
class Phase3Audit2CorrectionsIT {

    @TempDir
    Path temp;
    RecordsFixture rx;
    JdbcClient jdbc;

    @BeforeEach
    void setUp() throws IOException {
        rx = RecordsFixture.create(temp);
        jdbc = JdbcClient.create(rx.fx.ds);
    }

    @AfterEach
    void tearDown() {
        rx.close();
    }

    private void restrictRecord(UUID recordId) {
        ArchivalRecord r = rx.records().records().get(rx.admin, recordId);
        rx.records().records().correct(rx.admin, r.recordId(), r.version(), new RecordService.RecordInput(
                r.classificationCode(), r.summary(), r.sourceId(), r.sourceLocator(), r.recordDateText(),
                r.filingDateText(), r.transcriptionText(), r.abstractText(), Restriction.RESTRICTED), null);
    }

    private void restrictFile(UUID fileId) {
        ArchivalFile cur = rx.fx.file(fileId);
        rx.fx.preservation().files().correct(rx.admin, cur.fileId(), cur.version(), cur.managedFilename(), 0,
                Restriction.RESTRICTED, null);
    }

    private long count(String sql, Object p) {
        return jdbc.sql(sql).param("p", p).query(Long.class).single();
    }

    // ---- GAS-P3-003 ------------------------------------------------------------------------------

    /** Test 1: a run whose only Record is restricted later (its Files stay NORMAL) disappears for others. */
    @Test
    void gasP3_003_runOfOnlyARestrictedRecordDisappears() {
        List<ArchivalFile> f = rx.files(2);
        CaptureSession s = rx.session("One Record");
        rx.add(s, f);
        rx.start(s, f.get(0));
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(rx.records().grouping().runs(rx.viewer, s.captureSessionId())).hasSize(1);
        restrictRecord(rx.row(f.get(0).fileId()).appliedRecordId());

        for (Actor a : List.of(rx.editor, rx.viewer)) {
            assertThat(rx.records().grouping().runs(a, s.captureSessionId())).as("no authorized contribution").isEmpty();
            // The Files are still visible and still shown as grouped (approved indicator, unchanged).
            assertThat(rx.records().sessions().worksheet(a, s.captureSessionId(), 0, 50).rows())
                    .allSatisfy(w -> {
                        assertThat(w.row().applied()).isTrue();
                        assertThat(w.row().appliedRecordId()).isNull();
                    });
        }
        // Test 4/5: the Administrator keeps the full accounting; the stored run is not rewritten.
        assertThat(rx.records().grouping().runs(rx.admin, s.captureSessionId())).singleElement()
                .satisfies(v -> assertThat(v.rowsApplied()).isEqualTo(2));
        assertThat(rx.records().context().runs().find(run.runId()).orElseThrow()).isEqualTo(run);
    }

    /** Test 2: a mixed run counts only Files that are visible AND whose Record is visible. */
    @Test
    void gasP3_003_mixedRunCountsOnlyAuthorizedContributions() {
        List<ArchivalFile> f = rx.files(6);
        CaptureSession s = rx.session("Three Records");
        rx.add(s, f);
        rx.start(s, f.get(0));   // Record A: f0, f1
        rx.start(s, f.get(2));   // Record B: f2, f3
        rx.start(s, f.get(4));   // Record C: f4, f5
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        restrictRecord(rx.row(f.get(2).fileId()).appliedRecordId());   // B hidden, its Files NORMAL
        restrictFile(f.get(5).fileId());                                // one File of C hidden
        for (Actor a : List.of(rx.editor, rx.viewer)) {
            assertThat(rx.records().grouping().runs(a, s.captureSessionId())).singleElement().satisfies(v -> {
                assertThat(v.rowsApplied()).as("f0, f1, f4").isEqualTo(3);
                assertThat(v.recordsCreated()).as("A and C").isEqualTo(2);
                assertThat(v.recordsExtended()).isZero();
                assertThat(v.rowsHeld()).isNull();
                assertThat(v.rowsConflicted()).isNull();
                assertThat(v.rowsUngrouped()).isNull();
            });
        }
        assertThat(rx.records().grouping().runs(rx.admin, s.captureSessionId())).singleElement().satisfies(v -> {
            assertThat(v.rowsApplied()).isEqualTo(6);
            assertThat(v.recordsCreated()).isEqualTo(3);
        });
        // Test 5: lifting the restriction restores the contribution at once; stored data never changed.
        ArchivalRecord b = rx.records().records().get(rx.admin, rx.row(f.get(2).fileId()).appliedRecordId());
        rx.records().records().correct(rx.admin, b.recordId(), b.version(), new RecordService.RecordInput(null, null,
                null, null, null, null, null, null, Restriction.NORMAL), null);
        assertThat(rx.records().grouping().runs(rx.viewer, s.captureSessionId())).singleElement()
                .satisfies(v -> assertThat(v.rowsApplied()).isEqualTo(5));
    }

    /** An extended Record that becomes hidden is not counted as extended either. */
    @Test
    void gasP3_003_extendedRecordFollowsItsVisibility() {
        ArchivalFile a = rx.file();
        CaptureSession s = rx.session("Extend");
        rx.add(s, List.of(a));
        rx.start(s, a);
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        ArchivalFile b = rx.file();
        rx.add(s, List.of(b));
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(rx.records().grouping().runs(rx.viewer, s.captureSessionId()))
                .extracting(GroupingRunView::recordsExtended).contains(1);
        restrictRecord(rx.row(a.fileId()).appliedRecordId());
        assertThat(rx.records().grouping().runs(rx.viewer, s.captureSessionId())).isEmpty();
    }

    // ---- GAS-P3-004 ------------------------------------------------------------------------------

    private record Big(CaptureSession session, List<UUID> ids) {
    }

    /** One 1,001-row group: row 1 starts it, rows 1-1,000 unclassified, row 1,001 classified DEED. */
    private Big bigGroupClassifiedOnlyByTheLastRow() {
        List<UUID> ids = rx.syntheticFiles(1001);
        CaptureSession s = rx.session("Classification in the second chunk");
        rx.records().sessions().addFiles(rx.admin, s.captureSessionId(), ids);
        rx.records().sessions().saveMarks(rx.admin, s.captureSessionId(), List.of(
                new CaptureSessionService.MarkEdit(ids.get(0), rx.row(ids.get(0)).version(), true, null, null, null),
                new CaptureSessionService.MarkEdit(ids.get(1000), rx.row(ids.get(1000)).version(), false, null, null, "DEED")));
        return new Big(s, ids);
    }

    private void assertNoDeedAnywhere(Big b) {
        assertThat(count("SELECT count(*) FROM gas.archival_record WHERE capture_session_id = :p AND classification_code = 'DEED'",
                b.session().captureSessionId())).isZero();
        assertThat(count("SELECT count(*) FROM gas.change_history WHERE object_type = 'archival_record' "
                + "AND field_changes::text LIKE '%DEED%' AND object_id IN (SELECT record_id FROM gas.archival_record "
                + "WHERE capture_session_id = :p)", b.session().captureSessionId())).as("no history receives DEED").isZero();
    }

    /** The audit's scenario: the classification source is restricted at AFTER_PLAN. */
    @Test
    void gasP3_004_hiddenLaterChunkClassificationSourceIsNotUsed() {
        Big b = bigGroupClassifiedOnlyByTheLastRow();
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                restrictFile(b.ids().get(1000));
            }
        });
        GroupingRun run = g.apply(rx.editor, b.session().captureSessionId());

        assertNoDeedAnywhere(b);
        assertThat(count("SELECT count(*) FROM gas.archival_record WHERE capture_session_id = :p", b.session().captureSessionId()))
                .as("group held before any Record was created").isZero();
        assertThat(rx.row(b.ids().get(0)).applied()).isFalse();
        assertThat(count("SELECT count(*) FROM gas.grouping_run_group WHERE run_id = :p AND group_completed", run.runId())).isZero();
        assertThat(run.rowsHeld()).as("the 1,000 rows the Editor still sees").isEqualTo(1000);
        assertThat(run.rowsApplied()).isZero();

        // An Administrator later applies it under the approved first-classification rule.
        rx.records().grouping().apply(rx.admin, b.session().captureSessionId());
        UUID record = rx.row(b.ids().get(0)).appliedRecordId();
        assertThat(rx.records().records().get(rx.admin, record).classificationCode()).isEqualTo("DEED");
        assertThat(rx.records().context().records().links(record)).extracting(RecordFileLink::fileId)
                .containsExactlyElementsOf(b.ids());
    }

    /** A later-chunk classification source edited after planning is a conflict: no stale value is used. */
    @Test
    void gasP3_004_changedLaterChunkClassificationSourceIsAConflict() {
        Big b = bigGroupClassifiedOnlyByTheLastRow();
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                rx.records().sessions().saveMarks(rx.admin, b.session().captureSessionId(), List.of(
                        new CaptureSessionService.MarkEdit(b.ids().get(1000), rx.row(b.ids().get(1000)).version(), false,
                                null, null, "DTRUST")));
            }
        });
        GroupingRun run = g.apply(rx.editor, b.session().captureSessionId());
        assertNoDeedAnywhere(b);
        assertThat(run.rowsConflicted()).isEqualTo(1001);
        assertThat(count("SELECT count(*) FROM gas.archival_record WHERE capture_session_id = :p", b.session().captureSessionId()))
                .isZero();
        // The next run plans again and uses the current value.
        rx.records().grouping().apply(rx.editor, b.session().captureSessionId());
        UUID record = rx.row(b.ids().get(0)).appliedRecordId();
        assertThat(rx.records().records().get(rx.editor, record).classificationCode()).isEqualTo("DTRUST");
        assertThat(rx.records().context().records().links(record)).hasSize(1001);
    }

    /** A classification source deleted after planning is a conflict too. */
    @Test
    void gasP3_004_deletedClassificationSourceIsAConflict() {
        List<ArchivalFile> f = rx.files(2);
        CaptureSession s = rx.session("Deleted source");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.mark(s, f.get(1), false, null, null, "DEED");
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                ArchivalFile d = rx.fx.file(f.get(1).fileId());
                rx.fx.preservation().files().delete(rx.editor, d.fileId(), d.version(), "blurred");
            }
        });
        GroupingRun run = g.apply(rx.editor, s.captureSessionId());
        assertThat(run.rowsConflicted()).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM gas.archival_record WHERE capture_session_id = :p", s.captureSessionId())).isZero();
    }

    // ---- GAS-P3-010 ------------------------------------------------------------------------------

    /** Tests 1, 2, 3, 5: the picker lists only batches the actor may see, filtered before the limit. */
    @Test
    void gasP3_010_batchPickerFollowsTheBatchRule() {
        Actor other = rx.otherEditor();
        UUID mine = rx.fx.newBatch(rx.editor);
        rx.batchFile(rx.editor, mine, Restriction.NORMAL);
        UUID theirsMixed = rx.fx.newBatch(other);
        rx.batchFile(other, theirsMixed, Restriction.NORMAL);
        rx.restrictedBatchFile(other, theirsMixed);
        UUID theirsHidden = rx.fx.newBatch(other);
        rx.restrictedBatchFile(other, theirsHidden);
        UUID theirsEmpty = rx.fx.newBatch(other);
        // More of the other user's batches than the limit, all newer: mine must still be listed.
        for (int i = 0; i < 55; i++) {
            rx.fx.newBatch(other);
        }

        List<UploadBatchSummary> editorView = rx.records().sessions().recentBatches(rx.editor);
        assertThat(editorView).extracting(UploadBatchSummary::batchId).containsExactly(mine);
        assertThat(editorView).allSatisfy(b -> assertThat(b.createdByLabel()).isEqualTo("editor1"));
        // The other Editor has 58 batches: they see their own 50 newest (the limit), never this Editor's.
        assertThat(rx.records().sessions().recentBatches(other)).hasSize(50).extracting(UploadBatchSummary::batchId)
                .doesNotContain(mine);
        assertThat(rx.records().sessions().recentBatches(other))
                .allSatisfy(b -> assertThat(b.createdByLabel()).isEqualTo("editor2"));
        // Administrators (MANAGE_PRESERVATION) keep access to every batch: the 50 newest, here all the other user's.
        assertThat(rx.records().sessions().recentBatches(rx.admin)).hasSize(50)
                .allSatisfy(b -> assertThat(b.createdByLabel()).isEqualTo("editor2"));
    }

    /** Test 3 and 6: another user's batch - mixed, hidden-only or empty - answers exactly like a nonexistent one. */
    @Test
    void gasP3_010_otherUsersBatchesAnswerLikeNonexistentOnes() {
        Actor other = rx.otherEditor();
        UUID theirsMixed = rx.fx.newBatch(other);
        rx.batchFile(other, theirsMixed, Restriction.NORMAL);
        rx.restrictedBatchFile(other, theirsMixed);
        UUID theirsHidden = rx.fx.newBatch(other);
        rx.restrictedBatchFile(other, theirsHidden);
        UUID theirsEmpty = rx.fx.newBatch(other);
        CaptureSession s = rx.session("Batch rule");
        CaptureSessionService.AddResult nonexistent = rx.records().sessions().addBatch(rx.editor, s.captureSessionId(),
                UUID.randomUUID());
        for (UUID b : List.of(theirsMixed, theirsHidden, theirsEmpty)) {
            assertThat(rx.records().sessions().addBatch(rx.editor, s.captureSessionId(), b)).isEqualTo(nonexistent);
        }
        assertThat(nonexistent).isEqualTo(new CaptureSessionService.AddResult(0, 0, 0));
        assertThat(rx.records().sessions().worksheet(rx.admin, s.captureSessionId(), 0, 50).rows()).isEmpty();
        // The Administrator may add any batch; the batch's own creator may add theirs (counts stay GAS-P3-002-safe).
        assertThat(rx.records().sessions().addBatch(rx.admin, s.captureSessionId(), theirsMixed))
                .isEqualTo(new CaptureSessionService.AddResult(2, 0, 0));
        CaptureSession t = rx.session("Own batch");
        assertThat(rx.records().sessions().addBatch(other, t.captureSessionId(), theirsHidden))
                .as("hidden-only, even for its creator, answers like an empty batch").isEqualTo(new CaptureSessionService.AddResult(0, 0, 0));
    }

    @SuppressWarnings("unused")
    private static Stream<String> text(ChangeRecord c) {
        return Stream.empty();
    }
}
