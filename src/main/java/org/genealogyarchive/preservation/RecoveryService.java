package org.genealogyarchive.preservation;

import org.genealogyarchive.files.StagingArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * Restart and periodic recovery of archival file operations (BUILD-SPEC s.36; F-002, TX-002..TX-008,
 * BI-008). Decisions come from each operation's persistent state and a fresh check of the bytes on
 * disk - never from filenames.
 * <ul>
 *   <li>An upload is abandoned only when it has made no progress for {@code files.receivingTimeout}
 *       (database clock); a slow but active upload keeps reporting progress and is left alone.</li>
 *   <li>STAGED/PROMOTING/PROMOTED/COMMITTED/WAITING_FOR_VOLUME ingests and repairs are driven forward
 *       by {@link OperationProcessor}; pending destructions by {@link DestructionProcessor}.</li>
 *   <li>BLOCKED operations wait for an Administrator; nothing is guessed.</li>
 *   <li>Staged temporaries of finished operations are removed; staging files that belong to no
 *       operation are reported by integrity checks, never deleted here.</li>
 * </ul>
 */
public final class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final PreservationContext ctx;
    private final OperationProcessor processor;
    private final DestructionProcessor destruction;

    public RecoveryService(PreservationContext ctx, OperationProcessor processor, DestructionProcessor destruction) {
        this.ctx = ctx;
        this.processor = processor;
        this.destruction = destruction;
    }

    /** Summary of one recovery pass. */
    public record Result(int abandoned, int examined, int failed) {
    }

    public Result runOnce() {
        int abandoned = 0;
        for (UUID id : ctx.operations().abandonStaleReceiving(ctx.settings().receivingTimeout())) {
            abandoned++;
            log.warn("Upload {} was abandoned: its receiver renewed neither its lease nor its progress within {}", id,
                    ctx.settings().receivingTimeout());
        }
        // V0016: content whose missing location was recorded by a finding but not (yet) by its state - an
        // interrupted or legacy recording - is marked missing first, which vacates the location, before any
        // move is settled or anything is deleted.
        for (FileContent content : ctx.contents().presentButRecordedMissing()) {
            if (ctx.contents().setState(content.contentId(), content.state(), ContentState.MISSING)) {
                log.warn("Content {} had an open MISSING finding but was still {}; recorded as MISSING", content.contentId(),
                        content.state());
            }
        }
        // GAS-P2-008: finish quarantine moves interrupted between the move and its record.
        new QuarantineMoves(ctx).reconcileAll();
        int examined = 0;
        int failed = 0;
        for (FileOperation op : ctx.operations().needingAttention()) {
            examined++;
            try {
                switch (op.state()) {
                    case RECEIVING, BLOCKED -> { }
                    case CLEANUP_PENDING, CLEANUP_INCOMPLETE -> destruction.process(op.operationId());
                    case WAITING_FOR_VOLUME -> {
                        if (op.kind() == OperationKind.DESTROY) {
                            destruction.process(op.operationId());
                        } else {
                            processor.process(op.operationId(), () -> { });
                        }
                    }
                    case STAGED, PROMOTING, PROMOTED, COMMITTED -> processor.process(op.operationId(), () -> { });
                    default -> cleanStaging(op);
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Recovery could not advance file operation {} ({}): {}", op.operationId(), op.state(), e.getMessage());
            }
        }
        return new Result(abandoned, examined, failed);
    }

    private void cleanStaging(FileOperation op) {
        // A final operation that still owns a copy keeps its staged upload: it identifies that copy (V0018 audit).
        if (op.stagingName() == null || op.stagingCleaned() || op.targetCreated()) {
            return;
        }
        ctx.staging().ifPresent(staging -> {
            try {
                if (staging.delete(op.operationId())) {
                    ctx.operations().markStagingCleaned(op.operationId());
                }
            } catch (IOException e) {
                log.warn("Staged temporary of {} could not be removed yet", op.operationId());
            }
        });
    }

    /** Exposed for diagnostics. */
    public static boolean isStagedName(String name) {
        return StagingArea.operationOf(name).isPresent();
    }
}
