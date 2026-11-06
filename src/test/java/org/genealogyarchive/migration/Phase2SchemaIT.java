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
        assertThat(report.migrationsApplied()).as("V0009-V0014").isEqualTo(6);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(new PostMigrationValidator(ds, current).validate()).isEmpty();
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
                "relation quarantine_object kind=r");
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
