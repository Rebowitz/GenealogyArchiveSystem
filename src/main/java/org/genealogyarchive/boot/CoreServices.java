package org.genealogyarchive.boot;

import org.genealogyarchive.archive.ArchiveService;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.data.AppUserRepository;
import org.genealogyarchive.data.ArchiveInstanceRepository;
import org.genealogyarchive.data.BackgroundJobRepository;
import org.genealogyarchive.data.ChangeHistoryRepository;
import org.genealogyarchive.data.FriendlyIdSeriesRepository;
import org.genealogyarchive.data.PostgresServer;
import org.genealogyarchive.data.SchemaIdentityRepository;
import org.genealogyarchive.data.StorageVolumeRepository;
import org.genealogyarchive.data.Transactions;
import org.genealogyarchive.data.WorkerPresenceRepository;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.history.ChangeRecorder;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.jobs.JobQueue;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.security.PasswordHasher;
import org.genealogyarchive.security.UserService;
import org.genealogyarchive.service.OperationalStatusService;
import org.genealogyarchive.service.SystemStatusService;
import org.genealogyarchive.storage.StorageVolumeService;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Explicit assembly of the core services, shared by the Spring processes (via
 * {@link CoreConfiguration}) and the command line, so both use identical wiring.
 */
public record CoreServices(
        SystemStatusService systemStatus,
        OperationalStatusService operationalStatus,
        ArchiveService archives,
        UserService users,
        StorageVolumeService volumes,
        HistoryService history,
        JobQueue jobs,
        BackgroundJobRepository jobRepository,
        WorkerPresenceRepository workerPresence,
        FriendlyIdSeriesRepository friendlyIds,
        PasswordHasher passwordHasher,
        Clock clock) {

    public static CoreServices create(DataSource dataSource, InstallationConfig config, Clock clock) {
        return create(dataSource, new VolumeRoots(config.storageRoots()), clock, new PasswordHasher());
    }

    public static CoreServices create(DataSource dataSource, VolumeRoots roots, Clock clock, PasswordHasher hasher) {
        TransactionTemplate tx = Transactions.template(dataSource);
        ChangeHistoryRepository historyRepository = new ChangeHistoryRepository(dataSource);
        ChangeRecorder recorder = new ChangeRecorder(historyRepository, clock);
        HistoryService history = new HistoryService(historyRepository);
        ArchiveInstanceRepository archiveRepository = new ArchiveInstanceRepository(dataSource);
        StorageVolumeRepository volumeRepository = new StorageVolumeRepository(dataSource);
        FriendlyIdSeriesRepository friendlyIds = new FriendlyIdSeriesRepository(dataSource);
        BackgroundJobRepository jobRepository = new BackgroundJobRepository(dataSource);
        WorkerPresenceRepository presence = new WorkerPresenceRepository(dataSource);

        SystemStatusService systemStatus = new SystemStatusService(new PostgresServer(dataSource),
                new SchemaMigrator(dataSource), clock, new SchemaIdentityRepository(dataSource), archiveRepository);
        StorageVolumeService volumes = new StorageVolumeService(volumeRepository, recorder, tx, clock, roots);
        JobQueue jobs = new JobQueue(jobRepository, clock);
        return new CoreServices(
                systemStatus,
                new OperationalStatusService(volumes, jobs, presence, clock),
                new ArchiveService(archiveRepository, volumeRepository, friendlyIds, recorder, history, tx),
                new UserService(new AppUserRepository(dataSource), recorder, hasher, tx, clock),
                volumes,
                history,
                jobs,
                jobRepository,
                presence,
                friendlyIds,
                hasher,
                clock);
    }
}
