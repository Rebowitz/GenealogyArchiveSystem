package org.genealogyarchive.boot;

import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfig.LogFormat;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starts a long-running process (web or worker) from a validated {@link InstallationConfig}.
 * <p>
 * Installation settings are translated into Spring properties in exactly one place (here) and added
 * with highest precedence, before logging is initialised, so the installation file is authoritative
 * for binding address, port and logging.
 */
public final class ProcessLauncher {

    public static final String WEB = "web";
    public static final String WORKER = "worker";

    private ProcessLauncher() {
    }

    public static ConfigurableApplicationContext startWeb(InstallationConfig config, Map<String, Object> overrides) {
        return start(WebApplication.class, WebApplicationType.SERVLET, WEB, config, overrides);
    }

    public static ConfigurableApplicationContext startWorker(InstallationConfig config, Map<String, Object> overrides) {
        return start(WorkerApplication.class, WebApplicationType.NONE, WORKER, config, overrides);
    }

    private static ConfigurableApplicationContext start(Class<?> source, WebApplicationType type, String process,
                                                        InstallationConfig config, Map<String, Object> overrides) {
        Map<String, Object> properties = new LinkedHashMap<>(installationProperties(config, process));
        properties.putAll(overrides);

        SpringApplication app = new SpringApplication(source);
        app.setWebApplicationType(type);
        app.addListeners(new InstallationPropertiesListener(properties));
        app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("installationConfig", config));
        return app.run();
    }

    /** Installation configuration expressed as Spring properties. Visible for tests. */
    static Map<String, Object> installationProperties(InstallationConfig config, String process) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("gas.process", process);
        p.put("server.address", config.server().bindAddress());
        p.put("server.port", config.server().port());
        boolean json = config.logging().format() == LogFormat.JSON;
        if (json) {
            p.put("logging.structured.format.console", "ecs");
        }
        config.logging().directory().ifPresent(dir -> {
            Path file = dir.resolve("genealogy-archive-" + process + ".log");
            p.put("logging.file.name", file.toString());
            if (json) {
                p.put("logging.structured.format.file", "ecs");
            }
            // Size/age-based rolling inside the application, compatible with both hosts.
            p.put("logging.logback.rollingpolicy.max-file-size", "20MB");
            p.put("logging.logback.rollingpolicy.max-history", "30");
        });
        return p;
    }

    /**
     * Adds installation properties first in the environment, before logging initialisation and before
     * application.properties is consulted.
     */
    private static final class InstallationPropertiesListener
            implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

        private final Map<String, Object> properties;

        InstallationPropertiesListener(Map<String, Object> properties) {
            this.properties = Map.copyOf(properties);
        }

        @Override
        public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            event.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("genealogyArchiveInstallation", properties));
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
