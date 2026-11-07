package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.FileContentRepository;
import org.genealogyarchive.data.IntegrityRepository;
import org.genealogyarchive.data.UnresolvedObjectRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.storage.StorageVolume;
import org.genealogyarchive.service.RuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quick Integrity Check and Full Integrity Scan (BUILD-SPEC s.48; IV-001..IV-007, TX-006).
 * <ul>
 *   <li>The authoritative size and SHA-256 are never replaced (IV-003).</li>
 *   <li>Missing files keep their records (IV-004); content becomes MISSING and is restored to ACTIVE
 *       only if the file reappears with its authoritative hash.</li>
 *   <li>Unexpected objects, links and staging leftovers are reported, never adopted or deleted
 *       (IV-005, TX-006).</li>
 *   <li>An unavailable volume is reported as such; its files are not declared missing.</li>
 *   <li>Each run is recorded in scan history (IV-006).</li>
 * </ul>
 */
public final class IntegrityService {

    private static final Logger log = LoggerFactory.getLogger(IntegrityService.class);

    private final PreservationContext ctx;

    public IntegrityService(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** Queues a scan; the worker runs it. */
    public IntegrityScan requestScan(Actor actor, String kind) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        if (!"QUICK".equals(kind) && !"FULL".equals(kind)) {
            throw new RuleViolationException("Choose a Quick check or a Full scan.");
        }
        UUID scanId = GlobalIds.newId();
        ctx.integrity().insertScan(scanId, kind, actor.isSystem() ? null : actor.userId());
        ctx.enqueue(PreservationContext.JOB_INTEGRITY_SCAN, scanId);
        return ctx.integrity().findScan(scanId).orElseThrow();
    }

    public List<IntegrityScan> recentScans(Actor actor, int limit) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.integrity().recentScans(limit);
    }

    public List<IntegrityFinding> openFindings(Actor actor, int limit) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.integrity().openFindings(limit);
    }

    public IntegrityScan scan(Actor actor, UUID scanId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.integrity().findScan(scanId).orElseThrow(() -> new NotFoundException("The scan"));
    }

    public List<IntegrityFinding> findingsOfScan(Actor actor, UUID scanId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        return ctx.integrity().findingsOfScan(scanId);
    }

    /** Runs a queued scan (worker). */
    public void run(UUID scanId) {
        IntegrityScan scan = ctx.integrity().findScan(scanId).orElseThrow();
        if ("COMPLETED".equals(scan.state())) {
            return;
        }
        ctx.integrity().startScan(scanId);
        boolean full = "FULL".equals(scan.kind());
        // counts: [0] contents checked, [1] newly opened findings, [2] unresolved findings observed again
        int[] counts = {0, 0, 0};
        try {
            Map<UUID, Optional<ManagedVolume>> volumes = new HashMap<>();
            for (FileContent content : ctx.contents().expectedOnDisk()) {
                counts[0]++;
                Optional<ManagedVolume> volume = volumes.computeIfAbsent(content.volumeId(), id -> openOrReport(scanId, id, counts));
                if (volume.isPresent()) {
                    checkContent(scanId, volume.get(), content, full, counts);
                }
                observeUnresolved(scanId, content.contentId(), counts);
            }
            // GAS-P2-010: every active required volume and the ingestion destination are checked, even
            // when they hold no content yet.
            Set<UUID> volumeIds = new LinkedHashSet<>(volumes.keySet());
            for (StorageVolume v : ctx.volumes().findAll()) {
                if (v.active() && v.required()) {
                    volumeIds.add(v.volumeId());
                }
            }
            ctx.archives().ingestionVolume().ifPresent(volumeIds::add);
            for (UUID volumeId : volumeIds) {
                Optional<ManagedVolume> volume = volumes.computeIfAbsent(volumeId, id -> openOrReport(scanId, id, counts));
                if (volume.isPresent()) {
                    checkUnexpected(scanId, volume.get(), counts);
                }
            }
            checkUnresolved(scanId, volumes, counts);
            checkStaging(scanId, counts);
            int observed = counts[1] + counts[2];
            ctx.integrity().finishScan(scanId, "COMPLETED", counts[0], observed, summary(counts));
        } catch (RuntimeException e) {
            log.error("Integrity scan {} failed: {}", scanId, e.getMessage());
            ctx.integrity().finishScan(scanId, "FAILED", counts[0], counts[1] + counts[2],
                    "The scan stopped: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    /** GAS-P2-011: a scan reports damage that is still unresolved, not only newly found problems. */
    static String summary(int[] counts) {
        if (counts[1] == 0 && counts[2] == 0) {
            return "No problems found.";
        }
        StringBuilder b = new StringBuilder();
        if (counts[1] > 0) {
            b.append(counts[1]).append(" new problem(s) recorded.");
        }
        if (counts[2] > 0) {
            b.append(b.isEmpty() ? "" : " ").append(counts[2]).append(" previously recorded problem(s) still unresolved.");
        }
        return b.toString();
    }

    /**
     * Content already known to be missing or damaged stays a problem until it is repaired or verified
     * again, even when this scan cannot re-detect it (a quick scan does not hash). Its open findings are
     * observed by this scan so the outcome never reads "No problems found" while damage is unresolved.
     */
    private void observeUnresolved(UUID scanId, UUID contentId, int[] counts) {
        FileContent now = ctx.contents().find(contentId).orElse(null);
        if (now == null || now.state() == ContentState.ACTIVE) {
            return;
        }
        for (IntegrityFinding f : ctx.integrity().openFindingsFor(contentId)) {
            if (!scanId.equals(f.scanId()) && !ctx.integrity().observedBy(scanId, f.findingId())) {
                ctx.integrity().observeExisting(scanId, f.findingId());
                counts[2]++;
            }
        }
    }

    private Optional<ManagedVolume> openOrReport(UUID scanId, UUID volumeId, int[] counts) {
        try {
            return Optional.of(ctx.openVolume(volumeId));
        } catch (ManagedVolume.VolumeUnavailableException e) {
            record(scanId, new IntegrityRepository.NewFinding(FindingKind.VOLUME_UNAVAILABLE, volumeId, null, null, null,
                    null, null, null, e.getMessage()), counts);
            return Optional.empty();
        }
    }

    private void checkContent(UUID scanId, ManagedVolume volume, FileContent content, boolean full, int[] counts) {
        String where = content.currentPath() == null ? null : content.currentPath().value();
        try {
            // GAS-P2-007: a missing shard or originals directory (on a volume whose marker verified) means
            // the original is missing, so it can be repaired.
            // V0019 audit: MISSING only on verified absence. A location that cannot be examined (permission, I/O,
            // a vanished mount) is reported UNREADABLE below and decides nothing.
            if (!Presence.exists(ctx, volume, content.currentPath().value())) {
                IntegrityRepository.NewFinding missing = finding(FindingKind.MISSING, content, null, null,
                        "Expected stored original not found at " + where);
                // V0016: the finding and the content state (which vacates the location) are one transaction.
                ctx.tx().executeWithoutResult(status -> {
                    record(scanId, missing, counts);
                    try {
                        ctx.faults().reach(FaultPoints.Point.AFTER_MISSING_FINDING, content.contentId());
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                    // From any state a scan checks (ACTIVE or DAMAGED): content whose bytes left is MISSING, which
                    // vacates the location in the same statement - so a damaged copy that disappeared is never
                    // again taken for "damaged but ours" if something else appears there.
                    if (content.state() != ContentState.MISSING) {
                        ctx.contents().setState(content.contentId(), content.state(), ContentState.MISSING);
                    }
                });
                return;
            }
            Path path = volume.locate(content.currentPath()).orElseThrow(() ->
                    new Presence.InconclusiveException(where, "it vanished while being examined"));
            if (content.state() == ContentState.MISSING) {
                // V0016: the bytes left this location. Whatever is here now is not the archive's copy unless it is
                // verifiably the content (full scan); otherwise it is reported as an unexpected object, never
                // treated as damaged content (which a repair would quarantine) and never touched.
                if (full && !SafeFiles.isLinkOrReparsePoint(path)) {
                    ContentIo.Measurement m = ContentIo.measure(path);
                    if (m.size() == content.byteSize() && m.sha256().equals(content.sha256())) {
                        if (!SafeFiles.isReadOnly(path)) {
                            // Verified bytes but not protected: not a healthy preservation original. It stays MISSING
                            // (downloads, sharing and repair treat it so) and the difference is reported; a scan
                            // never changes protection. Restoring read-only and scanning again restores it.
                            record(scanId, finding(FindingKind.NOT_PROTECTED, content, m.size(), m.sha256(),
                                    "The missing original is present again with its authoritative bytes but is writable; "
                                    + "it is not restored until it is protected read-only"), counts);
                            return;
                        }
                        ctx.contents().markVerified(content.contentId(), ctx.clock().instant());
                        reappeared(content, "The stored original is present again, matches its authoritative SHA-256 and is "
                                + "protected read-only.");
                        return;
                    }
                }
                record(scanId, new IntegrityRepository.NewFinding(FindingKind.UNEXPECTED_OBJECT, content.volumeId(), null,
                        null, where, null, null, null, "An object is at the location of missing content " + content.contentId()
                        + "; it is not the archived bytes (or could not be verified by this scan) and was left untouched."),
                        counts);
                return;
            }
            if (SafeFiles.isLinkOrReparsePoint(path)) {
                record(scanId, finding(FindingKind.LINK_OR_REPARSE, content, null, null,
                        "The stored original's location is a link or reparse point"), counts);
                return;
            }
            long size = Files.size(path);
            if (size != content.byteSize()) {
                // The observed SHA-256 is recorded too (diagnostic: it identifies what was seen, not whose it is).
                ContentIo.Measurement damaged = ContentIo.measure(path);
                damaged(scanId, content, damaged, finding(FindingKind.SIZE_MISMATCH, content, damaged.size(), damaged.sha256(),
                        "Stored size " + size + " differs from the authoritative " + content.byteSize() + " bytes"), counts);
                return;
            }
            if (!SafeFiles.isReadOnly(path)) {
                record(scanId, finding(FindingKind.NOT_PROTECTED, content, null, null,
                        "The stored original has lost its read-only protection"), counts);
            }
            if (full) {
                ContentIo.Measurement m = ContentIo.measure(path);
                if (!m.sha256().equals(content.sha256())) {
                    damaged(scanId, content, m, finding(FindingKind.HASH_MISMATCH, content, m.size(), m.sha256(),
                            "SHA-256 of the stored original differs from the authoritative ingestion hash"), counts);
                    return;
                }
                ctx.contents().markVerified(content.contentId(), ctx.clock().instant());
            }
        } catch (SafeFiles.UnsafePathException e) {
            record(scanId, finding(FindingKind.LINK_OR_REPARSE, content, null, null,
                    "A directory above the stored original is a link, junction or reparse point"), counts);
        } catch (IOException e) {
            record(scanId, finding(FindingKind.UNREADABLE, content, null, null,
                    "The stored original could not be read (" + e.getClass().getSimpleName() + ")"), counts);
        }
    }

    /**
     * Records damage. When ACTIVE content first becomes DAMAGED, what this scan observed (size and SHA-256) is
     * recorded on its generation in the same transaction (V0018). It is an observation of a mismatch, not
     * evidence of which object is the archive's: the object may have been replaced before this scan ran, so
     * nothing moves or deletes bytes on its strength (V0018 audit, V0019). Never from MISSING (V0016), never
     * replaced; if the bytes at a DAMAGED content's location change again, that change is reported.
     */
    private void damaged(UUID scanId, FileContent content, ContentIo.Measurement observed, IntegrityRepository.NewFinding f,
                         int[] counts) {
        Optional<FileContentRepository.Generation> current = ctx.contents().generations(content.contentId()).stream()
                .filter(g -> g.generation() == content.currentGeneration()).findFirst();
        ctx.tx().executeWithoutResult(status -> {
            record(scanId, f, counts);
            if (content.state() == ContentState.ACTIVE
                    && ctx.contents().setState(content.contentId(), ContentState.ACTIVE, ContentState.DAMAGED)) {
                ctx.contents().recordDamageEvidence(content.contentId(), content.currentGeneration(), observed.size(),
                        observed.sha256());
            } else if (content.state() == ContentState.DAMAGED && current.isPresent() && current.get().hasDamageEvidence()
                    && (observed.size() != current.get().damagedSize() || !observed.sha256().equals(current.get().damagedSha256()))) {
                record(scanId, new IntegrityRepository.NewFinding(FindingKind.UNEXPECTED_OBJECT, content.volumeId(), null, null,
                        content.currentPath().value(), null, observed.size(), observed.sha256(),
                        "The object at the location of damaged content " + content.contentId() + " is no longer the damaged "
                        + "object observed when the damage was found; that observation is kept and this object is not "
                        + "treated as the archive's copy"), counts);
            }
        });
    }

    /**
     * Unresolved objects (V0019) stay problems until they are resolved: each open record whose object is still
     * there counts as unresolved in this scan's outcome; one whose object is verifiably gone is closed (ABSENT).
     * Nothing is moved, adopted or deleted.
     */
    private void checkUnresolved(UUID scanId, Map<UUID, Optional<ManagedVolume>> volumes, int[] counts) {
        for (UnresolvedObjectRepository.Entry u : ctx.unresolved().open()) {
            Optional<ManagedVolume> volume = volumes.computeIfAbsent(u.volumeId(), id -> openOrReport(scanId, id, counts));
            if (volume.isEmpty()) {
                counts[2]++;
                continue;
            }
            // Closed only on verified absence; present or undeterminable, it stays unresolved.
            if (Presence.probe(ctx, volume.get(), u.path()).verifiedAbsent()) {
                UnresolvedObjects.resolveAbsent(ctx, u, "nothing was at " + u.path() + " when integrity scan " + scanId
                        + " checked");
            } else {
                counts[2]++;
            }
        }
    }

    private void reappeared(FileContent content, String resolution) {
        ctx.tx().executeWithoutResult(s -> {
            if (ctx.contents().setState(content.contentId(), ContentState.MISSING, ContentState.ACTIVE)) {
                for (IntegrityFinding f : ctx.integrity().openFindingsFor(content.contentId())) {
                    if (f.kind() == FindingKind.MISSING) {
                        ctx.integrity().resolve(f.findingId(), resolution, null);
                    }
                }
            }
        });
    }

    private void checkUnexpected(UUID scanId, ManagedVolume volume, int[] counts) {
        Set<String> accounted = new HashSet<>(ctx.contents().accountedPaths(volume.volumeId()));
        accounted.addAll(ctx.operations().plannedTargets(volume.volumeId()));
        // Unresolved objects are already recorded (and counted by checkUnresolved), not unaccounted for.
        accounted.addAll(ctx.unresolved().openPaths(volume.volumeId()));
        try {
            for (ManagedVolume.Entry entry : volume.listOriginals()) {
                if (entry.kind() == ManagedVolume.Entry.Kind.LINK_OR_REPARSE) {
                    record(scanId, new IntegrityRepository.NewFinding(FindingKind.LINK_OR_REPARSE, volume.volumeId(), null,
                            null, entry.path().value(), null, null, null,
                            "A link, junction or reparse point was found in managed storage; it was not followed"), counts);
                } else if (!accounted.contains(entry.path().value())) {
                    record(scanId, new IntegrityRepository.NewFinding(FindingKind.UNEXPECTED_OBJECT, volume.volumeId(), null,
                            null, entry.path().value(), null, entry.size(), null,
                            "An object the archive does not account for is in managed storage. It was not adopted or deleted."),
                            counts);
                }
            }
        } catch (IOException e) {
            record(scanId, new IntegrityRepository.NewFinding(FindingKind.UNREADABLE, volume.volumeId(), null, null,
                    "originals", null, null, null, "Managed storage could not be listed (" + e.getClass().getSimpleName() + ")"),
                    counts);
        }
    }

    private void checkStaging(UUID scanId, int[] counts) {
        Optional<StagingArea> staging = ctx.staging();
        if (staging.isEmpty()) {
            return;
        }
        try {
            for (String name : staging.get().list()) {
                Optional<UUID> op = StagingArea.operationOf(name);
                boolean expected = op.flatMap(id -> ctx.operations().find(id))
                        .map(o -> !o.stagingCleaned()).orElse(false);
                if (!expected) {
                    record(scanId, new IntegrityRepository.NewFinding(FindingKind.STAGING_LEFTOVER, null, null, null, name,
                            op.flatMap(id -> ctx.operations().find(id)).map(FileOperation::operationId).orElse(null),
                            null, null, "A file in the staging directory belongs to no unfinished operation. It was not deleted."),
                            counts);
                }
            }
        } catch (IOException e) {
            log.warn("Staging directory could not be listed during scan {}", scanId);
        }
    }

    private static IntegrityRepository.NewFinding finding(FindingKind kind, FileContent content, Long size, String sha,
                                                           String detail) {
        return new IntegrityRepository.NewFinding(kind, content.volumeId(), content.contentId(), content.currentGeneration(),
                content.currentPath() == null ? null : content.currentPath().value(), null, size, sha, detail);
    }

    private void record(UUID scanId, IntegrityRepository.NewFinding finding, int[] counts) {
        if (scanId != null && ctx.integrity().observe(scanId, finding).isNew()) {
            counts[1]++;
        } else if (scanId != null) {
            counts[2]++;
        } else {
            ctx.integrity().insertFindingIfNew(null, finding);
        }
    }

    /**
     * Administrator request to re-verify one content. The check runs in the worker, because restoring
     * lost read-only protection is a change to managed storage and the web process has no write access
     * to archival volumes.
     */
    public void requestReverify(Actor actor, UUID contentId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        FileContent content = ctx.contents().find(contentId).orElseThrow(() -> new NotFoundException("The content"));
        if (content.state() == ContentState.DESTROYED || content.state() == ContentState.DESTROY_PENDING) {
            throw new RuleViolationException("Destroyed content cannot be verified.");
        }
        ctx.jobs().enqueue(PreservationContext.JOB_REVERIFY, "{\"id\":\"" + contentId + "\",\"by\":\""
                + PreservationContext.jsonSafe(actor.label()) + "\"}", 5);
    }

    /**
     * Worker side of re-verification: re-applies read-only protection if it was lost, re-hashes the
     * stored original and, only if it matches the authoritative SHA-256, returns the content to ACTIVE
     * and resolves its open findings. Never changes the authoritative hash.
     *
     * @return true if the content verified
     */
    public boolean reverify(UUID contentId, String requestedBy) {
        FileContent content = ctx.contents().find(contentId).orElseThrow(() -> new NotFoundException("The content"));
        if (content.state() == ContentState.DESTROYED || content.state() == ContentState.DESTROY_PENDING) {
            return false;
        }
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(content.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            throw new RuleViolationException(e.getMessage());
        }
        ContentIo.Measurement m;
        try {
            Presence.Probe probe = Presence.probe(ctx, volume, content.currentPath().value());
            if (!probe.present()) {
                return false;
            }
            Path path = probe.path();
            SafeFiles.requirePlainFile(path);
            // Bytes first: an object that is not the content is never touched, not even its protection.
            m = ContentIo.measure(path);
            if (m.size() != content.byteSize() || !m.sha256().equals(content.sha256())) {
                return false;
            }
            if (!SafeFiles.isReadOnly(path)) {
                SafeFiles.makeReadOnly(path);
            }
            if (!SafeFiles.isReadOnly(path)) {
                ctx.integrity().insertFindingIfNew(null, finding(FindingKind.NOT_PROTECTED, content, m.size(), m.sha256(),
                        "Re-verification found the authoritative bytes but could not protect them read-only; not restored"));
                return false;
            }
        } catch (IOException e) {
            log.warn("Re-verification of content {} could not read the stored original: {}", contentId, e.getClass().getSimpleName());
            return false;
        }
        ctx.tx().executeWithoutResult(s -> {
            if (content.state() != ContentState.ACTIVE) {
                ctx.contents().setState(contentId, content.state(), ContentState.ACTIVE);
            }
            ctx.contents().markVerified(contentId, ctx.clock().instant());
            for (IntegrityFinding f : ctx.integrity().openFindingsFor(contentId)) {
                ctx.integrity().resolve(f.findingId(), "Re-verified at the request of " + requestedBy
                        + ": the stored original matches its authoritative size and SHA-256 and is protected.", null);
            }
        });
        return true;
    }
}
