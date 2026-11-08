package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Researcher-driven consolidation of redundant captures (BUILD-SPEC s.33; F-006, F-007; P2-G, D3-03).
 * <ul>
 *   <li>Candidates: two or more ACTIVE Files the actor may see, all of the same restriction; none derived from
 *       another candidate (a derivative is not an independent capture).</li>
 *   <li>The preview shows each redundant File's Record links, session placement and derivatives before anything
 *       changes.</li>
 *   <li>Executing, in one transaction: each redundant File's Record links stay as they are (provenance); where the
 *       preferred File is not linked to that Record, a new link names the consolidation and the File it was carried
 *       from - nothing claims the preferred File was the historical source of another capture's relationships.
 *       Redundant Files move to recoverable deletion. No derivation is created.</li>
 *   <li>Permanent deletion later is the existing Administrator destruction, which keeps a tombstone (F-007).</li>
 * </ul>
 */
public final class ConsolidationService {

    private final RecordsContext ctx;
    private final RecordService records;

    public ConsolidationService(RecordsContext ctx, RecordService records) {
        this.ctx = ctx;
        this.records = records;
    }

    /** What would happen to one redundant File. */
    public record MemberPreview(ArchivalFile file, List<RecordService.FileUse> recordLinks,
                                List<RecordService.FileUse> linksToCarry, Optional<CaptureRow> captureRow,
                                List<ArchivalFile> derivatives) {
    }

    /** The full preview: shown before consolidation (F-006). */
    public record Preview(ArchivalFile preferred, List<MemberPreview> redundant, Map<UUID, Long> versions) {
    }

    public Preview preview(Actor actor, UUID preferredFileId, List<UUID> redundantFileIds) {
        requirePermissions(actor);
        List<UUID> redundantIds = distinct(preferredFileId, redundantFileIds);
        ArchivalFile preferred = candidate(actor, preferredFileId);
        List<ArchivalFile> members = redundantIds.stream().map(id -> candidate(actor, id)).toList();
        validate(preferred, members);
        Set<UUID> preferredRecords = ctx.records().linksOfFile(preferredFileId).stream()
                .map(RecordFileLink::recordId).collect(Collectors.toSet());
        List<MemberPreview> out = new ArrayList<>();
        for (ArchivalFile m : members) {
            List<RecordService.FileUse> uses = records.usesOfFile(actor, m.fileId());
            List<RecordService.FileUse> carry = uses.stream()
                    .filter(u -> !preferredRecords.contains(u.record().recordId())).toList();
            List<ArchivalFile> derivatives = ctx.files().derivativesOf(m.fileId()).stream()
                    .map(id -> ctx.files().find(id).orElseThrow()).filter(f -> RecordAccess.canSee(actor, f)).toList();
            out.add(new MemberPreview(m, uses, carry, ctx.sessions().row(m.fileId()), derivatives));
        }
        Map<UUID, Long> versions = new java.util.LinkedHashMap<>();
        versions.put(preferred.fileId(), preferred.version());
        members.forEach(m -> versions.put(m.fileId(), m.version()));
        return new Preview(preferred, out, versions);
    }

    /**
     * Consolidates, if every File is still in the state the researcher previewed ({@code expectedVersions}).
     *
     * @return the consolidation's ID
     */
    public UUID consolidate(Actor actor, UUID preferredFileId, List<UUID> redundantFileIds, Map<UUID, Long> expectedVersions,
                            String reason) {
        requirePermissions(actor);
        List<UUID> redundantIds = distinct(preferredFileId, redundantFileIds);
        String why = Texts.optional(reason, "The reason", 4000, true);
        return ctx.tx().execute(status -> {
            ArchivalFile preferred = lockCandidate(actor, preferredFileId, expectedVersions);
            List<ArchivalFile> members = new ArrayList<>();
            for (UUID id : redundantIds) {
                members.add(lockCandidate(actor, id, expectedVersions));
            }
            validate(preferred, members);
            UUID consolidationId = GlobalIds.newId();
            ctx.consolidations().insert(consolidationId, preferredFileId, redundantIds, why, ctx.clock().instant(),
                    actor.userId());
            String note = "Consolidation " + consolidationId;
            for (ArchivalFile m : members) {
                for (RecordFileLink link : ctx.records().linksOfFile(m.fileId())) {
                    if (ctx.records().link(link.recordId(), preferredFileId).isPresent()) {
                        continue;
                    }
                    ArchivalRecord record = ctx.records().lock(link.recordId()).orElseThrow();
                    records.insertLink(actor, record, preferred, link.sequence(), link.pageDesignation(),
                            "Carried from " + m.friendlyId() + " by consolidation", consolidationId, m,
                            "Redundant capture " + m.friendlyId() + " consolidated into " + preferred.friendlyId());
                    records.touch(ctx.records().find(record.recordId()).orElseThrow());
                }
                if (!ctx.files().updateLifecycle(m.fileId(), m.version(), FileLifecycle.ACTIVE, FileLifecycle.DELETED,
                        ctx.clock().instant(), actor.userId(), null, null, null, null)) {
                    throw new ConflictException("The file " + m.friendlyId());
                }
                ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, m.fileId(), ChangeKinds.DELETED, List.of(
                        FieldChange.of("lifecycle", FieldClass.CORRECTABLE, FileLifecycle.ACTIVE, FileLifecycle.DELETED),
                        FieldChange.set("preferred_capture", FieldClass.CORRECTABLE, preferred.friendlyId())),
                        why == null ? "Redundant capture (" + note + ")" : why);
            }
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, preferredFileId, "CONSOLIDATED", List.of(
                    FieldChange.set("redundant_captures", FieldClass.CORRECTABLE,
                            members.stream().map(ArchivalFile::friendlyId).collect(Collectors.joining(", ")))), why);
            return consolidationId;
        });
    }

    public Consolidation get(Actor actor, UUID consolidationId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        Consolidation c = ctx.consolidations().find(consolidationId)
                .orElseThrow(() -> new NotFoundException("The consolidation"));
        List<UUID> all = new ArrayList<>(c.redundantFileIds());
        all.add(c.preferredFileId());
        for (UUID id : all) {
            ctx.files().find(id).filter(f -> RecordAccess.canSee(actor, f))
                    .orElseThrow(() -> new NotFoundException("The consolidation"));
        }
        return c;
    }

    private static void requirePermissions(Actor actor) {
        AccessPolicy.require(actor, Permission.EDIT_FILES);
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
    }

    private static List<UUID> distinct(UUID preferred, List<UUID> redundant) {
        Set<UUID> ids = new LinkedHashSet<>(redundant);
        ids.remove(preferred);
        if (ids.isEmpty()) {
            throw new RuleViolationException("Select the preferred capture and at least one redundant capture.");
        }
        if (ids.size() > 50) {
            throw new RuleViolationException("At most 50 redundant captures can be consolidated at once.");
        }
        return List.copyOf(ids);
    }

    private ArchivalFile candidate(Actor actor, UUID fileId) {
        ArchivalFile f = ctx.files().find(fileId).filter(x -> RecordAccess.canSee(actor, x))
                .orElseThrow(() -> new NotFoundException("The file"));
        if (f.lifecycle() != FileLifecycle.ACTIVE) {
            throw new RuleViolationException(f.friendlyId() + " is not active; only active Files can be consolidated.");
        }
        return f;
    }

    private ArchivalFile lockCandidate(Actor actor, UUID fileId, Map<UUID, Long> expectedVersions) {
        ArchivalFile f = ctx.files().lock(fileId).filter(x -> RecordAccess.canSee(actor, x))
                .orElseThrow(() -> new NotFoundException("The file"));
        Long expected = expectedVersions.get(fileId);
        if (expected == null || expected != f.version()) {
            throw new ConflictException("The file " + f.friendlyId());
        }
        if (f.lifecycle() != FileLifecycle.ACTIVE) {
            throw new RuleViolationException(f.friendlyId() + " is not active; only active Files can be consolidated.");
        }
        return f;
    }

    private void validate(ArchivalFile preferred, List<ArchivalFile> members) {
        List<ArchivalFile> all = new ArrayList<>(members);
        all.add(preferred);
        if (all.stream().map(ArchivalFile::restriction).distinct().count() > 1) {
            throw new RuleViolationException("Consolidate only Files with the same restriction.");
        }
        Set<UUID> ids = all.stream().map(ArchivalFile::fileId).collect(Collectors.toSet());
        for (ArchivalFile f : all) {
            Optional<UUID> parent = ctx.files().derivation(f.fileId()).map(d -> d.parentFileId());
            if (parent.isPresent() && ids.contains(parent.get())) {
                throw new RuleViolationException(f.friendlyId() + " is a derivative of another selected File, not an "
                        + "independent capture.");
            }
        }
    }
}
