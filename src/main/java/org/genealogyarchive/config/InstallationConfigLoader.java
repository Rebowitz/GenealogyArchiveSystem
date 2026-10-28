package org.genealogyarchive.config;

import org.genealogyarchive.config.InstallationConfig.DatabaseSettings;
import org.genealogyarchive.config.InstallationConfig.LogFormat;
import org.genealogyarchive.config.InstallationConfig.LoggingSettings;
import org.genealogyarchive.config.InstallationConfig.ServerSettings;
import org.genealogyarchive.platform.NativePaths;
import org.genealogyarchive.platform.PhysicalPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Reads and strictly validates the installation configuration file (YAML).
 * <p>
 * Design rules:
 * <ul>
 *   <li>Every problem is collected and reported together; nothing is silently defaulted when it is
 *       required.</li>
 *   <li>Unknown keys are errors, so a misspelt key never silently falls back to a default.</li>
 *   <li>Secrets are never accepted inline in the file. The database password comes from an
 *       environment variable or from a separate protected file.</li>
 *   <li>Native absolute paths are validated against the rules of the host this process runs on.</li>
 * </ul>
 */
public final class InstallationConfigLoader {

    /** Environment variable naming the installation configuration file when --config is not given. */
    public static final String CONFIG_FILE_ENV = "GAS_CONFIG";
    /** Default environment variable holding the database password. */
    public static final String DEFAULT_PASSWORD_ENV = "GAS_DB_PASSWORD";

    static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    static final int DEFAULT_PORT = 8080;
    static final int DEFAULT_MAX_POOL_SIZE = 10;

    private static final Set<String> ROOT_KEYS = Set.of("database", "server", "logging", "storageRoots");
    private static final Set<String> DATABASE_KEYS =
            Set.of("url", "username", "password", "passwordEnv", "passwordFile", "maxPoolSize");
    private static final Set<String> SERVER_KEYS = Set.of("bindAddress", "port", "tlsReverseProxy");
    private static final Set<String> TLS_PROXY_KEYS = Set.of("addresses");
    private static final Set<String> LOGGING_KEYS = Set.of("format", "directory");

    private InstallationConfigLoader() {
    }

    /**
     * Loads and validates the configuration file.
     *
     * @throws ConfigValidationException listing every problem found
     */
    public static InstallationConfig load(Path file, Map<String, String> environment) {
        Path absolute = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new ConfigValidationException(List.of(
                    "Installation configuration file not found: " + absolute));
        }
        Object document;
        try {
            String text = Files.readString(absolute, StandardCharsets.UTF_8);
            // Windows editors (e.g. Notepad) may save UTF-8 with a byte-order mark.
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (IOException e) {
            throw new ConfigValidationException(List.of(
                    "Installation configuration file could not be read: " + absolute + " (" + e.getMessage() + ")"));
        } catch (YAMLException e) {
            throw new ConfigValidationException(List.of(
                    "Installation configuration file is not valid YAML: " + absolute
                            + " (parser detail omitted because it may contain secret values)"));
        }
        if (document == null) {
            document = Map.of();
        }
        if (!(document instanceof Map<?, ?> map)) {
            throw new ConfigValidationException(List.of(
                    "Installation configuration must be a YAML mapping at the top level: " + absolute));
        }
        return parse(absolute, asStringKeyed(map), environment);
    }

    /** Validates an already-parsed configuration tree. Visible for tests. */
    public static InstallationConfig parse(Path sourceFile, Map<String, Object> root, Map<String, String> environment) {
        Problems problems = new Problems();
        problems.rejectUnknown(root, "", ROOT_KEYS);

        DatabaseSettings database = parseDatabase(problems.section(root, "database", true), environment, problems);
        ServerSettings server = parseServer(problems.section(root, "server", false), problems);
        LoggingSettings logging = parseLogging(problems.section(root, "logging", false), problems);
        Map<UUID, Path> storageRoots = parseStorageRoots(problems.section(root, "storageRoots", false), problems);

        problems.throwIfAny();
        return new InstallationConfig(sourceFile, database, server, logging, storageRoots);
    }

    private static DatabaseSettings parseDatabase(Map<String, Object> db, Map<String, String> env, Problems p) {
        if (db == null) {
            return null;
        }
        p.rejectUnknown(db, "database", DATABASE_KEYS);
        String url = p.string(db, "url", "database.url", true);
        if (url != null) {
            try {
                JdbcUrls.requireSafe(url);
            } catch (IllegalArgumentException e) {
                p.add(e.getMessage());
                url = null;
            }
        }
        String username = p.string(db, "username", "database.username", true);
        int maxPool = p.integer(db, "maxPoolSize", "database.maxPoolSize", DEFAULT_MAX_POOL_SIZE, 1, 100);

        if (db.containsKey("password")) {
            p.add("database.password is not allowed: secrets must not be stored in the installation configuration file. "
                    + "Use the " + DEFAULT_PASSWORD_ENV + " environment variable or database.passwordFile.");
        }
        Secret password = resolvePassword(db, env, p);
        return url == null || username == null || password == null ? null
                : new DatabaseSettings(url, username, password, maxPool);
    }

    private static Secret resolvePassword(Map<String, Object> db, Map<String, String> env, Problems p) {
        String passwordFile = p.string(db, "passwordFile", "database.passwordFile", false);
        String passwordEnv = p.string(db, "passwordEnv", "database.passwordEnv", false);
        if (passwordFile != null && passwordEnv != null) {
            p.add("database.passwordFile and database.passwordEnv are mutually exclusive; choose one.");
            return null;
        }
        if (passwordFile != null) {
            Optional<Path> file = p.absoluteNativePath(passwordFile, "database.passwordFile");
            if (file.isEmpty()) {
                return null;
            }
            try {
                String content = Files.readString(file.get(), StandardCharsets.UTF_8);
                String secret = stripLineTerminators(content);
                if (secret.isEmpty()) {
                    p.add("database.passwordFile is empty: " + file.get());
                    return null;
                }
                return Secret.of(secret);
            } catch (IOException e) {
                p.add("database.passwordFile could not be read: " + file.get());
                return null;
            }
        }
        String variable = passwordEnv != null ? passwordEnv : DEFAULT_PASSWORD_ENV;
        String value = env.get(variable);
        if (value == null || value.isEmpty()) {
            p.add("Database password not provided: set the environment variable " + variable
                    + " or configure database.passwordFile.");
            return null;
        }
        return Secret.of(value);
    }

    private static String stripLineTerminators(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) {
            end--;
        }
        return s.substring(0, end);
    }

    private static ServerSettings parseServer(Map<String, Object> server, Problems p) {
        if (server == null) {
            return new ServerSettings(DEFAULT_BIND_ADDRESS, DEFAULT_PORT);
        }
        p.rejectUnknown(server, "server", SERVER_KEYS);
        String bind = p.string(server, "bindAddress", "server.bindAddress", false);
        // Port 0 (ephemeral) is permitted for tests and diagnostics.
        int port = p.integer(server, "port", "server.port", DEFAULT_PORT, 0, 65535);
        List<String> proxies = parseTlsReverseProxy(p.section(server, "tlsReverseProxy", false), p);
        return new ServerSettings(bind != null ? bind : DEFAULT_BIND_ADDRESS, port, proxies);
    }

    /**
     * {@code server.tlsReverseProxy.addresses}: the exact IP addresses of the TLS reverse proxy in front
     * of this server (GAS-P1-002). Only requests from these addresses may assert HTTPS via
     * {@code X-Forwarded-Proto}. Whether the overall deployment is acceptable is decided by
     * {@link TransportSecurity} when the web application is checked or started.
     */
    private static List<String> parseTlsReverseProxy(Map<String, Object> proxy, Problems p) {
        if (proxy == null) {
            return List.of();
        }
        p.rejectUnknown(proxy, "server.tlsReverseProxy", TLS_PROXY_KEYS);
        Object value = proxy.get("addresses");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            p.add("server.tlsReverseProxy.addresses must be a non-empty list of IP addresses");
            return List.of();
        }
        List<String> addresses = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) {
                p.add("server.tlsReverseProxy.addresses entries must be IP addresses written as text");
                return List.of();
            }
            addresses.add(text.strip());
        }
        TransportSecurity.proxyAddressProblems(addresses).forEach(p::add);
        return addresses;
    }

    private static LoggingSettings parseLogging(Map<String, Object> logging, Problems p) {
        if (logging == null) {
            return new LoggingSettings(LogFormat.JSON, Optional.empty());
        }
        p.rejectUnknown(logging, "logging", LOGGING_KEYS);
        LogFormat format = LogFormat.JSON;
        String formatText = p.string(logging, "format", "logging.format", false);
        if (formatText != null) {
            try {
                format = LogFormat.valueOf(formatText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                p.add("logging.format must be 'json' or 'text' (was '" + formatText + "')");
            }
        }
        String directory = p.string(logging, "directory", "logging.directory", false);
        Optional<Path> dir = directory == null ? Optional.empty() : p.absoluteNativePath(directory, "logging.directory");
        return new LoggingSettings(format, dir);
    }

    private static Map<UUID, Path> parseStorageRoots(Map<String, Object> roots, Problems p) {
        Map<UUID, Path> result = new LinkedHashMap<>();
        if (roots == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : roots.entrySet()) {
            String where = "storageRoots." + entry.getKey();
            UUID volumeId;
            try {
                volumeId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                p.add(where + ": key must be a Storage Volume ID (UUID)");
                continue;
            }
            if (!(entry.getValue() instanceof String text) || text.isBlank()) {
                p.add(where + ": value must be the absolute native root path of the volume on this host");
                continue;
            }
            p.absoluteNativePath(text, where).ifPresent(path -> result.put(volumeId, path));
        }
        // Check lexical overlap always and observable physical aliases through existing ancestors.
        // Absent roots remain configurable, not certified for later I/O.
        Map<UUID, Path> physical = new LinkedHashMap<>();
        for (Map.Entry<UUID, Path> entry : result.entrySet()) {
            try {
                PhysicalPaths.existingAncestorPath(entry.getValue())
                        .ifPresent(observed -> physical.put(entry.getKey(), observed));
            } catch (IOException e) {
                p.add("storageRoots." + entry.getKey() + ": physical root could not be validated");
            }
        }
        List<Map.Entry<UUID, Path>> entries = new ArrayList<>(result.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Path a = entries.get(i).getValue();
                Path b = entries.get(j).getValue();
                Path pa = physical.get(entries.get(i).getKey());
                Path pb = physical.get(entries.get(j).getKey());
                if (a.startsWith(b) || b.startsWith(a)
                        || (pa != null && pb != null && (pa.startsWith(pb) || pb.startsWith(pa)))) {
                    p.add("storageRoots: volumes " + entries.get(i).getKey() + " and " + entries.get(j).getKey()
                            + " have overlapping roots (" + a + ", " + b + ")");
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyed(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Map<?, ?> nested) {
                value = asStringKeyed(nested);
            }
            result.put(String.valueOf(e.getKey()), value);
        }
        return result;
    }

    /** Problem collector with small typed accessors. */
    private static final class Problems {
        private final List<String> problems = new ArrayList<>();

        void add(String problem) {
            problems.add(problem);
        }

        void throwIfAny() {
            if (!problems.isEmpty()) {
                throw new ConfigValidationException(problems);
            }
        }

        void rejectUnknown(Map<String, Object> map, String path, Set<String> allowed) {
            Set<String> unknown = new TreeSet<>(map.keySet());
            unknown.removeAll(allowed);
            for (String key : unknown) {
                add("Unknown configuration key '" + (path.isEmpty() ? key : path + "." + key)
                        + "' (allowed: " + String.join(", ", new TreeSet<>(allowed)) + ")");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> section(Map<String, Object> parent, String key, boolean required) {
            Object value = parent.get(key);
            if (value == null) {
                if (required) {
                    add("Missing required section '" + key + "'");
                }
                return null;
            }
            if (!(value instanceof Map<?, ?>)) {
                add("'" + key + "' must be a mapping");
                return null;
            }
            return (Map<String, Object>) value;
        }

        String string(Map<String, Object> map, String key, String path, boolean required) {
            Object value = map.get(key);
            if (value == null) {
                if (required) {
                    add("Missing required setting '" + path + "'");
                }
                return null;
            }
            if (!(value instanceof String s)) {
                add("'" + path + "' must be text");
                return null;
            }
            if (s.isBlank()) {
                if (required) {
                    add("Required setting '" + path + "' is empty");
                }
                return null;
            }
            return s.strip();
        }

        int integer(Map<String, Object> map, String key, String path, int defaultValue, int min, int max) {
            Object value = map.get(key);
            if (value == null) {
                return defaultValue;
            }
            if (!(value instanceof Integer i)) {
                add("'" + path + "' must be a whole number");
                return defaultValue;
            }
            if (i < min || i > max) {
                add("'" + path + "' must be between " + min + " and " + max + " (was " + i + ")");
                return defaultValue;
            }
            return i;
        }

        Optional<Path> absoluteNativePath(String text, String path) {
            NativePaths.Validation v = NativePaths.validateAbsolute(text);
            if (!v.valid()) {
                add(path + ": " + v.problem());
                return Optional.empty();
            }
            return Optional.of(v.path());
        }
    }
}
