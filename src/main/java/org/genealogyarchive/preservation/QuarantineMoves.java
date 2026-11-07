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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
 * Confirming a move also retires the vacated location in the same transaction (GAS-P2-FV01, IV01): every
 * operation's ownership of it ends and a superseded generation stored there becomes QUARANTINED (or
 * MISSING). The copy is then accounted by the ledger alone; the vacated path is only historical, and a
 * copy an operation later creates there is owned again from its exclusive create.
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
        // V0017: the intent records which object is moved - its size and the SHA-256 of its bytes (with its
        // last-modified time, for information) - so an interrupted move is only ever finished for an object with
        // those bytes, never for a different object that appeared at either end later.
        Optional<Path> at = volume.locate(source);
        QuarantineRepository.Entry entry;
        if (at.isPresent() && Files.exists(at.get(), LinkOption.NOFOLLOW_LINKS)) {
            SafeFiles.requirePlainFile(at.get());
            BasicFileAttributes seen = Files.readAttributes(at.get(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            ContentIo.Measurement m = ContentIo.measure(at.get());
            entry = ctx.quarantine().intend(volume.volumeId(), operationId, contentId, source.value(), q.value(), reason,
                    m.size(), seen.lastModifiedTime().to(TimeUnit.MICROSECONDS), m.sha256());
        } else {
            entry = ctx.quarantine().intend(volume.volumeId(), operationId, contentId, source.value(), q.value(), reason,
                    null, null, null);
        }
        ctx.faults().reach(FaultPoints.Point.AFTER_QUARANTINE_INTENT, operationId);
        return settle(volume, entry);
    }

    /** Thrown when the object at a move's source or destination is not the one the move was recorded for. */
    static final class UnexplainedObjectException extends IOException {
        UnexplainedObjectException(String message) {
            super(message);
        }
    }

    /**
     * Whether the object at {@code path} is the one recorded when the move was intended: same size and same
     * SHA-256 (V0017). An intent without that evidence - recorded before V0017, or when nothing was at the
     * source - can never vouch for an object: it is unverified, and the move is not finished silently.
     */
    private static boolean isRecordedObject(QuarantineRepository.Entry entry, Path path) throws IOException {
        if (!entry.hasEvidence()) {
            return false;
        }
        SafeFiles.requirePlainFile(path);
        ContentIo.Measurement m = ContentIo.measure(path);
        return m.size() == entry.sourceSize() && m.sha256().equals(entry.sourceSha256());
    }

    private static String unverifiedReason(QuarantineRepository.Entry entry) {
        if (entry.recordedAbsent()) {
            return "nothing was at " + entry.sourcePath() + " when this move was recorded";
        }
        return entry.hasEvidence() ? "it does not have the recorded size and SHA-256"
                : "this move was recorded without content evidence (before V0017); an Administrator must verify it";
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
        if (sourcePresent && !isRecordedObject(entry, source.get())) {
            throw new UnexplainedObjectException("the object now at " + entry.sourcePath() + " is not shown to be the one this "
                    + "move to " + entry.quarantinePath() + " was recorded for (" + unverifiedReason(entry)
                    + "); it was not moved or adopted");
        }
        if (!sourcePresent && destinationPresent && !isRecordedObject(entry, destination)) {
            throw new UnexplainedObjectException("the object at " + entry.quarantinePath() + " is not shown to be the one moved "
                    + "from " + entry.sourcePath() + " (" + unverifiedReason(entry) + "); nothing was confirmed");
        }
        QuarantineRepository.State outcome;
        String detail;
        if (sourcePresent) {
            SafeFiles.requirePlainFile(source.get());
            Path from = source.get();
            ctx.faults().reach(FaultPoints.Point.BEFORE_QUARANTINE_RENAME, entry.operationId());
            SafeFiles.withSharingRetry(() -> Files.move(from, destination, LinkOption.NOFOLLOW_LINKS));
            SafeFiles.flushDirectory(destination.getParent());
            SafeFiles.flushDirectory(from.getParent());
            // The object could have been replaced between the check above and the rename (no portable API moves
            // "this open file"). Verify what arrived; if it is not the recorded object, put it back untouched.
            if (!isRecordedObject(entry, destination)) {
                SafeFiles.withSharingRetry(() -> Files.move(destination, from, LinkOption.NOFOLLOW_LINKS));
                SafeFiles.flushDirectory(from.getParent());
                SafeFiles.flushDirectory(destination.getParent());
                throw new UnexplainedObjectException("the object at " + entry.sourcePath() + " changed while it was being "
                        + "moved; it was returned to where it was and not adopted");
            }
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
        // GAS-P2-FV01: the object's new location (or its absence) and the end of the operation's ownership of
        // the place it left are recorded together. The operation owns whatever it creates there later.
        QuarantineRepository.State settled = outcome;
        // GAS-P2-IV01: in the same transaction, the source location becomes historical for every record that
        // claimed it - every operation's ownership and a superseded generation's storage location.
        ctx.tx().executeWithoutResult(status -> {
            if (ctx.quarantine().setState(entry.quarantineId(), QuarantineRepository.State.INTENDED, settled, detail)) {
                ctx.operations().releaseTargetsAt(entry.volumeId(), entry.sourcePath());
                ctx.contents().retireGenerationAt(entry.contentId(), entry.sourcePath(),
                        settled == QuarantineRepository.State.QUARANTINED ? entry.quarantinePath() : null);
            }
        });
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
