package org.genealogyarchive.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Phase 4 schema (WP1: V0024; PHASE-4-PLAN s.8, ADR-0012 s.10). A populated accepted Phase 3 database (V0023)
 * upgrades with every accepted table byte-identical (UP-004, UP-006); V0024 adds only the people and place
 * vocabularies and {@code historical_date}; vocabularies hold only spec-named seeds (D4-11, U-1, U-2); the
 * Historical Date CHECKs enforce the single-row rules of clarifications s.2.2.
 */
class Phase4SchemaIT {

    private static final String USER = "66666666-6666-4666-8666-666666666666";

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

    // ---- Upgrade --------------------------------------------------------------------------------

    @Test
    void up006_aPopulatedPhase3DatabaseUpgradesWithEveryAcceptedTableUnchanged() throws Exception {
        SchemaMigrator.upTo(ds, "0023").migrate();
        seedPhase3();
        List<String> acceptedTables = query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'gas'"
                + " AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history' ORDER BY 1");
        Map<String, List<String>> before = snapshot(acceptedTables);

        SchemaMigrator current = SchemaMigrator.upTo(ds, "0024");
        SchemaMigrator.MigrationReport report = current.migrate();
        assertThat(report.fromVersion()).isEqualTo("0023");
        assertThat(report.migrationsApplied()).isEqualTo(1);
        assertThat(new PostMigrationValidator(ds, current).validate()).isEmpty();

        assertThat(snapshot(acceptedTables)).as("every accepted table is byte-identical").isEqualTo(before);
        assertThat(query("SELECT count(*) FROM gas.historical_date")).as("no date is created or parsed")
                .containsExactly("0");
        assertThat(query("SELECT record_date_text, filing_date_text FROM gas.archival_record"))
                .containsExactly("12 Feb 1720/21|March 1721");
    }

    @Test
    void v0024AddsOnlyVocabulariesAndHistoricalDate() throws Exception {
        SchemaMigrator.upTo(ds, "0023").migrate();
        List<String> before = query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'gas' ORDER BY 1");
        SchemaMigrator.upTo(ds, "0024").migrate();
        List<String> after = new ArrayList<>(query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'gas' ORDER BY 1"));
        after.removeAll(before);
        assertThat(after).containsExactlyInAnyOrder("relationship_type", "parentage_qualifier", "person_name_type",
                "place_type", "place_name_type", "historical_date");
    }

    // ---- Vocabularies (D4-11, U-1, U-2) ---------------------------------------------------------

    @Test
    void d4_11_seedsAreOnlyTheSpecNamedValues() throws Exception {
        SchemaMigrator.upTo(ds, "0024").migrate();
        assertThat(query("SELECT code, directed, category FROM gas.relationship_type ORDER BY code")).containsExactly(
                "GRANDPARENT_GRANDCHILD|t|DIRECT_ANCESTRY", "GUARDIAN_WARD|t|GUARDIANSHIP", "HOUSEHOLD_MEMBER|f|SOCIAL",
                "MASTER_APPRENTICE|t|SOCIAL", "PARENT_CHILD|t|PARENTAGE", "SIBLING|f|SIBLING", "SPOUSE|f|SPOUSE");
        assertThat(query("SELECT code FROM gas.parentage_qualifier ORDER BY code"))
                .containsExactly("ADOPTIVE", "BIOLOGICAL", "FOSTER", "STEP", "UNSPECIFIED");
        assertThat(query("SELECT code FROM gas.person_name_type ORDER BY code"))
                .containsExactly("BIRTH", "MARRIED", "NICKNAME", "VARIANT");
        assertThat(query("SELECT code, category FROM gas.place_type ORDER BY code"))
                .containsExactly("COUNTY|JURISDICTIONAL", "STATE|JURISDICTIONAL");
        assertThat(query("SELECT code FROM gas.place_name_type ORDER BY code"))
                .containsExactly("CURRENT", "HISTORICAL", "VARIANT");
    }

    @Test
    void vocabularyCodesArePermanentAndMeaningIsImmutable() throws Exception {
        SchemaMigrator.upTo(ds, "0024").migrate();
        assertThat(failure("DELETE FROM relationship_type WHERE code = 'SIBLING'")).contains("cannot be removed");
        assertThat(failure("UPDATE relationship_type SET category = 'OTHER' WHERE code = 'SIBLING'")).contains("Immutable");
        assertThat(failure("UPDATE relationship_type SET directed = true WHERE code = 'SIBLING'")).contains("Immutable");
        assertThat(failure("UPDATE place_type SET category = 'LOCALITY' WHERE code = 'COUNTY'")).contains("Immutable");
        assertThat(failure("DELETE FROM place_name_type WHERE code = 'VARIANT'")).contains("cannot be removed");
        assertThat(failure("DELETE FROM person_name_type WHERE code = 'BIRTH'")).contains("cannot be removed");
        // Relabelling and retiring stay possible (D3-08 pattern); administrators may add place types.
        execute("UPDATE relationship_type SET label = 'Parent (of)', row_version = row_version + 1 WHERE code = 'PARENT_CHILD'");
        execute("UPDATE place_name_type SET retired = true WHERE code = 'VARIANT'");
        execute("INSERT INTO place_type (code, label, category) VALUES ('TOWN', 'Town', 'LOCALITY')");
        assertThat(failure("INSERT INTO place_type (code, label, category) VALUES ('X', 'X', 'NEIGHBOURHOOD')"))
                .contains("place_type_category_check");
        // An undirected type reads the same both ways.
        assertThat(failure("INSERT INTO relationship_type (code, label, inverse_label, directed, category) VALUES "
                + "('COUSIN', 'Cousin', 'Other cousin', false, 'OTHER')")).contains("relationship_type_check");
    }

    @Test
    void parentageQualifiersAreFixedBySpecification() throws Exception {
        SchemaMigrator.upTo(ds, "0024").migrate();
        assertThat(failure("INSERT INTO parentage_qualifier (code, label) VALUES ('GOD', 'Godparent')")).contains("fixed");
        assertThat(failure("UPDATE parentage_qualifier SET label = 'Natural' WHERE code = 'BIOLOGICAL'")).contains("fixed");
        assertThat(failure("DELETE FROM parentage_qualifier WHERE code = 'STEP'")).contains("cannot be removed");
    }

    // ---- Historical Date single-row rules (clarifications s.2.2) ------------------------------------

    @Test
    void historicalDateChecksRejectInvalidRows() throws Exception {
        SchemaMigrator.upTo(ds, "0024").migrate();
        execute("INSERT INTO app_user (user_id, username, display_name, role, password_hash) VALUES ('" + USER
                + "', 'r', 'R', 'EDITOR', 'xxxxxxxxxxxxxxxxxxxx')");
        // Valid: an expression alone, and a confirmed point.
        execute(insert("'12 Feb 1720/21'", "'NOT_INTERPRETED'", "NULL, NULL, NULL, NULL", "NULL", "NULL, NULL"));
        execute(insert("'abt 1847'", "'POINT'", "1847, NULL, NULL, 'ABOUT'", "NULL", "2395663, 2396123"));

        Map<String, String> invalid = new LinkedHashMap<>();
        invalid.put("day without month", insert("'x'", "'POINT'", "1847, NULL, 3, 'EXACT'", "NULL", "1, 2"));
        invalid.put("month 13", insert("'x'", "'POINT'", "1847, 13, NULL, 'EXACT'", "NULL", "1, 2"));
        invalid.put("year 0", insert("'x'", "'POINT'", "0, NULL, NULL, 'EXACT'", "NULL", "1, 2"));
        invalid.put("year without modifier", insert("'x'", "'POINT'", "1847, NULL, NULL, NULL", "NULL", "1, 2"));
        invalid.put("point without year", insert("'x'", "'POINT'", "NULL, NULL, NULL, NULL", "NULL", "1, 2"));
        invalid.put("range without second point", insert("'x'", "'BETWEEN'", "1845, NULL, NULL, 'EXACT'", "NULL", "1, 2"));
        invalid.put("second point on a POINT", insert("'x'", "'POINT'", "1845, NULL, NULL, 'EXACT'", "1849", "1, 2"));
        invalid.put("unknown with a point", insert("'x'", "'UNKNOWN'", "1845, NULL, NULL, 'EXACT'", "NULL", "NULL, NULL"));
        invalid.put("uninterpreted with an envelope", insert("'x'", "'NOT_INTERPRETED'", "NULL, NULL, NULL, NULL", "NULL", "1, 2"));
        invalid.put("structured form with no bound", insert("'x'", "'POINT'", "1847, NULL, NULL, 'EXACT'", "NULL", "NULL, NULL"));
        invalid.put("reversed bounds", insert("'x'", "'POINT'", "1847, NULL, NULL, 'EXACT'", "NULL", "5, 2"));
        invalid.put("blank expression", insert("'  '", "'NOT_INTERPRETED'", "NULL, NULL, NULL, NULL", "NULL", "NULL, NULL"));
        for (Map.Entry<String, String> e : invalid.entrySet()) {
            assertThat(catchThrowable(() -> execute(e.getValue()))).as(e.getKey()).isNotNull();
        }
        assertThat(failure("INSERT INTO historical_date (historical_date_id, expression, form, p1_year, p1_modifier, "
                + "p1_dual_year, interpretation_origin, interpreted_expression, confirmed_at, earliest_jdn, latest_jdn, "
                + "created_at) VALUES (gen_random_uuid(), 'x', 'POINT', 1720, 'EXACT', 1722, 'RESEARCHER', 'x', now(), 1, 2, "
                + "now())")).contains("historical_date_p1_parts");
        assertThat(failure("INSERT INTO historical_date (historical_date_id, expression, form, calendar, created_at) "
                + "VALUES (gen_random_uuid(), 'x', 'NOT_INTERPRETED', 'JULIAN', now())")).contains("historical_date_uninterpreted");
        assertThat(failure("INSERT INTO historical_date (historical_date_id, expression, form, p1_year, p1_modifier, "
                + "earliest_jdn, latest_jdn, created_at) VALUES (gen_random_uuid(), 'x', 'POINT', 1847, 'EXACT', 1, 2, now())"))
                .as("a structured interpretation is always confirmed").contains("historical_date_confirmation");
        // Dates are permanent; identity is immutable.
        assertThat(failure("DELETE FROM historical_date")).contains("cannot be removed");
        assertThat(failure("UPDATE historical_date SET historical_date_id = gen_random_uuid()")).contains("Immutable");
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** points: "p1_year, p1_month, p1_day, p1_modifier"; second: p2_year or NULL; bounds: "earliest, latest". */
    private static String insert(String expression, String form, String points, String second, String bounds) {
        boolean confirmed = !form.equals("'NOT_INTERPRETED'");
        return "INSERT INTO historical_date (historical_date_id, expression, form, p1_year, p1_month, p1_day, p1_modifier,"
                + " p2_year, p2_modifier, earliest_jdn, latest_jdn, interpretation_origin, interpreted_expression,"
                + " confirmed_at, confirmed_by, created_at, created_by) VALUES (gen_random_uuid(), " + expression + ", " + form
                + ", " + points + ", " + second + ", " + (second.equals("NULL") ? "NULL" : "'EXACT'") + ", " + bounds + ", "
                + (confirmed ? "'RESEARCHER', " + expression + ", now(), '" + USER + "'" : "NULL, NULL, NULL, NULL")
                + ", now(), NULL)";
    }

    /** An accepted Phase 3 installation: user, volume, Files, Repository, Source, session, Record, Role, Citation. */
    private void seedPhase3() throws Exception {
        String[] sql = {
                "INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES ('11111111-1111-4111-8111-111111111111', 'v', 'originals', true)",
                "INSERT INTO app_user (user_id, username, display_name, role, password_hash) VALUES ('" + USER + "', 'legacy', 'Legacy', 'ADMINISTRATOR', 'xxxxxxxxxxxxxxxxxxxx')",
                "INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id) VALUES ('22222222-2222-4222-8222-222222222221', 'INGEST', 'COMMITTED', 'SYSTEM', 'legacy', '11111111-1111-4111-8111-111111111111')",
                "INSERT INTO file_content (content_id, volume_id, byte_size, sha256, state, created_by_operation) VALUES ('44444444-4444-4444-8444-444444444441', '11111111-1111-4111-8111-111111111111', 3, decode(repeat('ab', 32), 'hex'), 'ACTIVE', '22222222-2222-4222-8222-222222222221')",
                "INSERT INTO archival_file (file_id, friendly_number, content_id, ingest_operation_id, original_filename, managed_filename, media_type, ingested_at, ingested_by) VALUES ('77777777-7777-4777-8777-777777777771', 1, '44444444-4444-4444-8444-444444444441', '22222222-2222-4222-8222-222222222221', 'a.tif', 'a.tif', 'image/tiff', now(), '" + USER + "')",
                "INSERT INTO repository (repository_id, friendly_number, name, created_at) VALUES ('a0000000-0000-4000-8000-000000000001', 1, 'County courthouse (invented)', now())",
                "INSERT INTO source (source_id, friendly_number, title, jurisdiction_text, created_at) VALUES ('b0000000-0000-4000-8000-000000000001', 1, 'Deed Book Q (invented)', 'County of Frederick', now())",
                "INSERT INTO capture_session (capture_session_id, friendly_number, title, created_at) VALUES ('50000000-0000-4000-8000-000000000001', 1, 'Courthouse visit', now())",
                "INSERT INTO file_capture_context (file_id, capture_session_id, sequence_number, added_at) VALUES ('77777777-7777-4777-8777-777777777771', '50000000-0000-4000-8000-000000000001', 1, now())",
                "INSERT INTO archival_record (record_id, friendly_number, source_id, record_date_text, filing_date_text, created_at) VALUES ('30000000-0000-4000-8000-000000000001', 1, 'b0000000-0000-4000-8000-000000000001', '12 Feb 1720/21', 'March 1721', now())",
                "INSERT INTO record_file (record_id, file_id, sequence_number, page_designation, linked_at) VALUES ('30000000-0000-4000-8000-000000000001', '77777777-7777-4777-8777-777777777771', 1, '227', now())",
                "INSERT INTO record_role (role_id, record_id, role_code, party_as_written, sequence_number, created_at) VALUES ('c0000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', 'GRANTOR', 'Robt. Andrews (invented)', 1, now()), ('c0000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000001', 'GRANTEE', NULL, 2, now())",
                "INSERT INTO citation (citation_id, friendly_number, source_id, record_id, locator, created_at) VALUES ('d0000000-0000-4000-8000-000000000001', 1, 'b0000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', 'p. 227', now())"};
        for (String s : sql) {
            execute(s);
        }
    }

    private Map<String, List<String>> snapshot(List<String> tables) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String t : tables) {
            out.put(t, query("SELECT * FROM gas." + t + " ORDER BY 1"));
        }
        return out;
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
}
