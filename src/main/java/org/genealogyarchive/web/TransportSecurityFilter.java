package org.genealogyarchive.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.genealogyarchive.config.TransportSecurity;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Refuses every request that did not arrive over an acceptable transport (GAS-P1-002), before
 * sign-in, sessions or any page is processed. Only the service-manager health probes are exempt;
 * they carry no credentials and no archive data. The decision uses the TCP peer address and, only
 * for declared proxy addresses, the {@code X-Forwarded-Proto} header (see {@link TransportSecurity}).
 */
public final class TransportSecurityFilter extends OncePerRequestFilter {

    static final String REFUSAL = "This archive accepts sign-in and archive pages only from this computer or through"
            + " its configured TLS reverse proxy (HTTPS). The connection was refused.";

    private final TransportSecurity policy;

    public TransportSecurityFilter(TransportSecurity policy) {
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> forwardedProto = Collections.list(request.getHeaders("X-Forwarded-Proto"));
        if (isHealthProbe(request) || policy.permitsPasswordTraffic(request.getRemoteAddr(), forwardedProto, request.isSecure())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/plain;charset=UTF-8");
        response.getOutputStream().write(REFUSAL.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isHealthProbe(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "GET".equals(request.getMethod())
                && (path.equals("/actuator/health") || path.startsWith("/actuator/health/"));
    }
}
