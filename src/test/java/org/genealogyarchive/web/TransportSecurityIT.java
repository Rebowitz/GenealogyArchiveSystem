package org.genealogyarchive.web;

import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.ConfigValidationException;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfig.ServerSettings;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.Http;
import org.genealogyarchive.testsupport.TestDatabase;
import org.genealogyarchive.testsupport.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GAS-P1-002 against the real web application: a plain-HTTP network deployment is refused at start-up;
 * a declared TLS reverse proxy is honoured only for its own address; forwarding headers from other
 * addresses never make a request acceptable.
 */
class TransportSecurityIT {

    private static final HttpClient CLIENT = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();

    private TestDatabase db;
    private ConfigurableApplicationContext app;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ArchiveFixtures.migrateAndInitialize(db);
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.close();
        }
        db.close();
    }

    private InstallationConfig withServer(ServerSettings server) {
        InstallationConfig base = db.installationConfig();
        return new InstallationConfig(base.sourceFile(), base.database(), server, base.logging(), base.storageRoots());
    }

    private static HttpResponse<String> send(String host, int port, String method, String path, Map<String, String> headers,
                                             String formBody) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + path)).timeout(Duration.ofSeconds(60));
        headers.forEach(b::header);
        if (formBody == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/x-www-form-urlencoded").method(method, HttpRequest.BodyPublishers.ofString(formBody));
        }
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String sessionCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID="))
                .findFirst().orElseThrow(() -> new AssertionError("no session cookie"));
    }

    @Test
    void wildcardIpv4BindingWithoutProxyIsRefusedAtStartup() {
        assertThatThrownBy(() -> app = ProcessLauncher.startWeb(withServer(new ServerSettings("0.0.0.0", 0)), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("'0.0.0.0' is not a loopback address")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(db.cliEnvironment().get("GAS_DB_PASSWORD")));
        assertThat(app).isNull();
    }

    @Test
    void wildcardIpv6BindingWithoutProxyIsRefusedAtStartup() {
        assertThatThrownBy(() -> app = ProcessLauncher.startWeb(withServer(new ServerSettings("::", 0)), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("'::' is not a loopback address");
        assertThat(app).isNull();
    }

    @Test
    void overridingTheBindAddressCannotBypassTheStartupCheck() {
        assertThatThrownBy(() -> app = ProcessLauncher.startWeb(db.installationConfig(), Map.of("server.address", "0.0.0.0")))
                .isInstanceOf(ConfigValidationException.class);
        assertThat(app).isNull();
    }

    @Test
    void ipv6LoopbackBindingServesSignInPages() throws Exception {
        app = ProcessLauncher.startWeb(withServer(new ServerSettings("::1", 0)), Map.of());
        HttpResponse<String> login = send("[::1]", Http.port(app), "GET", "/login", Map.of(), null);
        assertThat(login.statusCode()).isEqualTo(200);
    }

    @Test
    void declaredTlsReverseProxyIsHonouredOnlyWhenItForwardsHttps() throws Exception {
        ArchiveFixtures.createUser(db, "proxied-admin", Role.ADMINISTRATOR);
        // The proxy runs on this computer, so loopback traffic must come through it.
        app = ProcessLauncher.startWeb(withServer(new ServerSettings("127.0.0.1", 0, List.of("127.0.0.1"))), Map.of());
        int port = Http.port(app);
        Map<String, String> viaProxy = Map.of("X-Forwarded-Proto", "https");

        HttpResponse<String> direct = send("127.0.0.1", port, "GET", "/login", Map.of(), null);
        assertThat(direct.statusCode()).as("bypassing the proxy").isEqualTo(403);
        assertThat(send("127.0.0.1", port, "GET", "/login", Map.of("X-Forwarded-Proto", "http"), null).statusCode())
                .as("proxy forwarding plain HTTP").isEqualTo(403);

        HttpResponse<String> loginPage = send("127.0.0.1", port, "GET", "/login", viaProxy, null);
        assertThat(loginPage.statusCode()).isEqualTo(200);
        String cookie = sessionCookie(loginPage);
        assertThat(cookie).as("session cookie is HTTPS-only behind the proxy").containsIgnoringCase("Secure");
        String form = "_csrf=" + URLEncoder.encode(WebSession.csrfToken(loginPage.body()), StandardCharsets.UTF_8)
                + "&username=proxied-admin&password=" + URLEncoder.encode(ArchiveFixtures.PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> signIn = send("127.0.0.1", port, "POST", "/login",
                Map.of("X-Forwarded-Proto", "https", "Cookie", cookie.split(";", 2)[0]), form);
        assertThat(signIn.statusCode()).isEqualTo(302);
        assertThat(signIn.headers().firstValue("Location").orElse("")).doesNotContain("error");
        String signedIn = sessionCookie(signIn).split(";", 2)[0];

        assertThat(send("127.0.0.1", port, "GET", "/archive", Map.of("X-Forwarded-Proto", "https", "Cookie", signedIn), null)
                .statusCode()).isEqualTo(200);
        assertThat(send("127.0.0.1", port, "GET", "/archive", Map.of("Cookie", signedIn), null).statusCode())
                .as("a signed-in session cannot be used outside the proxy").isEqualTo(403);
    }

    @Test
    void spoofedForwardingHeadersFromANetworkClientDoNotMakeItAcceptable() throws Exception {
        String lanAddress = nonLoopbackIpv4();
        // Network binding behind a declared proxy that is NOT the client used here (TEST-NET-1 address).
        app = ProcessLauncher.startWeb(withServer(new ServerSettings("0.0.0.0", 0, List.of("192.0.2.10"))), Map.of());
        int port = Http.port(app);

        HttpResponse<String> spoofed = send(lanAddress, port, "GET", "/login",
                Map.of("X-Forwarded-Proto", "https", "X-Forwarded-For", "192.0.2.10", "Forwarded", "for=192.0.2.10;proto=https"), null);
        assertThat(spoofed.statusCode()).isEqualTo(403);
        assertThat(spoofed.body()).doesNotContain("_csrf").doesNotContain("password");
        assertThat(send(lanAddress, port, "POST", "/login", Map.of("X-Forwarded-Proto", "https"),
                "username=x&password=y").statusCode()).isEqualTo(403);
        assertThat(send(lanAddress, port, "GET", "/actuator/health/liveness", Map.of(), null).statusCode())
                .as("service-manager probe stays reachable").isEqualTo(200);
    }

    /** This host's own non-loopback IPv4 address: requests to it arrive from a non-loopback peer. */
    private static String nonLoopbackIpv4() throws Exception {
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback()) {
                continue;
            }
            for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                    return a.getHostAddress();
                }
            }
        }
        throw new AssertionError("This test needs a non-loopback IPv4 address on the test host");
    }
}
