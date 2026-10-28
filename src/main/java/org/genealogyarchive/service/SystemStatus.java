package org.genealogyarchive.service;

import org.genealogyarchive.migration.SchemaStatus;
import org.genealogyarchive.platform.HostPlatform;

import java.time.Instant;

/**
 * Operational status of this Archive Instance's foundation (Phase 0 subset of BUILD-SPEC s.58).
 * Storage Volume, worker, job, backup and integrity-scan status are added in later phases.
 *
 * @param applicationVersion build version
 * @param host               host operating system family
 * @param databaseReachable  a connection to PostgreSQL succeeded
 * @param databaseVersion    server version text, or null if unreachable
 * @param databaseSupported  server meets the minimum supported version
 * @param schema             schema migration status
 * @param checkedAt          when this status was computed
 */
public record SystemStatus(
        String applicationVersion,
        HostPlatform host,
        boolean databaseReachable,
        String databaseVersion,
        boolean databaseSupported,
        SchemaStatus schema,
        Instant checkedAt) {

    /** Ready to serve archive work: database reachable, supported, schema current. */
    public boolean ready() {
        return databaseReachable && databaseSupported && schema.isCurrent();
    }
}
