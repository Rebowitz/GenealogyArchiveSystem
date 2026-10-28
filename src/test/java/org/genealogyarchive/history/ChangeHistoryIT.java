package org.genealogyarchive.history;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.data.ChangeHistoryRepository;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.Transactions;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUILD-SPEC s.45 / Q-007 foundation: Change History records significant changes atomically with
 * the change, is append-only, is not a read log, and is distinct from diagnostic logging.
 */
class ChangeHistoryIT {

    private TestDatabase db;
    private HikariDataSource ds;
    private CoreServices core;
    private Actor admin;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds).migrate();
        core = ArchiveFixtures.services(ds);
        core.archives().initialize(Actor.commandLine(), "History Test");
        admin = core.users().create(Actor.commandLine(), "admin", "Admin", Role.ADMINISTRATOR, ArchiveFixtures.PASSWORD).asActor();
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private long count() {
        return new ChangeHistoryRepository(ds).count();
    }

    @Test
    void historyIsAppendOnlyEvenToDirectSql() {
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("UPDATE gas.change_history SET actor_label = 'someone else'");
            }
        }).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("DELETE FROM gas.change_history");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void aRolledBackChangeLeavesNoHistory() {
        long before = count();
        ChangeRecorder recorder = new ChangeRecorder(new ChangeHistoryRepository(ds), Clock.systemUTC());
        assertThatThrownBy(() -> Transactions.template(ds).executeWithoutResult(status -> {
            recorder.record(admin, "archive_instance", GlobalIds.newId(), ChangeKinds.CORRECTED,
                    List.of(FieldChange.of("archive_name", FieldClass.CORRECTABLE, "a", "b")), null);
            throw new IllegalStateException("simulated failure after recording");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void readingDoesNotWriteHistory() {
        long before = count();
        core.archives().view(admin);
        core.archives().configuration(admin);
        core.users().list(admin);
        core.history().recent(admin, 50);
        core.systemStatus().check();
        core.operationalStatus().view(admin);
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void savingWithoutAnyChangeRecordsNothingAndKeepsTheVersion() {
        var archive = core.archives().current().orElseThrow();
        long before = count();
        var same = core.archives().rename(admin, archive.version(), archive.archiveName(), null);
        assertThat(count()).isEqualTo(before);
        assertThat(same.version()).isEqualTo(archive.version());
    }

    @Test
    void onlyChangedFieldsAreRecordedAndAnOptionalReasonIsKept() {
        var volume = core.volumes().register(admin, "Primary NAS", "primary", "Basement NAS", true);
        core.volumes().correct(admin, volume.volumeId(), volume.version(), "Primary NAS", "primary", "Office NAS", true,
                "Moved upstairs");
        ChangeRecord correction = core.history().forObject(admin, "storage_volume", volume.volumeId()).get(1);
        assertThat(correction.fieldChanges()).singleElement().satisfies(f -> {
            assertThat(f.field()).isEqualTo("description");
            assertThat(f.fieldClass()).isEqualTo(FieldClass.CORRECTABLE);
            assertThat(f.before()).isEqualTo("Basement NAS");
            assertThat(f.after()).isEqualTo("Office NAS");
        });
        assertThat(correction.reason()).isEqualTo("Moved upstairs");
    }
}
