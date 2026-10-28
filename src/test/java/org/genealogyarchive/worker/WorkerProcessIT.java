package org.genealogyarchive.worker;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Background worker process starts only on a ready foundation and stops cleanly (service stop). */
class WorkerProcessIT {

    private TestDatabase db;

    @BeforeEach
    void createDatabase() {
        db = TestDatabase.createFresh();
    }

    @AfterEach
    void dropDatabase() {
        db.close();
    }

    @Test
    void startsOnMigratedDatabaseAndStopsCleanly() {
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            new SchemaMigrator(ds).migrate();
        }
        ConfigurableApplicationContext worker =
                ProcessLauncher.startWorker(db.installationConfig(), Map.of("gas.worker.heartbeat", "PT1S"));
        WorkerProcess process = worker.getBean(WorkerProcess.class);
        assertThat(process.isRunning()).isTrue();
        assertThat(worker.getEnvironment().getProperty("local.server.port")).as("worker has no HTTP listener").isNull();

        worker.close();

        assertThat(process.isRunning()).isFalse();
    }

    @Test
    void refusesToStartOnUnmigratedDatabase() {
        assertThatThrownBy(() -> ProcessLauncher.startWorker(db.installationConfig(), Map.of()))
                .hasStackTraceContaining("Worker cannot start")
                .hasStackTraceContaining("NOT_INITIALIZED");
    }
}
