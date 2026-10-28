package org.genealogyarchive.cli;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.api.FlywayException;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.ConfigValidationException;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfigLoader;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.PostgresServer;
import org.genealogyarchive.migration.PostMigrationValidator;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.migration.SchemaStatus;
import org.genealogyarchive.service.SystemStatus;
import org.genealogyarchive.service.SystemStatusService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command-line front end shared by administrators and service definitions. One executable,
 * explicit commands, documented exit codes. Contains no business logic: it delegates.
 */
public final class Cli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILURE = 1;
    public static final int EXIT_CONFIG_INVALID = 2;
    public static final int EXIT_DATABASE_PROBLEM = 3;
    public static final int EXIT_MIGRATION_FAILED = 4;
    public static final int EXIT_USAGE = 64;

    static final String USAGE = """
            Usage: genealogy-archive <command> [--config=<installation.yml>]

            Commands:
              serve          Run the web application (browser entry point).
              worker         Run the background worker.
              migrate        Apply pending database schema migrations, then validate.
              status         Report database and schema status.
              check-config   Validate the installation configuration file.
              version        Print the application version.
              help           Print this help.

            The configuration file may also be given with the GAS_CONFIG environment variable.
            The database password is read from GAS_DB_PASSWORD or database.passwordFile.

            Exit codes: 0 ok, 1 failure, 2 invalid configuration, 3 database unavailable/unsupported/not ready,
                        4 migration failed or refused, 64 usage error.
            """;

    /**
     * Result of running a command.
     *
     * @param exitCode       process exit code when {@code runningProcess} is null
     * @param runningProcess context of a long-running process (serve/worker); the JVM keeps running
     */
    public record Outcome(int exitCode, ConfigurableApplicationContext runningProcess) {
        static Outcome exit(int code) {
            return new Outcome(code, null);
        }
    }

    private final Map<String, String> environment;
    private final PrintStream out;
    private final PrintStream err;

    public Cli(Map<String, String> environment, PrintStream out, PrintStream err) {
        this.environment = Map.copyOf(environment);
        this.out = out;
        this.err = err;
    }

    public Outcome run(String[] args) {
        ParsedArgs parsed;
        try {
            parsed = ParsedArgs.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.print(USAGE);
            return Outcome.exit(EXIT_USAGE);
        }
        try {
            return switch (parsed.command()) {
                case "help" -> {
                    out.print(USAGE);
                    yield Outcome.exit(EXIT_OK);
                }
                case "version" -> {
                    out.println("Genealogy Archive System " + SystemStatusService.applicationVersion());
                    yield Outcome.exit(EXIT_OK);
                }
                case "check-config" -> checkConfig(loadConfig(parsed));
                case "migrate" -> migrate(loadConfig(parsed));
                case "status" -> status(loadConfig(parsed));
                case "serve" -> new Outcome(EXIT_OK, ProcessLauncher.startWeb(loadConfig(parsed), Map.of()));
                case "worker" -> new Outcome(EXIT_OK, ProcessLauncher.startWorker(loadConfig(parsed), Map.of()));
                default -> {
                    err.println("Unknown command: " + parsed.command());
                    err.println();
                    err.print(USAGE);
                    yield Outcome.exit(EXIT_USAGE);
                }
            };
        } catch (ConfigValidationException e) {
            err.println(e.getMessage());
            return Outcome.exit(EXIT_CONFIG_INVALID);
        } catch (RuntimeException e) {
            err.println("FAILED: " + e.getMessage());
            return Outcome.exit(EXIT_FAILURE);
        }
    }

    private InstallationConfig loadConfig(ParsedArgs parsed) {
        String location = parsed.configPath() != null ? parsed.configPath()
                : environment.get(InstallationConfigLoader.CONFIG_FILE_ENV);
        if (location == null || location.isBlank()) {
            throw new ConfigValidationException(List.of("No installation configuration file given: use --config=<path> or set "
                    + InstallationConfigLoader.CONFIG_FILE_ENV + "."));
        }
        return InstallationConfigLoader.load(Path.of(location), environment);
    }

    private Outcome checkConfig(InstallationConfig config) {
        out.println("Installation configuration is valid: " + config.sourceFile());
        out.println("  database.url      " + config.database().jdbcUrl());
        out.println("  database.username " + config.database().username());
        out.println("  database.password " + config.database().password()); // masked by Secret.toString()
        out.println("  server            " + config.server().bindAddress() + ":" + config.server().port());
        out.println("  logging           " + config.logging().format()
                + config.logging().directory().map(d -> " -> " + d).orElse(" -> console only"));
        if (config.storageRoots().isEmpty()) {
            out.println("  storageRoots      (none configured)");
        }
        for (Map.Entry<UUID, Path> e : config.storageRoots().entrySet()) {
            out.println("  storageRoots      " + e.getKey() + " -> " + e.getValue());
        }
        return Outcome.exit(EXIT_OK);
    }

    private Outcome migrate(InstallationConfig config) {
        // Flyway may use a second connection for its lock/history handling.
        try (HikariDataSource ds = DataSources.create(config.database(), "migrate", 2)) {
            new PostgresServer(ds).requireSupportedVersion();
            SchemaMigrator migrator = new SchemaMigrator(ds);
            SchemaStatus before = migrator.status();
            out.println("Schema before: " + before.state()
                    + (before.currentVersion() != null ? " (version " + before.currentVersion() + ")" : ""));
            SchemaMigrator.MigrationReport report = migrator.migrate();
            out.println("Applied " + report.migrationsApplied() + " migration(s); schema version now " + report.toVersion());
            List<String> problems = new PostMigrationValidator(ds, migrator).validate();
            if (!problems.isEmpty()) {
                err.println("Post-migration validation FAILED:");
                problems.forEach(p -> err.println("  - " + p));
                return Outcome.exit(EXIT_MIGRATION_FAILED);
            }
            out.println("Post-migration validation passed.");
            return Outcome.exit(EXIT_OK);
        } catch (PostgresServer.UnsupportedDatabaseException | DataAccessException e) {
            err.println("Database problem: " + e.getMessage());
            return Outcome.exit(EXIT_DATABASE_PROBLEM);
        } catch (SchemaMigrator.MigrationRefusedException | FlywayException e) {
            err.println("Migration FAILED: " + e.getMessage());
            err.println("The schema was left at its last successfully applied version. "
                    + "If a consequential upgrade cannot be completed, restore the pre-upgrade backup.");
            return Outcome.exit(EXIT_MIGRATION_FAILED);
        }
    }

    private Outcome status(InstallationConfig config) {
        try (HikariDataSource ds = DataSources.create(config.database(), "status", 2)) {
            SystemStatus s = new SystemStatusService(new PostgresServer(ds), new SchemaMigrator(ds), Clock.systemUTC()).check();
            out.println("Application : " + s.applicationVersion());
            out.println("Host        : " + s.host() + " (" + s.host().serviceManager() + ")");
            out.println("Database    : " + (s.databaseReachable() ? "PostgreSQL " + s.databaseVersion() : "UNAVAILABLE")
                    + (s.databaseReachable() && !s.databaseSupported() ? " (UNSUPPORTED)" : ""));
            out.println("Schema      : " + s.schema().state()
                    + (s.schema().currentVersion() != null ? " at version " + s.schema().currentVersion() : ""));
            s.schema().messages().forEach(m -> out.println("              " + m));
            out.println("Ready       : " + (s.ready() ? "yes" : "NO"));
            return Outcome.exit(s.ready() ? EXIT_OK : EXIT_DATABASE_PROBLEM);
        }
    }

    /** Parsed command line: one command plus an optional --config. */
    record ParsedArgs(String command, String configPath) {
        static ParsedArgs parse(String[] args) {
            String command = null;
            String config = null;
            List<String> rest = new ArrayList<>(List.of(args));
            for (int i = 0; i < rest.size(); i++) {
                String a = rest.get(i);
                if (a.startsWith("--config=")) {
                    config = a.substring("--config=".length());
                } else if (a.equals("--config")) {
                    if (i + 1 >= rest.size()) {
                        throw new IllegalArgumentException("--config requires a file path");
                    }
                    config = rest.get(++i);
                } else if (a.equals("--help") || a.equals("-h")) {
                    command = "help";
                } else if (a.startsWith("-")) {
                    throw new IllegalArgumentException("Unknown option: " + a);
                } else if (command == null) {
                    command = a;
                } else {
                    throw new IllegalArgumentException("Unexpected argument: " + a);
                }
            }
            if (config != null && config.isBlank()) {
                throw new IllegalArgumentException("--config requires a file path");
            }
            return new ParsedArgs(command == null ? "help" : command, config);
        }
    }
}
