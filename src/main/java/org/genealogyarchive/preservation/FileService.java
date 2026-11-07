package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.FileOperationRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Viewing, correcting, deleting, restoring, destroying and downloading archival Files.
 * <p>
 * <b>Restriction rule (Phase 2, P2-H/B2):</b> a RESTRICTED File is visible only to actors with
 * {@link Permission#VIEW_RESTRICTED_FILES} (Administrators). For everyone else every route - view,
 * download, thumbnail, derivative and duplicate notices, history, friendly-ID lookup and guessed IDs -
 * behaves exactly as for a File that does not exist ({@link NotFoundException} "The file").
 */
public final class FileService {

    private static final String NOT_FOUND = "The file";

    private final PreservationContext ctx;

    public FileService(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** Everything shown on a File's page, already filtered for the actor. */
    public record FileView(ArchivalFile file, FileContent content, Optional<Derivation> derivation,
                           Optional<ArchivalFile> parent, List<ArchivalFile> derivatives,
                           List<ArchivalFile> sharingContent, List<IntegrityFinding> openFindings,
                           List<FileOperation> operations, boolean canSeeRestricted) {
    }

    public static boolean canSee(Actor actor, ArchivalFile file) {
        return AccessPolicy.allows(actor, Permission.VIEW_ARCHIVE)
                && (!file.restricted() || AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES));
    }

    /** The File if the actor may see it; otherwise "not found" (never "forbidden"). */
    public ArchivalFile get(Actor actor, UUID fileId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.files().find(fileId).filter(f -> canSee(actor, f)).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** Looks up {@code F00042}-style identifiers, subject to the same visibility rule. */
    public ArchivalFile getByFriendlyId(Actor actor, String friendlyId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        // GAS-P2-R02: the same parsing that Change History authorization uses to recognise references.
        long number = FileIdentifiers.friendlyNumber(friendlyId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return ctx.files().findByFriendlyNumber(number).filter(f -> canSee(actor, f))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    public FileView view(Actor actor, UUID fileId) {
        ArchivalFile file = get(actor, fileId);
        FileContent content = ctx.contents().find(file.contentId()).orElseThrow();
        Optional<Derivation> derivation = ctx.files().derivation(fileId);
        Optional<ArchivalFile> parent = derivation.flatMap(d -> ctx.files().find(d.parentFileId())).filter(p -> canSee(actor, p));
        List<ArchivalFile> derivatives = ctx.files().derivativesOf(fileId).stream()
                .map(id -> ctx.files().find(id).orElseThrow()).filter(f -> canSee(actor, f)).toList();
        List<ArchivalFile> sharing = ctx.files().byContent(file.contentId()).stream()
                .filter(f -> !f.fileId().equals(fileId) && f.lifecycle() != FileLifecycle.DESTROYED && canSee(actor, f))
                .toList();
        boolean admin = AccessPolicy.allows(actor, Permission.MANAGE_PRESERVATION);
        List<IntegrityFinding> findings = admin ? ctx.integrity().openFindingsFor(file.contentId()) : List.of();
        List<FileOperation> ops = admin ? ctx.operations().forFile(fileId) : List.of();
        return new FileView(file, content, derivation, parent, derivatives, sharing, findings, ops,
                AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES));
    }

    /** Newest Files the actor may see. Deleted Files are listed for those who can restore them. */
    public List<ArchivalFile> list(Actor actor, boolean includeDeleted, int limit) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        boolean deleted = includeDeleted && AccessPolicy.allows(actor, Permission.EDIT_FILES);
        return ctx.files().list(deleted, AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES), Math.min(limit, 500));
    }

    /** Correctable attributes (A-008: never touches the stored bytes). */
    public ArchivalFile correct(Actor actor, UUID fileId, long expectedVersion, String managedFilename,
                                int displayRotation, Restriction restriction, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_FILES);
        String name = managedFilename == null ? "" : managedFilename.strip();
        if (name.isEmpty() || name.length() > 255 || name.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw new RuleViolationException("The display filename must be 1 to 255 characters without control characters.");
        }
        if (displayRotation != 0 && displayRotation != 90 && displayRotation != 180 && displayRotation != 270) {
            throw new RuleViolationException("Display rotation must be 0, 90, 180 or 270 degrees.");
        }
        return ctx.tx().execute(status -> {
            ArchivalFile current = ctx.files().lock(fileId).filter(f -> canSee(actor, f))
                    .orElseThrow(() -> new NotFoundException(NOT_FOUND));
            if (current.lifecycle() == FileLifecycle.DESTROYED) {
                throw new RuleViolationException("A destroyed File cannot be changed.");
            }
            if (current.version() != expectedVersion) {
                throw new ConflictException("The file");
            }
            Restriction target = restriction == null ? current.restriction() : restriction;
            if (current.restricted() && target == Restriction.NORMAL) {
                AccessPolicy.require(actor, Permission.VIEW_RESTRICTED_FILES);
                Optional<Derivation> d = ctx.files().derivation(fileId);
                if (d.isPresent() && ctx.files().selfOrAncestorRestricted(d.get().parentFileId())) {
                    throw new RuleViolationException("A File this one was derived from (directly or through other "
                            + "derivatives) is restricted, so this derivative must stay restricted.");
                }
            }
            List<FieldChange> changes = List.of(
                    FieldChange.of("managed_filename", FieldClass.CORRECTABLE, current.managedFilename(), name),
                    FieldChange.of("display_rotation", FieldClass.CORRECTABLE, current.displayRotation(), displayRotation),
                    FieldChange.of("restriction", FieldClass.CORRECTABLE, current.restriction(), target));
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            boolean restricting = target == Restriction.RESTRICTED && !current.restricted();
            List<UUID> inheriting = restricting ? ctx.files().unrestrictedDescendants(fileId) : List.of();
            if (!ctx.files().updateAttributes(fileId, expectedVersion, name, displayRotation, target)) {
                throw new ConflictException("The file");
            }
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, fileId, ChangeKinds.CORRECTED, changes, reason);
            if (restricting) {
                recordInheritedRestriction(actor, fileId, inheriting);
            }
            return ctx.files().find(fileId).orElseThrow();
        });
    }

    /**
     * Restriction inheritance (P2-H, GAS-P2-004). The database restricts every derivative below a newly
     * restricted File, at any depth, in the same statement (V0014 triggers), and also restricts a
     * derivative recorded concurrently. This records that inherited change, with its cause, on each
     * derivative that was NORMAL before, and fails visibly if any derivative were left unrestricted.
     */
    private void recordInheritedRestriction(Actor actor, UUID fileId, List<UUID> inheriting) {
        for (UUID d : inheriting) {
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, d, ChangeKinds.CORRECTED, List.of(
                    FieldChange.of("restriction", FieldClass.CORRECTABLE, Restriction.NORMAL, Restriction.RESTRICTED)),
                    "Inherited: a File it was derived from was restricted.");
        }
        if (!ctx.files().unrestrictedDescendants(fileId).isEmpty()) {
            throw new IllegalStateException("Restriction inheritance left a derivative of File " + fileId + " unrestricted");
        }
    }


    /** Recoverable deletion (BUILD-SPEC s.37): a database state only; the bytes stay in place. */
    public ArchivalFile delete(Actor actor, UUID fileId, long expectedVersion, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_FILES);
        return lifecycle(actor, fileId, expectedVersion, FileLifecycle.ACTIVE, FileLifecycle.DELETED, ChangeKinds.DELETED, reason);
    }

    public ArchivalFile restore(Actor actor, UUID fileId, long expectedVersion, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_FILES);
        return lifecycle(actor, fileId, expectedVersion, FileLifecycle.DELETED, FileLifecycle.ACTIVE, ChangeKinds.RESTORED, reason);
    }

    private ArchivalFile lifecycle(Actor actor, UUID fileId, long expectedVersion, FileLifecycle from, FileLifecycle to,
                                   String kind, String reason) {
        return ctx.tx().execute(status -> {
            ArchivalFile current = ctx.files().lock(fileId).filter(f -> canSee(actor, f))
                    .orElseThrow(() -> new NotFoundException(NOT_FOUND));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The file");
            }
            if (current.lifecycle() != from) {
                throw new RuleViolationException("The File is " + current.lifecycle().name().toLowerCase() + ".");
            }
            Instant now = ctx.clock().instant();
            boolean deleting = to == FileLifecycle.DELETED;
            if (!ctx.files().updateLifecycle(fileId, expectedVersion, from, to, deleting ? now : null,
                    deleting ? actor.userId() : null, null, null, null, null)) {
                throw new ConflictException("The file");
            }
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, fileId, kind, List.of(
                    FieldChange.of("lifecycle", FieldClass.CORRECTABLE, from, to)), reason);
            return ctx.files().find(fileId).orElseThrow();
        });
    }

    /**
     * Permanent destruction (Administrator-controlled; BUILD-SPEC s.37, F-007). The File must already be
     * in recoverable deletion. In one transaction: the File becomes its own tombstone (identifiers stay
     * retired) and, only if no other File still uses the content, the content is marked DESTROY_PENDING
     * and a DESTROY operation is created; the worker then removes the bytes. Shared content is never
     * removed while referenced (transactional reference check under a row lock).
     */
    public FileOperation destroy(Actor actor, UUID fileId, long expectedVersion, String reason, UUID replacementFileId) {
        AccessPolicy.require(actor, Permission.DESTROY_FILES);
        if (reason == null || reason.isBlank()) {
            throw new RuleViolationException("Give a short reason for permanent destruction; it is kept with the tombstone.");
        }
        String why = reason.strip().length() > 2000 ? reason.strip().substring(0, 2000) : reason.strip();
        UUID operationId = GlobalIds.newId();
        // GAS-P2-IV03: destruction intent is ordered against every repair of the content. Inside the intent
        // transaction - on its own connection, so any number of repairs costs one pooled connection - it first
        // takes each unfinished repair's lock for the transaction (a worker holds the same lock for as long as it
        // works on the operation), waiting only briefly; a repair that is writing makes the request fail visibly.
        List<UUID> cancelled = new java.util.ArrayList<>();
        FileOperation op = destroyHolding(actor, fileId, expectedVersion, why, replacementFileId, operationId, cancelled);
        // Staged bytes of the repairs stopped by the intent are no longer needed.
        for (UUID repair : cancelled) {
            ctx.staging().ifPresent(staging -> {
                try {
                    if (staging.delete(repair)) {
                        ctx.operations().markStagingCleaned(repair);
                    }
                } catch (IOException e) {
                    // recovery retries the clean-up of a final operation's staged bytes
                }
            });
        }
        return op;
    }

    private static final java.util.Set<OperationState> STOPPED_BY_DESTRUCTION = java.util.EnumSet.of(OperationState.STAGED,
            OperationState.PROMOTING, OperationState.PROMOTED, OperationState.WAITING_FOR_VOLUME, OperationState.BLOCKED);

    private static final java.time.Duration REPAIR_LOCK_WAIT = java.time.Duration.ofSeconds(2);

    /**
     * Within the current transaction, takes the lock of every unfinished repair of the content, waiting at
     * most {@link #REPAIR_LOCK_WAIT} in total. Runs before any row is locked, so the order is always
     * operation lock, then rows - the order the worker uses.
     */
    private java.util.Set<UUID> lockRepairsForTransaction(UUID contentId) {
        java.util.Set<UUID> holding = new java.util.HashSet<>();
        long deadline = System.nanoTime() + REPAIR_LOCK_WAIT.toNanos();
        for (FileOperation w : ctx.operations().writersOfContent(contentId)) {
            if (w.kind() != OperationKind.REPAIR || w.state().isFinal()) {
                continue;
            }
            while (!ctx.operations().tryLockForTransaction(w.operationId())) {
                if (System.nanoTime() > deadline) {
                    throw new RuleViolationException("A repair of this File's bytes is being written right now (operation "
                            + w.operationId() + "). Try again when it has finished, or cancel it from Operations first.");
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuleViolationException("Destruction was interrupted; nothing was changed.");
                }
            }
            holding.add(w.operationId());
        }
        return holding;
    }

    private FileOperation destroyHolding(Actor actor, UUID fileId, long expectedVersion, String why, UUID replacementFileId,
                                         UUID operationId, List<UUID> cancelled) {
        FileOperation op = ctx.tx().execute(status -> {
            UUID contentId = ctx.files().find(fileId).filter(f -> canSee(actor, f)).map(ArchivalFile::contentId)
                    .orElseThrow(() -> new NotFoundException(NOT_FOUND));
            java.util.Set<UUID> holding = lockRepairsForTransaction(contentId);
            ArchivalFile file = ctx.files().lock(fileId).filter(f -> canSee(actor, f))
                    .orElseThrow(() -> new NotFoundException(NOT_FOUND));
            if (file.version() != expectedVersion) {
                throw new ConflictException("The file");
            }
            if (file.lifecycle() != FileLifecycle.DELETED) {
                throw new RuleViolationException("Only a File in recoverable deletion can be permanently destroyed.");
            }
            if (replacementFileId != null && (replacementFileId.equals(fileId)
                    || ctx.files().find(replacementFileId).filter(f -> f.lifecycle() != FileLifecycle.DESTROYED).isEmpty())) {
                throw new RuleViolationException("The preferred replacement must be another File that still exists.");
            }
            FileContent content = ctx.contents().lock(file.contentId()).orElseThrow();
            // With the content row locked no repair can create a copy (it needs the row in share mode); any
            // unfinished repair that is not held here started after the locks were taken and may be writing.
            for (FileOperation w : ctx.operations().writersOfContent(content.contentId())) {
                if (w.kind() == OperationKind.REPAIR && !w.state().isFinal() && w.state() != OperationState.RECEIVING
                        && !holding.contains(w.operationId())) {
                    throw new RuleViolationException("A repair of this File's bytes started just now (operation "
                            + w.operationId() + "). Try again when it has finished, or cancel it from Operations first.");
                }
            }
            Instant now = ctx.clock().instant();
            if (!ctx.files().updateLifecycle(fileId, expectedVersion, FileLifecycle.DELETED, FileLifecycle.DESTROYED,
                    file.deletedAt(), null, now, actor.userId(), why, replacementFileId)) {
                throw new ConflictException("The file");
            }
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, fileId, ChangeKinds.DESTROYED, List.of(
                    FieldChange.of("lifecycle", FieldClass.CORRECTABLE, FileLifecycle.DELETED, FileLifecycle.DESTROYED),
                    FieldChange.set("replacement_file_id", FieldClass.CORRECTABLE, replacementFileId)), why);
            boolean stillReferenced = ctx.contents().liveFileCount(content.contentId()) > 0;
            boolean removable = !stillReferenced && content.state() != ContentState.DESTROYED
                    && content.state() != ContentState.DESTROY_PENDING;
            if (removable) {
                ctx.contents().setState(content.contentId(), content.state(), ContentState.DESTROY_PENDING);
                // Unfinished repairs end with the intent, while their locks are held: none can write afterwards.
                for (FileOperation w : ctx.operations().writersOfContent(content.contentId())) {
                    if (w.kind() == OperationKind.REPAIR && holding.contains(w.operationId()) && STOPPED_BY_DESTRUCTION.contains(w.state())) {
                        String message = DestructionProcessor.stoppedRepairMessage(operationId, w);
                        if (ctx.operations().transition(w.operationId(), w.state(), OperationState.CANCELLED, message,
                                java.util.Map.of("message", message))) {
                            cancelled.add(w.operationId());
                        }
                    }
                }
            }
            ctx.operations().insert(new FileOperationRepository.NewOperation(operationId, OperationKind.DESTROY,
                    removable ? OperationState.CLEANUP_PENDING : OperationState.COMPLETED, actor, content.volumeId(),
                    null, null, null, fileId, content.contentId(), null, null, null, null, null, null, null),
                    removable ? "File retired (tombstone committed); removal of its bytes pending"
                            : "File retired (tombstone committed); its bytes are still used by another File and were kept");
            return ctx.operations().find(operationId).orElseThrow();
        });
        if (op.state() == OperationState.CLEANUP_PENDING) {
            ctx.enqueue(PreservationContext.JOB_DESTROY, operationId);
        }
        return op;
    }

    /** An open download of a File's preservation original. The caller closes the stream. */
    public record Download(ArchivalFile file, long size, InputStream stream) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    /**
     * Opens the original for download. Requires the File to be visible, ACTIVE (or DELETED for those
     * who may restore it) and its content ACTIVE; the stored size is checked against the authoritative
     * size, and a mismatch is recorded as an integrity finding instead of serving altered bytes.
     */
    public Download download(Actor actor, UUID fileId) {
        ArchivalFile file = get(actor, fileId);
        if (file.lifecycle() == FileLifecycle.DESTROYED
                || (file.lifecycle() == FileLifecycle.DELETED && !AccessPolicy.allows(actor, Permission.EDIT_FILES))) {
            throw new NotFoundException(NOT_FOUND);
        }
        FileContent content = ctx.contents().find(file.contentId()).orElseThrow();
        if (content.state() != ContentState.ACTIVE) {
            throw new RuleViolationException("This File's stored content is " + content.state().name().toLowerCase()
                    + " and is not being served. An Administrator can see the integrity findings and repair it.");
        }
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(content.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            throw new RuleViolationException("This File is temporarily unavailable: " + e.getMessage());
        }
        try {
            Optional<Path> located = volume.locate(content.currentPath());
            if (located.isEmpty() || !Files.exists(located.get(), LinkOption.NOFOLLOW_LINKS)) {
                // GAS-P2-007: a missing directory above the original is missing content too (repairable).
                ctx.integrity().insertFindingIfNew(null, new org.genealogyarchive.data.IntegrityRepository.NewFinding(
                        FindingKind.MISSING, content.volumeId(), content.contentId(), content.currentGeneration(),
                        content.currentPath().value(), null, null, null, "Stored original not found when a download was requested."));
                ctx.contents().setState(content.contentId(), ContentState.ACTIVE, ContentState.MISSING);
                throw new RuleViolationException("This File's stored original is missing. The problem has been recorded.");
            }
            Path path = located.get();
            FileChannel channel = SafeFiles.openForRead(path);
            long size = channel.size();
            if (size != content.byteSize()) {
                channel.close();
                ctx.integrity().insertFindingIfNew(null, new org.genealogyarchive.data.IntegrityRepository.NewFinding(
                        FindingKind.SIZE_MISMATCH, content.volumeId(), content.contentId(), content.currentGeneration(),
                        content.currentPath().value(), null, size, null,
                        "Stored size " + size + " differs from the authoritative " + content.byteSize() + " bytes."));
                throw new RuleViolationException("This File's stored original does not match its recorded size and is not being served. "
                        + "The problem has been recorded.");
            }
            return new Download(file, size, Channels.newInputStream(channel));
        } catch (IOException e) {
            throw new RuleViolationException("This File could not be read from its volume right now ("
                    + e.getClass().getSimpleName() + ").");
        }
    }

    /** The cached thumbnail of a visible File, if one has been generated. */
    public Optional<Path> thumbnail(Actor actor, UUID fileId) {
        ArchivalFile file = get(actor, fileId);
        if (file.lifecycle() == FileLifecycle.DESTROYED) {
            return Optional.empty();
        }
        return ctx.settings().cacheDirectory().map(dir -> ThumbnailService.location(dir, fileId))
                .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS));
    }

    public List<org.genealogyarchive.history.ChangeRecord> history(Actor actor, UUID fileId,
                                                                   org.genealogyarchive.history.HistoryService history) {
        get(actor, fileId);
        return history.forObject(actor, ArchivalFileRepository.OBJECT_TYPE, fileId);
    }
}
