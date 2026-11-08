package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.data.UnresolvedObjectRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unresolved objects (V0018 audit, V0019): occupied locations whose object the archive cannot show to be its
 * own. They are never moved or deleted on the archive's own judgement; a record stays open across restarts,
 * scans and repeated recovery until one of:
 * <ul>
 *   <li>the object is verifiably gone (a scan or destruction finds nothing there) - ABSENT;</li>
 *   <li>an Administrator releases it as not the archive's copy - NOT_ARCHIVAL (left where it is; reported as
 *       an unexpected object from then on);</li>
 *   <li>an Administrator attests that the recorded observation is the archive's object - the worker then moves it
 *       to quarantine against that hash (QUARANTINED), or destruction removes it (DESTROYED). An object that no
 *       longer matches the attested hash is not moved.</li>
 * </ul>
 */
public final class UnresolvedObjects {

    private static final Logger log = LoggerFactory.getLogger(UnresolvedObjects.class);

    private final PreservationContext ctx;

    public UnresolvedObjects(PreservationContext ctx) {
        this.ctx = ctx;
    }

    public List<UnresolvedObjectRepository.Entry> open(Actor actor) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.unresolved().open();
    }

    public UnresolvedObjectRepository.Entry get(Actor actor, UUID unresolvedId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.unresolved().find(unresolvedId).orElseThrow(() -> new NotFoundException("The unresolved object"));
    }

    /**
     * The Administrator has inspected the object and says it is not the archive's copy. Nothing on the volume
     * changes; the record closes and a superseded generation that pointed there becomes MISSING.
     * <p>
     * V0019 audit: a release is a final decision about the object, so it is synchronised with everything that
     * could act on the record's authorization. It takes the lock of the operation the record names - held by
     * an attested move from its check to its settlement and by a destruction for its whole pass - and is refused
     * while someone holds it. Under that lock and the record's row lock, it is refused once a move of the object
     * was recorded (the object is in quarantine or about to be, and the worker or recovery finishes the move);
     * otherwise it resolves the record, which no worker, retry or recovery can act on afterwards.
     */
    public UnresolvedObjectRepository.Entry release(Actor actor, UUID unresolvedId, String reason) {
        UnresolvedObjectRepository.Entry u = get(actor, unresolvedId);
        String why = requireReason(reason);
        var held = ctx.locks().tryLock("file-operation", u.operationId());
        if (held.isEmpty()) {
            throw new RuleViolationException("This object is being worked on right now (a move to quarantine or a "
                    + "destruction is using it). Nothing was changed; try again in a moment.");
        }
        try (var lock = held.get()) {
            // V0020: the decision is bound to the object examined for it, under the lock - CONFIRMED only if it is
            // exactly the recorded object. A release is never refused for that (leaving an object is never destructive),
            // but only a CONFIRMED release identifies an object later.
            String examined = examine(u);
            UnresolvedObjectRepository.DecisionEvidence evidence = examined == null
                    ? UnresolvedObjectRepository.DecisionEvidence.CONFIRMED : UnresolvedObjectRepository.DecisionEvidence.UNCONFIRMED;
            ctx.tx().executeWithoutResult(status -> {
                UnresolvedObjectRepository.Entry current = ctx.unresolved().lockForUpdate(unresolvedId).orElseThrow();
                if (!current.open()) {
                    throw new RuleViolationException("This object was already resolved (" + current.resolution() + ").");
                }
                Optional<QuarantineRepository.Entry> started = attestedMove(current);
                if (started.isPresent()) {
                    throw new RuleViolationException("A move of this object to quarantine was already recorded under the "
                            + "attestation (" + started.get().state() + ", destination " + started.get().quarantinePath()
                            + "). It is finished by the worker or by recovery; the object is then in quarantine and its "
                            + "record shows the outcome. Nothing was changed.");
                }
                if (!ctx.unresolved().release(current.unresolvedId(), "released by " + actor.label() + ": " + why,
                        actor.isSystem() ? null : actor.userId(), evidence)) {
                    throw new RuleViolationException("This object was already resolved.");
                }
                retireGeneration(current, GenerationState.MISSING);
                ctx.operations().note(current.operationId(), "unresolved object at " + current.path() + " released by "
                        + actor.label() + " as not the archive's copy" + (current.attested()
                        ? " (its earlier attestation is withdrawn; no move of it had been recorded)" : "") + ": " + why
                        + (examined == null ? "; the object examined was exactly the one recorded"
                        : "; the release identifies no object later, because " + examined));
            });
        }
        return ctx.unresolved().find(unresolvedId).orElseThrow();
    }

    /**
     * The Administrator attests that the object recorded (with the size and SHA-256 shown) is the archive's
     * copy. The worker moves it to quarantine against exactly that hash; destruction may remove it.
     * <p>
     * V0019 audit: the attestation, its operation event (the audit record) and the job that carries out the
     * move are one transaction - none exists without the others. Repeating the request is harmless: an
     * attestation already recorded is returned as it is and no second job is created.
     */
    public UnresolvedObjectRepository.Entry attest(Actor actor, UUID unresolvedId, String reason) {
        UnresolvedObjectRepository.Entry u = get(actor, unresolvedId);
        String why = requireReason(reason);
        if (actor.isSystem() || actor.userId() == null) {
            throw new RuleViolationException("An attestation is made by a named Administrator.");
        }
        if (u.attested() || !u.open()) {
            return attestUnderLock(actor, u, why);
        }
        // V0020: an attestation is bound to the object examined for it. It is made only if the object at the location
        // is exactly the one recorded - examined under the lock every move, release or destruction of it needs.
        var held = ctx.locks().tryLock("file-operation", u.operationId());
        if (held.isEmpty()) {
            throw new RuleViolationException("This object is being worked on right now (a move to quarantine or a "
                    + "destruction is using it). Nothing was changed; try again in a moment.");
        }
        try (var lock = held.get()) {
            if (u.identityEvidence() == UnresolvedObjectRepository.IdentityEvidence.LEGACY) {
                throw new RuleViolationException("This object was recorded before the archive kept identity evidence "
                        + "(times and file key), so an attestation could not be bound to it. Release it, or remove it on "
                        + "the volume; a later check records whatever is there afresh. Nothing was changed.");
            }
            String examined = examine(u);
            if (examined != null) {
                throw new RuleViolationException("The object at " + u.path() + " is not the one recorded (" + examined
                        + "), so it cannot be attested. Nothing was changed. Release this record; whatever is there now is "
                        + "recorded afresh by the next check.");
            }
            return attestUnderLock(actor, u, why);
        }
    }

    private UnresolvedObjectRepository.Entry attestUnderLock(Actor actor, UnresolvedObjectRepository.Entry u, String why) {
        UUID unresolvedId = u.unresolvedId();
        ctx.tx().executeWithoutResult(status -> {
            UnresolvedObjectRepository.Entry current = ctx.unresolved().lockForUpdate(unresolvedId).orElseThrow();
            if (!current.open()) {
                throw new RuleViolationException("This object was already resolved (" + current.resolution() + ").");
            }
            if (current.attested()) {
                return;
            }
            if (current.observedSha256() == null) {
                throw new RuleViolationException("No size and SHA-256 were recorded for this object, so there is nothing to "
                        + "attest. Inspect it on the volume and release it, or remove it there.");
            }
            if (!ctx.unresolved().attest(current.unresolvedId(), actor.userId())) {
                throw new RuleViolationException("This object was already resolved or attested.");
            }
            ctx.operations().note(current.operationId(), "unresolved object at " + current.path() + " attested by "
                    + actor.label() + " as the archive's object (SHA-256 " + current.observedSha256() + "; the object "
                    + "examined was exactly the one recorded): " + why);
            ctx.enqueue(PreservationContext.JOB_QUARANTINE_ATTESTED, current.unresolvedId());
        });
        return ctx.unresolved().find(unresolvedId).orElseThrow();
    }

    /**
     * Re-examines the object at a record's location against the identity recorded for it (V0020).
     *
     * @return null if it is exactly the recorded object; otherwise why it is not shown to be
     */
    private String examine(UnresolvedObjectRepository.Entry u) {
        Optional<UnresolvedObjectRepository.Identity> recorded = u.recordedIdentity();
        if (recorded.isEmpty()) {
            return u.identityEvidence() == UnresolvedObjectRepository.IdentityEvidence.LEGACY
                    ? "it was recorded before identity evidence was kept" : "no plain file was recorded there";
        }
        try {
            ManagedVolume volume = ctx.openVolume(u.volumeId());
            ObjectIdentity.Observation now = ObjectIdentity.observe(ctx, volume, u.path());
            if (now.absent()) {
                return "nothing is at " + u.path() + " now";
            }
            return ObjectIdentity.difference(recorded.get(), now.identity());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            return "the volume could not be examined (" + e.getMessage() + ")";
        } catch (IOException e) {
            return "the location could not be examined (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")";
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RuleViolationException("Give a short reason; it is kept with the record.");
        }
        String r = reason.strip();
        return r.length() > 2000 ? r.substring(0, 2000) : r;
    }

    /** The quarantine location an attested move of this record uses (one per record, so at most one move). */
    static PortableRelativePath attestedDestination(UnresolvedObjectRepository.Entry u) {
        return ManagedVolume.quarantinePath(u.operationId(), attestedSuffix(u));
    }

    private static String attestedSuffix(UnresolvedObjectRepository.Entry u) {
        return "att-" + u.unresolvedId().toString().replace("-", "");
    }

    private Optional<QuarantineRepository.Entry> attestedMove(UnresolvedObjectRepository.Entry u) {
        return ctx.quarantine().find(u.volumeId(), attestedDestination(u).value());
    }

    /**
     * Worker side of an attestation: moves the attested object to quarantine, verified against the attested
     * size and SHA-256. Content being destroyed is left to destruction (which removes an attested object
     * directly); a quarantined object needs no move.
     * <p>
     * V0019 audit: the whole execution - re-reading the authorization, recording the intent, the rename and its
     * settlement - happens under the lock of the operation the record names, which a release also needs. The
     * authorization is re-read under that lock immediately before the intent is recorded, so a released record
     * is never moved by a queued, retried or resumed job; a move whose intent was recorded before a crash is
     * settled only while the attestation is still current (see {@link QuarantineMoves#settle}).
     *
     * @return true if the record is now resolved
     */
    public boolean quarantineAttested(UUID unresolvedId) {
        UnresolvedObjectRepository.Entry first = ctx.unresolved().find(unresolvedId).orElseThrow();
        if (!first.open() || !first.attested() || first.kind() == UnresolvedObjectRepository.Kind.QUARANTINED_COPY
                || first.kind() == UnresolvedObjectRepository.Kind.CURRENT_COPY) {
            return !first.open();
        }
        if (first.contentId() != null) {
            ContentState state = ctx.contents().find(first.contentId()).map(FileContent::state).orElse(ContentState.DESTROYED);
            if (state == ContentState.DESTROY_PENDING || state == ContentState.DESTROYED) {
                return false;
            }
        }
        var held = ctx.locks().tryLock("file-operation", first.operationId());
        if (held.isEmpty()) {
            throw new IllegalStateException("operation " + first.operationId() + " is busy; the attested move will be retried");
        }
        UnresolvedObjectRepository.Entry u = first;
        try (var lock = held.get()) {
            // The current authorization, read under the lock a release needs: nothing can withdraw it from here on.
            u = ctx.unresolved().find(unresolvedId).orElseThrow();
            if (!u.open()) {
                return true;
            }
            if (!u.attested()) {
                return false;
            }
            ManagedVolume volume = ctx.openVolume(u.volumeId());
            // V0020: only an attestation bound to the object (CONFIRMED) moves anything, and only that object.
            if (!u.decisionConfirmed()) {
                ctx.operations().note(u.operationId(), "attested object at " + u.path() + " was not moved: the attestation "
                        + "was made before identity evidence was kept, so it is not bound to an object. Release the record, "
                        + "or remove the object on the volume.");
                return false;
            }
            ObjectIdentity.Observation now = ObjectIdentity.observe(ctx, volume, u.path());
            String changed = now.absent() ? null : ObjectIdentity.difference(u.recordedIdentity().orElseThrow(), now.identity());
            if (changed != null) {
                ctx.operations().note(u.operationId(), "attested object at " + u.path() + " was not moved: the object there "
                        + "is not the one attested (" + changed + "). It is left untouched; the record stays open.");
                return false;
            }
            ctx.faults().reach(FaultPoints.Point.BEFORE_QUARANTINE_MOVE, u.operationId());
            QuarantineRepository.Entry moved = new QuarantineMoves(ctx).move(volume, u.operationId(), u.contentId(),
                    PortableRelativePath.of(u.path()), attestedSuffix(u),
                    new QuarantineMoves.Evidence(u.observedSize(), u.observedSha256(),
                            QuarantineRepository.Basis.ADMINISTRATOR_ATTESTED),
                    QuarantineRepository.Reason.ATTESTED_OBJECT);
            ctx.operations().note(u.operationId(), "attested object from " + u.path() + (moved.state()
                    == QuarantineRepository.State.QUARANTINED ? " moved to " + moved.quarantinePath() : " was no longer there"));
            return true;
        } catch (QuarantineMoves.UnexplainedObjectException e) {
            ctx.operations().note(u.operationId(), "attested object at " + u.path() + " was not moved: " + e.getMessage());
            log.warn("Attested quarantine of {} refused: {}", u.path(), e.getMessage());
            return false;
        } catch (IOException e) {
            ctx.operations().note(u.operationId(), "attested object at " + u.path() + " could not be moved yet ("
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")");
            throw new IllegalStateException("attested move of " + u.path() + " failed; it will be retried", e);
        }
    }

    /** Closes a record whose object is verifiably gone; a superseded generation pointing there becomes MISSING. */
    /**
     * Closes a record as ABSENT, following the same lifecycle as every other final decision about it (second V0019
     * audit, finding 3). Only:
     * <ul>
     *   <li>under the lock of the operation the record names - held by an attested move from its check to its
     *       settlement, by destruction for its pass, and needed by a release (a caller that does not already hold
     *       it tries once and, if it is busy, leaves the record open for a later pass);</li>
     *   <li>when no move from that location is recorded but unsettled - absence caused by a move in progress is
     *       the move's to settle, which records the single final disposition;</li>
     *   <li>on a probe taken after the lock was acquired that verifies absence on the configured volume;</li>
     *   <li>in a transaction holding the record's row lock, if it is still open.</li>
     * </ul>
     *
     * @param operationLockHeld whether the caller already holds the lock of {@code u.operationId()}
     * @return true if the record is now closed as ABSENT
     */
    static boolean closeIfVerifiedAbsent(PreservationContext ctx, ManagedVolume volume, UnresolvedObjectRepository.Entry u,
                                         String how, boolean operationLockHeld) {
        Optional<OperationLocks.Held> held = Optional.empty();
        if (!operationLockHeld) {
            held = ctx.locks().tryLock("file-operation", u.operationId());
            if (held.isEmpty()) {
                return false;
            }
        }
        try {
            if (!ctx.quarantine().intendedFrom(u.volumeId(), u.path()).isEmpty()) {
                return false;
            }
            if (!Presence.probe(ctx, volume, u.path()).verifiedAbsent()) {
                return false;
            }
            ctx.faults().reach(FaultPoints.Point.BEFORE_ABSENCE_RECORDED, u.operationId());
            Boolean closed = ctx.tx().execute(status -> {
                UnresolvedObjectRepository.Entry current = ctx.unresolved().lockForUpdate(u.unresolvedId()).orElseThrow();
                if (!current.open() || !ctx.quarantine().intendedFrom(u.volumeId(), u.path()).isEmpty()) {
                    return false;
                }
                ctx.unresolved().resolve(current.unresolvedId(), UnresolvedObjectRepository.Resolution.ABSENT, how, null);
                new UnresolvedObjects(ctx).retireGeneration(current, GenerationState.MISSING);
                return true;
            });
            return Boolean.TRUE.equals(closed);
        } catch (IOException e) {
            return false;
        } finally {
            held.ifPresent(OperationLocks.Held::close);
        }
    }

    private void retireGeneration(UnresolvedObjectRepository.Entry u, GenerationState to) {
        if (u.contentId() == null || u.generation() == null) {
            return;
        }
        Optional<org.genealogyarchive.data.FileContentRepository.Generation> g =
                ctx.contents().generation(u.contentId(), u.generation());
        if (g.isPresent() && g.get().state() == GenerationState.UNRESOLVED && g.get().storagePath().equals(u.path())) {
            ctx.contents().setGenerationState(u.contentId(), u.generation(), GenerationState.UNRESOLVED, to, null);
        }
    }
}
