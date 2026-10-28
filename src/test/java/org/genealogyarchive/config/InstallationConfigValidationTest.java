package org.genealogyarchive.config;

import org.genealogyarchive.config.InstallationConfig.LogFormat;
import org.genealogyarchive.platform.HostPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PHASE-0: "configuration validation detects missing required installation configuration";
 * secrets kept out of configuration files (BUILD-SPEC s.55, UP-007).
 */
class InstallationConfigValidationTest {

    private static final Map<String, String> ENV_WITH_PASSWORD = Map.of("GAS_DB_PASSWORD", "s3cr3t-value");
    private static final Path SOURCE = Path.of("installation.yml");

    @TempDir
    Path temp;

    private static Map<String, Object> minimalDatabase() {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("url", "jdbc:postgresql://localhost:5432/gas");
        db.put("username", "gas");
        return db;
    }

    private static Map<String, Object> root(Map<String, Object> db) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("database", db);
        return root;
    }

    /** A host-native absolute path valid on whichever supported host runs the test. */
    private Path nativeAbsolute(String name) {
        return temp.resolve(name).toAbsolutePath();
    }

    @Test
    void minimalValidConfigurationUsesSafeDefaults() {
        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root(minimalDatabase()), ENV_WITH_PASSWORD);

        assertThat(config.database().jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/gas");
        assertThat(config.database().password().reveal()).isEqualTo("s3cr3t-value");
        assertThat(config.server().bindAddress()).as("loopback by default; LAN is deliberate").isEqualTo("127.0.0.1");
        assertThat(config.server().port()).isEqualTo(8080);
        assertThat(config.logging().format()).isEqualTo(LogFormat.JSON);
        assertThat(config.storageRoots()).isEmpty();
    }

    @Test
    void missingDatabaseSectionIsReported() {
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, new LinkedHashMap<>(), ENV_WITH_PASSWORD))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> assertThat(((ConfigValidationException) e).problems())
                        .anyMatch(p -> p.contains("Missing required section 'database'")));
    }

    @Test
    void missingRequiredSettingsAreAllReportedTogether() {
        Map<String, Object> db = new LinkedHashMap<>();
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root(db), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> assertThat(((ConfigValidationException) e).problems())
                        .anyMatch(p -> p.contains("database.url"))
                        .anyMatch(p -> p.contains("database.username"))
                        .anyMatch(p -> p.contains("GAS_DB_PASSWORD")));
    }

    @Test
    void passwordInsideConfigurationFileIsRejected() {
        Map<String, Object> db = minimalDatabase();
        db.put("password", "plain-text-password");
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root(db), ENV_WITH_PASSWORD))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("database.password is not allowed")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("plain-text-password"));
    }

    @Test
    void passwordCanComeFromProtectedFile() throws IOException {
        Path secretFile = Files.writeString(temp.resolve("db-password"), "from-file\r\n");
        Map<String, Object> db = minimalDatabase();
        db.put("passwordFile", secretFile.toAbsolutePath().toString());

        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root(db), Map.of());

        assertThat(config.database().password().reveal()).isEqualTo("from-file");
    }

    @Test
    void passwordCanComeFromNamedEnvironmentVariable() {
        Map<String, Object> db = minimalDatabase();
        db.put("passwordEnv", "ARCHIVE_DB_SECRET");
        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root(db), Map.of("ARCHIVE_DB_SECRET", "x1"));
        assertThat(config.database().password().reveal()).isEqualTo("x1");
    }

    @Test
    void secretsAreMaskedInTextRepresentations() {
        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root(minimalDatabase()), ENV_WITH_PASSWORD);
        assertThat(config.toString()).doesNotContain("s3cr3t-value");
        assertThat(config.database().password().toString()).isEqualTo("******");
    }

    @Test
    void unknownKeysAreErrorsNotSilentlyIgnored() {
        Map<String, Object> root = root(minimalDatabase());
        root.put("archiveId", "0192f0c4-0000-7000-8000-000000000000"); // archive configuration does not belong here
        Map<String, Object> db = minimalDatabase();
        db.put("usrname", "typo");
        root.put("database", db);

        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> assertThat(((ConfigValidationException) e).problems())
                        .anyMatch(p -> p.contains("'archiveId'"))
                        .anyMatch(p -> p.contains("'database.usrname'")));
    }

    @Test
    void nonPostgresUrlIsRejected() {
        Map<String, Object> db = minimalDatabase();
        db.put("url", "jdbc:mysql://localhost/gas");
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root(db), ENV_WITH_PASSWORD))
                .hasMessageContaining("jdbc:postgresql://");
    }

    @Test
    void portOutsideRangeIsRejected() {
        Map<String, Object> root = root(minimalDatabase());
        root.put("server", new LinkedHashMap<>(Map.of("port", 70000)));
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD))
                .hasMessageContaining("server.port");
    }

    @Test
    void storageRootsAcceptHostNativeAbsolutePaths() {
        UUID volume = UUID.fromString("0192f0c4-1111-7000-8000-000000000001");
        Map<String, Object> root = root(minimalDatabase());
        root.put("storageRoots", new LinkedHashMap<>(Map.of(volume.toString(), nativeAbsolute("primary").toString())));

        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD);

        assertThat(config.storageRoots()).containsEntry(volume, nativeAbsolute("primary").normalize());
    }

    /**
     * Cross-platform rule: the same test runs on both hosts and expects the other host's path style
     * to be rejected with a hint, never silently treated as a relative path.
     */
    @Test
    void foreignHostPathStyleIsRejectedWithHint() {
        boolean windows = HostPlatform.current() == HostPlatform.WINDOWS;
        String foreign = windows ? "/srv/genealogy-archive/primary" : "D:\\GenealogyArchive\\primary";
        Map<String, Object> root = root(minimalDatabase());
        root.put("storageRoots", new LinkedHashMap<>(Map.of("0192f0c4-1111-7000-8000-000000000001", foreign)));

        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("must be an absolute path")
                .hasMessageContaining(windows ? "looks like a POSIX path" : "looks like a Windows path");
    }

    @Test
    void relativeStorageRootIsRejected() {
        Map<String, Object> root = root(minimalDatabase());
        root.put("storageRoots", new LinkedHashMap<>(Map.of("0192f0c4-1111-7000-8000-000000000001", "archive/primary")));
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD))
                .hasMessageContaining("must be an absolute path");
    }

    @Test
    void storageRootKeyMustBeVolumeIdentityNotAName() {
        Map<String, Object> root = root(minimalDatabase());
        root.put("storageRoots", new LinkedHashMap<>(Map.of("primary-nas", nativeAbsolute("primary").toString())));
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD))
                .hasMessageContaining("Storage Volume ID (UUID)");
    }

    @Test
    void overlappingVolumeRootsAreRejected() {
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("0192f0c4-1111-7000-8000-000000000001", nativeAbsolute("vol").toString());
        roots.put("0192f0c4-1111-7000-8000-000000000002", nativeAbsolute("vol").resolve("nested").toString());
        Map<String, Object> root = root(minimalDatabase());
        root.put("storageRoots", roots);
        assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root, ENV_WITH_PASSWORD))
                .hasMessageContaining("overlapping roots");
    }

    @Test
    void missingFileIsReportedClearly() {
        Path missing = temp.resolve("does-not-exist.yml");
        assertThatThrownBy(() -> InstallationConfigLoader.load(missing, ENV_WITH_PASSWORD))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void yamlFileWithNativePathsLoads() throws IOException {
        Path logs = nativeAbsolute("logs");
        // Single-quoted YAML scalars keep Windows backslashes literal.
        String yaml = """
                database:
                  url: jdbc:postgresql://localhost:5432/gas
                  username: gas
                server:
                  bindAddress: 0.0.0.0
                  port: 8443
                logging:
                  format: text
                  directory: '%s'
                """.formatted(logs.toString().replace("'", "''"));
        Path file = Files.writeString(temp.resolve("installation.yml"), yaml);

        InstallationConfig config = InstallationConfigLoader.load(file, ENV_WITH_PASSWORD);

        assertThat(config.server().port()).isEqualTo(8443);
        assertThat(config.logging().format()).isEqualTo(LogFormat.TEXT);
        assertThat(config.logging().directory()).contains(logs.normalize());
    }

    @Test
    void shippedExampleConfigurationIsValidOnThisHost() {
        // Maven runs tests from the project root on both hosts.
        InstallationConfig config = InstallationConfigLoader.load(Path.of("config", "installation.example.yml"), ENV_WITH_PASSWORD);
        assertThat(config.database().username()).isEqualTo("genealogy_archive");
        assertThat(config.toString()).doesNotContain("s3cr3t-value");
    }

    @Test
    void utf8ByteOrderMarkFromWindowsEditorsIsAccepted() throws IOException {
        String yaml = "﻿database:\n  url: jdbc:postgresql://localhost:5432/gas\n  username: gas\n";
        Path file = Files.writeString(temp.resolve("bom.yml"), yaml);
        assertThat(InstallationConfigLoader.load(file, ENV_WITH_PASSWORD).database().username()).isEqualTo("gas");
    }

    @Test
    void invalidYamlIsReportedAsConfigurationProblem() throws IOException {
        Path file = Files.writeString(temp.resolve("bad.yml"), "database: [unclosed");
        assertThatThrownBy(() -> InstallationConfigLoader.load(file, ENV_WITH_PASSWORD))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("not valid YAML");
    }

    @Test
    void distinctLexicalRootsAliasingTheSamePhysicalTreeAreRejected() throws Exception {
        Path actual = Files.createDirectory(temp.resolve("actual"));
        Path alias = org.genealogyarchive.testsupport.DirectoryLinks.create(temp.resolve("alias"), actual);
        try {
            Map<String, Object> roots = new LinkedHashMap<>();
            roots.put("0192f0c4-1111-7000-8000-000000000001", actual.resolve("future").toString());
            roots.put("0192f0c4-1111-7000-8000-000000000002", alias.resolve("future/nested").toString());
            Map<String, Object> config = root(minimalDatabase());
            config.put("storageRoots", roots);
            assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, config, ENV_WITH_PASSWORD))
                    .hasMessageContaining("overlapping roots");
        } finally {
            Files.delete(alias);
        }
    }

    @Test
    void unavailableRootRetainsItsHostLocalConfigurationMeaning() {
        Path unavailable = temp.resolve("not-yet-mounted/root");
        if (HostPlatform.current() == HostPlatform.WINDOWS) {
            unavailable = null;
            for (char drive = 'Z'; drive >= 'A'; drive--) {
                Path candidate = Path.of(drive + ":\\");
                if (Files.notExists(candidate)) { unavailable = candidate.resolve("archive"); break; }
            }
            assertThat(unavailable).as("test needs an unused drive letter").isNotNull();
        }
        Map<String, Object> config = root(minimalDatabase());
        UUID id = UUID.fromString("0192f0c4-1111-7000-8000-000000000001");
        config.put("storageRoots", Map.of(id.toString(), unavailable.toString()));
        assertThat(InstallationConfigLoader.parse(SOURCE, config, ENV_WITH_PASSWORD).storageRoots())
                .containsEntry(id, unavailable.normalize());
    }
}
