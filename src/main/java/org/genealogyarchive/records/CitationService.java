package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.util.List;
import java.util.UUID;

/**
 * Citation foundation (BUILD-SPEC s.17): a Source and/or a Record, a locator and the citation text as entered. No
 * style formatting. A Citation of a Record the actor may not see is treated as nonexistent (D3-04).
 */
public final class CitationService {

    private static final String NOT_FOUND = "The citation";

    private final RecordsContext ctx;

    public CitationService(RecordsContext ctx) {
        this.ctx = ctx;
    }

    public Citation get(Actor actor, UUID citationId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.records().citation(citationId).filter(c -> visible(actor, c))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** Active Citations of a Source the actor may see. */
    public List<Citation> ofSource(Actor actor, UUID sourceId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.records().citationsOfSource(sourceId).stream().filter(c -> visible(actor, c)).toList();
    }

    public Citation create(Actor actor, UUID sourceId, UUID recordId, String locator, String text, String note) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        if (sourceId == null && recordId == null) {
            throw new RuleViolationException("A citation refers to a Source, a Record, or both.");
        }
        String l = Texts.optional(locator, "The locator", 500);
        String t = Texts.optional(text, "The citation", 4000, true);
        String n = Texts.optional(note, "The note", 2000, true);
        return ctx.tx().execute(status -> {
            String source = sourceId == null ? null : ctx.catalog().findSource(sourceId).map(Source::friendlyId)
                    .orElseThrow(() -> new NotFoundException("The source"));
            String record = recordId == null ? null : ctx.records().find(recordId).filter(r -> RecordAccess.canSee(actor, r))
                    .map(ArchivalRecord::friendlyId).orElseThrow(() -> new NotFoundException("The record"));
            UUID id = GlobalIds.newId();
            FriendlyId fid = ctx.friendlyIds().allocate(ArchivalRecordRepository.CITATION_TYPE);
            ctx.records().insertCitation(id, fid.number(), sourceId, recordId, l, t, n, ctx.clock().instant(), actor.userId());
            ctx.recorder().record(actor, ArchivalRecordRepository.CITATION_TYPE, id, ChangeKinds.CREATED, List.of(
                    FieldChange.set("friendly_id", FieldClass.IMMUTABLE, fid.value()),
                    FieldChange.set("source", FieldClass.CORRECTABLE, source),
                    FieldChange.set("record", FieldClass.CORRECTABLE, record),
                    FieldChange.set("locator", FieldClass.CORRECTABLE, l),
                    FieldChange.set("citation", FieldClass.CORRECTABLE, t),
                    FieldChange.set("note", FieldClass.CORRECTABLE, n)), null);
            return ctx.records().citation(id).orElseThrow();
        });
    }

    public Citation correct(Actor actor, UUID citationId, long expectedVersion, String locator, String text, String note,
                            boolean deleted) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String l = Texts.optional(locator, "The locator", 500);
        String t = Texts.optional(text, "The citation", 4000, true);
        String n = Texts.optional(note, "The note", 2000, true);
        return ctx.tx().execute(status -> {
            Citation current = get(actor, citationId);
            if (current.version() != expectedVersion) {
                throw new ConflictException(NOT_FOUND);
            }
            RecordLifecycle lifecycle = deleted ? RecordLifecycle.DELETED : RecordLifecycle.ACTIVE;
            List<FieldChange> changes = List.of(
                    FieldChange.of("locator", FieldClass.CORRECTABLE, current.locator(), l),
                    FieldChange.of("citation", FieldClass.CORRECTABLE, current.citationText(), t),
                    FieldChange.of("note", FieldClass.CORRECTABLE, current.note(), n),
                    FieldChange.of("lifecycle", FieldClass.CORRECTABLE, current.lifecycle(), lifecycle));
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!ctx.records().updateCitation(citationId, expectedVersion, l, t, n, lifecycle)) {
                throw new ConflictException(NOT_FOUND);
            }
            ctx.recorder().record(actor, ArchivalRecordRepository.CITATION_TYPE, citationId,
                    deleted && current.lifecycle() == RecordLifecycle.ACTIVE ? ChangeKinds.DELETED : ChangeKinds.CORRECTED,
                    changes, null);
            return ctx.records().citation(citationId).orElseThrow();
        });
    }

    private boolean visible(Actor actor, Citation c) {
        return c.recordId() == null || ctx.records().find(c.recordId()).filter(r -> RecordAccess.canSee(actor, r)).isPresent();
    }
}
