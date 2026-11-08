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
        Map<UUID, Path> storageRoots,
        FileSettings files) {

    public InstallationConfig {
        storageRoots = Map.copyOf(storageRoots);
        files = files == null ? FileSettings.defaults() : files;
    }

    /** Configuration without a {@code files} section (defaults: ingestion not configured). */
    public InstallationConfig(Path sourceFile, DatabaseSettings database, ServerSettings server, LoggingSettings logging,
                              Map<UUID, Path> storageRoots) {
        this(sourceFile, database, server, logging, storageRoots, FileSettings.defaults());
    }

    /**
     * Host-local file-handling settings (Phase 2).
     *
     * @param stagingDirectory  private directory where uploads are received before the worker promotes
     *                          them into a Storage Volume; ingestion is unavailable until it is set
     * @param cacheDirectory    directory for rebuildable thumbnails; thumbnails are skipped without it
     * @param maxUploadBytes    largest accepted upload, enforced while streaming (default 4 GiB)
     * @param receivingTimeout  how long an upload may make no progress before recovery abandons it;
     *                          a slow but active upload keeps reporting progress and is never abandoned
     */
    public record FileSettings(Optional<Path> stagingDirectory, Optional<Path> cacheDirectory, long maxUploadBytes,
                               java.time.Duration receivingTimeout) {

        public static final long DEFAULT_MAX_UPLOAD_BYTES = 4L * 1024 * 1024 * 1024;
        public static final java.time.Duration DEFAULT_RECEIVING_TIMEOUT = java.time.Duration.ofMinutes(15);

        public FileSettings {
            stagingDirectory = stagingDirectory == null ? Optional.empty() : stagingDirectory;
            cacheDirectory = cacheDirectory == null ? Optional.empty() : cacheDirectory;
        }

        public static FileSettings defaults() {
            return new FileSettings(Optional.empty(), Optional.empty(), DEFAULT_MAX_UPLOAD_BYTES, DEFAULT_RECEIVING_TIMEOUT);
        }
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
