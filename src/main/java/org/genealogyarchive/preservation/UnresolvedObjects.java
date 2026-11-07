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
     */
    public UnresolvedObjectRepository.Entry release(Actor actor, UUID unresolvedId, String reason) {
        UnresolvedObjectRepository.Entry u = get(actor, unresolvedId);
        String why = requireReason(reason);
        ctx.tx().executeWithoutResult(status -> {
            if (!ctx.unresolved().resolve(u.unresolvedId(), UnresolvedObjectRepository.Resolution.NOT_ARCHIVAL,
                    "released by " + actor.label() + ": " + why, actor.isSystem() ? null : actor.userId())) {
                throw new RuleViolationException("This object was already resolved.");
            }
            retireGeneration(u, GenerationState.MISSING);
        });
        return ctx.unresolved().find(unresolvedId).orElseThrow();
    }

    /**
     * The Administrator attests that the object recorded (with the size and SHA-256 shown) is the archive's
     * copy. The worker moves it to quarantine against exactly that hash; destruction may remove it.
     */
    public UnresolvedObjectRepository.Entry attest(Actor actor, UUID unresolvedId, String reason) {
        UnresolvedObjectRepository.Entry u = get(actor, unresolvedId);
        String why = requireReason(reason);
        if (actor.isSystem() || actor.userId() == null) {
            throw new RuleViolationException("An attestation is made by a named Administrator.");
        }
        if (u.observedSha256() == null) {
            throw new RuleViolationException("No size and SHA-256 were recorded for this object, so there is nothing to "
                    + "attest. Inspect it on the volume and release it, or remove it there.");
        }
        if (!ctx.unresolved().attest(u.unresolvedId(), actor.userId())) {
            throw new RuleViolationException("This object was already resolved or attested.");
        }
        ctx.operations().note(u.operationId(), "unresolved object at " + u.path() + " attested by " + actor.label()
                + " as the archive's object (SHA-256 " + u.observedSha256() + "): " + why);
        ctx.enqueue(PreservationContext.JOB_QUARANTINE_ATTESTED, u.unresolvedId());
        return ctx.unresolved().find(unresolvedId).orElseThrow();
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RuleViolationException("Give a short reason; it is kept with the record.");
        }
        String r = reason.strip();
        return r.length() > 2000 ? r.substring(0, 2000) : r;
    }

    /**
     * Worker side of an attestation: moves the attested object to quarantine, verified against the attested
     * size and SHA-256, under the lock of the operation the record names. Content being destroyed is left to
     * destruction (which removes an attested object directly); a quarantined object needs no move.
     *
     * @return true if the record is now resolved
     */
    public boolean quarantineAttested(UUID unresolvedId) {
        UnresolvedObjectRepository.Entry u = ctx.unresolved().find(unresolvedId).orElseThrow();
        if (!u.open() || !u.attested() || u.kind() == UnresolvedObjectRepository.Kind.QUARANTINED_COPY
                || u.kind() == UnresolvedObjectRepository.Kind.CURRENT_COPY) {
            return !u.open();
        }
        if (u.contentId() != null) {
            ContentState state = ctx.contents().find(u.contentId()).map(FileContent::state).orElse(ContentState.DESTROYED);
            if (state == ContentState.DESTROY_PENDING || state == ContentState.DESTROYED) {
                return false;
            }
        }
        var held = ctx.locks().tryLock("file-operation", u.operationId());
        if (held.isEmpty()) {
            throw new IllegalStateException("operation " + u.operationId() + " is busy; the attested move will be retried");
        }
        try (var lock = held.get()) {
            u = ctx.unresolved().find(unresolvedId).orElseThrow();
            if (!u.open()) {
                return true;
            }
            ManagedVolume volume = ctx.openVolume(u.volumeId());
            ctx.faults().reach(FaultPoints.Point.BEFORE_QUARANTINE_MOVE, u.operationId());
            QuarantineRepository.Entry moved = new QuarantineMoves(ctx).move(volume, u.operationId(), u.contentId(),
                    PortableRelativePath.of(u.path()), "att-" + u.unresolvedId().toString().replace("-", ""),
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
