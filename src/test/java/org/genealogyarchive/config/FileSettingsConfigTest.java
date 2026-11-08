package org.genealogyarchive.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2 installation settings for staging, cache, upload limit and receiving timeout (B5, requirement 6). */
class FileSettingsConfigTest {

    private static final Map<String, String> ENV = Map.of("GAS_DB_PASSWORD", "s3cr3t-value");
    private static final Path SOURCE = Path.of("installation.yml");

    @TempDir
    Path temp;

    private Map<String, Object> root(Map<String, Object> files, Map<String, Object> roots) {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("url", "jdbc:postgresql://localhost:5432/gas");
        db.put("username", "gas");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("database", db);
        if (roots != null) {
            root.put("storageRoots", roots);
        }
        if (files != null) {
            root.put("files", files);
        }
        return root;
    }

    private String abs(String name) {
        return temp.resolve(name).toAbsolutePath().toString();
    }

    private ConfigValidationException invalid(Map<String, Object> files, Map<String, Object> roots) {
        return (ConfigValidationException) org.assertj.core.api.Assertions.catchThrowable(
                () -> InstallationConfigLoader.parse(SOURCE, root(files, roots), ENV));
    }

    @Test
    void b5_defaultsAre4GiBAnd15MinutesWithNoStaging() {
        InstallationConfig c = InstallationConfigLoader.parse(SOURCE, root(null, null), ENV);
        assertThat(c.files().maxUploadBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
        assertThat(c.files().receivingTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(c.files().stagingDirectory()).isEmpty();
    }

    @Test
    void b5_theLimitAndTimeoutAreConfigurable() {
        Map<String, Object> files = new LinkedHashMap<>();
        files.put("stagingDirectory", abs("staging"));
        files.put("cacheDirectory", abs("cache"));
        files.put("maxUploadBytes", 10L * 1024 * 1024 * 1024);
        files.put("receivingTimeout", "PT45M");
        InstallationConfig c = InstallationConfigLoader.parse(SOURCE, root(files, null), ENV);
        assertThat(c.files().maxUploadBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
        assertThat(c.files().receivingTimeout()).isEqualTo(Duration.ofMinutes(45));
        assertThat(c.files().stagingDirectory()).contains(Path.of(abs("staging")));
    }

    @Test
    void invalidValuesAreReported() {
        Map<String, Object> files = new LinkedHashMap<>();
        files.put("maxUploadBytes", 0);
        files.put("receivingTimeout", "PT10S");
        files.put("stagingDirectory", "relative/staging");
        files.put("surprise", true);
        ConfigValidationException e = invalid(files, null);
        assertThat(e.problems()).anyMatch(p -> p.contains("files.maxUploadBytes"))
                .anyMatch(p -> p.contains("files.receivingTimeout"))
                .anyMatch(p -> p.contains("files.stagingDirectory"))
                .anyMatch(p -> p.contains("surprise"));
    }

    @Test
    void stagingAndCacheMayNotBeInsideAStorageVolumeOrShareADirectory() {
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put(UUID.randomUUID().toString(), abs("volume"));
        Map<String, Object> files = new LinkedHashMap<>();
        files.put("stagingDirectory", abs("volume") + java.io.File.separator + "staging");
        files.put("cacheDirectory", abs("volume") + java.io.File.separator + "staging");
        ConfigValidationException e = invalid(files, roots);
        assertThat(e.problems()).anyMatch(p -> p.contains("files.stagingDirectory"))
                .anyMatch(p -> p.contains("separate"));
    }
}
