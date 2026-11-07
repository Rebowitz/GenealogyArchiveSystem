package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Administrator actions on archival file operations: inspect, retry a BLOCKED or failed operation
 * after investigating, or cancel it. Cancelling never deletes anything from a volume: an uncommitted
 * copy is moved to quarantine.
 */
public final class OperationAdminService {

    private final PreservationContext ctx;

    public OperationAdminService(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** Preservation conditions for the Operations page and the status command (BUILD-SPEC s.58, OP-005). */
    public record PreservationStatus(List<VolumePreservationService.VolumePreservation> volumes,
                                     Map<OperationState, Long> operationCounts, long openFindings,
                                     java.util.Optional<IntegrityScan> lastScan, String ingestion,
                                     long unresolvedObjects) {

        public long operations(OperationState state) {
            return operationCounts.getOrDefault(state, 0L);
        }

        public long blocked() {
            return operations(OperationState.BLOCKED);
        }

        public long waiting() {
            return operations(OperationState.WAITING_FOR_VOLUME);
        }

        public long cleanupIncomplete() {
            return operations(OperationState.CLEANUP_INCOMPLETE);
        }

        /** Operations that need a person or are waiting for storage, and unresolved objects (V0019). */
        public long attentionNeeded() {
            return operations(OperationState.BLOCKED) + operations(OperationState.CLEANUP_INCOMPLETE)
                    + operations(OperationState.WAITING_FOR_VOLUME) + unresolvedObjects;
        }
    }

    public PreservationStatus status(Actor actor, VolumePreservationService volumes) {
        AccessPolicy.require(actor, Permission.VIEW_OPERATIONS);
        return snapshot(volumes);
    }

    /** Internal form for the server-side {@code status} command. */
    public PreservationStatus snapshot(VolumePreservationService volumes) {
        var list = volumes.status();
        String ingestion = list.stream().filter(VolumePreservationService.VolumePreservation::ingestionTarget).findFirst()
                .map(v -> v.usable() ? "to volume '" + v.volume().name() + "'"
                        : "to volume '" + v.volume().name() + "', which is NOT usable now: " + v.markerStatus())
                .orElse("no ingestion volume chosen");
        return new PreservationStatus(list, ctx.operations().countsByState(), ctx.integrity().openCount(),
                ctx.integrity().lastCompleted(), ingestion, ctx.unresolved().openCount());
    }

    public List<FileOperation> recent(Actor actor, int limit) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.operations().recent(limit);
    }

    public FileOperation get(Actor actor, UUID operationId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.operations().find(operationId).orElseThrow(() -> new NotFoundException("The operation"));
    }

    public List<OperationEvent> events(Actor actor, UUID operationId) {
        get(actor, operationId);
        return ctx.operations().events(operationId);
    }

    /**
     * Retries a BLOCKED operation. A collision gets a new, never-used target; other blocks return to
     * promotion, where a matching complete copy is verified and anything else is moved to quarantine.
     */
    public FileOperation retry(Actor actor, UUID operationId) {
        FileOperation op = get(actor, operationId);
        switch (op.state()) {
            case BLOCKED -> {
                if (op.blockedReason() == BlockedReason.STAGING_INVALID) {
                    throw new RuleViolationException("The staged bytes are not usable; cancel this operation and upload again.");
                }
                if (op.blockedReason() == BlockedReason.COLLISION) {
                    Map<String, Object> set = new HashMap<>();
                    set.put("message", "retried by " + actor.label() + " after a collision; a new target will be planned");
                    ctx.operations().transition(operationId, OperationState.BLOCKED,
                            op.kind() == OperationKind.REPAIR ? OperationState.STAGED : OperationState.STAGED,
                            "retry after collision by " + actor.label(), set);
                } else {
                    ctx.operations().transition(operationId, OperationState.BLOCKED,
                            op.targetPath() == null ? OperationState.STAGED : OperationState.PROMOTING,
                            "retry by " + actor.label() + " after " + op.blockedReason(), Map.of());
                }
            }
            case CLEANUP_INCOMPLETE, WAITING_FOR_VOLUME, STAGED, PROMOTING, PROMOTED, COMMITTED -> ctx.operations().note(operationId,
                    "retry requested by " + actor.label());
            default -> throw new RuleViolationException("This operation is " + op.state().name().toLowerCase() + " and cannot be retried.");
        }
        String job = switch (op.kind()) {
            case INGEST -> PreservationContext.JOB_INGEST;
            case REPAIR -> PreservationContext.JOB_REPAIR;
            case DESTROY -> PreservationContext.JOB_DESTROY;
        };
        ctx.enqueue(job, operationId);
        return ctx.operations().find(operationId).orElseThrow();
    }

    /**
     * Requests cancellation of a BLOCKED or waiting ingest/repair. Nothing is committed; an uncommitted
     * copy is moved to quarantine. The move is done by the worker (the only writer to archival
     * volumes); this records the request and queues it.
     */
    public FileOperation cancel(Actor actor, UUID operationId) {
        FileOperation op = get(actor, operationId);
        requireCancellable(op);
        ctx.operations().note(operationId, "cancellation requested by " + actor.label());
        ctx.jobs().enqueue(PreservationContext.JOB_CANCEL, "{\"id\":\"" + operationId + "\",\"by\":\""
                + PreservationContext.jsonSafe(actor.label()) + "\"}", 5);
        return ctx.operations().find(operationId).orElseThrow();
    }

    private static void requireCancellable(FileOperation op) {
        if (op.kind() == OperationKind.DESTROY) {
            throw new RuleViolationException("A destruction has already retired its File and cannot be cancelled; retry it instead.");
        }
        if (op.state() != OperationState.BLOCKED && op.state() != OperationState.WAITING_FOR_VOLUME) {
            throw new RuleViolationException("Only a blocked or waiting operation can be cancelled.");
        }
    }

    /**
     * Worker side of cancellation, under the operation's lock. If the operation has meanwhile moved on
     * (for example a waiting operation resumed and completed), the request is recorded and ignored.
     *
     * @return true if the operation is now CANCELLED
     */
    public boolean performCancel(UUID operationId, String requestedBy) {
        var held = ctx.locks().tryLock("file-operation", operationId);
        if (held.isEmpty()) {
            throw new IllegalStateException("file operation " + operationId + " is busy; the cancellation will be retried");
        }
        try (var lock = held.get()) {
            FileOperation op = ctx.operations().find(operationId).orElseThrow();
            if (op.kind() == OperationKind.DESTROY
                    || (op.state() != OperationState.BLOCKED && op.state() != OperationState.WAITING_FOR_VOLUME)) {
                ctx.operations().note(operationId, "cancellation by " + requestedBy + " not applied: the operation is now "
                        + op.state());
                return false;
            }
            return applyCancel(op, requestedBy);
        }
    }

    private boolean applyCancel(FileOperation requested, String requestedBy) {
        UUID operationId = requested.operationId();
        // As promotion does: a quarantine move of this operation that was interrupted is settled first, under its
        // own recorded evidence. If it cannot be (something else is at its source now), nothing is cancelled or
        // moved; the refusal is visible and the cancellation is retried.
        if (requested.volumeId() != null) {
            try {
                ManagedVolume volume = null;
                for (QuarantineRepository.Entry pending : ctx.quarantine().forOperation(operationId)) {
                    if (pending.state() == QuarantineRepository.State.INTENDED) {
                        volume = volume == null ? ctx.openVolume(requested.volumeId()) : volume;
                        new QuarantineMoves(ctx).settle(volume, pending);
                    }
                }
            } catch (ManagedVolume.VolumeUnavailableException | IOException e) {
                ctx.operations().note(operationId, "cancellation by " + requestedBy + " is waiting: an interrupted quarantine "
                        + "move could not be settled (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "); nothing was moved");
                throw new IllegalStateException("interrupted quarantine move of " + operationId + " could not be settled", e);
            }
        }
        // Settling may have ended the claim (the object is in quarantine now): decide from the current record.
        FileOperation op = ctx.operations().find(operationId).orElseThrow();
        String quarantine = op.targetPath() == null ? null : ctx.quarantine().forOperation(operationId).stream()
                .filter(e -> e.state() == QuarantineRepository.State.QUARANTINED && op.targetPath().equals(e.sourcePath())
                        && e.reason() == QuarantineRepository.Reason.CANCELLED_COPY)
                .map(QuarantineRepository.Entry::quarantinePath).findFirst().orElse(null);
        String leftInPlace = null;
        OperationCopies.Check unverified = null;
        boolean releaseOnly = false;
        // GAS-P2-009: only a copy this operation's own exclusive create produced may be moved. Anything else
        // at the planned location (never created by this operation: zero attempts, a recorded collision, or
        // an object that appeared while the volume was away) is left exactly where it is and reported.
        // V0018 audit: and the claim alone is not enough - the object must still be shown to be that copy
        // (OperationCopies); otherwise it is left untouched and recorded as unresolved.
        if (op.targetPath() != null && op.targetCreated()) {
            try {
                ManagedVolume volume = ctx.openVolume(op.volumeId());
                OperationCopies.Check own = OperationCopies.check(ctx, op, volume);
                if (own.verdict() == OperationCopies.Verdict.UNVERIFIED) {
                    unverified = own;
                    leftInPlace = op.targetPath();
                } else if (own.verdict() == OperationCopies.Verdict.ABSENT
                        && (op.expectedSize() == null || op.expectedSha256() == null)) {
                    // Nothing there and nothing to identify a copy by: the claim simply ends with the cancellation.
                    releaseOnly = true;
                } else {
                    // ABSENT: the move records that nothing was there and ends the claim. Should a complete copy appear
                    // in between, only the received bytes themselves (the receiver's hash) would be moved.
                    QuarantineMoves.Evidence evidence = own.verified() ? QuarantineMoves.Evidence.operationCopy(own.observed())
                            : new QuarantineMoves.Evidence(op.expectedSize(), op.expectedSha256(),
                            QuarantineRepository.Basis.OPERATION_COPY_VERIFIED);
                    QuarantineRepository.Entry moved = new QuarantineMoves(ctx).move(volume, operationId, op.contentId(),
                            PortableRelativePath.of(op.targetPath()), "cancelled", evidence,
                            QuarantineRepository.Reason.CANCELLED_COPY);
                    if (moved.state() == QuarantineRepository.State.QUARANTINED) {
                        quarantine = moved.quarantinePath();
                    }
                }
            } catch (ManagedVolume.VolumeUnavailableException | IOException e) {
                ctx.operations().note(operationId, "cancellation by " + requestedBy + " is waiting: the uncommitted copy "
                        + "could not be moved to quarantine yet (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
                throw new IllegalStateException("uncommitted copy of " + operationId + " could not be quarantined yet", e);
            }
        } else if (op.targetPath() != null) {
            try {
                // Not verifiably empty (present or undeterminable): reported as left in place. Nothing is touched.
                if (!Presence.probe(ctx, ctx.openVolume(op.volumeId()), op.targetPath()).verifiedAbsent()) {
                    leftInPlace = op.targetPath();
                }
            } catch (ManagedVolume.VolumeUnavailableException e) {
                // nothing is touched either way
            }
        }
        Map<String, Object> set = new HashMap<>();
        set.put("message", "cancelled by " + requestedBy);
        if (quarantine != null) {
            set.put("quarantine_path", quarantine);
        }
        String untouched = unverified != null
                ? "the object at " + leftInPlace + " is not shown to be the copy this operation created (" + unverified.reason()
                + "); it was left untouched and is recorded as unresolved"
                : "an object this operation did not create is at " + leftInPlace + " and was left untouched";
        if (leftInPlace != null) {
            set.put("message", "cancelled by " + requestedBy + "; " + untouched);
        }
        OperationCopies.Check unresolved = unverified;
        boolean release = releaseOnly;
        String detail = "cancelled by " + requestedBy + (quarantine == null ? "" : "; uncommitted copy moved to " + quarantine)
                + (leftInPlace == null ? "" : "; " + untouched);
        Boolean cancelled = ctx.tx().execute(status -> {
            if (unresolved != null) {
                OperationCopies.claimBecomesUnresolved(ctx, op, unresolved);
            } else if (release) {
                ctx.operations().releaseTarget(operationId, op.targetPath());
            }
            if (!ctx.operations().transition(operationId, op.state(), OperationState.CANCELLED, detail, set)) {
                status.setRollbackOnly();
                return false;
            }
            return true;
        });
        if (!Boolean.TRUE.equals(cancelled)) {
            return false;
        }
        ctx.staging().ifPresent(staging -> {
            try {
                if (staging.delete(operationId)) {
                    ctx.operations().markStagingCleaned(operationId);
                }
            } catch (IOException e) {
                // recovery retries the clean-up
            }
        });
        return true;
    }
}
