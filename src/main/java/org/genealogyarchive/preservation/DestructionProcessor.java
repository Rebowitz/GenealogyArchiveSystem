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
        // All of their locks are held together on one pooled connection, however many there are.
        OperationLocks.AllOrBusy writers = ctx.locks().tryLockAll("file-operation", otherWriters(op));
        if (writers.busy().isPresent()) {
            incomplete(op, "Removal waits for operation " + writers.busy().get() + ", which is still working on this content's "
                    + "bytes. The File is retired; removal is retried.");
            return;
        }
        try (OperationLocks.Held held = writers.held().orElseThrow()) {
            removeBytes(op, volume);
        }
    }

    /**
     * Every other operation that could still touch this content's bytes: unfinished ingests/repairs of the
     * content and owners of unsettled quarantine moves of it.
     */
    private Set<UUID> otherWriters(FileOperation op) {
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
        return ids;
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
            String message = stoppedRepairMessage(op.operationId(), w);
            if (ctx.operations().transition(w.operationId(), w.state(), OperationState.CANCELLED, message,
                    Map.of("message", message))) {
                ctx.operations().note(op.operationId(), "unfinished repair " + w.operationId() + " (" + w.state()
                        + ") stopped");
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

    /** Why an unfinished repair was stopped by destruction (intent or removal), and what of it remains. */
    static String stoppedRepairMessage(UUID destruction, FileOperation w) {
        String copy = w.targetPath() == null ? "nothing was written"
                : w.targetCreated() ? "the uncommitted copy it created at " + w.targetPath() + " is removed by that destruction"
                : "it owns no copy at its planned location " + w.targetPath() + " (copies it moved to quarantine are "
                + "removed with the content's); any object there was not created by it and is left untouched";
        return "cancelled: the content was permanently destroyed (operation " + destruction + "); nothing more is written; "
                + copy;
    }

    /** A copy an operation on this content owns: its own exclusive create produced it (GAS-P2-FV01). */
    private record Owned(UUID operationId, String path) {
    }

    /**
     * Copies owned by operations on this content (target_created). Ownership ends only when the copy is
     * accounted elsewhere - a confirmed quarantine move releases it in the same transaction - so this
     * includes a copy recreated at a location an earlier move vacated, and never an object the operation
     * did not create.
     */
    private List<Owned> ownedTargets(FileOperation op, ManagedVolume volume) throws IOException {
        List<Owned> owned = new ArrayList<>();
        for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
            if (!w.targetCreated() || w.targetPath() == null) {
                continue;
            }
            if (!volume.volumeId().equals(w.volumeId())) {
                throw new IOException("operation " + w.operationId() + " wrote a copy on another volume: " + w.targetPath());
            }
            owned.add(new Owned(w.operationId(), w.targetPath()));
        }
        return owned;
    }

    private void removeBytes(FileOperation op, ManagedVolume volume) {
        QuarantineMoves moves = new QuarantineMoves(ctx);
        Set<String> known = new LinkedHashSet<>();
        Set<String> historical = new LinkedHashSet<>();
        List<Owned> owned;
        try {
            stopUnfinishedRepairs(op);
            ctx.faults().reach(FaultPoints.Point.BEFORE_PHYSICAL_DELETE, op.operationId());
            // Interrupted moves are settled first, so every location below is current (GAS-P2-IV01).
            for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
                if (!entry.volumeId().equals(volume.volumeId())) {
                    throw new IOException("a quarantined copy is on another volume: " + entry.quarantinePath());
                }
                moves.settle(volume, entry);
            }
            // Current physical inventory only: a location counts if a record says the bytes are there now.
            // Locations bytes were moved away from (or never reached) are historical and never deleted.
            // A current location the archive recorded as vacated (V0016; or with an open MISSING finding) is not
            // evidence that whatever is there now is the content: it is removed only if it verifiably is.
            Set<String> recordedMissing = new java.util.HashSet<>();
            for (IntegrityFinding finding : ctx.integrity().openFindingsFor(op.contentId())) {
                if (finding.kind() == FindingKind.MISSING && finding.storagePath() != null) {
                    recordedMissing.add(finding.storagePath());
                }
            }
            FileContent content = ctx.contents().find(op.contentId()).orElseThrow();
            // V0017: every object destruction deletes must be shown to be this content's copy now. A location a
            // record names is deleted only if the object there has a SHA-256 that record vouches for: the
            // authoritative hash, the damaged copy's hash recorded when the damage was found, or the hash recorded
            // when it was moved to quarantine. Anything else there is left untouched and reported.
            java.util.Map<String, String> movedSha = new java.util.HashMap<>();
            for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
                if (entry.state() == QuarantineRepository.State.QUARANTINED && entry.sourceSha256() != null) {
                    movedSha.put(entry.quarantinePath(), entry.sourceSha256());
                }
            }
            for (FileContentRepository.Generation g : ctx.contents().generations(op.contentId())) {
                switch (g.state()) {
                    case CURRENT, QUARANTINE_PENDING -> {
                        boolean vacated = g.vacated() || recordedMissing.contains(g.storagePath());
                        if (vacated && !g.vacated() && g.state() == GenerationState.CURRENT) {
                            // An interrupted or legacy recording: reconcile it before anything is deleted.
                            ctx.contents().markVacated(g.contentId(), g.generation());
                        }
                        Set<String> vouched = new java.util.HashSet<>(Set.of(content.sha256()));
                        if (!vacated) {
                            vouched.addAll(damagedCopyHashes(g));
                        }
                        classify(volume, g.storagePath(), vouched, known, historical);
                    }
                    case QUARANTINED -> {
                        classifyQuarantined(volume, g.quarantinePath(), movedSha, known, historical);
                        historical.add(g.storagePath());
                    }
                    case MISSING, DESTROYED -> historical.add(g.storagePath());
                }
            }
            for (QuarantineRepository.Entry entry : ctx.quarantine().forContent(op.contentId())) {
                historical.add(entry.sourcePath());
                if (entry.state() == QuarantineRepository.State.QUARANTINED) {
                    classifyQuarantined(volume, entry.quarantinePath(), movedSha, known, historical);
                } else if (entry.state() == QuarantineRepository.State.REMOVED) {
                    historical.add(entry.quarantinePath());
                }
            }
            owned = ownedTargets(op, volume);
            owned.forEach(o -> known.add(o.path()));
            for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
                if (w.targetPath() != null && !w.targetCreated()) {
                    historical.add(w.targetPath());
                }
            }
            historical.removeAll(known);
            for (String location : known) {
                delete(volume, location);
                ctx.faults().reach(FaultPoints.Point.DURING_PHYSICAL_DELETE, op.operationId());
            }
        } catch (IOException e) {
            incomplete(op, "Physical removal is incomplete (" + e.getClass().getSimpleName()
                    + "). The File is retired; its bytes may still be on the volume. Retry from Operations.");
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (String location : known) {
            try {
                if (present(volume, location)) {
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
        // Objects at historical locations are not this content's copies: untouched, and reported.
        List<String> unexplained = new ArrayList<>();
        for (String location : historical) {
            try {
                if (present(volume, location)) {
                    unexplained.add(location);
                }
            } catch (IOException e) {
                unexplained.add(location + " (could not be checked)");
            }
        }
        List<Owned> release = owned;
        String detail = "every known copy removed (" + known.size() + " location(s) checked)"
                + (unexplained.isEmpty() ? "" : "; objects this archive did not record as copies of this content were left "
                + "untouched at " + String.join(", ", unexplained));
        ctx.tx().executeWithoutResult(status -> {
            Map<String, Object> set = new java.util.HashMap<>();
            set.put("outcome", "BYTES_REMOVED");
            if (!unexplained.isEmpty()) {
                set.put("message", detail);
            }
            if (!ctx.operations().transition(op.operationId(), op.state(), OperationState.COMPLETED, detail, set)) {
                status.setRollbackOnly();
                return;
            }
            // The removal is reconciled in one transaction: every record of a current copy is retired with it.
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
            ctx.contents().setState(op.contentId(), ContentState.DESTROY_PENDING, ContentState.DESTROYED);
            for (Owned o : release) {
                ctx.operations().releaseTarget(o.operationId(), o.path());
            }
        });
    }

    /** SHA-256 values integrity checking recorded for a generation's damaged copy. */
    private Set<String> damagedCopyHashes(FileContentRepository.Generation g) {
        Set<String> hashes = new java.util.HashSet<>();
        for (IntegrityFinding f : ctx.integrity().findingsFor(g.contentId())) {
            if ((f.kind() == FindingKind.HASH_MISMATCH || f.kind() == FindingKind.SIZE_MISMATCH)
                    && Integer.valueOf(g.generation()).equals(f.generation()) && f.observedSha256() != null) {
                hashes.add(f.observedSha256());
            }
        }
        return hashes;
    }

    /**
     * A location is a known copy only if nothing is there or the object there has a vouched-for SHA-256;
     * otherwise it is not shown to be the content's and becomes an unexplained location (never deleted).
     */
    private static void classify(ManagedVolume volume, String location, Set<String> vouched, Set<String> known,
                                 Set<String> historical) throws IOException {
        Optional<Path> p = volume.locate(PortableRelativePath.of(location));
        if (p.isEmpty() || !Files.exists(p.get(), LinkOption.NOFOLLOW_LINKS)) {
            known.add(location);
            return;
        }
        SafeFiles.requirePlainFile(p.get());
        if (vouched.contains(ContentIo.measure(p.get()).sha256())) {
            known.add(location);
        } else {
            historical.add(location);
        }
    }

    /** A quarantined copy is vouched for by the hash recorded when it was moved (V0017; by its record before). */
    private static void classifyQuarantined(ManagedVolume volume, String location, java.util.Map<String, String> movedSha,
                                            Set<String> known, Set<String> historical) throws IOException {
        String sha = movedSha.get(location);
        if (sha == null) {
            // Moved before V0017: the settled ledger record is the only evidence available (documented).
            known.add(location);
            return;
        }
        classify(volume, location, Set.of(sha), known, historical);
    }

    private static boolean present(ManagedVolume volume, String location) throws IOException {
        Optional<Path> p = volume.locate(PortableRelativePath.of(location));
        return p.isPresent() && Files.exists(p.get(), LinkOption.NOFOLLOW_LINKS);
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
