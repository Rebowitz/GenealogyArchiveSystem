package org.genealogyarchive.worker;

import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FaultPoints;
import org.genealogyarchive.preservation.FileOperation;
import org.genealogyarchive.preservation.OperationState;
import org.genealogyarchive.preservation.ThumbnailService;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.genealogyarchive.testsupport.ScriptedFaults;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real worker process drives Phase 2 jobs: ingestion through promotion, thumbnails, and recovery
 * after a crash inside a job (B3, F-002 with the production job and recovery wiring).
 */
class PreservationWorkerIT {

    /** Shared with the launched worker context; crashes the first promotion once after protection. */
    static final ScriptedFaults WORKER_FAULTS = new ScriptedFaults();

    @Configuration(proxyBeanMethods = false)
    static class Faults {
        @Bean
        FaultPoints faultPoints() {
            return WORKER_FAULTS;
        }
    }

    @TempDir
    Path temp;
    PreservationFixture fx;

    @BeforeEach
    void setUp() throws IOException {
        fx = PreservationFixture.create(temp);
        WORKER_FAULTS.clear();
    }

    @AfterEach
    void tearDown() {
        WORKER_FAULTS.clear();
        fx.close();
    }

    private ConfigurableApplicationContext startWorker() {
        InstallationConfig base = TestDatabase.installationConfig(fx.db.settings());
        InstallationConfig config = new InstallationConfig(base.sourceFile(), base.database(), base.server(), base.logging(),
                Map.of(fx.volumeId, fx.volumeRoot), fx.settings());
        return ProcessLauncher.startWorker(config, Map.of("gas.worker.heartbeat", "PT1S", "gas.worker.poll-interval", "PT0.2S",
                "gas.worker.recovery-interval", "PT1S"), Faults.class);
    }

    private static void await(Supplier<Boolean> condition, String what) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        while (!condition.get()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(100);
        }
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 640; x++) {
            image.setRGB(x, x * 3 / 4, 0xFFFFFF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void b3_theWorkerArchivesStagedUploadsAndMakesThumbnails() throws Exception {
        byte[] bytes = png();
        UUID id = fx.receive(fx.editor, "photo.png", bytes).operationId();
        ConfigurableApplicationContext worker = startWorker();
        try {
            await(() -> fx.operation(id).state() == OperationState.COMPLETED, "the upload to be archived");
            FileOperation op = fx.operation(id);
            ArchivalFile file = fx.file(op.fileId());
            assertThat(Files.readAllBytes(fx.storedPath(file))).isEqualTo(bytes);
            assertThat(file.mediaType()).isEqualTo("image/png");
            Path thumb = ThumbnailService.location(fx.cache, file.fileId());
            await(() -> Files.isRegularFile(thumb), "the thumbnail");
            BufferedImage t = ImageIO.read(thumb.toFile());
            assertThat(Math.max(t.getWidth(), t.getHeight())).isLessThanOrEqualTo(320);
        } finally {
            worker.close();
        }
    }

    @Test
    void f002_aCrashInsideAWorkerJobIsRecoveredToExactlyOneFile() throws Exception {
        WORKER_FAULTS.crashAt(FaultPoints.Point.AFTER_PROTECT);
        byte[] bytes = "a will proved in 1793".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        UUID id = fx.receive(fx.editor, "will.txt", bytes).operationId();
        ConfigurableApplicationContext worker = startWorker();
        try {
            await(() -> fx.operation(id).state() == OperationState.COMPLETED, "recovery to finish the upload");
            FileOperation op = fx.operation(id);
            assertThat(fx.preservation().context().files().byBatch(op.batchId())).hasSize(1);
            assertThat(fx.preservation().context().operations().events(id))
                    .as("the interrupted attempt and the recovery are both on record")
                    .filteredOn(e -> "PROMOTED".equals(e.toState())).hasSize(1);
            assertThat(Files.readAllBytes(fx.storedPath(fx.file(op.fileId())))).isEqualTo(bytes);
        } finally {
            worker.close();
        }
    }
}
