package org.genealogyarchive.service;

import org.genealogyarchive.migration.SchemaStatus;
import org.genealogyarchive.platform.HostPlatform;

import java.time.Instant;

/**
 * Readiness of this Archive Instance (subset of BUILD-SPEC s.58). Phase 1 adds the archive
 * initialization state; Storage Volume, worker and job status are reported separately by
 * {@link OperationalStatusService} because they do not decide readiness.
 *
 * @param applicationVersion build version
 * @param host               host operating system family
 * @param databaseReachable  a connection to PostgreSQL succeeded
 * @param databaseVersion    server version text, or null if unreachable
 * @param databaseSupported  server meets the minimum supported version
 * @param schema             schema migration status
 * @param archive            Archive Instance initialization state
 * @param checkedAt          when this status was computed
 */
public record SystemStatus(
        String applicationVersion,
        HostPlatform host,
        boolean databaseReachable,
        String databaseVersion,
        boolean databaseSupported,
        SchemaStatus schema,
        ArchiveStatus archive,
        Instant checkedAt) {

    /** Ready to serve archive work: database reachable, supported, schema current, archive initialized. */
    public boolean ready() {
        return databaseReachable && databaseSupported && schema.isCurrent() && archive.initialized();
    }

    /** Administrator-facing explanations, schema first. */
    public java.util.List<String> messages() {
        java.util.List<String> all = new java.util.ArrayList<>(schema.messages());
        if (schema.isCurrent()) {
            all.add(archive.message());
        }
        return all;
    }
}
