package org.genealogyarchive.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.archive.ArchiveInstance;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.security.AppUser;
import org.genealogyarchive.security.PasswordHasher;

import java.time.Clock;
import java.util.Map;

/**
 * Brings a throwaway test database to the Phase 1 "ready" state the way an administrator does:
 * migrate, then init-archive (owner decision B), optionally creating users.
 */
public final class ArchiveFixtures {

    /** A password that satisfies the password policy; tests only. */
    public static final String PASSWORD = "correct-horse-battery-staple";

    private ArchiveFixtures() {
    }

    /** Low bcrypt cost for fast tests; production uses cost 12. */
    public static CoreServices services(HikariDataSource ds) {
        return CoreServices.create(ds, new VolumeRoots(Map.of()), Clock.systemUTC(), new PasswordHasher(4));
    }

    public static ArchiveInstance migrateAndInitialize(TestDatabase db) {
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            new SchemaMigrator(ds).migrate();
            return services(ds).archives().initialize(Actor.commandLine(), "Test Archive");
        }
    }

    public static AppUser createUser(TestDatabase db, String username, Role role) {
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            return services(ds).users().create(Actor.commandLine(), username, username + " (test)", role, PASSWORD);
        }
    }
}
