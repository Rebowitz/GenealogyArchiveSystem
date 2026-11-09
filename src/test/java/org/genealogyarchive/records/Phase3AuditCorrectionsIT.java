package org.genealogyarchive.records;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Regression tests for the first independent Phase 3 audit (audited head 8b0f142), one group per finding
 * GAS-P3-001 ... GAS-P3-007 (GAS-P3-008 is in Phase3SchemaIT, GAS-P3-009 in scripts/ci/smoke-checks-test.sh). Each
 * reproduces the audit's scenario and fails against the audited code.
 */
class Phase3AuditCorrectionsIT {

    @TempDir
    Path temp;
    RecordsFixture rx;

    @BeforeEach
    void setUp() throws IOException {
        rx = RecordsFixture.create(temp);
    }

    @AfterEach
    void tearDown() {
        rx.close();
    }

    private ArchivalRecord restrict(UUID recordId) {
        ArchivalRecord r = rx.records().records().get(rx.admin, recordId);
        return rx.records().records().correct(rx.admin, r.recordId(), r.version(), new RecordService.RecordInput(
                r.classificationCode(), r.summary(), r.sourceId(), r.sourceLocator(), r.recordDateText(),
                r.filingDateText(), r.transcriptionText(), r.abstractText(), Restriction.RESTRICTED), "test restriction");
    }

    private void restrictFile(ArchivalFile f) {
        ArchivalFile cur = rx.fx.file(f.fileId());
        rx.fx.preservation().files().correct(rx.admin, cur.fileId(), cur.version(), cur.managedFilename(), 0,
                Restriction.RESTRICTED, "test restriction");
    }

    private static String answer(Runnable r) {
        Throwable t = catchThrowable(r::run);
        return t == null ? "ok" : t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    // ---- GAS-P3-001 ------------------------------------------------------------------------------

    /** A visible File grouped into a Record that is later restricted never names that Record. */
    @Test
    void gasP3_001_worksheetAndContextNeverNameAHiddenRecord() {
        ArchivalFile f = rx.file();
        CaptureSession s = rx.session("Later restricted");
        rx.add(s, List.of(f));
        rx.start(s, f);
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        UUID record = rx.row(f.fileId()).appliedRecordId();
        restrict(record);

        for (Actor a : List.of(rx.editor, rx.viewer)) {
            CaptureRow ws = rx.records().sessions().worksheet(a, s.captureSessionId(), 0, 50).rows().get(0).row();
            assertThat(ws.applied()).as("still shown as grouped").isTrue();
            assertThat(ws.appliedRecordId()).isNull();
            assertThat(ws.appliedRunId()).isNull();
            CaptureRow ctxRow = rx.records().sessions().context(a, f.fileId()).orElseThrow().row();
            assertThat(ctxRow.applied()).isTrue();
            assertThat(ctxRow.appliedRecordId()).isNull();
            assertThat(ctxRow.toString()).doesNotContain(record.toString());
        }
        CaptureRow editorResult = rx.records().sessions().correctOverrides(rx.editor, f.fileId(),
                rx.row(f.fileId()).version(), new CaptureOverrides(FieldOverride.inherit(), FieldOverride.inherit(),
                        FieldOverride.inherit(), FieldOverride.of("x"), FieldOverride.inherit()), null);
        assertThat(editorResult.appliedRecordId()).as("mutation results are projected too").isNull();
        // Administrators still see the Record.
        assertThat(rx.records().sessions().worksheet(rx.admin, s.captureSessionId(), 0, 50).rows().get(0).row()
                .appliedRecordId()).isEqualTo(record);
    }

    // ---- GAS-P3-002 ------------------------------------------------------------------------------

    /** Adding a batch never reveals how many of its Files are restricted. */
    @Test
    void gasP3_002_batchAddCountsOnlyVisibleFiles() {
        // The Editor's own batches (GAS-P3-010: only the creator or MANAGE_PRESERVATION may use a batch), whose
        // members an Administrator restricted after upload.
        UUID hiddenOnly = rx.fx.newBatch(rx.editor);
        for (int i = 0; i < 3; i++) {
            rx.restrictedBatchFile(rx.editor, hiddenOnly);
        }
        UUID empty = rx.fx.newBatch(rx.editor);
        UUID mixed = rx.fx.newBatch(rx.editor);
        rx.restrictedBatchFile(rx.editor, mixed);
        ArchivalFile v1 = rx.batchFile(rx.editor, mixed, Restriction.NORMAL);
        rx.restrictedBatchFile(rx.editor, mixed);
        ArchivalFile v2 = rx.batchFile(rx.editor, mixed, Restriction.NORMAL);

        CaptureSession s = rx.session("Batches");
        CaptureSessionService.AddResult hidden = rx.records().sessions().addBatch(rx.editor, s.captureSessionId(), hiddenOnly);
        CaptureSessionService.AddResult none = rx.records().sessions().addBatch(rx.editor, s.captureSessionId(), empty);
        assertThat(hidden).as("a hidden-only batch answers exactly like an empty one").isEqualTo(none);
        assertThat(hidden).isEqualTo(new CaptureSessionService.AddResult(0, 0, 0));

        CaptureSessionService.AddResult m = rx.records().sessions().addBatch(rx.editor, s.captureSessionId(), mixed);
        assertThat(m).isEqualTo(new CaptureSessionService.AddResult(2, 0, 0));
        assertThat(rx.records().sessions().worksheet(rx.editor, s.captureSessionId(), 0, 50).rows())
                .extracting(w -> w.file().fileId()).containsExactly(v1.fileId(), v2.fileId());
        // Adding the same batch again: only the visible Files count as already in a session.
        assertThat(rx.records().sessions().addBatch(rx.editor, s.captureSessionId(), mixed))
                .isEqualTo(new CaptureSessionService.AddResult(0, 2, 0));
        // Explicitly entered IDs keep the hidden-equals-nonexistent rule.
        ArchivalFile h = rx.restrictedFile();
        assertThat(rx.records().sessions().addByFriendlyIds(rx.editor, s.captureSessionId(), h.friendlyId()))
                .isEqualTo(rx.records().sessions().addByFriendlyIds(rx.editor, s.captureSessionId(), "F99999"));
    }

    // ---- GAS-P3-003 ------------------------------------------------------------------------------

    /** A formerly public run whose Files and Record become restricted no longer appears to non-Administrators. */
    @Test
    void gasP3_003_runSummariesFollowCurrentRestrictions() {
        List<ArchivalFile> f = rx.files(2);
        CaptureSession s = rx.session("Public run");
        rx.add(s, f);
        rx.start(s, f.get(0));
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        UUID record = rx.row(f.get(0).fileId()).appliedRecordId();
        List<GroupingRunView> before = rx.records().grouping().runs(rx.viewer, s.captureSessionId());
        assertThat(before).singleElement().satisfies(v -> {
            assertThat(v.rowsApplied()).isEqualTo(2);
            assertThat(v.recordsCreated()).isEqualTo(1);
            assertThat(v.rowsHeld()).as("unattributable counts omitted for non-Administrators").isNull();
        });

        // Restrict one File: counts follow what the reader can see now.
        restrictFile(f.get(1));
        assertThat(rx.records().grouping().runs(rx.viewer, s.captureSessionId()))
                .singleElement().satisfies(v -> assertThat(v.rowsApplied()).isEqualTo(1));
        // Restrict the other File and the Record: nothing visible remains, so the run is not shown at all.
        restrictFile(f.get(0));
        restrict(record);
        for (Actor a : List.of(rx.editor, rx.viewer)) {
            assertThat(rx.records().sessions().worksheet(a, s.captureSessionId(), 0, 50).rows()).isEmpty();
            assertThat(rx.records().grouping().runs(a, s.captureSessionId())).isEmpty();
        }
        // Administrators see the stored accounting, which is never rewritten.
        assertThat(rx.records().grouping().runs(rx.admin, s.captureSessionId())).singleElement().satisfies(v -> {
            assertThat(v.rowsApplied()).isEqualTo(2);
            assertThat(v.recordsCreated()).isEqualTo(1);
            assertThat(v.rowsHeld()).isZero();
        });
        assertThat(rx.records().context().runs().find(run.runId()).orElseThrow()).isEqualTo(run);
    }

    /** A Record restricted after the run hides only that Record's contribution. */
    @Test
    void gasP3_003_restrictedRecordIsNotCountedForOthers() {
        List<ArchivalFile> f = rx.files(2);
        CaptureSession s = rx.session("Two Records");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.start(s, f.get(1));
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        restrict(rx.row(f.get(1).fileId()).appliedRecordId());
        assertThat(rx.records().grouping().runs(rx.editor, s.captureSessionId()))
                .singleElement().satisfies(v -> assertThat(v.recordsCreated()).isEqualTo(1));
    }

    // ---- GAS-P3-004 ------------------------------------------------------------------------------

    /** A File restricted after planning is not applied by the Editor's run; its group is held, not completed. */
    @Test
    void gasP3_004_fileRestrictedAfterPlanIsNotApplied() {
        List<ArchivalFile> f = rx.files(3);
        CaptureSession s = rx.session("Restricted after plan");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.start(s, f.get(2));
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                restrictFile(f.get(1));
            }
        });
        GroupingRun run = g.apply(rx.editor, s.captureSessionId());
        assertThat(rx.row(f.get(0).fileId()).applied()).as("group with the now-hidden File held").isFalse();
        assertThat(rx.row(f.get(1).fileId()).applied()).isFalse();
        assertThat(rx.row(f.get(2).fileId()).applied()).as("unrelated group applied").isTrue();
        assertThat(run.rowsHeld()).as("only the still-visible row is counted").isEqualTo(1);
        assertThat(JdbcClient.create(rx.fx.ds).sql("SELECT count(*) FROM gas.record_file WHERE file_id = :f")
                .param("f", f.get(1).fileId()).query(Long.class).single()).isZero();
        // An Administrator then applies the held group.
        rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(rx.row(f.get(1).fileId()).appliedRecordId()).isEqualTo(rx.row(f.get(0).fileId()).appliedRecordId());
    }

    /** A File deleted after planning is a conflict: never linked from the stale snapshot. */
    @Test
    void gasP3_004_fileDeletedAfterPlanIsAConflict() {
        List<ArchivalFile> f = rx.files(2);
        CaptureSession s = rx.session("Deleted after plan");
        rx.add(s, f);
        rx.start(s, f.get(0));
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                ArchivalFile d = rx.fx.file(f.get(1).fileId());
                rx.fx.preservation().files().delete(rx.editor, d.fileId(), d.version(), "blurred");
            }
        });
        GroupingRun run = g.apply(rx.editor, s.captureSessionId());
        assertThat(run.rowsConflicted()).isEqualTo(2);
        assertThat(rx.row(f.get(0).fileId()).applied()).isFalse();
        // The next run plans with the File's current lifecycle: it marks its boundary but is not linked.
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(rx.row(f.get(0).fileId()).applied()).isTrue();
        assertThat(rx.row(f.get(1).fileId()).applied()).isFalse();
    }

    /** Between chunks: committed chunks stay, the rest of the group is held, and the group is never complete. */
    @Test
    void gasP3_004_restrictionBetweenChunksHoldsTheRest() {
        int n = 1500;
        List<UUID> ids = rx.syntheticFiles(n);
        CaptureSession s = rx.session("Between chunks");
        rx.records().sessions().addFiles(rx.admin, s.captureSessionId(), ids);
        rx.records().sessions().saveMarks(rx.admin, s.captureSessionId(), List.of(new CaptureSessionService.MarkEdit(
                ids.get(0), rx.row(ids.get(0)).version(), true, null, null, null)));
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_CHUNK && c == 1) {
                restrictFile(rx.fx.file(ids.get(1200)));
            }
        });
        GroupingRun run = g.apply(rx.editor, s.captureSessionId());
        UUID record = rx.row(ids.get(0)).appliedRecordId();
        JdbcClient jdbc = JdbcClient.create(rx.fx.ds);
        assertThat(jdbc.sql("SELECT count(*) FROM gas.record_file WHERE record_id = :r").param("r", record)
                .query(Long.class).single()).as("first chunk kept").isEqualTo(1000);
        assertThat(jdbc.sql("SELECT count(*) FROM gas.grouping_run_group WHERE run_id = :r AND group_completed")
                .param("r", run.runId()).query(Long.class).single()).as("never reported complete").isZero();
        assertThat(rx.row(ids.get(1200)).applied()).isFalse();
        assertThat(run.rowsHeld()).as("visible remaining rows only").isEqualTo(n - 1000 - 1);
        // An Administrator finishes it in the same Record, in order, without duplicates.
        rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(rx.records().context().records().links(record)).extracting(RecordFileLink::fileId)
                .containsExactlyElementsOf(ids);
    }

    /** The continued-from applied row becoming hidden after planning holds the continuation. */
    @Test
    void gasP3_004_hiddenContinuationSourceAfterPlanIsHeld() {
        ArchivalFile a = rx.file();
        CaptureSession s = rx.session("Continuation");
        rx.add(s, List.of(a));
        rx.start(s, a);
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        ArchivalFile b = rx.file();
        rx.add(s, List.of(b));
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                restrictFile(a);
            }
        });
        GroupingRun run = g.apply(rx.editor, s.captureSessionId());
        assertThat(rx.row(b.fileId()).applied()).isFalse();
        assertThat(run.rowsHeld()).isEqualTo(1);
    }

    // ---- GAS-P3-005 ------------------------------------------------------------------------------

    /**
     * The cutoff is removed after the request was validated but before planning (deterministically, at the
     * BEFORE_PLAN seam): the request is rejected like a nonexistent File, nothing is applied and no run is recorded.
     */
    @Test
    void gasP3_005_cutoffRemovedBeforePlanningRejectsTheRequest() {
        List<ArchivalFile> f = rx.files(5);
        CaptureSession s = rx.session("Cutoff removed");
        rx.add(s, f);
        rx.start(s, f.get(0));
        ArchivalFile cutoff = f.get(2);
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.BEFORE_PLAN) {
                rx.records().sessions().removeFile(rx.editor, cutoff.fileId(), rx.row(cutoff.fileId()).version(), null);
            }
        });
        Throwable t = catchThrowable(() -> g.apply(rx.editor, s.captureSessionId(), cutoff.fileId()));
        assertThat(t).isInstanceOf(NotFoundException.class)
                .hasMessage(catchThrowable(() -> rx.records().grouping().apply(rx.editor, s.captureSessionId(),
                        UUID.randomUUID())).getMessage());
        for (ArchivalFile x : f) {
            assertThat(rx.records().context().sessions().row(x.fileId()).map(CaptureRow::applied).orElse(false))
                    .as("nothing applied").isFalse();
        }
        assertThat(rx.records().grouping().runs(rx.admin, s.captureSessionId())).as("no run recorded").isEmpty();
    }

    /** Removed and re-added (so now at the end): the validated position no longer exists, so the request fails. */
    @Test
    void gasP3_005_cutoffMovedBeforePlanningRejectsTheRequest() {
        List<ArchivalFile> f = rx.files(5);
        CaptureSession s = rx.session("Cutoff moved");
        rx.add(s, f);
        rx.start(s, f.get(0));
        ArchivalFile cutoff = f.get(2);
        GroupingService g = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.BEFORE_PLAN) {
                rx.records().sessions().removeFile(rx.editor, cutoff.fileId(), rx.row(cutoff.fileId()).version(), null);
                rx.add(s, List.of(cutoff));
            }
        });
        assertThatThrownBy(() -> g.apply(rx.editor, s.captureSessionId(), cutoff.fileId()))
                .isInstanceOf(NotFoundException.class);
        for (ArchivalFile x : f) {
            assertThat(rx.row(x.fileId()).applied()).isFalse();
        }
        // R-005 still works with an intact cutoff.
        GroupingRun ok = rx.records().grouping().apply(rx.editor, s.captureSessionId(), f.get(1).fileId());
        assertThat(ok.rowsApplied()).isEqualTo(2);
        assertThat(rx.row(f.get(3).fileId()).applied()).isFalse();
    }

    // ---- GAS-P3-006 ------------------------------------------------------------------------------

    @Test
    void gasP3_006_citationsOfADeletedRecordAreFrozen() {
        ArchivalRecord r = rx.records().records().create(rx.editor, new RecordService.RecordInput(null, null, null, null,
                null, null, null, null, null));
        Citation c = rx.records().citations().create(rx.editor, null, r.recordId(), "p. 1", "text", null);
        Source src = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput("Book", null, null,
                null, null, null, null, null));
        Citation sourceOnly = rx.records().citations().create(rx.editor, src.sourceId(), null, "p. 2", null, null);
        ArchivalRecord cur = rx.records().records().get(rx.editor, r.recordId());
        rx.records().records().delete(rx.editor, cur.recordId(), cur.version(), "duplicate");

        assertThatThrownBy(() -> rx.records().citations().correct(rx.editor, c.citationId(), c.version(), "p. 9", "text",
                null, false)).isInstanceOf(RuleViolationException.class).hasMessageContaining("is deleted");
        assertThatThrownBy(() -> rx.records().citations().correct(rx.editor, c.citationId(), c.version(), "p. 1", "text",
                null, true)).as("delete").isInstanceOf(RuleViolationException.class);
        assertThat(rx.records().citations().get(rx.viewer, c.citationId())).as("still readable, unchanged").isEqualTo(c);
        // Source-only Citations are unaffected.
        assertThat(rx.records().citations().correct(rx.editor, sourceOnly.citationId(), sourceOnly.version(), "p. 3",
                null, null, false).locator()).isEqualTo("p. 3");
        // After restore, the Citation can change again.
        ArchivalRecord deleted = rx.records().records().get(rx.editor, r.recordId());
        rx.records().records().restore(rx.editor, deleted.recordId(), deleted.version(), null);
        assertThat(rx.records().citations().correct(rx.editor, c.citationId(), c.version(), "p. 9", "text", null, false)
                .locator()).isEqualTo("p. 9");
    }

    /** Creation racing deletion: whichever commits first, no Citation is created against a deleted Record. */
    @Test
    void gasP3_006_citationCreationRacingRecordDeletion() throws Exception {
        for (int i = 0; i < 10; i++) {
            ArchivalRecord r = rx.records().records().create(rx.editor, new RecordService.RecordInput(null, null, null,
                    null, null, null, null, null, null));
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Citation> created = new AtomicReference<>();
            Thread citer = new Thread(() -> {
                try {
                    go.await();
                    created.set(rx.records().citations().create(rx.editor, null, r.recordId(), "p. 1", null, null));
                } catch (Exception ignored) {
                    // refused because the Record was deleted first
                }
            });
            Thread deleter = new Thread(() -> {
                try {
                    go.await();
                    rx.records().records().delete(rx.admin, r.recordId(), r.version(), "race");
                } catch (Exception ignored) {
                    // cannot happen: the Citation insert does not change the Record's version
                }
            });
            citer.start();
            deleter.start();
            go.countDown();
            citer.join();
            deleter.join();
            ArchivalRecord after = rx.records().records().get(rx.admin, r.recordId());
            assertThat(after.lifecycle()).isEqualTo(RecordLifecycle.DELETED);
            if (created.get() != null) {
                // The Citation committed first; the deletion then waited for it. Its creation precedes the deletion.
                List<ChangeRecord> h = rx.records().records().history(rx.admin, r.recordId(), rx.fx.services().history());
                ChangeRecord deletion = h.stream().filter(x -> x.changeKind().equals("DELETED")).findFirst().orElseThrow();
                assertThat(created.get().createdAt()).isBeforeOrEqualTo(deletion.occurredAt());
            }
        }
    }

    // ---- GAS-P3-007 ------------------------------------------------------------------------------

    @Test
    void gasP3_007_linkNoteSurvivesUnlinkInHistory() {
        ArchivalFile f = rx.file();
        ArchivalRecord r = rx.records().records().create(rx.editor, new RecordService.RecordInput(null, null, null, null,
                null, null, null, null, null));
        String note = "margin note 7f3a (synthetic)";
        rx.records().records().linkFile(rx.editor, r.recordId(), r.version(), f.fileId(), "12", note);
        ArchivalRecord cur = rx.records().records().get(rx.editor, r.recordId());
        rx.records().records().unlinkFile(rx.editor, cur.recordId(), cur.version(), f.fileId(), "wrong deed");

        List<FieldChange> admin = rx.records().records().history(rx.admin, r.recordId(), rx.fx.services().history()).stream()
                .flatMap(h -> h.fieldChanges().stream()).filter(fc -> fc.field().equals("note")).toList();
        assertThat(admin).extracting(fc -> fc.before() + "->" + fc.after())
                .containsExactlyInAnyOrder("null->" + note, note + "->null");

        // Corrected then unlinked: every value is recoverable.
        ArchivalFile g = rx.file();
        ArchivalRecord c2 = rx.records().records().get(rx.editor, r.recordId());
        rx.records().records().linkFile(rx.editor, c2.recordId(), c2.version(), g.fileId(), "13", "first");
        c2 = rx.records().records().get(rx.editor, r.recordId());
        rx.records().records().correctLink(rx.editor, c2.recordId(), c2.version(), g.fileId(), "13", "second");
        c2 = rx.records().records().get(rx.editor, r.recordId());
        rx.records().records().unlinkFile(rx.editor, c2.recordId(), c2.version(), g.fileId(), null);
        List<String> notes = rx.records().records().history(rx.admin, r.recordId(), rx.fx.services().history()).stream()
                .flatMap(h -> h.fieldChanges().stream()).filter(fc -> fc.field().equals("note"))
                .map(fc -> fc.before() + "->" + fc.after()).toList();
        assertThat(notes).contains("null->first", "first->second", "second->null");

        // Restricting the File later redacts these events whole for non-Administrators.
        restrictFile(g);
        for (Actor a : List.of(rx.editor, rx.viewer)) {
            assertThat(rx.records().records().history(a, r.recordId(), rx.fx.services().history()).stream()
                    .flatMap(h -> h.fieldChanges().stream())
                    .flatMap(fc -> java.util.stream.Stream.of(fc.before(), fc.after())).filter(v -> v != null))
                    .noneMatch(v -> v.contains("first") || v.contains("second") || v.contains(g.friendlyId()));
        }
    }

    @SuppressWarnings("unused")
    private static void unused(NotFoundException e) {
    }
}
