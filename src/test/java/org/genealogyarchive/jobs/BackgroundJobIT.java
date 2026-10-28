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
            public void run(JobExecution execution) throws Exception {
                action.run(execution.job());
            }
        };
    }

    /** A handler that sees its {@link JobExecution} (lease state). */
    private static JobHandler executionHandler(String kind, ExecutionAction action) {
        return new JobHandler() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public void run(JobExecution execution) throws Exception {
                action.run(execution);
            }
        };
    }

    @FunctionalInterface
    interface ExecutionAction {
        void run(JobExecution execution) throws Exception;
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
        Job claimedByA = repo.claim(List.of("test.recover"), workerA, Clock.systemUTC().instant(), Duration.ofMinutes(5)).orElseThrow();
        assertThat(job(id).state()).isEqualTo(JobState.RUNNING);
        assertThat(runner(handler("test.recover", j -> { })).runOne())
                .as("an unexpired lease is respected").isFalse();

        expireLease(id);
        AtomicInteger runs = new AtomicInteger();
        assertThat(runner(handler("test.recover", j -> runs.incrementAndGet())).runOne()).isTrue();

        assertThat(runs).hasValue(1);
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(job(id).attempts()).isEqualTo(2);
        assertThat(repo.succeed(id, claimedByA.leaseToken(), Clock.systemUTC().instant()))
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

    // ---- GAS-P1-001: lease ownership during live execution. Short real leases, so expiry and renewal
    // actually happen during the test rather than being inferred from the 5-minute production lease.

    private static final Duration SHORT_LEASE = Duration.ofSeconds(2);
    private static final Duration RENEW_EVERY = Duration.ofMillis(250);

    private JobRunner shortLeaseRunner(Duration renewInterval, JobHandler... handlers) {
        return new JobRunner(repo, Clock.systemUTC(), GlobalIds.newId(), SHORT_LEASE, renewInterval, List.of(handlers));
    }

    /** Polls until {@code condition} holds; fails the test after {@code timeout}. */
    private static void awaitTrue(Duration timeout, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("condition not met within " + timeout);
            }
            Thread.sleep(50);
        }
    }

    @Test
    void leaseIsRenewedWhileAHealthyWorkerRunsAHandlerLongerThanTheLease() throws Exception {
        UUID id = core.jobs().enqueue("test.long", "{}", 3);
        CountDownLatch release = new CountDownLatch(1);
        List<Boolean> leaseHeldWhileRunning = new java.util.concurrent.CopyOnWriteArrayList<>();
        JobRunner owner = shortLeaseRunner(RENEW_EVERY, executionHandler("test.long", e -> {
            assertThat(release.await(1, TimeUnit.MINUTES)).isTrue();
            leaseHeldWhileRunning.add(e.leaseHeld());
        }));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit((java.util.concurrent.Callable<Boolean>) owner::runOne);
            awaitTrue(Duration.ofSeconds(30), () -> job(id).state() == JobState.RUNNING);
            java.time.Instant firstLeaseEnd = job(id).leaseUntil();
            Thread.sleep(SHORT_LEASE.multipliedBy(2).toMillis()); // well past the original lease end

            Job during = job(id);
            assertThat(during.state()).isEqualTo(JobState.RUNNING);
            assertThat(during.leaseUntil()).as("the owner renewed its lease").isAfter(firstLeaseEnd);
            assertThat(during.leaseUntil()).as("the lease is still current").isAfter(firstLeaseEnd.plus(SHORT_LEASE));

            release.countDown();
            assertThat(result.get(1, TimeUnit.MINUTES)).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
            owner.close();
        }
        assertThat(leaseHeldWhileRunning).containsExactly(true);
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(job(id).attempts()).isEqualTo(1);
    }

    @Test
    void competingWorkerDoesNotClaimOrRunAJobWhoseOwnerIsRenewingItsLease() throws Exception {
        UUID id = core.jobs().enqueue("test.exclusive", "{}", 3);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ExecutionAction body = e -> {
            executions.incrementAndGet();
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                assertThat(release.await(1, TimeUnit.MINUTES)).isTrue();
            } finally {
                active.decrementAndGet();
            }
        };
        JobRunner owner = shortLeaseRunner(RENEW_EVERY, executionHandler("test.exclusive", body));
        JobRunner competitor = shortLeaseRunner(RENEW_EVERY, executionHandler("test.exclusive", body));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit((java.util.concurrent.Callable<Boolean>) owner::runOne);
            awaitTrue(Duration.ofSeconds(30), () -> executions.get() == 1);

            // The competitor keeps trying for three lease intervals.
            long end = System.nanoTime() + SHORT_LEASE.multipliedBy(3).toNanos();
            int competitorClaims = 0;
            while (System.nanoTime() < end) {
                if (competitor.runOne()) {
                    competitorClaims++;
                }
                Thread.sleep(100);
            }
            assertThat(competitorClaims).as("competing worker claimed a live job").isZero();

            release.countDown();
            assertThat(result.get(1, TimeUnit.MINUTES)).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
            owner.close();
            competitor.close();
        }
        assertThat(executions).as("handler executions").hasValue(1);
        assertThat(maxActive).as("concurrent handler executions").hasValue(1);
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(job(id).attempts()).isEqualTo(1);
    }

    @Test
    void jobAbandonedByAWorkerThatStoppedRenewingIsRecoveredAfterTheShortLeaseExpires() throws Exception {
        UUID id = core.jobs().enqueue("test.abandoned", "{}", 3);
        // The first worker claims and then disappears: nothing renews its lease.
        Job abandoned = repo.claim(List.of("test.abandoned"), GlobalIds.newId(), Clock.systemUTC().instant(), SHORT_LEASE)
                .orElseThrow();
        AtomicInteger runs = new AtomicInteger();
        JobRunner recoverer = shortLeaseRunner(RENEW_EVERY, handler("test.abandoned", j -> runs.incrementAndGet()));
        try {
            assertThat(recoverer.runOne()).as("lease not yet expired").isFalse();
            awaitTrue(Duration.ofSeconds(30), recoverer::runOne);
        } finally {
            recoverer.close();
        }
        assertThat(runs).hasValue(1);
        assertThat(job(id).state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(job(id).attempts()).isEqualTo(2);
        assertThat(repo.succeed(id, abandoned.leaseToken(), Clock.systemUTC().instant())).isFalse();
    }

    @Test
    void afterOwnershipTransfersTheStaleAttemptCanNeitherRenewNorRecordAnOutcome() throws Exception {
        UUID id = core.jobs().enqueue("test.transfer", "{}", 5);
        Job stale = repo.claim(List.of("test.transfer"), GlobalIds.newId(), Clock.systemUTC().instant(), Duration.ofMillis(300))
                .orElseThrow();
        Job[] current = new Job[1];
        awaitTrue(Duration.ofSeconds(30), () -> {
            current[0] = repo.claim(List.of("test.transfer"), GlobalIds.newId(), Clock.systemUTC().instant(), Duration.ofMinutes(5))
                    .orElse(null);
            return current[0] != null;
        });
        assertThat(current[0].leaseToken()).isNotEqualTo(stale.leaseToken());
        java.time.Instant now = Clock.systemUTC().instant();

        assertThat(repo.renew(id, stale.leaseToken(), Duration.ofMinutes(5), now)).as("stale renewal").isFalse();
        assertThat(repo.succeed(id, stale.leaseToken(), now)).as("stale success").isFalse();
        assertThat(repo.fail(id, stale.leaseToken(), "stale", now, now)).as("stale failure").isFalse();
        Job after = job(id);
        assertThat(after.state()).isEqualTo(JobState.RUNNING);
        assertThat(after.leaseToken()).isEqualTo(current[0].leaseToken());
        assertThat(after.lastError()).isNull();

        assertThat(repo.renew(id, current[0].leaseToken(), Duration.ofMinutes(5), now)).as("current owner renews").isTrue();
        assertThat(repo.succeed(id, current[0].leaseToken(), now)).isTrue();
    }

    @Test
    void workerThatStopsRenewingLosesTheJobObservesTheLossAndRecordsNoOutcome() throws Exception {
        UUID id = core.jobs().enqueue("test.stalled", "{}", 3);
        CountDownLatch recovered = new CountDownLatch(1);
        AtomicInteger recoveredRuns = new AtomicInteger();
        List<Boolean> staleSawLease = new java.util.concurrent.CopyOnWriteArrayList<>();
        // Renewal interval far beyond the lease: this worker is alive but stalled (e.g. paused or cut off
        // from the database), so its lease genuinely expires while its handler is still running.
        JobRunner stalled = shortLeaseRunner(Duration.ofMinutes(10), executionHandler("test.stalled", e -> {
            assertThat(recovered.await(1, TimeUnit.MINUTES)).isTrue();
            staleSawLease.add(e.leaseHeld());
            throw new IllegalStateException("stale attempt reports a failure");
        }));
        JobRunner recoverer = shortLeaseRunner(RENEW_EVERY, handler("test.stalled", j -> recoveredRuns.incrementAndGet()));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var stalledResult = pool.submit((java.util.concurrent.Callable<Boolean>) stalled::runOne);
            awaitTrue(Duration.ofSeconds(30), () -> job(id).state() == JobState.RUNNING);
            awaitTrue(Duration.ofSeconds(30), recoverer::runOne);
            recovered.countDown();
            assertThat(stalledResult.get(1, TimeUnit.MINUTES)).isTrue();
        } finally {
            recovered.countDown();
            pool.shutdownNow();
            stalled.close();
            recoverer.close();
        }
        assertThat(staleSawLease).as("stale attempt sees it no longer owns the job").containsExactly(false);
        assertThat(recoveredRuns).hasValue(1);
        Job finalState = job(id);
        assertThat(finalState.state()).as("the stale attempt's failure was not recorded").isEqualTo(JobState.SUCCEEDED);
        assertThat(finalState.lastError()).isNull();
        assertThat(finalState.attempts()).isEqualTo(2);
    }

    @Test
    void exhaustedAttemptsFailAnAbandonedJobButNotALiveOneRunningPastTheOriginalLease() throws Exception {
        // Live: one attempt allowed, handler runs for three lease intervals while another worker sweeps.
        UUID live = core.jobs().enqueue("test.live-once", "{}", 1);
        // Abandoned: one attempt allowed, claimed by a worker that then disappears.
        UUID abandoned = core.jobs().enqueue("test.abandoned-once", "{}", 1);
        repo.claim(List.of("test.abandoned-once"), GlobalIds.newId(), Clock.systemUTC().instant(), SHORT_LEASE).orElseThrow();

        CountDownLatch release = new CountDownLatch(1);
        JobRunner owner = shortLeaseRunner(RENEW_EVERY, handler("test.live-once", j ->
                assertThat(release.await(1, TimeUnit.MINUTES)).isTrue()));
        JobRunner sweeper = shortLeaseRunner(RENEW_EVERY, handler("test.unrelated", j -> { }));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var result = pool.submit((java.util.concurrent.Callable<Boolean>) owner::runOne);
            awaitTrue(Duration.ofSeconds(30), () -> job(live).state() == JobState.RUNNING);
            long end = System.nanoTime() + SHORT_LEASE.multipliedBy(3).toNanos();
            while (System.nanoTime() < end) {
                sweeper.runOne(); // runs the expired-lease sweep each time
                Thread.sleep(100);
            }
            assertThat(job(live).state()).as("live job past its original lease").isEqualTo(JobState.RUNNING);
            assertThat(job(abandoned).state()).as("abandoned job with no attempts left").isEqualTo(JobState.FAILED);
            assertThat(job(abandoned).lastError()).contains("worker stopped");

            release.countDown();
            assertThat(result.get(1, TimeUnit.MINUTES)).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
            owner.close();
            sweeper.close();
        }
        assertThat(job(live).state()).isEqualTo(JobState.SUCCEEDED);
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
