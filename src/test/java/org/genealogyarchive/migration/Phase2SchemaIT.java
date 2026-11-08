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
        assertThat(report.migrationsApplied()).as("V0009-V0020").isEqualTo(12);
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
        assertThat(version()).isEqualTo("0020");
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
        assertThat(version()).isEqualTo("0020");
    }

    /**
     * V0019 (V0018 audit): an existing V0018 installation upgrades. Every quarantine move recorded before it is
     * UNTRUSTED (its hash was an observation of whatever was at the source), a legacy QUARANTINE_PENDING
     * generation is kept as it is, and the database refuses an UNRESOLVED generation without an open record.
     */
    @Test
    void anExistingV0018InstallationUpgradesAndEarlierMoveEvidenceIsUntrusted() throws Exception {
        SchemaMigrator.upTo(ds, "0018").migrate();
        String operation = "22222222-2222-4222-8222-222222222222";
        String content = "44444444-4444-4444-8444-444444444444";
        execute("INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('" + VOLUME + "', 'v', 'originals', true)");
        execute("INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id) VALUES ('"
                + operation + "', 'REPAIR', 'COMMITTED', 'SYSTEM', 'legacy', '" + VOLUME + "')");
        execute("INSERT INTO file_content (content_id, volume_id, byte_size, sha256, state, created_by_operation) VALUES ('"
                + content + "', '" + VOLUME + "', 3, decode(repeat('ab', 32), 'hex'), 'ACTIVE', '" + operation + "')");
        execute("INSERT INTO file_content_generation (content_id, generation, volume_id, storage_path, state, operation_id) "
                + "VALUES ('" + content + "', 1, '" + VOLUME + "', 'originals/44/44/c.g1', 'QUARANTINE_PENDING', '" + operation + "')");
        execute("INSERT INTO file_content_generation (content_id, generation, volume_id, storage_path, state, operation_id) "
                + "VALUES ('" + content + "', 2, '" + VOLUME + "', 'originals/44/44/c.g2', 'CURRENT', '" + operation + "')");
        execute("INSERT INTO quarantine_object (quarantine_id, volume_id, operation_id, content_id, source_path, quarantine_path, "
                + "reason, state, source_size, source_sha256) VALUES (gen_random_uuid(), '" + VOLUME + "', '" + operation + "', '"
                + content + "', 'originals/44/44/c.g1', 'quarantine/x/damaged-g1', 'DAMAGED_GENERATION', 'INTENDED', 3, repeat('cd', 32))");

        new SchemaMigrator(ds).migrate();
        assertThat(version()).isEqualTo("0020");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT evidence_basis FROM gas.quarantine_object")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("UNTRUSTED");
            }
            try (ResultSet rs = st.executeQuery("SELECT state FROM gas.file_content_generation WHERE generation = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("QUARANTINE_PENDING");
            }
        }
        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE file_content_generation SET state = 'UNRESOLVED' WHERE content_id = '" + content + "' AND generation = 1"));
        assertThat(refused).isNotNull();
        assertThat(allMessages(refused)).contains("without an open unresolved-object record");
        Throwable rewritten = org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE quarantine_object SET evidence_basis = 'ADMINISTRATOR_ATTESTED'"));
        assertThat(rewritten).as("the basis recorded with a move is never rewritten").isNotNull();
    }

    /**
     * V0020 (owner decision): an existing V0019 installation upgrades. Every existing unresolved-object record - open,
     * attested or released - is kept and marked LEGACY (no identity evidence), so it never identifies an object for
     * an attestation or a release. The database keeps new evidence complete, immutable, and bound to its decision.
     */
    @Test
    void anExistingV0019InstallationUpgradesAndItsRecordsAreLegacy() throws Exception {
        SchemaMigrator.upTo(ds, "0019").migrate();
        String operation = "22222222-2222-4222-8222-222222222222";
        String user = "55555555-5555-4555-8555-555555555555";
        execute("INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('" + VOLUME + "', 'v', 'originals', true)");
        execute("INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id) VALUES ('"
                + operation + "', 'REPAIR', 'COMMITTED', 'SYSTEM', 'legacy', '" + VOLUME + "')");
        execute("INSERT INTO app_user (user_id, username, display_name, role, password_hash) VALUES ('" + user
                + "', 'legacyadmin', 'Legacy Admin', 'ADMINISTRATOR', '" + "x".repeat(20) + "')");
        String sha = "'" + "ab".repeat(32) + "'";
        execute("INSERT INTO unresolved_object (unresolved_id, volume_id, path, operation_id, kind, observed_size, observed_sha256, "
                + "detail) VALUES ('60000000-0000-4000-8000-000000000001', '" + VOLUME + "', 'originals/aa/aa/open', '"
                + operation + "', 'OBJECT_AT_VACATED_LOCATION', 3, " + sha + ", 'legacy open')");
        execute("INSERT INTO unresolved_object (unresolved_id, volume_id, path, operation_id, kind, observed_size, observed_sha256, "
                + "detail, attested_by, attested_at) VALUES ('60000000-0000-4000-8000-000000000002', '" + VOLUME
                + "', 'originals/aa/aa/attested', '" + operation + "', 'OBJECT_AT_VACATED_LOCATION', 3, " + sha
                + ", 'legacy attested', '" + user + "', clock_timestamp())");
        execute("INSERT INTO unresolved_object (unresolved_id, volume_id, path, operation_id, kind, observed_size, observed_sha256, "
                + "detail, status, resolution, resolution_detail, resolved_at) VALUES ('60000000-0000-4000-8000-000000000003', '"
                + VOLUME + "', 'originals/aa/aa/released', '" + operation + "', 'OBJECT_AT_VACATED_LOCATION', 3, " + sha
                + ", 'legacy released', 'RESOLVED', 'NOT_ARCHIVAL', 'released', clock_timestamp())");

        new SchemaMigrator(ds).migrate();
        assertThat(version()).isEqualTo("0020");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT identity_evidence, decision_evidence, observed_created_at, observed_sha256, "
                     + "status, attested_at IS NOT NULL FROM gas.unresolved_object ORDER BY unresolved_id")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                assertThat(rs.getString(1)).as("existing records carry no identity evidence").isEqualTo("LEGACY");
                assertThat(rs.getString(2)).as("and no decision bound to an object").isNull();
                assertThat(rs.getObject(3)).isNull();
                assertThat(rs.getString(4)).as("existing observations are kept").isEqualTo("ab".repeat(32));
            }
            assertThat(rows).isEqualTo(3);
        }
        // New evidence must be complete.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "INSERT INTO unresolved_object (unresolved_id, volume_id, path, operation_id, kind, observed_size, observed_sha256, "
                        + "detail, identity_evidence) VALUES (gen_random_uuid(), '" + VOLUME + "', 'originals/aa/aa/new', '"
                        + operation + "', 'CURRENT_COPY', 3, " + sha + ", 'no times', 'RECORDED')")))
                .as("RECORDED evidence needs both times").isNotNull();
        execute("INSERT INTO unresolved_object (unresolved_id, volume_id, path, operation_id, kind, observed_size, observed_sha256, "
                + "detail, identity_evidence, observed_created_at, observed_modified_at, observed_file_key) VALUES "
                + "('60000000-0000-4000-8000-000000000004', '" + VOLUME + "', 'originals/aa/aa/new', '" + operation
                + "', 'CURRENT_COPY', 3, " + sha + ", 'recorded', 'RECORDED', clock_timestamp(), clock_timestamp(), '(dev=1,ino=2)')");
        // The observation is immutable.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE unresolved_object SET observed_modified_at = clock_timestamp() "
                        + "WHERE unresolved_id = '60000000-0000-4000-8000-000000000004'"))).isNotNull();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE unresolved_object SET identity_evidence = 'RECORDED' "
                        + "WHERE unresolved_id = '60000000-0000-4000-8000-000000000001'"))).as("a legacy record stays legacy").isNotNull();
        // Decision evidence changes only with its decision; a CONFIRMED decision needs RECORDED evidence.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE unresolved_object SET decision_evidence = 'CONFIRMED', decision_checked_at = clock_timestamp() "
                        + "WHERE unresolved_id = '60000000-0000-4000-8000-000000000004'"))).isNotNull();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE unresolved_object SET status = 'RESOLVED', resolution = 'NOT_ARCHIVAL', resolution_detail = 'x', "
                        + "resolved_at = clock_timestamp(), decision_evidence = 'CONFIRMED', decision_checked_at = clock_timestamp() "
                        + "WHERE unresolved_id = '60000000-0000-4000-8000-000000000001'"))).as("legacy cannot be CONFIRMED").isNotNull();
        // An open V0020 attestation is bound to its object.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> execute(
                "UPDATE unresolved_object SET attested_by = '" + user + "', attested_at = clock_timestamp() "
                        + "WHERE unresolved_id = '60000000-0000-4000-8000-000000000004'"))).isNotNull();
        execute("UPDATE unresolved_object SET attested_by = '" + user + "', attested_at = clock_timestamp(), "
                + "decision_evidence = 'CONFIRMED', decision_checked_at = clock_timestamp() "
                + "WHERE unresolved_id = '60000000-0000-4000-8000-000000000004'");
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
                "relation physical_location_owner kind=r",
                "relation unresolved_object kind=r");
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
