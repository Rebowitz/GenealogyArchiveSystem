package org.genealogyarchive.history;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.archive.ArchiveInstance;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Evidence strengthening (Phase 1 corrections): when Change History recording fails, the real service
 * mutation it describes rolls back with it - the mutation and its history share one transaction.
 * Recording is made to fail by a test-only trigger on this throwaway database.
 */
class ChangeHistoryAtomicityIT {

    private TestDatabase db;
    private HikariDataSource ds;
    private CoreServices core;
    private Actor admin;

    @BeforeEach
    void setUp() throws Exception {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test", 4);
        new SchemaMigrator(ds).migrate();
        core = ArchiveFixtures.services(ds);
        core.archives().initialize(Actor.commandLine(), "Original Name");
        admin = core.users().create(Actor.commandLine(), "admin", "Admin (test)", Role.ADMINISTRATOR,
                ArchiveFixtures.PASSWORD).asActor();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("""
                    CREATE FUNCTION gas.test_fail_history() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'test: change history unavailable'; END $$""");
            s.execute("""
                    CREATE TRIGGER test_fail_history BEFORE INSERT ON gas.change_history
                    FOR EACH ROW EXECUTE FUNCTION gas.test_fail_history()""");
        }
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private long count(String sql) throws Exception {
        try (Connection c = db.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void archiveRenameRollsBackWhenItsHistoryCannotBeRecorded() throws Exception {
        ArchiveInstance before = core.archives().current().orElseThrow();
        long historyBefore = count("SELECT count(*) FROM gas.change_history");

        assertThatThrownBy(() -> core.archives().rename(admin, before.version(), "Renamed", "test"))
                .hasStackTraceContaining("change history unavailable");

        ArchiveInstance after = core.archives().current().orElseThrow();
        assertThat(after.archiveName()).isEqualTo("Original Name");
        assertThat(after.version()).as("row_version unchanged").isEqualTo(before.version());
        assertThat(count("SELECT count(*) FROM gas.change_history")).isEqualTo(historyBefore);
    }

    @Test
    void accountCreationRollsBackWhenItsHistoryCannotBeRecorded() throws Exception {
        assertThatThrownBy(() -> core.users().create(admin, "editor-1", "Editor", Role.EDITOR, ArchiveFixtures.PASSWORD))
                .hasStackTraceContaining("change history unavailable");
        assertThat(count("SELECT count(*) FROM gas.app_user WHERE username = 'editor-1'")).isZero();
    }

    @Test
    void storageVolumeRegistrationRollsBackWhenItsHistoryCannotBeRecorded() throws Exception {
        assertThatThrownBy(() -> core.volumes().register(admin, "Primary", "primary", "Main store", true))
                .hasStackTraceContaining("change history unavailable");
        assertThat(count("SELECT count(*) FROM gas.storage_volume")).isZero();
    }
}
