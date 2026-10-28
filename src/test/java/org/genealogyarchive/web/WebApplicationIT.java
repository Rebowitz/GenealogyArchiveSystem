package org.genealogyarchive.web;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.InstallationConfig.DatabaseSettings;
import org.genealogyarchive.config.Secret;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.Http;
import org.genealogyarchive.testsupport.NetworkAttemptRecorder;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PHASE-0 tests: application starts against the supported PostgreSQL version; basic health and
 * readiness work; no normal runtime Internet/cloud dependency (UP-001/UP-002 foundation).
 */
class WebApplicationIT {

    private TestDatabase db;

    @BeforeEach
    void createDatabase() {
        db = TestDatabase.createFresh();
    }

    @AfterEach
    void dropDatabase() {
        db.close();
    }

    private void migrate() {
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            new SchemaMigrator(ds).migrate();
        }
    }

    @Test
    void startsAndReportsReadyAgainstMigratedDatabaseWithoutNetworkAccess() {
        migrate();
        try (NetworkAttemptRecorder network = NetworkAttemptRecorder.install();
             ConfigurableApplicationContext app = ProcessLauncher.startWeb(db.installationConfig(), Map.of())) {

            HttpResponse<String> liveness = Http.get(app, "/actuator/health/liveness");
            HttpResponse<String> readiness = Http.get(app, "/actuator/health/readiness");
            HttpResponse<String> home = Http.get(app, "/");
            HttpResponse<String> css = Http.get(app, "/css/app.css");

            assertThat(liveness.statusCode()).isEqualTo(200);
            assertThat(liveness.body()).contains("\"UP\"");
            assertThat(readiness.statusCode()).isEqualTo(200);
            assertThat(readiness.body()).contains("\"UP\"");
            assertThat(home.statusCode()).isEqualTo(200);
            assertThat(home.body()).contains("Genealogy Archive System", "Archive instance is ready", "CURRENT");
            assertThat(css.statusCode()).isEqualTo(200);

            assertThat(network.nonLocalAttempts())
                    .as("no outbound connection to any non-loopback host during startup or use")
                    .isEmpty();
        }
    }

    @Test
    void unmigratedDatabaseIsAliveButVisiblyNotReady() {
        try (ConfigurableApplicationContext app = ProcessLauncher.startWeb(db.installationConfig(), Map.of())) {
            assertThat(Http.get(app, "/actuator/health/liveness").statusCode()).isEqualTo(200);

            HttpResponse<String> readiness = Http.get(app, "/actuator/health/readiness");
            assertThat(readiness.statusCode()).isEqualTo(503);
            assertThat(readiness.body()).contains("OUT_OF_SERVICE");

            HttpResponse<String> home = Http.get(app, "/");
            assertThat(home.statusCode()).isEqualTo(200);
            assertThat(home.body()).contains("Archive instance is not ready", "Run the &#39;migrate&#39; command");
        }
    }

    @Test
    void unreachableDatabaseIsReportedNotHidden() {
        // Port 1 on loopback: nothing listens, connection is refused immediately.
        DatabaseSettings unreachable = new DatabaseSettings("jdbc:postgresql://127.0.0.1:1/gas", "gas", Secret.of("x"), 2);
        try (ConfigurableApplicationContext app = ProcessLauncher.startWeb(TestDatabase.installationConfig(unreachable), Map.of())) {
            assertThat(Http.get(app, "/actuator/health/liveness").statusCode()).isEqualTo(200);
            assertThat(Http.get(app, "/actuator/health/readiness").statusCode()).isEqualTo(503);
            assertThat(Http.get(app, "/").body()).contains("Archive instance is not ready", "Unavailable");
        }
    }

    @Test
    void onlyHealthIsExposedAmongManagementEndpoints() {
        migrate();
        try (ConfigurableApplicationContext app = ProcessLauncher.startWeb(db.installationConfig(), Map.of())) {
            assertThat(Http.get(app, "/actuator/env").statusCode()).isEqualTo(404);
            assertThat(Http.get(app, "/actuator/configprops").statusCode()).isEqualTo(404);
            assertThat(Http.get(app, "/actuator/beans").statusCode()).isEqualTo(404);
        }
    }
}
