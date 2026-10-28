package org.genealogyarchive.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.SchemaCatalog;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brief s.16: accepted Phase 0 database -> Phase 1 migrations -> valid Phase 1 state; no edited
 * Phase 0 migration; no historical domain tables (brief s.11, s.13).
 */
class Phase1SchemaIT {

    private TestDatabase db;
    private HikariDataSource ds;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private List<String> tables() {
        return new SchemaCatalog(ds).snapshot(DataSources.APPLICATION_SCHEMA).stream()
                .filter(l -> l.startsWith("relation ") && l.endsWith("kind=r")).toList();
    }

    @Test
    void acceptedPhase0DatabaseUpgradesCleanlyToPhase1() {
        SchemaMigrator.upTo(ds, "0001").migrate();
        SchemaMigrator current = new SchemaMigrator(ds);
        assertThat(current.status().state()).as("a Phase 0 database is behind this build").isEqualTo(SchemaStatus.State.BEHIND);
        assertThat(current.status().currentVersion()).isEqualTo("0001");

        SchemaMigrator.MigrationReport report = current.migrate();

        assertThat(report.fromVersion()).isEqualTo("0001");
        assertThat(report.migrationsApplied()).as("V0002-V0008").isEqualTo(7);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(new PostMigrationValidator(ds, current).validate()).isEmpty();
        assertThat(ArchiveFixtures.services(ds).archives().current())
                .as("upgrading never invents an Archive ID; init-archive is explicit").isEmpty();
    }

    @Test
    void jobRunningBeforeTheLeaseTokenMigrationGetsAnUnheldTokenAndRemainsRecoverable() throws Exception {
        SchemaMigrator.upTo(ds, "0007").migrate();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("""
                    INSERT INTO gas.background_job (job_id, kind, state, max_attempts, run_after, lease_owner, lease_until, attempts)
                    VALUES ('0192f0c4-2222-7000-8000-000000000001', 'test.legacy', 'RUNNING', 3, now(), gen_random_uuid(),
                            now() - interval '1 minute', 1)""");
        }
        new SchemaMigrator(ds).migrate();
        var repo = new org.genealogyarchive.data.BackgroundJobRepository(ds);
        UUID id = UUID.fromString("0192f0c4-2222-7000-8000-000000000001");
        assertThat(repo.findById(id).orElseThrow().leaseToken()).isNotNull();
        var claimed = repo.claim(List.of("test.legacy"), UUID.randomUUID(), java.time.Instant.now(), java.time.Duration.ofMinutes(5));
        assertThat(claimed).as("expired legacy lease is recovered").isPresent();
        assertThat(claimed.get().attempts()).isEqualTo(2);
    }

    @Test
    void upgradedAndFreshDatabasesReachTheSameSchema() {
        SchemaMigrator.upTo(ds, "0001").migrate();
        new SchemaMigrator(ds).migrate();
        List<String> upgraded = new SchemaCatalog(ds).snapshot(DataSources.APPLICATION_SCHEMA);
        try (TestDatabase fresh = TestDatabase.createFresh(); HikariDataSource freshDs = DataSources.create(fresh.settings(), "test")) {
            new SchemaMigrator(freshDs).migrate();
            assertThat(new SchemaCatalog(freshDs).snapshot(DataSources.APPLICATION_SCHEMA)).isEqualTo(upgraded);
        }
    }

    @Test
    void phase1AddsOnlyCoreInfrastructureTablesAndNoHistoricalDomainTables() {
        new SchemaMigrator(ds).migrate();
        assertThat(tables()).containsExactlyInAnyOrder(
                "relation flyway_schema_history kind=r",
                "relation schema_identity kind=r",
                "relation archive_instance kind=r",
                "relation app_user kind=r",
                "relation change_history kind=r",
                "relation storage_volume kind=r",
                "relation background_job kind=r",
                "relation worker_presence kind=r",
                "relation friendly_id_series kind=r");
    }

    @Test
    void validatorReportsInconsistentArchiveIdentityWithoutRepairingIt() throws Exception {
        new SchemaMigrator(ds).migrate();
        ArchiveFixtures.services(ds).archives().initialize(Actor.commandLine(), "One");
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            // Deliberately corrupt this throwaway fixture to simulate damaged identity.
            s.execute("ALTER TABLE gas.archive_instance DROP CONSTRAINT archive_instance_pkey");
            s.execute("ALTER TABLE gas.archive_instance DROP CONSTRAINT archive_instance_singleton_check");
            s.execute("INSERT INTO gas.archive_instance (singleton, archive_id, archive_name) VALUES (false, gen_random_uuid(), 'Two')");
        }
        assertThat(new PostMigrationValidator(ds, new SchemaMigrator(ds)).validate())
                .anyMatch(p -> p.contains("More than one Archive Instance"));
        var status = ArchiveFixtures.services(ds).systemStatus().check();
        assertThat(status.ready()).isFalse();
        assertThat(status.messages()).anyMatch(m -> m.contains("inconsistent"));
        try (Connection c = db.connect(); Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT count(*) FROM gas.archive_instance")) {
            rs.next();
            assertThat(rs.getInt(1)).as("nothing was silently repaired").isEqualTo(2);
        }
    }
}
