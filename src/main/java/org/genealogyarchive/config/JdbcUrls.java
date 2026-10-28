package org.genealogyarchive.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Installation URLs contain connection options, never credentials or driver extension code.
 * <p>
 * GAS-P0-R01: validation must agree with how the PostgreSQL JDBC driver interprets the URL. PgJDBC
 * matches option NAMES literally and case-sensitively and does not percent-decode them (it decodes
 * only values). The validator therefore never normalises a name: a name is accepted only if it is
 * byte-for-byte one of the canonical PgJDBC spellings in {@link #SUPPORTED_OPTIONS}. Case variants,
 * percent-encoded names, duplicates and non-canonical {@code sslmode} values are rejected instead of
 * being rewritten. The URL is passed to the driver unchanged, so what was approved is what the driver
 * receives.
 */
public final class JdbcUrls {

    /** Canonical option names, spelled exactly as the PostgreSQL JDBC driver defines them. */
    public static final List<String> SUPPORTED_OPTIONS = List.of(
            "sslmode", "sslrootcert", "connectTimeout", "socketTimeout", "tcpKeepAlive",
            "targetServerType", "loadBalanceHosts", "hostRecheckSeconds");

    public static final String SSL_MODE = "sslmode";

    /** Canonical sslmode values (PgJDBC SslMode), required literally - no case or percent variants. */
    public static final Set<String> SSL_MODES = Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

    private static final String PREFIX = "jdbc:postgresql://";

    private JdbcUrls() { }

    /** Why a URL was rejected. Descriptions never contain any part of the rejected URL. */
    enum Reason {
        CREDENTIALS_OR_FRAGMENT("credentials or a fragment in the URL"),
        MALFORMED("malformed option syntax or encoding"),
        NONCANONICAL_OR_UNSUPPORTED_NAME("an option name that is unsupported or not in canonical spelling "
                + "(names are case-sensitive and must not be percent-encoded)"),
        DUPLICATE("a duplicated option"),
        NONCANONICAL_SSLMODE("an sslmode value that is not one of disable, allow, prefer, require, verify-ca, verify-full "
                + "(exact lower-case spelling, not percent-encoded)");

        private final String description;

        Reason(String description) {
            this.description = description;
        }
    }

    /** Never include rejected URL text in an exception: it may contain a secret. */
    public static void requireSafe(String url) {
        approvedOptions(url);
    }

    /**
     * Validates the URL and returns the options it approves: canonical name -> decoded value, as the
     * PostgreSQL JDBC driver will see them. The URL itself is never rewritten.
     *
     * @throws IllegalArgumentException with a message that never echoes the URL or any of its parts
     */
    public static Map<String, String> approvedOptions(String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            throw new IllegalArgumentException("database.url must be a PostgreSQL JDBC URL starting with 'jdbc:postgresql://'");
        }
        String location = url.substring(PREFIX.length()).split("\\?", 2)[0];
        String authority = location.split("/", 2)[0];
        String decodedAuthority;
        try {
            decodedAuthority = URLDecoder.decode(authority, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw rejected(Reason.MALFORMED);
        }
        if (decodedAuthority.contains("@") || url.contains("#")) {
            throw rejected(Reason.CREDENTIALS_OR_FRAGMENT);
        }

        Map<String, String> options = new LinkedHashMap<>();
        int query = url.indexOf('?');
        if (query >= 0) {
            for (String part : url.substring(query + 1).split("&", -1)) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    throw rejected(Reason.MALFORMED);
                }
                String name = part.substring(0, eq);
                String rawValue = part.substring(eq + 1);
                // Exact, literal match: no decoding, no case folding (see class comment).
                if (!SUPPORTED_OPTIONS.contains(name)) {
                    throw rejected(Reason.NONCANONICAL_OR_UNSUPPORTED_NAME);
                }
                if (options.containsKey(name)) {
                    throw rejected(Reason.DUPLICATE);
                }
                if (SSL_MODE.equals(name) && !SSL_MODES.contains(rawValue)) {
                    throw rejected(Reason.NONCANONICAL_SSLMODE);
                }
                String value;
                try {
                    value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    throw rejected(Reason.MALFORMED);
                }
                options.put(name, value);
            }
        }
        return Collections.unmodifiableMap(options);
    }

    private static IllegalArgumentException rejected(Reason reason) {
        return new IllegalArgumentException("database.url rejected: it contains " + reason.description + ". "
                + "Use database.username and an environment/protected-file password for credentials. "
                + "Supported URL options, spelled exactly: " + String.join(", ", SUPPORTED_OPTIONS) + ".");
    }

    /** Diagnostics deliberately omit query values, even for permitted options. */
    public static String diagnostic(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?[options omitted]";
    }
}
