package org.genealogyarchive.testsupport;

import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfig.DatabaseSettings;
import org.genealogyarchive.config.InstallationConfig.LogFormat;
import org.genealogyarchive.config.InstallationConfig.LoggingSettings;
import org.genealogyarchive.config.InstallationConfig.ServerSettings;
import org.genealogyarchive.config.Secret;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A throwaway PostgreSQL database for one test class (PHASE-0: "test isolation does not depend on
 * production data").
 * <p>
 * The harness connects to a PostgreSQL server named by test-only environment variables, creates a
 * new uniquely named database {@code gas_test_<random>}, and drops it afterwards. It never reads
 * installation configuration and refuses to drop any database without the test prefix.
 * <p>
 * Required environment (integration tests FAIL, not skip, when absent):
 * <ul>
 *   <li>{@code GAS_TEST_DB_URL} - JDBC URL of an administrative database on the test server,
 *       e.g. {@code jdbc:postgresql://localhost:5432/postgres}</li>
 *   <li>{@code GAS_TEST_DB_USER} - a role allowed to CREATE DATABASE</li>
 *   <li>{@code GAS_TEST_DB_PASSWORD}</li>
 * </ul>
 */
public final class TestDatabase implements AutoCloseable {

    public static final String NAME_PREFIX = "gas_test_";
    private static final Pattern JDBC_URL = Pattern.compile("^(jdbc:postgresql://[^/]+/)([^?]*)(\\?.*)?$");

    private final String adminUrl;
    private final String user;
    private final String password;
    private final String name;
    private final String url;

    private TestDatabase(String adminUrl, String user, String password, String name) {
        this.adminUrl = adminUrl;
        this.user = user;
        this.password = password;
        this.name = name;
        this.url = databaseUrl(adminUrl, name);
    }

    /** Creates a new empty database on the test server. */
    public static TestDatabase createFresh() {
        String adminUrl = requiredEnv("GAS_TEST_DB_URL");
        String user = requiredEnv("GAS_TEST_DB_USER");
        String password = requiredEnv("GAS_TEST_DB_PASSWORD");
        String name = NAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase(Locale.ROOT);
        TestDatabase db = new TestDatabase(adminUrl, user, password, name);
        db.adminExecute("CREATE DATABASE " + name + " ENCODING 'UTF8' TEMPLATE template0");
        return db;
    }

    static String databaseUrl(String adminUrl, String databaseName) {
        Matcher m = JDBC_URL.matcher(adminUrl);
        if (!m.matches()) {
            throw new IllegalStateException("GAS_TEST_DB_URL must look like jdbc:postgresql://host:port/database");
        }
        return m.group(1) + databaseName + (m.group(3) != null ? m.group(3) : "");
    }

    /**
     * A byte-for-byte copy of this database (PostgreSQL TEMPLATE copy), as a restore onto another
     * server would produce. All connections to this database must be closed first.
     */
    public TestDatabase copy() {
        String name = NAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase(Locale.ROOT);
        TestDatabase copy = new TestDatabase(adminUrl, user, password, name);
        copy.adminExecute("CREATE DATABASE " + name + " TEMPLATE " + this.name);
        return copy;
    }

    /** Guard: only harness-created databases may ever be dropped. */
    static void requireTestDatabaseName(String databaseName) {
        if (!databaseName.startsWith(NAME_PREFIX) || !databaseName.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Refusing to drop non-test database '" + databaseName + "'");
        }
    }

    public String name() {
        return name;
    }

    public String url() {
        return url;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public DatabaseSettings settings() {
        return new DatabaseSettings(url, user, Secret.of(password), 4);
    }

    /** Settings for this database but with a different (wrong) password. */
    public DatabaseSettings settingsWithPassword(String otherPassword) {
        return new DatabaseSettings(url, user, Secret.of(otherPassword), 2);
    }

    /** Installation configuration for an ephemeral-port web process using this database. */
    public InstallationConfig installationConfig() {
        return installationConfig(settings());
    }

    public static InstallationConfig installationConfig(DatabaseSettings settings) {
        return new InstallationConfig(Path.of("test-installation.yml"), settings,
                new ServerSettings("127.0.0.1", 0),
                new LoggingSettings(LogFormat.TEXT, Optional.empty()),
                Map.of());
    }

    /** Environment map for CLI tests: the password is supplied the way installations supply it. */
    public Map<String, String> cliEnvironment() {
        return Map.of("GAS_DB_PASSWORD", password);
    }

    /** YAML installation file content pointing at this database (no password inside). */
    public String installationYaml() {
        return """
                database:
                  url: '%s'
                  username: '%s'
                server:
                  bindAddress: 127.0.0.1
                  port: 0
                logging:
                  format: text
                """.formatted(url.replace("'", "''"), user.replace("'", "''"));
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void close() {
        requireTestDatabaseName(name);
        adminExecute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
    }

    private void adminExecute(String sql) {
        try (Connection c = DriverManager.getConnection(adminUrl, user, password);
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Test database administration failed (" + sql + "): " + e.getMessage(), e);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PostgreSQL integration tests require environment variable " + name
                    + " (see README 'Running the tests'). Integration tests are never skipped silently.");
        }
        return value;
    }
}
