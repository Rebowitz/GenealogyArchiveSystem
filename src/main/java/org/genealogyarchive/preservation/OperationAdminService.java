package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.FileOperationRepository;
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
                                     java.util.Optional<IntegrityScan> lastScan, String ingestion) {

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

        /** Operations that need a person or are waiting for storage. */
        public long attentionNeeded() {
            return operations(OperationState.BLOCKED) + operations(OperationState.CLEANUP_INCOMPLETE)
                    + operations(OperationState.WAITING_FOR_VOLUME);
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
                ctx.integrity().lastCompleted(), ingestion);
    }

    public List<FileOperation> recent(Actor actor, int limit) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.operations().recent(limit);
    }

    public FileOperation get(Actor actor, UUID operationId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.operations().find(operationId).orElseThrow(() -> new NotFoundException("The operation"));
    }

    public List<FileOperationRepository.Event> events(Actor actor, UUID operationId) {
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

    /** Cancels a BLOCKED or waiting ingest/repair. Nothing is committed; an uncommitted copy goes to quarantine. */
    public FileOperation cancel(Actor actor, UUID operationId) {
        FileOperation op = get(actor, operationId);
        if (op.kind() == OperationKind.DESTROY) {
            throw new RuleViolationException("A destruction has already retired its File and cannot be cancelled; retry it instead.");
        }
        if (op.state() != OperationState.BLOCKED && op.state() != OperationState.WAITING_FOR_VOLUME) {
            throw new RuleViolationException("Only a blocked or waiting operation can be cancelled.");
        }
        String quarantine = null;
        if (op.targetPath() != null && op.blockedReason() != BlockedReason.COLLISION) {
            try {
                ManagedVolume volume = ctx.openVolume(op.volumeId());
                Path target = volume.resolveExisting(PortableRelativePath.of(op.targetPath()));
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    PortableRelativePath q = ManagedVolume.quarantinePath(operationId, "cancelled");
                    Path destination = volume.prepare(q);
                    SafeFiles.withSharingRetry(() -> Files.move(target, destination, LinkOption.NOFOLLOW_LINKS));
                    quarantine = q.value();
                }
            } catch (ManagedVolume.VolumeUnavailableException | IOException e) {
                throw new RuleViolationException("The uncommitted copy could not be moved to quarantine yet ("
                        + e.getClass().getSimpleName() + "); try again when the volume is available.");
            }
        }
        Map<String, Object> set = new HashMap<>();
        set.put("message", "cancelled by " + actor.label());
        if (quarantine != null) {
            set.put("quarantine_path", quarantine);
        }
        if (!ctx.operations().transition(operationId, op.state(), OperationState.CANCELLED,
                "cancelled by " + actor.label() + (quarantine == null ? "" : "; uncommitted copy moved to " + quarantine), set)) {
            throw new RuleViolationException("The operation changed meanwhile; reload and try again.");
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
        return ctx.operations().find(operationId).orElseThrow();
    }
}
