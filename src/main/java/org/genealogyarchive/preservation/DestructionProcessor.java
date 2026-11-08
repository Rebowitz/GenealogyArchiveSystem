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

    /** What, besides a hash, claims a location for deletion (fourth V0019 audit). */
    private enum Basis {
        /** The content's CURRENT generation at its storage location, while the content is DESTROY_PENDING. */
        CURRENT_GENERATION,
        /** An operation's live claim ({@code target_created}) on a copy {@link OperationCopies} verified. */
        OPERATION_COPY,
        /** A QUARANTINED ledger entry with trusted (and, if attested, honoured) evidence of what it moved. */
        QUARANTINE_MOVE,
        /** An open record an Administrator attested, whose operation's lock this removal holds. */
        ATTESTATION
    }

    /**
     * Why one object may be deleted: the claim, and the object's identity (size, SHA-256, times, file key - V0020)
     * as identified when the decision was made. The deletion helper re-establishes every part of it immediately
     * before deleting (fourth V0019 audit).
     *
     * @param claimant the claiming operation (OPERATION_COPY) or record (ATTESTATION); otherwise null
     */
    private record Authorization(Basis basis, UUID claimant, Integer generation, UnresolvedObjectRepository.Kind kind,
                                 UnresolvedObjectRepository.Identity identity) {
    }

    /**
     * A released object as classified: the identity that matched a CONFIRMED release's persisted evidence (V0020),
     * and what the location was examined as. Reconciliation counts the release as still holding only for an object
     * with exactly this identity. Path occupancy or a hash alone never carries a release over to another object.
     */
    private record Released(UnresolvedObjectRepository.Identity identity, UnresolvedObjectRepository.Kind kind,
                            Integer generation) {
    }

    /** A location verified absent when examined: what it was examined as, for reconciliation if it is occupied later. */
    private record Absent(UnresolvedObjectRepository.Kind kind, Integer generation) {
    }

    /**
     * Where each location stands in one removal pass (fourth V0019 audit). Every examined location is in exactly
     * one category; only {@code deletable} is ever handed to the deletion helper, and membership there is not
     * itself authority - the helper re-establishes it.
     */
    private static final class Inventory {
        /** Verified absent when examined. Never a deletion instruction; probed again after the deletions. */
        final Map<String, Absent> absent = new java.util.LinkedHashMap<>();
        /** Currently claimed objects identified for deletion, each with its authorization. */
        final Map<String, Authorization> deletable = new java.util.LinkedHashMap<>();
        /** Objects identified as ones an Administrator released, with that evidence: left untouched, re-examined. */
        final Map<String, Released> released = new java.util.LinkedHashMap<>();
        /** Unresolved or unclaimed objects: recorded, left untouched; they keep the removal incomplete. */
        final Map<String, String> unresolved = new java.util.LinkedHashMap<>();
        /** Former locations still to be examined (they claim nothing). */
        final Set<String> former = new LinkedHashSet<>();
        /** Locations whose object this pass deleted. */
        final Set<String> deleted = new java.util.HashSet<>();
        /** Locations that could not be checked again at reconciliation. */
        final List<String> uncheckable = new ArrayList<>();
        /** Operations whose locks this removal holds: only their records' attestations may authorise deletion. */
        final Set<UUID> locked = new java.util.HashSet<>();

        boolean examined(String location) {
            return absent.containsKey(location) || deletable.containsKey(location) || released.containsKey(location)
                    || unresolved.containsKey(location);
        }
    }

    /**
     * Removes every copy of the content's bytes the archive can identify (GAS-P2-006; V0018 audit). The bytes at
     * a location are deleted only if the object is identified by evidence established before now: the
     * authoritative SHA-256 at the current generation's location; for a quarantined object, the move's hash when
     * its basis was trusted; for an operation's uncommitted copy, {@link OperationCopies}; for an unresolved
     * object, an Administrator's attestation. A location a current record claims whose object is not identified
     * is recorded as unresolved, left untouched, and keeps the removal incomplete (never BYTES_REMOVED) until it
     * is verifiably gone or resolved. Locations the bytes left before (historical) are never deleted, and a
     * location verified absent is never a deletion instruction (fourth V0019 audit): whatever appears there later
     * is reconciled as unresolved (or, if released, reported).
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
                    UnresolvedObjects.closeIfVerifiedAbsent(ctx, volume, u, "nothing was at " + u.path() + " when destruction "
                            + op.operationId() + " checked", inv.locked.contains(u.operationId()));
                }
            }
            // Uncommitted copies: deletable only if they are verifiably the operation's copy (V0018 audit).
            for (FileOperation w : ctx.operations().writersOfContent(op.contentId())) {
                if (!w.targetCreated() || w.targetPath() == null) {
                    if (w.targetPath() != null) {
                        inv.former.add(w.targetPath());
                    }
                    continue;
                }
                if (!volume.volumeId().equals(w.volumeId())) {
                    throw new IOException("operation " + w.operationId() + " wrote a copy on another volume: " + w.targetPath());
                }
                if (!ctx.unresolved().releasedHashesAt(volume.volumeId(), w.targetPath()).isEmpty()) {
                    // An Administrator decided about this location before: decided by classify (never by the claim).
                    inv.former.add(w.targetPath());
                    continue;
                }
                OperationCopies.Check own = OperationCopies.check(ctx, w, volume);
                switch (own.verdict()) {
                    case UNVERIFIED -> ctx.tx().executeWithoutResult(status -> OperationCopies.claimBecomesUnresolved(ctx, w, own));
                    case ABSENT -> {
                        // Nothing to delete. The claim is released at completion; an object appearing here meanwhile is
                        // reconciled as unresolved, never deleted on the strength of this look.
                        owned.add(new Owned(w.operationId(), w.targetPath()));
                        inv.absent.putIfAbsent(w.targetPath(), new Absent(UnresolvedObjectRepository.Kind.OPERATION_COPY, null));
                    }
                    case COMPLETE, PARTIAL -> {
                        owned.add(new Owned(w.operationId(), w.targetPath()));
                        inv.deletable.putIfAbsent(w.targetPath(), new Authorization(Basis.OPERATION_COPY, w.operationId(), null,
                                UnresolvedObjectRepository.Kind.OPERATION_COPY, own.identity()));
                    }
                }
            }
            String authoritative = content.sha256();
            // Third V0019 audit: a hash identifies an object only where a current record claims the location for this
            // content. Superseded generations, former locations and open records claim nothing now: no hash is
            // trusted there (an attestation still is, see classify), so their objects - matching or not - are left.
            java.util.function.Predicate<UnresolvedObjectRepository.Identity> noClaim = id -> false;
            List<FileContentRepository.Generation> generations = ctx.contents().generations(op.contentId());
            List<QuarantineRepository.Entry> ledger = ctx.quarantine().forContent(op.contentId());
            for (FileContentRepository.Generation g : generations) {
                switch (g.state()) {
                    case CURRENT -> classify(volume, op, g.storagePath(), id -> id.sha256().equals(authoritative), Basis.CURRENT_GENERATION, inv,
                            g.vacated() ? UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION
                                    : UnresolvedObjectRepository.Kind.CURRENT_COPY, g.generation());
                    // Written before V0019: the superseded copy's identity was only observed.
                    case QUARANTINE_PENDING, UNRESOLVED -> classify(volume, op, g.storagePath(), noClaim, null, inv,
                            UnresolvedObjectRepository.Kind.DAMAGED_COPY, g.generation());
                    case QUARANTINED -> {
                        classify(volume, op, g.quarantinePath(), id -> trustedMoveMatches(g.quarantinePath(), ledger, id),
                                Basis.QUARANTINE_MOVE, inv, UnresolvedObjectRepository.Kind.QUARANTINED_COPY, null);
                        inv.former.add(g.storagePath());
                    }
                    case MISSING, DESTROYED -> inv.former.add(g.storagePath());
                }
            }
            for (QuarantineRepository.Entry entry : ledger) {
                inv.former.add(entry.sourcePath());
                if (entry.state() == QuarantineRepository.State.QUARANTINED) {
                    classify(volume, op, entry.quarantinePath(), id -> trustedMoveMatches(entry.quarantinePath(), ledger, id),
                            Basis.QUARANTINE_MOVE, inv, UnresolvedObjectRepository.Kind.QUARANTINED_COPY, null);
                } else if (entry.state() == QuarantineRepository.State.REMOVED) {
                    inv.former.add(entry.quarantinePath());
                }
            }
            // Every other open record of this content or its operations (an unverified uncommitted copy, for one).
            for (UnresolvedObjectRepository.Entry u : openRecords(op)) {
                if (!inv.deletable.containsKey(u.path()) && !inv.unresolved.containsKey(u.path())) {
                    classify(volume, op, u.path(), noClaim, null, inv, u.kind(), u.generation());
                }
            }
            // A location the content's bytes left (or were planned for) is owned by nothing now. Each is re-inspected:
            // verified empty -> nothing there; an object an Administrator released -> reported only; any other object,
            // even with the authoritative bytes -> unresolved, untouched, removal incomplete until an Administrator
            // attests or releases it. An earlier look (a repair's inspection, a settled move) never substitutes.
            for (String location : List.copyOf(inv.former)) {
                if (!inv.examined(location)) {
                    classify(volume, op, location, noClaim, null, inv,
                            UnresolvedObjectRepository.Kind.OBJECT_AT_VACATED_LOCATION, null);
                }
            }
            // An unresolved decision about a location wins over any other (the most conservative category).
            inv.deletable.keySet().removeAll(inv.unresolved.keySet());
            inv.absent.keySet().removeAll(inv.unresolved.keySet());
            inv.absent.keySet().removeAll(inv.deletable.keySet());
            inv.absent.keySet().removeAll(inv.released.keySet());
            // The inventory is complete; nothing below relies on it alone (fourth V0019 audit).
            ctx.faults().reach(FaultPoints.Point.AFTER_INVENTORY, op.operationId());
            for (Map.Entry<String, Authorization> d : inv.deletable.entrySet()) {
                deleteAuthorized(volume, op, d.getKey(), d.getValue(), inv);
                ctx.faults().reach(FaultPoints.Point.DURING_PHYSICAL_DELETE, op.operationId());
            }
            ctx.faults().reach(FaultPoints.Point.BEFORE_RECONCILIATION, op.operationId());
        } catch (IOException e) {
            incomplete(op, "Physical removal is incomplete (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())
                    + "). The File is retired; its bytes may still be on the volume. Retry from Operations.");
            return;
        }
        // Reconciliation: every location found empty - before the deletions or by them - is checked again. Whatever
        // has appeared is never deleted: released -> reported; anything else -> unresolved, removal incomplete.
        Map<String, Absent> toCheck = new java.util.LinkedHashMap<>(inv.absent);
        for (String location : inv.deleted) {
            Authorization a = inv.deletable.get(location);
            toCheck.putIfAbsent(location, new Absent(a.kind(), a.generation()));
        }
        Set<String> empty = new LinkedHashSet<>();
        for (Map.Entry<String, Absent> c : toCheck.entrySet()) {
            try {
                if (reconcile(volume, op, c.getKey(), c.getValue(), inv)) {
                    empty.add(c.getKey());
                }
            } catch (IOException e) {
                inv.uncheckable.add(c.getKey() + " (could not be checked)");
            }
        }
        // Released objects (fifth V0019 audit): reported only if the object there now is the one classified, by the
        // same evidence. Gone -> nothing to account for; anything else -> a replacement, unresolved.
        List<String> unexplained = new ArrayList<>();
        for (Map.Entry<String, Released> r : inv.released.entrySet()) {
            try {
                if (stillTheReleasedObject(volume, op, r.getKey(), r.getValue(), inv)) {
                    unexplained.add(r.getKey());
                }
            } catch (IOException e) {
                inv.uncheckable.add(r.getKey() + " (could not be checked)");
            }
        }
        if (!inv.uncheckable.isEmpty() || !inv.unresolved.isEmpty()) {
            // Never BYTES_REMOVED while an object a current record claims is still on the volume unidentified.
            StringBuilder why = new StringBuilder("Physical removal is incomplete. The File is retired.");
            if (!inv.uncheckable.isEmpty()) {
                why.append(" Locations not verified empty: ").append(String.join(", ", inv.uncheckable)).append('.');
            }
            inv.unresolved.forEach((location, reason) -> why.append(" The object at ").append(location)
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
        Set<String> deletedNow = inv.deleted;
        Set<String> gone = empty;
        String detail = "every known copy removed (" + deletedNow.size() + " object(s) deleted, "
                + (gone.size() - deletedNow.size()) + " location(s) verified already empty)"
                + (unexplained.isEmpty() ? "" : "; objects an Administrator released as not this archive's were left "
                + "untouched at " + String.join(", ", unexplained));
        Boolean completed = ctx.tx().execute(status -> {
            Map<String, Object> set = new java.util.HashMap<>();
            set.put("outcome", "BYTES_REMOVED");
            if (!unexplained.isEmpty()) {
                set.put("message", detail);
            }
            // V0019: every open record is at a location this pass emptied or verified empty; otherwise not complete.
            for (UnresolvedObjectRepository.Entry u : openRecords(op)) {
                if (!gone.contains(u.path())) {
                    status.setRollbackOnly();
                    return false;
                }
                // The record states what physically happened: deleted by this pass (by attestation or its location's
                // current owner) or verified absent - never DESTROYED for an object this pass did not delete.
                boolean wasDeleted = deletedNow.contains(u.path());
                ctx.unresolved().resolve(u.unresolvedId(), wasDeleted ? UnresolvedObjectRepository.Resolution.DESTROYED
                                : UnresolvedObjectRepository.Resolution.ABSENT,
                        (wasDeleted ? "removed by destruction " : "verified absent by destruction ") + op.operationId()
                                + (wasDeleted && u.attested() ? " as attested" : ""), null);
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
                // An object an Administrator released stays in quarantine, and its ledger entry says so.
                if (entry.state() == QuarantineRepository.State.QUARANTINED && gone.contains(entry.quarantinePath())) {
                    ctx.quarantine().setState(entry.quarantineId(), QuarantineRepository.State.QUARANTINED,
                            QuarantineRepository.State.REMOVED, (deletedNow.contains(entry.quarantinePath())
                                    ? "removed by destruction " : "verified absent by destruction ") + op.operationId());
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
     * Decides one location: verified absent -> {@code absent} (never a deletion instruction); identified by
     * {@code identifies} under the location's current claim ({@code basis}) -> {@code deletable}; an open record that
     * an Administrator attested (bound to the object, V0020), whose lock is held, and whose object is still exactly the
     * one attested -> {@code deletable}; the object of a CONFIRMED release, exactly -> {@code released}; anything else
     * -> recorded as unresolved (once, with the identity observed now) and left.
     */
    private void classify(ManagedVolume volume, FileOperation op, String location, java.util.function.Predicate<UnresolvedObjectRepository.Identity> identifies, Basis basis,
                          Inventory inv, UnresolvedObjectRepository.Kind kind, Integer generation) throws IOException {
        // Only verified absence counts as "nothing there"; an inaccessible location stops the pass (retried).
        ObjectIdentity.Observation seen = ObjectIdentity.observe(ctx, volume, location);
        if (seen.absent()) {
            inv.absent.putIfAbsent(location, new Absent(kind, generation));
            return;
        }
        UnresolvedObjectRepository.Identity id = seen.identity();
        Optional<UnresolvedObjectRepository.Entry> open = ctx.unresolved().openAt(volume.volumeId(), location);
        // Authorization first (second V0019 audit): an Administrator's release of this location is a decision about
        // the object, and no hash - not even the authoritative one - overrides it. Nothing here is ever deleted. The
        // released object is left and reported only if it is exactly the object the release was bound to (V0020);
        // anything else is unresolved.
        List<UnresolvedObjectRepository.Entry> releases = ctx.unresolved().releasedAt(volume.volumeId(), location);
        if (!releases.isEmpty()) {
            String notIt = notTheReleasedObject(open, id, releases, inv.absent.containsKey(location));
            if (notIt == null) {
                inv.released.put(location, new Released(id, kind, generation));
                return;
            }
            UnresolvedObjectRepository.Entry u = open.orElseGet(() -> newRecord(volume, op, location, kind, generation, id));
            inv.unresolved.put(location, unresolvedReason(u, id) + "; an Administrator released the object recorded here "
                    + "earlier, but " + notIt + ", so nothing at this location is removed or accounted for without a new decision");
            return;
        }
        // Identity second: a location still this content's (no contrary decision) whose object is identified.
        if (id != null && basis != null && open.isEmpty() && identifies.test(id)) {
            inv.deletable.putIfAbsent(location, new Authorization(basis, null, generation, kind, id));
            return;
        }
        if (open.isPresent()) {
            UnresolvedObjectRepository.Entry u = open.get();
            if (id != null && attestationBindsTo(u, id, inv)) {
                inv.deletable.putIfAbsent(location, new Authorization(Basis.ATTESTATION, u.unresolvedId(), u.generation(),
                        u.kind(), id));
            } else {
                inv.unresolved.put(location, unresolvedReason(u, id));
            }
            return;
        }
        UnresolvedObjectRepository.Entry u = newRecord(volume, op, location, kind, generation, id);
        inv.unresolved.put(location, unresolvedReason(u, id));
    }

    /**
     * Whether an open record's attestation authorises this removal to delete the object identified as {@code id}:
     * attested, bound to the object it was made about (CONFIRMED, V0020), its operation's lock held by this removal,
     * and the object still exactly the recorded one. A LEGACY attestation authorises nothing.
     */
    private static boolean attestationBindsTo(UnresolvedObjectRepository.Entry u, UnresolvedObjectRepository.Identity id,
                                              Inventory inv) {
        return u.open() && u.attested() && u.decisionConfirmed() && inv.locked.contains(u.operationId())
                && u.recordedIdentity().map(id::equals).orElse(false);
    }

    /**
     * Reconciliation (fourth V0019 audit): a location found empty is checked again. An object that has appeared is
     * never deleted, adopted, or taken for a released object (its disappearance was observed, so neither path nor
     * bytes show it is the same object): it is recorded as unresolved and keeps the removal incomplete.
     *
     * @return true if the location is verified empty now
     * @throws Presence.InconclusiveException if it cannot be determined
     */
    private boolean reconcile(ManagedVolume volume, FileOperation op, String location, Absent was, Inventory inv)
            throws IOException {
        ObjectIdentity.Observation seen = ObjectIdentity.observe(ctx, volume, location);
        if (seen.absent()) {
            return true;
        }
        refuse(volume, op, location, was.kind(), was.generation(), seen.identity(), "an object appeared at " + location
                + " after destruction found it empty; it is not shown to be this content's copy", inv);
        return false;
    }

    /**
     * Leaves the object at {@code location} untouched and records it as unresolved (an existing open record is kept);
     * it keeps the removal incomplete. The refusal is an operation event. A refused object never inherits an earlier
     * release (fifth V0019 audit): only classification, on its evidence, identifies a released object.
     */
    private void refuse(ManagedVolume volume, FileOperation op, String location, UnresolvedObjectRepository.Kind kind,
                        Integer generation, UnresolvedObjectRepository.Identity id, String why, Inventory inv) {
        Optional<UnresolvedObjectRepository.Entry> open = ctx.unresolved().openAt(volume.volumeId(), location);
        UnresolvedObjectRepository.Entry u = open.orElseGet(() -> newRecord(volume, op, location, kind, generation, id));
        inv.unresolved.put(location, why + " (unresolved object " + u.unresolvedId() + ")");
        ctx.operations().note(op.operationId(), "not deleted: " + why + "; recorded as unresolved object " + u.unresolvedId());
    }

    /**
     * Why the object at a released location is not shown to be the released object, or null if it is (V0020): no
     * open record; a plain file; the location not found empty earlier in this pass (a disappearance was seen); and
     * exactly the identity persisted for an object whose release was CONFIRMED - made about that very object. A
     * LEGACY or UNCONFIRMED release, a hash, or the path never identifies anything.
     */
    private static String notTheReleasedObject(Optional<UnresolvedObjectRepository.Entry> open,
                                               UnresolvedObjectRepository.Identity id,
                                               List<UnresolvedObjectRepository.Entry> releases, boolean seenEmpty) {
        if (open.isPresent()) {
            return "an unresolved record is open at this location";
        }
        if (id == null) {
            return "the object there now is not a plain file";
        }
        if (seenEmpty) {
            return "the location was found empty earlier in this pass, so the object there now is not shown to be it";
        }
        List<UnresolvedObjectRepository.Identity> bound = releases.stream()
                .filter(UnresolvedObjectRepository.Entry::decisionConfirmed)
                .map(r -> r.recordedIdentity().orElseThrow()).toList();
        if (bound.isEmpty()) {
            return "no release here is bound to an object (made before identity evidence was kept, or the object "
                    + "examined for it was not the one recorded)";
        }
        if (bound.contains(id)) {
            return null;
        }
        if (bound.stream().noneMatch(r -> r.size() == id.size() && r.sha256().equals(id.sha256()))) {
            return "the object there now does not have the size and SHA-256 of the released object";
        }
        return "the object there now has the released object's bytes but not its filesystem identity (creation, "
                + "modification time or file key)";
    }

    /**
     * Final reconciliation of a released location (fifth V0019 audit). Verified empty: the released object is gone and
     * there is nothing to account for (noted). Present with exactly the identity seen at classification (which matched
     * the release's persisted evidence): the release still holds. Anything else - different bytes, or the same bytes
     * with other times or file key - is a replacement: recorded unresolved, never deleted.
     *
     * @return true if the released object is still there and is reported
     * @throws Presence.InconclusiveException if it cannot be determined
     */
    private boolean stillTheReleasedObject(ManagedVolume volume, FileOperation op, String location, Released was,
                                           Inventory inv) throws IOException {
        ObjectIdentity.Observation seen = ObjectIdentity.observe(ctx, volume, location);
        if (seen.absent()) {
            ctx.operations().note(op.operationId(), "the object an Administrator released at " + location
                    + " is no longer there (verified absent)");
            return false;
        }
        String change = ObjectIdentity.difference(was.identity(), seen.identity());
        if (change == null) {
            return true;
        }
        refuse(volume, op, location, was.kind(), was.generation(), seen.identity(), "the object at " + location
                + " is not the released object identified earlier in this pass (" + change + "); a release does not pass "
                + "to a replacement", inv);
        return false;
    }

    private UnresolvedObjectRepository.Entry newRecord(ManagedVolume volume, FileOperation op, String location,
                                                       UnresolvedObjectRepository.Kind kind, Integer generation,
                                                       UnresolvedObjectRepository.Identity observed) {
        FileContent content = ctx.contents().find(op.contentId()).orElseThrow();
        return ctx.unresolved().recordIfAbsent(new UnresolvedObjectRepository.NewEntry(
                volume.volumeId(), location, content.contentId(),
                // A generation is named only for its own storage location (quarantine paths name none).
                generation, op.operationId(), kind, observed,
                "Destruction " + op.operationId() + " found an object at " + location + " that it cannot identify as a copy "
                        + "of content " + content.contentId() + " (" + describe(kind) + "). It was left untouched."));
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

    private static String unresolvedReason(UnresolvedObjectRepository.Entry u, UnresolvedObjectRepository.Identity now) {
        String why = describe(u.kind());
        if (u.attested() && !u.decisionConfirmed()) {
            why = "an Administrator attested it before identity evidence was kept, so the attestation is not bound to "
                    + "an object and authorises nothing; release the record or remove the object on the volume";
        } else if (u.attested() && !u.recordedIdentity().map(r -> r.equals(now)).orElse(false)) {
            why = "it is no longer the object an Administrator attested";
        }
        return why + " (unresolved object " + u.unresolvedId() + ")";
    }

    /**
     * Whether the object identified as {@code id} at a quarantine location is the object a trusted move put there
     * (V0019; V0020 integration): a QUARANTINED ledger entry for this content at that location whose evidence was
     * trusted when recorded, whose attestation (for an attested move) is bound to a CONFIRMED decision, and whose move
     * evidence - size, SHA-256 and recorded modification time - the object matches. Never the hash alone: a same-byte
     * object with another modification time is not the moved object. A settled ledger record, an untrusted (observed)
     * hash, a LEGACY attestation or a damage observation identifies nothing.
     */
    private boolean trustedMoveMatches(String quarantinePath, List<QuarantineRepository.Entry> ledger,
                                       UnresolvedObjectRepository.Identity id) {
        for (QuarantineRepository.Entry e : ledger) {
            if (quarantinePath.equals(e.quarantinePath()) && e.state() == QuarantineRepository.State.QUARANTINED
                    && e.hasTrustedEvidence() && attestationHonoured(e) && ObjectIdentity.matchesMove(id, e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An attested move's evidence authorises deletion only together with the record that move resolved as QUARANTINED
     * (V0019 audit), and only if that record's attestation was bound to the moved object (V0020 integration): RECORDED
     * evidence, a CONFIRMED decision, and the same size, SHA-256 and modification time as the move. A move under a
     * LEGACY or unconfirmed attestation authorises nothing; the copy then needs a new decision. Other trusted moves need
     * no record.
     */
    private boolean attestationHonoured(QuarantineRepository.Entry e) {
        if (e.reason() != QuarantineRepository.Reason.ATTESTED_OBJECT) {
            return true;
        }
        return ctx.unresolved().resolvedAt(e.volumeId(), e.sourcePath(), UnresolvedObjectRepository.Resolution.QUARANTINED)
                .stream().anyMatch(u -> ObjectIdentity.bindsMove(u, e));
    }

    /** @throws Presence.InconclusiveException unless presence or absence is verified */
    private boolean present(ManagedVolume volume, String location) throws IOException {
        return Presence.exists(ctx, volume, location);
    }

    /**
     * The one place archival bytes are deleted (fourth V0019 audit). Inventory membership is not authority: for the
     * one object it is about to delete, this re-establishes the configured volume's identity ({@link Presence}),
     * that the claim named by {@code auth} still holds in the database now, that no Administrator's release or
     * other open record applies to the location, and that the object is a plain file with exactly the identity
     * (size, SHA-256, times and file key - V0020) identified when the decision was made. Failing any of these it deletes nothing and records the
     * object as unresolved (or, if released, reports it). An empty location is never "deleted".
     * <p>
     * Residual window (P2-E, accepted): the final identity check and {@link Files#delete} are separate calls; Java
     * has no portable "delete only if still this object". An object swapped in between them by a local writer
     * could still be deleted. These checks narrow that window; they do not close it.
     */
    private void deleteAuthorized(ManagedVolume volume, FileOperation op, String location, Authorization auth, Inventory inv)
            throws IOException {
        ObjectIdentity.Observation seen = ObjectIdentity.observe(ctx, volume, location);
        if (seen.absent()) {
            // Gone meanwhile: nothing deleted; reconciliation checks it again and it is accounted as absent.
            inv.absent.putIfAbsent(location, new Absent(auth.kind(), auth.generation()));
            return;
        }
        if (seen.unidentifiable()) {
            refuse(volume, op, location, auth.kind(), auth.generation(), null, "the object at " + location
                    + " is not a plain file", inv);
            return;
        }
        Path p = seen.probe().path();
        String broken = claimBroken(op, volume, location, auth, inv);
        String changed = broken == null ? ObjectIdentity.difference(auth.identity(), seen.identity()) : null;
        if (changed != null) {
            broken = "the object at " + location + " is no longer the one identified for deletion (" + changed + ")";
        }
        if (broken != null) {
            refuse(volume, op, location, auth.kind(), auth.generation(), seen.identity(), broken, inv);
            return;
        }
        SafeFiles.clearReadOnly(p);
        SafeFiles.withSharingRetry(() -> {
            Files.delete(p);
            return null;
        });
        SafeFiles.flushDirectory(p.getParent());
        inv.deleted.add(location);
    }

    /** @return why the claim no longer authorises deleting the object at {@code location}, or null if it does */
    private String claimBroken(FileOperation op, ManagedVolume volume, String location, Authorization auth, Inventory inv) {
        if (!ctx.unresolved().releasedHashesAt(volume.volumeId(), location).isEmpty()) {
            return "an Administrator released the object recorded at " + location;
        }
        Optional<UnresolvedObjectRepository.Entry> open = ctx.unresolved().openAt(volume.volumeId(), location);
        if (open.isPresent() && (auth.basis() != Basis.ATTESTATION || !open.get().unresolvedId().equals(auth.claimant()))) {
            return "the object at " + location + " is recorded as unresolved";
        }
        FileContent content = ctx.contents().find(op.contentId()).orElse(null);
        if (content == null || content.state() != ContentState.DESTROY_PENDING) {
            return "content " + op.contentId() + " is no longer pending destruction";
        }
        return switch (auth.basis()) {
            case CURRENT_GENERATION -> ctx.contents().generations(op.contentId()).stream()
                    .anyMatch(g -> auth.generation() != null && g.generation() == auth.generation()
                            && g.state() == GenerationState.CURRENT && location.equals(g.storagePath())
                            && auth.identity().sha256().equals(content.sha256()))
                    ? null : "the current generation no longer claims " + location;
            case OPERATION_COPY -> ctx.operations().find(auth.claimant())
                    .filter(w -> w.targetCreated() && location.equals(w.targetPath()) && volume.volumeId().equals(w.volumeId()))
                    .isPresent() ? null : "operation " + auth.claimant() + " no longer claims a copy at " + location;
            case QUARANTINE_MOVE -> {
                List<QuarantineRepository.Entry> ledger = ctx.quarantine().forContent(op.contentId());
                boolean held = ledger.stream().anyMatch(e -> location.equals(e.quarantinePath())
                        && e.state() == QuarantineRepository.State.QUARANTINED);
                yield held && trustedMoveMatches(location, ledger, auth.identity()) ? null
                        : "no quarantine move with trusted evidence claims " + location;
            }
            case ATTESTATION -> open.filter(u -> attestationBindsTo(u, auth.identity(), inv))
                    .isPresent() ? null : "the Administrator's attestation no longer applies to " + location;
        };
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
