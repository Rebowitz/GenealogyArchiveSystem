package org.genealogyarchive.service;

import org.genealogyarchive.data.ArchiveInstanceRepository;
import org.genealogyarchive.data.PostgresServer;
import org.genealogyarchive.data.SchemaIdentityRepository;
import java.util.List;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.migration.SchemaStatus;
import org.genealogyarchive.platform.HostPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * Computes {@link SystemStatus}. Never throws for an unavailable database: unavailability is a
 * status to report visibly, not a crash.
 */
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);

    private final PostgresServer postgres;
    private final SchemaMigrator migrator;
    private final Clock clock;
    private final SchemaIdentityRepository identity;
    private final ArchiveInstanceRepository archives;

    public SystemStatusService(PostgresServer postgres, SchemaMigrator migrator, Clock clock,
                               SchemaIdentityRepository identity, ArchiveInstanceRepository archives) {
        this.postgres = postgres;
        this.migrator = migrator;
        this.clock = clock;
        this.identity = identity;
        this.archives = archives;
    }

    public SystemStatus check() {
        String version;
        boolean supported;
        try {
            int number = postgres.versionNumber();
            version = postgres.versionText();
            supported = PostgresServer.isSupported(number);
        } catch (RuntimeException e) {
            log.warn("Database unavailable: {}", rootMessage(e));
            return new SystemStatus(applicationVersion(), HostPlatform.current(), false, null, false,
                    SchemaStatus.unavailable("Database unavailable: " + rootMessage(e)),
                    ArchiveStatus.unknown("The archive cannot be checked while the database is unavailable."), clock.instant());
        }
        SchemaStatus schema;
        if (!supported) {
            schema = SchemaStatus.unavailable("PostgreSQL " + PostgresServer.MINIMUM_MAJOR_VERSION
                    + " or newer is required; the server reports " + version);
        } else {
            try {
                schema = migrator.status();
                if (schema.isCurrent() && !identity.hasExpectedIdentity()) {
                    schema = new SchemaStatus(SchemaStatus.State.INVALID, schema.currentVersion(),
                            schema.pendingVersions(), List.of("Genealogy Archive System foundation identity is absent or incorrect."));
                }
            } catch (RuntimeException e) {
                log.warn("Schema status could not be determined: {}", rootMessage(e));
                schema = SchemaStatus.unavailable("Schema status could not be determined: " + rootMessage(e));
            }
        }
        return new SystemStatus(applicationVersion(), HostPlatform.current(), true, version, supported, schema,
                archiveStatus(schema), clock.instant());
    }

    private ArchiveStatus archiveStatus(SchemaStatus schema) {
        if (!schema.isCurrent()) {
            return ArchiveStatus.unknown("The archive cannot be checked until the schema is current.");
        }
        try {
            var all = archives.findAll();
            if (all.isEmpty()) {
                return ArchiveStatus.notInitialized();
            }
            if (all.size() > 1) {
                return ArchiveStatus.unknown("Archive identity is inconsistent: more than one Archive Instance record exists.");
            }
            return ArchiveStatus.initialized(all.get(0).archiveId());
        } catch (RuntimeException e) {
            log.warn("Archive state could not be determined: {}", rootMessage(e));
            return ArchiveStatus.unknown("Archive state could not be determined: " + rootMessage(e));
        }
    }

    public static String applicationVersion() {
        String v = SystemStatusService.class.getPackage().getImplementationVersion();
        return v != null ? v : "development";
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
