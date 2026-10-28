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

/**
 * background_job rows (V0006): technical scheduling state for the worker. Not Archival Operation
 * History. Claiming uses FOR UPDATE SKIP LOCKED so concurrent workers never take the same job.
 */
public final class BackgroundJobRepository {

    private static final String COLUMNS = """
            job_id, kind, state, payload::text AS payload, attempts, max_attempts, run_after, lease_owner,
            lease_until, last_error, created_at, updated_at, finished_at""";

    private static final RowMapper<Job> JOB = (rs, n) -> new Job(
            Rows.uuid(rs, "job_id"), rs.getString("kind"), JobState.valueOf(rs.getString("state")),
            rs.getString("payload"), rs.getInt("attempts"), rs.getInt("max_attempts"),
            Rows.instant(rs, "run_after"), Rows.uuid(rs, "lease_owner"), Rows.instant(rs, "lease_until"),
            rs.getString("last_error"), Rows.instant(rs, "created_at"), Rows.instant(rs, "updated_at"),
            Rows.instant(rs, "finished_at"));

    private final JdbcClient jdbc;

    public BackgroundJobRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
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
                   SET state = 'FAILED', lease_owner = NULL, lease_until = NULL, finished_at = :now, updated_at = :now,
                       last_error = 'The worker stopped before the job finished and no attempts remain.'
                 WHERE state = 'RUNNING' AND lease_until < :now AND attempts >= max_attempts
                """).param("now", Rows.param(now)).update();
    }

    /**
     * Claims one runnable job of the given kinds: queued and due, or running with an expired lease
     * (recovery after restart, OP-001). Increments attempts and takes a lease.
     */
    public Optional<Job> claim(Collection<String> kinds, UUID workerId, Instant now, Duration lease) {
        if (kinds.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                WITH next AS (
                    SELECT job_id FROM gas.background_job
                     WHERE kind IN (:kinds)
                       AND attempts < max_attempts
                       AND ((state = 'QUEUED' AND run_after <= :now) OR (state = 'RUNNING' AND lease_until < :now))
                     ORDER BY run_after, job_id
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED)
                UPDATE gas.background_job j
                   SET state = 'RUNNING', attempts = j.attempts + 1, lease_owner = :worker,
                       lease_until = :leaseUntil, updated_at = :now
                  FROM next
                 WHERE j.job_id = next.job_id
                RETURNING j.job_id
                """)
                .param("kinds", List.copyOf(kinds)).param("now", Rows.param(now)).param("worker", workerId)
                .param("leaseUntil", Rows.param(now.plus(lease)))
                .query(UUID.class).optional()
                .flatMap(this::findById);
    }

    /** @return true if this worker still held the lease and the job is now SUCCEEDED */
    public boolean succeed(UUID jobId, UUID workerId, Instant now) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET state = 'SUCCEEDED', lease_owner = NULL, lease_until = NULL, last_error = NULL,
                       finished_at = :now, updated_at = :now
                 WHERE job_id = :id AND state = 'RUNNING' AND lease_owner = :worker
                """).param("id", jobId).param("worker", workerId).param("now", Rows.param(now)).update() == 1;
    }

    /** Records a failed attempt: re-queued after {@code retryAt} if attempts remain, otherwise FAILED. */
    public boolean fail(UUID jobId, UUID workerId, String error, Instant now, Instant retryAt) {
        return jdbc.sql("""
                UPDATE gas.background_job
                   SET state = CASE WHEN attempts < max_attempts THEN 'QUEUED' ELSE 'FAILED' END,
                       run_after = CASE WHEN attempts < max_attempts THEN :retryAt ELSE run_after END,
                       finished_at = CASE WHEN attempts < max_attempts THEN NULL ELSE :now END,
                       lease_owner = NULL, lease_until = NULL, last_error = :error, updated_at = :now
                 WHERE job_id = :id AND state = 'RUNNING' AND lease_owner = :worker
                """)
                .param("id", jobId).param("worker", workerId).param("error", error)
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
