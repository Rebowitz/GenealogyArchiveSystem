package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.FileOperationRepository;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Administrator-supplied verified repair of MISSING or DAMAGED content (P2-I, IV-007). The supplied
 * bytes are staged exactly like an upload; the worker accepts them only if their size and SHA-256
 * equal the authoritative values, writes them to a new generation (never over the damaged copy),
 * verifies the stored result, resolves the findings, records the repair on every File that uses the
 * content and moves the damaged copy to quarantine. Identity, provenance and the authoritative hash
 * never change.
 */
public final class RepairService {

    private final PreservationContext ctx;
    private final IngestionService receiver;

    public RepairService(PreservationContext ctx, IngestionService receiver) {
        this.ctx = ctx;
        this.receiver = receiver;
    }

    /** Receives replacement bytes for {@code contentId}. */
    public FileOperation receive(Actor actor, UUID contentId, String suppliedFilename, long declaredLength, InputStream body) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        FileContent content = ctx.contents().find(contentId).orElseThrow(() -> new NotFoundException("The content"));
        if (content.state() != ContentState.MISSING && content.state() != ContentState.DAMAGED) {
            throw new RuleViolationException("Only content recorded as missing or damaged can be repaired "
                    + "(this content is " + content.state().name().toLowerCase() + ").");
        }
        long max = ctx.settings().maxUploadBytes();
        if (declaredLength > max) {
            throw new IngestionService.UploadTooLargeException(max);
        }
        List<IntegrityFinding> open = ctx.integrity().openFindingsFor(contentId);
        StagingArea staging = ctx.requireStaging();
        UUID operationId = GlobalIds.newId();
        ctx.operations().insert(new FileOperationRepository.NewOperation(operationId, OperationKind.REPAIR,
                OperationState.RECEIVING, actor, content.volumeId(), null, null, staging.nameFor(operationId), null,
                contentId, open.isEmpty() ? null : open.get(0).findingId(),
                IngestionService.originalFilename(suppliedFilename == null ? "replacement" : suppliedFilename),
                null, null, null, null, null),
                "repair of content " + contentId + " started by " + actor.label());
        receiver.stage(operationId, staging, declaredLength, body, max);
        FileOperation op = ctx.operations().find(operationId).orElseThrow();
        return op;
    }
}
