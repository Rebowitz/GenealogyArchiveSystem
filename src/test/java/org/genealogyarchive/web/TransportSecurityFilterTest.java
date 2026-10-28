package org.genealogyarchive.web;

import org.genealogyarchive.config.TransportSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** GAS-P1-002: the request-level transport rule, using the real filter with servlet test doubles. */
class TransportSecurityFilterTest {

    private static final TransportSecurity BEHIND_PROXY = TransportSecurity.forDeployment("0.0.0.0", List.of("192.168.1.5"));

    private static MockHttpServletResponse send(TransportSecurity policy, String method, String path, String remote,
                                                String... forwardedProto) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remote);
        for (String value : forwardedProto) {
            request.addHeader("X-Forwarded-Proto", value);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new TransportSecurityFilter(policy).doFilter(request, response, chain);
        if (chain.getRequest() != null) {
            response.setStatus(200); // reached the application
        }
        return response;
    }

    @Test
    void spoofedForwardedProtoFromAnUndeclaredAddressIsRefusedForSignIn() throws Exception {
        MockHttpServletResponse r = send(BEHIND_PROXY, "POST", "/login", "192.168.1.77", "https");
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).isEqualTo(TransportSecurityFilter.REFUSAL);
    }

    @Test
    void spoofedForwardedProtoFromAnUndeclaredIpv6AddressIsRefused() throws Exception {
        assertThat(send(BEHIND_PROXY, "GET", "/login", "2001:db8::77", "https").getStatus()).isEqualTo(403);
    }

    @Test
    void declaredProxyForwardingHttpsReachesTheApplication() throws Exception {
        assertThat(send(BEHIND_PROXY, "POST", "/login", "192.168.1.5", "https").getStatus()).isEqualTo(200);
    }

    @Test
    void declaredProxyForwardingPlainHttpIsRefused() throws Exception {
        assertThat(send(BEHIND_PROXY, "POST", "/login", "192.168.1.5", "http").getStatus()).isEqualTo(403);
        assertThat(send(BEHIND_PROXY, "POST", "/login", "192.168.1.5").getStatus()).isEqualTo(403);
    }

    @Test
    void loopbackRequestsReachTheApplicationOnALoopbackDeployment() throws Exception {
        TransportSecurity loopback = TransportSecurity.forDeployment("127.0.0.1", List.of());
        assertThat(send(loopback, "POST", "/login", "127.0.0.1").getStatus()).isEqualTo(200);
        assertThat(send(loopback, "GET", "/archive", "0:0:0:0:0:0:0:1").getStatus()).isEqualTo(200);
    }

    @Test
    void onlyHealthProbesAreExemptAndTheyCarryNoCredentials() throws Exception {
        assertThat(send(BEHIND_PROXY, "GET", "/actuator/health/readiness", "192.168.1.77").getStatus()).isEqualTo(200);
        assertThat(send(BEHIND_PROXY, "GET", "/", "192.168.1.77").getStatus()).isEqualTo(403);
        assertThat(send(BEHIND_PROXY, "POST", "/actuator/health", "192.168.1.77").getStatus()).isEqualTo(403);
    }
}
