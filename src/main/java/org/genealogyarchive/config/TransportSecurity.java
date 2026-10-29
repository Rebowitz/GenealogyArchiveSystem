package org.genealogyarchive.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The transport-security boundary for password sign-in (owner decision P1-C, correction GAS-P1-002).
 * <p>
 * Phase 1 has no built-in TLS. Password sign-in is therefore permitted only over transports that do
 * not cross the network in clear text:
 * <ul>
 *   <li><b>Deployment rule.</b> The web listener may bind to a loopback address ({@code 127.0.0.0/8},
 *       {@code ::1}, {@code localhost}). Any other binding - a LAN address, a host name, or a wildcard
 *       such as {@code 0.0.0.0} or {@code ::} - is refused unless the installation explicitly declares
 *       the TLS reverse proxy in front of it ({@code server.tlsReverseProxy.addresses}).</li>
 *   <li><b>Request rule.</b> A request is accepted if it arrives over TLS terminated by this server
 *       (later phases), or comes from a declared proxy address and carries exactly one
 *       {@code X-Forwarded-Proto: https} header, or comes directly from a loopback address that is
 *       not a declared proxy. Forwarding headers from any other address are ignored: they can never
 *       make a request count as secure.</li>
 * </ul>
 * Proxy addresses are exact IP literals only (no host names, ranges or wildcards), so trust never
 * depends on DNS. Messages name the offending setting and value; they never contain secrets.
 */
public final class TransportSecurity {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

    private final Set<InetAddress> trustedProxies;

    private TransportSecurity(Set<InetAddress> trustedProxies) {
        this.trustedProxies = Set.copyOf(trustedProxies);
    }

    /**
     * Builds the policy for an effective bind address and declared proxy addresses.
     *
     * @throws ConfigValidationException if the deployment would expose password sign-in over plain HTTP,
     *                                   or a proxy address is not a usable IP literal
     */
    public static TransportSecurity forDeployment(String effectiveBindAddress, List<String> proxyAddresses) {
        List<String> problems = new ArrayList<>();
        Set<InetAddress> proxies = parseProxies(proxyAddresses, problems);
        if (problems.isEmpty() && proxies.isEmpty() && !isLoopbackBinding(effectiveBindAddress)) {
            problems.add(nonLoopbackProblem(effectiveBindAddress));
        }
        if (!problems.isEmpty()) {
            throw new ConfigValidationException(problems);
        }
        return new TransportSecurity(proxies);
    }

    /** Problems with these server settings (empty when acceptable). Used by check-config and serve. */
    public static List<String> problems(InstallationConfig.ServerSettings server) {
        try {
            forDeployment(server.bindAddress(), server.tlsReverseProxyAddresses());
            return List.of();
        } catch (ConfigValidationException e) {
            return e.problems();
        }
    }

    /** Problems with declared proxy addresses alone (used while loading the configuration file). */
    static List<String> proxyAddressProblems(List<String> proxyAddresses) {
        List<String> problems = new ArrayList<>();
        parseProxies(proxyAddresses, problems);
        return problems;
    }

    static String nonLoopbackProblem(String bindAddress) {
        return "server.bindAddress '" + (bindAddress == null ? "(all interfaces)" : bindAddress)
                + "' is not a loopback address. Password sign-in over unprotected HTTP on a network is not"
                + " permitted. Bind to 127.0.0.1 or ::1, or put a TLS reverse proxy in front of this server and"
                + " declare its IP address under server.tlsReverseProxy.addresses.";
    }

    /**
     * True only for loopback bindings. Wildcards ({@code 0.0.0.0}, {@code ::}), LAN addresses, host
     * names other than {@code localhost}, and an absent address (which binds every interface) are not.
     */
    public static boolean isLoopbackBinding(String bindAddress) {
        if (bindAddress == null) {
            return false;
        }
        String text = bindAddress.trim();
        if (text.equalsIgnoreCase("localhost")) {
            return true;
        }
        return ipLiteral(text).map(InetAddress::isLoopbackAddress).orElse(false);
    }

    /**
     * Whether a request may carry password sign-in and signed-in traffic.
     *
     * @param remoteAddress        the TCP peer address as seen by the server (never a header value)
     * @param forwardedProtoValues every {@code X-Forwarded-Proto} header value, in order
     * @param tlsTerminatedHere    true if the connection itself is TLS (not available in Phase 1)
     */
    public boolean permitsPasswordTraffic(String remoteAddress, List<String> forwardedProtoValues, boolean tlsTerminatedHere) {
        if (tlsTerminatedHere) {
            return true;
        }
        Optional<InetAddress> peer = ipLiteral(remoteAddress);
        if (peer.isEmpty()) {
            return false;
        }
        if (trustedProxies.contains(peer.get())) {
            return forwardedProtoValues.size() == 1
                    && forwardedProtoValues.get(0).trim().equalsIgnoreCase("https");
        }
        return peer.get().isLoopbackAddress();
    }

    public boolean behindTlsReverseProxy() {
        return !trustedProxies.isEmpty();
    }

    private static Set<InetAddress> parseProxies(List<String> addresses, List<String> problems) {
        Set<InetAddress> result = new LinkedHashSet<>();
        if (addresses == null) {
            return result;
        }
        for (String text : addresses) {
            Optional<InetAddress> address = ipLiteral(text);
            if (address.isEmpty()) {
                problems.add("server.tlsReverseProxy.addresses: '" + text
                        + "' is not an IP address (use the proxy's exact IPv4 or IPv6 address; host names and ranges are not accepted)");
            } else if (address.get().isAnyLocalAddress() || address.get().isMulticastAddress()) {
                problems.add("server.tlsReverseProxy.addresses: '" + text + "' is a wildcard or multicast address, not a proxy");
            } else {
                result.add(address.get());
            }
        }
        return result;
    }

    /** Parses an IPv4 or IPv6 literal without any name lookup; anything else is empty. */
    static Optional<InetAddress> ipLiteral(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim();
        if (t.startsWith("[") && t.endsWith("]")) {
            t = t.substring(1, t.length() - 1);
        }
        boolean v4 = IPV4.matcher(t).matches();
        boolean v6 = !v4 && IPV6.matcher(t).matches();
        if (!v4 && !v6) {
            return Optional.empty(); // host names, ranges, zone IDs: never resolved
        }
        if (v4) {
            for (String part : t.split("\\.")) {
                if (Integer.parseInt(part) > 255) {
                    return Optional.empty();
                }
            }
        }
        try {
            // A syntactically literal address is parsed, not looked up.
            return Optional.of(InetAddress.getByName(t.toLowerCase(Locale.ROOT)));
        } catch (UnknownHostException | SecurityException e) {
            return Optional.empty();
        }
    }
}
