package org.genealogyarchive.data;

import org.genealogyarchive.records.GroupingRun;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** grouping_run and grouping_run_group (V0022): the durable identity and outcome of each grouping run. */
public final class GroupingRunRepository {

    private static final String COLUMNS = """
            run_id, capture_session_id, state, started_at, started_by, finished_at, rows_planned, rows_applied,
            rows_ungrouped, rows_conflicted, records_created, records_extended""";

    private static final RowMapper<GroupingRun> RUN = (rs, n) -> new GroupingRun(Rows.uuid(rs, "run_id"),
            Rows.uuid(rs, "capture_session_id"), GroupingRun.State.valueOf(rs.getString("state")),
            Rows.instant(rs, "started_at"), Rows.uuid(rs, "started_by"), Rows.instant(rs, "finished_at"),
            rs.getInt("rows_planned"), rs.getInt("rows_applied"), rs.getInt("rows_ungrouped"),
            rs.getInt("rows_conflicted"), rs.getInt("records_created"), rs.getInt("records_extended"));

    private final JdbcClient jdbc;

    public GroupingRunRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void start(UUID runId, UUID sessionId, int rowsPlanned, Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.grouping_run (run_id, capture_session_id, rows_planned, started_at, started_by)
                VALUES (:id, :s, :planned, :at, :by)
                """).param("id", runId).param("s", sessionId).param("planned", rowsPlanned)
                .param("at", Rows.param(at)).param("by", by).update();
    }

    public Optional<GroupingRun> find(UUID runId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.grouping_run WHERE run_id = :id").param("id", runId)
                .query(RUN).optional();
    }

    public List<GroupingRun> ofSession(UUID sessionId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.grouping_run WHERE capture_session_id = :s ORDER BY started_at DESC, run_id DESC LIMIT :n
                """).param("s", sessionId).param("n", limit).query(RUN).list();
    }

    /** Records one completed group, in the same transaction that applied it. */
    public void completeGroup(UUID runId, String groupKey, UUID recordId, boolean created, int rowsApplied,
                              int rowsConflicted, Instant at) {
        jdbc.sql("""
                INSERT INTO gas.grouping_run_group (run_id, group_key, record_id, created, rows_applied, completed_at)
                VALUES (:run, :k, :r, :created, :applied, :at)
                """).param("run", runId).param("k", groupKey).param("r", recordId).param("created", created)
                .param("applied", rowsApplied).param("at", Rows.param(at)).update();
        count(runId, rowsApplied, rowsConflicted, created ? 1 : 0, created ? 0 : 1);
    }

    /** Adds to a running run's counters (a group whose rows all conflicted applies nothing but is counted). */
    public void count(UUID runId, int applied, int conflicted, int created, int extended) {
        jdbc.sql("""
                UPDATE gas.grouping_run SET rows_applied = rows_applied + :a, rows_conflicted = rows_conflicted + :c,
                       records_created = records_created + :cr, records_extended = records_extended + :ex
                 WHERE run_id = :run AND state = 'RUNNING'
                """).param("a", applied).param("c", conflicted).param("cr", created).param("ex", extended)
                .param("run", runId).update();
    }

    public void finish(UUID runId, GroupingRun.State state, int rowsUngrouped, Instant at) {
        jdbc.sql("""
                UPDATE gas.grouping_run SET state = :state, rows_ungrouped = :u, finished_at = :at
                 WHERE run_id = :run AND state = 'RUNNING'
                """).param("state", state.name()).param("u", rowsUngrouped).param("at", Rows.param(at))
                .param("run", runId).update();
    }

    /**
     * Closes runs of the session left RUNNING by a process that stopped (called only while holding the
     * session's grouping lock, so no live run can be among them). Their completed groups stay as recorded.
     */
    public int closeStale(UUID sessionId, Instant at) {
        return jdbc.sql("""
                UPDATE gas.grouping_run SET state = 'INTERRUPTED', finished_at = :at
                 WHERE capture_session_id = :s AND state = 'RUNNING'
                """).param("s", sessionId).param("at", Rows.param(at)).update();
    }
}
