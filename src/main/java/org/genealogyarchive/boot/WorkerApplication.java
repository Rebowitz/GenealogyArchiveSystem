package org.genealogyarchive.boot;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The background worker process (no HTTP listener). In Phase 0 it verifies the foundation and
 * stays running under the service manager; the PostgreSQL-backed job mechanism arrives in Phase 1.
 */
@SpringBootApplication(scanBasePackages = {"org.genealogyarchive.worker"})
@Import(CoreConfiguration.class)
public class WorkerApplication {
}
