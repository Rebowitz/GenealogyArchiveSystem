package org.genealogyarchive.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.config.InstallationConfig.DatabaseSettings;

/**
 * Builds the PostgreSQL connection pool from installation configuration. All application tables
 * live in the {@value #APPLICATION_SCHEMA} schema, never in {@code public}.
 */
public final class DataSources {

    /** PostgreSQL schema holding every Genealogy Archive System table. */
    public static final String APPLICATION_SCHEMA = "gas";

    private DataSources() {
    }

    /**
     * @param purpose short label (web, worker, migrate) shown in pg_stat_activity.application_name
     */
    public static HikariDataSource create(DatabaseSettings settings, String purpose) {
        return create(settings, purpose, settings.maxPoolSize());
    }

    public static HikariDataSource create(DatabaseSettings settings, String purpose, int poolSize) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(settings.jdbcUrl());
        hc.setUsername(settings.username());
        hc.setPassword(settings.password().reveal());
        hc.setMaximumPoolSize(poolSize);
        hc.setMinimumIdle(Math.min(1, poolSize));
        hc.setPoolName("gas-" + purpose);
        hc.setSchema(APPLICATION_SCHEMA);
        hc.setConnectionTimeout(10_000);
        // Do not block startup on the database: the web application must start and report
        // "not ready" visibly when PostgreSQL is unavailable.
        hc.setInitializationFailTimeout(-1);
        hc.addDataSourceProperty("ApplicationName", "genealogy-archive-" + purpose);
        return new HikariDataSource(hc);
    }
}
