package org.genealogyarchive.data;

import org.genealogyarchive.records.GroupingRun;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** grouping_run and grouping_run_group (V0022): the durable identity and progress of each grouping run. */
public final class GroupingRunRepository {

    private static final String COLUMNS = """
            run_id, capture_session_id, state, started_at, started_by, restricted_view, finished_at, rows_planned,
            rows_applied, rows_ungrouped, rows_conflicted, rows_held, groups_held, records_created, records_extended""";

    private static final RowMapper<GroupingRun> RUN = (rs, n) -> new GroupingRun(Rows.uuid(rs, "run_id"),
            Rows.uuid(rs, "capture_session_id"), GroupingRun.State.valueOf(rs.getString("state")),
            Rows.instant(rs, "started_at"), Rows.uuid(rs, "started_by"), rs.getBoolean("restricted_view"),
            Rows.instant(rs, "finished_at"), rs.getInt("rows_planned"), rs.getInt("rows_applied"),
            rs.getInt("rows_ungrouped"), rs.getInt("rows_conflicted"), rs.getInt("rows_held"), rs.getInt("groups_held"),
            rs.getInt("records_created"), rs.getInt("records_extended"));

    /** One committed chunk of a group. */
    public record Chunk(UUID runId, String groupKey, int chunkNumber, UUID recordId, boolean created, int rowsApplied,
                        boolean groupCompleted, Instant completedAt) {
    }

    private final JdbcClient jdbc;

    public GroupingRunRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void start(UUID runId, UUID sessionId, boolean restrictedView, Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.grouping_run (run_id, capture_session_id, restricted_view, started_at, started_by)
                VALUES (:id, :s, :rv, :at, :by)
                """).param("id", runId).param("s", sessionId).param("rv", restrictedView)
                .param("at", Rows.param(at)).param("by", by).update();
    }

    public Optional<GroupingRun> find(UUID runId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.grouping_run WHERE run_id = :id").param("id", runId)
                .query(RUN).optional();
    }

    /** Runs of a session, newest first; runs started with restricted view only if {@code includeRestrictedView}. */
    public List<GroupingRun> ofSession(UUID sessionId, boolean includeRestrictedView, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.grouping_run WHERE capture_session_id = :s AND (:all OR NOT restricted_view)
                 ORDER BY started_at DESC, run_id DESC LIMIT :n
                """).param("s", sessionId).param("all", includeRestrictedView).param("n", limit).query(RUN).list();
    }

    public List<Chunk> chunks(UUID runId) {
        return jdbc.sql("""
                SELECT run_id, group_key, chunk_number, record_id, created, rows_applied, group_completed, completed_at
                  FROM gas.grouping_run_group WHERE run_id = :run ORDER BY completed_at, group_key, chunk_number
                """).param("run", runId).query((rs, n) -> new Chunk(Rows.uuid(rs, "run_id"), rs.getString("group_key"),
                        rs.getInt("chunk_number"), Rows.uuid(rs, "record_id"), rs.getBoolean("created"),
                        rs.getInt("rows_applied"), rs.getBoolean("group_completed"), Rows.instant(rs, "completed_at")))
                .list();
    }

    /** Records the planned row count once planning is done. */
    public void planned(UUID runId, int rowsPlanned, int rowsUngrouped, int rowsHeld, int groupsHeld) {
        jdbc.sql("""
                UPDATE gas.grouping_run SET rows_planned = :p, rows_ungrouped = :u, rows_held = :h, groups_held = :gh
                 WHERE run_id = :run AND state = 'RUNNING'
                """).param("p", rowsPlanned).param("u", rowsUngrouped).param("h", rowsHeld).param("gh", groupsHeld)
                .param("run", runId).update();
    }

    /** Records one committed chunk and the run's counters, in the transaction that applied it. */
    public void recordChunk(UUID runId, String groupKey, int chunkNumber, UUID recordId, boolean created,
                            int rowsApplied, boolean groupCompleted, Instant at) {
        jdbc.sql("""
                INSERT INTO gas.grouping_run_group (run_id, group_key, chunk_number, record_id, created, rows_applied,
                    group_completed, completed_at)
                VALUES (:run, :k, :c, :r, :created, :applied, :done, :at)
                """).param("run", runId).param("k", groupKey).param("c", chunkNumber).param("r", recordId)
                .param("created", created).param("applied", rowsApplied).param("done", groupCompleted)
                .param("at", Rows.param(at)).update();
        jdbc.sql("""
                UPDATE gas.grouping_run SET rows_applied = rows_applied + :a,
                       records_created = records_created + :cr, records_extended = records_extended + :ex
                 WHERE run_id = :run AND state = 'RUNNING'
                """).param("a", rowsApplied).param("cr", created ? 1 : 0)
                .param("ex", !created && chunkNumber == 1 ? 1 : 0).param("run", runId).update();
    }

    public void conflicted(UUID runId, int rows) {
        jdbc.sql("UPDATE gas.grouping_run SET rows_conflicted = rows_conflicted + :c WHERE run_id = :run AND state = 'RUNNING'")
                .param("c", rows).param("run", runId).update();
    }

    public void finish(UUID runId, GroupingRun.State state, Instant at) {
        jdbc.sql("UPDATE gas.grouping_run SET state = :state, finished_at = :at WHERE run_id = :run AND state = 'RUNNING'")
                .param("state", state.name()).param("at", Rows.param(at)).param("run", runId).update();
    }

    /**
     * Closes runs of the session left RUNNING by a process that stopped (called only while holding the session's
     * grouping lock, so no live run can be among them). Their committed chunks stay as recorded.
     */
    public int closeStale(UUID sessionId, Instant at) {
        return jdbc.sql("""
                UPDATE gas.grouping_run SET state = 'INTERRUPTED', finished_at = :at
                 WHERE capture_session_id = :s AND state = 'RUNNING'
                """).param("s", sessionId).param("at", Rows.param(at)).update();
    }
}
