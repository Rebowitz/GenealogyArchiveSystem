package org.genealogyarchive.data;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * quarantine_object rows (V0014): the ledger of objects file operations move into quarantine. An entry
 * is written as INTENDED and committed before the move, and confirmed afterwards, so a crash between the
 * filesystem move and the database update is always reconcilable (GAS-P2-008). Never deleted.
 */
public final class QuarantineRepository {

    /** Why an object was set aside. */
    public enum Reason { PARTIAL_COPY, UNVERIFIED_COPY, CANCELLED_COPY, DAMAGED_GENERATION }

    /** Detail of an intent recorded when nothing was at the source. */
    public static final String ABSENT_AT_INTENT = "nothing was at the source when the move was intended";

    /** Where the ledger entry stands. */
    public enum State { INTENDED, QUARANTINED, SOURCE_ABSENT, REMOVED }

    /**
     * @param sourceSize           size of the object at {@code sourcePath} when the move was intended (V0016)
     * @param sourceModifiedMicros its last-modified time then, in microseconds; with the size, the evidence
     *                             that a later object at either end is the same object
     */
    public record Entry(UUID quarantineId, UUID volumeId, UUID operationId, UUID contentId, String sourcePath,
                        String quarantinePath, Reason reason, State state, String detail, Long sourceSize,
                        Long sourceModifiedMicros) {
        public boolean hasEvidence() {
            return sourceSize != null && sourceModifiedMicros != null;
        }

        /** The intent was recorded (V0016 or later) when nothing was at the source. */
        public boolean recordedAbsent() {
            return detail != null && detail.startsWith(ABSENT_AT_INTENT);
        }
    }

    private static final String COLUMNS = """
            quarantine_id, volume_id, operation_id, content_id, source_path, quarantine_path, reason, state, detail,
            source_size, source_modified_micros""";
    private static final RowMapper<Entry> ENTRY = (rs, n) -> new Entry(Rows.uuid(rs, "quarantine_id"),
            Rows.uuid(rs, "volume_id"), Rows.uuid(rs, "operation_id"), Rows.uuid(rs, "content_id"),
            rs.getString("source_path"), rs.getString("quarantine_path"), Reason.valueOf(rs.getString("reason")),
            State.valueOf(rs.getString("state")), rs.getString("detail"), rs.getObject("source_size", Long.class),
            rs.getObject("source_modified_micros", Long.class));

    private final JdbcClient jdbc;

    public QuarantineRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Records the intent to move {@code sourcePath} to {@code quarantinePath}, or returns the existing entry. */
    public Entry intend(UUID volumeId, UUID operationId, UUID contentId, String sourcePath, String quarantinePath,
                        Reason reason, Long sourceSize, Long sourceModifiedMicros) {
        jdbc.sql("""
                INSERT INTO gas.quarantine_object (quarantine_id, volume_id, operation_id, content_id, source_path,
                                                   quarantine_path, reason, state, source_size, source_modified_micros, detail)
                VALUES (:id, :vol, :op, :content, :src, :q, :reason, 'INTENDED', :size, :mtime,
                        CASE WHEN CAST(:size AS bigint) IS NULL THEN :absent END)
                ON CONFLICT (volume_id, quarantine_path) DO NOTHING
                """).param("id", org.genealogyarchive.identity.GlobalIds.newId()).param("vol", volumeId)
                .param("op", operationId).param("content", contentId).param("src", sourcePath).param("q", quarantinePath)
                .param("reason", reason.name()).param("size", sourceSize).param("mtime", sourceModifiedMicros)
                .param("absent", ABSENT_AT_INTENT).update();
        return find(volumeId, quarantinePath).orElseThrow();
    }

    public Optional<Entry> find(UUID volumeId, String quarantinePath) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.quarantine_object WHERE volume_id = :v AND quarantine_path = :q")
                .param("v", volumeId).param("q", quarantinePath).query(ENTRY).optional();
    }

    /** @return true if the entry was in {@code from} */
    public boolean setState(UUID quarantineId, State from, State to, String detail) {
        return jdbc.sql("""
                UPDATE gas.quarantine_object SET state = :to, detail = coalesce(:detail, detail),
                       updated_at = clock_timestamp()
                 WHERE quarantine_id = :id AND state = :from
                """).param("to", to.name()).param("detail", detail).param("id", quarantineId).param("from", from.name())
                .update() == 1;
    }

    /** Entries whose move has not been confirmed (recovery reconciles them). */
    public List<Entry> intended() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.quarantine_object WHERE state = 'INTENDED' ORDER BY created_at")
                .query(ENTRY).list();
    }

    /** Every ledger entry holding (or that may hold) bytes of the content. */
    public List<Entry> forContent(UUID contentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.quarantine_object WHERE content_id = :c ORDER BY created_at")
                .param("c", contentId).query(ENTRY).list();
    }

    public List<Entry> forOperation(UUID operationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.quarantine_object WHERE operation_id = :o ORDER BY created_at")
                .param("o", operationId).query(ENTRY).list();
    }
}
