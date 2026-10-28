package org.genealogyarchive.data;

import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;

/**
 * Reads the technical marker that identifies a database as belonging to the Genealogy Archive
 * System (created by migration V0001). Used to fail visibly if the application is pointed at the
 * wrong database. This is NOT the Archive ID (Phase 1).
 */
public final class SchemaIdentityRepository {

    public static final String EXPECTED_APPLICATION = "genealogy-archive-system";

    private static final String SELECT_APPLICATIONS = """
            SELECT application FROM gas.schema_identity
            """;

    private final JdbcClient jdbc;

    public SchemaIdentityRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Every application marker row (exactly one is expected). */
    public List<String> applicationMarkers() {
        return jdbc.sql(SELECT_APPLICATIONS).query(String.class).list();
    }
}
