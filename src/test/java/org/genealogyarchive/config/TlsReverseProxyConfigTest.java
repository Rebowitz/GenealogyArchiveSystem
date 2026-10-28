package org.genealogyarchive.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GAS-P1-002: explicit, validated declaration of the trusted TLS reverse proxy. */
class TlsReverseProxyConfigTest {

    private static final String SECRET = "Pw-must-not-leak-7Q2";
    private static final Map<String, String> ENV = Map.of("GAS_DB_PASSWORD", SECRET);
    private static final Path SOURCE = Path.of("installation.yml");

    private static Map<String, Object> root(Map<String, Object> server) {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("url", "jdbc:postgresql://localhost:5432/gas");
        db.put("username", "gas");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("database", db);
        root.put("server", server);
        return root;
    }

    private static Map<String, Object> server(String bind, Object proxySection) {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("bindAddress", bind);
        if (proxySection != null) {
            server.put("tlsReverseProxy", proxySection);
        }
        return server;
    }

    @Test
    void noProxyDeclaredMeansNoAddressIsTrusted() {
        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root(server("127.0.0.1", null)), ENV);
        assertThat(config.server().tlsReverseProxyAddresses()).isEmpty();
        assertThat(TransportSecurity.problems(config.server())).isEmpty();
    }

    @Test
    void declaredProxyAddressesAreLoadedAndMakeANetworkBindingAcceptable() {
        InstallationConfig config = InstallationConfigLoader.parse(SOURCE,
                root(server("0.0.0.0", Map.of("addresses", List.of("192.168.1.5", "2001:db8::5")))), ENV);
        assertThat(config.server().tlsReverseProxyAddresses()).containsExactly("192.168.1.5", "2001:db8::5");
        assertThat(TransportSecurity.problems(config.server())).isEmpty();
    }

    @Test
    void networkBindingWithoutProxyLoadsButIsReportedAsATransportProblem() {
        InstallationConfig config = InstallationConfigLoader.parse(SOURCE, root(server("0.0.0.0", null)), ENV);
        assertThat(TransportSecurity.problems(config.server())).singleElement().asString()
                .contains("'0.0.0.0' is not a loopback address")
                .doesNotContain(SECRET);
    }

    @Test
    void invalidProxyDeclarationsAreConfigurationErrorsWithoutSecrets() {
        for (Object section : List.of(
                Map.of("addresses", List.of("proxy.example.org")),
                Map.of("addresses", List.of("10.0.0.0/8")),
                Map.of("addresses", List.of()),
                Map.of("addresses", "192.168.1.5"),
                Map.of("addresses", List.of("192.168.1.5"), "trustAll", true))) {
            assertThatThrownBy(() -> InstallationConfigLoader.parse(SOURCE, root(server("0.0.0.0", section)), ENV))
                    .as("%s", section)
                    .isInstanceOf(ConfigValidationException.class)
                    .hasMessageContaining("server.tlsReverseProxy")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(SECRET));
        }
    }
}
