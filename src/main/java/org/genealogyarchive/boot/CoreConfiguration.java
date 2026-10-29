package org.genealogyarchive.boot;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.archive.ArchiveService;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.jobs.JobQueue;
import org.genealogyarchive.security.AppUserDetailsService;
import org.genealogyarchive.security.PasswordHasher;
import org.genealogyarchive.security.UserService;
import org.genealogyarchive.service.OperationalStatusService;
import org.genealogyarchive.service.SystemStatusService;
import org.genealogyarchive.storage.StorageVolumeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Beans shared by every long-running process (web application and worker). Wiring is explicit
 * (see {@link CoreServices}) so it is obvious what exists.
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
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    VolumeRoots volumeRoots(InstallationConfig config) {
        return new VolumeRoots(config.storageRoots());
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean
    CoreServices coreServices(DataSource dataSource, VolumeRoots roots, Clock clock, PasswordHasher hasher) {
        return CoreServices.create(dataSource, roots, clock, hasher);
    }

    @Bean
    SystemStatusService systemStatusService(CoreServices core) {
        return core.systemStatus();
    }

    @Bean
    OperationalStatusService operationalStatusService(CoreServices core) {
        return core.operationalStatus();
    }

    @Bean
    ArchiveService archiveService(CoreServices core) {
        return core.archives();
    }

    @Bean
    UserService userService(CoreServices core) {
        return core.users();
    }

    @Bean
    StorageVolumeService storageVolumeService(CoreServices core) {
        return core.volumes();
    }

    @Bean
    HistoryService historyService(CoreServices core) {
        return core.history();
    }

    @Bean
    JobQueue jobQueue(CoreServices core) {
        return core.jobs();
    }

    /** Local accounts only. Also prevents Spring Boot from creating a default user in any process. */
    @Bean
    UserDetailsService userDetailsService(UserService users) {
        return new AppUserDetailsService(users);
    }

    @Bean
    PasswordEncoder passwordEncoder(PasswordHasher hasher) {
        return hasher.encoder();
    }
}
