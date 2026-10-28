package org.genealogyarchive.service;

import org.genealogyarchive.data.PostgresServer;
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

    public SystemStatusService(PostgresServer postgres, SchemaMigrator migrator, Clock clock) {
        this.postgres = postgres;
        this.migrator = migrator;
        this.clock = clock;
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
