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
import org.genealogyarchive.data.UnresolvedObjectRepository;
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
        Set<UUID> others = otherWriters(op);
        OperationLocks.AllOrBusy writers = ctx.locks().tryLockAll("file-operation", others);
        if (writers.busy().isPresent()) {
            incomplete(op, "Removal waits for operation " + writers.busy().get() + ", which is still working on this content's "
                    + "bytes. The File is retired; removal is retried.");
            return;
        }
        try (OperationLocks.Held held = writers.held().orElseThrow()) {
            Set<UUID> locked = new LinkedHashSet<>(others);
            locked.add(op.operationId());
            removeBytes(op, volume, locked);
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
        // An attested quarantine move, and an Administrator's release or attestation decision, run under the lock
        // of the operation the record names (V0019 audit): holding them all, no record of this content can be
        // released or moved while removal relies on it.
        for (UnresolvedObjectRepository.Entry u : openRecords(op)) {
            ids.add(u.operationId());
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
                if (w.targetCreated()) {
                    // Its staged upload is the evidence that identifies the copy it created (OperationCopies);
                    // it is removed after that copy is.
                    continue;
                }
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
                : w.targetCreated() ? "the uncommitted copy it created at " + w.targetPath() + " is removed by that destruction "
                + "once it is verified to be that copy (otherwise it is left untouched and recorded as unresolved)"
                : "it owns no copy at its planned location " + w.targetPath() + " (copies it moved to quarantine are "
                + "removed with the content's); any object there was not created by it and is left untouched";
        return "cancelled: the content was permanently destroyed (operation " + destruction + "); nothing more is written; "
                + copy;
    }

    /** A copy an operation on this content owns, verified to be that copy (or absent). */
    private record Owned(UUID operationId, String path) {
    }

    /** Where each location stands in one removal pass. */
    private static final class Inventory {
        final Set<String> known = new LinkedHashSet<>();
        final Set<String> historical = new LinkedHashSet<>();
        final Map<String, String> unresolved = new java.util.LinkedHashMap<>();
        /** Operations whose locks this removal holds: only their records' attestations may authorise deletion. */
        final Set<UUID> locked = new java.util.HashSet<>();
    }

    /**
     * Removes every copy of the content's bytes the archive can identify (GAS-P2-006; V0018 audit). The bytes at
     * a location are deleted only if nothing is there or the object is identified by evidence established
     * before now: the authoritative SHA-256; for a quarantined object, the move's hash when its basis was
     * trusted; for an operation's uncommitted copy, {@link OperationCopies}; for an unresolved object, an
     * Administrator's attestation. A location a current record claims whose object is not identified is
     * recorded as unresolved, left untouched, and keeps the removal incomplete (never BYTES_REMOVED) until it
     * is verifiably gone or resolved. Locations the bytes left before (historical) are never touched.
     */
    private void removeBytes(FileOperation op, ManagedVolume volume, Set<UUID> locked) {
        QuarantineMoves moves = new QuarantineMoves(ctx);
        Inventory inv = new Inventory();
        inv.locked.addAll(locked);
        List<Owned> owned = new ArrayList<>();
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
            // An interrupted or legacy missing recording is reconciled before anything is deleted (V0016).
            Set<String> recordedMissing = new java.util.HashSet<>();
            for (IntegrityFinding finding : ctx.integrity().openFindingsFor(op.contentId())) {
                if (finding.kind() == FindingKind.MISSING && finding.storagePath() != null) {
                    recordedMissing.add(finding.storagePath());
                }
            }
            FileContent content = ctx.contents().find(op.contentId()).orElseThrow();
            for (FileContentRepository.Generation g : ctx.contents().generations(op.contentId())) {
                if (g.state() == GenerationState.CURRENT && !g.vacated() && recordedMissing.contains(g.storagePath())) {
                    ctx.contents().markVacated(g.contentId(), g.generation());
                }
            }
            // Unresolved objects that are verifiably gone are closed; the others stay unresolved.
            for (UnresolvedObjectRepository.Entry u : openRecords(op)) {
                if (!present(volume, u.path())) {
                    UnresolvedObjects.resolveAbsent(ctx, u, "nothing was at " + u.path() + " when destruction " + op.operationId()
                            + " checked");
                }
            }
            // Uncommitted copies: removed only if they are verifiably the operation's copy (V0018 audit).
            for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
                if (!w.targetCreated() || w.targetPath() == null) {
                    if (w.targetPath() != null) {
                        inv.historical.add(w.targetPath());
                    }
                    continue;
                }
                if (!volume.volumeId().equals(w.volumeId())) {
                    throw new IOException("operation " + w.operationId() + " wrote a copy on another volume: " + w.targetPath());
                }
                OperationCopies.Check own = OperationCopies.check(ctx, w, volume);
                if (own.verdict() == OperationCopies.Verdict.UNVERIFIED) {
                    ctx.tx().executeWithoutResult(status -> OperationCopies.claimBecomesUnresolved(ctx, w, own));
                    continue;
                }
                owned.add(new Owned(w.operationId(), w.targetPath()));
                inv.known.add(w.targetPath());
            }
            String authoritative = content.sha256();
            List<FileContentRepository.Generation> generations = ctx.contents().generations(op.contentId());
            List<QuarantineRepository.Entry> ledger = ctx.quarantine().forContent(op.contentId());
            for (FileContentRepository.Generation g : generations) {
                switch (g.state()) {
                    case CURRENT -> classify(volume, op, g.storagePath(), Set.of(authoritative), inv, g.vacated()
                            ? UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION : UnresolvedObjectRepository.Kind.CURRENT_COPY,
                            g.generation());
                    // Written before V0019: the superseded copy's identity was only observed.
                    case QUARANTINE_PENDING -> classify(volume, op, g.storagePath(), Set.of(authoritative), inv,
                            UnresolvedObjectRepository.Kind.DAMAGED_COPY, g.generation());
                    case UNRESOLVED -> classify(volume, op, g.storagePath(), Set.of(authoritative), inv,
                            UnresolvedObjectRepository.Kind.DAMAGED_COPY, g.generation());
                    case QUARANTINED -> {
                        classify(volume, op, g.quarantinePath(), trustedMoveHashes(g.quarantinePath(), ledger), inv,
                                UnresolvedObjectRepository.Kind.QUARANTINED_COPY, null);
                        inv.historical.add(g.storagePath());
                    }
                    case MISSING, DESTROYED -> inv.historical.add(g.storagePath());
                }
            }
            for (QuarantineRepository.Entry entry : ledger) {
                inv.historical.add(entry.sourcePath());
                if (entry.state() == QuarantineRepository.State.QUARANTINED) {
                    classify(volume, op, entry.quarantinePath(), trustedMoveHashes(entry.quarantinePath(), ledger), inv,
                            UnresolvedObjectRepository.Kind.QUARANTINED_COPY, null);
                } else if (entry.state() == QuarantineRepository.State.REMOVED) {
                    inv.historical.add(entry.quarantinePath());
                }
            }
            // Every other open record of this content or its operations (an unverified uncommitted copy, for one).
            for (UnresolvedObjectRepository.Entry u : openRecords(op)) {
                if (!inv.known.contains(u.path()) && !inv.unresolved.containsKey(u.path())) {
                    classify(volume, op, u.path(), Set.of(authoritative), inv, u.kind(), u.generation());
                }
            }
            inv.historical.removeAll(inv.known);
            inv.historical.removeAll(inv.unresolved.keySet());
            // V0019 audit: a location the content's bytes left (or were planned for) is not evidence of anything
            // now. Each is re-inspected: empty, or the authoritative bytes -> nothing remains of this content there;
            // an object an Administrator released -> reported only; anything else -> unresolved, untouched, and the
            // removal is not complete. An earlier look (a repair's inspection, a settled move) never substitutes.
            List<String> former = new ArrayList<>(inv.historical);
            inv.historical.clear();
            for (String location : former) {
                classify(volume, op, location, Set.of(authoritative), inv,
                        UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION, null);
            }
            inv.historical.removeAll(inv.known);
            inv.known.removeAll(inv.unresolved.keySet());
            for (String location : inv.known) {
                delete(volume, location);
                ctx.faults().reach(FaultPoints.Point.DURING_PHYSICAL_DELETE, op.operationId());
            }
        } catch (IOException e) {
            incomplete(op, "Physical removal is incomplete (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())
                    + "). The File is retired; its bytes may still be on the volume. Retry from Operations.");
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (String location : inv.known) {
            try {
                if (present(volume, location)) {
                    remaining.add(location);
                }
            } catch (IOException e) {
                remaining.add(location + " (could not be checked)");
            }
        }
        // Released objects at former locations are only reported - but only once they were seen again now.
        List<String> unexplained = new ArrayList<>();
        for (String location : inv.historical) {
            try {
                if (present(volume, location)) {
                    unexplained.add(location);
                }
            } catch (IOException e) {
                remaining.add(location + " (could not be checked)");
            }
        }
        if (!remaining.isEmpty() || !inv.unresolved.isEmpty()) {
            // Never BYTES_REMOVED while an object a current record claims is still on the volume unidentified.
            StringBuilder why = new StringBuilder("Physical removal is incomplete. The File is retired.");
            if (!remaining.isEmpty()) {
                why.append(" Bytes remain at ").append(String.join(", ", remaining)).append('.');
            }
            inv.unresolved.forEach((location, reason) -> why.append(" The archival copy recorded at ").append(location)
                    .append(" was left untouched: ").append(reason).append('.'));
            if (!inv.unresolved.isEmpty()) {
                why.append(" An Administrator must inspect it and resolve it (attest or release it under Preservation, "
                        + "or remove it); then retry from Operations.");
            } else {
                why.append(" Retry from Operations.");
            }
            incomplete(op, why.toString());
            return;
        }
        List<Owned> release = owned;
        Set<String> removed = inv.known;
        String detail = "every known copy removed (" + removed.size() + " location(s) checked)"
                + (unexplained.isEmpty() ? "" : "; objects an Administrator released as not this archive's were left "
                + "untouched at " + String.join(", ", unexplained));
        Boolean completed = ctx.tx().execute(status -> {
            Map<String, Object> set = new java.util.HashMap<>();
            set.put("outcome", "BYTES_REMOVED");
            if (!unexplained.isEmpty()) {
                set.put("message", detail);
            }
            // V0019: every open record is at a location just removed with trusted evidence; otherwise not complete.
            for (UnresolvedObjectRepository.Entry u : openRecords(op)) {
                if (!removed.contains(u.path())) {
                    status.setRollbackOnly();
                    return false;
                }
                ctx.unresolved().resolve(u.unresolvedId(), UnresolvedObjectRepository.Resolution.DESTROYED,
                        "removed by destruction " + op.operationId() + (u.attested() ? " as attested" : " (authoritative bytes)"),
                        null);
            }
            if (!ctx.operations().transition(op.operationId(), op.state(), OperationState.COMPLETED, detail, set)) {
                status.setRollbackOnly();
                return false;
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
            return true;
        });
        if (!Boolean.TRUE.equals(completed)) {
            incomplete(op, "Physical removal is incomplete: an unresolved object appeared while it ran. Retry from Operations.");
            return;
        }
        // The staged uploads kept as evidence of the stopped repairs' copies are no longer needed.
        for (Owned o : release) {
            ctx.staging().ifPresent(staging -> {
                try {
                    if (staging.delete(o.operationId())) {
                        ctx.operations().markStagingCleaned(o.operationId());
                    }
                } catch (IOException e) {
                    // recovery retries the clean-up of a final operation's staged bytes
                }
            });
        }
    }

    /** Open unresolved records of the content and of every operation that wrote (or tried to write) it. */
    private List<UnresolvedObjectRepository.Entry> openRecords(FileOperation op) {
        Map<UUID, UnresolvedObjectRepository.Entry> open = new java.util.LinkedHashMap<>();
        ctx.unresolved().openForContent(op.contentId()).forEach(u -> open.put(u.unresolvedId(), u));
        ctx.unresolved().openForOperation(op.operationId()).forEach(u -> open.put(u.unresolvedId(), u));
        for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
            ctx.unresolved().openForOperation(w.operationId()).forEach(u -> open.put(u.unresolvedId(), u));
        }
        return List.copyOf(open.values());
    }

    /**
     * Decides one current location: absent or identified by {@code trusted} -> removed; an open record that an
     * Administrator attested and that still matches -> removed; released by an Administrator (same object) ->
     * historical; anything else -> recorded as unresolved (once) and left untouched.
     */
    private void classify(ManagedVolume volume, FileOperation op, String location, Set<String> trusted, Inventory inv,
                          UnresolvedObjectRepository.Kind kind, Integer generation) throws IOException {
        // Only verified absence counts as "nothing there"; an inaccessible location stops the pass (retried).
        Presence.Probe probe = Presence.probe(ctx, volume, location);
        if (probe.verifiedAbsent()) {
            inv.known.add(location);
            return;
        }
        if (!probe.present()) {
            throw new Presence.InconclusiveException(location, probe.problem());
        }
        Path p = probe.path();
        ContentIo.Measurement m = null;
        if (!SafeFiles.isLinkOrReparsePoint(p) && probe.attributes().isRegularFile()) {
            m = ContentIo.measure(p);
            if (trusted.contains(m.sha256())) {
                inv.known.add(location);
                return;
            }
        }
        Optional<UnresolvedObjectRepository.Entry> open = ctx.unresolved().openAt(volume.volumeId(), location);
        if (open.isPresent()) {
            UnresolvedObjectRepository.Entry u = open.get();
            if (m != null && u.attested() && inv.locked.contains(u.operationId())
                    && m.sha256().equals(u.observedSha256()) && m.size() == u.observedSize()) {
                inv.known.add(location);
            } else {
                inv.unresolved.put(location, unresolvedReason(u, m));
            }
            return;
        }
        Optional<UnresolvedObjectRepository.Entry> latest = ctx.unresolved().latestAt(volume.volumeId(), location);
        if (m != null && latest.isPresent() && latest.get().resolution() == UnresolvedObjectRepository.Resolution.NOT_ARCHIVAL
                && m.sha256().equals(latest.get().observedSha256())) {
            // An Administrator released this very object as not the archive's: it is only reported.
            inv.historical.add(location);
            return;
        }
        FileContent content = ctx.contents().find(op.contentId()).orElseThrow();
        UnresolvedObjectRepository.Entry u = ctx.unresolved().recordIfAbsent(new UnresolvedObjectRepository.NewEntry(
                volume.volumeId(), location, content.contentId(),
                // A generation is named only for its own storage location (quarantine paths name none).
                generation, op.operationId(), kind, m == null ? null : m.size(), m == null ? null : m.sha256(),
                "Destruction " + op.operationId() + " found an object at " + location + " that it cannot identify as a copy "
                        + "of content " + content.contentId() + " (" + describe(kind) + "). It was left untouched."));
        inv.unresolved.put(location, unresolvedReason(u, m));
    }

    private static String describe(UnresolvedObjectRepository.Kind kind) {
        return switch (kind) {
            case CURRENT_COPY -> "it does not have the authoritative SHA-256";
            case OBJECT_AT_VACATED_LOCATION -> "this content's bytes left this location (or were only planned for it), "
                    + "and the object now there does not have the authoritative SHA-256";
            case DAMAGED_COPY -> "a superseded damaged copy; only an observation of it was ever recorded";
            case QUARANTINED_COPY -> "no trusted hash of what was moved to quarantine identifies it";
            case OPERATION_COPY -> "it is not shown to be the copy the operation created";
        };
    }

    private static String unresolvedReason(UnresolvedObjectRepository.Entry u, ContentIo.Measurement now) {
        String why = describe(u.kind());
        if (u.attested() && now != null && !now.sha256().equals(u.observedSha256())) {
            why = "it is no longer the object an Administrator attested";
        }
        return why + " (unresolved object " + u.unresolvedId() + ")";
    }

    /**
     * Hashes that identify a quarantined object: the move's recorded SHA-256 when it was trusted when recorded
     * (V0019). A settled ledger record, an untrusted (observed) hash or a damage observation never does.
     */
    private Set<String> trustedMoveHashes(String quarantinePath, List<QuarantineRepository.Entry> ledger) {
        Set<String> trusted = new java.util.HashSet<>();
        for (QuarantineRepository.Entry e : ledger) {
            if (quarantinePath.equals(e.quarantinePath()) && e.hasTrustedEvidence() && attestationHonoured(e)) {
                trusted.add(e.sourceSha256());
            }
        }
        return trusted;
    }

    /**
     * An attested move's hash authorises deletion only together with the record that move resolved as
     * QUARANTINED (V0019 audit): a move recorded after a release - which the lifecycle prevents - would have no
     * such record and authorises nothing. Other trusted moves need no record.
     */
    private boolean attestationHonoured(QuarantineRepository.Entry e) {
        if (e.reason() != QuarantineRepository.Reason.ATTESTED_OBJECT) {
            return true;
        }
        return ctx.unresolved().resolvedAs(e.volumeId(), e.sourcePath(), UnresolvedObjectRepository.Resolution.QUARANTINED,
                e.sourceSha256());
    }

    /** @throws Presence.InconclusiveException unless presence or absence is verified */
    private boolean present(ManagedVolume volume, String location) throws IOException {
        return Presence.exists(ctx, volume, location);
    }

    private void delete(ManagedVolume volume, String location) throws IOException {
        if (!Presence.exists(ctx, volume, location)) {
            return;
        }
        Path p = volume.locate(PortableRelativePath.of(location)).orElseThrow(() ->
                new Presence.InconclusiveException(location, "it vanished while being examined"));
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
