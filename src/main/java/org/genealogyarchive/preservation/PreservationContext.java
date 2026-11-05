package org.genealogyarchive.preservation;

import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.ArchiveInstanceRepository;
import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.data.FileOperationRepository;
import org.genealogyarchive.data.FriendlyIdSeriesRepository;
import org.genealogyarchive.data.IntegrityRepository;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.data.StorageVolumeRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.history.ChangeRecorder;
import org.genealogyarchive.jobs.JobQueue;
import org.genealogyarchive.service.NotFoundException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared dependencies of the preservation services: repositories, the installation's volume roots,
 * staging and cache locations, transactions, change recording, job queue, clock and fault points.
 */
public record PreservationContext(
        FileOperationRepository operations,
        FileContentRepository contents,
        ArchivalFileRepository files,
        IntegrityRepository integrity,
        ArchiveInstanceRepository archives,
        StorageVolumeRepository volumes,
        FriendlyIdSeriesRepository friendlyIds,
        OperationLocks locks,
        TransactionTemplate tx,
        ChangeRecorder recorder,
        JobQueue jobs,
        VolumeRoots roots,
        FileSettings settings,
        Clock clock,
        FaultPoints faults) {

    public static final String JOB_INGEST = "file.ingest";
    public static final String JOB_DESTROY = "file.destroy";
    public static final String JOB_REPAIR = "file.repair";
    public static final String JOB_THUMBNAIL = "file.thumbnail";
    public static final String JOB_INTEGRITY_SCAN = "file.integrity-scan";

    /** The staging area, if configured. */
    public Optional<StagingArea> staging() {
        return settings.stagingDirectory().map(StagingArea::new);
    }

    /** Staging that has been verified usable, or a visible explanation. */
    public StagingArea requireStaging() {
        StagingArea staging = staging().orElseThrow(() -> new StagingArea.StagingUnavailableException(
                "no files.stagingDirectory is configured for this installation"));
        staging.verify();
        return staging;
    }

    public UUID archiveId() {
        return archives.find().orElseThrow(() -> new NotFoundException("The archive")).archiveId();
    }

    /** Opens a volume after verifying its marker (throws {@link ManagedVolume.VolumeUnavailableException}). */
    public ManagedVolume openVolume(UUID volumeId) {
        return ManagedVolume.open(roots, volumeId, archiveId());
    }

    /** Enqueues a single-operation job (payload {@code {"operationId":"..."}}). */
    public void enqueue(String kind, UUID id) {
        jobs.enqueue(kind, "{\"id\":\"" + id + "\"}", 5);
    }

    /** Parses the ID from a job payload written by {@link #enqueue}. */
    public static UUID idFromPayload(String payloadJson) {
        int i = payloadJson.indexOf("\"id\"");
        int start = payloadJson.indexOf('"', payloadJson.indexOf(':', i) + 1) + 1;
        return UUID.fromString(payloadJson.substring(start, start + 36));
    }
}
