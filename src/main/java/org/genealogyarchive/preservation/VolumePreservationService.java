package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.archive.ArchiveInstance;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.storage.StorageVolume;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage Volume preparation and preservation status (P2-C, P2-D): initializing a volume's marker,
 * choosing the ingestion destination, per-volume marker status and free space, and the web
 * process's read-only verification.
 */
public final class VolumePreservationService {

    private final PreservationContext ctx;

    public VolumePreservationService(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Writes this archive's marker to a registered volume's configured root (command line,
     * Administrator on the server). Refuses if a marker is already present, if the volume is retired
     * or unconfigured, or if the database says the volume was initialized before (its marker would
     * then be missing - investigate rather than re-create).
     */
    public void initializeVolume(Actor actor, UUID volumeId) {
        AccessPolicy.require(actor, Permission.MANAGE_STORAGE_VOLUMES);
        StorageVolume volume = ctx.volumes().findById(volumeId).orElseThrow(() -> new NotFoundException("The storage volume"));
        if (!volume.active()) {
            throw new RuleViolationException("A retired volume cannot be initialized.");
        }
        Optional<Instant> before = ctx.volumes().markerInitializedAt(volumeId);
        if (before.isPresent()) {
            throw new RuleViolationException("This volume was initialized on " + before.get() + ". If its marker is missing, "
                    + "the wrong directory may be configured or mounted; investigate instead of initializing again.");
        }
        Path root = ctx.roots().rootOf(volumeId).orElseThrow(() -> new RuleViolationException(
                "This installation has no storageRoots entry for volume " + volumeId + "."));
        if (!Files.isDirectory(root)) {
            throw new RuleViolationException("The configured root " + root + " does not exist or is not a directory.");
        }
        Instant now = ctx.clock().instant();
        try {
            ManagedVolume.initialize(root, ctx.archiveId(), volumeId, now);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new RuleViolationException("A volume marker already exists in " + root + "; it was not changed.");
        } catch (IOException e) {
            throw new RuleViolationException("The volume could not be initialized: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        }
        ctx.tx().executeWithoutResult(s -> {
            ctx.volumes().markInitialized(volumeId, now);
            ctx.recorder().record(actor, "storage_volume", volumeId, ChangeKinds.INITIALIZED, List.of(
                    FieldChange.set("marker_initialized_at", FieldClass.IMMUTABLE, now)), null);
        });
    }

    /** Chooses where newly ingested originals are written (archive configuration). */
    public void setIngestionVolume(Actor actor, long expectedArchiveVersion, UUID volumeId, String reason) {
        AccessPolicy.require(actor, Permission.CONFIGURE_ARCHIVE);
        StorageVolume volume = ctx.volumes().findById(volumeId).orElseThrow(() -> new NotFoundException("The storage volume"));
        if (!volume.active()) {
            throw new RuleViolationException("A retired volume cannot receive new Files.");
        }
        if (ctx.volumes().markerInitializedAt(volumeId).isEmpty()) {
            throw new RuleViolationException("Initialize the volume first (command: init-volume).");
        }
        ctx.tx().executeWithoutResult(s -> {
            ArchiveInstance archive = ctx.archives().find().orElseThrow(() -> new NotFoundException("The archive"));
            Optional<UUID> previous = ctx.archives().ingestionVolume();
            if (archive.version() != expectedArchiveVersion || !ctx.archives().setIngestionVolume(expectedArchiveVersion, volumeId)) {
                throw new ConflictException("The archive settings");
            }
            ctx.recorder().record(actor, "archive_instance", archive.archiveId(), ChangeKinds.CORRECTED, List.of(
                    FieldChange.of("ingestion_volume_id", FieldClass.CORRECTABLE, previous.orElse(null), volumeId)), reason);
        });
    }

    public Optional<UUID> ingestionVolume() {
        return ctx.archives().ingestionVolume();
    }

    /** Preservation status of one active volume on this installation. */
    public record VolumePreservation(StorageVolume volume, boolean initializedInArchive, String markerStatus,
                                     boolean usable, Long usableBytes, boolean ingestionTarget) {
    }

    public List<VolumePreservation> status() {
        Optional<UUID> target = ctx.archives().ingestionVolume();
        List<VolumePreservation> result = new ArrayList<>();
        for (StorageVolume v : ctx.volumes().findAll()) {
            if (!v.active()) {
                continue;
            }
            boolean initialized = ctx.volumes().markerInitializedAt(v.volumeId()).isPresent();
            String marker;
            boolean usable = false;
            Long free = null;
            try {
                ManagedVolume volume = ctx.openVolume(v.volumeId());
                marker = "verified";
                usable = true;
                free = Files.getFileStore(volume.root()).getUsableSpace();
            } catch (ManagedVolume.VolumeUnavailableException e) {
                marker = e.reason().explanation();
            } catch (IOException e) {
                marker = "verified (free space unknown)";
                usable = true;
            }
            result.add(new VolumePreservation(v, initialized, marker, usable, free,
                    target.map(id -> id.equals(v.volumeId())).orElse(false)));
        }
        return result;
    }

    /**
     * Verifies, from the process that calls it, that archival originals cannot be written: tries an
     * exclusive create in each usable volume's {@code originals} directory. In a correct production
     * deployment the web service is denied (ACL deny on Windows, {@code ProtectSystem=strict} on
     * Linux). If the create succeeds, the probe file is removed again and a problem is returned.
     *
     * @return problems found (empty when the process cannot write any archival originals)
     */
    public List<String> verifyNoWriteAccess() {
        List<String> problems = new ArrayList<>();
        for (VolumePreservation v : status()) {
            if (!v.usable()) {
                continue;
            }
            try {
                ManagedVolume volume = ctx.openVolume(v.volume().volumeId());
                // The volume root always exists once the marker has been verified, so a refusal here
                // is a permission refusal, not a missing directory.
                Path probe = volume.root().resolve(".web-write-probe-" + GlobalIds.newId());
                try (var channel = SafeFiles.createExclusive(probe)) {
                    // created: write access exists
                }
                Files.deleteIfExists(probe);
                problems.add("This process can write archival originals on volume '" + v.volume().name()
                        + "'; the deployment's read-only protection for the web service is missing.");
            } catch (AccessDeniedException e) {
                // expected (Windows: DENY write ACE for the web service SID; POSIX: no write permission)
            } catch (java.nio.file.NoSuchFileException e) {
                problems.add("Write access to volume '" + v.volume().name() + "' could not be checked: " + e.getMessage());
            } catch (FileSystemException e) {
                if (e.getReason() == null || !e.getReason().toLowerCase(java.util.Locale.ROOT).contains("read-only")) {
                    problems.add("Write access to volume '" + v.volume().name() + "' could not be confirmed as denied ("
                            + e.getClass().getSimpleName() + (e.getReason() == null ? "" : ": " + e.getReason()) + ").");
                }
                // "Read-only file system" (systemd ProtectSystem=strict): expected
            } catch (IOException | RuntimeException e) {
                problems.add("Write access to volume '" + v.volume().name() + "' could not be checked ("
                        + e.getClass().getSimpleName() + ").");
            }
        }
        return problems;
    }
}
