package org.genealogyarchive.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Installation configuration: settings that belong to THIS host and never travel with the archive
 * (BUILD-SPEC s.55). Database connection, secrets, server binding, log directory and the native
 * filesystem roots of Storage Volumes live here.
 * <p>
 * Archive configuration (Archive ID, vocabularies, classifications, archive preferences) is a
 * different thing: it is stored in the archive database and travels through backup, export and
 * Transfer Packages. It is introduced in Phase 1 and must never be added to this record.
 *
 * @param sourceFile    file this configuration was read from (diagnostics only)
 * @param database      PostgreSQL connection settings
 * @param server        HTTP binding
 * @param logging       diagnostic log settings
 * @param storageRoots  Storage Volume ID -> absolute native root path on this host. The map key is
 *                      the portable identity; the path is host-local and is never persisted as
 *                      archival data.
 */
public record InstallationConfig(
        Path sourceFile,
        DatabaseSettings database,
        ServerSettings server,
        LoggingSettings logging,
        Map<UUID, Path> storageRoots) {

    public InstallationConfig {
        storageRoots = Map.copyOf(storageRoots);
    }

    /** PostgreSQL connection settings. The password is held as a masked {@link Secret}. */
    public record DatabaseSettings(String jdbcUrl, String username, Secret password, int maxPoolSize) {
        public DatabaseSettings {
            JdbcUrls.requireSafe(jdbcUrl);
        }

        @Override
        public String toString() {
            return "DatabaseSettings[jdbcUrl=" + JdbcUrls.diagnostic(jdbcUrl)
                    + ", username=" + username + ", password=******, maxPoolSize=" + maxPoolSize + "]";
        }
    }

    /**
     * HTTP listener settings. Default binding is loopback-only. A non-loopback binding is accepted only
     * with a declared TLS reverse proxy (see {@link TransportSecurity}).
     *
     * @param tlsReverseProxyAddresses exact IP addresses of the TLS reverse proxy; empty when none
     */
    public record ServerSettings(String bindAddress, int port, List<String> tlsReverseProxyAddresses) {

        public ServerSettings {
            tlsReverseProxyAddresses = tlsReverseProxyAddresses == null ? List.of() : List.copyOf(tlsReverseProxyAddresses);
        }

        /** Settings without a TLS reverse proxy. */
        public ServerSettings(String bindAddress, int port) {
            this(bindAddress, port, List.of());
        }
    }

    /** Diagnostic logging: console always; optional rolling file in {@code directory}. */
    public record LoggingSettings(LogFormat format, Optional<Path> directory) {
    }

    public enum LogFormat {
        /** One JSON object per line (Elastic Common Schema field names). Default for services. */
        JSON,
        /** Human-readable lines for interactive development. */
        TEXT
    }
}
