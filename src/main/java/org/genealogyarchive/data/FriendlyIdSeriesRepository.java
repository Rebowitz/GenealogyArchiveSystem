package org.genealogyarchive.data;

import org.genealogyarchive.identity.FriendlyId;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

/**
 * Friendly-ID series registry (V0007). Allocation draws from the object type's own PostgreSQL
 * sequence; sequence values are never handed out twice, so friendly IDs are never reused.
 */
public final class FriendlyIdSeriesRepository {

    /** A registered series. */
    public record Series(String objectType, String prefix, int minDigits, String sequenceName) {
    }

    private final JdbcClient jdbc;

    public FriendlyIdSeriesRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public List<Series> findAll() {
        return jdbc.sql("SELECT object_type, prefix, min_digits, sequence_name FROM gas.friendly_id_series ORDER BY object_type")
                .query((rs, n) -> new Series(rs.getString("object_type"), rs.getString("prefix"),
                        rs.getInt("min_digits"), rs.getString("sequence_name")))
                .list();
    }

    public Optional<Series> find(String objectType) {
        return findAll().stream().filter(s -> s.objectType().equals(objectType)).findFirst();
    }

    /**
     * Allocates the next friendly ID for {@code objectType}. Must be called in the transaction that
     * inserts the object; if that transaction rolls back the number is simply skipped (a gap).
     *
     * @throws IllegalStateException if the type has no registered series
     */
    public FriendlyId allocate(String objectType) {
        Series series = find(objectType).orElseThrow(() ->
                new IllegalStateException("No friendly identifier series is registered for " + objectType));
        long next = jdbc.sql("SELECT nextval(CAST(:seq AS regclass))")
                .param("seq", "gas." + series.sequenceName())
                .query(Long.class).single();
        return new FriendlyId(series.prefix(), next, series.minDigits());
    }

    /** Sequences that are missing or set to CYCLE (which would reuse numbers). Used by post-migration validation. */
    public List<String> unsafeSequences() {
        return jdbc.sql("""
                SELECT f.sequence_name
                  FROM gas.friendly_id_series f
                  LEFT JOIN pg_sequences s ON s.schemaname = 'gas' AND s.sequencename = f.sequence_name
                 WHERE s.sequencename IS NULL OR s.cycle
                 ORDER BY f.sequence_name
                """).query(String.class).list();
    }
}
