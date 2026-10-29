package org.genealogyarchive.service;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.WorkerPresenceRepository;
import org.genealogyarchive.jobs.JobQueue;
import org.genealogyarchive.storage.StorageVolumeService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Builds {@link OperationalStatus} for Administrators and the {@code status} command. */
public final class OperationalStatusService {

    /** A worker that has not reported within this window is shown as not running. */
    public static final Duration WORKER_ALIVE_WINDOW = Duration.ofMinutes(2);

    private final StorageVolumeService volumes;
    private final JobQueue jobs;
    private final WorkerPresenceRepository workers;
    private final Clock clock;

    public OperationalStatusService(StorageVolumeService volumes, JobQueue jobs, WorkerPresenceRepository workers, Clock clock) {
        this.volumes = volumes;
        this.jobs = jobs;
        this.workers = workers;
        this.clock = clock;
    }

    public OperationalStatus view(Actor actor) {
        AccessPolicy.require(actor, Permission.VIEW_OPERATIONS);
        return snapshot();
    }

    /** Internal form for the server-side {@code status} command. */
    public OperationalStatus snapshot() {
        Instant now = clock.instant();
        return new OperationalStatus(volumes.availability(), volumes.unregisteredConfiguredVolumes(), jobs.counts(),
                workers.findAll().stream().map(p -> new OperationalStatus.Worker(p.workerId(), p.startedAt(),
                        p.lastSeenAt(), p.appVersion(), p.lastSeenAt().isAfter(now.minus(WORKER_ALIVE_WINDOW)))).toList(),
                now);
    }
}
