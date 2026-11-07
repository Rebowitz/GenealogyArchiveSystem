package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
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
                if (!ctx.unresolved().resolve(current.unresolvedId(), UnresolvedObjectRepository.Resolution.NOT_ARCHIVAL,
                        "released by " + actor.label() + ": " + why, actor.isSystem() ? null : actor.userId())) {
                    throw new RuleViolationException("This object was already resolved.");
                }
                retireGeneration(current, GenerationState.MISSING);
                ctx.operations().note(current.operationId(), "unresolved object at " + current.path() + " released by "
                        + actor.label() + " as not the archive's copy" + (current.attested()
                        ? " (its earlier attestation is withdrawn; no move of it had been recorded)" : "") + ": " + why);
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
                    + actor.label() + " as the archive's object (SHA-256 " + current.observedSha256() + "): " + why);
            ctx.enqueue(PreservationContext.JOB_QUARANTINE_ATTESTED, current.unresolvedId());
        });
        return ctx.unresolved().find(unresolvedId).orElseThrow();
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
    static void resolveAbsent(PreservationContext ctx, UnresolvedObjectRepository.Entry u, String how) {
        ctx.tx().executeWithoutResult(status -> {
            if (ctx.unresolved().resolve(u.unresolvedId(), UnresolvedObjectRepository.Resolution.ABSENT, how, null)) {
                new UnresolvedObjects(ctx).retireGeneration(u, GenerationState.MISSING);
            }
        });
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
