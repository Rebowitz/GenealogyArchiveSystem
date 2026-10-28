package org.genealogyarchive.boot;

import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfig.DatabaseSettings;
import org.genealogyarchive.config.InstallationConfig.LogFormat;
import org.genealogyarchive.config.InstallationConfig.LoggingSettings;
import org.genealogyarchive.config.InstallationConfig.ServerSettings;
import org.genealogyarchive.config.Secret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Installation configuration is translated into process settings in exactly one place. */
class ProcessLauncherTest {

    private static InstallationConfig config(LogFormat format, Optional<Path> logDir) {
        return new InstallationConfig(Path.of("installation.yml"),
                new DatabaseSettings("jdbc:postgresql://localhost/gas", "gas", Secret.of("pw-not-a-property"), 5),
                new ServerSettings("0.0.0.0", 8123),
                new LoggingSettings(format, logDir),
                Map.of());
    }

    @Test
    void jsonLoggingToConsoleAndRollingFile(@TempDir Path logs) {
        Map<String, Object> p = ProcessLauncher.installationProperties(config(LogFormat.JSON, Optional.of(logs)), "web");

        assertThat(p).containsEntry("gas.process", "web")
                .containsEntry("server.address", "0.0.0.0")
                .containsEntry("server.port", 8123)
                .containsEntry("logging.structured.format.console", "ecs")
                .containsEntry("logging.structured.format.file", "ecs")
                .containsEntry("logging.file.name", logs.resolve("genealogy-archive-web.log").toString());
    }

    @Test
    void textLoggingIsPlainConsole() {
        Map<String, Object> p = ProcessLauncher.installationProperties(config(LogFormat.TEXT, Optional.empty()), "worker");
        assertThat(p).doesNotContainKey("logging.structured.format.console").doesNotContainKey("logging.file.name");
    }

    @Test
    void secretsAreNeverTurnedIntoProperties() {
        Map<String, Object> p = ProcessLauncher.installationProperties(config(LogFormat.JSON, Optional.empty()), "web");
        assertThat(p.values()).noneMatch(v -> String.valueOf(v).contains("pw-not-a-property"));
    }
}
