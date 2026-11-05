package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.IntegrityRepository;
import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.StagingArea;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.platform.SafeFiles;
import org.genealogyarchive.service.NotFoundException;
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
        int[] counts = {0, 0};
        try {
            Map<UUID, Optional<ManagedVolume>> volumes = new HashMap<>();
            for (FileContent content : ctx.contents().expectedOnDisk()) {
                counts[0]++;
                Optional<ManagedVolume> volume = volumes.computeIfAbsent(content.volumeId(), id -> openOrReport(scanId, id, counts));
                if (volume.isPresent()) {
                    checkContent(scanId, volume.get(), content, full, counts);
                }
            }
            Set<UUID> volumeIds = new LinkedHashSet<>(volumes.keySet());
            ctx.archives().ingestionVolume().ifPresent(volumeIds::add);
            for (UUID volumeId : volumeIds) {
                Optional<ManagedVolume> volume = volumes.computeIfAbsent(volumeId, id -> openOrReport(scanId, id, counts));
                if (volume.isPresent()) {
                    checkUnexpected(scanId, volume.get(), counts);
                }
            }
            checkStaging(scanId, counts);
            ctx.integrity().finishScan(scanId, "COMPLETED", counts[0], counts[1],
                    counts[1] == 0 ? "No problems found." : counts[1] + " new problem(s) recorded.");
        } catch (RuntimeException e) {
            log.error("Integrity scan {} failed: {}", scanId, e.getMessage());
            ctx.integrity().finishScan(scanId, "FAILED", counts[0], counts[1], "The scan stopped: " + e.getClass().getSimpleName());
            throw e;
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
            Path path = volume.resolveExisting(content.currentPath());
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                record(scanId, finding(FindingKind.MISSING, content, null, null,
                        "Expected stored original not found at " + where), counts);
                ctx.contents().setState(content.contentId(), ContentState.ACTIVE, ContentState.MISSING);
                return;
            }
            if (SafeFiles.isLinkOrReparsePoint(path)) {
                record(scanId, finding(FindingKind.LINK_OR_REPARSE, content, null, null,
                        "The stored original's location is a link or reparse point"), counts);
                return;
            }
            long size = Files.size(path);
            if (size != content.byteSize()) {
                record(scanId, finding(FindingKind.SIZE_MISMATCH, content, size, null,
                        "Stored size " + size + " differs from the authoritative " + content.byteSize() + " bytes"), counts);
                markDamaged(content);
                return;
            }
            if (!SafeFiles.isReadOnly(path)) {
                record(scanId, finding(FindingKind.NOT_PROTECTED, content, null, null,
                        "The stored original has lost its read-only protection"), counts);
            }
            if (full) {
                ContentIo.Measurement m = ContentIo.measure(path);
                if (!m.sha256().equals(content.sha256())) {
                    record(scanId, finding(FindingKind.HASH_MISMATCH, content, m.size(), m.sha256(),
                            "SHA-256 of the stored original differs from the authoritative ingestion hash"), counts);
                    markDamaged(content);
                    return;
                }
                ctx.contents().markVerified(content.contentId(), ctx.clock().instant());
            }
            if (content.state() == ContentState.MISSING && full) {
                reappeared(content, "The stored original is present again and matches its authoritative SHA-256.");
            }
        } catch (SafeFiles.UnsafePathException e) {
            record(scanId, finding(FindingKind.LINK_OR_REPARSE, content, null, null,
                    "A directory above the stored original is a link, junction or reparse point"), counts);
        } catch (IOException e) {
            record(scanId, finding(FindingKind.UNREADABLE, content, null, null,
                    "The stored original could not be read (" + e.getClass().getSimpleName() + ")"), counts);
        }
    }

    private void markDamaged(FileContent content) {
        if (content.state() == ContentState.ACTIVE || content.state() == ContentState.MISSING) {
            ctx.contents().setState(content.contentId(), content.state(), ContentState.DAMAGED);
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
        if (ctx.integrity().insertFindingIfNew(scanId, finding).isPresent()) {
            counts[1]++;
        }
    }

    /**
     * Administrator re-verification of one content: re-applies read-only protection if it was lost,
     * re-hashes the stored original and, only if it matches the authoritative SHA-256, returns the
     * content to ACTIVE and resolves its open findings. Never changes the authoritative hash.
     *
     * @return true if the content verified
     */
    public boolean reverify(Actor actor, UUID contentId) {
        AccessPolicy.require(actor, Permission.MANAGE_PRESERVATION);
        FileContent content = ctx.contents().find(contentId).orElseThrow(() -> new NotFoundException("The content"));
        if (content.state() == ContentState.DESTROYED || content.state() == ContentState.DESTROY_PENDING) {
            throw new RuleViolationException("Destroyed content cannot be verified.");
        }
        ManagedVolume volume;
        try {
            volume = ctx.openVolume(content.volumeId());
        } catch (ManagedVolume.VolumeUnavailableException e) {
            throw new RuleViolationException(e.getMessage());
        }
        ContentIo.Measurement m;
        try {
            Path path = volume.resolveExisting(content.currentPath());
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            if (!SafeFiles.isReadOnly(path)) {
                SafeFiles.makeReadOnly(path);
            }
            m = ContentIo.measure(path);
        } catch (IOException e) {
            throw new RuleViolationException("The stored original could not be read (" + e.getClass().getSimpleName() + ").");
        }
        if (m.size() != content.byteSize() || !m.sha256().equals(content.sha256())) {
            return false;
        }
        ctx.tx().executeWithoutResult(s -> {
            if (content.state() != ContentState.ACTIVE) {
                ctx.contents().setState(contentId, content.state(), ContentState.ACTIVE);
            }
            ctx.contents().markVerified(contentId, ctx.clock().instant());
            for (IntegrityFinding f : ctx.integrity().openFindingsFor(contentId)) {
                ctx.integrity().resolve(f.findingId(), "Re-verified by " + actor.label()
                        + ": the stored original matches its authoritative size and SHA-256 and is protected.", null);
            }
        });
        return true;
    }
}
