package org.genealogyarchive.data;

import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

/**
 * Facts about the connected PostgreSQL server, and the minimum-version rule (ADR-0003).
 */
public final class PostgresServer {

    /** Minimum supported PostgreSQL major version (ADR-0003). */
    public static final int MINIMUM_MAJOR_VERSION = 16;

    private final JdbcClient jdbc;

    public PostgresServer(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Server version as {@code server_version_num}, e.g. 160004 for 16.4. */
    public int versionNumber() {
        return jdbc.sql("SELECT current_setting('server_version_num')::int").query(Integer.class).single();
    }

    /** Human-readable server version string, e.g. "16.4". */
    public String versionText() {
        return jdbc.sql("SELECT current_setting('server_version')").query(String.class).single();
    }

    public static int majorVersion(int serverVersionNum) {
        return serverVersionNum / 10_000;
    }

    public static boolean isSupported(int serverVersionNum) {
        return majorVersion(serverVersionNum) >= MINIMUM_MAJOR_VERSION;
    }

    /**
     * @throws UnsupportedDatabaseException if the server is older than the supported baseline
     */
    public void requireSupportedVersion() {
        int version = versionNumber();
        if (!isSupported(version)) {
            throw new UnsupportedDatabaseException("PostgreSQL " + MINIMUM_MAJOR_VERSION
                    + " or newer is required; the server reports " + versionText());
        }
    }

    /** The connected server is not a supported PostgreSQL version. */
    public static final class UnsupportedDatabaseException extends RuntimeException {
        public UnsupportedDatabaseException(String message) {
            super(message);
        }
    }
}
