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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** init-archive and create-admin as administrators run them (owner decision B). */
class CliPhase1IT {

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
        return new Run(outcome.exitCode(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private String config() {
        return "--config=" + configFile;
    }

    @Test
    void initArchiveRequiresAMigratedSchema() {
        Run run = run(db.cliEnvironment(), "init-archive", "--name=Too Early", config());
        assertThat(run.exitCode()).isEqualTo(Cli.EXIT_DATABASE_PROBLEM);
    }

    @Test
    void initArchiveCreatesTheArchiveOnceAndRefusesASecondTime() {
        assertThat(run(db.cliEnvironment(), "migrate", config()).exitCode()).isEqualTo(Cli.EXIT_OK);

        Run first = run(db.cliEnvironment(), "init-archive", "--name", "Andrews Family", config());
        assertThat(first.exitCode()).as(first.all()).isEqualTo(Cli.EXIT_OK);
        Matcher m = Pattern.compile("Archive ID   : ([0-9a-f-]{36})").matcher(first.out());
        assertThat(m.find()).isTrue();
        String archiveId = m.group(1);

        Run second = run(db.cliEnvironment(), "init-archive", "--name=Another", config());
        assertThat(second.exitCode()).isEqualTo(Cli.EXIT_REFUSED);
        assertThat(second.err()).contains("already holds archive " + archiveId);

        Run status = run(db.cliEnvironment(), "status", config());
        assertThat(status.exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(status.out()).contains("Archive ID " + archiveId, "Worker      : NOT running", "Ready       : yes");
    }

    @Test
    void initArchiveWithoutANameIsAUsageError() {
        assertThat(run(db.cliEnvironment(), "init-archive", config()).exitCode()).isEqualTo(Cli.EXIT_USAGE);
    }

    @Test
    void createAdminTakesThePasswordFromTheEnvironmentAndNeverPrintsIt() {
        run(db.cliEnvironment(), "migrate", config());
        run(db.cliEnvironment(), "init-archive", "--name=Admin Test", config());
        Map<String, String> env = new HashMap<>(db.cliEnvironment());
        env.put(Cli.NEW_ADMIN_PASSWORD_ENV, "first-admin-passphrase");

        Run created = run(env, "create-admin", "--username=archivist", "--display-name=The Archivist", config());
        assertThat(created.exitCode()).as(created.all()).isEqualTo(Cli.EXIT_OK);
        assertThat(created.all()).doesNotContain("first-admin-passphrase");

        Run duplicate = run(env, "create-admin", "--username=ARCHIVIST", config());
        assertThat(duplicate.exitCode()).isEqualTo(Cli.EXIT_REFUSED);

        env.put(Cli.NEW_ADMIN_PASSWORD_ENV, "short");
        Run weak = run(env, "create-admin", "--username=other", config());
        assertThat(weak.exitCode()).isEqualTo(Cli.EXIT_REFUSED);
        assertThat(weak.all()).doesNotContain("short\n");
    }

    @Test
    void createAdminWithoutPasswordSourceIsAUsageErrorWhenNotInteractive() {
        run(db.cliEnvironment(), "migrate", config());
        run(db.cliEnvironment(), "init-archive", "--name=No Password", config());
        Run run = run(db.cliEnvironment(), "create-admin", "--username=archivist", config());
        assertThat(run.exitCode()).isEqualTo(Cli.EXIT_USAGE);
        assertThat(run.err()).contains(Cli.NEW_ADMIN_PASSWORD_ENV);
    }
}
