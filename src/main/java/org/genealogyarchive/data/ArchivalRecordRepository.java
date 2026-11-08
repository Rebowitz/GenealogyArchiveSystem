package org.genealogyarchive.data;

import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.records.ArchivalRecord;
import org.genealogyarchive.records.Citation;
import org.genealogyarchive.records.RecordFileLink;
import org.genealogyarchive.records.RecordLifecycle;
import org.genealogyarchive.records.RecordRole;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** archival_record, record_file, record_role and citation (V0022). Records and Citations are never deleted. */
public final class ArchivalRecordRepository {

    public static final String OBJECT_TYPE = "archival_record";
    public static final String PREFIX = "R";
    public static final String CITATION_TYPE = "citation";
    public static final String CITATION_PREFIX = "CT";

    private static final String RECORD_SELECT = """
            SELECT r.record_id, r.friendly_number, r.classification_code, r.summary, r.source_id, r.capture_session_id,
                   r.source_locator, r.record_date_text, r.filing_date_text, r.transcription_text, r.abstract_text,
                   r.restriction, r.lifecycle,
                   r.created_at, coalesce(u.username, 'command line') AS created_by_label, r.row_version
              FROM gas.archival_record r
              LEFT JOIN gas.app_user u ON u.user_id = r.created_by
            """;

    private static final RowMapper<ArchivalRecord> RECORD = (rs, n) -> new ArchivalRecord(Rows.uuid(rs, "record_id"),
            friendlyIdOf(rs.getLong("friendly_number")), rs.getString("classification_code"), rs.getString("summary"),
            Rows.uuid(rs, "source_id"), Rows.uuid(rs, "capture_session_id"), rs.getString("source_locator"),
            rs.getString("record_date_text"), rs.getString("filing_date_text"), rs.getString("transcription_text"),
            rs.getString("abstract_text"), Restriction.valueOf(rs.getString("restriction")), RecordLifecycle.valueOf(rs.getString("lifecycle")),
            Rows.instant(rs, "created_at"), rs.getString("created_by_label"), rs.getLong("row_version"));

    private static final String LINK_COLUMNS = "record_id, file_id, sequence_number, page_designation, note, linked_at";

    private static final RowMapper<RecordFileLink> LINK = (rs, n) -> new RecordFileLink(Rows.uuid(rs, "record_id"),
            Rows.uuid(rs, "file_id"), rs.getInt("sequence_number"), rs.getString("page_designation"), rs.getString("note"),
            Rows.instant(rs, "linked_at"));

    private static final String ROLE_COLUMNS = "role_id, record_id, role_code, party_as_written, note, sequence_number, row_version";

    private static final RowMapper<RecordRole> ROLE = (rs, n) -> new RecordRole(Rows.uuid(rs, "role_id"),
            Rows.uuid(rs, "record_id"), rs.getString("role_code"), rs.getString("party_as_written"), rs.getString("note"),
            rs.getInt("sequence_number"), rs.getLong("row_version"));

    private static final String CITATION_COLUMNS = """
            citation_id, friendly_number, source_id, record_id, locator, citation_text, note, lifecycle, created_at, row_version""";

    private static final RowMapper<Citation> CITATION = (rs, n) -> new Citation(Rows.uuid(rs, "citation_id"),
            new FriendlyId(CITATION_PREFIX, rs.getLong("friendly_number"), 5).value(), Rows.uuid(rs, "source_id"),
            Rows.uuid(rs, "record_id"), rs.getString("locator"), rs.getString("citation_text"), rs.getString("note"),
            RecordLifecycle.valueOf(rs.getString("lifecycle")), Rows.instant(rs, "created_at"), rs.getLong("row_version"));

    private final JdbcClient jdbc;

    public ArchivalRecordRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public static String friendlyIdOf(long number) {
        return new FriendlyId(PREFIX, number, 5).value();
    }

    // ---- records ---------------------------------------------------------------------------------

    /** Values of a Record's correctable fields. */
    public record RecordFields(String classificationCode, String summary, UUID sourceId, String sourceLocator,
                               String recordDateText, String filingDateText, String transcriptionText,
                               String abstractText, Restriction restriction) {
    }

    public void insertRecord(UUID id, long number, RecordFields f, UUID captureSessionId, Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.archival_record (record_id, friendly_number, classification_code, summary, source_id,
                    capture_session_id, source_locator, record_date_text, filing_date_text, transcription_text,
                    abstract_text, restriction, created_at, created_by)
                VALUES (:id, :n, :cls, :summary, :source, :session, :locator, :rdate, :fdate, :tr, :ab, :restriction, :at, :by)
                """).param("id", id).param("n", number).param("cls", f.classificationCode()).param("summary", f.summary())
                .param("source", f.sourceId()).param("session", captureSessionId).param("locator", f.sourceLocator())
                .param("rdate", f.recordDateText()).param("fdate", f.filingDateText()).param("tr", f.transcriptionText())
                .param("ab", f.abstractText()).param("restriction", f.restriction().name()).param("at", Rows.param(at))
                .param("by", by).update();
    }

    public Optional<ArchivalRecord> find(UUID id) {
        return jdbc.sql(RECORD_SELECT + " WHERE r.record_id = :id").param("id", id).query(RECORD).optional();
    }

    public Optional<ArchivalRecord> lock(UUID id) {
        return jdbc.sql(RECORD_SELECT + " WHERE r.record_id = :id FOR UPDATE OF r").param("id", id).query(RECORD).optional();
    }

    public Optional<ArchivalRecord> findByNumber(long number) {
        return jdbc.sql(RECORD_SELECT + " WHERE r.friendly_number = :n").param("n", number).query(RECORD).optional();
    }

    /** Newest first, filtered. Restricted Records only when {@code includeRestricted}. */
    public List<ArchivalRecord> list(boolean includeDeleted, boolean includeRestricted, String classification,
                                     UUID sourceId, UUID sessionId, int limit) {
        return jdbc.sql(RECORD_SELECT + """
                 WHERE (:deleted OR r.lifecycle = 'ACTIVE')
                   AND (:restricted OR r.restriction = 'NORMAL')
                   AND (CAST(:cls AS text) IS NULL OR r.classification_code = :cls)
                   AND (CAST(:source AS uuid) IS NULL OR r.source_id = :source)
                   AND (CAST(:session AS uuid) IS NULL OR r.capture_session_id = :session)
                 ORDER BY r.created_at DESC, r.friendly_number DESC LIMIT :n
                """).param("deleted", includeDeleted).param("restricted", includeRestricted).param("cls", classification)
                .param("source", sourceId).param("session", sessionId).param("n", limit).query(RECORD).list();
    }

    public boolean update(UUID id, long expectedVersion, RecordFields f) {
        return jdbc.sql("""
                UPDATE gas.archival_record SET classification_code = :cls, summary = :summary, source_id = :source,
                       source_locator = :locator, record_date_text = :rdate, filing_date_text = :fdate,
                       transcription_text = :tr, abstract_text = :ab, restriction = :restriction,
                       row_version = row_version + 1
                 WHERE record_id = :id AND row_version = :v
                """).param("cls", f.classificationCode()).param("summary", f.summary()).param("source", f.sourceId())
                .param("locator", f.sourceLocator()).param("rdate", f.recordDateText()).param("fdate", f.filingDateText())
                .param("tr", f.transcriptionText()).param("ab", f.abstractText())
                .param("restriction", f.restriction().name()).param("id", id)
                .param("v", expectedVersion).update() == 1;
    }

    public boolean updateLifecycle(UUID id, long expectedVersion, RecordLifecycle from, RecordLifecycle to, Instant at,
                                   UUID by) {
        return jdbc.sql("""
                UPDATE gas.archival_record SET lifecycle = :to, deleted_at = :at, deleted_by = :by, row_version = row_version + 1
                 WHERE record_id = :id AND row_version = :v AND lifecycle = :from
                """).param("to", to.name()).param("at", Rows.param(at)).param("by", by).param("id", id)
                .param("v", expectedVersion).param("from", from.name()).update() == 1;
    }

    /** Bumps the version of a Record whose links or roles changed (optimistic concurrency covers them too). */
    public boolean touch(UUID id, long expectedVersion) {
        return jdbc.sql("UPDATE gas.archival_record SET row_version = row_version + 1 WHERE record_id = :id AND row_version = :v")
                .param("id", id).param("v", expectedVersion).update() == 1;
    }

    /** Of the given IDs, those of Records that are restricted now. */
    public Set<UUID> restrictedAmong(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT record_id FROM gas.archival_record WHERE restriction = 'RESTRICTED' AND record_id IN (:ids)")
                .param("ids", List.copyOf(ids)).query((rs, n) -> Rows.uuid(rs, "record_id")).list());
    }

    /** Of the given friendly numbers, those of Records that exist and are not restricted. */
    public Set<Long> unrestrictedNumbers(Collection<Long> numbers) {
        if (numbers.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT friendly_number FROM gas.archival_record WHERE restriction <> 'RESTRICTED' AND friendly_number IN (:n)")
                .param("n", List.copyOf(numbers)).query(Long.class).list());
    }

    /** Of the given IDs, those of Citations that cite a restricted Record. */
    public Set<UUID> citationsOfRestrictedRecords(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                SELECT c.citation_id FROM gas.citation c JOIN gas.archival_record r ON r.record_id = c.record_id
                 WHERE r.restriction = 'RESTRICTED' AND c.citation_id IN (:ids)
                """).param("ids", List.copyOf(ids)).query((rs, n) -> Rows.uuid(rs, "citation_id")).list());
    }

    // ---- File links ------------------------------------------------------------------------------

    public List<RecordFileLink> links(UUID recordId) {
        return jdbc.sql("SELECT " + LINK_COLUMNS + " FROM gas.record_file WHERE record_id = :r ORDER BY sequence_number, linked_at")
                .param("r", recordId).query(LINK).list();
    }

    public List<RecordFileLink> linksOfFile(UUID fileId) {
        return jdbc.sql("SELECT " + LINK_COLUMNS + " FROM gas.record_file WHERE file_id = :f ORDER BY linked_at")
                .param("f", fileId).query(LINK).list();
    }

    public Optional<RecordFileLink> link(UUID recordId, UUID fileId) {
        return jdbc.sql("SELECT " + LINK_COLUMNS + " FROM gas.record_file WHERE record_id = :r AND file_id = :f")
                .param("r", recordId).param("f", fileId).query(LINK).optional();
    }

    public int maxLinkSequence(UUID recordId) {
        return jdbc.sql("SELECT coalesce(max(sequence_number), 0) FROM gas.record_file WHERE record_id = :r")
                .param("r", recordId).query(Integer.class).single();
    }

    /** @return false if the File is already linked to the Record */
    public boolean insertLink(UUID recordId, UUID fileId, int sequence, String page, String note, Instant at, UUID by) {
        return jdbc.sql("""
                INSERT INTO gas.record_file (record_id, file_id, sequence_number, page_designation, note, linked_at, linked_by)
                VALUES (:r, :f, :seq, :page, :note, :at, :by) ON CONFLICT (record_id, file_id) DO NOTHING
                """).param("r", recordId).param("f", fileId).param("seq", sequence).param("page", page).param("note", note)
                .param("at", Rows.param(at)).param("by", by).update() == 1;
    }

    public boolean updateLink(UUID recordId, UUID fileId, int sequence, String page, String note) {
        return jdbc.sql("""
                UPDATE gas.record_file SET sequence_number = :seq, page_designation = :page, note = :note
                 WHERE record_id = :r AND file_id = :f
                """).param("seq", sequence).param("page", page).param("note", note).param("r", recordId).param("f", fileId)
                .update() == 1;
    }

    public boolean removeLink(UUID recordId, UUID fileId) {
        return jdbc.sql("DELETE FROM gas.record_file WHERE record_id = :r AND file_id = :f")
                .param("r", recordId).param("f", fileId).update() == 1;
    }

    // ---- Roles -----------------------------------------------------------------------------------

    public List<RecordRole> roles(UUID recordId) {
        return jdbc.sql("SELECT " + ROLE_COLUMNS + " FROM gas.record_role WHERE record_id = :r ORDER BY sequence_number, created_at")
                .param("r", recordId).query(ROLE).list();
    }

    public Optional<RecordRole> role(UUID roleId) {
        return jdbc.sql("SELECT " + ROLE_COLUMNS + " FROM gas.record_role WHERE role_id = :id").param("id", roleId)
                .query(ROLE).optional();
    }

    public int maxRoleSequence(UUID recordId) {
        return jdbc.sql("SELECT coalesce(max(sequence_number), 0) FROM gas.record_role WHERE record_id = :r")
                .param("r", recordId).query(Integer.class).single();
    }

    public void insertRole(UUID roleId, UUID recordId, String code, String party, String note, int sequence, Instant at,
                           UUID by) {
        jdbc.sql("""
                INSERT INTO gas.record_role (role_id, record_id, role_code, party_as_written, note, sequence_number, created_at, created_by)
                VALUES (:id, :r, :code, :party, :note, :seq, :at, :by)
                """).param("id", roleId).param("r", recordId).param("code", code).param("party", party).param("note", note)
                .param("seq", sequence).param("at", Rows.param(at)).param("by", by).update();
    }

    public boolean updateRole(UUID roleId, long expectedVersion, String code, String party, String note) {
        return jdbc.sql("""
                UPDATE gas.record_role SET role_code = :code, party_as_written = :party, note = :note, row_version = row_version + 1
                 WHERE role_id = :id AND row_version = :v
                """).param("code", code).param("party", party).param("note", note).param("id", roleId)
                .param("v", expectedVersion).update() == 1;
    }

    public boolean removeRole(UUID roleId, long expectedVersion) {
        return jdbc.sql("DELETE FROM gas.record_role WHERE role_id = :id AND row_version = :v")
                .param("id", roleId).param("v", expectedVersion).update() == 1;
    }

    // ---- Citations -------------------------------------------------------------------------------

    public void insertCitation(UUID id, long number, UUID sourceId, UUID recordId, String locator, String text, String note,
                               Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.citation (citation_id, friendly_number, source_id, record_id, locator, citation_text, note,
                    created_at, created_by)
                VALUES (:id, :n, :source, :record, :locator, :text, :note, :at, :by)
                """).param("id", id).param("n", number).param("source", sourceId).param("record", recordId)
                .param("locator", locator).param("text", text).param("note", note).param("at", Rows.param(at))
                .param("by", by).update();
    }

    public Optional<Citation> citation(UUID id) {
        return jdbc.sql("SELECT " + CITATION_COLUMNS + " FROM gas.citation WHERE citation_id = :id").param("id", id)
                .query(CITATION).optional();
    }

    public List<Citation> citationsOfRecord(UUID recordId) {
        return jdbc.sql("SELECT " + CITATION_COLUMNS + " FROM gas.citation WHERE record_id = :r AND lifecycle = 'ACTIVE' ORDER BY friendly_number")
                .param("r", recordId).query(CITATION).list();
    }

    public List<Citation> citationsOfSource(UUID sourceId) {
        return jdbc.sql("SELECT " + CITATION_COLUMNS + " FROM gas.citation WHERE source_id = :s AND lifecycle = 'ACTIVE' ORDER BY friendly_number")
                .param("s", sourceId).query(CITATION).list();
    }

    public boolean updateCitation(UUID id, long expectedVersion, String locator, String text, String note,
                                  RecordLifecycle lifecycle) {
        return jdbc.sql("""
                UPDATE gas.citation SET locator = :locator, citation_text = :text, note = :note, lifecycle = :lc,
                       row_version = row_version + 1
                 WHERE citation_id = :id AND row_version = :v
                """).param("locator", locator).param("text", text).param("note", note).param("lc", lifecycle.name())
                .param("id", id).param("v", expectedVersion).update() == 1;
    }
}
