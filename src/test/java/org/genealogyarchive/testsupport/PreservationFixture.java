package org.genealogyarchive.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileOperation;
import org.genealogyarchive.preservation.IngestionService;
import org.genealogyarchive.preservation.PreservationServices;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.security.AppUser;
import org.genealogyarchive.security.PasswordHasher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ready Phase 2 archive for tests: migrated database, initialized archive, one registered and
 * initialized Storage Volume chosen as the ingestion destination, staging and cache directories,
 * Administrator/Editor/Viewer actors, and scripted fault points.
 */
public final class PreservationFixture implements AutoCloseable {

    public final TestDatabase db;
    public final HikariDataSource ds;
    public final Path dir;
    public final Path volumeRoot;
    public final Path staging;
    public final Path cache;
    public final UUID volumeId;
    public final ScriptedFaults faults = new ScriptedFaults();
    public final Actor admin;
    public final Actor editor;
    public final Actor viewer;
    private final AtomicInteger keys = new AtomicInteger();
    private CoreServices services;
    private FileSettings settings;

    private PreservationFixture(Path dir) throws IOException {
        this.dir = dir;
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds).migrate();
        CoreServices bootstrap = ArchiveFixtures.services(ds);
        bootstrap.archives().initialize(Actor.commandLine(), "Test Archive");
        admin = actor(bootstrap, "admin1", Role.ADMINISTRATOR);
        editor = actor(bootstrap, "editor1", Role.EDITOR);
        viewer = actor(bootstrap, "viewer1", Role.VIEWER);
        volumeId = bootstrap.volumes().register(Actor.commandLine(), "Primary", "Originals", null, true).volumeId();
        volumeRoot = Files.createDirectories(dir.resolve("volume"));
        staging = dir.resolve("staging");
        cache = dir.resolve("cache");
        settings = new FileSettings(Optional.of(staging), Optional.of(cache), FileSettings.DEFAULT_MAX_UPLOAD_BYTES,
                Duration.ofMinutes(15));
        rebuild();
        preservation().volumes().initializeVolume(Actor.commandLine(), volumeId);
        long version = services.archives().view(admin).version();
        preservation().volumes().setIngestionVolume(admin, version, volumeId, null);
    }

    public static PreservationFixture create(Path tempDir) throws IOException {
        return new PreservationFixture(tempDir);
    }

    private static Actor actor(CoreServices s, String name, Role role) {
        AppUser u = s.users().create(Actor.commandLine(), name, name, role, ArchiveFixtures.PASSWORD);
        return Actor.user(u.userId(), u.username(), role);
    }

    /** Re-assembles services (for example after changing settings or the volume roots). */
    public CoreServices rebuild() {
        services = CoreServices.create(ds, new VolumeRoots(Map.of(volumeId, volumeRoot)), Clock.systemUTC(),
                new PasswordHasher(4), settings, faults);
        return services;
    }

    public CoreServices withSettings(FileSettings newSettings) {
        settings = newSettings;
        return rebuild();
    }

    /** Services whose volume root is configured elsewhere (simulates a disconnected or wrong mount). */
    public CoreServices servicesWithRoot(Path root) {
        return CoreServices.create(ds, new VolumeRoots(Map.of(volumeId, root)), Clock.systemUTC(),
                new PasswordHasher(4), settings, faults);
    }

    public FileSettings settings() {
        return settings;
    }

    public CoreServices services() {
        return services;
    }

    public PreservationServices preservation() {
        return services.preservation();
    }

    public UUID newBatch(Actor actor) {
        return preservation().ingestion().startBatch(actor, null).batchId();
    }

    public String nextKey() {
        return "item-" + keys.incrementAndGet();
    }

    /** Receives one upload into staging (web side only; the worker has not run yet). */
    public FileOperation receive(Actor actor, String name, byte[] bytes) {
        return receive(actor, newBatch(actor), nextKey(), name, bytes, Restriction.NORMAL, null, null);
    }

    public FileOperation receive(Actor actor, UUID batch, String key, String name, byte[] bytes, Restriction restriction,
                                 UUID parent, String method) {
        return preservation().ingestion().receive(actor, new IngestionService.Upload(batch, key, name,
                        Instant.parse("2020-01-02T03:04:05Z"), restriction, "test source", parent, method),
                bytes.length, new ByteArrayInputStream(bytes));
    }

    /** Runs the worker side for one operation. */
    public FileOperation process(UUID operationId) {
        preservation().processor().process(operationId, () -> { });
        return operation(operationId);
    }

    public FileOperation operation(UUID operationId) {
        return preservation().context().operations().find(operationId).orElseThrow();
    }

    /** Receive and archive: returns the committed File. */
    public ArchivalFile ingest(Actor actor, String name, byte[] bytes) {
        FileOperation op = process(receive(actor, name, bytes).operationId());
        if (op.fileId() == null) {
            throw new IllegalStateException("not archived: " + op.state() + " " + op.message());
        }
        return preservation().context().files().find(op.fileId()).orElseThrow();
    }

    public ArchivalFile file(UUID fileId) {
        return preservation().context().files().find(fileId).orElseThrow();
    }

    /** Absolute path of the current stored original of a File. */
    public Path storedPath(ArchivalFile file) {
        var content = preservation().context().contents().find(file.contentId()).orElseThrow();
        return content.currentPath().toNative(volumeRoot);
    }

    @Override
    public void close() {
        ds.close();
        db.close();
    }
}
