package org.genealogyarchive.preservation;

import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.platform.SafeFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Physical removal after a committed destruction (BUILD-SPEC s.37, TX-007). The tombstone and the
 * DESTROY_PENDING content state were committed first; this removes the bytes of every accounted
 * generation. A failure is recorded as CLEANUP_INCOMPLETE and reported - never treated as success.
 */
public final class DestructionProcessor {

    private static final Logger log = LoggerFactory.getLogger(DestructionProcessor.class);

    private final PreservationContext ctx;

    public DestructionProcessor(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** @return true if this call held the operation's lock */
    public boolean process(UUID operationId) {
        Optional<OperationLocks.Held> held = ctx.locks().tryLock("file-operation", operationId);
        if (held.isEmpty()) {
            return false;
        }
        try (OperationLocks.Held lock = held.get()) {
            FileOperation op = ctx.operations().find(operationId).orElseThrow();
            if (op.kind() != OperationKind.DESTROY) {
                return true;
            }
            switch (op.state()) {
                case CLEANUP_PENDING, CLEANUP_INCOMPLETE -> removeBytes(op);
                case WAITING_FOR_VOLUME -> {
                    try {
                        ctx.openVolume(op.volumeId());
                        ctx.operations().transition(op.operationId(), OperationState.WAITING_FOR_VOLUME,
                                OperationState.CLEANUP_PENDING, "volume available again; resuming removal");
                        removeBytes(ctx.operations().find(operationId).orElseThrow());
                    } catch (ManagedVolume.VolumeUnavailableException e) {
                        // still waiting
                    }
                }
                default -> { }
            }
            return true;
        }
    }

    private void removeBytes(FileOperation op) {
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            ctx.operations().transition(op.operationId(), op.state(), OperationState.WAITING_FOR_VOLUME, e.getMessage(),
                    Map.of("message", e.getMessage()));
            return;
        }
        try {
            ctx.faults().reach(FaultPoints.Point.BEFORE_PHYSICAL_DELETE, op.operationId());
            for (FileContentRepository.Generation g : ctx.contents().generations(op.contentId())) {
                if (g.state() != GenerationState.CURRENT && g.state() != GenerationState.QUARANTINE_PENDING) {
                    continue;
                }
                Path path = volume.resolveExisting(PortableRelativePath.of(g.storagePath()));
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    SafeFiles.clearReadOnly(path);
                    SafeFiles.withSharingRetry(() -> {
                        Files.delete(path);
                        return null;
                    });
                    SafeFiles.flushDirectory(path.getParent());
                }
                ctx.contents().setGenerationState(g.contentId(), g.generation(), g.state(), GenerationState.DESTROYED, null);
            }
        } catch (IOException e) {
            String message = "Physical removal is incomplete (" + e.getClass().getSimpleName()
                    + "). The File is retired; its bytes may still be on the volume. Retry from Operations.";
            log.warn("Destruction {}: {}", op.operationId(), message);
            if (op.state() == OperationState.CLEANUP_PENDING) {
                ctx.operations().transition(op.operationId(), OperationState.CLEANUP_PENDING, OperationState.CLEANUP_INCOMPLETE,
                        message, Map.of("message", message));
            } else {
                ctx.operations().note(op.operationId(), message);
            }
            return;
        }
        ctx.tx().executeWithoutResult(status -> {
            if (!ctx.operations().transition(op.operationId(), op.state(), OperationState.COMPLETED,
                    "bytes removed from the volume", Map.of("outcome", "BYTES_REMOVED"))) {
                status.setRollbackOnly();
                return;
            }
            ctx.contents().setState(op.contentId(), ContentState.DESTROY_PENDING, ContentState.DESTROYED);
        });
    }
}
