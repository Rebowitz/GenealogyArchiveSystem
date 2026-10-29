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

/** GAS-P1-002: check-config and serve refuse a plain-HTTP network deployment, without exposing secrets. */
class CliTransportSecurityTest {

    private static final String SECRET = "Pw-must-not-leak-9X4";

    @TempDir
    Path temp;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private Cli.Outcome run(String bindAndProxyYaml, String command) throws IOException {
        Path config = Files.writeString(temp.resolve("installation.yml"), """
                database:
                  url: jdbc:postgresql://127.0.0.1:1/never-contacted
                  username: gas
                server:
                %s
                """.formatted(bindAndProxyYaml.indent(2).stripTrailing()));
        Cli cli = new Cli(Map.of("GAS_DB_PASSWORD", SECRET), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return cli.run(new String[]{command, "--config=" + config});
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void checkConfigRefusesWildcardIpv4BindingWithoutProxy() throws IOException {
        assertThat(run("bindAddress: 0.0.0.0", "check-config").exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(output()).contains("'0.0.0.0' is not a loopback address").doesNotContain(SECRET);
    }

    @Test
    void checkConfigRefusesWildcardIpv6BindingWithoutProxy() throws IOException {
        assertThat(run("bindAddress: '::'", "check-config").exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(output()).contains("is not a loopback address").doesNotContain(SECRET);
    }

    @Test
    void serveRefusesToStartOnALanAddressWithoutProxy() throws IOException {
        // Refused before any listener or database connection is attempted.
        assertThat(run("bindAddress: 192.168.1.20", "serve").exitCode()).isEqualTo(Cli.EXIT_CONFIG_INVALID);
        assertThat(output()).contains("'192.168.1.20' is not a loopback address").doesNotContain(SECRET);
    }

    @Test
    void checkConfigAcceptsLoopbackIpv4AndIpv6() throws IOException {
        assertThat(run("bindAddress: 127.0.0.1", "check-config").exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(run("bindAddress: '::1'", "check-config").exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(output()).doesNotContain(SECRET);
    }

    @Test
    void checkConfigAcceptsNetworkBindingBehindADeclaredTlsReverseProxy() throws IOException {
        Cli.Outcome outcome = run("""
                bindAddress: 0.0.0.0
                tlsReverseProxy:
                  addresses: ['192.168.1.5']""", "check-config");
        assertThat(outcome.exitCode()).isEqualTo(Cli.EXIT_OK);
        assertThat(output()).contains("tlsReverseProxy   192.168.1.5").doesNotContain(SECRET);
    }
}
