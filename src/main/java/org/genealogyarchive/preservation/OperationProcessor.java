package org.genealogyarchive.preservation;

import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.data.IntegrityRepository;
import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.data.UnresolvedObjectRepository;
import org.genealogyarchive.platform.SafeFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Worker side of ingestion and verified repair (BUILD-SPEC s.31, s.35, s.36, s.48; Phase 2 plan
 * sections 2-5). Drives one operation through its persistent states:
 * <pre>
 *   STAGED -> PROMOTING -> PROMOTED -> COMMITTED -> COMPLETED
 *                \-> WAITING_FOR_VOLUME / BLOCKED (visible, recoverable)
 * </pre>
 * <ul>
 *   <li><b>No overwrite.</b> The target is created with exclusive create; anything already present at
 *       a target that this operation never attempted to write is a COLLISION and is left untouched.
 *       A partial copy left by this operation's own interrupted attempt is moved to quarantine (never
 *       deleted) and the next attempt writes a new, never-used generation path.</li>
 *   <li><b>Protection before success.</b> The read-only protection is applied and verified before the
 *       operation can become PROMOTED, and re-checked before commit; failure blocks the operation.</li>
 *   <li><b>Verification before commit.</b> The stored copy is re-read and must match the size and
 *       SHA-256 measured while staging (and, for repair, the authoritative values).</li>
 *   <li><b>One File per operation.</b> The database refuses a second File for the same operation, so
 *       recovery and retries converge (TX-003).</li>
 * </ul>
 * Processing of an operation is serialized with a PostgreSQL advisory lock (released automatically if
 * the worker dies) and every state change is a compare-and-set.
 */
public final class OperationProcessor {

    private static final Logger log = LoggerFactory.getLogger(OperationProcessor.class);
    private static final String LOCK = "file-operation";

    private final PreservationContext ctx;
    private final QuarantineMoves quarantine;

    public OperationProcessor(PreservationContext ctx) {
        this.ctx = ctx;
        this.quarantine = new QuarantineMoves(ctx);
    }

    /**
     * Advances an INGEST or REPAIR operation as far as it can go now.
     *
     * @param leaseGuard called before each consequential step (a background job passes its lease check)
     * @return true if this call held the operation's lock (false if another process is working on it)
     */
    public boolean process(UUID operationId, Runnable leaseGuard) {
        Optional<OperationLocks.Held> held = ctx.locks().tryLock(LOCK, operationId);
        if (held.isEmpty()) {
            return false;
        }
        try (OperationLocks.Held lock = held.get()) {
            for (int step = 0; step < 12; step++) {
                FileOperation op = ctx.operations().find(operationId).orElseThrow();
                if (op.kind() == OperationKind.DESTROY) {
                    return true;
                }
                boolean progressed = switch (op.state()) {
                    case STAGED -> plan(op, leaseGuard);
                    case PROMOTING -> promote(op, leaseGuard);
                    case PROMOTED -> verifyAndCommit(op, leaseGuard);
                    case COMMITTED -> finish(op);
                    case WAITING_FOR_VOLUME -> resume(op);
                    default -> cleanupFinal(op);
                };
                if (!progressed) {
                    break;
                }
            }
            return true;
        }
    }

    // ---- STAGED -----------------------------------------------------------------------------

    private boolean plan(FileOperation op, Runnable guard) {
        guard.run();
        StagingArea staging = ctx.requireStaging();
        try {
            if (staging.existing(op.operationId()).isEmpty()) {
                return block(op, BlockedReason.STAGING_INVALID, "The staged upload is missing; it cannot be archived.");
            }
        } catch (IOException e) {
            return block(op, BlockedReason.STAGING_INVALID, "The staged upload is not a plain file in the staging directory.");
        }
        if (op.kind() == OperationKind.REPAIR) {
            return planRepair(op, staging);
        }
        UUID contentId = GlobalIds.newId();
        PortableRelativePath path = ManagedVolume.originalPath(contentId, 1);
        return ctx.operations().transition(op.operationId(), OperationState.STAGED, OperationState.PROMOTING,
                "target planned: " + path.value(), Map.of("content_id", contentId, "target_generation", 1,
                        "target_path", path.value(), "target_created", false));
    }

    private boolean planRepair(FileOperation op, StagingArea staging) {
        FileContent content = ctx.contents().find(op.contentId()).orElseThrow();
        if (content.state() != ContentState.MISSING && content.state() != ContentState.DAMAGED) {
            return reject(op, staging, "The content no longer needs repair (state " + content.state() + ").");
        }
        if (op.expectedSize() == null || op.expectedSize() != content.byteSize()
                || !content.sha256().equals(op.expectedSha256())) {
            return reject(op, staging, "The supplied bytes do not match the authoritative size and SHA-256 "
                    + "(expected " + content.byteSize() + " bytes, " + content.sha256() + "); nothing was changed.");
        }
        int previous = op.targetGeneration() == null ? 0 : op.targetGeneration();
        int generation = Math.max(ctx.contents().maxGeneration(content.contentId()), previous) + 1;
        PortableRelativePath path = ManagedVolume.originalPath(content.contentId(), generation);
        return ctx.operations().transition(op.operationId(), OperationState.STAGED, OperationState.PROMOTING,
                "repair target planned: " + path.value(), Map.of("target_generation", generation,
                        "target_path", path.value(), "volume_id", content.volumeId(), "target_created", false));
    }

    private boolean reject(FileOperation op, StagingArea staging, String message) {
        ctx.operations().transition(op.operationId(), OperationState.STAGED, OperationState.REJECTED,
                message, Map.of("message", message));
        deleteStaging(op.operationId(), staging);
        return false;
    }

    // ---- PROMOTING ----------------------------------------------------------------------------

    /**
     * Creates the operation's copy exclusively and records that the operation owns it, in one transaction
     * (GAS-P2-IV03). For a repair the transaction first locks the content row in share mode and re-reads
     * its state: destruction intent locks that row for update, so it either committed before (the repair
     * sees DESTROY_PENDING and creates nothing) or waits until the copy exists and is owned, after which it
     * cannot proceed while this worker holds the operation's lock (see {@code FileService.destroy}).
     *
     * @return the open channel, or null if the content is being destroyed (nothing was created)
     */
    private FileChannel createOwned(FileOperation op, Path target) throws IOException {
        FileChannel[] created = new FileChannel[1];
        try {
            ctx.tx().executeWithoutResult(status -> {
                if (op.kind() == OperationKind.REPAIR) {
                    ContentState state = ctx.contents().lockShared(op.contentId()).map(FileContent::state)
                            .orElse(ContentState.DESTROYED);
                    if (state == ContentState.DESTROY_PENDING || state == ContentState.DESTROYED) {
                        return;
                    }
                }
                try {
                    created[0] = SafeFiles.createExclusive(target);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                // Recorded before any byte is written: from now on the object at the target is ours.
                ctx.operations().markTargetCreated(op.operationId(), op.targetPath());
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        } catch (RuntimeException e) {
            if (created[0] != null) {
                created[0].close();
            }
            throw e;
        }
        return created[0];
    }

    private boolean promote(FileOperation op, Runnable guard) {
        if (op.kind() == OperationKind.REPAIR) {
            ContentState state = ctx.contents().find(op.contentId()).map(FileContent::state).orElse(ContentState.DESTROYED);
            if (state == ContentState.DESTROY_PENDING || state == ContentState.DESTROYED) {
                // GAS-P2-FV01: never write a new copy of content being destroyed; the destruction stops this
                // repair under its lock and accounts for anything it already created.
                return false;
            }
        }
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            return toWaiting(op, e);
        }
        StagingArea staging = ctx.requireStaging();
        Path staged;
        Path target;
        try {
            staged = staging.existing(op.operationId()).orElse(null);
            target = volume.prepare(PortableRelativePath.of(op.targetPath()));
        } catch (SafeFiles.UnsafePathException e) {
            return block(op, BlockedReason.UNSAFE_PATH, "A managed directory is a link, junction or reparse point; nothing was written.");
        } catch (IOException e) {
            return ioProblem(op, e);
        }

        if (op.kind() == OperationKind.INGEST && op.attempts() == 0 && staged != null && tryShare(op, staged, guard)) {
            return true;
        }

        // GAS-P2-008: finish any quarantine move of this operation that was interrupted before it was
        // recorded, before deciding anything from what is (or is not) at the target.
        for (QuarantineRepository.Entry pending : ctx.quarantine().forOperation(op.operationId())) {
            if (pending.state() == QuarantineRepository.State.INTENDED) {
                try {
                    quarantine.settle(volume, pending);
                } catch (FileAlreadyExistsException e) {
                    return block(op, BlockedReason.STATE_UNKNOWN, "An interrupted quarantine move of " + pending.sourcePath()
                            + " cannot be completed: both the object and " + pending.quarantinePath()
                            + " exist. Nothing was moved.");
                } catch (IOException e) {
                    return ioProblem(op, e);
                }
                // GAS-P2-FV01: settling released the operation's ownership of the location it vacated.
                op = ctx.operations().find(op.operationId()).orElseThrow();
            }
        }

        // V0019 audit: an inaccessible target is not "absent" - the attempt fails visibly and is retried.
        boolean targetPresent;
        try {
            targetPresent = Presence.exists(ctx, volume, target);
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        if (targetPresent) {
            if (!op.targetCreated()) {
                // GAS-P2-009: only an object this operation's own exclusive create produced is ever treated
                // as its copy; anything else at the planned location is left exactly where it is.
                return block(op, BlockedReason.COLLISION, "An object already exists at the planned location "
                        + op.targetPath() + " although this operation never created it. It was not touched.");
            }
            // V0018 audit: the claim says only that this operation once created an object here. The object now
            // here is its copy only if it is the received bytes or a prefix of the (re-verified) staged upload.
            OperationCopies.Check own;
            try {
                own = OperationCopies.check(ctx, volume, op, Optional.ofNullable(staged));
            } catch (SafeFiles.UnsafePathException e) {
                return block(op, BlockedReason.UNSAFE_PATH, "The planned location holds a link or reparse point; it was not touched.");
            } catch (IOException e) {
                return ioProblem(op, e);
            }
            switch (own.verdict()) {
                case COMPLETE -> {
                    return protectAndPromote(op, target, "a complete copy from an earlier attempt was found and verified");
                }
                case PARTIAL -> {
                    guard.run();
                    return quarantinePartialAndReplan(op, volume, own.observed());
                }
                case UNVERIFIED -> {
                    return unverifiedOwnCopy(op, own);
                }
                default -> {
                    // ABSENT: vanished since the check above; the next pass creates the copy again.
                    return true;
                }
            }
        }

        if (staged == null) {
            return block(op, BlockedReason.STAGING_INVALID, "The staged upload is missing; nothing was written.");
        }
        guard.run();
        ctx.operations().incrementAttempts(op.operationId());
        ContentIo.Measurement copied;
        try {
            ctx.faults().reach(FaultPoints.Point.BEFORE_TARGET_CREATE, op.operationId());
            FileChannel created = createOwned(op, target);
            if (created == null) {
                return false;
            }
            try (FileChannel out = created) {
                copied = ContentIo.copy(staged, out, ctx.faults(), op.operationId());
            }
            SafeFiles.flushDirectory(target.getParent());
        } catch (FileAlreadyExistsException e) {
            return block(op, BlockedReason.COLLISION, "An object appeared at the planned location " + op.targetPath()
                    + " before it could be created. It was not touched.");
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        if (!matchesExpected(op, copied)) {
            String where;
            try {
                // The bytes this worker just wrote, measured while writing them, identify the copy (V0019).
                where = quarantine.move(volume, op.operationId(), op.contentId(), PortableRelativePath.of(op.targetPath()),
                        "unverified-copy-" + (op.attempts() + 1), QuarantineMoves.Evidence.operationCopy(copied),
                        QuarantineRepository.Reason.UNVERIFIED_COPY).quarantinePath();
            } catch (IOException e) {
                where = null;
                log.warn("Operation {}: unverified copy could not be moved to quarantine yet; its intent is recorded",
                        op.operationId());
            }
            return block(op, BlockedReason.STAGING_INVALID, "The staged bytes changed after they were received "
                    + "(size/SHA-256 differ). " + (where == null ? "Moving the copy to quarantine is pending; "
                    : "The copy was moved to " + where + "; ") + "nothing was archived.");
        }
        try {
            ctx.faults().reach(FaultPoints.Point.BEFORE_PROTECT, op.operationId());
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        return protectAndPromote(op, target, "copied " + copied.size() + " bytes into " + op.targetPath());
    }

    private boolean protectAndPromote(FileOperation op, Path target, String detail) {
        try {
            SafeFiles.makeReadOnly(target);
        } catch (IOException e) {
            return block(op, BlockedReason.PROTECTION, "The read-only protection could not be applied to the stored copy ("
                    + e.getClass().getSimpleName() + "). The File is not reported as archived until it is.");
        }
        try {
            ctx.faults().reach(FaultPoints.Point.AFTER_PROTECT, op.operationId());
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        return ctx.operations().transition(op.operationId(), OperationState.PROMOTING, OperationState.PROMOTED,
                detail + "; protected read-only");
    }

    /**
     * GAS-P2-008: the partial copy's move is recorded in the quarantine ledger before it happens and
     * confirmed after it; if the process stops between the move and this transition, the next attempt
     * settles the ledger entry first (see promote) and then simply writes the target again.
     */
    private boolean quarantinePartialAndReplan(FileOperation op, ManagedVolume volume, ContentIo.Measurement partial) {
        QuarantineRepository.Entry moved;
        try {
            // The partial copy was identified as a prefix of the verified staged upload (OperationCopies).
            moved = quarantine.move(volume, op.operationId(), op.contentId(), PortableRelativePath.of(op.targetPath()),
                    "partial-" + op.attempts(), QuarantineMoves.Evidence.operationCopy(partial),
                    QuarantineRepository.Reason.PARTIAL_COPY);
        } catch (FileAlreadyExistsException e) {
            return block(op, BlockedReason.STATE_UNKNOWN, "The partial copy at " + op.targetPath()
                    + " cannot be moved to quarantine because its destination already exists. Nothing was moved.");
        } catch (QuarantineMoves.UnexplainedObjectException e) {
            return block(op, BlockedReason.STATE_UNKNOWN, "The partial copy at " + op.targetPath() + " was not moved: "
                    + e.getMessage() + ".");
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        try {
            ctx.faults().reach(FaultPoints.Point.AFTER_QUARANTINE_RECORDED, op.operationId());
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        UUID contentId = op.contentId();
        int next = Math.max(op.targetGeneration() + 1, ctx.contents().maxGeneration(contentId) + 1);
        PortableRelativePath path = ManagedVolume.originalPath(contentId, next);
        Map<String, Object> set = new HashMap<>();
        set.put("target_generation", next);
        set.put("target_path", path.value());
        set.put("quarantine_path", moved.quarantinePath());
        set.put("target_created", false);
        return ctx.operations().transition(op.operationId(), OperationState.PROMOTING, OperationState.PROMOTING,
                "partial copy from an interrupted attempt moved to " + moved.quarantinePath() + "; next attempt writes "
                        + path.value(), set);
    }

    /**
     * The object at this operation's claimed target is not shown to be its copy (V0018 audit): it is never
     * moved, adopted or deleted. In one transaction the claim ends, the object is recorded as unresolved and
     * the operation blocks visibly; an Administrator investigates.
     */
    private boolean unverifiedOwnCopy(FileOperation op, OperationCopies.Check own) {
        String message = "The object at " + op.targetPath() + ", where this operation created its copy, is not shown to "
                + "be that copy (" + own.reason() + "). It was left untouched and is recorded as unresolved; nothing was "
                + "archived.";
        log.warn("File operation {} blocked ({}): {}", op.operationId(), BlockedReason.STATE_UNKNOWN, message);
        ctx.tx().executeWithoutResult(status -> {
            OperationCopies.claimBecomesUnresolved(ctx, op, own);
            if (!ctx.operations().transition(op.operationId(), op.state(), OperationState.BLOCKED, message,
                    Map.of("blocked_reason", BlockedReason.STATE_UNKNOWN, "message", message))) {
                status.setRollbackOnly();
            }
        });
        return false;
    }

    // ---- exact duplicates (P2-B) ----------------------------------------------------------------

    private boolean tryShare(FileOperation op, Path staged, Runnable guard) {
        for (FileContent candidate : ctx.contents().duplicateCandidates(op.expectedSize(), op.expectedSha256())) {
            if (candidate.currentPath() == null || usedByRestrictedFile(candidate.contentId())) {
                // Never share with content a restricted File uses: the "identical bytes already stored"
                // outcome would otherwise reveal to the uploader that the restricted File exists.
                continue;
            }
            // GAS-P2-005: the shared original must meet the same guarantee as a new copy before success is
            // reported - protected read-only, and matching its authoritative size and SHA-256 - and be
            // byte-for-byte identical to this upload.
            Path existing;
            try {
                ManagedVolume volume = ctx.openVolume(candidate.volumeId());
                Optional<Path> located = volume.locate(candidate.currentPath());
                if (located.isEmpty() || !Files.isRegularFile(located.get(), LinkOption.NOFOLLOW_LINKS)) {
                    log.info("Operation {}: candidate content {} is not present; the upload is stored separately",
                            op.operationId(), candidate.contentId());
                    continue;
                }
                existing = located.get();
                ContentIo.Measurement stored = ContentIo.measure(existing);
                if (stored.size() != candidate.byteSize() || !stored.sha256().equals(candidate.sha256())
                        || !ContentIo.identical(staged, existing)) {
                    log.info("Operation {}: candidate content {} no longer verifies or differs from the upload; "
                            + "the upload is stored separately", op.operationId(), candidate.contentId());
                    continue;
                }
            } catch (ManagedVolume.VolumeUnavailableException | IOException e) {
                continue;
            }
            boolean wasProtected;
            try {
                wasProtected = SafeFiles.isReadOnly(existing);
            } catch (IOException e) {
                continue;
            }
            if (!wasProtected) {
                ctx.integrity().insertFindingIfNew(null, new IntegrityRepository.NewFinding(FindingKind.NOT_PROTECTED,
                        candidate.volumeId(), candidate.contentId(), candidate.currentGeneration(),
                        candidate.currentPath().value(), op.operationId(), null, null,
                        "Found while sharing an identical upload: the stored original had lost its read-only protection. "
                                + "The worker re-applied it before the upload was reported as archived."));
                try {
                    ctx.faults().reach(FaultPoints.Point.BEFORE_PROTECT, op.operationId());
                    SafeFiles.makeReadOnly(existing);
                    if (!SafeFiles.isReadOnly(existing)) {
                        return block(op, BlockedReason.PROTECTION, "The identical stored original " + candidate.currentPath().value()
                                + " is still not protected read-only. The upload is not reported as archived.");
                    }
                } catch (IOException e) {
                    return block(op, BlockedReason.PROTECTION, "The identical stored original " + candidate.currentPath().value()
                            + " is not protected read-only and protection could not be re-applied ("
                            + e.getClass().getSimpleName() + "). The upload is not reported as archived.");
                }
            }
            guard.run();
            if (commitShared(op, candidate, staged)) {
                return true;
            }
        }
        return false;
    }

    private boolean commitShared(FileOperation op, FileContent verified, Path staged) {
        String mediaType = mediaTypeOf(staged);
        UUID contentId = verified.contentId();
        Boolean done = ctx.tx().execute(status -> {
            Optional<FileContent> locked = ctx.contents().lock(contentId);
            // The verified generation must still be the current one, and the content still ACTIVE.
            if (locked.isEmpty() || locked.get().state() != ContentState.ACTIVE
                    || locked.get().currentGeneration() != verified.currentGeneration()
                    || usedByRestrictedFile(contentId)) {
                return false;
            }
            ctx.contents().markVerified(contentId, ctx.clock().instant());
            UUID fileId = GlobalIds.newId();
            Map<String, Object> set = new HashMap<>();
            set.put("content_id", contentId);
            set.put("file_id", fileId);
            set.put("outcome", "SHARED_CONTENT");
            if (!ctx.operations().transition(op.operationId(), OperationState.PROMOTING, OperationState.COMMITTED,
                    "byte-for-byte identical to stored content " + contentId + "; bytes shared, new File and provenance", set)) {
                status.setRollbackOnly();
                return false;
            }
            insertFile(op, fileId, contentId, mediaType, true);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    private boolean usedByRestrictedFile(UUID contentId) {
        return ctx.files().byContent(contentId).stream()
                .anyMatch(f -> f.lifecycle() != FileLifecycle.DESTROYED && f.restricted());
    }

    // ---- PROMOTED ---------------------------------------------------------------------------------

    private boolean verifyAndCommit(FileOperation op, Runnable guard) {
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            return toWaiting(op, e);
        }
        Path target;
        ContentIo.Measurement m;
        try {
            target = volume.resolveExisting(PortableRelativePath.of(op.targetPath()));
            if (!Presence.exists(ctx, volume, target)) {
                return block(op, BlockedReason.STATE_UNKNOWN, "The promoted copy at " + op.targetPath()
                        + " is no longer present; the state cannot be determined safely.");
            }
            if (!SafeFiles.isReadOnly(target)) {
                SafeFiles.makeReadOnly(target);
            }
            m = ContentIo.measure(target);
        } catch (SafeFiles.UnsafePathException e) {
            return block(op, BlockedReason.UNSAFE_PATH, "The stored copy's location is a link or reparse point.");
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        if (!matchesExpected(op, m)) {
            return block(op, BlockedReason.VERIFY_MISMATCH, "The stored copy did not verify (" + m.size()
                    + " bytes, SHA-256 " + m.sha256() + "). Nothing was committed; the copy was left in place.");
        }
        try {
            ctx.faults().reach(FaultPoints.Point.AFTER_VERIFY_BEFORE_COMMIT, op.operationId());
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        guard.run();
        if (op.kind() != OperationKind.REPAIR) {
            return commitNewContent(op, MediaTypes.sniff(m.head()));
        }
        Superseded old;
        try {
            old = inspectSuperseded(op, volume);
            ctx.faults().reach(FaultPoints.Point.AFTER_SUPERSEDED_INSPECTION, op.operationId());
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        return commitRepair(op, old);
    }

    /** What occupies the location of the generation a repair supersedes, looked at just before the commit. */
    private record Superseded(int generation, boolean present, UnresolvedObjectRepository.Identity observed) {
    }

    private Superseded inspectSuperseded(FileOperation op, ManagedVolume volume) throws IOException {
        FileContent content = ctx.contents().find(op.contentId()).orElseThrow();
        // V0019 audit: only verified absence retires the location as MISSING; an inaccessible location stops the
        // commit (retried). The look is not evidence after the commit: destruction re-inspects every former location.
        Presence.Probe probe = Presence.probe(ctx, volume, content.currentPath().value());
        if (probe.verifiedAbsent()) {
            return new Superseded(content.currentGeneration(), false, null);
        }
        if (!probe.present()) {
            throw new Presence.InconclusiveException(content.currentPath().value(), probe.problem());
        }
        Path at = probe.path();
        if (SafeFiles.isLinkOrReparsePoint(at) || !Files.isRegularFile(at, LinkOption.NOFOLLOW_LINKS)) {
            return new Superseded(content.currentGeneration(), true, null);
        }
        // V0020: the observation's identity evidence (attributes read before the bytes) is persisted with the record.
        return new Superseded(content.currentGeneration(), true, ObjectIdentity.of(probe.attributes(), ContentIo.measure(at)));
    }

    private boolean commitNewContent(FileOperation op, String mediaType) {
        Boolean done = ctx.tx().execute(status -> {
            UUID fileId = GlobalIds.newId();
            if (!ctx.operations().transition(op.operationId(), OperationState.PROMOTED, OperationState.COMMITTED,
                    "verified and committed", Map.of("file_id", fileId, "outcome", "NEW_CONTENT"))) {
                status.setRollbackOnly();
                return false;
            }
            Instant now = ctx.clock().instant();
            // One owner at a time (V0016): the committed copy now belongs to its generation, not to this operation.
            ctx.operations().releaseTarget(op.operationId(), op.targetPath());
            ctx.contents().insert(op.contentId(), op.volumeId(), op.expectedSize(), op.expectedSha256(),
                    op.operationId(), op.targetGeneration(), PortableRelativePath.of(op.targetPath()), now);
            insertFile(op, fileId, op.contentId(), mediaType, false);
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    private void insertFile(FileOperation op, UUID fileId, UUID contentId, String mediaType, boolean shared) {
        long number = ctx.friendlyIds().allocate(ArchivalFileRepository.OBJECT_TYPE).number();
        String managed = op.originalFilename().length() > 255 ? op.originalFilename().substring(0, 255) : op.originalFilename();
        Instant now = ctx.clock().instant();
        Restriction restriction = op.requestedRestriction() == null ? Restriction.NORMAL : op.requestedRestriction();
        if (op.parentFileId() != null) {
            // GAS-P2-004: the parent (or an ancestor) may have been restricted after this upload was received.
            // The parent's row lock orders this check against a concurrent restriction of the parent.
            ctx.files().lock(op.parentFileId());
            if (ctx.files().selfOrAncestorRestricted(op.parentFileId())) {
                restriction = Restriction.RESTRICTED;
            }
        }
        ctx.files().insert(new ArchivalFileRepository.NewFile(fileId, number, contentId, op.operationId(),
                op.originalFilename(), managed.strip().isEmpty() ? "file" : managed, mediaType, now, op.actorUserId(),
                op.batchId(), op.clientLastModified(), op.sourceNote(), restriction));
        List<FieldChange> fields = new ArrayList<>(List.of(
                FieldChange.set("file_id", FieldClass.IMMUTABLE, fileId),
                FieldChange.set("friendly_id", FieldClass.IMMUTABLE, ArchivalFileRepository.friendlyIdOf(number)),
                FieldChange.set("original_filename", FieldClass.IMMUTABLE, op.originalFilename()),
                FieldChange.set("byte_size", FieldClass.IMMUTABLE, op.expectedSize()),
                FieldChange.set("sha256", FieldClass.IMMUTABLE, op.expectedSha256()),
                FieldChange.set("restriction", FieldClass.CORRECTABLE, restriction)));
        if (shared) {
            fields.add(FieldChange.set("stored_bytes", FieldClass.IMMUTABLE, "shared with existing content " + contentId));
        }
        if (op.parentFileId() != null) {
            ctx.files().insertDerivation(fileId, op.parentFileId(), op.derivationMethod());
            fields.add(FieldChange.set("derived_from", FieldClass.IMMUTABLE, op.parentFileId()));
            fields.add(FieldChange.set("derivation_method", FieldClass.IMMUTABLE, op.derivationMethod()));
        }
        ctx.recorder().recordFor(op.actorUserId(), op.actorLabel(), ArchivalFileRepository.OBJECT_TYPE, fileId,
                ChangeKinds.CREATED, fields, null);
    }

    private boolean commitRepair(FileOperation op, Superseded inspected) {
        Boolean done = ctx.tx().execute(status -> {
            if (!ctx.operations().transition(op.operationId(), OperationState.PROMOTED, OperationState.COMMITTED,
                    "repaired generation " + op.targetGeneration() + " verified against the authoritative SHA-256",
                    Map.of("outcome", "REPAIRED"))) {
                status.setRollbackOnly();
                return false;
            }
            FileContent content = ctx.contents().lock(op.contentId()).orElseThrow();
            if (content.state() != ContentState.MISSING && content.state() != ContentState.DAMAGED) {
                status.setRollbackOnly();
                return false;
            }
            int old = content.currentGeneration();
            if (old != inspected.generation()) {
                status.setRollbackOnly();
                return false;
            }
            // V0018 audit: the superseded location is retired by what is physically there. Nothing there: MISSING
            // (truly absent). Anything there - the damaged copy, or something that appeared where the bytes left -
            // cannot be shown to be the archive's (an observed mismatch identifies nothing), so it is UNRESOLVED:
            // left untouched, recorded durably, never moved or deleted without trusted evidence.
            boolean vacated = ctx.contents().generations(content.contentId()).stream()
                    .anyMatch(g -> g.generation() == old && g.vacated());
            if (inspected.present()) {
                FileContentRepository.Generation g = ctx.contents().generation(content.contentId(), old).orElseThrow();
                ctx.unresolved().recordIfAbsent(new UnresolvedObjectRepository.NewEntry(g.volumeId(), g.storagePath(),
                        content.contentId(), old, op.operationId(),
                        vacated ? UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION
                                : UnresolvedObjectRepository.Kind.DAMAGED_COPY,
                        inspected.observed(),
                        "Repair " + op.operationId() + " superseded generation " + old + " (content " + content.state()
                                + "). An object is at " + g.storagePath() + " that the archive cannot identify as its copy"
                                + (vacated ? " (the archive recorded the bytes leaving this location)" : "")
                                + ". It was left untouched. An Administrator may attest that it is the archive's damaged "
                                + "copy (it is then moved to quarantine) or that it is not (it is released)."));
                ctx.contents().setGenerationState(content.contentId(), old, GenerationState.CURRENT, GenerationState.UNRESOLVED, null);
            } else {
                ctx.contents().setGenerationState(content.contentId(), old, GenerationState.CURRENT, GenerationState.MISSING, null);
            }
            // One owner at a time: the committed copy now belongs to its generation, not to this operation.
            ctx.operations().releaseTarget(op.operationId(), op.targetPath());
            ctx.contents().addGeneration(content.contentId(), op.targetGeneration(), content.volumeId(),
                    PortableRelativePath.of(op.targetPath()), GenerationState.CURRENT, op.operationId());
            ctx.contents().setState(content.contentId(), content.state(), ContentState.ACTIVE);
            ctx.contents().markVerified(content.contentId(), ctx.clock().instant());
            String resolution = "Repaired by operation " + op.operationId() + ": generation " + op.targetGeneration()
                    + " written from Administrator-supplied bytes and verified against the authoritative SHA-256."
                    + (inspected.present() ? " The object at the superseded location is recorded as unresolved." : "");
            for (IntegrityFinding f : ctx.integrity().openFindingsFor(content.contentId())) {
                ctx.integrity().resolve(f.findingId(), resolution, op.operationId());
            }
            for (ArchivalFile file : ctx.files().byContent(content.contentId())) {
                ctx.recorder().recordFor(op.actorUserId(), op.actorLabel(), ArchivalFileRepository.OBJECT_TYPE,
                        file.fileId(), ChangeKinds.REPAIRED, List.of(
                                FieldChange.of("stored_generation", FieldClass.CORRECTABLE, old, op.targetGeneration()),
                                FieldChange.set("previous_state", FieldClass.CORRECTABLE, content.state())), null);
            }
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    // ---- COMMITTED ------------------------------------------------------------------------------

    private boolean finish(FileOperation op) {
        try {
            ctx.faults().reach(FaultPoints.Point.AFTER_COMMIT_BEFORE_CLEANUP, op.operationId());
        } catch (IOException e) {
            return ioProblem(op, e);
        }
        if (op.kind() == OperationKind.REPAIR && !quarantineSuperseded(op)) {
            return false;
        }
        StagingArea staging = ctx.requireStaging();
        deleteStaging(op.operationId(), staging);
        boolean done = ctx.operations().transition(op.operationId(), OperationState.COMMITTED, OperationState.COMPLETED,
                op.kind() == OperationKind.REPAIR ? "repair complete" : "safely archived");
        if (done && op.kind() == OperationKind.INGEST && op.fileId() != null && ctx.settings().cacheDirectory().isPresent()) {
            ctx.enqueue(PreservationContext.JOB_THUMBNAIL, op.fileId());
        }
        return false;
    }

    /**
     * Legacy QUARANTINE_PENDING generations (recorded before V0019, when a repair moved the superseded damaged
     * copy automatically). Their identity evidence was an observation, so nothing is moved (V0018 audit): an
     * interrupted move recorded earlier is settled under its own rules, and then the location is retired by what
     * is there - MISSING if nothing, otherwise UNRESOLVED with a durable record, untouched.
     */
    private boolean quarantineSuperseded(FileOperation op) {
        List<FileContentRepository.Generation> pending = ctx.contents().generations(op.contentId()).stream()
                .filter(g -> g.state() == GenerationState.QUARANTINE_PENDING).toList();
        if (pending.isEmpty()) {
            return true;
        }
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            ctx.operations().note(op.operationId(), "superseded generation awaits retirement: " + e.getMessage());
            return false;
        }
        for (FileContentRepository.Generation g : pending) {
            try {
                for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(g.contentId())) {
                    if (entry.state() == QuarantineRepository.State.INTENDED && entry.sourcePath().equals(g.storagePath())) {
                        quarantine.settle(volume, entry);
                    }
                }
                FileContentRepository.Generation now = ctx.contents().generation(g.contentId(), g.generation()).orElseThrow();
                if (now.state() != GenerationState.QUARANTINE_PENDING) {
                    continue;
                }
                Presence.Probe probe = Presence.probe(ctx, volume, g.storagePath());
                if (!probe.present() && !probe.verifiedAbsent()) {
                    throw new Presence.InconclusiveException(g.storagePath(), probe.problem());
                }
                boolean present = probe.present();
                UnresolvedObjectRepository.Identity observed = present && probe.attributes().isRegularFile()
                        && !SafeFiles.isLinkOrReparsePoint(probe.path())
                        ? ObjectIdentity.of(probe.attributes(), ContentIo.measure(probe.path())) : null;
                ctx.tx().executeWithoutResult(status -> {
                    if (present) {
                        ctx.unresolved().recordIfAbsent(new UnresolvedObjectRepository.NewEntry(g.volumeId(), g.storagePath(),
                                g.contentId(), g.generation(), op.operationId(), UnresolvedObjectRepository.Kind.DAMAGED_COPY,
                                observed,
                                "Generation " + g.generation() + " was superseded by repair " + op.operationId() + " and was "
                                        + "awaiting quarantine. The object at " + g.storagePath() + " cannot be identified as the "
                                        + "archive's damaged copy (only an observation was recorded), so it was left untouched."));
                    }
                    ctx.contents().setGenerationState(g.contentId(), g.generation(), GenerationState.QUARANTINE_PENDING,
                            present ? GenerationState.UNRESOLVED : GenerationState.MISSING, null);
                });
                ctx.operations().note(op.operationId(), "superseded generation " + g.generation() + (present
                        ? " is recorded as unresolved; the object at " + g.storagePath() + " was left untouched"
                        : " was no longer present"));
            } catch (IOException e) {
                ctx.operations().note(op.operationId(), "superseded generation " + g.generation()
                        + " could not be retired yet (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "); it remains accounted for");
                return false;
            }
        }
        return true;
    }

    // ---- WAITING_FOR_VOLUME / final ----------------------------------------------------------------

    private boolean resume(FileOperation op) {
        try {
            ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            return false;
        }
        OperationState next = op.targetPath() == null ? OperationState.STAGED : OperationState.PROMOTING;
        return ctx.operations().transition(op.operationId(), OperationState.WAITING_FOR_VOLUME, next,
                "volume available again; resuming");
    }

    private boolean cleanupFinal(FileOperation op) {
        // The staged upload is the evidence of a copy the operation still owns (OperationCopies): kept until then.
        if (op.state().isFinal() && op.stagingName() != null && !op.stagingCleaned() && !op.targetCreated()) {
            ctx.staging().ifPresent(staging -> deleteStaging(op.operationId(), staging));
        }
        return false;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private boolean matchesExpected(FileOperation op, ContentIo.Measurement m) {
        return op.expectedSize() != null && m.size() == op.expectedSize() && m.sha256().equals(op.expectedSha256());
    }

    private boolean toWaiting(FileOperation op, ManagedVolume.VolumeUnavailableException e) {
        ctx.operations().transition(op.operationId(), op.state(), OperationState.WAITING_FOR_VOLUME,
                e.getMessage(), Map.of("message", e.getMessage()));
        return false;
    }

    private boolean block(FileOperation op, BlockedReason reason, String message) {
        log.warn("File operation {} blocked ({}): {}", op.operationId(), reason, message);
        ctx.operations().transition(op.operationId(), op.state(), OperationState.BLOCKED, message,
                Map.of("blocked_reason", reason, "message", message));
        return false;
    }

    /**
     * An I/O failure during an attempt. If the volume has become unavailable the operation waits for
     * it (TX-004, TX-008); otherwise the failure propagates so the job is retried and stays visible.
     */
    private boolean ioProblem(FileOperation op, IOException e) {
        try {
            ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException unavailable) {
            return toWaiting(op, unavailable);
        }
        ctx.operations().note(op.operationId(), "attempt failed: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        throw new FileOperationFailedException(op.operationId(), e);
    }

    private void deleteStaging(UUID operationId, StagingArea staging) {
        try {
            if (staging.delete(operationId)) {
                ctx.operations().markStagingCleaned(operationId);
            }
        } catch (IOException e) {
            log.warn("Staged temporary of operation {} could not be removed yet; recovery will retry", operationId);
        }
    }

    private static String mediaTypeOf(Path file) {
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return MediaTypes.sniff(in.readNBytes(32));
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    /** An attempt failed for a reason other than an unavailable volume; the operation is retried later. */
    public static final class FileOperationFailedException extends RuntimeException {
        public FileOperationFailedException(UUID operationId, IOException cause) {
            super("File operation " + operationId + " attempt failed: " + cause.getClass().getSimpleName(), cause);
        }
    }
}
