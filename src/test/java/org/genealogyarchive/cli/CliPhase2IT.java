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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** register-volume, init-volume and set-ingestion-volume as administrators run them (P2-C, P2-D). */
class CliPhase2IT {

    @TempDir
    Path temp;

    private TestDatabase db;
    private Path configFile;
    private Path volume;

    @BeforeEach
    void setUp() throws IOException {
        db = TestDatabase.createFresh();
        configFile = Files.writeString(temp.resolve("installation.yml"), db.installationYaml());
        volume = Files.createDirectories(temp.resolve("volume"));
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

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] withConfig = java.util.Arrays.copyOf(args, args.length + 1);
        withConfig[args.length] = "--config=" + configFile;
        Map<String, String> env = db.cliEnvironment();
        Cli.Outcome outcome = new Cli(env, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(withConfig);
        return new Run(outcome.exitCode(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private void mapVolume(String volumeId) throws IOException {
        Files.writeString(configFile, db.installationYaml() + """
                storageRoots:
                  '%s': '%s'
                files:
                  stagingDirectory: '%s'
                """.formatted(volumeId, volume.toAbsolutePath(), temp.resolve("staging").toAbsolutePath()));
    }

    @Test
    void anAdministratorRegistersInitializesAndChoosesTheIngestionVolume() throws IOException {
        assertThat(run("migrate").exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(run("register-volume", "--name=Primary", "--purpose=primary").exitCode())
                .as("refused before init-archive").isEqualTo(Cli.EXIT_DATABASE_PROBLEM);
        assertThat(run("init-archive", "--name=Volume Test").exitCode()).isEqualTo(Cli.EXIT_OK);

        Run registered = run("register-volume", "--name=Primary", "--purpose=primary");
        assertThat(registered.exitCode()).as(registered.all()).isEqualTo(Cli.EXIT_OK);
        Matcher m = Pattern.compile("Volume ID +: ([0-9a-f-]{36})").matcher(registered.out());
        assertThat(m.find()).isTrue();
        String id = m.group(1);

        Run notMapped = run("init-volume", "--volume-id=" + id);
        assertThat(notMapped.exitCode()).as(notMapped.all()).isEqualTo(Cli.EXIT_REFUSED);
        assertThat(run("set-ingestion-volume", "--volume-id=" + id).exitCode())
                .as("an uninitialized volume cannot receive Files").isEqualTo(Cli.EXIT_REFUSED);

        mapVolume(id);
        Run init = run("init-volume", "--volume-id=" + id);
        assertThat(init.exitCode()).as(init.all()).isEqualTo(Cli.EXIT_OK);
        assertThat(Files.readString(volume.resolve(".genealogy-archive-volume"))).contains(id);
        Run again = run("init-volume", "--volume-id=" + id);
        assertThat(again.exitCode()).isEqualTo(Cli.EXIT_REFUSED);
        assertThat(again.all()).contains("was initialized on");

        assertThat(run("set-ingestion-volume", "--volume-id=" + id).exitCode()).isEqualTo(Cli.EXIT_OK);
        Run status = run("status");
        assertThat(status.out()).contains("Ingestion").contains("Primary");
    }

    @Test
    void volumeCommandsReportUsageErrors() {
        assertThat(run("migrate").exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(run("init-archive", "--name=Usage Test").exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(run("register-volume", "--name=Primary").exitCode()).isEqualTo(Cli.EXIT_USAGE);
        assertThat(run("init-volume", "--volume-id=not-a-uuid").exitCode()).isEqualTo(Cli.EXIT_USAGE);
    }
}
