package org.genealogyarchive.data;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bounded, non-production verification for the WP1-R1 locking-protocol proposal (Stage A direction section 8:
 * LP-6 and SA-R1). It uses the existing schema and plain SQL sessions; no production locking code is involved or
 * changed. Each session reports its backend pid and transaction id, and blockers are read from
 * {@code pg_blocking_pids} while a session waits. Runs on every CI job (PostgreSQL 16 and 18, Linux and Windows).
 *
 * <p>These tests <em>characterise current PostgreSQL and current-schema behaviour</em>; they do not test the
 * proposed protocol, which is not implemented. Test 3 demonstrates an existing deadlock of the accepted protocol
 * and must be replaced by its protocol counterpart (proposal LP-7) when the protocol is implemented.
 */
class Lp6LockBehaviourIT {

    private static final String PARENT = "77777777-7777-4777-8777-777777777771";
    private static final String DERIVATIVE = "77777777-7777-4777-8777-777777777772";
    private static final String RECORD_1 = "30000000-0000-4000-8000-000000000001";
    private static final String RECORD_2 = "30000000-0000-4000-8000-000000000002";

    private TestDatabase db;
    private HikariDataSource ds;
    private ExecutorService pool;
    private final List<Connection> sessions = new ArrayList<>();
    /** Backend pids captured when each session opens: a connection busy in another thread cannot be queried. */
    private final java.util.Map<Connection, Integer> pids = new java.util.IdentityHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test", 8);
        new SchemaMigrator(ds).migrate();
        pool = Executors.newCachedThreadPool();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("SET search_path = gas");
            st.execute("INSERT INTO storage_volume (volume_id, name, purpose, required) VALUES "
                    + "('11111111-1111-4111-8111-111111111111', 'v', 'originals', true)");
            st.execute("INSERT INTO app_user (user_id, username, display_name, role, password_hash) VALUES "
                    + "('66666666-6666-4666-8666-666666666666', 'u', 'U', 'ADMINISTRATOR', 'xxxxxxxxxxxxxxxxxxxx')");
            st.execute("INSERT INTO file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id) VALUES "
                    + "('22222222-2222-4222-8222-222222222221', 'INGEST', 'COMMITTED', 'SYSTEM', 'x', '11111111-1111-4111-8111-111111111111'),"
                    + "('22222222-2222-4222-8222-222222222222', 'INGEST', 'COMMITTED', 'SYSTEM', 'x', '11111111-1111-4111-8111-111111111111')");
            st.execute("INSERT INTO file_content (content_id, volume_id, byte_size, sha256, state, created_by_operation) VALUES "
                    + "('44444444-4444-4444-8444-444444444441', '11111111-1111-4111-8111-111111111111', 3, "
                    + "decode(repeat('ab', 32), 'hex'), 'ACTIVE', '22222222-2222-4222-8222-222222222221')");
            st.execute("INSERT INTO archival_file (file_id, friendly_number, content_id, ingest_operation_id, original_filename, "
                    + "managed_filename, media_type, ingested_at, ingested_by) VALUES "
                    + "('" + PARENT + "', 1, '44444444-4444-4444-8444-444444444441', '22222222-2222-4222-8222-222222222221', "
                    + "'p.tif', 'p.tif', 'image/tiff', now(), '66666666-6666-4666-8666-666666666666'),"
                    + "('" + DERIVATIVE + "', 2, '44444444-4444-4444-8444-444444444441', '22222222-2222-4222-8222-222222222222', "
                    + "'d.tif', 'd.tif', 'image/tiff', now(), '66666666-6666-4666-8666-666666666666')");
            st.execute("INSERT INTO file_derivation (file_id, parent_file_id, method) VALUES ('" + DERIVATIVE + "', '"
                    + PARENT + "', 'thumbnail')");
            st.execute("INSERT INTO archival_record (record_id, friendly_number, created_at) VALUES ('" + RECORD_1
                    + "', 1, now()), ('" + RECORD_2 + "', 2, now())");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Connection c : sessions) {
            try {
                c.rollback();
                c.close();
            } catch (SQLException ignored) {
                // the session may already be broken by an expected error
            }
        }
        pool.shutdownNow();
        ds.close();
        db.close();
    }

    /** LP-6 (a): a foreign-key INSERT needing KEY SHARE on a File held FOR SHARE, while FOR UPDATE waits. */
    @Test
    void lp6_foreignKeyInsertDoesNotQueueBehindAWaitingForUpdate() throws Exception {
        Connection g = session("lp6-G");
        Connection c = session("lp6-C");
        Connection l = session("lp6-L");
        exec(g, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + PARENT + "' FOR SHARE");
        Future<Throwable> cWait = async(() -> exec(c, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + PARENT + "' FOR UPDATE"));
        List<Integer> cBlockers = awaitBlocked("lp6-C");
        assertThat(cBlockers).as("C waits for G").containsExactly(pid(g));

        long start = System.nanoTime();
        Throwable insert = catchAll(() -> exec(l, "INSERT INTO gas.record_file (record_id, file_id, sequence_number, linked_at)"
                + " VALUES ('" + RECORD_1 + "', '" + PARENT + "', 1, now())"));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(insert).as("L's foreign-key check is granted at once").isNull();
        assertThat(millis).isLessThan(5_000);
        assertThat(blockersOf("lp6-C")).as("C now waits for G (and possibly L), never the reverse").contains(pid(g));

        l.commit();
        g.commit();
        assertThat(cWait.get(30, TimeUnit.SECONDS)).as("C is granted once G and L have finished").isNull();
    }

    /** LP-6 (b): the same schedule with an explicit SELECT ... FOR KEY SHARE. */
    @Test
    void lp6_explicitKeyShareDoesNotQueueBehindAWaitingForUpdate() throws Exception {
        Connection g = session("lp6-G");
        Connection c = session("lp6-C");
        Connection l = session("lp6-L");
        exec(g, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + PARENT + "' FOR SHARE");
        Future<Throwable> cWait = async(() -> exec(c, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + PARENT + "' FOR UPDATE"));
        assertThat(awaitBlocked("lp6-C")).containsExactly(pid(g));
        Throwable keyShare = catchAll(() -> exec(l, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + PARENT + "' FOR KEY SHARE"));
        assertThat(keyShare).isNull();
        l.commit();
        g.commit();
        assertThat(cWait.get(30, TimeUnit.SECONDS)).isNull();
    }

    /**
     * SA-R1: the three-transaction cycle G -> L -> C -> G of the accepted protocol, with every lock granted before
     * the last request. G holds a derivative FOR SHARE (grouping), C holds the parent FOR UPDATE and its restriction
     * cascade waits for the derivative (FileService), L holds the Record FOR UPDATE and its link insert waits for
     * KEY SHARE on the parent (RecordService.linkFile). G then requests the Record. PostgreSQL reports 40P01, not a
     * lock timeout. A successful LP-6 result above does not remove this cycle.
     */
    @Test
    void saR1_theGrantedLockCycleOfTheAcceptedProtocolDeadlocks() throws Exception {
        Connection g = session("sar1-G");
        Connection c = session("sar1-C");
        Connection l = session("sar1-L");
        exec(g, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + DERIVATIVE + "' FOR SHARE");
        exec(c, "SELECT 1 FROM gas.archival_file WHERE file_id = '" + PARENT + "' FOR UPDATE");
        Future<Throwable> cascade = async(() -> exec(c, "UPDATE gas.archival_file SET restriction = 'RESTRICTED', "
                + "row_version = row_version + 1 WHERE file_id = '" + PARENT + "'"));
        assertThat(awaitBlocked("sar1-C")).as("the cascade waits for G's share lock").containsExactly(pid(g));
        exec(l, "SELECT 1 FROM gas.archival_record WHERE record_id = '" + RECORD_2 + "' FOR UPDATE");
        Future<Throwable> link = async(() -> exec(l, "INSERT INTO gas.record_file (record_id, file_id, sequence_number, linked_at)"
                + " VALUES ('" + RECORD_2 + "', '" + PARENT + "', 1, now())"));
        assertThat(awaitBlocked("sar1-L")).as("the link's foreign-key check waits for C").containsExactly(pid(c));

        Future<Throwable> gRecord = async(() -> exec(g, "SELECT 1 FROM gas.archival_record WHERE record_id = '" + RECORD_2
                + "' FOR UPDATE"));
        // Exactly one transaction of the cycle is chosen as the deadlock victim; it fails with 40P01.
        Throwable victim = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (victim == null && System.nanoTime() < deadline) {
            for (Future<Throwable> f : List.of(gRecord, cascade, link)) {
                if (f.isDone() && f.get() != null) {
                    victim = f.get();
                }
            }
            Thread.sleep(20);
        }
        assertThat(victim).as("one transaction of the cycle is aborted").isInstanceOf(SQLException.class);
        assertThat(((SQLException) victim).getSQLState()).as("deadlock detected, not a lock timeout").isEqualTo("40P01");
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private Connection session(String name) throws SQLException {
        Connection c = ds.getConnection();
        c.setAutoCommit(false);
        sessions.add(c);
        try (Statement st = c.createStatement()) {
            st.execute("SET application_name = '" + name + "'");
            st.execute("SET lock_timeout = '20s'");
        }
        c.commit();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT pg_backend_pid()")) {
            rs.next();
            pids.put(c, rs.getInt(1));
        }
        c.commit();
        return c;
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private interface SqlAction {
        void run() throws SQLException;
    }

    private static Throwable catchAll(SqlAction action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private Future<Throwable> async(SqlAction action) {
        Callable<Throwable> call = () -> catchAll(action);
        return pool.submit(call);
    }

    private int pid(Connection c) {
        return pids.get(c);
    }

    private List<Integer> blockersOf(String application) throws SQLException {
        try (Connection m = ds.getConnection(); PreparedStatement ps = m.prepareStatement(
                "SELECT unnest(pg_blocking_pids(pid)) FROM pg_stat_activity WHERE application_name = ?")) {
            ps.setString(1, application);
            List<Integer> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
            return out;
        }
    }

    /** Waits until the session is waiting on a heavyweight lock and returns its blockers. */
    private List<Integer> awaitBlocked(String application) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            try (Connection m = ds.getConnection(); PreparedStatement ps = m.prepareStatement(
                    "SELECT wait_event_type FROM pg_stat_activity WHERE application_name = ?")) {
                ps.setString(1, application);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "Lock".equals(rs.getString(1))) {
                        List<Integer> blockers = blockersOf(application);
                        if (!blockers.isEmpty()) {
                            return blockers;
                        }
                    }
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError(application + " did not become blocked");
    }
}
