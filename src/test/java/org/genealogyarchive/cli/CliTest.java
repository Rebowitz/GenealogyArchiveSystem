package org.genealogyarchive.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Command-line behaviour that needs no database. */
class CliTest {

    @TempDir
    Path temp;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private Cli cli(Map<String, String> env) {
        return new Cli(env, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path writeConfig(String yaml) throws IOException {
        return Files.writeString(temp.resolve("installation.yml"), yaml);
    }

    @Test
    void helpIsShownWithoutCommand() {
        Cli.Outcome outcome = cli(Map.of()).run(new String[0]);
        assertThat(outcome.exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(stdout()).contains("serve", "worker", "migrate", "check-config");
    }

    @Test
    void unknownCommandIsUsageError() {
        assertThat(cli(Map.of()).run(new String[]{"synchronise"}).exitCode()).isEqualTo(Cli.EXIT_USAGE);
        assertThat(stderr()).contains("Unknown command: synchronise");
    }

    @Test
    void unknownOptionIsUsageError() {
        assertThat(cli(Map.of()).run(new String[]{"migrate", "--force"}).exitCode()).isEqualTo(Cli.EXIT_USAGE);
    }

    @Test
    void missingConfigurationLocationIsReported() {
        Cli.Outcome outcome = cli(Map.of()).run(new String[]{"check-config"});
        assertThat(outcome.exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(stderr()).contains("No installation configuration file given", "GAS_CONFIG");
    }

    @Test
    void checkConfigPrintsSummaryWithMaskedPassword() throws IOException {
        Path config = writeConfig("""
                database:
                  url: jdbc:postgresql://localhost:5432/gas
                  username: gas
                """);
        Cli.Outcome outcome = cli(Map.of("GAS_DB_PASSWORD", "very-secret-pw"))
                .run(new String[]{"check-config", "--config=" + config});

        assertThat(outcome.exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(stdout()).contains("Installation configuration is valid", "******");
        assertThat(stdout() + stderr()).doesNotContain("very-secret-pw");
    }

    @Test
    void configLocationMayComeFromEnvironment() throws IOException {
        Path config = writeConfig("""
                database:
                  url: jdbc:postgresql://localhost:5432/gas
                  username: gas
                """);
        Cli.Outcome outcome = cli(Map.of("GAS_CONFIG", config.toString(), "GAS_DB_PASSWORD", "pw"))
                .run(new String[]{"check-config"});
        assertThat(outcome.exitCode()).isEqualTo(Cli.EXIT_OK);
    }

    @Test
    void invalidConfigurationListsEveryProblem() throws IOException {
        Path config = writeConfig("""
                database:
                  url: jdbc:mysql://localhost/gas
                  password: inline-secret
                """);
        Cli.Outcome outcome = cli(Map.of()).run(new String[]{"check-config", "--config", config.toString()});

        assertThat(outcome.exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(stderr()).contains("database.url", "database.username", "database.password is not allowed");
        assertThat(stderr()).doesNotContain("inline-secret");
    }

    @Test
    void secretBearingUrlIsRejectedWithoutDiagnosticDisclosure() throws IOException {
        Path config = writeConfig("""
                database:
                  url: jdbc:postgresql://localhost/gas?%70assword=URL_SECRET
                  username: gas
                """);
        Cli.Outcome result = cli(Map.of("GAS_DB_PASSWORD", "ENV_SECRET"))
                .run(new String[]{"check-config", "--config=" + config});
        assertThat(result.exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(stderr()).contains("database.url").doesNotContain("URL_SECRET", "ENV_SECRET");
        assertThat(stdout()).doesNotContain("URL_SECRET", "ENV_SECRET");
    }

    @Test
    void malformedYamlCannotEchoInlineSecretInParserExcerpt() throws IOException {
        Path config = writeConfig("database: [password: YAML_SECRET");
        Cli.Outcome result = cli(Map.of()).run(new String[]{"check-config", "--config=" + config});
        assertThat(result.exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(stderr()).contains("not valid YAML").doesNotContain("YAML_SECRET");
    }
}
