package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Archival Records, their File links and Roles (BUILD-SPEC s.19; R-001...R-006).
 * <ul>
 *   <li>File-to-Record is many-to-many; page designation is independent of image sequence.</li>
 *   <li>Classification is correctable; identity, provenance and preservation originals never change (R-006,
 *       CT-003, CT-004, A-008). Nothing here touches stored bytes.</li>
 *   <li>Nothing beyond identity is required (A-003). Roles never create Persons or relationships (A-009, D3-01).</li>
 *   <li>Restriction (D3-04): a RESTRICTED Record exists only for actors with VIEW_RESTRICTED_RECORDS; restricted
 *       Files never appear through a Record for actors who may not see them.</li>
 *   <li>Lifecycle (D3-12): ACTIVE or DELETED (recoverable). A deleted Record keeps its identity, links, Roles,
 *       Citations and history and stays reachable by its identifier, marked deleted (as a deleted File does); it
 *       is left out of default lists and grouping, and nothing about it can be changed except restoring it.
 *       There is no purge or merge, and identifiers are never reused.</li>
 * </ul>
 */
public final class RecordService {

    private static final String NOT_FOUND = "The record";

    private final RecordsContext ctx;
    private final VocabularyService vocabularies;

    public RecordService(RecordsContext ctx, VocabularyService vocabularies) {
        this.ctx = ctx;
        this.vocabularies = vocabularies;
    }

    /**
     * What a researcher enters for a Record. Blank means not known. The verbatim transcription and the
     * researcher-written abstract are separate (D3-10); neither is OCR.
     */
    public record RecordInput(String classificationCode, String summary, UUID sourceId, String sourceLocator,
                              String recordDateText, String filingDateText, String transcriptionText,
                              String abstractText, Restriction restriction) {
    }

    /** A visible File shown with its link. */
    public record LinkedFile(RecordFileLink link, ArchivalFile file) {
    }

    /** Everything on a Record's page, already filtered for the actor. */
    public record RecordView(ArchivalRecord record, String classificationLabel, Optional<Source> source,
                             Optional<CaptureSession> session, List<LinkedFile> files, List<RecordRole> roles,
                             Map<String, String> roleLabels, List<Citation> citations, boolean canSeeRestricted) {
    }

    /** A visible Record that uses a File. */
    public record FileUse(ArchivalRecord record, RecordFileLink link) {
    }

    // ---- reading ---------------------------------------------------------------------------------

    public ArchivalRecord get(Actor actor, UUID recordId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.records().find(recordId).filter(r -> RecordAccess.canSee(actor, r))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    public ArchivalRecord getByFriendlyId(Actor actor, String text) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        long n = RecordAccess.friendlyNumber(text, ArchivalRecordRepository.PREFIX)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return ctx.records().findByNumber(n).filter(r -> RecordAccess.canSee(actor, r))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    public List<ArchivalRecord> list(Actor actor, boolean includeDeleted, String classification, UUID sourceId,
                                     UUID sessionId, int limit) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        boolean deleted = includeDeleted && AccessPolicy.allows(actor, Permission.EDIT_RECORDS);
        return ctx.records().list(deleted, RecordAccess.seesRestrictedRecords(actor),
                VocabularyService.normalizeCode(classification), sourceId, sessionId, Math.max(1, Math.min(limit, 500)));
    }

    public RecordView view(Actor actor, UUID recordId) {
        ArchivalRecord r = get(actor, recordId);
        Map<String, VocabularyTerm> classes = vocabularies.byCode(Vocabulary.CLASSIFICATION);
        String label = r.classificationCode() == null ? null
                : Optional.ofNullable(classes.get(r.classificationCode())).map(VocabularyTerm::label).orElse(r.classificationCode());
        List<LinkedFile> files = visibleLinks(actor, recordId);
        List<Citation> citations = ctx.records().citationsOfRecord(recordId,
                AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return new RecordView(r, label, Optional.ofNullable(r.sourceId()).flatMap(id -> ctx.catalog().findSource(id)),
                Optional.ofNullable(r.captureSessionId()).flatMap(id -> ctx.sessions().find(id)), files,
                ctx.records().roles(recordId), vocabularies.labels(actor, Vocabulary.ROLE_TYPE), citations,
                RecordAccess.seesRestrictedRecords(actor));
    }

    /** Visible Records using a visible File (for the File's page). */
    public List<FileUse> usesOfFile(Actor actor, UUID fileId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        ArchivalFile f = ctx.files().find(fileId).filter(x -> RecordAccess.canSee(actor, x))
                .orElseThrow(() -> new NotFoundException("The file"));
        List<FileUse> out = new ArrayList<>();
        for (RecordFileLink link : ctx.records().linksOfFile(f.fileId())) {
            ctx.records().find(link.recordId()).filter(r -> RecordAccess.canSee(actor, r))
                    .ifPresent(r -> out.add(new FileUse(r, link)));
        }
        return out;
    }

    public List<ChangeRecord> history(Actor actor, UUID recordId, HistoryService history) {
        get(actor, recordId);
        return history.forObject(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId);
    }

    // ---- creating and correcting -------------------------------------------------------------------

    private ArchivalRecordRepository.RecordFields fields(Actor actor, RecordInput in, String currentClassification,
                                                         Restriction currentRestriction) {
        Restriction restriction = in.restriction() == null ? currentRestriction : in.restriction();
        if (restriction != currentRestriction) {
            // D3-04: setting or lowering a Record's restriction is an Administrator decision.
            AccessPolicy.require(actor, Permission.VIEW_RESTRICTED_RECORDS);
        }
        if (in.sourceId() != null && ctx.catalog().findSource(in.sourceId()).isEmpty()) {
            throw new NotFoundException("The source");
        }
        return new ArchivalRecordRepository.RecordFields(
                vocabularies.requireUsable(Vocabulary.CLASSIFICATION, in.classificationCode(), currentClassification),
                Texts.optional(in.summary(), "The summary", 500),
                in.sourceId(),
                Texts.optional(in.sourceLocator(), "The book/page reference", 500),
                Texts.optional(in.recordDateText(), "The record date (as written)", 200),
                Texts.optional(in.filingDateText(), "The filing date (as written)", 200),
                Texts.optional(in.transcriptionText(), "The verbatim transcription", 100_000, true),
                Texts.optional(in.abstractText(), "The abstract", 20_000, true),
                restriction);
    }

    public ArchivalRecord create(Actor actor, RecordInput input) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        return ctx.tx().execute(status -> {
            ArchivalRecordRepository.RecordFields f = fields(actor, input, null, Restriction.NORMAL);
            return insert(actor, f, null);
        });
    }

    /** Inserts a Record in the caller's transaction (also used by courthouse grouping). */
    ArchivalRecord insert(Actor actor, ArchivalRecordRepository.RecordFields f, UUID captureSessionId) {
        UUID id = GlobalIds.newId();
        FriendlyId fid = ctx.friendlyIds().allocate(ArchivalRecordRepository.OBJECT_TYPE);
        ctx.records().insertRecord(id, fid.number(), f, captureSessionId, ctx.clock().instant(), actor.userId());
        List<FieldChange> changes = new ArrayList<>();
        changes.add(FieldChange.set("friendly_id", FieldClass.IMMUTABLE, fid.value()));
        if (captureSessionId != null) {
            changes.add(FieldChange.set("capture_session", FieldClass.IMMUTABLE,
                    ctx.sessions().find(captureSessionId).map(CaptureSession::friendlyId).orElseThrow()));
        }
        changes.addAll(recordChanges(new ArchivalRecordRepository.RecordFields(null, null, null, null, null, null, null,
                null, Restriction.NORMAL), f));
        ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, id, ChangeKinds.CREATED, changes, null);
        return ctx.records().find(id).orElseThrow();
    }

    public ArchivalRecord correct(Actor actor, UUID recordId, long expectedVersion, RecordInput input, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        return ctx.tx().execute(status -> {
            ArchivalRecord current = lockVisible(actor, recordId, expectedVersion);
            ArchivalRecordRepository.RecordFields f = fields(actor, input, current.classificationCode(), current.restriction());
            List<FieldChange> changes = recordChanges(fieldsOf(current), f);
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!ctx.records().update(recordId, expectedVersion, f)) {
                throw new ConflictException(NOT_FOUND);
            }
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId, ChangeKinds.CORRECTED, changes, reason);
            return ctx.records().find(recordId).orElseThrow();
        });
    }

    public ArchivalRecord delete(Actor actor, UUID recordId, long expectedVersion, String reason) {
        return lifecycle(actor, recordId, expectedVersion, RecordLifecycle.ACTIVE, RecordLifecycle.DELETED,
                ChangeKinds.DELETED, reason);
    }

    public ArchivalRecord restore(Actor actor, UUID recordId, long expectedVersion, String reason) {
        return lifecycle(actor, recordId, expectedVersion, RecordLifecycle.DELETED, RecordLifecycle.ACTIVE,
                ChangeKinds.RESTORED, reason);
    }

    private ArchivalRecord lifecycle(Actor actor, UUID recordId, long expectedVersion, RecordLifecycle from,
                                     RecordLifecycle to, String kind, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        return ctx.tx().execute(status -> {
            ArchivalRecord current = lockSeen(actor, recordId, expectedVersion);
            if (current.lifecycle() != from) {
                throw new RuleViolationException("The Record is " + current.lifecycle().name().toLowerCase() + ".");
            }
            boolean deleting = to == RecordLifecycle.DELETED;
            if (!ctx.records().updateLifecycle(recordId, expectedVersion, from, to, deleting ? ctx.clock().instant() : null,
                    deleting ? actor.userId() : null)) {
                throw new ConflictException(NOT_FOUND);
            }
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId, kind,
                    List.of(FieldChange.of("lifecycle", FieldClass.CORRECTABLE, from, to)), reason);
            return ctx.records().find(recordId).orElseThrow();
        });
    }

    // ---- File links ------------------------------------------------------------------------------

    public RecordFileLink linkFile(Actor actor, UUID recordId, long expectedVersion, UUID fileId, String page, String note) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String p = Texts.optional(page, "The page designation", 50);
        String n = Texts.optional(note, "The note", 2000, true);
        return ctx.tx().execute(status -> {
            ArchivalRecord record = lockVisible(actor, recordId, expectedVersion);
            ArchivalFile file = visibleFile(actor, fileId);
            if (file.lifecycle() == FileLifecycle.DESTROYED) {
                throw new RuleViolationException("A destroyed File cannot be linked.");
            }
            if (!insertLink(actor, record, file, ctx.records().maxLinkSequence(recordId) + 1, p, n, null)) {
                throw new RuleViolationException(file.friendlyId() + " is already linked to " + record.friendlyId() + ".");
            }
            touch(record);
            return ctx.records().link(recordId, fileId).orElseThrow();
        });
    }

    /**
     * Inserts a link and its history in the caller's transaction (also used by grouping).
     *
     * @return false if the File was already linked
     */
    boolean insertLink(Actor actor, ArchivalRecord record, ArchivalFile file, int sequence, String page, String note,
                       String reason) {
        if (!ctx.records().insertLink(record.recordId(), file.fileId(), sequence, page, note, ctx.clock().instant(),
                actor.userId())) {
            return false;
        }
        // The stored image sequence is never written to history: its gaps could reveal restricted Files (ADR-0011 s.8).
        List<FieldChange> changes = List.of(
                FieldChange.set("file", FieldClass.CORRECTABLE, file.friendlyId()),
                FieldChange.set("page_designation", FieldClass.CORRECTABLE, page),
                // GAS-P3-007: the researcher's note is part of the history from the start.
                FieldChange.set("note", FieldClass.CORRECTABLE, note));
        ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, record.recordId(), "FILE_LINKED", changes, reason);
        return true;
    }

    /** Corrects a link's page designation and note (R-001: the page is never derived from the image order). */
    public RecordFileLink correctLink(Actor actor, UUID recordId, long expectedVersion, UUID fileId, String page,
                                      String note) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String p = Texts.optional(page, "The page designation", 50);
        String n = Texts.optional(note, "The note", 2000, true);
        return ctx.tx().execute(status -> {
            ArchivalRecord record = lockVisible(actor, recordId, expectedVersion);
            ArchivalFile file = visibleFile(actor, fileId);
            RecordFileLink link = ctx.records().link(recordId, fileId).orElseThrow(() -> new NotFoundException("The link"));
            List<FieldChange> changes = List.of(
                    FieldChange.set("file", FieldClass.CORRECTABLE, file.friendlyId()),
                    FieldChange.of("page_designation", FieldClass.CORRECTABLE, link.pageDesignation(), p),
                    FieldChange.of("note", FieldClass.CORRECTABLE, link.note(), n));
            if (changes.stream().skip(1).noneMatch(FieldChange::changed)) {
                return link;
            }
            ctx.records().updateLink(recordId, fileId, p, n);
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId, "LINK_CORRECTED", changes, null);
            touch(record);
            return ctx.records().link(recordId, fileId).orElseThrow();
        });
    }

    /**
     * Moves a File one place earlier or later in the Record's image order, past its nearest neighbour that the
     * actor can see. Links the actor cannot see keep their places, so nothing about them is revealed or changed.
     *
     * @return false if the File is already first (or last) among the visible Files
     */
    public boolean moveLink(Actor actor, UUID recordId, long expectedVersion, UUID fileId, boolean earlier) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        Boolean moved = ctx.tx().execute(status -> {
            ArchivalRecord record = lockVisible(actor, recordId, expectedVersion);
            ArchivalFile file = visibleFile(actor, fileId);
            List<RecordFileLink> visible = visibleLinks(actor, recordId).stream().map(LinkedFile::link).toList();
            int at = -1;
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i).fileId().equals(fileId)) {
                    at = i;
                }
            }
            if (at < 0) {
                throw new NotFoundException("The link");
            }
            int other = earlier ? at - 1 : at + 1;
            if (other < 0 || other >= visible.size()) {
                return false;
            }
            ctx.records().swapSequences(recordId, fileId, visible.get(other).fileId());
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId, "LINK_MOVED", List.of(
                    FieldChange.set("file", FieldClass.CORRECTABLE, file.friendlyId()),
                    FieldChange.set("moved", FieldClass.CORRECTABLE, earlier ? "earlier" : "later")), null);
            touch(record);
            return true;
        });
        return Boolean.TRUE.equals(moved);
    }

    /** The Record's links whose Files the actor may see, in image order. */
    List<LinkedFile> visibleLinks(Actor actor, UUID recordId) {
        List<LinkedFile> files = new ArrayList<>();
        for (RecordFileLink link : ctx.records().links(recordId)) {
            ctx.files().find(link.fileId()).filter(f -> RecordAccess.canSee(actor, f))
                    .ifPresent(f -> files.add(new LinkedFile(link, f)));
        }
        return files;
    }

    public void unlinkFile(Actor actor, UUID recordId, long expectedVersion, UUID fileId, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        ctx.tx().executeWithoutResult(status -> {
            ArchivalRecord record = lockVisible(actor, recordId, expectedVersion);
            ArchivalFile file = visibleFile(actor, fileId);
            RecordFileLink link = ctx.records().link(recordId, fileId).orElseThrow(() -> new NotFoundException("The link"));
            ctx.records().removeLink(recordId, fileId);
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId, "FILE_UNLINKED", List.of(
                    FieldChange.of("file", FieldClass.CORRECTABLE, file.friendlyId(), null),
                    FieldChange.of("page_designation", FieldClass.CORRECTABLE, link.pageDesignation(), null),
                    // GAS-P3-007: the note survives in history after the association is removed.
                    FieldChange.of("note", FieldClass.CORRECTABLE, link.note(), null)), reason);
            touch(record);
        });
    }

    // ---- Roles -----------------------------------------------------------------------------------

    public RecordRole addRole(Actor actor, UUID recordId, long expectedVersion, String roleCode, String party, String note) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String p = Texts.optional(party, "The party as written", 500);
        String n = Texts.optional(note, "The note", 2000, true);
        return ctx.tx().execute(status -> {
            ArchivalRecord record = lockVisible(actor, recordId, expectedVersion);
            String code = vocabularies.requireUsable(Vocabulary.ROLE_TYPE, roleCode, null);
            if (code == null) {
                throw new RuleViolationException("Choose a role.");
            }
            UUID roleId = GlobalIds.newId();
            ctx.records().insertRole(roleId, recordId, code, p, n, ctx.records().maxRoleSequence(recordId) + 1,
                    ctx.clock().instant(), actor.userId());
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, recordId, "ROLE_ADDED", List.of(
                    FieldChange.set("role", FieldClass.CORRECTABLE, code),
                    FieldChange.set("party_as_written", FieldClass.CORRECTABLE, p),
                    FieldChange.set("role_note", FieldClass.CORRECTABLE, n)), null);
            touch(record);
            return ctx.records().role(roleId).orElseThrow();
        });
    }

    public RecordRole correctRole(Actor actor, UUID roleId, long recordVersion, long roleVersion, String roleCode,
                                  String party, String note) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String p = Texts.optional(party, "The party as written", 500);
        String n = Texts.optional(note, "The note", 2000, true);
        return ctx.tx().execute(status -> {
            RecordRole role = ctx.records().role(roleId).orElseThrow(() -> new NotFoundException("The role"));
            ArchivalRecord record = lockVisible(actor, role.recordId(), recordVersion);
            String code = vocabularies.requireUsable(Vocabulary.ROLE_TYPE, roleCode, role.roleCode());
            if (code == null) {
                throw new RuleViolationException("Choose a role.");
            }
            if (!ctx.records().updateRole(roleId, roleVersion, code, p, n)) {
                throw new ConflictException("The role");
            }
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, record.recordId(), "ROLE_CORRECTED", List.of(
                    FieldChange.of("role", FieldClass.CORRECTABLE, role.roleCode(), code),
                    FieldChange.of("party_as_written", FieldClass.CORRECTABLE, role.partyAsWritten(), p),
                    FieldChange.of("role_note", FieldClass.CORRECTABLE, role.note(), n)), null);
            touch(record);
            return ctx.records().role(roleId).orElseThrow();
        });
    }

    public void removeRole(Actor actor, UUID roleId, long recordVersion, long roleVersion, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        ctx.tx().executeWithoutResult(status -> {
            RecordRole role = ctx.records().role(roleId).orElseThrow(() -> new NotFoundException("The role"));
            ArchivalRecord record = lockVisible(actor, role.recordId(), recordVersion);
            if (!ctx.records().removeRole(roleId, roleVersion)) {
                throw new ConflictException("The role");
            }
            ctx.recorder().record(actor, ArchivalRecordRepository.OBJECT_TYPE, record.recordId(), "ROLE_REMOVED", List.of(
                    FieldChange.of("role", FieldClass.CORRECTABLE, role.roleCode(), null),
                    FieldChange.of("party_as_written", FieldClass.CORRECTABLE, role.partyAsWritten(), null)), reason);
            touch(record);
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /**
     * The Record locked for a change in this transaction: the actor may see it, the version is current and it is
     * ACTIVE (a deleted Record changes only by being restored, D3-12).
     */
    ArchivalRecord lockVisible(Actor actor, UUID recordId, long expectedVersion) {
        ArchivalRecord current = lockSeen(actor, recordId, expectedVersion);
        if (current.lifecycle() != RecordLifecycle.ACTIVE) {
            throw new RuleViolationException(current.friendlyId() + " is deleted; restore it before changing it.");
        }
        return current;
    }

    private ArchivalRecord lockSeen(Actor actor, UUID recordId, long expectedVersion) {
        ArchivalRecord current = ctx.records().lock(recordId).filter(r -> RecordAccess.canSee(actor, r))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        if (current.version() != expectedVersion) {
            throw new ConflictException(NOT_FOUND);
        }
        return current;
    }

    private ArchivalFile visibleFile(Actor actor, UUID fileId) {
        return ctx.files().find(fileId).filter(f -> RecordAccess.canSee(actor, f))
                .orElseThrow(() -> new NotFoundException("The file"));
    }

    /** Links and roles are part of a Record: changing them makes earlier copies of the Record stale. */
    void touch(ArchivalRecord record) {
        if (!ctx.records().touch(record.recordId(), record.version())) {
            throw new ConflictException(NOT_FOUND);
        }
    }

    static ArchivalRecordRepository.RecordFields fieldsOf(ArchivalRecord r) {
        return new ArchivalRecordRepository.RecordFields(r.classificationCode(), r.summary(), r.sourceId(),
                r.sourceLocator(), r.recordDateText(), r.filingDateText(), r.transcriptionText(), r.abstractText(),
                r.restriction());
    }

    private List<FieldChange> recordChanges(ArchivalRecordRepository.RecordFields b, ArchivalRecordRepository.RecordFields a) {
        return List.of(
                FieldChange.of("classification", FieldClass.CORRECTABLE, b.classificationCode(), a.classificationCode()),
                FieldChange.of("summary", FieldClass.CORRECTABLE, b.summary(), a.summary()),
                FieldChange.of("source", FieldClass.CORRECTABLE, sourceLabel(b.sourceId()), sourceLabel(a.sourceId())),
                FieldChange.of("source_locator", FieldClass.CORRECTABLE, b.sourceLocator(), a.sourceLocator()),
                FieldChange.of("record_date_as_written", FieldClass.CORRECTABLE, b.recordDateText(), a.recordDateText()),
                FieldChange.of("filing_date_as_written", FieldClass.CORRECTABLE, b.filingDateText(), a.filingDateText()),
                FieldChange.of("transcription_verbatim", FieldClass.CORRECTABLE, b.transcriptionText(), a.transcriptionText()),
                FieldChange.of("abstract", FieldClass.CORRECTABLE, b.abstractText(), a.abstractText()),
                FieldChange.of("restriction", FieldClass.CORRECTABLE, b.restriction(), a.restriction()));
    }

    private String sourceLabel(UUID sourceId) {
        return sourceId == null ? null : ctx.catalog().findSource(sourceId).map(Source::friendlyId).orElse(null);
    }

}
