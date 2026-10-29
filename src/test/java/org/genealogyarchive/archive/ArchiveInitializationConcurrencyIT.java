package org.genealogyarchive.archive;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Actor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidence strengthening (Phase 1 corrections): concurrent init-archive attempts are serialized by
 * the initialization lock and produce exactly one Archive identity.
 * <p>
 * Deterministic: a gate transaction holds the same table lock that initialization takes, all
 * initializers are started and observed waiting on that lock in pg_locks, and only then is the gate
 * released, so every initializer genuinely contends at the same moment.
 */
class ArchiveInitializationConcurrencyIT {

    private static final int INITIALIZERS = 6;

    private TestDatabase db;
    private HikariDataSource ds;
    private CoreServices core;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test", INITIALIZERS + 2);
        new SchemaMigrator(ds).migrate();
        core = ArchiveFixtures.services(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private int waitingOnArchiveLock(Statement s) throws Exception {
        try (ResultSet rs = s.executeQuery("""
                SELECT count(*) FROM pg_locks l JOIN pg_class c ON c.oid = l.relation
                 JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'gas' AND c.relname = 'archive_instance' AND NOT l.granted""")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void concurrentInitializersSerializeOnTheLockAndExactlyOneArchiveIdentityIsCreated() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(INITIALIZERS);
        List<Future<ArchiveInstance>> attempts = new ArrayList<>();
        try (Connection gate = db.connect(); Connection observer = db.connect(); Statement observe = observer.createStatement()) {
            gate.setAutoCommit(false);
            try (Statement g = gate.createStatement()) {
                g.execute("LOCK TABLE gas.archive_instance IN SHARE ROW EXCLUSIVE MODE");
            }
            for (int i = 0; i < INITIALIZERS; i++) {
                String name = "Contender " + i;
                attempts.add(pool.submit(() -> core.archives().initialize(Actor.commandLine(), name)));
            }
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (waitingOnArchiveLock(observe) < INITIALIZERS) {
                assertThat(System.nanoTime()).as("all initializers waiting on the lock").isLessThan(end);
                Thread.sleep(50);
            }
            assertThat(core.archives().current()).as("nothing created while the lock is held").isEmpty();
            gate.commit(); // release: all initializers now contend
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
        }

        List<ArchiveInstance> created = new ArrayList<>();
        List<Throwable> refused = new ArrayList<>();
        for (Future<ArchiveInstance> f : attempts) {
            try {
                created.add(f.get());
            } catch (java.util.concurrent.ExecutionException e) {
                refused.add(e.getCause());
            }
        }
        assertThat(created).as("exactly one initializer succeeds").hasSize(1);
        UUID archiveId = created.get(0).archiveId();
        assertThat(refused).hasSize(INITIALIZERS - 1)
                .allSatisfy(t -> assertThat(t).isInstanceOf(ArchiveService.ArchiveAlreadyInitializedException.class)
                        .hasMessageContaining(archiveId.toString()));

        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT count(*), min(archive_id::text) FROM gas.archive_instance")) {
                rs.next();
                assertThat(rs.getInt(1)).as("archive rows").isEqualTo(1);
                assertThat(rs.getString(2)).isEqualTo(archiveId.toString());
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT count(*) FROM gas.change_history WHERE object_type = 'archive_instance' AND change_kind = 'CREATED'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("creation recorded once").isEqualTo(1);
            }
        }
        assertThat(core.archives().current()).get().extracting(ArchiveInstance::archiveId).isEqualTo(archiveId);
    }
}
