package org.genealogyarchive.worker;

import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileContent;
import org.genealogyarchive.preservation.FileOperation;
import org.genealogyarchive.preservation.OperationState;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.testsupport.KillableWorkerMain;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GAS-P2-002 (Phase 2 plan section 9; F-002, TX-003/005): a real, separately launched worker JVM is
 * forcibly terminated by the operating system (Process#destroyForcibly: SIGKILL on Linux,
 * TerminateProcess on Windows) while a partial copy is on disk; a fresh worker JVM then recovers. No
 * graceful shutdown, no exception unwinding, no Spring context close is involved in the termination.
 */
class WorkerProcessKillIT {

    @TempDir
    Path temp;
    PreservationFixture fx;
    final List<Process> processes = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        fx = PreservationFixture.create(temp);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        for (Process p : processes) {
            if (p.isAlive()) {
                p.destroyForcibly();
                p.waitFor(30, TimeUnit.SECONDS);
            }
        }
        fx.close();
    }

    private Path writeConfig() throws IOException {
        String yaml = fx.db.installationYaml() + """
                storageRoots:
                  '%s': '%s'
                files:
                  stagingDirectory: '%s'
                  cacheDirectory: '%s'
                """.formatted(fx.volumeId, fx.volumeRoot.toAbsolutePath(), fx.staging.toAbsolutePath(),
                fx.cache.toAbsolutePath());
        return Files.writeString(temp.resolve("installation.yml"), yaml);
    }

    private Process launchWorker(Path config, Path log, String pauseAt, Path marker) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin",
                File.separatorChar == '\\' ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-cp", System.getProperty("java.class.path")));
        if (pauseAt != null) {
            command.add("-Dgas.test.pause-at=" + pauseAt);
            command.add("-Dgas.test.pause-marker=" + marker.toAbsolutePath());
        }
        command.add(KillableWorkerMain.class.getName());
        command.add(config.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        pb.environment().putAll(fx.db.cliEnvironment());
        Process p = pb.start();
        processes.add(p);
        return p;
    }

    private static void await(Supplier<Boolean> condition, Duration limit, String what, Path log) throws Exception {
        Instant deadline = Instant.now().plus(limit);
        while (!condition.get()) {
            if (Instant.now().isAfter(deadline)) {
                String tail = Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "(no log)";
                throw new AssertionError("timed out waiting for " + what + "\n--- worker log ---\n"
                        + tail.substring(Math.max(0, tail.length() - 6000)));
            }
            Thread.sleep(200);
        }
    }

    private static byte[] content() {
        byte[] b = new byte[3 * 1024 * 1024 + 4099]; // several copy chunks
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (i * 31 + (i >>> 13));
        }
        return b;
    }

    private List<Path> filesUnder(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void gasP2_002_aWorkerProcessKilledMidCopyIsRecoveredByAFreshWorkerProcess() throws Exception {
        byte[] bytes = content();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        UUID batch = fx.newBatch(fx.editor);
        UUID id = fx.receive(fx.editor, batch, "kill-1", "Kirchenbuch 1790 S. 4.tif", bytes, Restriction.NORMAL, null, null)
                .operationId();
        assertThat(fx.operation(id).state()).isEqualTo(OperationState.STAGED);
        Path config = writeConfig();

        // 1. A real worker process copies part of the file and is stopped there.
        Path marker = temp.resolve("paused.txt");
        Path firstLog = temp.resolve("worker-1.log");
        Process first = launchWorker(config, firstLog, "DURING_COPY", marker);
        await(() -> Files.exists(marker), Duration.ofSeconds(120), "the worker to reach the middle of the copy", firstLog);
        assertThat(Files.readString(marker).trim()).isEqualTo(id.toString());
        FileOperation midCopy = fx.operation(id);
        assertThat(midCopy.state()).isEqualTo(OperationState.PROMOTING);
        assertThat(midCopy.targetCreated()).isTrue();
        Path target = PortableRelativePath.of(midCopy.targetPath()).toNative(fx.volumeRoot);
        long partialSize = Files.size(target);
        assertThat(partialSize).as("a genuine partial copy is on disk").isPositive().isLessThan(bytes.length);

        // 2. Forcible operating-system termination: no shutdown hooks, no unwinding.
        first.destroyForcibly();
        assertThat(first.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(first.isAlive()).isFalse();
        assertThat(fx.operation(id).state()).as("nothing advanced the operation after the kill").isEqualTo(OperationState.PROMOTING);

        // 3. A fresh worker process recovers.
        Path secondLog = temp.resolve("worker-2.log");
        Process second = launchWorker(config, secondLog, null, null);
        await(() -> fx.operation(id).state() == OperationState.COMPLETED, Duration.ofSeconds(120),
                "the fresh worker to finish the upload", secondLog);

        FileOperation done = fx.operation(id);
        // Permanent identity and provenance: exactly one File, from this upload.
        List<ArchivalFile> files = fx.preservation().context().files().byBatch(batch);
        assertThat(files).singleElement().satisfies(f -> {
            assertThat(f.fileId()).isEqualTo(done.fileId());
            assertThat(f.friendlyId()).matches("F\\d{5,}");
            assertThat(f.ingestOperationId()).isEqualTo(id);
            assertThat(f.originalFilename()).isEqualTo("Kirchenbuch 1790 S. 4.tif");
            assertThat(f.ingestedBy()).isEqualTo(fx.editor.userId());
            assertThat(f.sourceNote()).isEqualTo("test source");
        });
        ArchivalFile file = files.get(0);
        // Content integrity and protection.
        FileContent content = fx.preservation().context().contents().find(file.contentId()).orElseThrow();
        assertThat(content.byteSize()).isEqualTo(bytes.length);
        assertThat(content.sha256()).isEqualTo(sha);
        Path stored = fx.storedPath(file);
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
        assertThat(SafeFiles.isReadOnly(stored)).isTrue();
        // The partial copy is quarantined and accounted for.
        List<QuarantineRepository.Entry> ledger = fx.preservation().context().quarantine().forOperation(id);
        assertThat(ledger).singleElement().satisfies(e -> {
            assertThat(e.reason()).isEqualTo(QuarantineRepository.Reason.PARTIAL_COPY);
            assertThat(e.state()).isEqualTo(QuarantineRepository.State.QUARANTINED);
            assertThat(e.sourcePath()).isEqualTo(midCopy.targetPath());
        });
        Path quarantined = PortableRelativePath.of(ledger.get(0).quarantinePath()).toNative(fx.volumeRoot);
        assertThat(Files.size(quarantined)).isEqualTo(partialSize);
        assertThat(filesUnder(fx.volumeRoot.resolve("quarantine"))).containsExactly(quarantined);
        // Staging cleaned.
        await(() -> fx.operation(id).stagingCleaned(), Duration.ofSeconds(30), "staging clean-up", secondLog);
        assertThat(filesUnder(fx.staging)).isEmpty();

        // 4. Idempotence: further recovery passes (the live worker's and an explicit one) change nothing.
        int events = fx.preservation().context().operations().events(id).size();
        Thread.sleep(3000); // the fresh worker runs recovery every second
        second.destroy();
        second.waitFor(60, TimeUnit.SECONDS);
        fx.rebuild();
        fx.preservation().recovery().runOnce();
        assertThat(fx.preservation().context().operations().events(id)).hasSize(events);
        assertThat(fx.preservation().context().files().byBatch(batch)).hasSize(1);
        assertThat(fx.preservation().context().quarantine().forOperation(id)).hasSize(1);
        assertThat(filesUnder(fx.volumeRoot.resolve("originals"))).containsExactly(stored);
        assertThat(fx.scan(fx.services(), "FULL")).isEmpty();
    }
}
