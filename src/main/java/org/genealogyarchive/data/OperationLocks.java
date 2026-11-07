package org.genealogyarchive.data;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutual exclusion for processing one file operation (or one content), so the ingest job, a
 * recovery pass and an Administrator retry never act on the same operation at the same time.
 * <p>
 * Uses a PostgreSQL session-level advisory lock held on a dedicated connection for the duration of
 * the work. If the worker process dies, PostgreSQL drops the connection and the lock with it, so
 * crash recovery is never blocked. Straightforward and proportionate: no lock tables, no leases to
 * expire, no distributed coordination.
 */
public final class OperationLocks {

    private final DataSource dataSource;

    public OperationLocks(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** A held lock; closing it releases the lock and returns the connection. */
    public interface Held extends AutoCloseable {
        @Override
        void close();
    }

    /** The advisory-lock key text for an operation or content; the same key in session and transaction locks. */
    public static String key(String namespace, UUID id) {
        return namespace + ":" + id;
    }

    /** SQL expression turning a key text parameter into the advisory-lock key. */
    public static final String KEY_SQL = "hashtextextended(?, 7340)";

    /** Result of {@link #tryLockAll}: either every lock is held (on one connection), or the first busy id. */
    public record AllOrBusy(Optional<Held> held, Optional<UUID> busy) {
    }

    /**
     * Tries to take the locks of all {@code ids} without waiting, holding them together on ONE pooled
     * connection however many there are (GAS-P2 pool exhaustion: one connection per lock let a handful of
     * idle repairs exhaust the pool). If any is busy, none is kept.
     */
    public AllOrBusy tryLockAll(String namespace, java.util.Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return new AllOrBusy(Optional.of(() -> { }), Optional.empty());
        }
        Connection c = null;
        java.util.List<String> taken = new java.util.ArrayList<>();
        try {
            c = dataSource.getConnection();
            c.setAutoCommit(true);
            for (UUID id : ids) {
                String key = key(namespace, id);
                boolean locked;
                try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(" + KEY_SQL + ")")) {
                    ps.setString(1, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        locked = rs.getBoolean(1);
                    }
                }
                if (!locked) {
                    releaseAll(c, taken);
                    return new AllOrBusy(Optional.empty(), Optional.of(id));
                }
                taken.add(key);
            }
            Connection held = c;
            java.util.List<String> keys = java.util.List.copyOf(taken);
            return new AllOrBusy(Optional.of(() -> releaseAll(held, keys)), Optional.empty());
        } catch (SQLException e) {
            if (c != null) {
                discard(c);
            }
            throw new IllegalStateException("Could not take operation locks: " + e.getMessage(), e);
        }
    }

    private static void releaseAll(Connection c, java.util.List<String> keys) {
        try {
            for (String key : keys) {
                try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(" + KEY_SQL + ")")) {
                    ps.setString(1, key);
                    ps.execute();
                }
            }
            closeQuietly(c);
        } catch (SQLException e) {
            discard(c);
        }
    }

    private static void discard(Connection c) {
        try {
            c.abort(Runnable::run);
        } catch (SQLException ignored) {
            closeQuietly(c);
        }
    }

    /**
     * Tries to take the lock for {@code id} without waiting.
     *
     * @return the held lock, or empty if another process holds it
     */
    public Optional<Held> tryLock(String namespace, UUID id) {
        String key = key(namespace, id);
        Connection c = null;
        try {
            c = dataSource.getConnection();
            c.setAutoCommit(true);
            boolean locked;
            try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 7340))")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    locked = rs.getBoolean(1);
                }
            }
            if (!locked) {
                c.close();
                return Optional.empty();
            }
            Connection held = c;
            return Optional.of(() -> release(held, key));
        } catch (SQLException e) {
            closeQuietly(c);
            throw new IllegalStateException("Could not take an operation lock: " + e.getMessage(), e);
        }
    }

    private static void release(Connection c, String key) {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 7340))")) {
            ps.setString(1, key);
            ps.execute();
            closeQuietly(c);
        } catch (SQLException e) {
            // Never return a connection that may still hold the lock to the pool: discard it, which
            // ends the database session and releases the lock.
            try {
                c.abort(Runnable::run);
            } catch (SQLException ignored) {
                closeQuietly(c);
            }
        }
    }

    private static void closeQuietly(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException e) {
                // nothing further to do
            }
        }
    }
}
