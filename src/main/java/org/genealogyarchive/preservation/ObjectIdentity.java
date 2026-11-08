package org.genealogyarchive.preservation;

import org.genealogyarchive.data.QuarantineRepository;
import org.genealogyarchive.data.UnresolvedObjectRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.platform.SafeFiles;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Object-identity evidence (V0020): what the platform lets the archive know about one physical object - size,
 * SHA-256, creation and last-modification times and, where there is one, the file key. It is persisted when an
 * object is recorded and re-examined at an Administrator's decision and before anything relies on that decision.
 * Only exact equality counts. It is evidence, not proof: Windows/NTFS gives Java no file key, times can be set by
 * ordinary tools, and an object given the same bytes, times and key cannot be told apart (accepted with P2-E).
 */
final class ObjectIdentity {

    private ObjectIdentity() {
    }

    /** The identity of a plain file from its attributes (read first) and its measured bytes. */
    static UnresolvedObjectRepository.Identity of(BasicFileAttributes a, ContentIo.Measurement m) {
        return new UnresolvedObjectRepository.Identity(m.size(), m.sha256(), a.creationTime().toInstant(),
                a.lastModifiedTime().toInstant(), a.fileKey() == null ? null : String.valueOf(a.fileKey()));
    }

    /** What is at a location now. */
    record Observation(Presence.Probe probe, ContentIo.Measurement measurement, UnresolvedObjectRepository.Identity identity) {
        boolean absent() {
            return probe.verifiedAbsent();
        }

        /** Present but not a plain file (nothing to identify). */
        boolean unidentifiable() {
            return probe.present() && identity == null;
        }
    }

    /**
     * Examines a location: verified absent, a plain file with its identity, or present but not a plain file.
     *
     * @throws Presence.InconclusiveException if presence cannot be determined on the configured volume
     */
    static Observation observe(PreservationContext ctx, ManagedVolume volume, String location) throws IOException {
        Presence.Probe probe = Presence.probe(ctx, volume, location);
        if (probe.verifiedAbsent()) {
            return new Observation(probe, null, null);
        }
        if (!probe.present()) {
            throw new Presence.InconclusiveException(location, probe.problem());
        }
        if (SafeFiles.isLinkOrReparsePoint(probe.path()) || !probe.attributes().isRegularFile()) {
            return new Observation(probe, null, null);
        }
        ContentIo.Measurement m = ContentIo.measure(probe.path());
        return new Observation(probe, m, of(probe.attributes(), m));
    }

    /** Why {@code now} is not the object {@code recorded}, in words, or null if every piece of evidence is equal. */
    static String difference(UnresolvedObjectRepository.Identity recorded, UnresolvedObjectRepository.Identity now) {
        if (now == null) {
            return "it is not a plain file";
        }
        if (recorded.size() != now.size() || !recorded.sha256().equals(now.sha256())) {
            return "its bytes differ (size " + now.size() + ", SHA-256 " + now.sha256() + ")";
        }
        if (!recorded.equals(now)) {
            return "its bytes are the same but its filesystem identity differs (creation, modification time or file key)";
        }
        return null;
    }

    /** An instant in microseconds since the epoch, as the quarantine ledger records modification times. */
    static long micros(Instant t) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, t);
    }

    /**
     * Whether an object matches a quarantine move's evidence (V0020 integration): the size, SHA-256 and - where the
     * entry recorded it - last-modification time of the object the move was recorded for. A move keeps the
     * modification time; creation time and file key need not survive a relocation, so they are not part of it.
     */
    static boolean matchesMove(UnresolvedObjectRepository.Identity id, QuarantineRepository.Entry e) {
        return id != null && e.hasEvidence() && id.size() == e.sourceSize() && id.sha256().equals(e.sourceSha256())
                && (e.sourceModifiedMicros() == null || micros(id.modified()) == e.sourceModifiedMicros());
    }

    /**
     * Whether an Administrator's attestation is bound to a quarantine move (V0020 integration): the record was
     * attested with a CONFIRMED decision about RECORDED evidence, and that evidence's size, SHA-256 and modification
     * time are the move's. A LEGACY or unconfirmed attestation, or a move recorded for another object, is not bound.
     */
    static boolean bindsMove(UnresolvedObjectRepository.Entry u, QuarantineRepository.Entry e) {
        if (!u.attested() || !u.decisionConfirmed() || !e.hasEvidence() || e.sourceModifiedMicros() == null) {
            return false;
        }
        UnresolvedObjectRepository.Identity id = u.recordedIdentity().orElseThrow();
        return id.size() == e.sourceSize() && id.sha256().equals(e.sourceSha256())
                && micros(id.modified()) == e.sourceModifiedMicros();
    }
}
