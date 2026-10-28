package org.genealogyarchive.config;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Installation URLs contain connection options, never credentials or driver extension code. */
public final class JdbcUrls {
    private static final Set<String> OPTIONS = Set.of(
            "sslmode", "sslrootcert", "connecttimeout", "sockettimeout", "tcpkeepalive",
            "targetservertype", "loadbalancehosts", "hostrecheckseconds");

    private JdbcUrls() { }

    /** Never include rejected URL text in an exception: it may contain a secret. */
    public static void requireSafe(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("database.url must be a PostgreSQL JDBC URL starting with 'jdbc:postgresql://'");
        }
        try {
            String location = url.substring("jdbc:postgresql://".length()).split("\\?", 2)[0];
            String authority = location.split("/", 2)[0];
            if (URLDecoder.decode(authority, StandardCharsets.UTF_8).contains("@") || url.contains("#")) {
                throw new IllegalArgumentException();
            }
            int query = url.indexOf('?');
            if (query >= 0) {
                for (String part : url.substring(query + 1).split("&", -1)) {
                    String[] pair = part.split("=", 2);
                    String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (pair.length != 2 || !OPTIONS.contains(key)) {
                        throw new IllegalArgumentException();
                    }
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("database.url contains credentials, malformed encoding, or unsupported options; "
                    + "use database.username and an environment/protected-file password. "
                    + "Supported URL options: sslmode, sslrootcert, connectTimeout, socketTimeout, tcpKeepAlive, "
                    + "targetServerType, loadBalanceHosts, hostRecheckSeconds.");
        }
    }

    /** Diagnostics deliberately omit query values, even for permitted options. */
    public static String diagnostic(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?[options omitted]";
    }
}
