package org.genealogyarchive.preservation;

import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.genealogyarchive.testsupport.ScriptedFaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Crash, failure and restart recovery of file operations (F-002, TX-002..TX-005, TX-008, BI-008;
 * authorization requirement 1: active vs abandoned uploads).
 */
class RecoveryIT {

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

    private static byte[] content(String s) {
        // Larger than one copy chunk so DURING_COPY leaves a genuinely partial target.
        byte[] head = s.getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[2_600_000];
        for (int i = 0; i < b.length; i++) {
            b[i] = i < head.length ? head[i] : (byte) (i * 31);
        }
        return b;
    }

    private long count(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    private void assertArchivedOnce(UUID operationId, byte[] expected) throws IOException {
        FileOperation op = fx.operation(operationId);
        assertThat(op.state()).isEqualTo(OperationState.COMPLETED);
        ArchivalFile file = fx.file(op.fileId());
        Path stored = fx.storedPath(file);
        assertThat(Files.readAllBytes(stored)).isEqualTo(expected);
        assertThat(SafeFiles.isReadOnly(stored)).isTrue();
        assertThat(fx.preservation().context().files().findByIngestOperation(operationId)).isPresent();
        assertThat(fx.preservation().context().files().byBatch(op.batchId())).as("exactly one File").hasSize(1);
        assertThat(count(fx.staging)).as("staging cleaned").isZero();
    }

    @ParameterizedTest
    @EnumSource(value = FaultPoints.Point.class, names = {"BEFORE_TARGET_CREATE", "DURING_COPY", "BEFORE_PROTECT",
            "AFTER_PROTECT", "AFTER_VERIFY_BEFORE_COMMIT", "AFTER_COMMIT_BEFORE_CLEANUP"})
    void f002_aWorkerCrashAtAnyPointRecoversToExactlyOneVerifiedFile(FaultPoints.Point point) throws Exception {
        byte[] bytes = content("crash at " + point);
        UUID id = fx.receive(fx.editor, "page.tif", bytes).operationId();
        fx.faults.crashAt(point);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        assertThat(fx.operation(id).state()).isNotIn(OperationState.COMPLETED, OperationState.BLOCKED);

        fx.rebuild(); // a restarted worker: new services, same database and disks
        RecoveryService.Result r = fx.preservation().recovery().runOnce();
        assertThat(r.failed()).isZero();

        assertArchivedOnce(id, bytes);
        IntegrityScan scan = fx.preservation().integrity().requestScan(fx.admin, "FULL");
        fx.preservation().integrity().run(scan.scanId());
        assertThat(fx.preservation().integrity().findingsOfScan(fx.admin, scan.scanId()))
                .as("nothing unaccounted for is left in managed storage").isEmpty();
    }

    @Test
    void tx003_aPartialCopyFromAnInterruptedAttemptIsQuarantinedNotOverwritten() throws Exception {
        byte[] bytes = content("partial");
        UUID id = fx.receive(fx.editor, "partial.tif", bytes).operationId();
        fx.faults.crashAt(FaultPoints.Point.DURING_COPY);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        String firstTarget = fx.operation(id).targetPath();

        fx.preservation().recovery().runOnce();

        FileOperation op = fx.operation(id);
        assertThat(op.state()).isEqualTo(OperationState.COMPLETED);
        assertThat(op.targetPath()).as("a new location was planned").isNotEqualTo(firstTarget);
        assertThat(op.quarantinePath()).isNotNull();
        Path quarantined = PortableRelativePath.of(op.quarantinePath()).toNative(fx.volumeRoot);
        assertThat(Files.size(quarantined)).isLessThan(bytes.length).isPositive();
        assertThat(Files.exists(PortableRelativePath.of(firstTarget).toNative(fx.volumeRoot))).isFalse();
    }

    @Test
    void anIoFailureIsVisibleAndRetriedWithoutCreatingADuplicate() throws Exception {
        byte[] bytes = content("io failure");
        UUID id = fx.receive(fx.editor, "io.tif", bytes).operationId();
        fx.faults.ioFailureAt(FaultPoints.Point.BEFORE_PROTECT);
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(OperationProcessor.FileOperationFailedException.class);
        assertThat(fx.preservation().context().operations().events(id))
                .anyMatch(e -> e.detail() != null && e.detail().contains("attempt failed"));
        fx.process(id);
        assertArchivedOnce(id, bytes);
    }

    @Test
    void tx004_aMissingVolumeMakesTheOperationWaitAndItResumesWhenTheVolumeReturns() throws Exception {
        byte[] bytes = content("waiting");
        UUID id = fx.receive(fx.editor, "w.tif", bytes).operationId();
        var disconnected = fx.servicesWithRoot(temp.resolve("not-mounted"));
        disconnected.preservation().processor().process(id, () -> { });
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.WAITING_FOR_VOLUME);
        assertThat(fx.operation(id).message()).isNotBlank();
        disconnected.preservation().recovery().runOnce();
        assertThat(fx.operation(id).state()).as("still waiting; nothing guessed").isEqualTo(OperationState.WAITING_FOR_VOLUME);

        fx.preservation().recovery().runOnce();
        assertArchivedOnce(id, bytes);
    }

    @Test
    void p2d_aVolumeWhoseMarkerBelongsToAnotherArchiveIsNeverWritten() throws Exception {
        Path impostor = Files.createDirectories(temp.resolve("impostor"));
        org.genealogyarchive.files.ManagedVolume.initialize(impostor, UUID.randomUUID(), fx.volumeId, java.time.Instant.now());
        byte[] bytes = content("wrong volume");
        UUID id = fx.receive(fx.editor, "x.tif", bytes).operationId();
        fx.servicesWithRoot(impostor).preservation().processor().process(id, () -> { });
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.WAITING_FOR_VOLUME);
        assertThat(count(impostor.resolve("originals"))).isZero();
    }

    @Test
    void tx005_anObjectAtThePlannedLocationThatThisOperationNeverWroteBlocksAndIsNotTouched() throws Exception {
        byte[] bytes = content("collision");
        UUID id = fx.receive(fx.editor, "c.tif", bytes).operationId();
        fx.servicesWithRoot(temp.resolve("absent")).preservation().processor().process(id, () -> { });
        FileOperation waiting = fx.operation(id);
        assertThat(waiting.targetPath()).isNotNull();
        Path target = PortableRelativePath.of(waiting.targetPath()).toNative(fx.volumeRoot);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "someone else's file");

        fx.process(id);

        FileOperation blocked = fx.operation(id);
        assertThat(blocked.state()).isEqualTo(OperationState.BLOCKED);
        assertThat(blocked.blockedReason()).isEqualTo(BlockedReason.COLLISION);
        assertThat(Files.readString(target)).isEqualTo("someone else's file");
        assertThat(fx.preservation().operations().status(fx.admin, fx.preservation().volumes()).blocked()).isEqualTo(1);

        fx.preservation().operations().retry(fx.admin, id);
        fx.process(id);
        assertArchivedOnce(id, bytes);
        assertThat(Files.readString(target)).as("still untouched").isEqualTo("someone else's file");
    }

    @Test
    void cancellingABlockedOperationQuarantinesItsUncommittedCopyInTheWorker() throws Exception {
        byte[] bytes = content("cancel me");
        UUID id = fx.receive(fx.editor, "k.tif", bytes).operationId();
        fx.faults.arm(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT, op -> {
            throw new ScriptedFaults.SimulatedCrash(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT);
        });
        assertThatThrownBy(() -> fx.process(id)).isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        // Simulate damage to the promoted copy so verification blocks.
        Path target = PortableRelativePath.of(fx.operation(id).targetPath()).toNative(fx.volumeRoot);
        SafeFiles.clearReadOnly(target);
        Files.write(target, new byte[]{1, 2, 3});
        fx.process(id);
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.BLOCKED);
        assertThat(fx.operation(id).blockedReason()).isEqualTo(BlockedReason.VERIFY_MISMATCH);

        fx.preservation().operations().cancel(fx.admin, id);
        assertThat(fx.operation(id).state()).as("the web side only records the request").isEqualTo(OperationState.BLOCKED);
        assertThat(Files.exists(target)).isTrue();

        assertThat(fx.preservation().operations().performCancel(id, "admin1")).isTrue();
        FileOperation cancelled = fx.operation(id);
        assertThat(cancelled.state()).isEqualTo(OperationState.CANCELLED);
        assertThat(Files.exists(target)).isFalse();
        assertThat(Files.exists(PortableRelativePath.of(cancelled.quarantinePath()).toNative(fx.volumeRoot))).isTrue();
        assertThat(cancelled.fileId()).isNull();
        assertThat(count(fx.staging)).isZero();
    }

    @Test
    void requirement1_aCrashedReceiverLeavesAnUploadThatRecoveryAbandonsOnlyAfterTheTimeout() throws Exception {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1L << 30, Duration.ofSeconds(2)));
        fx.faults.crashAt(FaultPoints.Point.AFTER_STAGING_WRITE);
        UUID batch = fx.newBatch(fx.editor);
        assertThatThrownBy(() -> fx.receive(fx.editor, batch, "k", "n.tif", content("n"), Restriction.NORMAL, null, null))
                .isInstanceOf(ScriptedFaults.SimulatedCrash.class);
        UUID id = fx.preservation().context().operations().findByBatchItem(batch, "k").orElseThrow().operationId();

        assertThat(fx.preservation().recovery().runOnce().abandoned()).as("too recent to abandon").isZero();
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.RECEIVING);
        assertThat(count(fx.staging)).isEqualTo(1);

        Thread.sleep(3500);
        assertThat(fx.preservation().recovery().runOnce().abandoned()).isEqualTo(1);
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.ABANDONED);
        assertThat(count(fx.staging)).as("abandoned staged bytes cleaned").isZero();
    }

    /** Delivers a byte every {@code gapMillis}, optionally stalling once for {@code stallMillis}. */
    private static InputStream trickle(int total, long gapMillis, int stallAfter, long stallMillis) {
        return new InputStream() {
            int sent;

            @Override
            public int read() throws IOException {
                if (sent >= total) {
                    return -1;
                }
                try {
                    Thread.sleep(sent == stallAfter ? stallMillis : gapMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                sent++;
                return 65;
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
    void requirement1_aSlowButActiveUploadIsNeverDiscardedByRecovery() throws Exception {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1L << 30, Duration.ofSeconds(2)));
        UUID batch = fx.newBatch(fx.editor);
        AtomicBoolean running = new AtomicBoolean(true);
        int[] abandonedByRecovery = {0};
        CompletableFuture<Void> recovery = CompletableFuture.runAsync(() -> {
            while (running.get()) {
                abandonedByRecovery[0] += fx.preservation().recovery().runOnce().abandoned();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        // 24 bytes at 300 ms each: about 7 seconds in total, far longer than the 2-second timeout.
        FileOperation op = fx.preservation().ingestion().receive(fx.editor,
                new IngestionService.Upload(batch, "slow", "slow.txt", null, null, null, null, null), 24,
                trickle(24, 300, -1, 0));
        running.set(false);
        recovery.get(10, TimeUnit.SECONDS);

        assertThat(abandonedByRecovery[0]).isZero();
        assertThat(op.state()).isEqualTo(OperationState.STAGED);
        assertThat(op.expectedSize()).isEqualTo(24);
    }

    @Test
    void requirement1_anUploadThatStallsPastTheTimeoutIsAbandonedAndCannotLaterBeArchived() throws Exception {
        fx.withSettings(new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), 1L << 30, Duration.ofSeconds(2)));
        UUID batch = fx.newBatch(fx.editor);
        CompletableFuture<FileOperation> upload = CompletableFuture.supplyAsync(() -> fx.preservation().ingestion().receive(
                fx.editor, new IngestionService.Upload(batch, "stall", "stall.txt", null, null, null, null, null), 20,
                trickle(20, 100, 5, 4500)));
        Thread.sleep(3500);
        assertThat(fx.preservation().recovery().runOnce().abandoned()).isEqualTo(1);

        FileOperation op = upload.get(20, TimeUnit.SECONDS);
        assertThat(op.state()).isEqualTo(OperationState.ABANDONED);
        assertThat(op.fileId()).isNull();
        assertThat(count(fx.staging)).isZero();
    }
}
