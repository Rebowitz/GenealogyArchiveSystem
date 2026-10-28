package org.genealogyarchive.storage;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.StorageVolumeRepository;
import org.genealogyarchive.files.VolumeRoots;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.ChangeRecorder;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Storage Volume registration and availability (BUILD-SPEC s.28). Phase 1 never writes archival
 * Files; availability is a read-only check of the installation-configured root.
 */
public final class StorageVolumeService {

    public static final String OBJECT_TYPE = "storage_volume";
    private static final Pattern PURPOSE = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final StorageVolumeRepository volumes;
    private final ChangeRecorder recorder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final VolumeRoots roots;

    public StorageVolumeService(StorageVolumeRepository volumes, ChangeRecorder recorder, TransactionTemplate tx,
                                Clock clock, VolumeRoots roots) {
        this.volumes = volumes;
        this.recorder = recorder;
        this.tx = tx;
        this.clock = clock;
        this.roots = roots;
    }

    public StorageVolume register(Actor actor, String name, String purpose, String description, boolean required) {
        AccessPolicy.require(actor, Permission.MANAGE_STORAGE_VOLUMES);
        String n = validName(name);
        String p = validPurpose(purpose);
        String d = validDescription(description);
        return tx.execute(status -> {
            if (volumes.activeNameTaken(n, null)) {
                throw new RuleViolationException("An active volume is already named '" + n + "'.");
            }
            StorageVolume v = new StorageVolume(GlobalIds.newId(), n, p, d, required, clock.instant(), null, 1);
            volumes.insert(v);
            recorder.record(actor, OBJECT_TYPE, v.volumeId(), ChangeKinds.CREATED, List.of(
                    FieldChange.set("volume_id", FieldClass.IMMUTABLE, v.volumeId()),
                    FieldChange.set("name", FieldClass.CORRECTABLE, n),
                    FieldChange.set("purpose", FieldClass.CORRECTABLE, p),
                    FieldChange.set("description", FieldClass.CORRECTABLE, d),
                    FieldChange.set("required", FieldClass.CORRECTABLE, required)), null);
            return volumes.findById(v.volumeId()).orElseThrow();
        });
    }

    public StorageVolume correct(Actor actor, UUID volumeId, long expectedVersion, String name, String purpose,
                                 String description, boolean required, String reason) {
        AccessPolicy.require(actor, Permission.MANAGE_STORAGE_VOLUMES);
        String n = validName(name);
        String p = validPurpose(purpose);
        String d = validDescription(description);
        return tx.execute(status -> {
            StorageVolume current = volumes.findById(volumeId).orElseThrow(() -> new NotFoundException("The storage volume"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The storage volume");
            }
            if (!current.active()) {
                throw new RuleViolationException("A retired volume cannot be changed.");
            }
            if (volumes.activeNameTaken(n, volumeId)) {
                throw new RuleViolationException("An active volume is already named '" + n + "'.");
            }
            List<FieldChange> changes = List.of(
                    FieldChange.of("name", FieldClass.CORRECTABLE, current.name(), n),
                    FieldChange.of("purpose", FieldClass.CORRECTABLE, current.purpose(), p),
                    FieldChange.of("description", FieldClass.CORRECTABLE, current.description(), d),
                    FieldChange.of("required", FieldClass.CORRECTABLE, current.required(), required));
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!volumes.update(volumeId, expectedVersion, n, p, d, required, null)) {
                throw new ConflictException("The storage volume");
            }
            recorder.record(actor, OBJECT_TYPE, volumeId, ChangeKinds.CORRECTED, changes, reason);
            return volumes.findById(volumeId).orElseThrow();
        });
    }

    /** Retires a volume. Its ID stays permanently reserved (rows are never deleted). */
    public StorageVolume retire(Actor actor, UUID volumeId, long expectedVersion, String reason) {
        AccessPolicy.require(actor, Permission.MANAGE_STORAGE_VOLUMES);
        return tx.execute(status -> {
            StorageVolume current = volumes.findById(volumeId).orElseThrow(() -> new NotFoundException("The storage volume"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The storage volume");
            }
            if (!current.active()) {
                return current;
            }
            var now = clock.instant();
            if (!volumes.update(volumeId, expectedVersion, current.name(), current.purpose(), current.description(), false, now)) {
                throw new ConflictException("The storage volume");
            }
            recorder.record(actor, OBJECT_TYPE, volumeId, ChangeKinds.RETIRED, List.of(
                    FieldChange.of("required", FieldClass.CORRECTABLE, current.required(), false),
                    FieldChange.of("retired_at", FieldClass.CORRECTABLE, null, now)), reason);
            return volumes.findById(volumeId).orElseThrow();
        });
    }

    public List<StorageVolume> list(Actor actor) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return volumes.findAll();
    }

    public StorageVolume get(Actor actor, UUID volumeId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return volumes.findById(volumeId).orElseThrow(() -> new NotFoundException("The storage volume"));
    }

    /** Availability of every active registered volume on this installation (read-only checks). */
    public List<VolumeStatus> availability() {
        List<VolumeStatus> result = new ArrayList<>();
        for (StorageVolume v : volumes.findAll()) {
            if (v.active()) {
                result.add(new VolumeStatus(v, availabilityOf(v.volumeId())));
            }
        }
        return result;
    }

    /** Volume IDs that installation configuration maps but the archive has not registered. */
    public Set<UUID> unregisteredConfiguredVolumes() {
        Set<UUID> registered = volumes.findAll().stream().map(StorageVolume::volumeId).collect(Collectors.toSet());
        return roots.configuredVolumeIds().stream().filter(id -> !registered.contains(id))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private VolumeAvailability availabilityOf(UUID volumeId) {
        Optional<Path> root = roots.rootOf(volumeId);
        if (root.isEmpty()) {
            return VolumeAvailability.NOT_CONFIGURED;
        }
        return Files.isDirectory(root.get()) ? VolumeAvailability.AVAILABLE : VolumeAvailability.UNAVAILABLE;
    }

    static String validName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 100) {
            throw new RuleViolationException("The volume name must be 1 to 100 characters.");
        }
        return n;
    }

    static String validPurpose(String purpose) {
        String p = purpose == null ? "" : purpose.strip();
        if (!PURPOSE.matcher(p).matches()) {
            throw new RuleViolationException("The purpose is a short code of lower-case letters, digits and '-', "
                    + "for example primary, backup or removable-backup.");
        }
        return p;
    }

    static String validDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String d = description.strip();
        if (d.length() > 2000) {
            throw new RuleViolationException("The description must be at most 2000 characters.");
        }
        return d;
    }
}
