package org.genealogyarchive.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.archive.ArchiveConfiguration;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.security.PasswordHasher;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUILD-SPEC s.28 Storage Volume model, owner decision D (open purpose code; an unavailable required
 * volume is reported but does not make the instance unready), and UP-007 separation of archive and
 * installation configuration.
 */
class StorageVolumeIT {

    @TempDir
    Path temp;

    private TestDatabase db;
    private HikariDataSource ds;
    private Actor admin;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds).migrate();
        CoreServices core = services(Map.of());
        core.archives().initialize(Actor.commandLine(), "Volume Test");
        admin = core.users().create(Actor.commandLine(), "admin", "Admin", Role.ADMINISTRATOR, ArchiveFixtures.PASSWORD).asActor();
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private CoreServices services(Map<UUID, Path> roots) {
        return CoreServices.create(ds, new VolumeRoots(roots), Clock.systemUTC(), new PasswordHasher(4));
    }

    @Test
    void registrationAssignsAPermanentVersion7VolumeIdAndAcceptsOpenPurposeCodes() {
        CoreServices core = services(Map.of());
        StorageVolume primary = core.volumes().register(admin, "Primary NAS", "primary", null, true);
        StorageVolume usb = core.volumes().register(admin, "USB backup 2", "removable-backup", "Kept off-site", false);
        StorageVolume exchange = core.volumes().register(admin, "Transfer drive", "transfer-packages", null, false);

        assertThat(GlobalIds.isVersion7(primary.volumeId())).isTrue();
        assertThat(usb.purpose()).isEqualTo("removable-backup");
        assertThat(exchange.purpose()).isEqualTo("transfer-packages");
        assertThatThrownBy(() -> core.volumes().register(admin, "Bad", "Primary Storage", null, false))
                .isInstanceOf(RuleViolationException.class);
    }

    @Test
    void retiringKeepsTheVolumeIdReservedAndNeverDeletesIt() {
        CoreServices core = services(Map.of());
        StorageVolume v = core.volumes().register(admin, "Old NAS", "primary", null, true);
        StorageVolume retired = core.volumes().retire(admin, v.volumeId(), v.version(), "Replaced");
        assertThat(retired.active()).isFalse();
        assertThat(retired.required()).as("a retired volume is never required").isFalse();

        StorageVolume replacement = core.volumes().register(admin, "Old NAS", "primary", null, true);
        assertThat(replacement.volumeId()).as("a new volume never reuses a retired ID").isNotEqualTo(v.volumeId());
        assertThat(core.volumes().list(admin)).extracting(StorageVolume::volumeId).contains(v.volumeId(), replacement.volumeId());
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("DELETE FROM gas.storage_volume");
            }
        }).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("UPDATE gas.storage_volume SET volume_id = gen_random_uuid()");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void availabilityIsReadOnlyAndAnUnavailableRequiredVolumeDoesNotMakeTheInstanceUnready() throws Exception {
        CoreServices setup = services(Map.of());
        StorageVolume present = setup.volumes().register(admin, "Present", "primary", null, true);
        StorageVolume missing = setup.volumes().register(admin, "Disconnected NAS", "primary-secondary", null, true);
        StorageVolume backup = setup.volumes().register(admin, "Offline backup", "backup", null, false);
        StorageVolume unmapped = setup.volumes().register(admin, "Unmapped", "backup", null, false);
        Path presentRoot = Files.createDirectory(temp.resolve("present"));
        Path missingRoot = temp.resolve("not-mounted");
        Path backupRoot = temp.resolve("backup-drive-unplugged");
        UUID unregistered = GlobalIds.newId();

        CoreServices core = services(Map.of(present.volumeId(), presentRoot, missing.volumeId(), missingRoot,
                backup.volumeId(), backupRoot, unregistered, Files.createDirectory(temp.resolve("unregistered"))));
        Map<UUID, VolumeStatus> status = new java.util.HashMap<>();
        core.volumes().availability().forEach(s -> status.put(s.volume().volumeId(), s));

        assertThat(status.get(present.volumeId()).availability()).isEqualTo(VolumeAvailability.AVAILABLE);
        assertThat(status.get(missing.volumeId()).availability()).isEqualTo(VolumeAvailability.UNAVAILABLE);
        assertThat(status.get(missing.volumeId()).problem()).as("required + unavailable is reported").isTrue();
        assertThat(status.get(backup.volumeId()).problem()).as("an offline optional backup is normal").isFalse();
        assertThat(status.get(unmapped.volumeId()).availability()).isEqualTo(VolumeAvailability.NOT_CONFIGURED);
        assertThat(core.volumes().unregisteredConfiguredVolumes()).containsExactly(unregistered);
        assertThat(core.operationalStatus().view(admin).volumeProblems()).isTrue();
        assertThat(core.systemStatus().check().ready()).as("database-only work continues (F-010)").isTrue();
        assertThat(missingRoot).as("checking availability creates nothing").doesNotExist();
        try (var listing = Files.list(presentRoot)) {
            assertThat(listing).as("Phase 1 writes no files to volumes").isEmpty();
        }
    }

    @Test
    void archiveConfigurationCarriesVolumeIdentityButNeverInstallationConfiguration() throws Exception {
        StorageVolume v = services(Map.of()).volumes().register(admin, "Primary NAS", "primary", "Main store", true);
        Path root = Files.createDirectory(temp.resolve("nas-mount-point"));
        InstallationConfig installation = db.installationConfig();
        CoreServices core = services(Map.of(v.volumeId(), root));

        ArchiveConfiguration configuration = core.archives().configuration(admin);

        assertThat(configuration.storageVolumes()).singleElement().satisfies(vol -> {
            assertThat(vol.volumeId()).isEqualTo(v.volumeId());
            assertThat(vol.name()).isEqualTo("Primary NAS");
        });
        String rendered = configuration.toString();
        assertThat(rendered)
                .doesNotContain(root.toString())
                .doesNotContain("nas-mount-point")
                .doesNotContain(installation.database().jdbcUrl())
                .doesNotContain(db.password())
                .doesNotContain(db.user());
    }
}
