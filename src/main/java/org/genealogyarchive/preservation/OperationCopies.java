package org.genealogyarchive.preservation;

import org.genealogyarchive.data.UnresolvedObjectRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.platform.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Identity evidence for an operation's uncommitted copy (V0018 audit, finding 1). An operation's claim
 * ({@code target_created}) says only that its exclusive create once produced an object at the target; it is
 * never evidence that the object there now is that copy. The object is the operation's copy only if:
 * <ul>
 *   <li>it has exactly the size and SHA-256 the receiver measured for the upload (a complete copy), or</li>
 *   <li>it is a byte-for-byte prefix of the staged upload, after the staged upload itself re-verifies against
 *       the receiver's size and SHA-256 (a copy interrupted while it was written).</li>
 * </ul>
 * Anything else - other bytes, a link, or no staged upload left to compare with - is unresolved: it is left
 * exactly where it is. Promotion, cancellation, recovery (which drives promotion and cancellation) and
 * destruction all use this one check.
 */
final class OperationCopies {

    private OperationCopies() {
    }

    enum Verdict { ABSENT, COMPLETE, PARTIAL, UNVERIFIED }

    /**
     * @param observed the object's size and SHA-256 now (null if absent or unreadable); trusted only together
     *                 with a COMPLETE or PARTIAL verdict
     * @param reason   why the object is unverified (UNVERIFIED only)
     */
    record Check(Verdict verdict, ContentIo.Measurement observed, String reason) {
        boolean verified() {
            return verdict == Verdict.COMPLETE || verdict == Verdict.PARTIAL;
        }
    }

    static Check check(PreservationContext ctx, FileOperation op, ManagedVolume volume) throws IOException {
        // An unreadable staged upload is an error (retried), never "gone" (V0019 audit).
        Optional<Path> staged = ctx.staging().isPresent() ? ctx.staging().get().existing(op.operationId()) : Optional.empty();
        return check(ctx, volume, op, staged);
    }

    /**
     * @throws Presence.InconclusiveException if whether anything is at the target cannot be determined (an
     *         inaccessible location is never "absent")
     */
    static Check check(PreservationContext ctx, ManagedVolume volume, FileOperation op, Optional<Path> staged)
            throws IOException {
        Presence.Probe probe = Presence.probe(ctx, volume, op.targetPath());
        if (probe.verifiedAbsent()) {
            return new Check(Verdict.ABSENT, null, null);
        }
        if (!probe.present()) {
            throw new Presence.InconclusiveException(op.targetPath(), probe.problem());
        }
        Path target = probe.path();
        if (SafeFiles.isLinkOrReparsePoint(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return new Check(Verdict.UNVERIFIED, null, "it is not a plain file");
        }
        ContentIo.Measurement m = ContentIo.measure(target);
        if (op.expectedSize() == null || op.expectedSha256() == null) {
            return new Check(Verdict.UNVERIFIED, m, "the operation recorded no size and SHA-256 for its upload");
        }
        if (m.size() == op.expectedSize() && m.sha256().equals(op.expectedSha256())) {
            return new Check(Verdict.COMPLETE, m, null);
        }
        if (staged.isEmpty()) {
            return new Check(Verdict.UNVERIFIED, m, "the staged upload it was copied from is gone, so nothing shows "
                    + "that this object is the operation's partial copy");
        }
        ContentIo.Measurement source = ContentIo.measure(staged.get());
        if (source.size() != op.expectedSize() || !source.sha256().equals(op.expectedSha256())) {
            return new Check(Verdict.UNVERIFIED, m, "the staged upload no longer has the size and SHA-256 measured "
                    + "when it was received");
        }
        if (m.size() < source.size() && ContentIo.isPrefixOf(target, staged.get())) {
            return new Check(Verdict.PARTIAL, m, null);
        }
        return new Check(Verdict.UNVERIFIED, m, "its bytes are not the bytes this operation was copying");
    }

    /**
     * In the caller's transaction: ends the operation's claim and records the object at its target as
     * unresolved instead (never moved, adopted or deleted).
     */
    static void claimBecomesUnresolved(PreservationContext ctx, FileOperation op, Check check) {
        ctx.operations().releaseTarget(op.operationId(), op.targetPath());
        ctx.unresolved().recordIfAbsent(new UnresolvedObjectRepository.NewEntry(op.volumeId(), op.targetPath(),
                ctx.contents().find(op.contentId()).isPresent() ? op.contentId() : null, null, op.operationId(),
                UnresolvedObjectRepository.Kind.OPERATION_COPY,
                check.observed() == null ? null : check.observed().size(),
                check.observed() == null ? null : check.observed().sha256(),
                "Operation " + op.operationId() + " created a copy at " + op.targetPath() + ", but the object there now "
                        + "is not shown to be that copy: " + check.reason() + ". It was left untouched."));
    }
}
