package org.genealogyarchive.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GAS-P1-002 / owner decision P1-C: the transport boundary for password sign-in. */
class TransportSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.0.0.2", "localhost", "LOCALHOST", "::1", "[::1]", "0:0:0:0:0:0:0:1"})
    void loopbackBindingsWithoutAProxyAreAccepted(String bind) {
        assertThat(TransportSecurity.isLoopbackBinding(bind)).isTrue();
        assertThat(TransportSecurity.forDeployment(bind, List.of()).behindTlsReverseProxy()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "::", "0:0:0:0:0:0:0:0", "[::]", "192.168.1.20", "10.0.0.5", "fe80::1",
            "2001:db8::10", "archive.example.org", "archive-host"})
    void nonLoopbackAndWildcardBindingsWithoutAProxyAreRefused(String bind) {
        assertThat(TransportSecurity.isLoopbackBinding(bind)).isFalse();
        assertThatThrownBy(() -> TransportSecurity.forDeployment(bind, List.of()))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("is not a loopback address")
                .hasMessageContaining("server.tlsReverseProxy.addresses");
    }

    @Test
    void absentBindAddressMeansAllInterfacesAndIsRefused() {
        assertThatThrownBy(() -> TransportSecurity.forDeployment(null, List.of()))
                .isInstanceOf(ConfigValidationException.class).hasMessageContaining("all interfaces");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "::", "192.168.1.20"})
    void nonLoopbackBindingWithADeclaredTlsReverseProxyIsAccepted(String bind) {
        assertThat(TransportSecurity.forDeployment(bind, List.of("192.168.1.5", "2001:db8::5")).behindTlsReverseProxy()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"proxy.example.org", "10.0.0.0/8", "0.0.0.0", "::", "256.1.1.1", "fe80::1%eth0", "224.0.0.1", ""})
    void proxyAddressesMustBeExactUnicastIpLiterals(String proxy) {
        assertThatThrownBy(() -> TransportSecurity.forDeployment("127.0.0.1", List.of(proxy)))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("server.tlsReverseProxy.addresses");
    }

    @Test
    void directLoopbackRequestsArePermittedWithoutAProxy() {
        TransportSecurity policy = TransportSecurity.forDeployment("127.0.0.1", List.of());
        assertThat(policy.permitsPasswordTraffic("127.0.0.1", List.of(), false)).isTrue();
        assertThat(policy.permitsPasswordTraffic("0:0:0:0:0:0:0:1", List.of(), false)).as("IPv6 loopback").isTrue();
    }

    @Test
    void forwardingHeadersFromAnAddressThatIsNotADeclaredProxyAreIgnored() {
        TransportSecurity policy = TransportSecurity.forDeployment("0.0.0.0", List.of("192.168.1.5"));
        assertThat(policy.permitsPasswordTraffic("192.168.1.77", List.of("https"), false)).as("spoofed IPv4 client").isFalse();
        assertThat(policy.permitsPasswordTraffic("2001:db8::77", List.of("https"), false)).as("spoofed IPv6 client").isFalse();
        assertThat(policy.permitsPasswordTraffic("192.168.1.77", List.of(), false)).as("plain LAN client").isFalse();
    }

    @Test
    void declaredProxyMustAssertHttpsExactlyOnce() {
        TransportSecurity policy = TransportSecurity.forDeployment("0.0.0.0", List.of("192.168.1.5", "2001:db8::5"));
        assertThat(policy.permitsPasswordTraffic("192.168.1.5", List.of("https"), false)).isTrue();
        assertThat(policy.permitsPasswordTraffic("2001:db8:0:0:0:0:0:5", List.of("HTTPS"), false)).as("IPv6 proxy").isTrue();
        assertThat(policy.permitsPasswordTraffic("192.168.1.5", List.of(), false)).as("no header").isFalse();
        assertThat(policy.permitsPasswordTraffic("192.168.1.5", List.of("http"), false)).as("http").isFalse();
        assertThat(policy.permitsPasswordTraffic("192.168.1.5", List.of("https", "http"), false)).as("two headers").isFalse();
        assertThat(policy.permitsPasswordTraffic("192.168.1.5", List.of("https,http"), false)).as("list value").isFalse();
    }

    @Test
    void loopbackProxyMeansLocalTrafficMustAlsoComeThroughTheProxy() {
        TransportSecurity policy = TransportSecurity.forDeployment("127.0.0.1", List.of("127.0.0.1"));
        assertThat(policy.permitsPasswordTraffic("127.0.0.1", List.of(), false)).isFalse();
        assertThat(policy.permitsPasswordTraffic("127.0.0.1", List.of("https"), false)).isTrue();
    }

    @Test
    void unparseablePeerAddressIsRefused() {
        TransportSecurity policy = TransportSecurity.forDeployment("127.0.0.1", List.of());
        assertThat(policy.permitsPasswordTraffic("not-an-address", List.of("https"), false)).isFalse();
        assertThat(policy.permitsPasswordTraffic(null, List.of(), false)).isFalse();
    }
}
