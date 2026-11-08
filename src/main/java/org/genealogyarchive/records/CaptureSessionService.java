package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.CaptureSessionRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileIdentifiers;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Capture Sessions (BUILD-SPEC s.38; BI-001, UI-004, UI-005): context entered once and inherited by many Files.
 * <ul>
 *   <li>Inheritance is resolved when read: correcting the session changes every File that does not override a
 *       value, and an override changes only its own File.</li>
 *   <li>Files are added explicitly (from an upload batch or by File IDs); ingestion is unchanged.</li>
 *   <li>The courthouse worksheet's marks (D3-02) are saved per row, so a half-finished batch is ordinary saved
 *       state (R-005). Applying them is {@link GroupingService}.</li>
 *   <li>Restricted Files are never shown, counted or changed for actors who may not see them.</li>
 * </ul>
 */
public final class CaptureSessionService {

    private static final String NOT_FOUND = "The capture session";
    /** Most Files added by one request. */
    public static final int ADD_LIMIT = 2000;

    private final RecordsContext ctx;
    private final VocabularyService vocabularies;

    public CaptureSessionService(RecordsContext ctx, VocabularyService vocabularies) {
        this.ctx = ctx;
        this.vocabularies = vocabularies;
    }

    /** What a researcher enters for a session; blank means not known. */
    public record SessionInput(String title, UUID repositoryId, UUID sourceId, LocalDate captureDate, String researcher,
                               String equipmentMethod, String notes) {
    }

    /**
     * A File's capture context as inherited and overridden, resolved when read. {@code overridden} names the fields
     * the File gives its own value; {@code blanked} names the fields it records as not known for this File even
     * though the session has a value. Every other field is the session's current value.
     */
    public record EffectiveContext(CaptureSession session, CaptureRow row, Optional<Repository> repository,
                                   Optional<Source> source, LocalDate captureDate, String researcher,
                                   String equipmentMethod, Set<String> overridden, Set<String> blanked) {
    }

    /** One worksheet row the actor may see. */
    public record WorksheetRow(CaptureRow row, ArchivalFile file) {
    }

    /** A page of the worksheet. {@code hasMore} says whether visible rows follow. */
    public record Worksheet(CaptureSession session, List<WorksheetRow> rows, int offset, boolean hasMore) {
    }

    /** One row's marks as submitted from the worksheet. */
    public record MarkEdit(UUID fileId, long version, boolean startNewRecord, String recordGroup, String pageDesignation,
                           String classificationCode) {
    }

    /** What adding Files did. */
    public record AddResult(int added, int alreadyInASession, int notAvailable) {
    }

    // ---- sessions --------------------------------------------------------------------------------

    private CaptureSessionRepository.SessionFields fields(SessionInput in) {
        if (in.repositoryId() != null && ctx.catalog().findRepository(in.repositoryId()).isEmpty()) {
            throw new NotFoundException("The repository");
        }
        if (in.sourceId() != null && ctx.catalog().findSource(in.sourceId()).isEmpty()) {
            throw new NotFoundException("The source");
        }
        return new CaptureSessionRepository.SessionFields(Texts.required(in.title(), "The session title", 300),
                in.repositoryId(), in.sourceId(), in.captureDate(), Texts.optional(in.researcher(), "The researcher", 200),
                Texts.optional(in.equipmentMethod(), "The equipment or method", 500),
                Texts.optional(in.notes(), "The notes", 8000, true));
    }

    public CaptureSession create(Actor actor, SessionInput input) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        return ctx.tx().execute(status -> {
            CaptureSessionRepository.SessionFields f = fields(input);
            UUID id = GlobalIds.newId();
            FriendlyId fid = ctx.friendlyIds().allocate(CaptureSessionRepository.OBJECT_TYPE);
            ctx.sessions().insert(id, fid.number(), f, ctx.clock().instant(), actor.userId());
            List<FieldChange> changes = new ArrayList<>();
            changes.add(FieldChange.set("friendly_id", FieldClass.IMMUTABLE, fid.value()));
            changes.addAll(sessionChanges(new CaptureSessionRepository.SessionFields(null, null, null, null, null, null, null), f));
            ctx.recorder().record(actor, CaptureSessionRepository.OBJECT_TYPE, id, ChangeKinds.CREATED, changes, null);
            return ctx.sessions().find(id).orElseThrow();
        });
    }

    public CaptureSession get(Actor actor, UUID sessionId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.sessions().find(sessionId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    public CaptureSession getByFriendlyId(Actor actor, String text) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        long n = RecordAccess.friendlyNumber(text, CaptureSessionRepository.PREFIX)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return ctx.sessions().findByNumber(n).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    public List<CaptureSession> list(Actor actor, UUID sourceId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.sessions().list(sourceId, 500);
    }

    public CaptureSession correct(Actor actor, UUID sessionId, long expectedVersion, SessionInput input, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        return ctx.tx().execute(status -> {
            CaptureSession current = ctx.sessions().lock(sessionId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
            if (current.version() != expectedVersion) {
                throw new ConflictException(NOT_FOUND);
            }
            CaptureSessionRepository.SessionFields f = fields(input);
            List<FieldChange> changes = sessionChanges(new CaptureSessionRepository.SessionFields(current.title(),
                    current.repositoryId(), current.sourceId(), current.captureDate(), current.researcher(),
                    current.equipmentMethod(), current.notes()), f);
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!ctx.sessions().update(sessionId, expectedVersion, f)) {
                throw new ConflictException(NOT_FOUND);
            }
            ctx.recorder().record(actor, CaptureSessionRepository.OBJECT_TYPE, sessionId, ChangeKinds.CORRECTED, changes, reason);
            return ctx.sessions().find(sessionId).orElseThrow();
        });
    }

    public List<ChangeRecord> history(Actor actor, UUID sessionId, HistoryService history) {
        get(actor, sessionId);
        return history.forObject(actor, CaptureSessionRepository.OBJECT_TYPE, sessionId);
    }

    public List<UploadBatchSummary> recentBatches(Actor actor) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        return ctx.sessions().recentBatches(50);
    }

    // ---- membership ------------------------------------------------------------------------------

    /**
     * Adds the Files of an upload batch that the actor may see, in ingestion order (GAS-P3-002). Files the actor
     * may not see are not candidates at all, so no count in the result (added, already in a session, not
     * available) can reveal how many restricted Files the batch holds. Explicitly entered IDs keep the
     * hidden-equals-nonexistent rule instead ({@link #addByFriendlyIds}).
     */
    public AddResult addBatch(Actor actor, UUID sessionId, UUID batchId) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        List<UUID> visible = ctx.sessions().filesOfBatch(batchId).stream()
                .filter(id -> ctx.files().find(id).filter(f -> RecordAccess.canSee(actor, f)).isPresent())
                .toList();
        Counts c = add(actor, sessionId, visible);
        // A File restricted between the filter above and the insert is neither added nor counted (GAS-P3-002).
        return new AddResult(c.added(), c.taken(), c.destroyed());
    }

    /**
     * Adds Files named by friendly IDs: single IDs and inclusive ranges ({@code F00010-F00210}), separated by
     * commas, spaces or line breaks.
     */
    public AddResult addByFriendlyIds(Actor actor, UUID sessionId, String text) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        List<Long> numbers = new ArrayList<>();
        for (String token : (text == null ? "" : text).split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            String[] ends = token.split("-", -1);
            if (ends.length == 1) {
                numbers.add(fileNumber(ends[0]));
            } else if (ends.length == 2) {
                long a = fileNumber(ends[0]);
                long b = fileNumber(ends[1]);
                if (b < a || b - a >= ADD_LIMIT) {
                    throw new RuleViolationException("The range " + token + " is reversed or too long (at most "
                            + ADD_LIMIT + " Files).");
                }
                for (long n = a; n <= b; n++) {
                    numbers.add(n);
                }
            } else {
                throw new RuleViolationException("Not a File ID or range: " + token);
            }
            if (numbers.size() > ADD_LIMIT) {
                throw new RuleViolationException("At most " + ADD_LIMIT + " Files can be added at once.");
            }
        }
        if (numbers.isEmpty()) {
            throw new RuleViolationException("Enter File IDs such as F00010 or a range such as F00010-F00210.");
        }
        List<UUID> ids = new ArrayList<>();
        int unknown = 0;
        for (long n : new LinkedHashSet<>(numbers)) {
            Optional<ArchivalFile> f = ctx.files().findByFriendlyNumber(n);
            if (f.isPresent()) {
                ids.add(f.get().fileId());
            } else {
                unknown++;
            }
        }
        AddResult r = addFiles(actor, sessionId, ids);
        return new AddResult(r.added(), r.alreadyInASession(), r.notAvailable() + unknown);
    }

    private static long fileNumber(String token) {
        OptionalLong n = FileIdentifiers.friendlyNumber(token);
        if (n.isEmpty()) {
            throw new RuleViolationException("Not a File ID: " + token);
        }
        return n.getAsLong();
    }

    /**
     * Adds Files at the end of the session's sequence, in the given order. Files the actor may not see, or that
     * were destroyed, are counted as not available - exactly like IDs that do not exist.
     */
    public AddResult addFiles(Actor actor, UUID sessionId, List<UUID> fileIds) {
        Counts c = add(actor, sessionId, fileIds);
        return new AddResult(c.added(), c.taken(), c.destroyed() + c.hidden());
    }

    /** What an add did; {@code hidden} counts Files the actor may not see (or that do not exist). */
    private record Counts(int added, int taken, int destroyed, int hidden) {
    }

    private Counts add(Actor actor, UUID sessionId, List<UUID> fileIds) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        if (fileIds.size() > ADD_LIMIT) {
            throw new RuleViolationException("At most " + ADD_LIMIT + " Files can be added at once.");
        }
        return ctx.tx().execute(status -> {
            CaptureSession session = ctx.sessions().lock(sessionId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
            int sequence = ctx.sessions().maxSequence(sessionId);
            int added = 0;
            int taken = 0;
            int destroyed = 0;
            int hidden = 0;
            for (UUID fileId : new LinkedHashSet<>(fileIds)) {
                Optional<ArchivalFile> file = ctx.files().find(fileId).filter(f -> RecordAccess.canSee(actor, f));
                if (file.isEmpty()) {
                    hidden++;
                    continue;
                }
                if (file.get().lifecycle() == FileLifecycle.DESTROYED) {
                    destroyed++;
                    continue;
                }
                if (!ctx.sessions().addRow(fileId, sessionId, sequence + 1, ctx.clock().instant(), actor.userId())) {
                    taken++;
                    continue;
                }
                sequence++;
                added++;
                // No stored sequence in history: its gaps could reveal restricted Files (ADR-0011 s.8).
                ctx.recorder().record(actor, CaptureSessionRepository.OBJECT_TYPE, sessionId, "FILE_ADDED", List.of(
                        FieldChange.set("file", FieldClass.CORRECTABLE, file.get().friendlyId())), null);
                ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, fileId, "CAPTURE_SESSION_SET", List.of(
                        FieldChange.set("capture_session", FieldClass.CORRECTABLE, session.friendlyId())), null);
            }
            return new Counts(added, taken, destroyed, hidden);
        });
    }

    /** Removes a File from its session (only before it has been grouped into a Record). */
    public void removeFile(Actor actor, UUID fileId, long expectedVersion, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        ctx.tx().executeWithoutResult(status -> {
            ArchivalFile file = visibleFile(actor, fileId);
            // Lock order session -> row, as in adding, saving marks and grouping (no lock-order inversion).
            UUID sessionOfRow = ctx.sessions().row(fileId).map(CaptureRow::captureSessionId)
                    .orElseThrow(() -> new NotFoundException("The session entry"));
            CaptureSession session = ctx.sessions().lock(sessionOfRow).orElseThrow();
            CaptureRow row = ctx.sessions().lockRow(fileId).filter(r -> r.captureSessionId().equals(sessionOfRow))
                    .orElseThrow(() -> new ConflictException("The session entry"));
            if (row.version() != expectedVersion) {
                throw new ConflictException("The session entry");
            }
            if (row.applied()) {
                throw new RuleViolationException(file.friendlyId() + " has already been grouped into a Record; "
                        + "unlink it from the Record instead.");
            }
            if (!ctx.sessions().removeRow(fileId, expectedVersion)) {
                throw new ConflictException("The session entry");
            }
            ctx.recorder().record(actor, CaptureSessionRepository.OBJECT_TYPE, session.captureSessionId(), "FILE_REMOVED",
                    List.of(FieldChange.of("file", FieldClass.CORRECTABLE, file.friendlyId(), null)), reason);
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, fileId, "CAPTURE_SESSION_SET", List.of(
                    FieldChange.of("capture_session", FieldClass.CORRECTABLE, session.friendlyId(), null)), reason);
        });
    }

    // ---- inheritance -----------------------------------------------------------------------------

    /** The File's capture context, if it belongs to a session; inherited values resolved now. */
    public Optional<EffectiveContext> context(Actor actor, UUID fileId) {
        visibleFile(actor, fileId);
        return ctx.sessions().row(fileId).map(row -> {
            CaptureSession s = ctx.sessions().find(row.captureSessionId()).orElseThrow();
            Set<String> overridden = new LinkedHashSet<>();
            Set<String> blanked = new LinkedHashSet<>();
            UUID repo = pick(row.repositoryMode(), row.overrideRepositoryId(), s.repositoryId(), "repository", overridden, blanked);
            UUID source = pick(row.sourceMode(), row.overrideSourceId(), s.sourceId(), "source", overridden, blanked);
            LocalDate date = pick(row.captureDateMode(), row.overrideCaptureDate(), s.captureDate(), "capture_date",
                    overridden, blanked);
            String researcher = pick(row.researcherMode(), row.overrideResearcher(), s.researcher(), "researcher",
                    overridden, blanked);
            String method = pick(row.equipmentMethodMode(), row.overrideEquipmentMethod(), s.equipmentMethod(),
                    "equipment_method", overridden, blanked);
            return new EffectiveContext(s, forReader(actor, row), Optional.ofNullable(repo).flatMap(id -> ctx.catalog().findRepository(id)),
                    Optional.ofNullable(source).flatMap(id -> ctx.catalog().findSource(id)), date, researcher, method,
                    Set.copyOf(overridden), Set.copyOf(blanked));
        });
    }

    /** Tri-state resolution (plan section 10.2): inherit the session's value, use the File's own, or none. */
    static <T> T pick(OverrideMode mode, T own, T inherited, String name, Set<String> overridden, Set<String> blanked) {
        return switch (mode) {
            case INHERIT -> inherited;
            case VALUE -> {
                overridden.add(name);
                yield own;
            }
            case BLANK -> {
                blanked.add(name);
                yield null;
            }
        };
    }

    /**
     * Sets one File's overrides. Each field inherits the session's value, has the File's own value, or is
     * explicitly blank for this File. Only that File changes; the session and its other Files do not (BI-001).
     * Recorded in the File's Change History as a mode and a value per field, so "inherits" and "explicitly not
     * known" are never confused with a value. Correcting the session itself is recorded only in the session's
     * history: inheriting Files' history is never rewritten.
     */
    public CaptureRow correctOverrides(Actor actor, UUID fileId, long expectedVersion, CaptureOverrides o,
                                       String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        CaptureOverrides clean = new CaptureOverrides(o.repository(), o.source(),
                o.captureDate(), text(o.researcher(), "The researcher", 200),
                text(o.equipmentMethod(), "The equipment or method", 500));
        return ctx.tx().execute(status -> {
            visibleFile(actor, fileId);
            CaptureRow row = ctx.sessions().lockRow(fileId).orElseThrow(() -> new NotFoundException("The session entry"));
            if (row.version() != expectedVersion) {
                throw new ConflictException("The session entry");
            }
            CaptureOverrides before = CaptureOverrides.of(row);
            List<FieldChange> changes = new ArrayList<>();
            overrideChanges(changes, "capture_repository", before.repository().mode(), clean.repository().mode(),
                    label(before.repository().value(), true), label(clean.repository().value(), true));
            overrideChanges(changes, "capture_source", before.source().mode(), clean.source().mode(),
                    label(before.source().value(), false), label(clean.source().value(), false));
            overrideChanges(changes, "capture_date", before.captureDate().mode(), clean.captureDate().mode(),
                    before.captureDate().value(), clean.captureDate().value());
            overrideChanges(changes, "capture_researcher", before.researcher().mode(), clean.researcher().mode(),
                    before.researcher().value(), clean.researcher().value());
            overrideChanges(changes, "capture_equipment_method", before.equipmentMethod().mode(),
                    clean.equipmentMethod().mode(), before.equipmentMethod().value(), clean.equipmentMethod().value());
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return forReader(actor, row);
            }
            if (!ctx.sessions().updateOverrides(fileId, expectedVersion, clean)) {
                throw new ConflictException("The session entry");
            }
            ctx.recorder().record(actor, ArchivalFileRepository.OBJECT_TYPE, fileId, "CAPTURE_CONTEXT_CORRECTED", changes, reason);
            return forReader(actor, ctx.sessions().row(fileId).orElseThrow());
        });
    }

    private static FieldOverride<String> text(FieldOverride<String> o,
                                                                    String what, int max) {
        if (o.mode() != OverrideMode.VALUE) {
            return o;
        }
        String clean = Texts.optional(o.value(), what, max);
        if (clean == null) {
            throw new RuleViolationException(what + " override needs a value; choose \"not known for this File\" instead.");
        }
        return FieldOverride.of(clean);
    }

    private static void overrideChanges(List<FieldChange> out, String field, OverrideMode modeBefore, OverrideMode modeAfter,
                                        Object valueBefore, Object valueAfter) {
        out.add(FieldChange.of(field + "_mode", FieldClass.CORRECTABLE, modeBefore.name(), modeAfter.name()));
        out.add(FieldChange.of(field, FieldClass.CORRECTABLE, valueBefore, valueAfter));
    }

    private String label(UUID id, boolean repository) {
        if (id == null) {
            return null;
        }
        return repository
                ? ctx.catalog().findRepository(id).map(Repository::friendlyId).orElseThrow(() -> new NotFoundException("The repository"))
                : ctx.catalog().findSource(id).map(Source::friendlyId).orElseThrow(() -> new NotFoundException("The source"));
    }

    // ---- worksheet -------------------------------------------------------------------------------

    /** A page of visible rows, in sequence order. */
    public Worksheet worksheet(Actor actor, UUID sessionId, int offset, int limit) {
        CaptureSession session = get(actor, sessionId);
        int from = Math.max(0, offset);
        int size = Math.max(1, Math.min(limit, 200));
        List<WorksheetRow> visible = visibleRows(actor, sessionId);
        int to = Math.min(visible.size(), from + size);
        List<WorksheetRow> page = from >= visible.size() ? List.of() : visible.subList(from, to);
        return new Worksheet(session, List.copyOf(page), from, to < visible.size());
    }

    /**
     * A row as the actor may see it (GAS-P3-001): the applied Record is named only when the actor may see that
     * Record; a row grouped into a Record they cannot see shows only that it is grouped.
     */
    CaptureRow forReader(Actor actor, CaptureRow row) {
        boolean recordVisible = row.appliedRecordId() != null && ctx.records().find(row.appliedRecordId())
                .filter(r -> RecordAccess.canSee(actor, r)).isPresent();
        return row.forReader(recordVisible);
    }

    List<WorksheetRow> visibleRows(Actor actor, UUID sessionId) {
        List<WorksheetRow> out = new ArrayList<>();
        for (CaptureRow row : ctx.sessions().rows(sessionId)) {
            ctx.files().find(row.fileId()).filter(f -> RecordAccess.canSee(actor, f))
                    .ifPresent(f -> out.add(new WorksheetRow(forReader(actor, row), f)));
        }
        return out;
    }

    /**
     * Saves worksheet marks for some rows in one transaction. Rows already grouped are not changed. Each row is
     * checked against the version the researcher saw (SEC-008).
     *
     * @return how many rows changed
     */
    public int saveMarks(Actor actor, UUID sessionId, List<MarkEdit> edits) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        if (edits.size() > 500) {
            throw new RuleViolationException("At most 500 rows can be saved at once.");
        }
        Integer changed = ctx.tx().execute(status -> {
            CaptureSession session = ctx.sessions().lock(sessionId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
            int n = 0;
            for (MarkEdit e : edits) {
                ArchivalFile file = visibleFile(actor, e.fileId());
                CaptureRow row = ctx.sessions().lockRow(e.fileId())
                        .filter(r -> r.captureSessionId().equals(session.captureSessionId()))
                        .orElseThrow(() -> new NotFoundException("The session entry"));
                String group = Texts.optional(e.recordGroup(), "The record group", 100);
                String page = Texts.optional(e.pageDesignation(), "The page designation", 50);
                String cls = vocabularies.requireUsable(Vocabulary.CLASSIFICATION, e.classificationCode(), row.classificationCode());
                List<FieldChange> changes = List.of(
                        FieldChange.set("file", FieldClass.CORRECTABLE, file.friendlyId()),
                        FieldChange.of("start_new_record", FieldClass.CORRECTABLE, row.startNewRecord(), e.startNewRecord()),
                        FieldChange.of("record_group", FieldClass.CORRECTABLE, row.recordGroup(), group),
                        FieldChange.of("page_designation", FieldClass.CORRECTABLE, row.pageDesignation(), page),
                        FieldChange.of("classification", FieldClass.CORRECTABLE, row.classificationCode(), cls));
                if (changes.stream().skip(1).noneMatch(FieldChange::changed)) {
                    continue;
                }
                if (row.version() != e.version()) {
                    throw new ConflictException("The worksheet row for " + file.friendlyId());
                }
                if (row.applied()) {
                    throw new RuleViolationException(file.friendlyId() + " has already been grouped; its marks no longer change.");
                }
                if (!ctx.sessions().updateMarks(e.fileId(), e.version(),
                        new CaptureSessionRepository.Marks(e.startNewRecord(), group, page, cls))) {
                    throw new ConflictException("The worksheet row for " + file.friendlyId());
                }
                ctx.recorder().record(actor, CaptureSessionRepository.OBJECT_TYPE, sessionId, "WORKSHEET_CORRECTED", changes, null);
                n++;
            }
            return n;
        });
        return changed == null ? 0 : changed;
    }

    private ArchivalFile visibleFile(Actor actor, UUID fileId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.files().find(fileId).filter(f -> RecordAccess.canSee(actor, f))
                .orElseThrow(() -> new NotFoundException("The file"));
    }

    private List<FieldChange> sessionChanges(CaptureSessionRepository.SessionFields b, CaptureSessionRepository.SessionFields a) {
        return List.of(
                FieldChange.of("title", FieldClass.CORRECTABLE, b.title(), a.title()),
                FieldChange.of("repository", FieldClass.CORRECTABLE, label(b.repositoryId(), true), label(a.repositoryId(), true)),
                FieldChange.of("source", FieldClass.CORRECTABLE, label(b.sourceId(), false), label(a.sourceId(), false)),
                FieldChange.of("capture_date", FieldClass.CORRECTABLE, b.captureDate(), a.captureDate()),
                FieldChange.of("researcher", FieldClass.CORRECTABLE, b.researcher(), a.researcher()),
                FieldChange.of("equipment_method", FieldClass.CORRECTABLE, b.equipmentMethod(), a.equipmentMethod()),
                FieldChange.of("notes", FieldClass.CORRECTABLE, b.notes(), a.notes()));
    }
}
