package org.genealogyarchive.data;

import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.records.CaptureRow;
import org.genealogyarchive.records.CaptureSession;
import org.genealogyarchive.records.OverrideMode;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** capture_session, file_capture_context and capture_session_group (V0021, V0022). */
public final class CaptureSessionRepository {

    public static final String OBJECT_TYPE = "capture_session";
    public static final String PREFIX = "CS";

    private static final String SESSION_COLUMNS = """
            capture_session_id, friendly_number, title, repository_id, source_id, capture_date, researcher,
            equipment_method, notes, created_at, row_version""";

    private static final RowMapper<CaptureSession> SESSION = (rs, n) -> new CaptureSession(
            Rows.uuid(rs, "capture_session_id"), new FriendlyId(PREFIX, rs.getLong("friendly_number"), 4).value(),
            rs.getString("title"), Rows.uuid(rs, "repository_id"), Rows.uuid(rs, "source_id"),
            rs.getObject("capture_date", LocalDate.class), rs.getString("researcher"), rs.getString("equipment_method"),
            rs.getString("notes"), Rows.instant(rs, "created_at"), rs.getLong("row_version"));

    private static final String ROW_COLUMNS = """
            file_id, capture_session_id, sequence_number, repository_mode, override_repository_id, source_mode,
            override_source_id, capture_date_mode, override_capture_date, researcher_mode, override_researcher,
            equipment_method_mode, override_equipment_method, start_new_record, record_group, page_designation,
            classification_code, applied_record_id, applied_run_id, row_version""";

    private static final RowMapper<CaptureRow> ROW = (rs, n) -> new CaptureRow(Rows.uuid(rs, "file_id"),
            Rows.uuid(rs, "capture_session_id"), rs.getInt("sequence_number"),
            OverrideMode.valueOf(rs.getString("repository_mode")), Rows.uuid(rs, "override_repository_id"),
            OverrideMode.valueOf(rs.getString("source_mode")), Rows.uuid(rs, "override_source_id"),
            OverrideMode.valueOf(rs.getString("capture_date_mode")), rs.getObject("override_capture_date", LocalDate.class),
            OverrideMode.valueOf(rs.getString("researcher_mode")), rs.getString("override_researcher"),
            OverrideMode.valueOf(rs.getString("equipment_method_mode")), rs.getString("override_equipment_method"),
            rs.getBoolean("start_new_record"), rs.getString("record_group"), rs.getString("page_designation"),
            rs.getString("classification_code"), Rows.uuid(rs, "applied_record_id"), Rows.uuid(rs, "applied_run_id"),
            rs.getLong("row_version"));

    private final JdbcClient jdbc;

    public CaptureSessionRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Values of a session's correctable context. */
    public record SessionFields(String title, UUID repositoryId, UUID sourceId, LocalDate captureDate, String researcher,
                                String equipmentMethod, String notes) {
    }

    public void insert(UUID id, long number, SessionFields f, Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.capture_session (capture_session_id, friendly_number, title, repository_id, source_id,
                    capture_date, researcher, equipment_method, notes, created_at, created_by)
                VALUES (:id, :n, :title, :repo, :source, :date, :researcher, :method, :notes, :at, :by)
                """).param("id", id).param("n", number).param("title", f.title()).param("repo", f.repositoryId())
                .param("source", f.sourceId()).param("date", f.captureDate()).param("researcher", f.researcher())
                .param("method", f.equipmentMethod()).param("notes", f.notes()).param("at", Rows.param(at))
                .param("by", by).update();
    }

    public Optional<CaptureSession> find(UUID id) {
        return jdbc.sql("SELECT " + SESSION_COLUMNS + " FROM gas.capture_session WHERE capture_session_id = :id")
                .param("id", id).query(SESSION).optional();
    }

    /** Locks the session row for the caller's transaction (serialises grouping and membership changes). */
    public Optional<CaptureSession> lock(UUID id) {
        return jdbc.sql("SELECT " + SESSION_COLUMNS + " FROM gas.capture_session WHERE capture_session_id = :id FOR UPDATE")
                .param("id", id).query(SESSION).optional();
    }

    public Optional<CaptureSession> findByNumber(long number) {
        return jdbc.sql("SELECT " + SESSION_COLUMNS + " FROM gas.capture_session WHERE friendly_number = :n")
                .param("n", number).query(SESSION).optional();
    }

    public List<CaptureSession> list(UUID sourceId, int limit) {
        return jdbc.sql("SELECT " + SESSION_COLUMNS + """
                 FROM gas.capture_session WHERE (CAST(:source AS uuid) IS NULL OR source_id = :source)
                 ORDER BY created_at DESC, friendly_number DESC LIMIT :n
                """).param("source", sourceId).param("n", limit).query(SESSION).list();
    }

    public boolean update(UUID id, long expectedVersion, SessionFields f) {
        return jdbc.sql("""
                UPDATE gas.capture_session SET title = :title, repository_id = :repo, source_id = :source,
                       capture_date = :date, researcher = :researcher, equipment_method = :method, notes = :notes,
                       row_version = row_version + 1
                 WHERE capture_session_id = :id AND row_version = :v
                """).param("title", f.title()).param("repo", f.repositoryId()).param("source", f.sourceId())
                .param("date", f.captureDate()).param("researcher", f.researcher()).param("method", f.equipmentMethod())
                .param("notes", f.notes()).param("id", id).param("v", expectedVersion).update() == 1;
    }

    // ---- membership ------------------------------------------------------------------------------

    public Optional<CaptureRow> row(UUID fileId) {
        return jdbc.sql("SELECT " + ROW_COLUMNS + " FROM gas.file_capture_context WHERE file_id = :f").param("f", fileId)
                .query(ROW).optional();
    }

    public Optional<CaptureRow> lockRow(UUID fileId) {
        return jdbc.sql("SELECT " + ROW_COLUMNS + " FROM gas.file_capture_context WHERE file_id = :f FOR UPDATE")
                .param("f", fileId).query(ROW).optional();
    }

    /** Every row of the session, in sequence order. */
    public List<CaptureRow> rows(UUID sessionId) {
        return jdbc.sql("SELECT " + ROW_COLUMNS + " FROM gas.file_capture_context WHERE capture_session_id = :s ORDER BY sequence_number")
                .param("s", sessionId).query(ROW).list();
    }

    public int maxSequence(UUID sessionId) {
        return jdbc.sql("SELECT coalesce(max(sequence_number), 0) FROM gas.file_capture_context WHERE capture_session_id = :s")
                .param("s", sessionId).query(Integer.class).single();
    }

    /** @return false if the File already belongs to a session */
    public boolean addRow(UUID fileId, UUID sessionId, int sequence, Instant at, UUID by) {
        return jdbc.sql("""
                INSERT INTO gas.file_capture_context (file_id, capture_session_id, sequence_number, added_at, added_by)
                VALUES (:f, :s, :seq, :at, :by) ON CONFLICT (file_id) DO NOTHING
                """).param("f", fileId).param("s", sessionId).param("seq", sequence).param("at", Rows.param(at))
                .param("by", by).update() == 1;
    }

    public boolean removeRow(UUID fileId, long expectedVersion) {
        return jdbc.sql("DELETE FROM gas.file_capture_context WHERE file_id = :f AND row_version = :v")
                .param("f", fileId).param("v", expectedVersion).update() == 1;
    }

    /** One field's override: a mode, and a value present exactly in VALUE mode (plan section 10.2). */
    public record Override<T>(OverrideMode mode, T value) {
        public Override {
            if ((mode == OverrideMode.VALUE) != (value != null)) {
                throw new IllegalArgumentException("An override has a value exactly when its mode is VALUE");
            }
        }

        public static <T> Override<T> inherit() {
            return new Override<>(OverrideMode.INHERIT, null);
        }

        public static <T> Override<T> blank() {
            return new Override<>(OverrideMode.BLANK, null);
        }

        public static <T> Override<T> of(T value) {
            return new Override<>(OverrideMode.VALUE, value);
        }
    }

    /** All of one File's overrides. */
    public record Overrides(Override<UUID> repository, Override<UUID> source, Override<LocalDate> captureDate,
                            Override<String> researcher, Override<String> equipmentMethod) {

        public static Overrides of(CaptureRow r) {
            return new Overrides(new Override<>(r.repositoryMode(), r.overrideRepositoryId()),
                    new Override<>(r.sourceMode(), r.overrideSourceId()),
                    new Override<>(r.captureDateMode(), r.overrideCaptureDate()),
                    new Override<>(r.researcherMode(), r.overrideResearcher()),
                    new Override<>(r.equipmentMethodMode(), r.overrideEquipmentMethod()));
        }
    }

    public boolean updateOverrides(UUID fileId, long expectedVersion, Overrides o) {
        return jdbc.sql("""
                UPDATE gas.file_capture_context SET repository_mode = :repoMode, override_repository_id = :repo,
                       source_mode = :sourceMode, override_source_id = :source,
                       capture_date_mode = :dateMode, override_capture_date = :date,
                       researcher_mode = :researcherMode, override_researcher = :researcher,
                       equipment_method_mode = :methodMode, override_equipment_method = :method,
                       row_version = row_version + 1
                 WHERE file_id = :f AND row_version = :v
                """).param("repoMode", o.repository().mode().name()).param("repo", o.repository().value())
                .param("sourceMode", o.source().mode().name()).param("source", o.source().value())
                .param("dateMode", o.captureDate().mode().name()).param("date", o.captureDate().value())
                .param("researcherMode", o.researcher().mode().name()).param("researcher", o.researcher().value())
                .param("methodMode", o.equipmentMethod().mode().name()).param("method", o.equipmentMethod().value())
                .param("f", fileId).param("v", expectedVersion).update() == 1;
    }

    /** Worksheet marks of one row (D3-02). */
    public record Marks(boolean startNewRecord, String recordGroup, String pageDesignation, String classificationCode) {
    }

    public boolean updateMarks(UUID fileId, long expectedVersion, Marks m) {
        return jdbc.sql("""
                UPDATE gas.file_capture_context SET start_new_record = :start, record_group = :grp,
                       page_designation = :page, classification_code = :cls, row_version = row_version + 1
                 WHERE file_id = :f AND row_version = :v AND applied_record_id IS NULL
                """).param("start", m.startNewRecord()).param("grp", m.recordGroup()).param("page", m.pageDesignation())
                .param("cls", m.classificationCode()).param("f", fileId).param("v", expectedVersion).update() == 1;
    }

    /**
     * Marks a row applied by a grouping run, only once and only if the row is still the version that was planned
     * (a row edited after planning is a conflict and is left for the next run).
     */
    public boolean markApplied(UUID fileId, long plannedVersion, UUID recordId, UUID runId) {
        return jdbc.sql("""
                UPDATE gas.file_capture_context SET applied_record_id = :r, applied_run_id = :run,
                       row_version = row_version + 1
                 WHERE file_id = :f AND applied_record_id IS NULL AND row_version = :v
                """).param("r", recordId).param("run", runId).param("f", fileId).param("v", plannedVersion)
                .update() == 1;
    }

    /** Files ingested in an upload batch, in ingestion order (friendly number). */
    public List<UUID> filesOfBatch(UUID batchId) {
        return jdbc.sql("SELECT file_id FROM gas.archival_file WHERE batch_id = :b ORDER BY friendly_number")
                .param("b", batchId).query((rs, n) -> Rows.uuid(rs, "file_id")).list();
    }

    /** A recent upload batch (no File counts: they could reveal restricted Files). */
    public record BatchSummary(UUID batchId, Instant createdAt, String createdByLabel, String note) {
    }

    public List<BatchSummary> recentBatches(int limit) {
        return jdbc.sql("""
                SELECT b.batch_id, b.created_at, u.username, b.note
                  FROM gas.file_upload_batch b JOIN gas.app_user u ON u.user_id = b.created_by
                 ORDER BY b.created_at DESC LIMIT :n
                """).param("n", limit).query((rs, n) -> new BatchSummary(Rows.uuid(rs, "batch_id"),
                        Rows.instant(rs, "created_at"), rs.getString("username"), rs.getString("note"))).list();
    }

    // ---- explicit groups -------------------------------------------------------------------------

    public Optional<UUID> groupRecord(UUID sessionId, String key) {
        return jdbc.sql("SELECT record_id FROM gas.capture_session_group WHERE capture_session_id = :s AND group_key = :k")
                .param("s", sessionId).param("k", key).query((rs, n) -> Rows.uuid(rs, "record_id")).optional();
    }

    public void insertGroup(UUID sessionId, String key, UUID recordId, Instant at) {
        jdbc.sql("""
                INSERT INTO gas.capture_session_group (capture_session_id, group_key, record_id, created_at)
                VALUES (:s, :k, :r, :at)
                """).param("s", sessionId).param("k", key).param("r", recordId).param("at", Rows.param(at)).update();
    }
}
