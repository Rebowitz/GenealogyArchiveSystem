package org.genealogyarchive.data;

import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** worker_presence rows (V0006): which worker processes are alive, for status reporting. */
public final class WorkerPresenceRepository {

    /** One worker process as last reported. */
    public record Presence(UUID workerId, Instant startedAt, Instant lastSeenAt, String appVersion) {
    }

    private final JdbcClient jdbc;

    public WorkerPresenceRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void heartbeat(UUID workerId, Instant startedAt, Instant now, String appVersion) {
        jdbc.sql("""
                INSERT INTO gas.worker_presence (worker_id, started_at, last_seen_at, app_version)
                VALUES (:id, :started, :now, :version)
                ON CONFLICT (worker_id) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
                """)
                .param("id", workerId).param("started", Rows.param(startedAt))
                .param("now", Rows.param(now)).param("version", appVersion)
                .update();
    }

    public void remove(UUID workerId) {
        jdbc.sql("DELETE FROM gas.worker_presence WHERE worker_id = :id").param("id", workerId).update();
    }

    public List<Presence> findAll() {
        return jdbc.sql("SELECT worker_id, started_at, last_seen_at, app_version FROM gas.worker_presence ORDER BY started_at")
                .query((rs, n) -> new Presence(Rows.uuid(rs, "worker_id"), Rows.instant(rs, "started_at"),
                        Rows.instant(rs, "last_seen_at"), rs.getString("app_version")))
                .list();
    }
}
