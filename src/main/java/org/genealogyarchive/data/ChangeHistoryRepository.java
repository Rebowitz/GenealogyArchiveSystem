package org.genealogyarchive.data;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** change_history rows (V0004). Append-only. */
public final class ChangeHistoryRepository {

    private static final String SELECT = """
            SELECT h.change_id, h.occurred_at, h.actor_kind, h.actor_user_id, h.actor_label,
                   h.object_type, h.object_id, h.change_kind, h.reason,
                   f.ord, f.item ->> 'field' AS field, f.item ->> 'class' AS field_class,
                   f.item ->> 'before' AS before_value, f.item ->> 'after' AS after_value
              FROM gas.change_history h
              LEFT JOIN LATERAL jsonb_array_elements(h.field_changes) WITH ORDINALITY AS f(item, ord) ON true
            """;

    private final JdbcClient jdbc;

    public ChangeHistoryRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void insert(ChangeRecord record, String fieldChangesJson) {
        jdbc.sql("""
                INSERT INTO gas.change_history (change_id, occurred_at, actor_kind, actor_user_id, actor_label,
                                                object_type, object_id, change_kind, field_changes, reason)
                VALUES (:id, :at, :kind, :userId, :label, :type, :objectId, :changeKind, CAST(:fields AS jsonb), :reason)
                """)
                .param("id", record.changeId()).param("at", Rows.param(record.occurredAt()))
                .param("kind", record.actorKind().name()).param("userId", record.actorUserId())
                .param("label", record.actorLabel()).param("type", record.objectType())
                .param("objectId", record.objectId()).param("changeKind", record.changeKind())
                .param("fields", fieldChangesJson).param("reason", record.reason())
                .update();
    }

    public List<ChangeRecord> forObject(String objectType, UUID objectId) {
        return group(jdbc.sql(SELECT + " WHERE h.object_type = :type AND h.object_id = :id ORDER BY h.occurred_at, h.change_id, f.ord")
                .param("type", objectType).param("id", objectId)
                .query((rs, n) -> row(rs)).list());
    }

    public List<ChangeRecord> recent(int limit) {
        return group(jdbc.sql(SELECT + """
                 WHERE h.change_id IN (SELECT change_id FROM gas.change_history ORDER BY occurred_at DESC, change_id DESC LIMIT :limit)
                 ORDER BY h.occurred_at DESC, h.change_id DESC, f.ord
                """).param("limit", limit).query((rs, n) -> row(rs)).list());
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM gas.change_history").query(Long.class).single();
    }

    private record Row(ChangeRecord header, FieldChange field) {
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        String userId = rs.getString("actor_user_id");
        ChangeRecord header = new ChangeRecord(Rows.uuid(rs, "change_id"), Rows.instant(rs, "occurred_at"),
                Actor.Kind.valueOf(rs.getString("actor_kind")), userId == null ? null : UUID.fromString(userId),
                rs.getString("actor_label"), rs.getString("object_type"), Rows.uuid(rs, "object_id"),
                rs.getString("change_kind"), List.of(), rs.getString("reason"));
        String field = rs.getString("field");
        FieldChange change = field == null ? null : new FieldChange(field, FieldClass.valueOf(rs.getString("field_class")),
                rs.getString("before_value"), rs.getString("after_value"));
        return new Row(header, change);
    }

    private static List<ChangeRecord> group(List<Row> rows) {
        Map<UUID, ChangeRecord> headers = new LinkedHashMap<>();
        Map<UUID, List<FieldChange>> fields = new LinkedHashMap<>();
        for (Row r : rows) {
            headers.putIfAbsent(r.header().changeId(), r.header());
            List<FieldChange> list = fields.computeIfAbsent(r.header().changeId(), k -> new ArrayList<>());
            if (r.field() != null) {
                list.add(r.field());
            }
        }
        List<ChangeRecord> result = new ArrayList<>();
        headers.forEach((id, h) -> result.add(new ChangeRecord(h.changeId(), h.occurredAt(), h.actorKind(),
                h.actorUserId(), h.actorLabel(), h.objectType(), h.objectId(), h.changeKind(), fields.get(id), h.reason())));
        return result;
    }
}
