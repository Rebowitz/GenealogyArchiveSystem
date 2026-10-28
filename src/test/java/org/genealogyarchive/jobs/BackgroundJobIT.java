package org.genealogyarchive.jobs;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.data.BackgroundJobRepository;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUILD-SPEC s.47 PostgreSQL-backed job foundation: OP-001 (survives restart / lease recovery),
 * OP-002 (failures inspectable and retryable), OP-004 (work waits without a handler/worker),
 * and no double processing between concurrent workers. Test-only job kinds.
 */
class BackgroundJobIT {

    private TestDatabase db;
    private HikariDataSource ds;
    private CoreServices core;
    private BackgroundJobRepository repo;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test", 8);
        new SchemaMigrator(ds).migrate();
        core = ArchiveFixtures.services(ds);
        repo = new BackgroundJobRepository(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private static JobHandler handler(String kind, JobAction action) {
        return new JobHandler() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public void run(Job job) throws Exception {
                action.run(job);
            }
        };
    }

    @FunctionalInterface
    interface JobAction {
        void run(Job job) throws Exception;
    }

    private JobRunner runner(JobHandler... handlers) {
        return new JobRunner(repo, Clock.systemUTC(), GlobalIds.newId(), Duration.ofMinutes(5), List.of(handlers));
    }

    private Job job(UUID id) {
        return repo.findById(id).orElseThrow();
    }

    @Test
    void queuedJobRunsOnceAndSucceeds() {
        AtomicInteger runs = new AtomicInteger();
        UUID id = core.jobs().enqueue("test.echo", "{\"text\":\"hello\"}", 3);
        assertThat(job(id).state()).isEqualTo(JobState.QUEUED);

        int ran = runner(handler("test.echo", j -> {
            assertThat(j.payloadJson()).contains("hello");
            runs.incrementAndGet();
        })).runAvailable(10);

        assertThat(ran).isEqualTo(1);
        assertThat(runs).hasValue(1);
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(job(id).finishedAt()).isNotNull();
    }

    @Test
    void failingJobIsRetriedThenFailsVisiblyAndCanBeRetriedByAnAdministrator() throws Exception {
        UUID id = core.jobs().enqueue("test.flaky", "{}", 2);
        AtomicInteger attempts = new AtomicInteger();
        JobRunner runner = runner(handler("test.flaky", j -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new IllegalStateException("thumbnail tool missing");
            }
        }));

        runner.runOne();
        assertThat(job(id).state()).isEqualTo(JobState.QUEUED);
        assertThat(job(id).lastError()).contains("thumbnail tool missing");
        makeDue(id);
        runner.runOne();
        assertThat(job(id).state()).as("OP-002: failure kept for inspection").isEqualTo(JobState.FAILED);
        assertThat(job(id).attempts()).isEqualTo(2);

        Actor viewer = Actor.user(GlobalIds.newId(), "viewer", Role.VIEWER);
        assertThatThrownBy(() -> core.jobs().retry(viewer, id)).isInstanceOf(AccessDeniedException.class);
        Actor admin = Actor.user(GlobalIds.newId(), "admin", Role.ADMINISTRATOR);
        core.jobs().retry(admin, id);
        assertThatThrownBy(() -> core.jobs().retry(admin, id)).as("only FAILED jobs can be retried")
                .isInstanceOf(RuleViolationException.class);
        runner.runOne();
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
    }

    @Test
    void jobOfAWorkerThatStoppedIsRecoveredAfterItsLeaseExpires() throws Exception {
        UUID id = core.jobs().enqueue("test.recover", "{}", 3);
        // Worker A claims the job and then "crashes" (never reports completion).
        UUID workerA = GlobalIds.newId();
        assertThat(repo.claim(List.of("test.recover"), workerA, Clock.systemUTC().instant(), Duration.ofMinutes(5))).isPresent();
        assertThat(job(id).state()).isEqualTo(JobState.RUNNING);
        assertThat(runner(handler("test.recover", j -> { })).runOne())
                .as("an unexpired lease is respected").isFalse();

        expireLease(id);
        AtomicInteger runs = new AtomicInteger();
        assertThat(runner(handler("test.recover", j -> runs.incrementAndGet())).runOne()).isTrue();

        assertThat(runs).hasValue(1);
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(job(id).attempts()).isEqualTo(2);
        assertThat(repo.succeed(id, workerA, Clock.systemUTC().instant()))
                .as("the crashed worker can no longer complete the job").isFalse();
    }

    @Test
    void expiredLeaseWithNoAttemptsLeftFailsVisibly() throws Exception {
        UUID id = core.jobs().enqueue("test.once", "{}", 1);
        repo.claim(List.of("test.once"), GlobalIds.newId(), Clock.systemUTC().instant(), Duration.ofMinutes(5));
        expireLease(id);
        runner(handler("test.once", j -> { })).runOne();
        assertThat(job(id).state()).isEqualTo(JobState.FAILED);
        assertThat(job(id).lastError()).contains("worker stopped");
    }

    @Test
    void jobsWithoutAHandlerWaitQueuedAndAreCounted() {
        core.jobs().enqueue("test.no-handler", "{}", 3);
        assertThat(runner(handler("test.other", j -> { })).runAvailable(10)).isZero();
        assertThat(core.jobs().counts()).containsEntry(JobState.QUEUED, 1L);
    }

    @Test
    void jobsPersistAcrossServiceRestarts() {
        UUID id = core.jobs().enqueue("test.persist", "{}", 3);
        ds.close();
        ds = DataSources.create(db.settings(), "test", 4);
        BackgroundJobRepository afterRestart = new BackgroundJobRepository(ds);
        assertThat(afterRestart.findById(id)).isPresent().get().extracting(Job::state).isEqualTo(JobState.QUEUED);
        repo = afterRestart;
    }

    @Test
    void concurrentWorkersNeverRunTheSameJobTwice() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ids.add(core.jobs().enqueue("test.count", "{}", 1));
        }
        Map<UUID, AtomicInteger> executions = new ConcurrentHashMap<>();
        JobHandler counting = handler("test.count", j -> {
            executions.computeIfAbsent(j.jobId(), k -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(5);
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int w = 0; w < 4; w++) {
            JobRunner runner = runner(counting);
            pool.submit(() -> {
                start.await();
                return runner.runAvailable(100);
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();

        assertThat(executions).hasSize(40);
        assertThat(executions.values()).allMatch(n -> n.get() == 1);
        assertThat(ids).allMatch(id -> job(id).state() == JobState.SUCCEEDED);
    }

    private void expireLease(UUID id) throws Exception {
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("UPDATE gas.background_job SET lease_until = now() - interval '1 minute' WHERE job_id = '" + id + "'");
        }
    }

    private void makeDue(UUID id) throws Exception {
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("UPDATE gas.background_job SET run_after = now() - interval '1 second' WHERE job_id = '" + id + "'");
        }
    }
}
