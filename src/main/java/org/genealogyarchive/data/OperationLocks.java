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

    /**
     * Tries to take the lock for {@code id} without waiting.
     *
     * @return the held lock, or empty if another process holds it
     */
    public Optional<Held> tryLock(String namespace, UUID id) {
        String key = namespace + ":" + id;
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
