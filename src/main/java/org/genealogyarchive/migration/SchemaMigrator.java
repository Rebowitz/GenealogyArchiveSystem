package org.genealogyarchive.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.genealogyarchive.data.DataSources;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Explicit, sequential, versioned schema migration (BUILD-SPEC s.56, UP-004/UP-005).
 * <p>
 * Migrations are plain SQL files {@code db/migration/V####__description.sql}. Each runs in its own
 * PostgreSQL transaction, so a failing migration leaves the schema exactly as it was before that
 * migration. Applied migrations are checksummed; an edited, missing or unknown migration is
 * reported, never silently accepted. Migration is invoked deliberately by the {@code migrate}
 * command; the web application and worker never migrate on startup.
 * <p>
 * Destructive or consequential migrations in later phases must be preceded by a recoverable
 * pre-upgrade backup (BUILD-SPEC s.56); the backup mechanism itself is Phase 8.
 */
public final class SchemaMigrator {

    public static final String DEFAULT_LOCATION = "classpath:db/migration";
    public static final String HISTORY_TABLE = "flyway_schema_history";

    private final Flyway flyway;

    public SchemaMigrator(DataSource dataSource) {
        this(dataSource, DEFAULT_LOCATION);
    }

    /** Additional locations are used by tests only (for example deliberately failing migrations). */
    public SchemaMigrator(DataSource dataSource, String... locations) {
        this(dataSource, null, locations);
    }

    /**
     * A migrator that applies migrations only up to {@code targetVersion}. Used to reproduce an
     * earlier accepted schema state (for example Phase 0 = version 0001) when testing upgrades.
     */
    public static SchemaMigrator upTo(DataSource dataSource, String targetVersion) {
        return new SchemaMigrator(dataSource, targetVersion, DEFAULT_LOCATION);
    }

    private SchemaMigrator(DataSource dataSource, String targetVersion, String... locations) {
        var configuration = Flyway.configure();
        if (targetVersion != null) {
            configuration = configuration.target(targetVersion);
        }
        this.flyway = configuration
                .dataSource(dataSource)
                .schemas(DataSources.APPLICATION_SCHEMA)
                .defaultSchema(DataSources.APPLICATION_SCHEMA)
                .createSchemas(true)
                .table(HISTORY_TABLE)
                .locations(locations)
                .failOnMissingLocations(true)
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                // Never ignore unknown ("future"), missing or failed migrations: report them.
                .ignoreMigrationPatterns(new String[0])
                .outOfOrder(false)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
    }

    /** Applies all pending migrations in order. */
    public MigrationReport migrate() {
        SchemaStatus before = status();
        if (before.state() == SchemaStatus.State.AHEAD
                || before.state() == SchemaStatus.State.FAILED
                || before.state() == SchemaStatus.State.INVALID) {
            throw new MigrationRefusedException(before);
        }
        MigrateResult result = flyway.migrate();
        return new MigrationReport(
                before.currentVersion(),
                result.targetSchemaVersion != null ? result.targetSchemaVersion : before.currentVersion(),
                result.migrationsExecuted);
    }

    /** Inspects the database without changing it. */
    public SchemaStatus status() {
        MigrationInfoService info = flyway.info();
        MigrationInfo current = info.current();
        String currentVersion = current != null && current.getVersion() != null ? current.getVersion().getVersion() : null;

        List<String> pending = Arrays.stream(info.pending())
                .map(m -> m.getVersion() != null ? m.getVersion().getVersion() : m.getDescription())
                .toList();
        List<MigrationInfo> applied = Arrays.asList(info.applied());

        List<String> failed = new ArrayList<>();
        List<String> future = new ArrayList<>();
        for (MigrationInfo m : info.all()) {
            MigrationState state = m.getState();
            if (state.isFailed()) {
                failed.add(describe(m));
            }
            if (state == MigrationState.FUTURE_SUCCESS || state == MigrationState.FUTURE_FAILED) {
                future.add(describe(m));
            }
        }

        if (!failed.isEmpty()) {
            return SchemaStatus.of(SchemaStatus.State.FAILED, currentVersion, pending,
                    "A schema migration previously failed: " + String.join(", ", failed)
                            + ". Restore the pre-upgrade backup or repair deliberately before continuing.");
        }
        if (!future.isEmpty()) {
            return SchemaStatus.of(SchemaStatus.State.AHEAD, currentVersion, pending,
                    "The database contains migrations unknown to this application build (" + String.join(", ", future)
                            + "). It was upgraded by a newer version; do not run this older version against it.");
        }
        if (applied.isEmpty()) {
            return SchemaStatus.of(SchemaStatus.State.NOT_INITIALIZED, null, pending,
                    "The database has not been initialised. Run the 'migrate' command.");
        }
        if (!pending.isEmpty()) {
            return SchemaStatus.of(SchemaStatus.State.BEHIND, currentVersion, pending,
                    "The database schema is at version " + currentVersion + "; this build requires "
                            + pending.get(pending.size() - 1) + ". Run the 'migrate' command.");
        }
        ValidateResult validation = flyway.validateWithResult();
        if (!validation.validationSuccessful) {
            return new SchemaStatus(SchemaStatus.State.INVALID, currentVersion, pending,
                    List.of("Applied migrations do not match this build: " + validation.getAllErrorMessages()));
        }
        return SchemaStatus.of(SchemaStatus.State.CURRENT, currentVersion, pending,
                "Schema is current at version " + currentVersion + ".");
    }

    private static String describe(MigrationInfo m) {
        return (m.getVersion() != null ? "V" + m.getVersion().getVersion() : "R") + " " + m.getDescription();
    }

    /** Summary of a successful migrate run. */
    public record MigrationReport(String fromVersion, String toVersion, int migrationsApplied) {
    }

    /** Migration was refused because the current state must be resolved by an administrator first. */
    public static final class MigrationRefusedException extends RuntimeException {
        private final SchemaStatus status;

        public MigrationRefusedException(SchemaStatus status) {
            super("Migration refused (" + status.state() + "): " + String.join(" ", status.messages()));
            this.status = status;
        }

        public SchemaStatus status() {
            return status;
        }
    }
}
