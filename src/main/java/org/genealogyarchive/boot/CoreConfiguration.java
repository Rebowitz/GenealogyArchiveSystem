package org.genealogyarchive.boot;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.PostgresServer;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.service.SystemStatusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Beans shared by every long-running process (web application and worker). Wiring is explicit
 * (no component scanning of the core) so it is obvious what exists.
 * <p>
 * The {@link InstallationConfig} bean is registered by {@link ProcessLauncher} before the context
 * refreshes; it is the single source of installation settings.
 */
@Configuration(proxyBeanMethods = false)
public class CoreConfiguration {

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(InstallationConfig config, @Value("${gas.process}") String process) {
        return DataSources.create(config.database(), process);
    }

    @Bean
    PostgresServer postgresServer(DataSource dataSource) {
        return new PostgresServer(dataSource);
    }

    @Bean
    SchemaMigrator schemaMigrator(DataSource dataSource) {
        return new SchemaMigrator(dataSource);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SystemStatusService systemStatusService(PostgresServer postgres, SchemaMigrator migrator, Clock clock) {
        return new SystemStatusService(postgres, migrator, clock);
    }

    @Bean
    VolumeRoots volumeRoots(InstallationConfig config) {
        return new VolumeRoots(config.storageRoots());
    }
}
