package org.genealogyarchive.data;

import org.genealogyarchive.jobs.Job;
import org.genealogyarchive.jobs.JobState;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * background_job rows (V0006): technical scheduling state for the worker. Not Archival Operation
 * History. Claiming uses FOR UPDATE SKIP LOCKED so concurrent workers never take the same job.
 * <p>
 * Lease ownership (GAS-P1-001): each claim issues a fresh {@code lease_token}. Renewal and completion
 * require that token and an unexpired lease. Lease times are compared with the database clock
 * ({@code clock_timestamp()}), so workers on hosts with different clocks agree on expiry.
 */
public final class BackgroundJobRepository {

    private static final String COLUMNS = """
            job_id, kind, state, payload::text AS payload, attempts, max_attempts, run_after, lease_owner,
            lease_token, lease_until, last_error, created_at, updated_at, finished_at""";

    private static final RowMapper<Job> JOB = (rs, n) -> new Job(
            Rows.uuid(rs, "job_id"), rs.getString("kind"), JobState.valueOf(rs.getString("state")),
            rs.getString("payload"), rs.getInt("attempts"), rs.getInt("max_attempts"),
            Rows.instant(rs, "run_after"), Rows.uuid(rs, "lease_owner"), Rows.uuid(rs, "lease_token"),
            Rows.instant(rs, "lease_until"),
            rs.getString("last_error"), Rows.instant(rs, "created_at"), Rows.instant(rs, "updated_at"),
            Rows.instant(rs, "finished_at"));

    /** The claimed row exactly as written by the claiming UPDATE (GAS-P1-R01). */
    private static final String CLAIMED_COLUMNS = """
            j.job_id, j.kind, j.state, j.payload::text AS payload, j.attempts, j.max_attempts, j.run_after,
            j.lease_owner, j.lease_token, j.lease_until, j.last_error, j.created_at, j.updated_at, j.finished_at""";

    private final JdbcClient jdbc;
    private final Consumer<Job> claimDelivery;

    public BackgroundJobRepository(DataSource dataSource) {
        this(dataSource, claimed -> { });
    }

    /**
     * Test seam: {@code claimDelivery} runs after the claiming statement has completed and before its
     * result is returned to the caller - the boundary at which a separate re-read could previously
     * observe a later attempt. Production code uses {@link #BackgroundJobRepository(DataSource)}.
     */
    public BackgroundJobRepository(DataSource dataSource, Consumer<Job> claimDelivery) {
        this.jdbc = JdbcClient.create(dataSource);
        this.claimDelivery = claimDelivery;
    }

    public void insert(UUID jobId, String kind, String payloadJson, int maxAttempts, Instant runAfter) {
        jdbc.sql("""
                INSERT INTO gas.background_job (job_id, kind, state, payload, max_attempts, run_after)
                VALUES (:id, :kind, 'QUEUED', CAST(:payload AS jsonb), :max, :runAfter)
                """)
                .param("id", jobId).param("kind", kind).param("payload", payloadJson)
                .param("max", maxAttempts).param("runAfter", Rows.param(runAfter))
                .update();
    }

    public Optional<Job> findById(UUID jobId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.background_job WHERE job_id = :id")
                .param("id", jobId).query(JOB).optional();
    }

    public List<Job> recent(int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.background_job ORDER BY created_at DESC, job_id DESC LIMIT :limit")
                .param("limit", limit).query(JOB).list();
    }

    public Map<JobState, Long> countsByState() {
        Map<JobState, Long> counts = new EnumMap<>(JobState.class);
        for (JobState s : JobState.values()) {
            counts.put(s, 0L);
        }
        jdbc.sql("SELECT state, count(*) AS n FROM gas.background_job GROUP BY state")
                .query((rs, n) -> Map.entry(JobState.valueOf(rs.getString("state")), rs.getLong("n")))
                .list().forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    /**
     * Running jobs whose lease expired (the worker stopped or crashed) and that have no attempts left
     * are marked FAILED, visibly, instead of being retried forever.
     */
    public int failExpiredWithoutAttemptsLeft(Instant now) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET state = 'FAILED', lease_owner = NULL, lease_token = NULL, lease_until = NULL, finished_at = :now, updated_at = :now,
                       last_error = 'The worker stopped or lost its lease before the job finished and no attempts remain.'
                 WHERE state = 'RUNNING' AND lease_until < clock_timestamp() AND attempts >= max_attempts
                """).param("now", Rows.param(now)).update();
    }

    /**
     * Claims one runnable job of the given kinds: queued and due, or running with an expired lease
     * (recovery after a worker was lost, OP-001). Increments attempts and takes a lease with a
     * fresh token. A lease that its owner keeps renewing never expires, so it is never reclaimed here.
     * <p>
     * GAS-P1-R01: the result is the attempt snapshot produced by this claim's own
     * {@code UPDATE ... RETURNING} - the token, owner, attempt number and lease end written by this
     * statement. There is no second read, so a later reclaim can never substitute its token here.
     */
    public Optional<Job> claim(Collection<String> kinds, UUID workerId, Instant now, Duration lease) {
        if (kinds.isEmpty()) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        Optional<Job> claimed = jdbc.sql("""
                WITH next AS (
                    SELECT job_id FROM gas.background_job
                     WHERE kind IN (:kinds)
                       AND attempts < max_attempts
                       AND ((state = 'QUEUED' AND run_after <= :now) OR (state = 'RUNNING' AND lease_until < clock_timestamp()))
                     ORDER BY run_after, job_id
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED)
                UPDATE gas.background_job j
                   SET state = 'RUNNING', attempts = j.attempts + 1, lease_owner = :worker, lease_token = :token,
                       lease_until = clock_timestamp() + make_interval(secs => CAST(:leaseMillis AS double precision) / 1000),
                       updated_at = :now
                  FROM next
                 WHERE j.job_id = next.job_id
                RETURNING""" + " " + CLAIMED_COLUMNS)
                .param("kinds", List.copyOf(kinds)).param("now", Rows.param(now)).param("worker", workerId)
                .param("token", token).param("leaseMillis", lease.toMillis())
                .query(JOB).optional();
        claimed.ifPresent(job -> {
            if (!token.equals(job.leaseToken()) || !workerId.equals(job.leaseOwner())) {
                throw new IllegalStateException("Claim of job " + job.jobId() + " returned another attempt's lease");
            }
        });
        claimed.ifPresent(claimDelivery);
        return claimed;
    }

    /**
     * Extends the lease of the attempt holding {@code leaseToken}, only while that lease is still
     * unexpired. A refused renewal means ownership is lost for good: the token is never reissued.
     *
     * @return true if the lease was extended
     */
    public boolean renew(UUID jobId, UUID leaseToken, Duration lease, Instant now) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET lease_until = clock_timestamp() + make_interval(secs => CAST(:leaseMillis AS double precision) / 1000),
                       updated_at = :now
                 WHERE job_id = :id AND state = 'RUNNING' AND lease_token = :token AND lease_until >= clock_timestamp()
                """).param("id", jobId).param("token", leaseToken).param("leaseMillis", lease.toMillis())
                .param("now", Rows.param(now)).update() == 1;
    }

    /** @return true if this attempt still held an unexpired lease and the job is now SUCCEEDED */
    public boolean succeed(UUID jobId, UUID leaseToken, Instant now) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET state = 'SUCCEEDED', lease_owner = NULL, lease_token = NULL, lease_until = NULL, last_error = NULL,
                       finished_at = :now, updated_at = :now
                 WHERE job_id = :id AND state = 'RUNNING' AND lease_token = :token AND lease_until >= clock_timestamp()
                """).param("id", jobId).param("token", leaseToken).param("now", Rows.param(now)).update() == 1;
    }

    /**
     * Records a failed attempt: re-queued after {@code retryAt} if attempts remain, otherwise FAILED.
     * Like {@link #succeed}, accepted only from the attempt holding an unexpired lease.
     */
    public boolean fail(UUID jobId, UUID leaseToken, String error, Instant now, Instant retryAt) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET state = CASE WHEN attempts < max_attempts THEN 'QUEUED' ELSE 'FAILED' END,
                       run_after = CASE WHEN attempts < max_attempts THEN :retryAt ELSE run_after END,
                       finished_at = CASE WHEN attempts < max_attempts THEN NULL ELSE :now END,
                       lease_owner = NULL, lease_token = NULL, lease_until = NULL, last_error = :error, updated_at = :now
                 WHERE job_id = :id AND state = 'RUNNING' AND lease_token = :token AND lease_until >= clock_timestamp()
                """)
                .param("id", jobId).param("token", leaseToken).param("error", error)
                .param("now", Rows.param(now)).param("retryAt", Rows.param(retryAt))
                .update() == 1;
    }

    /** Re-queues a FAILED job with one more attempt. @return true if the job was FAILED */
    public boolean retry(UUID jobId, Instant now) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET state = 'QUEUED', max_attempts = LEAST(attempts + 1, 100), run_after = :now,
                       finished_at = NULL, updated_at = :now
                 WHERE job_id = :id AND state = 'FAILED' AND attempts < 100
                """).param("id", jobId).param("now", Rows.param(now)).update() == 1;
    }
}
