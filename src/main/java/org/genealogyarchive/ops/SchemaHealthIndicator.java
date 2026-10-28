package org.genealogyarchive.ops;

import org.genealogyarchive.service.SystemStatus;
import org.genealogyarchive.service.SystemStatusService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness contributor named "schema": the instance is ready only when the database is a supported
 * PostgreSQL version and the schema exactly matches this build. Included in the readiness group, so
 * {@code /actuator/health/readiness} reports OUT_OF_SERVICE (HTTP 503) until 'migrate' has run.
 * Liveness is unaffected: the process is alive and can explain what is wrong.
 */
@Component("schemaHealthIndicator")
public class SchemaHealthIndicator implements HealthIndicator {

    private final SystemStatusService statusService;

    public SchemaHealthIndicator(SystemStatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public Health health() {
        SystemStatus status = statusService.check();
        Health.Builder builder = status.ready() ? Health.up() : Health.outOfService();
        return builder
                .withDetail("schemaState", status.schema().state().name())
                .withDetail("schemaVersion", String.valueOf(status.schema().currentVersion()))
                .build();
    }
}
