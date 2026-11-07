package org.genealogyarchive.preservation;

import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.genealogyarchive.testsupport.ScriptedFaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Connection-pool exhaustion from repair locking (final outstanding finding): with a deliberately small
 * pool (3 connections) and more idle repairs than connections, destruction intent and removal must still
 * proceed or fail visibly, never exhaust the pool, and never let a repair write after the intent commits.
 */
class RepairLockPoolIT {

    private static final int POOL = 3;
    private static final int IDLE_REPAIRS = 6;

    @TempDir
    Path temp;
    PreservationFixture fx;

    @BeforeEach
    void setUp() throws IOException {
        fx = PreservationFixture.withPoolSize(temp, POOL);
    }

    @AfterEach
    void tearDown() {
        fx.faults.clear();
        fx.close();
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] large(String head) {
        byte[] bytes = new byte[3 * 1024 * 1024];
        byte[] h = b(head);
        System.arraycopy(h, 0, bytes, 0, h.length);
        for (int i = h.length; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31);
        }
        return bytes;
    }

    /**
     * Content whose original was lost (recorded MISSING), so it needs repair. (A damaged original would stay at
     * its location as an unresolved object after destruction - V0018 audit - which these tests are not about.)
     */
    private ArchivalFile damaged(String name, byte[] bytes) throws Exception {
        ArchivalFile f = fx.ingest(fx.editor, name, bytes);
        Path stored = fx.storedPath(f);
        SafeFiles.clearReadOnly(stored);
        Files.delete(stored);
        fx.scan(fx.services(), "QUICK");
        assertThat(fx.preservation().context().contents().find(f.contentId()).orElseThrow().state())
                .isEqualTo(ContentState.MISSING);
        return f;
    }

    private FileOperation receiveRepair(ArchivalFile f, byte[] bytes) {
        return fx.preservation().repair().receive(fx.admin, f.contentId(), "good.tif", bytes.length,
                new ByteArrayInputStream(bytes));
    }

    /** Repairs planned and then left idle (as after a stop before creating their copy). */
    private List<FileOperation> idleRepairs(ArchivalFile f, byte[] bytes) {
        List<FileOperation> idle = new ArrayList<>();
        for (int i = 0; i < IDLE_REPAIRS; i++) {
            UUID id = receiveRepair(f, bytes).operationId();
            fx.faults.crashAt(FaultPoints.Point.BEFORE_TARGET_CREATE);
            assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
            assertThat(fx.operation(id).state()).isEqualTo(OperationState.PROMOTING);
            idle.add(fx.operation(id));
        }
        fx.faults.clear();
        return idle;
    }

    private int activeConnections() {
        return fx.ds.getHikariPoolMXBean().getActiveConnections();
    }

    private ArchivalFile deleted(ArchivalFile f) {
        return fx.preservation().files().delete(fx.editor, f.fileId(), fx.file(f.fileId()).version(), null);
    }

    @Test
    void destructionWithMoreIdleRepairsThanPoolConnectionsProceedsWithoutExhaustingThePool() throws Exception {
        byte[] bytes = b("census page, many idle repairs");
        ArchivalFile f = damaged("census.tif", bytes);
        List<FileOperation> idle = idleRepairs(f, bytes);
        ArchivalFile gone = deleted(f);

        long started = System.nanoTime();
        FileOperation op = fx.preservation().files().destroy(fx.admin, f.fileId(), gone.version(), "duplicate scan", null);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("no wait for a pooled connection")
                .isLessThan(Duration.ofSeconds(5));
        assertThat(activeConnections()).isZero();
        for (FileOperation r : idle) {
            FileOperation stopped = fx.operation(r.operationId());
            assertThat(stopped.state()).isEqualTo(OperationState.CANCELLED);
            assertThat(stopped.stagingCleaned()).isTrue();
        }

        // No stopped repair writes afterwards, however often it is driven.
        for (FileOperation r : idle) {
            fx.process(r.operationId());
            assertThat(fx.volumeRoot.resolve(r.targetPath())).doesNotExist();
        }
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        fx.preservation().recovery().runOnce();
        assertThat(activeConnections()).isZero();
        try (var files = Files.walk(fx.volumeRoot.resolve("originals"))) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void aRepairWritingAmongManyIdleOnesMakesDestructionFailVisiblyAndThePoolStaysUsable() throws Exception {
        byte[] bytes = large("plat, one repair writing among idle ones");
        ArchivalFile f = damaged("plat.tif", bytes);
        List<FileOperation> idle = idleRepairs(f, bytes);
        UUID writing = receiveRepair(f, bytes).operationId();
        ArchivalFile gone = deleted(f);

        CountDownLatch copying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger chunks = new AtomicInteger();
        fx.faults.armSticky(FaultPoints.Point.DURING_COPY, id -> {
            if (id.equals(writing) && chunks.incrementAndGet() == 1) {
                copying.countDown();
                try {
                    release.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        var worker = Executors.newSingleThreadExecutor();
        try {
            var repairing = worker.submit(() -> fx.process(writing));
            assertThat(copying.await(30, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            assertThatThrownBy(() -> fx.preservation().files().destroy(fx.admin, f.fileId(), gone.version(), "race", null))
                    .isInstanceOf(RuleViolationException.class).hasMessageContaining("being written right now");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(8));
            // Other work still gets connections while the repair copies.
            assertThat(fx.services().history().recent(fx.admin, 10)).isNotEmpty();
            assertThat(idle).allSatisfy(r -> assertThat(fx.operation(r.operationId()).state()).isEqualTo(OperationState.PROMOTING));

            release.countDown();
            assertThat(repairing.get(60, TimeUnit.SECONDS).state()).isEqualTo(OperationState.COMPLETED);
        } finally {
            release.countDown();
            worker.shutdownNow();
            fx.faults.clear();
        }

        // The repair has finished: destruction now proceeds and stops the idle ones.
        FileOperation op = fx.preservation().files().destroy(fx.admin, f.fileId(), gone.version(), "after repair", null);
        assertThat(idle).allSatisfy(r -> assertThat(fx.operation(r.operationId()).state()).isEqualTo(OperationState.CANCELLED));
        fx.preservation().destruction().process(op.operationId());
        assertThat(fx.operation(op.operationId()).state()).isEqualTo(OperationState.COMPLETED);
        assertThat(activeConnections()).isZero();
    }

    @Test
    void anyNumberOfOperationLocksIsHeldOnOneConnection() {
        OperationLocks locks = fx.preservation().context().locks();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4 * POOL; i++) {
            ids.add(UUID.randomUUID());
        }
        OperationLocks.AllOrBusy all = locks.tryLockAll("file-operation", ids);
        assertThat(all.busy()).isEmpty();
        try (OperationLocks.Held held = all.held().orElseThrow()) {
            assertThat(activeConnections()).isEqualTo(1);
            // Each lock is really held: another session cannot take any of them.
            assertThat(locks.tryLock("file-operation", ids.get(ids.size() - 1))).isEmpty();
            OperationLocks.AllOrBusy again = locks.tryLockAll("file-operation", List.of(UUID.randomUUID(), ids.get(0)));
            assertThat(again.busy()).hasValue(ids.get(0));
            assertThat(again.held()).isEmpty();
            assertThat(activeConnections()).isEqualTo(1);
        }
        assertThat(activeConnections()).isZero();
        try (var one = locks.tryLock("file-operation", ids.get(0)).orElseThrow()) {
            assertThat(one).isNotNull();
        }
    }
}
