package org.genealogyarchive.data;

import org.genealogyarchive.data.LockSequence.Acquisition;
import org.genealogyarchive.data.LockSequence.Level;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.records.ArchivalRecord;
import org.genealogyarchive.records.CaptureSession;
import org.genealogyarchive.records.CatalogService;
import org.genealogyarchive.records.GroupingRun;
import org.genealogyarchive.records.Source;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LO-1 for the accepted Phase 3 paths (W12; clarifications s.5.5.3 and s.10.7). With the guard on, the real
 * Phase 3 services run unchanged; every explicit archival row lock they take is announced at L1 in the accepted
 * Phase 3 order (session, worksheet rows, Files, Record), and none takes a Phase 4 lock.
 */
class LockSequenceIT {

    @TempDir
    Path temp;
    RecordsFixture rx;
    final List<List<Acquisition>> sequences = new CopyOnWriteArrayList<>();
    AutoCloseable observer;

    @BeforeEach
    void setUp() throws Exception {
        rx = RecordsFixture.create(temp);
        observer = LockSequence.observe(s -> {
            if (!s.isEmpty()) {
                sequences.add(s);
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        observer.close();
        rx.close();
    }

    @Test
    void lo1_phase3PathsAnnounceOnlyLevel1LocksInTheAcceptedOrder() {
        assertThat(LockSequence.guardEnabled()).isTrue();
        List<ArchivalFile> f = rx.files(4);
        CaptureSession s = rx.session("Deed book visit (invented)");
        sequences.clear();
        rx.add(s, f);
        rx.mark(s, f.get(0), true, null, "227", "DEED");
        rx.start(s, f.get(2));
        GroupingRun run = rx.records().grouping().apply(rx.admin, s.captureSessionId());
        assertThat(run.state()).isEqualTo(GroupingRun.State.COMPLETED);

        List<List<Acquisition>> grouping = sequences.stream()
                .filter(q -> q.get(0).table().equals("capture_session")
                        && q.stream().anyMatch(a -> a.table().equals("archival_file"))).toList();
        assertThat(grouping).as("grouping chunks that lock rows and Files").isNotEmpty();
        for (List<Acquisition> q : grouping) {
            int firstFile = indexOf(q, "archival_file");
            assertThat(q.subList(1, firstFile)).as("worksheet rows precede the Files")
                    .isNotEmpty().allMatch(a -> a.table().equals("file_capture_context"));
            int record = indexOf(q, "archival_record");
            assertThat(q.subList(0, record)).as("a locked Record follows the session, rows and Files")
                    .allMatch(a -> a.table().equals("capture_session") || a.table().equals("file_capture_context")
                            || a.table().equals("archival_file"));
        }

        UUID recordId = rx.row(f.get(0).fileId()).appliedRecordId();
        ArchivalRecord record = rx.records().records().get(rx.admin, recordId);
        sequences.clear();
        rx.records().records().addRole(rx.admin, recordId, record.version(), "GRANTOR", "Robt. Andrews (invented)", null);
        Source source = rx.records().catalog().createSource(rx.admin, new CatalogService.SourceInput(
                "Deed Book Q (invented)", null, null, null, null, null, null, null));
        rx.records().citations().create(rx.admin, source.sourceId(), recordId, "p. 227", null, null);
        rx.records().catalog().correctSource(rx.admin, source.sourceId(), source.version(), new CatalogService.SourceInput(
                "Deed Book Q, corrected (invented)", null, null, null, null, null, null, null), "typo");
        assertThat(sequences).as("role, citation and source edits lock").hasSizeGreaterThanOrEqualTo(3);

        List<Acquisition> all = new ArrayList<>();
        sequences.forEach(all::addAll);
        assertThat(all).as("no Phase 3 path takes a Phase 4 lock").allMatch(a -> a.level() == Level.L1_PHASE3);
    }

    // ---- WP1-R3: rejection before the blocking SQL runs ----------------------------------------------

    /**
     * Two transactions each hold a File FOR SHARE and then ask for FOR UPDATE on it: executed, the two upgrades
     * would deadlock. The guard must reject both before any blocking SQL is sent, so each fails at once with a
     * {@link LockSequence.LockOrderViolation} rather than a lock timeout or a PostgreSQL deadlock error.
     */
    @Test
    void wp1r3_concurrentLevel1ShareHoldersCannotUpgrade() throws Exception {
        ArchivalFile file = rx.file();
        ArchivalFileRepository files = rx.records().context().files();
        org.springframework.transaction.support.TransactionTemplate tx = rx.records().context().tx();
        org.springframework.jdbc.core.simple.JdbcClient jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(rx.fx.ds);
        java.util.concurrent.CyclicBarrier bothShared = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<Throwable> upgrader = () -> {
            try {
                tx.executeWithoutResult(status -> {
                    jdbc.sql("SET LOCAL lock_timeout = '5s'").update();
                    files.lockShared(file.fileId());
                    await(bothShared);
                    files.lock(file.fileId());
                });
                return null;
            } catch (Throwable e) {
                return e;
            }
        };
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            long start = System.nanoTime();
            java.util.concurrent.Future<Throwable> a = pool.submit(upgrader);
            java.util.concurrent.Future<Throwable> b = pool.submit(upgrader);
            Throwable ea = a.get(30, java.util.concurrent.TimeUnit.SECONDS);
            Throwable eb = b.get(30, java.util.concurrent.TimeUnit.SECONDS);
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertThat(ea).isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("upgrade");
            assertThat(eb).isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("upgrade");
            assertThat(millis).as("rejected before waiting for the other holder").isLessThan(5_000);
        } finally {
            pool.shutdownNow();
        }
    }

    /** A single transaction: the upgrade is refused while another transaction holds the row FOR SHARE. */
    @Test
    void wp1r3_theGuardRejectsBeforeExecutingBlockingSql() throws Exception {
        ArchivalFile file = rx.file();
        ArchivalFileRepository files = rx.records().context().files();
        org.springframework.transaction.support.TransactionTemplate tx = rx.records().context().tx();
        org.springframework.jdbc.core.simple.JdbcClient jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(rx.fx.ds);
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread holder = new Thread(() -> tx.executeWithoutResult(status -> {
            files.lockShared(file.fileId());
            held.countDown();
            try {
                release.await(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        try {
            assertThat(held.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long start = System.nanoTime();
            Throwable e = org.assertj.core.api.Assertions.catchThrowable(() -> tx.executeWithoutResult(status -> {
                jdbc.sql("SET LOCAL lock_timeout = '5s'").update();
                files.lockShared(file.fileId());
                files.lock(file.fileId());
            }));
            assertThat(e).isInstanceOf(LockSequence.LockOrderViolation.class);
            assertThat((System.nanoTime() - start) / 1_000_000).as("no wait on the other holder").isLessThan(5_000);
        } finally {
            release.countDown();
            holder.join(30_000);
        }
    }

    private static void await(java.util.concurrent.CyclicBarrier barrier) {
        try {
            barrier.await(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int indexOf(List<Acquisition> q, String table) {
        for (int i = 0; i < q.size(); i++) {
            if (q.get(i).table().equals(table)) {
                return i;
            }
        }
        return q.size();
    }
}
