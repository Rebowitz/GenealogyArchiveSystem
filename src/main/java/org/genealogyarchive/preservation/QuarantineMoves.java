package org.genealogyarchive.preservation;

import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.platform.SafeFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Accountable quarantine moves (GAS-P2-008). Every move is recorded in the quarantine ledger as
 * INTENDED and committed before anything is moved, then confirmed. {@link #settle} finishes an entry
 * from what is actually on disk and is idempotent, so a crash at any point - before the move, after
 * the move but before the confirmation - is reconciled on the next attempt or by recovery:
 * <ul>
 *   <li>source present, destination absent: move it, then confirm;</li>
 *   <li>destination present, source absent: the move happened; confirm;</li>
 *   <li>both absent: record SOURCE_ABSENT (nothing to account for);</li>
 *   <li>both present: something unexplained is in the way; fail visibly, touch nothing.</li>
 * </ul>
 */
final class QuarantineMoves {

    private static final Logger log = LoggerFactory.getLogger(QuarantineMoves.class);

    private final PreservationContext ctx;

    QuarantineMoves(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** Records the intent, moves {@code source} into quarantine, and confirms it. */
    QuarantineRepository.Entry move(ManagedVolume volume, UUID operationId, UUID contentId, PortableRelativePath source,
                                    String suffix, QuarantineRepository.Reason reason) throws IOException {
        PortableRelativePath q = ManagedVolume.quarantinePath(operationId, suffix);
        QuarantineRepository.Entry entry = ctx.quarantine().intend(volume.volumeId(), operationId, contentId,
                source.value(), q.value(), reason);
        return settle(volume, entry);
    }

    /** Finishes an INTENDED entry from the state on disk; other states are returned unchanged. */
    QuarantineRepository.Entry settle(ManagedVolume volume, QuarantineRepository.Entry entry) throws IOException {
        if (entry.state() != QuarantineRepository.State.INTENDED) {
            return entry;
        }
        PortableRelativePath sourceRel = PortableRelativePath.of(entry.sourcePath());
        Optional<Path> source = volume.locate(sourceRel);
        boolean sourcePresent = source.isPresent() && Files.exists(source.get(), LinkOption.NOFOLLOW_LINKS);
        Path destination = volume.prepare(PortableRelativePath.of(entry.quarantinePath()));
        boolean destinationPresent = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        if (sourcePresent && destinationPresent) {
            throw new FileAlreadyExistsException(entry.quarantinePath(), entry.sourcePath(),
                    "both the object and its quarantine destination exist; nothing was moved");
        }
        QuarantineRepository.State outcome;
        String detail;
        if (sourcePresent) {
            SafeFiles.requirePlainFile(source.get());
            Path from = source.get();
            SafeFiles.withSharingRetry(() -> Files.move(from, destination, LinkOption.NOFOLLOW_LINKS));
            SafeFiles.flushDirectory(destination.getParent());
            SafeFiles.flushDirectory(from.getParent());
            ctx.faults().reach(FaultPoints.Point.AFTER_QUARANTINE_MOVE, entry.operationId());
            outcome = QuarantineRepository.State.QUARANTINED;
            detail = "moved from " + entry.sourcePath();
        } else if (destinationPresent) {
            outcome = QuarantineRepository.State.QUARANTINED;
            detail = "an earlier attempt had already moved it from " + entry.sourcePath() + "; confirmed";
        } else {
            outcome = QuarantineRepository.State.SOURCE_ABSENT;
            detail = "nothing was found at " + entry.sourcePath() + " or in quarantine";
        }
        ctx.quarantine().setState(entry.quarantineId(), QuarantineRepository.State.INTENDED, outcome, detail);
        return ctx.quarantine().find(entry.volumeId(), entry.quarantinePath()).orElseThrow();
    }

    /** Recovery: settles every unconfirmed move whose volume is available. @return entries settled */
    int reconcileAll() {
        int settled = 0;
        for (QuarantineRepository.Entry entry : ctx.quarantine().intended()) {
            // The operation's lock keeps recovery and a worker from settling the same move at once.
            var held = ctx.locks().tryLock("file-operation", entry.operationId());
            if (held.isEmpty()) {
                continue;
            }
            try (var lock = held.get()) {
                settle(ctx.openVolume(entry.volumeId()), entry);
                settled++;
            } catch (ManagedVolume.VolumeUnavailableException e) {
                // reconciled when the volume returns
            } catch (IOException | RuntimeException e) {
                log.warn("Quarantine move {} of operation {} could not be reconciled yet: {}", entry.quarantinePath(),
                        entry.operationId(), e.getMessage());
            }
        }
        return settled;
    }
}
