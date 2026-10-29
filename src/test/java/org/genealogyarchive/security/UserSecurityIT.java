package org.genealogyarchive.security;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.genealogyarchive.testsupport.ArchiveFixtures.PASSWORD;

/**
 * SEC-001/002/003 role enforcement at the service boundary (not only in the UI), SEC-007
 * attribution, SEC-008 stale saves, and account safety rules.
 */
class UserSecurityIT {

    private TestDatabase db;
    private HikariDataSource ds;
    private CoreServices core;
    private AppUser admin;
    private AppUser editor;
    private AppUser viewer;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds).migrate();
        core = ArchiveFixtures.services(ds);
        core.archives().initialize(Actor.commandLine(), "Security Test");
        admin = core.users().create(Actor.commandLine(), "admin", "Admin", Role.ADMINISTRATOR, PASSWORD);
        editor = core.users().create(admin.asActor(), "editor", "Editor", Role.EDITOR, PASSWORD);
        viewer = core.users().create(admin.asActor(), "viewer", "Viewer", Role.VIEWER, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    @Test
    void administratorPerformsAdministrativeOperations() {
        var archive = core.archives().current().orElseThrow();
        core.archives().rename(admin.asActor(), archive.version(), "Renamed", null);
        core.volumes().register(admin.asActor(), "Primary", "primary", null, true);
        assertThat(core.users().list(admin.asActor())).hasSize(3);
        assertThat(core.operationalStatus().view(admin.asActor())).isNotNull();
    }

    @Test
    void editorAndViewerCannotPerformAdministrativeOperationsEvenByCallingServicesDirectly() {
        for (AppUser user : List.of(editor, viewer)) {
            Actor actor = user.asActor();
            var archive = core.archives().current().orElseThrow();
            assertThatThrownBy(() -> core.archives().rename(actor, archive.version(), "x", null)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> core.users().create(actor, "x1", "X", Role.ADMINISTRATOR, PASSWORD)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> core.users().list(actor)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> core.users().update(actor, user.userId(), user.version(), "X", Role.ADMINISTRATOR, true))
                    .as("no self-promotion").isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> core.volumes().register(actor, "V", "primary", null, true)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> core.operationalStatus().view(actor)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> core.jobs().recent(actor, 10)).isInstanceOf(AccessDeniedException.class);
            // Permitted read operations:
            assertThat(core.archives().view(actor).archiveName()).isEqualTo("Security Test");
            assertThat(core.history().recent(actor, 10)).isNotEmpty();
        }
        assertThat(core.archives().current().orElseThrow().archiveName()).isEqualTo("Security Test");
    }

    @Test
    void changesAreAttributedToTheSignedInActorAutomatically() {
        var archive = core.archives().current().orElseThrow();
        core.archives().rename(admin.asActor(), archive.version(), "Attributed", null);
        ChangeRecord last = core.history().recent(admin.asActor(), 1).get(0);
        assertThat(last.actorUserId()).isEqualTo(admin.userId());
        assertThat(last.actorLabel()).isEqualTo("admin");
        assertThat(last.reason()).as("no administrative explanation is required").isNull();
    }

    @Test
    void staleAccountEditIsRefusedWithoutOverwriting() {
        AppUser current = core.users().update(admin.asActor(), editor.userId(), editor.version(), "Editor Renamed", Role.EDITOR, true);
        assertThatThrownBy(() -> core.users().update(admin.asActor(), editor.userId(), editor.version(), "Stale", Role.VIEWER, true))
                .isInstanceOf(ConflictException.class);
        assertThat(core.users().get(admin.asActor(), editor.userId()).displayName()).isEqualTo(current.displayName());
    }

    @Test
    void theLastEnabledAdministratorCannotBeDisabledOrDemoted() {
        AppUser second = core.users().create(admin.asActor(), "admin2", "Admin Two", Role.ADMINISTRATOR, PASSWORD);
        second = core.users().update(admin.asActor(), second.userId(), second.version(), "Admin Two", Role.EDITOR, true);
        AppUser fresh = core.users().get(admin.asActor(), admin.userId());
        assertThatThrownBy(() -> core.users().update(Actor.commandLine(), admin.userId(), fresh.version(), "Admin", Role.VIEWER, true))
                .isInstanceOf(RuleViolationException.class).hasMessageContaining("at least one enabled Administrator");
        assertThatThrownBy(() -> core.users().update(Actor.commandLine(), admin.userId(), fresh.version(), "Admin", Role.ADMINISTRATOR, false))
                .isInstanceOf(RuleViolationException.class);
        assertThat(second.role()).isEqualTo(Role.EDITOR);
    }

    @Test
    void disabledAccountsAreNotActiveButRemainForAttribution() {
        AppUser disabled = core.users().update(admin.asActor(), viewer.userId(), viewer.version(), "Viewer", Role.VIEWER, false);
        assertThat(disabled.enabled()).isFalse();
        assertThat(core.users().activeUser(viewer.userId())).isEmpty();
        assertThat(core.users().list(admin.asActor())).extracting(AppUser::username).contains("viewer");
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("DELETE FROM gas.app_user WHERE username = 'viewer'");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void usernamesAreUniqueRegardlessOfCaseAndImmutable() {
        assertThatThrownBy(() -> core.users().create(admin.asActor(), "ADMIN", "Other", Role.VIEWER, PASSWORD))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("UPDATE gas.app_user SET username = 'renamed' WHERE username = 'viewer'");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void passwordsAreStoredOnlyAsSaltedHashesAndNeverInChangeHistory() throws SQLException {
        core.users().changeOwnPassword(editor.asActor(), PASSWORD, "a-brand-new-passphrase");
        core.users().resetPassword(admin.asActor(), viewer.userId(), viewer.version(), "admin-chosen-passphrase");
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT password_hash FROM gas.app_user")) {
                while (rs.next()) {
                    assertThat(rs.getString(1)).startsWith("{bcrypt}$2")
                            .doesNotContain(PASSWORD, "a-brand-new-passphrase", "admin-chosen-passphrase");
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT string_agg(field_changes::text || coalesce(reason, ''), ' ') FROM gas.change_history")) {
                rs.next();
                assertThat(rs.getString(1)).doesNotContain(PASSWORD, "a-brand-new-passphrase", "admin-chosen-passphrase", "{bcrypt}");
            }
        }
        assertThat(core.history().forObject(admin.asActor(), UserService.OBJECT_TYPE, editor.userId()))
                .extracting(ChangeRecord::changeKind).contains("PASSWORD_CHANGED");
    }

    @Test
    void changingOwnPasswordRequiresTheCurrentPassword() {
        assertThatThrownBy(() -> core.users().changeOwnPassword(viewer.asActor(), "not-my-password", "another-good-passphrase"))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> core.users().changeOwnPassword(viewer.asActor(), PASSWORD, "short"))
                .isInstanceOf(RuleViolationException.class);
        core.users().changeOwnPassword(viewer.asActor(), PASSWORD, "another-good-passphrase");
        var credentials = core.users().credentials("viewer").orElseThrow();
        assertThat(core.passwordHasher().matches("another-good-passphrase", credentials.passwordHash())).isTrue();
    }
}
