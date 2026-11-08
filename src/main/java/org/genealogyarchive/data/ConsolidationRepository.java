package org.genealogyarchive.data;

import org.genealogyarchive.records.Consolidation;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** file_consolidation and file_consolidation_member (V0023). Permanent. */
public final class ConsolidationRepository {

    private final JdbcClient jdbc;

    public ConsolidationRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void insert(UUID id, UUID preferredFileId, List<UUID> redundant, String reason, Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.file_consolidation (consolidation_id, preferred_file_id, reason, decided_at, decided_by)
                VALUES (:id, :p, :reason, :at, :by)
                """).param("id", id).param("p", preferredFileId).param("reason", reason).param("at", Rows.param(at))
                .param("by", by).update();
        for (UUID f : redundant) {
            jdbc.sql("INSERT INTO gas.file_consolidation_member (consolidation_id, file_id) VALUES (:id, :f)")
                    .param("id", id).param("f", f).update();
        }
    }

    public Optional<Consolidation> find(UUID id) {
        return jdbc.sql("""
                SELECT c.consolidation_id, c.preferred_file_id, c.reason, c.decided_at,
                       coalesce(u.username, 'command line') AS decided_by_label
                  FROM gas.file_consolidation c LEFT JOIN gas.app_user u ON u.user_id = c.decided_by
                 WHERE c.consolidation_id = :id
                """).param("id", id).query((rs, n) -> new Consolidation(Rows.uuid(rs, "consolidation_id"),
                        Rows.uuid(rs, "preferred_file_id"), members(Rows.uuid(rs, "consolidation_id")), rs.getString("reason"),
                        Rows.instant(rs, "decided_at"), rs.getString("decided_by_label"))).optional();
    }

    public List<UUID> members(UUID id) {
        return jdbc.sql("SELECT file_id FROM gas.file_consolidation_member WHERE consolidation_id = :id ORDER BY file_id")
                .param("id", id).query((rs, n) -> Rows.uuid(rs, "file_id")).list();
    }

    /** Consolidations a File took part in (as preferred or redundant). */
    public List<UUID> involving(UUID fileId) {
        return jdbc.sql("""
                SELECT consolidation_id FROM gas.file_consolidation WHERE preferred_file_id = :f
                UNION SELECT consolidation_id FROM gas.file_consolidation_member WHERE file_id = :f
                """).param("f", fileId).query((rs, n) -> Rows.uuid(rs, "consolidation_id")).list();
    }
}
