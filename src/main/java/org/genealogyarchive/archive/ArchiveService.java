package org.genealogyarchive.archive;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchiveInstanceRepository;
import org.genealogyarchive.data.FriendlyIdSeriesRepository;
import org.genealogyarchive.data.StorageVolumeRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.ChangeRecorder;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Archive identity and archive-level configuration.
 * <p>
 * The Archive ID is created only by {@link #initialize} (the {@code init-archive} command, owner
 * decision B), never by a migration, and can never change afterwards (enforced here and by
 * PostgreSQL triggers).
 */
public final class ArchiveService {

    public static final String OBJECT_TYPE = "archive_instance";

    private final ArchiveInstanceRepository archives;
    private final StorageVolumeRepository volumes;
    private final FriendlyIdSeriesRepository series;
    private final ChangeRecorder recorder;
    private final HistoryService history;
    private final TransactionTemplate tx;

    public ArchiveService(ArchiveInstanceRepository archives, StorageVolumeRepository volumes,
                          FriendlyIdSeriesRepository series, ChangeRecorder recorder, HistoryService history,
                          TransactionTemplate tx) {
        this.archives = archives;
        this.volumes = volumes;
        this.series = series;
        this.recorder = recorder;
        this.history = history;
        this.tx = tx;
    }

    /**
     * Creates this installation's Archive Instance with a new Archive ID.
     *
     * @throws ArchiveAlreadyInitializedException if this database already holds an archive
     */
    public ArchiveInstance initialize(Actor actor, String archiveName) {
        AccessPolicy.require(actor, Permission.INITIALIZE_ARCHIVE);
        String name = validName(archiveName);
        return tx.execute(status -> {
            archives.lockForInitialization();
            Optional<ArchiveInstance> existing = archives.find();
            if (existing.isPresent()) {
                throw new ArchiveAlreadyInitializedException(existing.get().archiveId());
            }
            var archiveId = GlobalIds.newId();
            archives.insert(archiveId, name);
            recorder.record(actor, OBJECT_TYPE, archiveId, ChangeKinds.CREATED, List.of(
                    FieldChange.set("archive_id", FieldClass.IMMUTABLE, archiveId),
                    FieldChange.set("archive_name", FieldClass.CORRECTABLE, name)), null);
            return archives.find().orElseThrow();
        });
    }

    /** The archive, if initialized. Internal read used by status and readiness; no archive content. */
    public Optional<ArchiveInstance> current() {
        return archives.find();
    }

    /** The archive for display to a signed-in user. */
    public ArchiveInstance view(Actor actor) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return archives.find().orElseThrow(() -> new NotFoundException("The archive"));
    }

    /** Corrects the archive display name (optimistic concurrency, recorded in Change History). */
    public ArchiveInstance rename(Actor actor, long expectedVersion, String newName, String reason) {
        AccessPolicy.require(actor, Permission.CONFIGURE_ARCHIVE);
        String name = validName(newName);
        return tx.execute(status -> {
            ArchiveInstance current = archives.find().orElseThrow(() -> new NotFoundException("The archive"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The archive configuration");
            }
            if (current.archiveName().equals(name)) {
                return current;
            }
            if (!archives.rename(expectedVersion, name)) {
                throw new ConflictException("The archive configuration");
            }
            recorder.record(actor, OBJECT_TYPE, current.archiveId(), ChangeKinds.CORRECTED, List.of(
                    FieldChange.of("archive_name", FieldClass.CORRECTABLE, current.archiveName(), name)), reason);
            return archives.find().orElseThrow();
        });
    }

    /** Change History of the archive configuration. */
    public List<ChangeRecord> history(Actor actor) {
        ArchiveInstance archive = view(actor);
        return history.forObject(actor, OBJECT_TYPE, archive.archiveId());
    }

    /**
     * Archive-level configuration in its portable form (the shape later exports will carry).
     * Contains nothing from installation configuration.
     */
    public ArchiveConfiguration configuration(Actor actor) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return portableConfiguration();
    }

    /** Internal form of {@link #configuration(Actor)} for system processes (e.g. future export jobs). */
    public ArchiveConfiguration portableConfiguration() {
        ArchiveInstance archive = archives.find().orElseThrow(() -> new NotFoundException("The archive"));
        return new ArchiveConfiguration(archive.archiveId(), archive.archiveName(),
                volumes.findAll().stream().map(v -> new ArchiveConfiguration.Volume(v.volumeId(), v.name(),
                        v.purpose(), v.description(), v.required(), v.retiredAt() != null)).toList(),
                series.findAll().stream().map(s -> new ArchiveConfiguration.IdentifierSeries(
                        s.objectType(), s.prefix(), s.minDigits())).toList());
    }

    static String validName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 200) {
            throw new RuleViolationException("The archive name must be 1 to 200 characters.");
        }
        return n;
    }

    /** This database already holds an Archive Instance; a second one is never created. */
    public static final class ArchiveAlreadyInitializedException extends RuleViolationException {
        public ArchiveAlreadyInitializedException(java.util.UUID existing) {
            super("This database already holds archive " + existing + ". An Archive Instance is created only once; "
                    + "to start an unrelated archive, use a new empty database.");
        }
    }
}
