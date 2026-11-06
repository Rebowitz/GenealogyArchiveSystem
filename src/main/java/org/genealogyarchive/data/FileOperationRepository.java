package org.genealogyarchive.data;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.preservation.BlockedReason;
import org.genealogyarchive.preservation.FileOperation;
import org.genealogyarchive.preservation.OperationEvent;
import org.genealogyarchive.preservation.UploadBatch;
import org.genealogyarchive.preservation.OperationKind;
import org.genealogyarchive.preservation.OperationState;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.preservation.Sha256;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * file_operation, file_operation_event and file_upload_batch (V0009). Every state change is a
 * compare-and-set ({@code WHERE state = :from}) written together with its history event, so two
 * actors can never both advance the same operation (Phase 2 plan section 3).
 */
public final class FileOperationRepository {

    private static final String COLUMNS = """
            operation_id, kind, state, actor_label, actor_user_id, volume_id, batch_id, item_key, staging_name,
            content_id, target_generation, target_path, file_id, original_filename, client_last_modified,
            source_note, requested_restriction, parent_file_id, derivation_method, expected_size, expected_sha256,
            received_bytes, last_progress_at, outcome, blocked_reason, message, quarantine_path, staging_cleaned,
            finding_id, attempts, created_at, updated_at, completed_at, target_created, receiver_alive_at""";

    /** Columns a transition may set, with the SQL cast each needs. */
    private static final Map<String, String> SETTABLE = Map.ofEntries(
            Map.entry("volume_id", "CAST(:volume_id AS uuid)"),
            Map.entry("content_id", "CAST(:content_id AS uuid)"),
            Map.entry("target_generation", ":target_generation"),
            Map.entry("target_path", ":target_path"),
            Map.entry("file_id", "CAST(:file_id AS uuid)"),
            Map.entry("expected_size", ":expected_size"),
            Map.entry("expected_sha256", ":expected_sha256"),
            Map.entry("received_bytes", ":received_bytes"),
            Map.entry("outcome", ":outcome"),
            Map.entry("blocked_reason", ":blocked_reason"),
            Map.entry("message", ":message"),
            Map.entry("quarantine_path", ":quarantine_path"),
            Map.entry("staging_cleaned", ":staging_cleaned"),
            Map.entry("target_created", ":target_created"),
            Map.entry("finding_id", "CAST(:finding_id AS uuid)"));

    private static final RowMapper<FileOperation> OPERATION = (rs, n) -> {
        byte[] sha = rs.getBytes("expected_sha256");
        String restriction = rs.getString("requested_restriction");
        String blocked = rs.getString("blocked_reason");
        Integer generation = (Integer) rs.getObject("target_generation");
        Long expectedSize = (Long) rs.getObject("expected_size");
        return new FileOperation(Rows.uuid(rs, "operation_id"), OperationKind.valueOf(rs.getString("kind")),
                OperationState.valueOf(rs.getString("state")), rs.getString("actor_label"),
                Rows.uuid(rs, "actor_user_id"), Rows.uuid(rs, "volume_id"), Rows.uuid(rs, "batch_id"),
                rs.getString("item_key"), rs.getString("staging_name"), Rows.uuid(rs, "content_id"), generation,
                rs.getString("target_path"), Rows.uuid(rs, "file_id"), rs.getString("original_filename"),
                Rows.instant(rs, "client_last_modified"), rs.getString("source_note"),
                restriction == null ? null : Restriction.valueOf(restriction), Rows.uuid(rs, "parent_file_id"),
                rs.getString("derivation_method"), expectedSize, sha == null ? null : Sha256.hex(sha),
                rs.getLong("received_bytes"), Rows.instant(rs, "last_progress_at"), rs.getString("outcome"),
                blocked == null ? null : BlockedReason.valueOf(blocked), rs.getString("message"),
                rs.getString("quarantine_path"), rs.getBoolean("staging_cleaned"), Rows.uuid(rs, "finding_id"),
                rs.getInt("attempts"), Rows.instant(rs, "created_at"), Rows.instant(rs, "updated_at"),
                Rows.instant(rs, "completed_at"), rs.getBoolean("target_created"), Rows.instant(rs, "receiver_alive_at"));
    };

    /** Values for a new operation row. Unused fields are null. */
    public record NewOperation(UUID operationId, OperationKind kind, OperationState state, Actor actor, UUID volumeId,
                               UUID batchId, String itemKey, String stagingName, UUID fileId, UUID contentId,
                               UUID findingId, String originalFilename, Instant clientLastModified,
                               String sourceNote, Restriction requestedRestriction, UUID parentFileId,
                               String derivationMethod) {
    }

    /** A batch row. */
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public FileOperationRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
        this.tx = Transactions.template(dataSource);
    }

    public void insertBatch(UUID batchId, UUID createdBy, UUID volumeId, String note) {
        jdbc.sql("INSERT INTO gas.file_upload_batch (batch_id, created_by, volume_id, note) VALUES (:id, :by, :vol, :note)")
                .param("id", batchId).param("by", createdBy).param("vol", volumeId).param("note", note).update();
    }

    public Optional<UploadBatch> findBatch(UUID batchId) {
        return jdbc.sql("SELECT batch_id, created_by, volume_id, created_at, note FROM gas.file_upload_batch WHERE batch_id = :id")
                .param("id", batchId)
                .query((rs, n) -> new UploadBatch(Rows.uuid(rs, "batch_id"), Rows.uuid(rs, "created_by"),
                        Rows.uuid(rs, "volume_id"), Rows.instant(rs, "created_at"), rs.getString("note")))
                .optional();
    }

    /** Inserts a new operation and its first history event. */
    public void insert(NewOperation op, String detail) {
        tx.executeWithoutResult(s -> {
            Actor a = op.actor();
            jdbc.sql("""
                    INSERT INTO gas.file_operation (operation_id, kind, state, actor_kind, actor_user_id, actor_label,
                        volume_id, batch_id, item_key, staging_name, file_id, content_id, finding_id, original_filename,
                        client_last_modified, source_note, requested_restriction, parent_file_id, derivation_method,
                        last_progress_at)
                    VALUES (:id, :kind, :state, :akind, :auser, :alabel, :vol, :batch, :item, :staging, :file, :content,
                        :finding, :fname, :modified, :note, :restriction, :parent, :method, clock_timestamp())
                    """)
                    .param("id", op.operationId()).param("kind", op.kind().name()).param("state", op.state().name())
                    .param("akind", a.kind().name()).param("auser", a.userId()).param("alabel", a.label())
                    .param("vol", op.volumeId()).param("batch", op.batchId()).param("item", op.itemKey())
                    .param("staging", op.stagingName()).param("file", op.fileId()).param("content", op.contentId())
                    .param("finding", op.findingId()).param("fname", op.originalFilename())
                    .param("modified", Rows.param(op.clientLastModified())).param("note", op.sourceNote())
                    .param("restriction", op.requestedRestriction() == null ? null : op.requestedRestriction().name())
                    .param("parent", op.parentFileId()).param("method", op.derivationMethod())
                    .update();
            event(op.operationId(), null, op.state(), detail);
        });
    }

    public Optional<FileOperation> find(UUID operationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.file_operation WHERE operation_id = :id")
                .param("id", operationId).query(OPERATION).optional();
    }

    public Optional<FileOperation> findByBatchItem(UUID batchId, String itemKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.file_operation WHERE batch_id = :b AND item_key = :k")
                .param("b", batchId).param("k", itemKey).query(OPERATION).optional();
    }

    public List<FileOperation> byBatch(UUID batchId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.file_operation WHERE batch_id = :b ORDER BY created_at, operation_id")
                .param("b", batchId).query(OPERATION).list();
    }

    /** Operations that are not final, or final but with staged bytes still to clean up. Oldest first. */
    public List<FileOperation> needingAttention() {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.file_operation
                WHERE state NOT IN ('COMPLETED', 'ABANDONED', 'CANCELLED', 'REJECTED')
                   OR (staging_name IS NOT NULL AND NOT staging_cleaned)
                ORDER BY created_at, operation_id
                """).query(OPERATION).list();
    }

    public List<FileOperation> recent(int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.file_operation ORDER BY created_at DESC, operation_id DESC LIMIT :n")
                .param("n", limit).query(OPERATION).list();
    }

    public List<FileOperation> forFile(UUID fileId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.file_operation WHERE file_id = :f ORDER BY created_at, operation_id")
                .param("f", fileId).query(OPERATION).list();
    }

    /**
     * Every ingest or repair operation that targets {@code contentId} (GAS-P2-R01): destruction must account
     * for copies such operations created but never committed as content generations.
     */
    public List<FileOperation> writersOfContent(UUID contentId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.file_operation
                WHERE content_id = :c AND kind <> 'DESTROY'
                ORDER BY created_at, operation_id
                """).param("c", contentId).query(OPERATION).list();
    }

    public Map<OperationState, Long> countsByState() {
        Map<OperationState, Long> counts = new LinkedHashMap<>();
        jdbc.sql("SELECT state, count(*) AS n FROM gas.file_operation GROUP BY state")
                .query((rs, n) -> Map.entry(OperationState.valueOf(rs.getString("state")), rs.getLong("n")))
                .list().forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    /** One history event of an operation. */
    public List<OperationEvent> events(UUID operationId) {
        return jdbc.sql("SELECT seq, occurred_at, from_state, to_state, detail FROM gas.file_operation_event WHERE operation_id = :id ORDER BY seq")
                .param("id", operationId)
                .query((rs, n) -> new OperationEvent(rs.getInt("seq"), Rows.instant(rs, "occurred_at"), rs.getString("from_state"),
                        rs.getString("to_state"), rs.getString("detail")))
                .list();
    }

    /**
     * Records upload progress. Only succeeds while the operation is still RECEIVING, so a receiver
     * learns immediately if recovery abandoned its upload.
     *
     * @return true if the operation is still RECEIVING
     */
    public boolean heartbeat(UUID operationId, long receivedBytes) {
        return jdbc.sql("""
                UPDATE gas.file_operation
                   SET received_bytes = :bytes, last_progress_at = clock_timestamp(), updated_at = clock_timestamp()
                 WHERE operation_id = :id AND state = 'RECEIVING'
                """).param("bytes", receivedBytes).param("id", operationId).update() == 1;
    }

    /**
     * Compare-and-set transition with optional column updates, recorded as a history event in the
     * same transaction.
     *
     * @param set column -> value; only columns in the settable list are accepted
     * @return true if the operation was in {@code from} and is now in {@code to}
     */
    public boolean transition(UUID operationId, OperationState from, OperationState to, String detail, Map<String, Object> set) {
        Boolean done = tx.execute(s -> {
            StringBuilder sql = new StringBuilder("UPDATE gas.file_operation SET state = :to, updated_at = clock_timestamp()");
            if (to.isFinal()) {
                sql.append(", completed_at = clock_timestamp()");
            }
            if (to != OperationState.BLOCKED && !set.containsKey("blocked_reason")) {
                sql.append(", blocked_reason = NULL");
            }
            for (String column : set.keySet()) {
                String expr = SETTABLE.get(column);
                if (expr == null) {
                    throw new IllegalArgumentException("Column not settable by a transition: " + column);
                }
                sql.append(", ").append(column).append(" = ").append(expr);
            }
            sql.append(" WHERE operation_id = :id AND state = :from");
            JdbcClient.StatementSpec spec = jdbc.sql(sql.toString())
                    .param("to", to.name()).param("from", from.name()).param("id", operationId);
            for (Map.Entry<String, Object> e : set.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Enum<?> en) {
                    v = en.name();
                } else if (v instanceof UUID u) {
                    v = u.toString();
                } else if ("expected_sha256".equals(e.getKey()) && v instanceof String hex) {
                    v = Sha256.bytes(hex);
                }
                spec = spec.param(e.getKey(), v);
            }
            if (spec.update() != 1) {
                return false;
            }
            event(operationId, from, to, detail);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    public boolean transition(UUID operationId, OperationState from, OperationState to, String detail) {
        return transition(operationId, from, to, detail, Map.of());
    }

    /** Records an event without a state change (e.g. a retry attempt or a cleanup note). */
    public void note(UUID operationId, String detail) {
        tx.executeWithoutResult(s -> {
            OperationState state = OperationState.valueOf(jdbc.sql(
                    "SELECT state FROM gas.file_operation WHERE operation_id = :id FOR UPDATE")
                    .param("id", operationId).query(String.class).single());
            event(operationId, state, state, detail);
        });
    }

    public void incrementAttempts(UUID operationId) {
        jdbc.sql("UPDATE gas.file_operation SET attempts = attempts + 1, updated_at = clock_timestamp() WHERE operation_id = :id")
                .param("id", operationId).update();
    }

    public void markStagingCleaned(UUID operationId) {
        jdbc.sql("UPDATE gas.file_operation SET staging_cleaned = true, updated_at = clock_timestamp() WHERE operation_id = :id")
                .param("id", operationId).update();
    }

    /**
     * Renews the receiver lease of uploads still being received by a live process (GAS-P2-001). Called
     * on a timer, independently of byte arrivals, so a live receiver blocked in a read or write keeps
     * its upload.
     *
     * @return how many leases were renewed
     */
    public int renewReceivers(java.util.Collection<UUID> operationIds) {
        if (operationIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                UPDATE gas.file_operation SET receiver_alive_at = clock_timestamp()
                 WHERE state = 'RECEIVING' AND operation_id = ANY (CAST(:ids AS uuid[]))
                """).param("ids", operationIds.stream().map(UUID::toString).toArray(String[]::new)).update();
    }

    /**
     * Abandons uploads whose receiver has stopped: neither its lease nor its byte progress was renewed
     * for {@code grace} (database clock). A live receiver renews its lease on a timer whether or not
     * bytes arrive, so it is never selected; a client that stops sending is ended by the receiver
     * itself (the server's read timeout), not by recovery.
     *
     * @return the abandoned operation IDs
     */
    public List<UUID> abandonStaleReceiving(Duration grace) {
        String stale = """
                 greatest(last_progress_at, coalesce(receiver_alive_at, last_progress_at))
                      < clock_timestamp() - make_interval(secs => CAST(:grace AS double precision))
                """;
        double seconds = grace.toMillis() / 1000.0;
        List<UUID> ids = jdbc.sql("SELECT operation_id FROM gas.file_operation WHERE state = 'RECEIVING' AND " + stale)
                .param("grace", seconds).query(UUID.class).list();
        return ids.stream().filter(id -> {
            Boolean ok = tx.execute(s -> {
                int n = jdbc.sql("""
                        UPDATE gas.file_operation
                           SET state = 'ABANDONED', updated_at = clock_timestamp(), completed_at = clock_timestamp(),
                               message = 'The process receiving this upload stopped; the upload was abandoned. The source file was not touched.'
                         WHERE operation_id = :id AND state = 'RECEIVING' AND""" + stale)
                        .param("id", id).param("grace", seconds).update();
                if (n == 1) {
                    event(id, OperationState.RECEIVING, OperationState.ABANDONED,
                            "receiver lease and progress not renewed within " + grace);
                }
                return n == 1;
            });
            return Boolean.TRUE.equals(ok);
        }).toList();
    }

    /**
     * Records that this operation's exclusive create of its planned target succeeded (GAS-P2-009). Only
     * an object created this way is ever treated as the operation's own uncommitted copy.
     */
    public void markTargetCreated(UUID operationId, String targetPath) {
        int n = jdbc.sql("""
                UPDATE gas.file_operation SET target_created = true, updated_at = clock_timestamp()
                 WHERE operation_id = :id AND target_path = :path
                """).param("id", operationId).param("path", targetPath).update();
        if (n != 1) {
            throw new IllegalStateException("operation " + operationId + " no longer targets " + targetPath);
        }
    }

    /** Portable target paths planned by unfinished operations on a volume (not yet content generations). */
    public Set<String> plannedTargets(UUID volumeId) {
        return Set.copyOf(jdbc.sql("""
                SELECT target_path FROM gas.file_operation
                 WHERE volume_id = :v AND target_path IS NOT NULL
                   AND state IN ('PROMOTING', 'PROMOTED', 'WAITING_FOR_VOLUME', 'BLOCKED')
                """).param("v", volumeId).query(String.class).list());
    }

    private void event(UUID operationId, OperationState from, OperationState to, String detail) {
        jdbc.sql("""
                INSERT INTO gas.file_operation_event (operation_id, seq, from_state, to_state, detail)
                SELECT :id, coalesce(max(seq), 0) + 1, :from, :to, :detail
                  FROM gas.file_operation_event WHERE operation_id = :id
                """)
                .param("id", operationId).param("from", from == null ? null : from.name()).param("to", to.name())
                .param("detail", detail == null ? null : detail.length() > 4000 ? detail.substring(0, 4000) : detail)
                .update();
    }
}
