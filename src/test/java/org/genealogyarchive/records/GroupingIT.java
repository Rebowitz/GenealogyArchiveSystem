package org.genealogyarchive.records;

import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Courthouse grouping (R-004, R-005, BI-008; D3-02; ADR-0011 s.4) including the owner safeguards of 2026-11-08:
 * hidden boundary rows never cause a silent merge (safeguard 1), and groups larger than one 1,000-row transaction
 * are applied in bounded, resumable chunks with no duplicates (safeguard 2). Replays are tested at every
 * transaction boundary.
 */
class GroupingIT {

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

    /** Simulated crash. */
    static final class Crash extends RuntimeException {
        Crash(String m) {
            super(m);
        }
    }

    /** Throws once at a chosen point (optionally a chosen chunk). */
    static GroupingFaults crashAt(GroupingFaults.Point point, int chunk) {
        boolean[] fired = {false};
        return (p, run, key, c) -> {
            if (p == point && !fired[0] && (chunk == 0 || c == chunk)) {
                fired[0] = true;
                throw new Crash("crash at " + p + " chunk " + c);
            }
        };
    }

    private CaptureRow row(ArchivalFile f) {
        return rx.row(f.fileId());
    }

    private UUID recordOf(ArchivalFile f) {
        return row(f).appliedRecordId();
    }

    /** File ids linked to a Record, in image order. */
    private List<UUID> linked(UUID recordId) {
        return rx.records().context().records().links(recordId).stream().map(RecordFileLink::fileId).toList();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    // ---- R-004 / R-005 ---------------------------------------------------------------------------

    /** R-004: blank rows continue the preceding Record until the next start marker; explicit groups win. */
    @Test
    void r004_startNewRecordAndExplicitGroups() {
        List<ArchivalFile> f = rx.files(8);
        CaptureSession s = rx.session("Deed book visit");
        rx.add(s, f);
        rx.mark(s, f.get(0), true, null, "227", "DEED");
        rx.mark(s, f.get(1), false, null, "228", null);
        rx.mark(s, f.get(2), false, null, "229", null);
        rx.start(s, f.get(3));
        // An explicit group wins over Start New Record and joins rows that are not adjacent.
        rx.mark(s, f.get(4), true, "Deed C", "300", "DTRUST");
        rx.mark(s, f.get(6), false, " deed c ", "301", null);
        rx.mark(s, f.get(7), false, null, "302", null); // continues Deed C (the row above it)

        GroupingRun run = rx.records().grouping().apply(rx.admin, s.captureSessionId());

        assertThat(run.state()).isEqualTo(GroupingRun.State.COMPLETED);
        UUID a = recordOf(f.get(0));
        assertThat(linked(a)).containsExactly(f.get(0).fileId(), f.get(1).fileId(), f.get(2).fileId());
        UUID b = recordOf(f.get(3));
        assertThat(b).isNotEqualTo(a);
        UUID c = recordOf(f.get(4));
        assertThat(recordOf(f.get(5))).as("blank row after C continues C").isEqualTo(c);
        assertThat(recordOf(f.get(6))).isEqualTo(c);
        assertThat(recordOf(f.get(7))).isEqualTo(c);
        assertThat(linked(c)).containsExactly(f.get(4).fileId(), f.get(5).fileId(), f.get(6).fileId(), f.get(7).fileId());
        // R-001: page designations are kept as entered, independent of image order.
        assertThat(rx.records().context().records().link(a, f.get(1).fileId()).orElseThrow().pageDesignation()).isEqualTo("228");
        assertThat(rx.records().context().records().find(a).orElseThrow().classificationCode()).isEqualTo("DEED");
        assertThat(rx.records().context().records().find(c).orElseThrow().classificationCode()).isEqualTo("DTRUST");
        assertThat(run.recordsCreated()).isEqualTo(3);
        assertThat(run.rowsApplied()).isEqualTo(8);
    }

    /**
     * R-005: of 200 images the researcher marks the first 83 and stops, applying only through image 83. The other 117
     * stay unapplied, valid archived Files with their saved (blank) marks; nothing requires finishing the batch.
     */
    @Test
    void r005_stopAfter83Of200() {
        List<UUID> ids = rx.syntheticFiles(200);
        CaptureSession s = rx.session("Two hundred images");
        rx.records().sessions().addFiles(rx.admin, s.captureSessionId(), ids);
        List<CaptureSessionService.MarkEdit> marks = new ArrayList<>();
        for (int i = 0; i < 83; i += 3) {
            marks.add(new CaptureSessionService.MarkEdit(ids.get(i), rx.row(ids.get(i)).version(), true, null, null, "DEED"));
        }
        rx.records().sessions().saveMarks(rx.editor, s.captureSessionId(), marks);

        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId(), ids.get(82));

        assertThat(run.state()).isEqualTo(GroupingRun.State.COMPLETED);
        assertThat(run.rowsApplied()).isEqualTo(83);
        assertThat(run.recordsCreated()).isEqualTo(28);
        for (int i = 0; i < 200; i++) {
            assertThat(rx.row(ids.get(i)).applied()).as("row " + i).isEqualTo(i < 83);
        }
        assertThat(count("SELECT count(*) FROM gas.archival_file WHERE lifecycle = 'ACTIVE' AND friendly_number >= "
                + "(SELECT min(friendly_number) FROM gas.archival_file WHERE original_filename LIKE 'synthetic-%')"))
                .as("all 200 remain valid archived Files").isEqualTo(200);
        assertThat(rx.records().sessions().worksheet(rx.editor, s.captureSessionId(), 0, 200).rows()).hasSize(200);
    }

    /** R-005: rows with nothing before them stay ungrouped, valid, and are counted. */
    @Test
    void r005_leadingBlankRowsStayUngrouped() {
        List<ArchivalFile> f = rx.files(4);
        CaptureSession s = rx.session("Partly indexed");
        rx.add(s, f);
        rx.start(s, f.get(2));
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(row(f.get(0)).applied()).isFalse();
        assertThat(row(f.get(1)).applied()).isFalse();
        assertThat(recordOf(f.get(3))).isEqualTo(recordOf(f.get(2)));
        assertThat(run.rowsUngrouped()).isEqualTo(2);
        // Later the researcher marks them; a new run applies only those rows.
        rx.start(s, f.get(0));
        GroupingRun second = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(second.rowsApplied()).isEqualTo(2);
        assertThat(linked(recordOf(f.get(0)))).containsExactly(f.get(0).fileId(), f.get(1).fileId());
    }

    // ---- replay and interruption -----------------------------------------------------------------

    private record Scenario(CaptureSession session, List<ArchivalFile> files) {
    }

    private Scenario threeGroups() {
        List<ArchivalFile> f = rx.files(6);
        CaptureSession s = rx.session("Replay");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.start(s, f.get(2));
        rx.mark(s, f.get(4), false, "g", null, null);
        return new Scenario(s, f);
    }

    private void assertAppliedExactlyOnce(Scenario sc) {
        String session = "'" + sc.session().captureSessionId() + "'";
        assertThat(count("SELECT count(*) FROM gas.archival_record WHERE capture_session_id = " + session))
                .as("one Record per group").isEqualTo(3);
        assertThat(count("SELECT count(*) FROM gas.record_file l JOIN gas.archival_record r ON r.record_id = l.record_id "
                + "WHERE r.capture_session_id = " + session)).as("one link per row").isEqualTo(6);
        for (ArchivalFile f : sc.files()) {
            assertThat(row(f).applied()).isTrue();
            assertThat(count("SELECT count(*) FROM gas.record_file WHERE file_id = '" + f.fileId() + "'")).isEqualTo(1);
        }
    }

    /** Interrupt at every transaction boundary, then replay: no duplicates, an INTERRUPTED run, then COMPLETED. */
    @Test
    void replayAfterInterruptionAtEveryBoundaryCreatesNoDuplicates() {
        for (GroupingFaults.Point point : GroupingFaults.Point.values()) {
            Scenario sc = threeGroups();
            GroupingService faulty = rx.grouping(crashAt(point, point == GroupingFaults.Point.AFTER_PLAN
                    || point == GroupingFaults.Point.BEFORE_FINISH ? 0 : 1));
            assertThatThrownBy(() -> faulty.apply(rx.admin, sc.session().captureSessionId()))
                    .as(point.name()).isInstanceOf(Crash.class);
            List<GroupingRun> runs = rx.records().grouping().runs(rx.admin, sc.session().captureSessionId());
            assertThat(runs).hasSize(1);
            assertThat(runs.get(0).state()).isEqualTo(GroupingRun.State.INTERRUPTED);

            GroupingRun replay = rx.records().grouping().apply(rx.admin, sc.session().captureSessionId());
            assertThat(replay.state()).isEqualTo(GroupingRun.State.COMPLETED);
            assertAppliedExactlyOnce(sc);
            // A third run has nothing left to do.
            GroupingRun third = rx.records().grouping().apply(rx.admin, sc.session().captureSessionId());
            assertThat(third.rowsApplied()).isZero();
            assertThat(third.recordsCreated()).isZero();
            assertAppliedExactlyOnce(sc);
        }
    }

    /** A run left RUNNING by a process that died is closed as INTERRUPTED by the next run, which continues. */
    @Test
    void staleRunningRunIsClosedByTheNextRun() {
        Scenario sc = threeGroups();
        UUID stale = UUID.randomUUID();
        rx.records().context().tx().executeWithoutResult(t -> rx.records().context().runs().start(stale,
                sc.session().captureSessionId(), true, java.time.Instant.now(), rx.admin.userId()));
        GroupingRun run = rx.records().grouping().apply(rx.admin, sc.session().captureSessionId());
        assertThat(run.state()).isEqualTo(GroupingRun.State.COMPLETED);
        assertThat(rx.records().context().runs().find(stale).orElseThrow().state()).isEqualTo(GroupingRun.State.INTERRUPTED);
        assertAppliedExactlyOnce(sc);
    }

    /** Only one run per session at a time. */
    @Test
    void aSecondConcurrentRunIsRefused() throws Exception {
        Scenario sc = threeGroups();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GroupingService slow = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                inside.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        AtomicReference<GroupingRun> first = new AtomicReference<>();
        Thread t = new Thread(() -> first.set(slow.apply(rx.admin, sc.session().captureSessionId())));
        t.start();
        inside.await();
        assertThatThrownBy(() -> rx.records().grouping().apply(rx.editor, sc.session().captureSessionId()))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("already running");
        release.countDown();
        t.join();
        assertThat(first.get().state()).isEqualTo(GroupingRun.State.COMPLETED);
        assertAppliedExactlyOnce(sc);
    }

    /** A row edited after planning is a conflict: its group is left whole for the next run, never split. */
    @Test
    void aRowEditedDuringTheRunLeavesItsGroupForTheNextRun() {
        Scenario sc = threeGroups();
        ArchivalFile second = sc.files().get(1);
        GroupingService editing = rx.grouping((p, run, key, c) -> {
            if (p == GroupingFaults.Point.AFTER_PLAN) {
                rx.mark(sc.session(), second, false, null, "edited page", null);
            }
        });
        GroupingRun run = editing.apply(rx.admin, sc.session().captureSessionId());
        assertThat(run.state()).isEqualTo(GroupingRun.State.COMPLETED);
        assertThat(run.rowsConflicted()).isEqualTo(2);
        assertThat(row(sc.files().get(0)).applied()).as("group not split").isFalse();
        assertThat(row(second).applied()).isFalse();
        GroupingRun next = rx.records().grouping().apply(rx.admin, sc.session().captureSessionId());
        assertThat(next.rowsApplied()).isEqualTo(2);
        assertThat(rx.records().context().records().link(recordOf(second), second.fileId()).orElseThrow().pageDesignation())
                .isEqualTo("edited page");
        assertAppliedExactlyOnce(sc);
    }

    /** Applied rows are final: their marks cannot change, and removing them from the session is refused. */
    @Test
    void appliedRowsAreFinal() {
        Scenario sc = threeGroups();
        rx.records().grouping().apply(rx.admin, sc.session().captureSessionId());
        ArchivalFile f = sc.files().get(0);
        assertThatThrownBy(() -> rx.mark(sc.session(), f, false, "other", null, null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> rx.records().sessions().removeFile(rx.admin, f.fileId(), row(f).version(), null))
                .isInstanceOf(RuleViolationException.class);
    }

    /** Cross-batch: an explicit group key joins the same Record in a later run, from Files added later. */
    @Test
    void explicitGroupsSpanBatchesAndRuns() {
        List<ArchivalFile> first = rx.files(2);
        CaptureSession s = rx.session("Two visits' uploads");
        rx.add(s, first);
        rx.mark(s, first.get(0), false, "Will of J. Doe", "1", null);
        rx.mark(s, first.get(1), false, "Will of J. Doe", "2", null);
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        UUID record = recordOf(first.get(0));

        List<ArchivalFile> later = rx.files(2);
        rx.add(s, later);
        rx.mark(s, later.get(0), true, "will of j. doe", "3", null); // group wins over start
        rx.mark(s, later.get(1), false, null, "4", null);
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(run.recordsCreated()).isZero();
        assertThat(run.recordsExtended()).isEqualTo(1);
        assertThat(linked(record)).containsExactly(first.get(0).fileId(), first.get(1).fileId(), later.get(0).fileId(),
                later.get(1).fileId());
    }

    /** A blank row after an applied row continues that row's Record in a later run. */
    @Test
    void blankRowsAddedLaterContinueTheAppliedRecordAbove() {
        List<ArchivalFile> f = rx.files(1);
        CaptureSession s = rx.session("Continue later");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        List<ArchivalFile> more = rx.files(2);
        rx.add(s, more);
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(linked(recordOf(f.get(0)))).containsExactly(f.get(0).fileId(), more.get(0).fileId(), more.get(1).fileId());
    }

    /** A deleted Record is never extended: rows that would continue it are held. */
    @Test
    void aDeletedRecordIsNotExtended() {
        List<ArchivalFile> f = rx.files(1);
        CaptureSession s = rx.session("Deleted target");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        ArchivalRecord r = rx.records().records().get(rx.admin, recordOf(f.get(0)));
        rx.records().records().delete(rx.admin, r.recordId(), r.version(), "test");
        ArchivalFile more = rx.files(1).get(0);
        rx.add(s, List.of(more));
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(run.rowsHeld()).isEqualTo(1);
        assertThat(row(more).applied()).isFalse();
    }

    /** A deleted File's Start New Record still marks a boundary; the File itself is not linked. */
    @Test
    void aDeletedFileStillMarksItsBoundary() {
        List<ArchivalFile> f = rx.files(4);
        CaptureSession s = rx.session("Deleted boundary");
        rx.add(s, f);
        rx.start(s, f.get(0));
        rx.start(s, f.get(2));
        ArchivalFile d = rx.fx.file(f.get(2).fileId());
        rx.fx.preservation().files().delete(rx.editor, d.fileId(), d.version(), "blurred duplicate");
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(recordOf(f.get(1))).isEqualTo(recordOf(f.get(0)));
        assertThat(recordOf(f.get(3))).as("not merged into the first Record").isNotEqualTo(recordOf(f.get(0)));
        assertThat(row(f.get(2)).applied()).isFalse();
    }

    // ---- owner safeguard 1: hidden boundaries never merge Records ---------------------------------

    /**
     * Rows A B [H: restricted, Start New Record] C. An Editor cannot see H. Skipping it would continue C into A's
     * Record - a silent merge. Instead H's group (H, C) is held; A's group is applied; an Administrator later
     * applies the held group as its own Record.
     */
    @Test
    void safeguard1_hiddenStartNewRecordNeverMergesRecords() {
        ArchivalFile a = rx.file();
        ArchivalFile b = rx.file();
        ArchivalFile h = rx.restrictedFile();
        ArchivalFile c = rx.file();
        CaptureSession s = rx.session("Hidden boundary");
        rx.add(s, List.of(a, b, h, c));
        rx.start(s, a);
        rx.start(s, h);

        GroupingRun editorRun = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(linked(recordOf(a))).containsExactly(a.fileId(), b.fileId());
        assertThat(row(c).applied()).as("C is held, not merged into A's Record").isFalse();
        assertThat(editorRun.rowsHeld()).as("only rows the Editor can see are counted").isEqualTo(1);
        assertThat(editorRun.groupsHeld()).isEqualTo(1);

        GroupingRun adminRun = rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(adminRun.rowsHeld()).isZero();
        assertThat(linked(recordOf(h))).containsExactly(h.fileId(), c.fileId());
        assertThat(recordOf(h)).isNotEqualTo(recordOf(a));
    }

    /** A hidden explicit group: the rows after it belong to that group, so they are held too. */
    @Test
    void safeguard1_hiddenExplicitGroupHoldsItsContinuation() {
        ArchivalFile a = rx.file();
        ArchivalFile h = rx.restrictedFile();
        ArchivalFile c = rx.file();
        CaptureSession s = rx.session("Hidden group");
        rx.add(s, List.of(a, h, c));
        rx.start(s, a);
        rx.mark(s, h, false, "secret", null, null);
        rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(linked(recordOf(a))).containsExactly(a.fileId());
        assertThat(row(c).applied()).isFalse();
        rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(linked(recordOf(h))).containsExactly(h.fileId(), c.fileId());
    }

    /** A hidden blank row in the middle of a group holds the group rather than splitting it. */
    @Test
    void safeguard1_mixedVisibilityGroupIsHeldWhole() {
        ArchivalFile a = rx.file();
        ArchivalFile h = rx.restrictedFile();
        ArchivalFile c = rx.file();
        ArchivalFile d = rx.file();
        CaptureSession s = rx.session("Mixed group");
        rx.add(s, List.of(a, h, c, d));
        rx.start(s, a);
        rx.start(s, d);
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(row(a).applied()).isFalse();
        assertThat(row(c).applied()).isFalse();
        assertThat(linked(recordOf(d))).containsExactly(d.fileId());
        assertThat(run.rowsHeld()).isEqualTo(2);
        rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(linked(recordOf(a))).containsExactly(a.fileId(), h.fileId(), c.fileId());
    }

    /** Continuing from an applied row the Editor cannot see is held too. */
    @Test
    void safeguard1_continuationFromAHiddenAppliedRowIsHeld() {
        ArchivalFile h = rx.restrictedFile();
        CaptureSession s = rx.session("Hidden applied row");
        rx.add(s, List.of(h));
        rx.start(s, h);
        rx.records().grouping().apply(rx.admin, s.captureSessionId());
        ArchivalFile c = rx.file();
        rx.add(s, List.of(c));
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        assertThat(row(c).applied()).isFalse();
        assertThat(run.rowsHeld()).isEqualTo(1);
    }

    /** Runs started by an Administrator are not shown to others (their counts could reveal restricted rows). */
    @Test
    void safeguard1_administratorRunsAreHiddenFromOthers() {
        ArchivalFile h = rx.restrictedFile();
        CaptureSession s = rx.session("Admin run");
        rx.add(s, List.of(h));
        rx.start(s, h);
        rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(rx.records().grouping().runs(rx.admin, s.captureSessionId())).hasSize(1);
        assertThat(rx.records().grouping().runs(rx.editor, s.captureSessionId())).isEmpty();
        assertThat(rx.records().grouping().runs(rx.viewer, s.captureSessionId())).isEmpty();
    }

    // ---- owner safeguard 2: groups larger than one transaction ------------------------------------

    /**
     * A 2,345-row group: three bounded chunks (1000, 1000, 345). Interrupted after the first chunk commits, the
     * committed chunk stays valid and is not reported complete; a retry continues the same Record, in order, with
     * no duplicate Record or link.
     */
    @Test
    void safeguard2_largeGroupIsAppliedInResumableChunks() {
        int n = 2345;
        List<UUID> ids = rx.syntheticFiles(n);
        CaptureSession s = rx.session("Very long record");
        for (int from = 0; from < n; from += CaptureSessionService.ADD_LIMIT) {
            rx.records().sessions().addFiles(rx.admin, s.captureSessionId(),
                    ids.subList(from, Math.min(n, from + CaptureSessionService.ADD_LIMIT)));
        }
        CaptureRow first = rx.row(ids.get(0));
        rx.records().sessions().saveMarks(rx.admin, s.captureSessionId(), List.of(
                new CaptureSessionService.MarkEdit(ids.get(0), first.version(), true, null, "1", "DEED")));

        GroupingService crashing = rx.grouping(crashAt(GroupingFaults.Point.AFTER_CHUNK, 1));
        assertThatThrownBy(() -> crashing.apply(rx.admin, s.captureSessionId())).isInstanceOf(Crash.class);

        UUID record = rx.row(ids.get(0)).appliedRecordId();
        assertThat(record).isNotNull();
        assertThat(count("SELECT count(*) FROM gas.record_file WHERE record_id = '" + record + "'")).isEqualTo(1000);
        assertThat(count("SELECT count(*) FROM gas.grouping_run_group WHERE group_completed")).as("partial is not complete")
                .isZero();
        assertThat(rx.row(ids.get(999)).applied()).isTrue();
        assertThat(rx.row(ids.get(1000)).applied()).isFalse();

        GroupingRun retry = rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(retry.state()).isEqualTo(GroupingRun.State.COMPLETED);
        assertThat(retry.recordsCreated()).isZero();
        assertThat(retry.rowsApplied()).isEqualTo(n - 1000);
        assertThat(count("SELECT count(*) FROM gas.archival_record WHERE capture_session_id = '" + s.captureSessionId() + "'"))
                .as("one Record").isEqualTo(1);
        assertThat(linked(record)).as("every File once, in order").containsExactlyElementsOf(ids);
        assertThat(count("SELECT count(*) FROM gas.grouping_run_group WHERE run_id = '" + retry.runId()
                + "' AND rows_applied > 1000")).isZero();
        assertThat(count("SELECT count(*) FROM gas.grouping_run_group WHERE run_id = '" + retry.runId()
                + "' AND group_completed")).isEqualTo(1);
    }

    /** A large group that is not interrupted is recorded as chunks, completed only by the last one. */
    @Test
    void safeguard2_largeGroupWithoutInterruption() {
        int n = 1001;
        List<UUID> ids = rx.syntheticFiles(n);
        CaptureSession s = rx.session("Just over one chunk");
        rx.records().sessions().addFiles(rx.admin, s.captureSessionId(), ids);
        CaptureRow first = rx.row(ids.get(0));
        rx.records().sessions().saveMarks(rx.admin, s.captureSessionId(), List.of(
                new CaptureSessionService.MarkEdit(ids.get(0), first.version(), false, "big", null, null)));
        GroupingRun run = rx.records().grouping().apply(rx.editor, s.captureSessionId());
        List<String> chunks = new ArrayList<>();
        for (var c : rx.records().context().runs().chunks(run.runId())) {
            chunks.add(c.chunkNumber() + ":" + c.rowsApplied() + ":" + c.groupCompleted() + ":" + c.created());
        }
        assertThat(chunks).containsExactly("1:1000:false:true", "2:1:true:false");
        assertThat(linked(rx.row(ids.get(0)).appliedRecordId())).containsExactlyElementsOf(ids);
    }

    // ---- preservation originals are untouched ---------------------------------------------------

    @Test
    void groupingNeverTouchesPreservationOriginals() throws IOException {
        Scenario sc = threeGroups();
        Map<UUID, byte[]> before = new HashMap<>();
        for (ArchivalFile f : sc.files()) {
            before.put(f.fileId(), Files.readAllBytes(rx.fx.storedPath(f)));
        }
        rx.records().grouping().apply(rx.admin, sc.session().captureSessionId());
        for (ArchivalFile f : sc.files()) {
            ArchivalFile now = rx.fx.file(f.fileId());
            assertThat(rx.fx.storedPath(now)).isEqualTo(rx.fx.storedPath(f));
            assertThat(Files.readAllBytes(rx.fx.storedPath(now))).isEqualTo(before.get(f.fileId()));
            assertThat(now.managedFilename()).isEqualTo(f.managedFilename());
        }
    }
}
