package org.genealogyarchive.data;

import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.preservation.ContentState;
import org.genealogyarchive.preservation.FileContent;
import org.genealogyarchive.preservation.GenerationState;
import org.genealogyarchive.preservation.Sha256;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** file_content and file_content_generation (V0010). Sizes and hashes are never updated. */
public final class FileContentRepository {

    private static final String SELECT = """
            SELECT c.content_id, c.volume_id, c.byte_size, c.sha256, c.state, c.created_at, c.last_verified_at,
                   c.row_version, g.generation, g.storage_path
              FROM gas.file_content c
              LEFT JOIN gas.file_content_generation g ON g.content_id = c.content_id AND g.state = 'CURRENT'
            """;

    private static final RowMapper<FileContent> CONTENT = (rs, n) -> {
        String path = rs.getString("storage_path");
        Integer generation = (Integer) rs.getObject("generation");
        return new FileContent(Rows.uuid(rs, "content_id"), Rows.uuid(rs, "volume_id"), rs.getLong("byte_size"),
                Sha256.hex(rs.getBytes("sha256")), ContentState.valueOf(rs.getString("state")),
                generation == null ? 0 : generation, path == null ? null : PortableRelativePath.of(path),
                Rows.instant(rs, "created_at"), Rows.instant(rs, "last_verified_at"), rs.getLong("row_version"));
    };

    /** One physical generation. */
    /**
     * @param vacated the archive recorded that the bytes left {@code storagePath} (content MISSING); an object
     *                found there later is not this generation's copy until verified (V0016)
     */
    public record Generation(UUID contentId, int generation, UUID volumeId, String storagePath, GenerationState state,
                             String quarantinePath, UUID operationId, boolean vacated) {
    }

    private final JdbcClient jdbc;

    public FileContentRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Inserts verified content with its first generation as CURRENT. Call inside the commit transaction. */
    public void insert(UUID contentId, UUID volumeId, long size, String sha256, UUID operationId, int generation,
                       PortableRelativePath path, Instant verifiedAt) {
        jdbc.sql("""
                INSERT INTO gas.file_content (content_id, volume_id, byte_size, sha256, state, created_by_operation, last_verified_at)
                VALUES (:id, :vol, :size, :sha, 'ACTIVE', :op, :verified)
                """).param("id", contentId).param("vol", volumeId).param("size", size).param("sha", Sha256.bytes(sha256))
                .param("op", operationId).param("verified", Rows.param(verifiedAt)).update();
        addGeneration(contentId, generation, volumeId, path, GenerationState.CURRENT, operationId);
    }

    public void addGeneration(UUID contentId, int generation, UUID volumeId, PortableRelativePath path,
                              GenerationState state, UUID operationId) {
        jdbc.sql("""
                INSERT INTO gas.file_content_generation (content_id, generation, volume_id, storage_path, state, operation_id)
                VALUES (:id, :gen, :vol, :path, :state, :op)
                """).param("id", contentId).param("gen", generation).param("vol", volumeId).param("path", path.value())
                .param("state", state.name()).param("op", operationId).update();
    }

    public Optional<FileContent> find(UUID contentId) {
        return jdbc.sql(SELECT + " WHERE c.content_id = :id").param("id", contentId).query(CONTENT).optional();
    }

    /** Locks the content row for the rest of the transaction (destruction, sharing, repair decisions). */
    public Optional<FileContent> lock(UUID contentId) {
        jdbc.sql("SELECT content_id FROM gas.file_content WHERE content_id = :id FOR UPDATE")
                .param("id", contentId).query(UUID.class).optional();
        return find(contentId);
    }

    /**
     * Locks the content row in share mode for the rest of the transaction (GAS-P2-IV03): held by a repair
     * while it checks the content and creates its copy, so destruction intent (which locks the row for
     * update) is ordered strictly before or after that creation.
     */
    public Optional<FileContent> lockShared(UUID contentId) {
        jdbc.sql("SELECT content_id FROM gas.file_content WHERE content_id = :id FOR SHARE")
                .param("id", contentId).query(UUID.class).optional();
        return find(contentId);
    }

    /**
     * Retires a superseded generation's storage location once the move of its bytes is settled
     * (GAS-P2-IV01): QUARANTINE_PENDING becomes QUARANTINED at {@code quarantinePath}, or MISSING when
     * the bytes were no longer there. Its storage path is then only a historical location.
     */
    public boolean retireGenerationAt(UUID contentId, String storagePath, String quarantinePath) {
        return jdbc.sql("""
                UPDATE gas.file_content_generation
                   SET state = :to, quarantine_path = :qpath, updated_at = clock_timestamp()
                 WHERE content_id = :id AND storage_path = :path AND state = 'QUARANTINE_PENDING'
                """).param("to", quarantinePath == null ? GenerationState.MISSING.name() : GenerationState.QUARANTINED.name())
                .param("qpath", quarantinePath).param("id", contentId).param("path", storagePath).update() > 0;
    }

    /** ACTIVE contents with exactly this size and SHA-256 (candidates only; bytes must still be compared). */
    public List<FileContent> duplicateCandidates(long size, String sha256) {
        return jdbc.sql(SELECT + " WHERE c.state = 'ACTIVE' AND c.byte_size = :size AND c.sha256 = :sha ORDER BY c.created_at")
                .param("size", size).param("sha", Sha256.bytes(sha256)).query(CONTENT).list();
    }

    /** @return true if the content was in {@code from} */
    public boolean setState(UUID contentId, ContentState from, ContentState to) {
        return jdbc.sql("""
                UPDATE gas.file_content SET state = :to, row_version = row_version + 1
                 WHERE content_id = :id AND state = :from
                """).param("to", to.name()).param("from", from.name()).param("id", contentId).update() == 1;
    }

    public void markVerified(UUID contentId, Instant at) {
        jdbc.sql("UPDATE gas.file_content SET last_verified_at = :at WHERE content_id = :id")
                .param("at", Rows.param(at)).param("id", contentId).update();
    }

    /** Number of Files that have not been destroyed and use this content. */
    public long liveFileCount(UUID contentId) {
        return jdbc.sql("SELECT count(*) FROM gas.archival_file WHERE content_id = :id AND lifecycle <> 'DESTROYED'")
                .param("id", contentId).query(Long.class).single();
    }

    public List<Generation> generations(UUID contentId) {
        return jdbc.sql("""
                SELECT content_id, generation, volume_id, storage_path, state, quarantine_path, operation_id, vacated
                  FROM gas.file_content_generation WHERE content_id = :id ORDER BY generation
                """).param("id", contentId).query(GENERATION).list();
    }

    public int maxGeneration(UUID contentId) {
        return jdbc.sql("SELECT coalesce(max(generation), 0) FROM gas.file_content_generation WHERE content_id = :id")
                .param("id", contentId).query(Integer.class).single();
    }

    /** @return true if the generation was in {@code from} */
    public boolean setGenerationState(UUID contentId, int generation, GenerationState from, GenerationState to,
                                      String quarantinePath) {
        return jdbc.sql("""
                UPDATE gas.file_content_generation
                   SET state = :to, quarantine_path = coalesce(:qpath, quarantine_path), updated_at = clock_timestamp()
                 WHERE content_id = :id AND generation = :gen AND state = :from
                """).param("to", to.name()).param("qpath", quarantinePath).param("id", contentId).param("gen", generation)
                .param("from", from.name()).update() == 1;
    }

    /** Records that the bytes left a generation's location (V0016), for content no longer able to say MISSING. */
    public void markVacated(UUID contentId, int generation) {
        jdbc.sql("""
                UPDATE gas.file_content_generation SET vacated = true, updated_at = clock_timestamp()
                 WHERE content_id = :id AND generation = :gen AND state = 'CURRENT' AND NOT vacated
                """).param("id", contentId).param("gen", generation).update();
    }

    /**
     * Content still ACTIVE or DAMAGED although an open MISSING finding records its current location as empty -
     * an interrupted or legacy recording that recovery reconciles before anything else (V0016, V0017).
     */
    public List<FileContent> presentButRecordedMissing() {
        return jdbc.sql(SELECT + """
                 WHERE c.state IN ('ACTIVE', 'DAMAGED')
                   AND EXISTS (SELECT 1 FROM gas.integrity_finding f
                                WHERE f.content_id = c.content_id AND f.kind = 'MISSING' AND f.status = 'OPEN'
                                  AND f.storage_path = g.storage_path)
                """).query(CONTENT).list();
    }

    /** Generations awaiting confirmation that their damaged bytes are in quarantine. */
    public List<Generation> quarantinePending() {
        return jdbc.sql("""
                SELECT content_id, generation, volume_id, storage_path, state, quarantine_path, operation_id, vacated
                  FROM gas.file_content_generation WHERE state = 'QUARANTINE_PENDING' ORDER BY created_at
                """).query(GENERATION).list();
    }

    /** Contents expected to be on disk (for integrity checks). */
    public List<FileContent> expectedOnDisk() {
        return jdbc.sql(SELECT + " WHERE c.state IN ('ACTIVE', 'MISSING', 'DAMAGED') ORDER BY c.created_at, c.content_id")
                .query(CONTENT).list();
    }

    /** Storage paths on a volume that the archive accounts for (current and quarantine-pending generations). */
    public Set<String> accountedPaths(UUID volumeId) {
        return Set.copyOf(jdbc.sql("""
                SELECT storage_path FROM gas.file_content_generation
                 WHERE volume_id = :v AND state IN ('CURRENT', 'QUARANTINE_PENDING') AND NOT vacated
                """).param("v", volumeId).query(String.class).list());
    }

    public long countByState(ContentState state) {
        return jdbc.sql("SELECT count(*) FROM gas.file_content WHERE state = :s").param("s", state.name())
                .query(Long.class).single();
    }

    private static final RowMapper<Generation> GENERATION = (rs, n) -> new Generation(Rows.uuid(rs, "content_id"),
            rs.getInt("generation"), Rows.uuid(rs, "volume_id"), rs.getString("storage_path"),
            GenerationState.valueOf(rs.getString("state")), rs.getString("quarantine_path"), Rows.uuid(rs, "operation_id"),
            rs.getBoolean("vacated"));
}
