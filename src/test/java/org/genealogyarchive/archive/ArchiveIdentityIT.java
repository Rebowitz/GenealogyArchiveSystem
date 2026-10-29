package org.genealogyarchive.archive;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.SystemStatusService;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 1 Archive ID requirements (BUILD-SPEC s.7, brief s.4, owner decision B). */
class ArchiveIdentityIT {

    private TestDatabase db;
    private HikariDataSource ds;
    private CoreServices core;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds).migrate();
        core = ArchiveFixtures.services(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
        db.close();
    }

    private UUID storedArchiveId(TestDatabase database) throws SQLException {
        try (Connection c = database.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT archive_id FROM gas.archive_instance")) {
            assertThat(rs.next()).isTrue();
            UUID id = rs.getObject(1, UUID.class);
            assertThat(rs.next()).as("exactly one Archive Instance").isFalse();
            return id;
        }
    }

    @Test
    void migrationAloneNeverCreatesAnArchive() {
        assertThat(core.archives().current()).isEmpty();
        assertThat(core.systemStatus().check().archive().initialized()).isFalse();
        assertThat(core.systemStatus().check().ready()).isFalse();
    }

    @Test
    void initializationCreatesOneVersion7ArchiveIdAndRecordsWhoDidIt() throws SQLException {
        ArchiveInstance archive = core.archives().initialize(Actor.commandLine(), "Andrews Family Archive");

        assertThat(GlobalIds.isVersion7(archive.archiveId())).isTrue();
        assertThat(storedArchiveId(db)).isEqualTo(archive.archiveId());
        assertThat(core.systemStatus().check().ready()).isTrue();
        ChangeRecord created = core.history().forObject(adminActor(), ArchiveService.OBJECT_TYPE, archive.archiveId()).get(0);
        assertThat(created.changeKind()).isEqualTo("CREATED");
        assertThat(created.actorLabel()).isEqualTo("command line");
        assertThat(created.fieldChanges()).anyMatch(f -> f.field().equals("archive_id")
                && f.after().equals(archive.archiveId().toString()));
    }

    @Test
    void aSecondInitializationIsRefusedAndTheArchiveIdNeverChanges() throws SQLException {
        UUID first = core.archives().initialize(Actor.commandLine(), "First").archiveId();

        assertThatThrownBy(() -> core.archives().initialize(Actor.commandLine(), "Second"))
                .isInstanceOf(ArchiveService.ArchiveAlreadyInitializedException.class)
                .hasMessageContaining(first.toString());
        assertThat(storedArchiveId(db)).isEqualTo(first);
    }

    @Test
    void onlyTheCommandLineSystemActorMayInitialize() {
        Actor admin = Actor.user(GlobalIds.newId(), "admin", Role.ADMINISTRATOR);
        assertThatThrownBy(() -> core.archives().initialize(admin, "X")).isInstanceOf(AccessDeniedException.class);
        assertThat(core.archives().current()).isEmpty();
    }

    @Test
    void archiveIdIsImmutableEvenToDirectSql() throws SQLException {
        UUID id = core.archives().initialize(Actor.commandLine(), "Immutable").archiveId();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("UPDATE gas.archive_instance SET archive_id = gen_random_uuid()"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("Immutable column");
            assertThatThrownBy(() -> s.execute("DELETE FROM gas.archive_instance")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("TRUNCATE gas.archive_instance")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.execute("INSERT INTO gas.archive_instance (archive_id, archive_name) VALUES (gen_random_uuid(), 'x')"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(storedArchiveId(db)).isEqualTo(id);
    }

    @Test
    void correctingTheArchiveNameKeepsIdentityUsesOptimisticConcurrencyAndIsRecorded() {
        ArchiveInstance archive = core.archives().initialize(Actor.commandLine(), "Old Name");
        Actor admin = adminActor();

        ArchiveInstance renamed = core.archives().rename(admin, archive.version(), "New Name", null);
        assertThat(renamed.archiveId()).as("A-001").isEqualTo(archive.archiveId());
        assertThat(renamed.archiveName()).isEqualTo("New Name");
        assertThat(renamed.version()).isEqualTo(archive.version() + 1);

        // SEC-008: a stale save is refused, nothing is overwritten.
        assertThatThrownBy(() -> core.archives().rename(admin, archive.version(), "Stale Name", null))
                .isInstanceOf(ConflictException.class);
        assertThat(core.archives().current().orElseThrow().archiveName()).isEqualTo("New Name");

        ChangeRecord correction = core.archives().history(admin).get(1);
        assertThat(correction.changeKind()).isEqualTo("CORRECTED");
        assertThat(correction.actorLabel()).isEqualTo("admin");
        assertThat(correction.fieldChanges()).singleElement().satisfies(f -> {
            assertThat(f.before()).isEqualTo("Old Name");
            assertThat(f.after()).isEqualTo("New Name");
        });
    }

    @Test
    void archiveIdPersistsAcrossApplicationRestarts() {
        UUID id = core.archives().initialize(Actor.commandLine(), "Restart").archiveId();
        ds.close();
        ds = null;
        for (int restart = 0; restart < 2; restart++) {
            try (ConfigurableApplicationContext app = ProcessLauncher.startWeb(db.installationConfig(), Map.of())) {
                assertThat(app.getBean(SystemStatusService.class).check().archive().archiveId()).isEqualTo(id);
                assertThat(app.getBean(ArchiveService.class).current().orElseThrow().archiveId()).isEqualTo(id);
            }
        }
    }

    @Test
    void archiveIdIsDistinctFromSchemaMarkerApplicationVersionAndHost() throws Exception {
        UUID id = core.archives().initialize(Actor.commandLine(), "Distinct").archiveId();
        String text = id.toString();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT application FROM gas.schema_identity")) {
            rs.next();
            assertThat(rs.getString(1)).as("Phase 0 marker is unchanged and is not the Archive ID")
                    .isEqualTo("genealogy-archive-system").isNotEqualTo(text);
        }
        assertThat(text).doesNotContain(SystemStatusService.applicationVersion())
                .doesNotContain(db.name())
                .doesNotContain(InetAddress.getLocalHost().getHostName().toLowerCase());
    }

    @Test
    void archiveIdIsStoredArchiveDataSoACopiedDatabaseCarriesTheSameId() throws SQLException {
        // Models restoring the same archive elsewhere (BK-007 foundation). The ID is not derived from
        // the database name, host or path. (Copying to start an UNRELATED archive is unsupported.)
        UUID id = core.archives().initialize(Actor.commandLine(), "Restorable").archiveId();
        ds.close();
        ds = null;
        try (TestDatabase restored = db.copy()) {
            assertThat(restored.name()).isNotEqualTo(db.name());
            assertThat(storedArchiveId(restored)).isEqualTo(id);
        }
    }

    @Test
    void independentlyInitializedArchivesHaveDifferentIds() {
        UUID here = core.archives().initialize(Actor.commandLine(), "Here").archiveId();
        try (TestDatabase other = TestDatabase.createFresh();
             HikariDataSource otherDs = DataSources.create(other.settings(), "test")) {
            new SchemaMigrator(otherDs).migrate();
            UUID there = ArchiveFixtures.services(otherDs).archives().initialize(Actor.commandLine(), "Here").archiveId();
            assertThat(there).isNotEqualTo(here);
        }
    }

    /** A real Administrator account, so history attribution references an existing user. */
    private Actor adminActor() {
        return core.users().create(Actor.commandLine(), "admin", "Admin (test)", Role.ADMINISTRATOR,
                ArchiveFixtures.PASSWORD).asActor();
    }
}
