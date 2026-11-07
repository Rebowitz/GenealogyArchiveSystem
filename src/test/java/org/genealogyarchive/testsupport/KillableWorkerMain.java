package org.genealogyarchive.testsupport;

import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfigLoader;
import org.genealogyarchive.preservation.FaultPoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point of a separate worker JVM for the real process-termination test (GAS-P2-002). It starts the
 * production-wired worker exactly as {@code worker} does, plus one test fault point: when the system
 * property {@code gas.test.pause-at} names a protocol point, the worker writes the operation ID to the
 * file named by {@code gas.test.pause-marker} on reaching it and then blocks forever, so the test can
 * terminate the process at a known moment (for example mid-copy, with a partial copy on disk).
 */
public final class KillableWorkerMain {

    private KillableWorkerMain() {
    }

    public static void main(String[] args) throws Exception {
        InstallationConfig config = InstallationConfigLoader.load(Path.of(args[0]), System.getenv());
        ProcessLauncher.startWorker(config, Map.of(
                "gas.worker.heartbeat", "PT1S",
                "gas.worker.poll-interval", "PT0.2S",
                "gas.worker.recovery-interval", "PT1S"), PausingFaults.class);
        Thread.currentThread().join();
    }

    @Configuration(proxyBeanMethods = false)
    static class PausingFaults {
        @Bean
        FaultPoints faultPoints() {
            String pauseAt = System.getProperty("gas.test.pause-at");
            String marker = System.getProperty("gas.test.pause-marker");
            if (pauseAt == null) {
                return FaultPoints.NONE;
            }
            FaultPoints.Point point = FaultPoints.Point.valueOf(pauseAt);
            return (reached, operationId) -> {
                if (reached != point) {
                    return;
                }
                Files.writeString(Path.of(marker), operationId.toString(), StandardCharsets.UTF_8);
                while (true) {
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        // a forcible termination never returns here; keep blocking otherwise
                    }
                }
            };
        }
    }
}
