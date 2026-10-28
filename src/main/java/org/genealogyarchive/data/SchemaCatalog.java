package org.genealogyarchive.data;

import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;

/**
 * Deterministic description of the application schema's structure (tables, columns, constraints,
 * indexes) read from the PostgreSQL catalog. Used to prove that repeated migration leaves the schema
 * unchanged and that a failed migration leaves no partial structure (PHASE-0 tests, UP-005).
 */
public final class SchemaCatalog {

    private static final String SNAPSHOT = """
            SELECT 'column ' || c.table_name || '.' || c.column_name || ' ' || c.data_type
                   || ' nullable=' || c.is_nullable || ' default=' || coalesce(c.column_default, '-') AS line
              FROM information_schema.columns c
             WHERE c.table_schema = :schema
            UNION ALL
            SELECT 'constraint ' || cl.relname || '.' || con.conname || ' ' || pg_get_constraintdef(con.oid)
              FROM pg_constraint con
              JOIN pg_class cl ON cl.oid = con.conrelid
              JOIN pg_namespace n ON n.oid = cl.relnamespace
             WHERE n.nspname = :schema
            UNION ALL
            SELECT 'index ' || indexname || ' ' || indexdef
              FROM pg_indexes
             WHERE schemaname = :schema
            UNION ALL
            SELECT 'relation ' || cl.relname || ' kind=' || cl.relkind::text
              FROM pg_class cl
              JOIN pg_namespace n ON n.oid = cl.relnamespace
             WHERE n.nspname = :schema
            ORDER BY 1
            """;

    private final JdbcClient jdbc;

    public SchemaCatalog(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Sorted structural description of {@code schema}; empty if the schema does not exist. */
    public List<String> snapshot(String schema) {
        return jdbc.sql(SNAPSHOT).param("schema", schema).query(String.class).list();
    }
}
