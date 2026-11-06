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
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Physical removal after a committed destruction (BUILD-SPEC s.37, TX-007). The tombstone and the
 * DESTROY_PENDING content state were committed first; this removes the bytes of every accounted
 * generation. A failure is recorded as CLEANUP_INCOMPLETE and reported - never treated as success.
 */
public final class DestructionProcessor {

    private static final Logger log = LoggerFactory.getLogger(DestructionProcessor.class);

    /** Repair states that can still write, commit or move bytes and are stopped by destruction (GAS-P2-R01). */
    private static final Set<OperationState> STOPPABLE = EnumSet.of(OperationState.STAGED, OperationState.PROMOTING,
            OperationState.PROMOTED, OperationState.WAITING_FOR_VOLUME, OperationState.BLOCKED);

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
     * remains. It also covers copies that unfinished repairs created but never committed (GAS-P2-R01).
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
        // GAS-P2-R01: no other operation may write, commit or move bytes of this content while it is removed.
        List<OperationLocks.Held> writers = new ArrayList<>();
        try {
            Optional<UUID> busy = holdWriters(op, writers);
            if (busy.isPresent()) {
                incomplete(op, "Removal waits for operation " + busy.get() + ", which is still working on this content's "
                        + "bytes. The File is retired; removal is retried.");
                return;
            }
            removeBytes(op, volume);
        } finally {
            for (OperationLocks.Held held : writers) {
                held.close();
            }
        }
    }

    /**
     * Takes the lock of every other operation that could still touch this content's bytes: unfinished
     * ingests/repairs of the content and owners of unsettled quarantine moves of it.
     *
     * @return the first operation that is busy elsewhere, if any (nothing is then removed)
     */
    private Optional<UUID> holdWriters(FileOperation op, List<OperationLocks.Held> held) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
            if (!w.state().isFinal()) {
                ids.add(w.operationId());
            }
        }
        for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
            if (entry.state() == QuarantineRepository.State.INTENDED) {
                ids.add(entry.operationId());
            }
        }
        ids.remove(op.operationId());
        for (UUID id : ids) {
            Optional<OperationLocks.Held> lock = ctx.locks().tryLock("file-operation", id);
            if (lock.isEmpty()) {
                return Optional.of(id);
            }
            held.add(lock.get());
        }
        return Optional.empty();
    }

    /**
     * Stops every unfinished repair of the destroyed content (GAS-P2-R01), under its lock, so none can
     * write, commit or move bytes after removal. A copy one of them created (and only such a copy) is
     * removed below with the content's other copies; an object it did not create is left untouched.
     */
    private void stopUnfinishedRepairs(FileOperation op) {
        for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
            if (w.kind() != OperationKind.REPAIR || !STOPPABLE.contains(w.state())) {
                continue;
            }
            String copy = w.targetPath() == null ? "nothing was written"
                    : w.targetCreated() ? "the uncommitted copy it created at " + w.targetPath() + " is removed by that destruction"
                    : "it created nothing on the volume; any object at " + w.targetPath() + " was not created by it and is left untouched";
            String message = "cancelled: the content was permanently destroyed (operation " + op.operationId() + "); " + copy;
            if (ctx.operations().transition(w.operationId(), w.state(), OperationState.CANCELLED, message,
                    Map.of("message", message))) {
                ctx.operations().note(op.operationId(), "unfinished repair " + w.operationId() + " (" + w.state()
                        + ") stopped; " + copy);
                ctx.staging().ifPresent(staging -> {
                    try {
                        if (staging.delete(w.operationId())) {
                            ctx.operations().markStagingCleaned(w.operationId());
                        }
                    } catch (IOException e) {
                        // recovery retries the clean-up of a final operation's staged bytes
                    }
                });
            }
        }
    }

    /**
     * Uncommitted copies owned by operations on this content: targets their own exclusive create
     * produced (target_created) and that were not since moved away through the quarantine ledger.
     */
    private List<String> ownedUncommittedTargets(FileOperation op, ManagedVolume volume) throws IOException {
        List<String> owned = new ArrayList<>();
        for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
            if (!w.targetCreated() || w.targetPath() == null) {
                continue;
            }
            boolean movedAway = ctx.quarantine().forOperation(w.operationId()).stream()
                    .anyMatch(e -> e.sourcePath().equals(w.targetPath()) && e.state() != QuarantineRepository.State.INTENDED);
            if (movedAway) {
                continue;
            }
            if (!volume.volumeId().equals(w.volumeId())) {
                throw new IOException("operation " + w.operationId() + " wrote a copy on another volume: " + w.targetPath());
            }
            owned.add(w.targetPath());
        }
        return owned;
    }

    private void removeBytes(FileOperation op, ManagedVolume volume) {
        QuarantineMoves moves = new QuarantineMoves(ctx);
        Set<String> known = new LinkedHashSet<>();
        try {
            stopUnfinishedRepairs(op);
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
            known.addAll(ownedUncommittedTargets(op, volume));
            for (String location : known) {
                delete(volume, location);
                ctx.faults().reach(FaultPoints.Point.DURING_PHYSICAL_DELETE, op.operationId());
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
