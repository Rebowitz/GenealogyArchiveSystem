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
 * Phase 3 schema (V0021-V0022): an accepted Phase 2 database (V0020) with Files, a destroyed File's tombstone and a
 * quarantine object upgrades cleanly with that data unchanged (plan section 10.7); Phase 3 adds only the
 * Repository/Source/Capture Session/Record/grouping tables (ADR-0011); identifiers and history are permanent;
 * vocabularies hold only spec-named seeds (D3-08).
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

    /** An accepted Phase 2 installation: an active File, a destroyed File (tombstone) and a quarantine object. */
    private static final String[] PHASE_2_DATA = {
            "INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('11111111-1111-4111-8111-111111111111', 'v', 'originals', true)",
            "INSERT INTO app_user (user_id, username, display_name, role, password_hash) VALUES ('66666666-6666-4666-8666-666666666666', 'legacy', 'Legacy', 'ADMINISTRATOR', 'xxxxxxxxxxxxxxxxxxxx')",
            "INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id) VALUES ('22222222-2222-4222-8222-222222222221', 'INGEST', 'COMMITTED', 'SYSTEM', 'legacy', '11111111-1111-4111-8111-111111111111'), ('22222222-2222-4222-8222-222222222222', 'INGEST', 'COMMITTED', 'SYSTEM', 'legacy', '11111111-1111-4111-8111-111111111111'), ('22222222-2222-4222-8222-222222222223', 'REPAIR', 'COMMITTED', 'SYSTEM', 'legacy', '11111111-1111-4111-8111-111111111111')",
            "INSERT INTO file_content (content_id, volume_id, byte_size, sha256, state, created_by_operation) VALUES ('44444444-4444-4444-8444-444444444441', '11111111-1111-4111-8111-111111111111', 3, decode(repeat('ab', 32), 'hex'), 'ACTIVE', '22222222-2222-4222-8222-222222222221'), ('44444444-4444-4444-8444-444444444442', '11111111-1111-4111-8111-111111111111', 3, decode(repeat('cd', 32), 'hex'), 'ACTIVE', '22222222-2222-4222-8222-222222222222')",
            "INSERT INTO archival_file (file_id, friendly_number, content_id, ingest_operation_id, original_filename, managed_filename, media_type, ingested_at, ingested_by) VALUES ('77777777-7777-4777-8777-777777777771', 1, '44444444-4444-4444-8444-444444444441', '22222222-2222-4222-8222-222222222221', 'a.tif', 'a.tif', 'image/tiff', now(), '66666666-6666-4666-8666-666666666666'), ('77777777-7777-4777-8777-777777777772', 2, '44444444-4444-4444-8444-444444444442', '22222222-2222-4222-8222-222222222222', 'b.tif', 'b.tif', 'image/tiff', now(), '66666666-6666-4666-8666-666666666666')",
            "UPDATE archival_file SET lifecycle='DELETED', deleted_at=now(), row_version=row_version+1 WHERE friendly_number=2",
            "UPDATE archival_file SET lifecycle='DESTROYED', destroyed_at=now(), destruction_reason='test', row_version=row_version+1 WHERE friendly_number=2",
            "INSERT INTO quarantine_object (quarantine_id, volume_id, operation_id, content_id, source_path, quarantine_path, reason, state, source_size, source_sha256) VALUES ('88888888-8888-4888-8888-888888888881', '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222223', '44444444-4444-4444-8444-444444444441', 'originals/44/44/c.g1', 'quarantine/x/damaged-g1', 'DAMAGED_GENERATION', 'INTENDED', 3, repeat('cd', 32))"
    };

    private static final String[] PHASE_2_SNAPSHOTS = {
            "SELECT * FROM gas.archival_file ORDER BY file_id",
            "SELECT * FROM gas.file_content ORDER BY content_id",
            "SELECT * FROM gas.file_operation ORDER BY operation_id",
            "SELECT * FROM gas.quarantine_object ORDER BY quarantine_id",
            "SELECT * FROM gas.app_user ORDER BY user_id",
            "SELECT * FROM gas.storage_volume ORDER BY volume_id",
            "SELECT * FROM gas.friendly_id_series ORDER BY object_type"};

    @Test
    void acceptedPhase2DatabaseWithTombstoneAndQuarantineUpgradesCleanlyAndKeepsItsData() throws Exception {
        SchemaMigrator.upTo(ds, "0020").migrate();
        for (String sql : PHASE_2_DATA) {
            execute(sql);
        }
        assertThat(query("SELECT friendly_number, lifecycle FROM gas.archival_file ORDER BY 1"))
                .containsExactly("1|ACTIVE", "2|DESTROYED");
        List<List<String>> before = new ArrayList<>();
        for (String sql : PHASE_2_SNAPSHOTS) {
            before.add(query(sql));
        }

        SchemaMigrator current = new SchemaMigrator(ds);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.BEHIND);
        SchemaMigrator.MigrationReport report = current.migrate();

        assertThat(report.fromVersion()).isEqualTo("0020");
        assertThat(report.migrationsApplied()).as("V0021-V0022").isEqualTo(2);
        assertThat(current.status().state()).isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(new PostMigrationValidator(ds, current).validate()).isEmpty();
        for (int i = 0; i < PHASE_2_SNAPSHOTS.length; i++) {
            List<String> after = query(PHASE_2_SNAPSHOTS[i]);
            if (PHASE_2_SNAPSHOTS[i].contains("friendly_id_series")) {
                assertThat(after).as("existing series unchanged").containsAll(before.get(i));
            } else {
                assertThat(after).as("Phase 2 data unchanged: " + PHASE_2_SNAPSHOTS[i]).isEqualTo(before.get(i));
            }
        }
        // The tombstone is still final after the upgrade.
        assertThat(failure("UPDATE archival_file SET lifecycle = 'ACTIVE', destroyed_at = NULL, deleted_at = NULL "
                + "WHERE friendly_number = 2")).isNotBlank();
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
                "relation grouping_run kind=r",
                "relation grouping_run_group kind=r");
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

    /** Plan section 10.1: a Citation's targets and original locator never change; the current locator may. */
    @Test
    void citationTargetsAndOriginalLocatorAreImmutable() throws Exception {
        new SchemaMigrator(ds).migrate();
        execute("INSERT INTO source (source_id, friendly_number, title, created_at) VALUES "
                + "('20000000-0000-4000-8000-000000000001', 1, 'Deed Book 12', now())");
        execute("INSERT INTO source (source_id, friendly_number, title, created_at) VALUES "
                + "('20000000-0000-4000-8000-000000000002', 2, 'Deed Book 13', now())");
        execute("INSERT INTO citation (citation_id, friendly_number, source_id, original_locator, locator, created_at) VALUES "
                + "('40000000-0000-4000-8000-000000000001', 1, '20000000-0000-4000-8000-000000000001', 'p. 227', 'p. 227', now())");
        execute("UPDATE citation SET locator = 'pp. 227-228', row_version = row_version + 1");
        assertThat(query("SELECT original_locator, locator FROM gas.citation")).containsExactly("p. 227|pp. 227-228");
        assertThat(failure("UPDATE citation SET original_locator = 'p. 228'")).contains("Immutable column");
        assertThat(failure("UPDATE citation SET source_id = '20000000-0000-4000-8000-000000000002'")).contains("Immutable column");
        assertThat(failure("UPDATE citation SET record_id = source_id")).contains("Immutable column");
    }

    /** Plan section 10.2: a value is stored exactly in VALUE mode; INHERIT and BLANK carry none. */
    @Test
    void overrideModesAreTriState() throws Exception {
        new SchemaMigrator(ds).migrate();
        seedFileInSession();
        execute("UPDATE file_capture_context SET researcher_mode = 'BLANK'");
        execute("UPDATE file_capture_context SET researcher_mode = 'VALUE', override_researcher = 'R. Andrews'");
        assertThat(failure("UPDATE file_capture_context SET researcher_mode = 'BLANK'")).contains("check");
        assertThat(failure("UPDATE file_capture_context SET researcher_mode = 'INHERIT'")).contains("check");
        assertThat(failure("UPDATE file_capture_context SET researcher_mode = 'UNKNOWN', override_researcher = NULL"))
                .contains("check");
        assertThat(failure("UPDATE file_capture_context SET capture_date_mode = 'INHERIT', override_capture_date = '1850-01-01'"))
                .contains("check");
    }

    /** ADR-0011 s.4: a grouped row stays grouped; runs, chunks and group identities are permanent. */
    @Test
    void groupingIsPermanent() throws Exception {
        new SchemaMigrator(ds).migrate();
        seedFileInSession();
        execute("INSERT INTO archival_record (record_id, friendly_number, created_at) VALUES "
                + "('30000000-0000-4000-8000-000000000001', 1, now())");
        execute("INSERT INTO grouping_run (run_id, capture_session_id, restricted_view, started_at) VALUES "
                + "('90000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', false, now())");
        assertThat(failure("UPDATE file_capture_context SET applied_record_id = '30000000-0000-4000-8000-000000000001'"))
                .as("record and run together").contains("check");
        execute("UPDATE file_capture_context SET applied_record_id = '30000000-0000-4000-8000-000000000001', "
                + "applied_run_id = '90000000-0000-4000-8000-000000000001'");
        assertThat(failure("UPDATE file_capture_context SET applied_record_id = NULL, applied_run_id = NULL"))
                .contains("permanent");
        assertThat(failure("DELETE FROM file_capture_context")).contains("cannot be removed");
        execute("UPDATE file_capture_context SET row_version = row_version + 1");

        assertThat(failure("INSERT INTO grouping_run_group (run_id, group_key, chunk_number, record_id, created, rows_applied, "
                + "group_completed, completed_at) VALUES ('90000000-0000-4000-8000-000000000001', 's:x', 1, "
                + "'30000000-0000-4000-8000-000000000001', true, 1001, false, now())")).as("a chunk is bounded").contains("check");
        execute("INSERT INTO grouping_run_group (run_id, group_key, chunk_number, record_id, created, rows_applied, "
                + "group_completed, completed_at) VALUES ('90000000-0000-4000-8000-000000000001', 's:x', 1, "
                + "'30000000-0000-4000-8000-000000000001', true, 1000, false, now())");
        execute("INSERT INTO grouping_run_group (run_id, group_key, chunk_number, record_id, created, rows_applied, "
                + "group_completed, completed_at) VALUES ('90000000-0000-4000-8000-000000000001', 's:x', 2, "
                + "'30000000-0000-4000-8000-000000000001', false, 5, true, now())");
        assertThat(failure("DELETE FROM grouping_run_group")).contains("cannot be removed");
        assertThat(failure("UPDATE grouping_run_group SET group_completed = true")).contains("cannot be removed");

        assertThat(failure("INSERT INTO capture_session_group (capture_session_id, group_key, record_id, created_at) VALUES "
                + "('50000000-0000-4000-8000-000000000001', 'book 12', '30000000-0000-4000-8000-000000000001', now())"))
                .as("keys are namespaced").contains("check");
        execute("INSERT INTO capture_session_group (capture_session_id, group_key, record_id, created_at) VALUES "
                + "('50000000-0000-4000-8000-000000000001', 'g:book 12', '30000000-0000-4000-8000-000000000001', now())");
        assertThat(failure("DELETE FROM capture_session_group")).contains("cannot be removed");

        execute("UPDATE grouping_run SET state = 'INTERRUPTED', finished_at = now()");
        assertThat(failure("UPDATE grouping_run SET state = 'RUNNING', finished_at = NULL")).contains("finished");
        assertThat(failure("DELETE FROM grouping_run")).contains("cannot be removed");
    }

    private void seedFileInSession() throws Exception {
        for (String sql : PHASE_2_DATA) {
            execute(sql);
        }
        execute("INSERT INTO capture_session (capture_session_id, friendly_number, title, created_at) VALUES "
                + "('50000000-0000-4000-8000-000000000001', 1, 'Courthouse visit', now())");
        execute("INSERT INTO file_capture_context (file_id, capture_session_id, sequence_number, added_at) VALUES "
                + "('77777777-7777-4777-8777-777777777771', '50000000-0000-4000-8000-000000000001', 1, now())");
    }
}
