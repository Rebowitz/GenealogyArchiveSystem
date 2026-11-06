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
import org.genealogyarchive.data.QuarantineRepository;
import java.util.List;
import java.util.ArrayList;
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

    /**
     * Removes every known copy of the content's bytes (GAS-P2-006): each generation's storage location
     * (whatever its state, in case bytes reappeared), each quarantined generation's quarantine copy,
     * and each quarantine-ledger entry for the content - after settling any move that was interrupted
     * before it was recorded. Completion (BYTES_REMOVED) is recorded only after re-checking that none of
     * those locations still holds bytes; otherwise the operation is CLEANUP_INCOMPLETE and says what
     * remains.
     */
    private void removeBytes(FileOperation op) {
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(op.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            ctx.operations().transition(op.operationId(), op.state(), OperationState.WAITING_FOR_VOLUME, e.getMessage(),
                    Map.of("message", e.getMessage()));
            return;
        }
        QuarantineMoves moves = new QuarantineMoves(ctx);
        List<String> known = new ArrayList<>();
        try {
            ctx.faults().reach(FaultPoints.Point.BEFORE_PHYSICAL_DELETE, op.operationId());
            for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
                if (!entry.volumeId().equals(volume.volumeId())) {
                    throw new IOException("a quarantined copy is on another volume: " + entry.quarantinePath());
                }
                moves.settle(volume, entry);
            }
            for (FileContentRepository.Generation g : ctx.contents().generations(op.contentId())) {
                known.add(g.storagePath());
                if (g.quarantinePath() != null) {
                    known.add(g.quarantinePath());
                }
            }
            for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
                if (entry.state() == QuarantineRepository.State.QUARANTINED || entry.state() == QuarantineRepository.State.REMOVED) {
                    known.add(entry.quarantinePath());
                }
            }
            for (String location : known) {
                delete(volume, location);
            }
            for (FileContentRepository.Generation g : ctx.contents().generations(op.contentId())) {
                if (g.state() != GenerationState.DESTROYED) {
                    ctx.contents().setGenerationState(g.contentId(), g.generation(), g.state(), GenerationState.DESTROYED, null);
                }
            }
            for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
                if (entry.state() == QuarantineRepository.State.QUARANTINED) {
                    ctx.quarantine().setState(entry.quarantineId(), QuarantineRepository.State.QUARANTINED,
                            QuarantineRepository.State.REMOVED, "removed by destruction " + op.operationId());
                }
            }
        } catch (IOException e) {
            incomplete(op, "Physical removal is incomplete (" + e.getClass().getSimpleName()
                    + "). The File is retired; its bytes may still be on the volume. Retry from Operations.");
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (String location : known) {
            try {
                Optional<Path> p = volume.locate(PortableRelativePath.of(location));
                if (p.isPresent() && Files.exists(p.get(), LinkOption.NOFOLLOW_LINKS)) {
                    remaining.add(location);
                }
            } catch (IOException e) {
                remaining.add(location + " (could not be checked)");
            }
        }
        if (!remaining.isEmpty()) {
            incomplete(op, "Physical removal is incomplete: bytes remain at " + String.join(", ", remaining)
                    + ". The File is retired. Retry from Operations.");
            return;
        }
        ctx.tx().executeWithoutResult(status -> {
            if (!ctx.operations().transition(op.operationId(), op.state(), OperationState.COMPLETED,
                    "every known copy removed (" + known.size() + " location(s) checked)", Map.of("outcome", "BYTES_REMOVED"))) {
                status.setRollbackOnly();
                return;
            }
            ctx.contents().setState(op.contentId(), ContentState.DESTROY_PENDING, ContentState.DESTROYED);
        });
    }

    private static void delete(ManagedVolume volume, String location) throws IOException {
        Optional<Path> path = volume.locate(PortableRelativePath.of(location));
        if (path.isEmpty() || !Files.exists(path.get(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path p = path.get();
        SafeFiles.requirePlainFile(p);
        SafeFiles.clearReadOnly(p);
        SafeFiles.withSharingRetry(() -> {
            Files.delete(p);
            return null;
        });
        SafeFiles.flushDirectory(p.getParent());
    }

    private void incomplete(FileOperation op, String message) {
        log.warn("Destruction {}: {}", op.operationId(), message);
        if (op.state() == OperationState.CLEANUP_PENDING) {
            ctx.operations().transition(op.operationId(), OperationState.CLEANUP_PENDING, OperationState.CLEANUP_INCOMPLETE,
                    message, Map.of("message", message));
        } else {
            ctx.operations().note(op.operationId(), message);
        }
    }
}
