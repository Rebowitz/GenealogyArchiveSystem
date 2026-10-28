package org.genealogyarchive.service;

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

    public SystemStatusService(PostgresServer postgres, SchemaMigrator migrator, Clock clock,
                               SchemaIdentityRepository identity) {
        this.postgres = postgres;
        this.migrator = migrator;
        this.clock = clock;
        this.identity = identity;
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
                    SchemaStatus.unavailable("Database unavailable: " + rootMessage(e)), clock.instant());
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
                clock.instant());
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
