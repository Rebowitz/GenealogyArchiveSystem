package org.genealogyarchive.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.SchemaCatalog;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 schema: an accepted Phase 1 database (V0008) upgrades cleanly; Phase 2 adds only File
 * preservation tables and no historical domain tables (Phase 2 brief: no Person/Source/Record).
 */
class Phase2SchemaIT {

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
    void acceptedPhase1DatabaseUpgradesCleanlyToPhase2() {
        SchemaMigrator.upTo(ds, "0008").migrate();
        SchemaMigrator current = new SchemaMigrator(ds);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.BEHIND);

        SchemaMigrator.MigrationReport report = current.migrate();

        assertThat(report.fromVersion()).isEqualTo("0008");
        assertThat(report.migrationsApplied()).as("V0009-V0018").isEqualTo(10);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(new PostMigrationValidator(ds, current).validate()).isEmpty();
    }

    private static String allMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private void execute(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("SET search_path = gas");
            st.execute(sql);
        }
    }

    private String version() throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT max(version) FROM gas.flyway_schema_history WHERE success")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static final String VOLUME = "11111111-1111-4111-8111-111111111111";

    private void operationClaiming(String operationId, String path) throws Exception {
        execute("INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id, target_path, "
                + "target_created) VALUES ('" + operationId + "', 'REPAIR', 'PROMOTING', 'SYSTEM', 'legacy', '" + VOLUME
                + "', '" + path + "', true)");
    }

    /** An existing Phase 2 installation (V0016) with a sound claim upgrades and gains its claim row. */
    @Test
    void anExistingPhase2InstallationUpgradesAndItsClaimsAreRecorded() throws Exception {
        SchemaMigrator.upTo(ds, "0016").migrate();
        execute("INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('" + VOLUME + "', 'v', 'originals', true)");
        operationClaiming("22222222-2222-4222-8222-222222222222", "originals/aa/bb/one.g2");
        new SchemaMigrator(ds).migrate();
        assertThat(version()).isEqualTo("0018");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT owner_kind, operation_id FROM gas.physical_location_owner")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("OPERATION");
            assertThat(rs.getString(2)).isEqualTo("22222222-2222-4222-8222-222222222222");
        }
    }

    /**
     * V0018: conflicting legacy claims are never skipped or decided automatically. The upgrade stops with a
     * diagnostic naming them, changes nothing, and succeeds once an Administrator has resolved them.
     */
    @Test
    void conflictingLegacyClaimsStopTheUpgradeWithADiagnosticAndNothingIsChosen() throws Exception {
        SchemaMigrator.upTo(ds, "0016").migrate();
        execute("INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('" + VOLUME + "', 'v', 'originals', true)");
        String first = "22222222-2222-4222-8222-222222222222";
        String second = "33333333-3333-4333-8333-333333333333";
        operationClaiming(first, "originals/aa/bb/same.g2");
        operationClaiming(second, "originals/aa/bb/same.g2");

        for (int attempt = 0; attempt < 2; attempt++) {
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> new SchemaMigrator(ds).migrate());
            assertThat(failure).as("attempt %s", attempt).isNotNull();
            assertThat(allMessages(failure)).contains("Ownership claims conflict", second, "originals/aa/bb/same.g2", first);
            assertThat(version()).as("V0018 not applied").isEqualTo("0017");
            try (Connection c = ds.getConnection(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM gas.file_operation WHERE target_created")) {
                rs.next();
                assertThat(rs.getInt(1)).as("both claims kept for review").isEqualTo(2);
            }
        }
        // The Administrator establishes which operation did not create the object (here: the one V0017 could
        // not record), clears its claim, and upgrades.
        String unrecorded;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT o.operation_id FROM gas.file_operation o WHERE o.target_created "
                     + "AND NOT EXISTS (SELECT 1 FROM gas.physical_location_owner p WHERE p.operation_id = o.operation_id)")) {
            assertThat(rs.next()).isTrue();
            unrecorded = rs.getString(1);
            assertThat(rs.next()).isFalse();
        }
        execute("UPDATE file_operation SET target_created = false WHERE operation_id = '" + unrecorded + "'");
        new SchemaMigrator(ds).migrate();
        assertThat(version()).isEqualTo("0018");
    }

    @Test
    void phase2AddsOnlyFilePreservationTables() {
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
                "relation friendly_id_series kind=r",
                "relation file_upload_batch kind=r",
                "relation file_operation kind=r",
                "relation file_operation_event kind=r",
                "relation file_content kind=r",
                "relation file_content_generation kind=r",
                "relation archival_file kind=r",
                "relation file_derivation kind=r",
                "relation integrity_scan kind=r",
                "relation integrity_finding kind=r",
                "relation integrity_finding_observation kind=r",
                "relation quarantine_object kind=r",
                "relation physical_location_owner kind=r");
    }

    @Test
    void fileSeriesUsesPrefixFAndIsRegisteredOnUpgrade() throws Exception {
        SchemaMigrator.upTo(ds, "0008").migrate();
        new SchemaMigrator(ds).migrate();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT prefix FROM gas.friendly_id_series WHERE object_type = 'archival_file'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("F");
        }
    }
}
