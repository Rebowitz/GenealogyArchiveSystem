package org.genealogyarchive.service;

import org.genealogyarchive.jobs.JobState;
import org.genealogyarchive.storage.VolumeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Operational conditions an Administrator should see (BUILD-SPEC s.58, OP-004, OP-005). None of
 * these decide readiness: database-only work continues while, for example, a volume is offline or
 * the worker is stopped; queued work simply waits.
 */
public record OperationalStatus(
        List<VolumeStatus> volumes,
        Set<UUID> unregisteredConfiguredVolumes,
        Map<JobState, Long> jobCounts,
        List<Worker> workers,
        Instant checkedAt) {

    /** A worker process as last reported; {@code alive} if it reported recently. */
    public record Worker(UUID workerId, Instant startedAt, Instant lastSeenAt, String appVersion, boolean alive) {
    }

    public OperationalStatus {
        volumes = List.copyOf(volumes);
        unregisteredConfiguredVolumes = Set.copyOf(unregisteredConfiguredVolumes);
        jobCounts = Map.copyOf(jobCounts);
        workers = List.copyOf(workers);
    }

    public boolean anyWorkerAlive() {
        return workers.stream().anyMatch(Worker::alive);
    }

    public boolean volumeProblems() {
        return volumes.stream().anyMatch(VolumeStatus::problem);
    }

    public long jobs(JobState state) {
        return jobCounts.getOrDefault(state, 0L);
    }

    public long queuedJobs() {
        return jobs(JobState.QUEUED);
    }

    public long runningJobs() {
        return jobs(JobState.RUNNING);
    }

    public long failedJobs() {
        return jobs(JobState.FAILED);
    }
}
