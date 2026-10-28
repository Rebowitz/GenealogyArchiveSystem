package org.genealogyarchive.cli;

import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * The documented administrator procedure end to end: installation file + password from the
 * environment -> migrate -> status (UP-004 foundation; PHASE-0 repeatable migrate command).
 */
class CliDatabaseIT {

    @TempDir
    Path temp;

    private TestDatabase db;
    private Path configFile;

    @BeforeEach
    void setUp() throws IOException {
        db = TestDatabase.createFresh();
        configFile = Files.writeString(temp.resolve("installation.yml"), db.installationYaml());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private record Run(int exitCode, String out, String err) {
        String all() {
            return out + err;
        }
    }

    private Run run(Map<String, String> env, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Cli.Outcome outcome = new Cli(env, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(args);
        assertThat(outcome.runningProcess()).isNull();
        return new Run(outcome.exitCode(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void migrateThenStatusThenRepeatMigrate() {
        Run before = run(db.cliEnvironment(), "status", "--config=" + configFile);
        assertThat(before.exitCode()).isEqualTo(Cli.EXIT_DATABASE_PROBLEM);
        assertThat(before.out()).contains("NOT_INITIALIZED", "Ready       : NO");

        Run first = run(db.cliEnvironment(), "migrate", "--config=" + configFile);
        assertThat(first.exitCode()).as(first.all()).isEqualTo(Cli.EXIT_OK);
        assertThat(first.out()).contains("Schema before: NOT_INITIALIZED", "Post-migration validation passed.");

        Run again = run(db.cliEnvironment(), "migrate", "--config=" + configFile);
        assertThat(again.exitCode()).as(again.all()).isEqualTo(Cli.EXIT_OK);
        assertThat(again.out()).contains("Applied 0 migration(s)");

        Run after = run(db.cliEnvironment(), "status", "--config=" + configFile);
        assertThat(after.exitCode()).as(after.all()).isEqualTo(Cli.EXIT_OK);
        assertThat(after.out()).contains("CURRENT", "Ready       : yes");
    }

    @Test
    void missingPasswordIsAConfigurationError() {
        Run run = run(Map.of(), "migrate", "--config=" + configFile);
        assertThat(run.exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(run.err()).contains("GAS_DB_PASSWORD");
    }

    @Test
    void wrongPasswordIsADatabaseProblemAndIsNotEchoed() {
        String wrong = "Wr0ng-Secret-7f3a9c-cli";
        Run run = run(Map.of("GAS_DB_PASSWORD", wrong), "migrate", "--config=" + configFile);
        assertThat(run.exitCode()).isEqualTo(Cli.EXIT_DATABASE_PROBLEM);
        assertThat(run.all()).doesNotContain(wrong);
    }
}
