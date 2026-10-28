package org.genealogyarchive.worker;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.data.BackgroundJobRepository;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.jobs.Job;
import org.genealogyarchive.jobs.JobHandler;
import org.genealogyarchive.jobs.JobState;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.service.OperationalStatus;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The worker process runs persistent jobs, reports presence (OP-004/OP-005) and requires an initialized archive. */
class WorkerJobsIT {

    private TestDatabase db;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    /** Test-only job handler registered into the worker process. */
    @Configuration(proxyBeanMethods = false)
    static class TestHandlers {
        @Bean
        JobHandler echoHandler() {
            return new JobHandler() {
                @Override
                public String kind() {
                    return "test.worker-echo";
                }

                @Override
                public void run(Job job) {
                    // no side effects: completion is the observable result
                }
            };
        }
    }

    @Test
    void workerRefusesToStartBeforeTheArchiveIsInitialized() {
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            new SchemaMigrator(ds).migrate();
        }
        assertThatThrownBy(() -> ProcessLauncher.startWorker(db.installationConfig(), Map.of()))
                .hasStackTraceContaining("Worker cannot start")
                .hasStackTraceContaining("init-archive");
    }

    @Test
    void workerProcessesQueuedJobsAndReportsPresenceUntilStopped() throws Exception {
        ArchiveFixtures.migrateAndInitialize(db);
        UUID jobId;
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            jobId = ArchiveFixtures.services(ds).jobs().enqueue("test.worker-echo", "{}", 3);
        }

        ConfigurableApplicationContext worker = ProcessLauncher.startWorker(db.installationConfig(),
                Map.of("gas.worker.heartbeat", "PT1S", "gas.worker.poll-interval", "PT0.2S"), TestHandlers.class);
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            BackgroundJobRepository jobs = new BackgroundJobRepository(ds);
            Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
            while (jobs.findById(jobId).orElseThrow().state() != JobState.SUCCEEDED && Instant.now().isBefore(deadline)) {
                Thread.sleep(100);
            }
            assertThat(jobs.findById(jobId).orElseThrow().state()).isEqualTo(JobState.SUCCEEDED);

            Actor admin = Actor.user(UUID.randomUUID(), "admin", Role.ADMINISTRATOR);
            OperationalStatus running = ArchiveFixtures.services(ds).operationalStatus().view(admin);
            assertThat(running.anyWorkerAlive()).isTrue();

            worker.close();
            OperationalStatus stopped = ArchiveFixtures.services(ds).operationalStatus().view(admin);
            assertThat(stopped.workers()).as("a cleanly stopped worker removes its presence").isEmpty();
            assertThat(stopped.anyWorkerAlive()).isFalse();
        } finally {
            if (worker.isActive()) {
                worker.close();
            }
        }
    }
}
