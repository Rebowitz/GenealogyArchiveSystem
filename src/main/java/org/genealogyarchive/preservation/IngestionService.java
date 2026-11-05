package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.FileOperationRepository;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.storage.StorageVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Receiving side of ingestion (runs in the web process; BUILD-SPEC s.31 steps 1-4, F-001).
 * <p>
 * The web process never writes to an archival Storage Volume. It streams each upload into the
 * installation-local staging area while measuring size and SHA-256, enforces the upload limit as
 * bytes arrive, reports progress so recovery can tell an active upload from an abandoned one, and
 * hands the staged operation to the worker. Success is reported to the user only after the worker
 * has verified and committed the File.
 */
public final class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final Pattern ITEM_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final long HEARTBEAT_MILLIS = 1000;

    private final PreservationContext ctx;

    public IngestionService(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** An upload request: provenance captured from the browser at upload time. */
    public record Upload(UUID batchId, String itemKey, String originalFilename, Instant clientLastModified,
                         Restriction restriction, String sourceNote, UUID parentFileId, String derivationMethod) {
    }

    /** Where new uploads would go and whether ingestion is possible right now. */
    public record Readiness(boolean ready, String explanation, StorageVolume volume, long maxUploadBytes) {
    }

    /** Checks whether ingestion can accept uploads (volume set, initialized and present; staging usable). */
    public Readiness readiness(Actor actor) {
        AccessPolicy.require(actor, Permission.INGEST_FILES);
        long max = ctx.settings().maxUploadBytes();
        var volumeId = ctx.archives().ingestionVolume();
        if (volumeId.isEmpty()) {
            return new Readiness(false, "No ingestion volume has been chosen for this archive (Archive settings).", null, max);
        }
        StorageVolume volume = ctx.volumes().findById(volumeId.get()).orElseThrow();
        try {
            ctx.openVolume(volume.volumeId());
            ctx.requireStaging();
        } catch (RuntimeException e) {
            return new Readiness(false, e.getMessage(), volume, max);
        }
        return new Readiness(true, "Uploads are stored on volume '" + volume.name() + "'.", volume, max);
    }

    /** Starts an upload batch. Refuses visibly if ingestion is not possible. */
    public UploadBatch startBatch(Actor actor, String note) {
        Readiness r = readiness(actor);
        if (!r.ready()) {
            throw new RuleViolationException("Files cannot be uploaded right now: " + r.explanation());
        }
        UUID batchId = GlobalIds.newId();
        ctx.operations().insertBatch(batchId, actor.userId(), r.volume().volumeId(), blankToNull(note, 2000));
        return ctx.operations().findBatch(batchId).orElseThrow();
    }

    public UploadBatch batch(Actor actor, UUID batchId) {
        AccessPolicy.require(actor, Permission.INGEST_FILES);
        UploadBatch b = ctx.operations().findBatch(batchId)
                .orElseThrow(() -> new NotFoundException("The upload batch"));
        if (!b.createdBy().equals(actor.userId()) && !AccessPolicy.allows(actor, Permission.MANAGE_PRESERVATION)) {
            throw new NotFoundException("The upload batch");
        }
        return b;
    }

    public List<FileOperation> batchItems(Actor actor, UUID batchId) {
        batch(actor, batchId);
        return ctx.operations().byBatch(batchId);
    }

    /** The operation, if the actor started it (or may manage preservation). */
    public FileOperation operation(Actor actor, UUID operationId) {
        AccessPolicy.require(actor, Permission.INGEST_FILES);
        FileOperation op = ctx.operations().find(operationId).orElseThrow(() -> new NotFoundException("The upload"));
        if (!actor.userId().equals(op.actorUserId()) && !AccessPolicy.allows(actor, Permission.MANAGE_PRESERVATION)) {
            throw new NotFoundException("The upload");
        }
        return op;
    }

    /**
     * Receives one upload into staging. If the batch already has an operation for this item key
     * (a resent request after a network failure), that operation is returned and the body is not
     * read: a retry never creates a second File (BI-008, TX-003).
     *
     * @param declaredLength the request's Content-Length, or -1 if unknown (chunked)
     * @throws UploadTooLargeException if the declared or received size exceeds the limit
     */
    public FileOperation receive(Actor actor, Upload upload, long declaredLength, InputStream body) {
        UploadBatch batch = batch(actor, upload.batchId());
        if (upload.itemKey() == null || !ITEM_KEY.matcher(upload.itemKey()).matches()) {
            throw new RuleViolationException("Each uploaded file needs an item key of letters, digits, '-' or '_'.");
        }
        var existing = ctx.operations().findByBatchItem(batch.batchId(), upload.itemKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        long max = ctx.settings().maxUploadBytes();
        if (declaredLength > max) {
            throw new UploadTooLargeException(max);
        }
        String filename = originalFilename(upload.originalFilename());
        Restriction restriction = upload.restriction() == null ? Restriction.NORMAL : upload.restriction();
        String method = null;
        if (upload.parentFileId() != null) {
            var parent = ctx.files().find(upload.parentFileId())
                    .filter(f -> f.lifecycle() != FileLifecycle.DESTROYED)
                    .filter(f -> !f.restricted() || AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES))
                    .orElseThrow(() -> new NotFoundException("The parent file"));
            method = blankToNull(upload.derivationMethod(), 200);
            if (method == null) {
                throw new RuleViolationException("Describe how the derivative was made (for example: cropped in an external editor).");
            }
            if (parent.restricted()) {
                restriction = Restriction.RESTRICTED;
            }
        }
        StagingArea staging = ctx.requireStaging();
        try {
            ctx.openVolume(batch.volumeId());
        } catch (RuntimeException e) {
            throw new RuleViolationException("Files cannot be uploaded right now: " + e.getMessage());
        }

        UUID operationId = GlobalIds.newId();
        ctx.operations().insert(new FileOperationRepository.NewOperation(operationId, OperationKind.INGEST,
                OperationState.RECEIVING, actor, batch.volumeId(), batch.batchId(), upload.itemKey(),
                staging.nameFor(operationId), null, null, null, filename, upload.clientLastModified(),
                blankToNull(upload.sourceNote(), 2000), restriction, upload.parentFileId(), method),
                "upload started by " + actor.label());
        stage(operationId, staging, declaredLength, body, max);
        return ctx.operations().find(operationId).orElseThrow();
    }

    /**
     * Streams the body into staging. On any failure the partial temporary is removed and the
     * operation is ABANDONED; the user's source file is never touched.
     */
    void stage(UUID operationId, StagingArea staging, long declaredLength, InputStream body, long max) {
        MessageDigest digest = Sha256.digest();
        long received = 0;
        String failure = null;
        try (FileChannel out = staging.create(operationId)) {
            byte[] buffer = new byte[ContentIo.CHUNK];
            long lastBeat = System.nanoTime();
            int n;
            while ((n = body.read(buffer)) >= 0) {
                if (n == 0) {
                    continue;
                }
                received += n;
                if (received > max) {
                    throw new UploadTooLargeException(max);
                }
                digest.update(buffer, 0, n);
                ByteBuffer bb = ByteBuffer.wrap(buffer, 0, n);
                while (bb.hasRemaining()) {
                    out.write(bb);
                }
                if ((System.nanoTime() - lastBeat) / 1_000_000 >= HEARTBEAT_MILLIS) {
                    lastBeat = System.nanoTime();
                    if (!ctx.operations().heartbeat(operationId, received)) {
                        throw new IOException("the upload was abandoned by recovery after making no progress");
                    }
                }
            }
            if (declaredLength >= 0 && received != declaredLength) {
                throw new IOException("the upload ended after " + received + " of " + declaredLength + " bytes");
            }
            out.force(true);
        } catch (UploadTooLargeException e) {
            abandon(operationId, staging, "The upload exceeded the limit of " + max + " bytes and was discarded.");
            throw e;
        } catch (IOException | RuntimeException e) {
            failure = "The upload did not complete (" + e.getMessage() + "); nothing was archived and the source file was not touched.";
        }
        if (failure != null) {
            abandon(operationId, staging, failure);
            return;
        }
        try {
            ctx.faults().reach(FaultPoints.Point.AFTER_STAGING_WRITE, operationId);
        } catch (IOException e) {
            abandon(operationId, staging, "The upload could not be staged (" + e.getMessage() + ").");
            return;
        }
        boolean staged = ctx.operations().transition(operationId, OperationState.RECEIVING, OperationState.STAGED,
                "received " + received + " bytes", Map.of("expected_size", received,
                        "expected_sha256", Sha256.hex(digest.digest()), "received_bytes", received));
        if (staged) {
            boolean repair = ctx.operations().find(operationId).map(o -> o.kind() == OperationKind.REPAIR).orElse(false);
            ctx.enqueue(repair ? PreservationContext.JOB_REPAIR : PreservationContext.JOB_INGEST, operationId);
        } else {
            log.warn("Upload {} was abandoned by recovery before it could be staged", operationId);
            cleanup(operationId, staging);
        }
    }

    private void abandon(UUID operationId, StagingArea staging, String message) {
        ctx.operations().transition(operationId, OperationState.RECEIVING, OperationState.ABANDONED, message,
                Map.of("message", message));
        cleanup(operationId, staging);
    }

    private void cleanup(UUID operationId, StagingArea staging) {
        try {
            if (staging.delete(operationId)) {
                ctx.operations().markStagingCleaned(operationId);
            }
        } catch (IOException e) {
            log.warn("Staged temporary of upload {} could not be removed yet; recovery will retry", operationId);
        }
    }

    /** Keeps only the final name component as captured; rejects empty and control characters. */
    static String originalFilename(String raw) {
        if (raw == null) {
            throw new RuleViolationException("The original filename is missing.");
        }
        String name = raw;
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (cut >= 0) {
            name = name.substring(cut + 1);
        }
        if (name.isBlank() || name.length() > 1024 || name.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw new RuleViolationException("The original filename is empty, too long or contains control characters.");
        }
        return name;
    }

    private static String blankToNull(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        if (t.length() > max) {
            throw new RuleViolationException("A text value is longer than " + max + " characters.");
        }
        return t;
    }

    /** The upload is larger than the configured limit. */
    public static final class UploadTooLargeException extends RuleViolationException {
        private final long limit;

        public UploadTooLargeException(long limit) {
            super("The file is larger than this archive's upload limit of " + limit + " bytes (files.maxUploadBytes).");
            this.limit = limit;
        }

        public long limit() {
            return limit;
        }
    }
}
