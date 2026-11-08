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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Phase 3 schema (V0021-V0023): an accepted Phase 2 database (V0020) upgrades cleanly with its data unchanged;
 * Phase 3 adds only the Repository/Source/Capture Session/Record tables (ADR-0011); identifiers and history are
 * permanent; vocabularies hold only spec-named seeds (D3-08).
 */
class Phase3SchemaIT {

    private static final String VOLUME = "11111111-1111-4111-8111-111111111111";

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

    private void execute(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("SET search_path = gas");
            st.execute(sql);
        }
    }

    private List<String> query(String sql) throws Exception {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(i > 1 ? "|" : "").append(rs.getString(i));
                }
                out.add(row.toString());
            }
        }
        return out;
    }

    private String failure(String sql) {
        Throwable t = catchThrowable(() -> execute(sql));
        assertThat(t).as(sql).isNotNull();
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void acceptedPhase2DatabaseUpgradesCleanlyAndKeepsItsData() throws Exception {
        SchemaMigrator.upTo(ds, "0020").migrate();
        execute("INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('" + VOLUME + "', 'v', 'originals', true)");
        execute("INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id) VALUES "
                + "('22222222-2222-4222-8222-222222222222', 'REPAIR', 'COMMITTED', 'SYSTEM', 'legacy', '" + VOLUME + "')");
        List<String> before = query("SELECT operation_id, kind, state, row_version FROM gas.file_operation ORDER BY 1");

        SchemaMigrator current = new SchemaMigrator(ds);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.BEHIND);
        SchemaMigrator.MigrationReport report = current.migrate();

        assertThat(report.fromVersion()).isEqualTo("0020");
        assertThat(report.migrationsApplied()).as("V0021-V0023").isEqualTo(3);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(new PostMigrationValidator(ds, current).validate()).isEmpty();
        assertThat(query("SELECT operation_id, kind, state, row_version FROM gas.file_operation ORDER BY 1"))
                .as("Phase 2 data unchanged").isEqualTo(before);
    }

    @Test
    void phase3AddsOnlyRecordTables() {
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
                "relation unresolved_object kind=r",
                // Phase 3
                "relation record_classification kind=r",
                "relation record_role_type kind=r",
                "relation repository kind=r",
                "relation source kind=r",
                "relation capture_session kind=r",
                "relation file_capture_context kind=r",
                "relation archival_record kind=r",
                "relation record_file kind=r",
                "relation record_role kind=r",
                "relation capture_session_group kind=r",
                "relation citation kind=r",
                "relation file_consolidation kind=r",
                "relation file_consolidation_member kind=r");
    }

    /** D3-07 prefixes; D3-08 seeds: only the values the specifications name. */
    @Test
    void friendlySeriesAndSeedVocabulariesAreRegistered() throws Exception {
        new SchemaMigrator(ds).migrate();
        assertThat(query("SELECT object_type, prefix, min_digits FROM gas.friendly_id_series ORDER BY object_type"))
                .contains("archival_record|R|5", "repository|RP|4", "source|S|5", "capture_session|CS|4", "citation|CT|5",
                        "archival_file|F|5");
        assertThat(query("SELECT code FROM gas.record_classification ORDER BY code")).containsExactly("DEED", "DTRUST");
        assertThat(query("SELECT code FROM gas.record_role_type ORDER BY code")).containsExactly("ADJOINING_LANDHOLDER",
                "BENEFICIARY", "EXECUTOR", "GRANTEE", "GRANTOR", "PRIOR_OWNER", "SURVEYOR", "TESTATOR", "WITNESS");
    }

    /** A-001, A-002, A-003, R-006, CT-003, D3-12: identity is permanent; incomplete Records are valid. */
    @Test
    void recordIdentityIsPermanentAndIncompleteRecordsAreValid() throws Exception {
        new SchemaMigrator(ds).migrate();
        execute("INSERT INTO archival_record (record_id, friendly_number, created_at) VALUES "
                + "('30000000-0000-4000-8000-000000000001', 1842, now())");
        execute("UPDATE archival_record SET classification_code = 'DEED' WHERE friendly_number = 1842");
        execute("UPDATE archival_record SET classification_code = 'DTRUST', row_version = row_version + 1 WHERE friendly_number = 1842");
        assertThat(query("SELECT record_id, classification_code FROM gas.archival_record"))
                .containsExactly("30000000-0000-4000-8000-000000000001|DTRUST");
        assertThat(failure("UPDATE archival_record SET friendly_number = 2")).contains("Immutable column");
        assertThat(failure("UPDATE archival_record SET record_id = '30000000-0000-4000-8000-000000000009'")).contains("Immutable column");
        assertThat(failure("DELETE FROM archival_record")).contains("cannot be removed");
        assertThat(failure("UPDATE archival_record SET classification_code = 'NO_SUCH'")).contains("foreign key");
        assertThat(failure("UPDATE archival_record SET lifecycle = 'DELETED'")).as("deletion records when").contains("check");
        assertThat(failure("DELETE FROM record_classification WHERE code = 'DEED'")).contains("cannot be removed");
        assertThat(failure("UPDATE record_classification SET code = 'DEEDS' WHERE code = 'DEED'")).contains("Immutable column");
        execute("UPDATE record_classification SET retired = true WHERE code = 'DEED'");
    }

    @Test
    void repositoriesSourcesSessionsAndCitationsArePermanent() throws Exception {
        new SchemaMigrator(ds).migrate();
        execute("INSERT INTO repository (repository_id, friendly_number, name, created_at) VALUES "
                + "('10000000-0000-4000-8000-000000000001', 1, 'County Clerk', now())");
        // O-003: a Source without a Repository.
        execute("INSERT INTO source (source_id, friendly_number, title, created_at) VALUES "
                + "('20000000-0000-4000-8000-000000000001', 1, 'Deed Book 12', now())");
        execute("INSERT INTO capture_session (capture_session_id, friendly_number, title, created_at) VALUES "
                + "('50000000-0000-4000-8000-000000000001', 1, 'Courthouse visit', now())");
        assertThat(failure("DELETE FROM repository")).contains("cannot be removed");
        assertThat(failure("DELETE FROM source")).contains("cannot be removed");
        assertThat(failure("DELETE FROM capture_session")).contains("cannot be removed");
        assertThat(failure("INSERT INTO citation (citation_id, friendly_number, created_at) VALUES "
                + "('40000000-0000-4000-8000-000000000001', 1, now())")).as("a Citation refers to something").contains("citation_check");
        execute("INSERT INTO citation (citation_id, friendly_number, source_id, created_at) VALUES "
                + "('40000000-0000-4000-8000-000000000001', 1, '20000000-0000-4000-8000-000000000001', now())");
        assertThat(failure("DELETE FROM citation")).contains("cannot be removed");
        assertThat(failure("INSERT INTO repository (repository_id, friendly_number, name, created_at) VALUES "
                + "('10000000-0000-4000-8000-000000000002', 2, '   ', now())")).as("a name is a real label").contains("check");
    }
}
