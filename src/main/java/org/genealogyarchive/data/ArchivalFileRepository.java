package org.genealogyarchive.data;

import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Derivation;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.preservation.Restriction;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** archival_file and file_derivation (V0011). Rows are never deleted; destroyed rows are tombstones. */
public final class ArchivalFileRepository {

    public static final String OBJECT_TYPE = "archival_file";
    private static final String PREFIX = "F";
    private static final int MIN_DIGITS = 5;

    private static final String SELECT = """
            SELECT f.file_id, f.friendly_number, f.content_id, f.ingest_operation_id, f.original_filename,
                   f.managed_filename, f.media_type, f.ingested_at, f.ingested_by, u.username AS ingested_by_label,
                   f.batch_id, f.client_last_modified, f.source_note, f.lifecycle, f.restriction, f.display_rotation,
                   f.deleted_at, f.destroyed_at, f.destruction_reason, f.replacement_file_id, f.row_version
              FROM gas.archival_file f
              JOIN gas.app_user u ON u.user_id = f.ingested_by
            """;

    private static final RowMapper<ArchivalFile> FILE = (rs, n) -> new ArchivalFile(
            Rows.uuid(rs, "file_id"), new FriendlyId(PREFIX, rs.getLong("friendly_number"), MIN_DIGITS).value(),
            Rows.uuid(rs, "content_id"), Rows.uuid(rs, "ingest_operation_id"), rs.getString("original_filename"),
            rs.getString("managed_filename"), rs.getString("media_type"), Rows.instant(rs, "ingested_at"),
            Rows.uuid(rs, "ingested_by"), rs.getString("ingested_by_label"), Rows.uuid(rs, "batch_id"),
            Rows.instant(rs, "client_last_modified"), rs.getString("source_note"),
            FileLifecycle.valueOf(rs.getString("lifecycle")), Restriction.valueOf(rs.getString("restriction")),
            rs.getInt("display_rotation"), Rows.instant(rs, "deleted_at"), Rows.instant(rs, "destroyed_at"),
            rs.getString("destruction_reason"), Rows.uuid(rs, "replacement_file_id"), rs.getLong("row_version"));

    /** Values of a new File (inserted in the ingestion commit transaction). */
    public record NewFile(UUID fileId, long friendlyNumber, UUID contentId, UUID ingestOperationId,
                          String originalFilename, String managedFilename, String mediaType, Instant ingestedAt,
                          UUID ingestedBy, UUID batchId, Instant clientLastModified, String sourceNote,
                          Restriction restriction) {
    }

    private final JdbcClient jdbc;

    public ArchivalFileRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public static String friendlyIdOf(long number) {
        return new FriendlyId(PREFIX, number, MIN_DIGITS).value();
    }

    public void insert(NewFile f) {
        jdbc.sql("""
                INSERT INTO gas.archival_file (file_id, friendly_number, content_id, ingest_operation_id, original_filename,
                    managed_filename, media_type, ingested_at, ingested_by, batch_id, client_last_modified, source_note,
                    restriction)
                VALUES (:id, :num, :content, :op, :orig, :managed, :media, :at, :by, :batch, :modified, :note, :restriction)
                """)
                .param("id", f.fileId()).param("num", f.friendlyNumber()).param("content", f.contentId())
                .param("op", f.ingestOperationId()).param("orig", f.originalFilename()).param("managed", f.managedFilename())
                .param("media", f.mediaType()).param("at", Rows.param(f.ingestedAt())).param("by", f.ingestedBy())
                .param("batch", f.batchId()).param("modified", Rows.param(f.clientLastModified()))
                .param("note", f.sourceNote()).param("restriction", f.restriction().name())
                .update();
    }

    public Optional<ArchivalFile> find(UUID fileId) {
        return jdbc.sql(SELECT + " WHERE f.file_id = :id").param("id", fileId).query(FILE).optional();
    }

    public Optional<ArchivalFile> findByFriendlyNumber(long number) {
        return jdbc.sql(SELECT + " WHERE f.friendly_number = :n").param("n", number).query(FILE).optional();
    }

    public Optional<ArchivalFile> findByIngestOperation(UUID operationId) {
        return jdbc.sql(SELECT + " WHERE f.ingest_operation_id = :op").param("op", operationId).query(FILE).optional();
    }

    /** Newest first. */
    public List<ArchivalFile> list(boolean includeDeleted, boolean includeRestricted, int limit) {
        return jdbc.sql(SELECT + """
                 WHERE f.lifecycle <> 'DESTROYED'
                   AND (:deleted OR f.lifecycle = 'ACTIVE')
                   AND (:restricted OR f.restriction = 'NORMAL')
                 ORDER BY f.ingested_at DESC, f.friendly_number DESC LIMIT :n
                """).param("deleted", includeDeleted).param("restricted", includeRestricted).param("n", limit)
                .query(FILE).list();
    }

    /** IDs and friendly numbers of every File that is restricted now (any lifecycle, tombstones included). */
    public java.util.Map<UUID, Long> restrictedFiles() {
        java.util.Map<UUID, Long> out = new java.util.HashMap<>();
        jdbc.sql("SELECT file_id, friendly_number FROM gas.archival_file WHERE restriction = 'RESTRICTED'")
                .query((rs, n) -> {
                    out.put(Rows.uuid(rs, "file_id"), rs.getLong("friendly_number"));
                    return null;
                }).list();
        return out;
    }

    /** Contents that a restricted File uses (their IDs refer, indirectly, to that File). */
    public java.util.Set<UUID> contentsOfRestrictedFiles() {
        return new java.util.HashSet<>(jdbc.sql(
                "SELECT DISTINCT content_id FROM gas.archival_file WHERE restriction = 'RESTRICTED'")
                .query((rs, n) -> Rows.uuid(rs, "content_id")).list());
    }

    public List<ArchivalFile> byContent(UUID contentId) {
        return jdbc.sql(SELECT + " WHERE f.content_id = :c ORDER BY f.friendly_number").param("c", contentId).query(FILE).list();
    }

    public List<ArchivalFile> byBatch(UUID batchId) {
        return jdbc.sql(SELECT + " WHERE f.batch_id = :b ORDER BY f.friendly_number").param("b", batchId).query(FILE).list();
    }

    public Optional<ArchivalFile> lock(UUID fileId) {
        jdbc.sql("SELECT file_id FROM gas.archival_file WHERE file_id = :id FOR UPDATE").param("id", fileId)
                .query(UUID.class).optional();
        return find(fileId);
    }

    /** Correctable attributes. @return true if the row had {@code expectedVersion} */
    public boolean updateAttributes(UUID fileId, long expectedVersion, String managedFilename, int displayRotation,
                                    Restriction restriction) {
        return jdbc.sql("""
                UPDATE gas.archival_file
                   SET managed_filename = :name, display_rotation = :rot, restriction = :restriction,
                       row_version = row_version + 1
                 WHERE file_id = :id AND row_version = :expected
                """).param("name", managedFilename).param("rot", displayRotation).param("restriction", restriction.name())
                .param("id", fileId).param("expected", expectedVersion).update() == 1;
    }

    /** Lifecycle change. @return true if the row had {@code expectedVersion} and the expected lifecycle */
    public boolean updateLifecycle(UUID fileId, long expectedVersion, FileLifecycle from, FileLifecycle to,
                                   Instant deletedAt, UUID deletedBy, Instant destroyedAt, UUID destroyedBy,
                                   String reason, UUID replacement) {
        return jdbc.sql("""
                UPDATE gas.archival_file
                   SET lifecycle = :to,
                       deleted_at = CASE WHEN :to = 'DESTROYED' THEN deleted_at ELSE CAST(:deletedAt AS timestamptz) END,
                       deleted_by = CASE WHEN :to = 'DESTROYED' THEN deleted_by ELSE CAST(:deletedBy AS uuid) END,
                       destroyed_at = :destroyedAt,
                       destroyed_by = :destroyedBy, destruction_reason = :reason, replacement_file_id = :replacement,
                       row_version = row_version + 1
                 WHERE file_id = :id AND row_version = :expected AND lifecycle = :from
                """).param("to", to.name()).param("deletedAt", Rows.param(deletedAt)).param("deletedBy", deletedBy == null ? null : deletedBy.toString())
                .param("destroyedAt", Rows.param(destroyedAt)).param("destroyedBy", destroyedBy).param("reason", reason)
                .param("replacement", replacement).param("id", fileId).param("expected", expectedVersion)
                .param("from", from.name()).update() == 1;
    }

    public void insertDerivation(UUID fileId, UUID parentFileId, String method) {
        jdbc.sql("INSERT INTO gas.file_derivation (file_id, parent_file_id, method) VALUES (:f, :p, :m)")
                .param("f", fileId).param("p", parentFileId).param("m", method).update();
    }

    public Optional<Derivation> derivation(UUID fileId) {
        return jdbc.sql("SELECT file_id, parent_file_id, method, created_at FROM gas.file_derivation WHERE file_id = :f")
                .param("f", fileId)
                .query((rs, n) -> new Derivation(Rows.uuid(rs, "file_id"), Rows.uuid(rs, "parent_file_id"),
                        rs.getString("method"), Rows.instant(rs, "created_at")))
                .optional();
    }

    public List<UUID> derivativesOf(UUID parentFileId) {
        return jdbc.sql("SELECT file_id FROM gas.file_derivation WHERE parent_file_id = :p ORDER BY created_at")
                .param("p", parentFileId).query(UUID.class).list();
    }

    /** True if the File or any of its ancestors (parent, grandparent, ...) is restricted now. */
    public boolean selfOrAncestorRestricted(UUID fileId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                WITH RECURSIVE up(file_id) AS (
                    SELECT CAST(:f AS uuid)
                    UNION
                    SELECT d.parent_file_id FROM gas.file_derivation d JOIN up ON d.file_id = up.file_id)
                SELECT EXISTS (SELECT 1 FROM up JOIN gas.archival_file a ON a.file_id = up.file_id
                                WHERE a.restriction = 'RESTRICTED')
                """).param("f", fileId).query(Boolean.class).single());
    }

    /** Derivatives at every level below the File (children, grandchildren, ...) that are not restricted. */
    public List<UUID> unrestrictedDescendants(UUID fileId) {
        return jdbc.sql("""
                WITH RECURSIVE down(file_id) AS (
                    SELECT d.file_id FROM gas.file_derivation d WHERE d.parent_file_id = :f
                    UNION
                    SELECT d.file_id FROM gas.file_derivation d JOIN down ON d.parent_file_id = down.file_id)
                SELECT a.file_id FROM down JOIN gas.archival_file a ON a.file_id = down.file_id
                 WHERE a.restriction = 'NORMAL' ORDER BY a.friendly_number
                """).param("f", fileId).query((rs, n) -> Rows.uuid(rs, "file_id")).list();
    }

    public long countByLifecycle(FileLifecycle lifecycle) {
        return jdbc.sql("SELECT count(*) FROM gas.archival_file WHERE lifecycle = :l").param("l", lifecycle.name())
                .query(Long.class).single();
    }
}
