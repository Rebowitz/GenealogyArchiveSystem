package org.genealogyarchive.ops;

import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.testsupport.Http;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUILD-SPEC s.46 / SEC-010 foundation: database credentials never reach diagnostic logs, even when
 * authentication fails and the failure is logged and reported.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecretsNotLoggedIT {

    private static final String WRONG_PASSWORD = "Wr0ng-Secret-7f3a9c-web";

    private TestDatabase db;

    @BeforeEach
    void createDatabase() {
        db = TestDatabase.createFresh();
    }

    @AfterEach
    void dropDatabase() {
        db.close();
    }

    @Test
    void failedAuthenticationIsLoggedWithoutThePassword(CapturedOutput output) {
        try (ConfigurableApplicationContext app = ProcessLauncher.startWeb(
                TestDatabase.installationConfig(db.settingsWithPassword(WRONG_PASSWORD)), Map.of())) {
            assertThat(Http.get(app, "/actuator/health/readiness").statusCode()).isEqualTo(503);
            Http.get(app, "/");
        }
        assertThat(output.getAll())
                .as("the authentication failure is visible in diagnostics")
                .containsIgnoringCase("password authentication failed");
        assertThat(output.getAll()).doesNotContain(WRONG_PASSWORD);
    }

    @Test
    void jsonStructuredLogsDoNotContainThePassword(CapturedOutput output) {
        try (ConfigurableApplicationContext app = ProcessLauncher.startWeb(
                TestDatabase.installationConfig(db.settingsWithPassword(WRONG_PASSWORD)),
                Map.of("logging.structured.format.console", "ecs"))) {
            Http.get(app, "/actuator/health/readiness");
        }
        assertThat(output.getAll()).contains("\"log.level\"");
        assertThat(output.getAll()).doesNotContain(WRONG_PASSWORD);
    }
}
