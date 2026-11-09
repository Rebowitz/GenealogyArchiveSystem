package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.data.GroupingRunRepository;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Courthouse grouping (BUILD-SPEC s.38, s.40; R-004, R-005, BI-008, PF-002; D3-02; ADR-0011 s.4 and the two owner
 * safeguards of 2026-11-08).
 * <p><b>Planning</b> uses every row of the session in its stable order (sequence, then File id), whether or not the
 * person applying may see it, so a row they cannot see still marks the boundaries it marks for an Administrator:
 * <ul>
 *   <li>a row with {@code record_group} belongs to that group (the explicit group wins over Start New Record);</li>
 *   <li>otherwise a Start New Record row starts a group;</li>
 *   <li>otherwise a blank row continues the preceding row's group, or the Record of the preceding row if it was
 *       already applied;</li>
 *   <li>blank rows with nothing before them stay ungrouped: valid archived material (R-005).</li>
 * </ul>
 * <b>Holding (safeguard 1).</b> A group is not applied, and its rows are counted as held, if any of its rows, the
 * applied row it continues, or the Record it would extend is something the person applying may not see, or if that
 * Record is deleted. So a hidden Start New Record or explicit group can never be skipped into a silent merge; an
 * Administrator, who sees everything, applies such groups. Counts reported to a person cover only rows they may see.
 * <p><b>Bounded, resumable application (safeguard 2).</b> A group's applicable rows (ACTIVE Files) are applied in
 * chunks of at most {@link #CHUNK_ROWS} rows, each in its own transaction that creates or extends the group's
 * Record, links its Files in order, marks its rows applied (permanently, with the run) and records the chunk. The
 * group-to-Record identity is durable ({@code capture_session_group}), so later chunks, later runs and replays after
 * a crash extend the same Record; applied rows are final, so nothing is linked twice. A group whose rows were edited
 * after planning stops at that chunk (earlier chunks stay valid) and is counted as conflicted. A group is recorded as
 * completed only by the chunk that applies its last row.
 * <p><b>Stopping part-way (R-005).</b> A run may be bounded to the rows up to and including one File, so a researcher
 * who has marked only the first part of a batch applies only that part; later rows stay unapplied, valid archived
 * material, until they are marked and applied.
 * <p><b>Current authorization at commit (GAS-P3-004).</b> Each chunk transaction share-locks every File it applies
 * and every applied row it continues from, and re-reads them: if any is no longer visible to the person applying
 * (or a target Record is no longer visible or ACTIVE) the group's remaining work is held; if a File's lifecycle
 * changed since planning, the group's remaining work is a conflict, left for the next run. Committed chunks stay;
 * the group is not reported complete. Links are made with the freshly read File, never the planning snapshot.
 * <p>One run per session at a time (advisory lock); a run left RUNNING by a process that stopped is closed as
 * INTERRUPTED by the next one. Nothing here touches preservation originals.
 */
public final class GroupingService {

    /** Most rows applied in one transaction. Not a limit on the size of a group or Record. */
    public static final int CHUNK_ROWS = 1000;

    private static final String LOCK_NAMESPACE = "capture-session-grouping";

    private final RecordsContext ctx;
    private final RecordService records;
    private final CaptureSessionService sessions;
    private final GroupingFaults faults;

    public GroupingService(RecordsContext ctx, RecordService records, CaptureSessionService sessions) {
        this(ctx, records, sessions, GroupingFaults.NONE);
    }

    public GroupingService(RecordsContext ctx, RecordService records, CaptureSessionService sessions,
                           GroupingFaults faults) {
        this.ctx = ctx;
        this.records = records;
        this.sessions = sessions;
        this.faults = faults;
    }

    /** One planned row. {@code applicable}: its File is ACTIVE (others mark boundaries but are not linked). */
    private record PlanRow(CaptureRow row, ArchivalFile file, boolean visible, boolean applicable) {
    }

    /** One group of the plan. {@code targetRecordId} is set when the group extends an existing Record. */
    private static final class Group {
        final String key;
        UUID targetRecordId;
        boolean held;
        final List<PlanRow> rows = new ArrayList<>();
        /** Applied rows this group continues from (rule 3); their visibility is re-checked at commit. */
        final List<UUID> continuesFrom = new ArrayList<>();

        Group(String key) {
            this.key = key;
        }

        List<PlanRow> applicable() {
            return rows.stream().filter(PlanRow::applicable).toList();
        }
    }

    /** The planned groups (in order of first row) and the rows left ungrouped that the planner may see. */
    private record Plan(List<Group> groups, int ungroupedVisible) {
    }

    /** Thrown inside a chunk transaction to roll it back because a planned row or its target changed. */
    private static final class Conflict extends RuntimeException {
        Conflict() {
            super(null, null, false, false);
        }
    }

    /** Thrown inside a chunk transaction to roll it back because the group now depends on something hidden. */
    private static final class Hold extends RuntimeException {
        Hold() {
            super(null, null, false, false);
        }
    }

    /** Applies the whole worksheet. @return the run as recorded */
    public GroupingRun apply(Actor actor, UUID sessionId) {
        return apply(actor, sessionId, null);
    }

    /**
     * Applies the worksheet up to and including the row of {@code throughFileId} (all rows when null). That File
     * must be one of the session's rows the actor may see; otherwise it is "not found", as for a nonexistent File.
     *
     * @return the run as recorded
     */
    public GroupingRun apply(Actor actor, UUID sessionId, UUID throughFileId) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        sessions.get(actor, sessionId);
        Integer cutoffSequence = null;
        if (throughFileId != null) {
            Optional<CaptureRow> cutoff = ctx.sessions().row(throughFileId).filter(r -> r.captureSessionId().equals(sessionId));
            if (cutoff.isEmpty() || ctx.files().find(throughFileId).filter(f -> RecordAccess.canSee(actor, f)).isEmpty()) {
                throw new NotFoundException("The file");
            }
            cutoffSequence = cutoff.get().sequence();
        }
        Optional<OperationLocks.Held> held = ctx.locks().tryLock(LOCK_NAMESPACE, sessionId);
        if (held.isEmpty()) {
            throw new RuleViolationException("Grouping is already running for this capture session.");
        }
        try (OperationLocks.Held lock = held.get()) {
            UUID runId = GlobalIds.newId();
            boolean restrictedView = AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES);
            ctx.tx().executeWithoutResult(status -> ctx.runs().closeStale(sessionId, ctx.clock().instant()));
            faults.reach(GroupingFaults.Point.BEFORE_PLAN, null, null, 0);
            // Planned before the run is recorded: a cutoff missing from the snapshot rejects the request with
            // nothing applied and no run started (GAS-P3-005).
            Plan plan = plan(actor, sessionId, throughFileId, cutoffSequence);
            ctx.tx().executeWithoutResult(status ->
                    ctx.runs().start(runId, sessionId, restrictedView, ctx.clock().instant(), actor.userId()));
            try {
                int planned = 0;
                int heldRows = 0;
                int heldGroups = 0;
                for (Group g : plan.groups()) {
                    if (g.held) {
                        // Only rows this person may see are counted, so the counts reveal nothing hidden.
                        long visible = g.rows.stream().filter(r -> r.applicable() && r.visible()).count();
                        heldRows += (int) visible;
                        heldGroups += visible > 0 ? 1 : 0;
                    } else {
                        planned += g.applicable().size();
                    }
                }
                int p = planned;
                int hr = heldRows;
                int hg = heldGroups;
                ctx.tx().executeWithoutResult(status -> ctx.runs().planned(runId, p, plan.ungroupedVisible(), hr, hg));
                faults.reach(GroupingFaults.Point.AFTER_PLAN, runId, null, 0);
                for (Group g : plan.groups()) {
                    if (!g.held && !g.applicable().isEmpty()) {
                        applyGroup(actor, sessionId, runId, g);
                    }
                }
                faults.reach(GroupingFaults.Point.BEFORE_FINISH, runId, null, 0);
                ctx.tx().executeWithoutResult(status ->
                        ctx.runs().finish(runId, GroupingRun.State.COMPLETED, ctx.clock().instant()));
            } catch (RuntimeException e) {
                // Committed chunks stay; the run is closed as interrupted and a new run continues from them.
                try {
                    ctx.tx().executeWithoutResult(status ->
                            ctx.runs().finish(runId, GroupingRun.State.INTERRUPTED, ctx.clock().instant()));
                } catch (RuntimeException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
            return ctx.runs().find(runId).orElseThrow();
        }
    }

    /**
     * Runs of the session as the actor may see them (GAS-P3-003). A reader who can see restricted Files and Records
     * gets the stored accounting. Anyone else gets only runs started without restricted view, built from the run's
     * authorized contributions: applied Files whose File and applied Record the reader may both see now. Applied,
     * created and extended counts come from that set alone, unattributable counts are omitted, and a run with no
     * authorized contribution is not shown at all (GAS-P3-003). Stored runs are never changed.
     */
    public List<GroupingRunView> runs(Actor actor, UUID sessionId) {
        sessions.get(actor, sessionId);
        boolean full = AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES)
                && RecordAccess.seesRestrictedRecords(actor);
        List<GroupingRunView> out = new ArrayList<>();
        for (GroupingRun run : ctx.runs().ofSession(sessionId, full, 50)) {
            if (full) {
                out.add(GroupingRunView.full(run));
                continue;
            }
            // GAS-P3-003: a contribution is one File the run applied together with the Record it went into; it
            // counts only if the reader may see both, now. Retention and every counter use this one set.
            Set<UUID> createdHere = new LinkedHashSet<>();
            for (var chunk : ctx.runs().chunks(run.runId())) {
                if (chunk.created()) {
                    createdHere.add(chunk.recordId());
                }
            }
            int applied = 0;
            Set<UUID> visibleRecords = new LinkedHashSet<>();
            for (GroupingRunRepository.Contribution c : ctx.runs().contributions(run.runId())) {
                boolean fileVisible = ctx.files().find(c.fileId()).filter(f -> RecordAccess.canSee(actor, f)).isPresent();
                boolean recordVisible = ctx.records().find(c.recordId()).filter(r -> RecordAccess.canSee(actor, r)).isPresent();
                if (fileVisible && recordVisible) {
                    applied++;
                    visibleRecords.add(c.recordId());
                }
            }
            int visibleCreated = (int) visibleRecords.stream().filter(createdHere::contains).count();
            int visibleExtended = visibleRecords.size() - visibleCreated;
            if (applied == 0) {
                continue;
            }
            out.add(new GroupingRunView(run.runId(), run.state(), run.startedAt(), run.finishedAt(), applied,
                    visibleCreated, visibleExtended, null, null, null));
        }
        return out;
    }

    // ---- planning --------------------------------------------------------------------------------

    /**
     * Plans over one snapshot of the session's rows. With a cutoff, the cutoff row must be in that snapshot, at
     * the position validated when the request arrived, and visible; otherwise the request is rejected exactly as
     * for a File that does not exist. A missing cutoff never means "apply everything" (GAS-P3-005).
     */
    private Plan plan(Actor actor, UUID sessionId, UUID throughFileId, Integer cutoffSequence) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Group current = null;
        int ungrouped = 0;
        boolean reachedBound = false;
        for (CaptureRow row : ctx.sessions().rows(sessionId)) {
            if (reachedBound) {
                break;
            }
            ArchivalFile file = ctx.files().find(row.fileId()).orElseThrow();
            boolean visible = RecordAccess.canSee(actor, file);
            if (row.fileId().equals(throughFileId)) {
                if (!visible || cutoffSequence == null || row.sequence() != cutoffSequence) {
                    throw new NotFoundException("The file");
                }
                reachedBound = true;
            }
            if (row.applied()) {
                // Rule 3: following blank rows continue this row's Record - held unless the actor may see both the
                // row and the Record, and the Record is ACTIVE.
                Optional<ArchivalRecord> rec = ctx.records().find(row.appliedRecordId());
                Group g = groups.computeIfAbsent("r:" + row.appliedRecordId(), Group::new);
                g.targetRecordId = row.appliedRecordId();
                g.continuesFrom.add(row.fileId());
                if (!visible || rec.isEmpty() || !RecordAccess.canSee(actor, rec.get())
                        || rec.get().lifecycle() != RecordLifecycle.ACTIVE) {
                    g.held = true;
                }
                current = g;
                continue;
            }
            String key;
            if (row.recordGroup() != null) {
                key = "g:" + groupKey(row.recordGroup());
            } else if (row.startNewRecord()) {
                key = "s:" + row.fileId();
            } else if (current != null) {
                key = current.key;
            } else {
                if (visible) {
                    ungrouped++;
                }
                continue;
            }
            Group g = groups.computeIfAbsent(key, Group::new);
            g.rows.add(new PlanRow(row, file, visible, file.lifecycle() == FileLifecycle.ACTIVE));
            if (!visible) {
                g.held = true;
            }
            current = g;
        }
        for (Group g : groups.values()) {
            if (g.targetRecordId == null) {
                g.targetRecordId = ctx.sessions().groupRecord(sessionId, g.key).orElse(null);
            }
            if (g.targetRecordId != null && !g.held) {
                Optional<ArchivalRecord> rec = ctx.records().find(g.targetRecordId);
                if (rec.isEmpty() || !RecordAccess.canSee(actor, rec.get()) || rec.get().lifecycle() != RecordLifecycle.ACTIVE) {
                    g.held = true;
                }
            }
        }
        if (throughFileId != null && !reachedBound) {
            throw new NotFoundException("The file");
        }
        return new Plan(List.copyOf(groups.values()), ungrouped);
    }

    // ---- applying --------------------------------------------------------------------------------

    private void applyGroup(Actor actor, UUID sessionId, UUID runId, Group g) {
        List<PlanRow> rows = g.applicable();
        // Approved rule: a new Record takes the classification of its group's first row that has one. That row may
        // fall in a later chunk, so it is validated where the Record is created (GAS-P3-004, second audit).
        PlanRow classificationSource = rows.stream().filter(r -> r.row().classificationCode() != null)
                .findFirst().orElse(null);
        int chunkNumber = 0;
        for (int from = 0; from < rows.size(); from += CHUNK_ROWS) {
            List<PlanRow> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK_ROWS));
            boolean last = from + CHUNK_ROWS >= rows.size();
            int number = ++chunkNumber;
            faults.reach(GroupingFaults.Point.BEFORE_CHUNK, runId, g.key, number);
            try {
                ctx.tx().executeWithoutResult(status ->
                        applyChunk(actor, sessionId, runId, g, chunk, number, last, classificationSource));
            } catch (Conflict c) {
                int remaining = rows.size() - from;
                ctx.tx().executeWithoutResult(status -> ctx.runs().conflicted(runId, remaining));
                return;
            } catch (Hold h) {
                // Count only the remaining rows the starter can still see, so the count reveals nothing hidden.
                int visible = (int) rows.subList(from, rows.size()).stream()
                        .filter(r -> ctx.files().find(r.row().fileId()).filter(f -> RecordAccess.canSee(actor, f)).isPresent())
                        .count();
                ctx.tx().executeWithoutResult(status -> ctx.runs().held(runId, visible));
                return;
            }
            faults.reach(GroupingFaults.Point.AFTER_CHUNK, runId, g.key, number);
        }
    }

    private void applyChunk(Actor actor, UUID sessionId, UUID runId, Group g, List<PlanRow> chunk, int number,
                            boolean last, PlanRow classificationSource) {
        // Lock order: session, worksheet rows, Files (shared), Record - the same order as every other path that
        // takes more than one of these.
        CaptureSession session = ctx.sessions().lock(sessionId).orElseThrow(() -> new NotFoundException("The capture session"));
        for (PlanRow p : chunk) {
            CaptureRow now = ctx.sessions().lockRow(p.row().fileId()).orElse(null);
            if (now == null || now.applied() || now.version() != p.row().version()
                    || !now.captureSessionId().equals(sessionId)) {
                throw new Conflict();
            }
        }
        // GAS-P3-004: authorize with the Files as they are now, held stable until commit.
        List<ArchivalFile> current = new ArrayList<>(chunk.size());
        for (PlanRow p : chunk) {
            ArchivalFile f = ctx.files().lockShared(p.row().fileId()).orElseThrow(Conflict::new);
            if (!RecordAccess.canSee(actor, f)) {
                throw new Hold();
            }
            if (f.lifecycle() != FileLifecycle.ACTIVE) {
                throw new Conflict();
            }
            current.add(f);
        }
        for (UUID from : g.continuesFrom) {
            ArchivalFile f = ctx.files().lockShared(from).orElseThrow(Conflict::new);
            if (!RecordAccess.canSee(actor, f)) {
                throw new Hold();
            }
        }
        UUID target = g.targetRecordId;
        if (target == null) {
            target = ctx.sessions().groupRecord(sessionId, g.key).orElse(null);
        }
        ArchivalRecord record;
        boolean created = false;
        if (target != null) {
            record = ctx.records().lock(target).orElseThrow(Conflict::new);
            if (!RecordAccess.canSee(actor, record) || record.lifecycle() != RecordLifecycle.ACTIVE) {
                throw new Hold();
            }
        } else {
            // GAS-P3-004 (second audit): the row supplying the new Record's classification is a dependency of this
            // transaction even if it lies in a later chunk. Lock its worksheet row (same order: rows before Files)
            // and its File, and use its classification only if the row is unchanged and unapplied and its File is
            // ACTIVE and visible to the person applying; otherwise hold or conflict the group before anything is
            // written, so no hidden or stale value reaches the Record or its history.
            String classification = null;
            if (classificationSource != null) {
                CaptureRow src = ctx.sessions().lockRow(classificationSource.row().fileId()).orElse(null);
                if (src == null || src.applied() || src.version() != classificationSource.row().version()
                        || !src.captureSessionId().equals(sessionId)) {
                    throw new Conflict();
                }
                ArchivalFile srcFile = ctx.files().lockShared(src.fileId()).orElseThrow(Conflict::new);
                if (!RecordAccess.canSee(actor, srcFile)) {
                    throw new Hold();
                }
                if (srcFile.lifecycle() != FileLifecycle.ACTIVE) {
                    throw new Conflict();
                }
                classification = src.classificationCode();
            }
            record = records.insert(actor, new ArchivalRecordRepository.RecordFields(classification, null,
                    session.sourceId(), null, null, null, null, null, Restriction.NORMAL), sessionId);
            ctx.sessions().insertGroup(sessionId, g.key, record.recordId(), ctx.clock().instant());
            created = true;
        }
        String reason = "Courthouse grouping in " + session.friendlyId();
        int sequence = ctx.records().maxLinkSequence(record.recordId());
        for (int i = 0; i < chunk.size(); i++) {
            PlanRow p = chunk.get(i);
            if (records.insertLink(actor, record, current.get(i), sequence + 1, p.row().pageDesignation(), null, reason)) {
                sequence++;
            }
            if (!ctx.sessions().markApplied(p.row().fileId(), p.row().version(), record.recordId(), runId)) {
                throw new Conflict();
            }
        }
        records.touch(ctx.records().find(record.recordId()).orElseThrow());
        ctx.runs().recordChunk(runId, g.key, number, record.recordId(), created, chunk.size(), last, ctx.clock().instant());
        faults.reach(GroupingFaults.Point.BEFORE_CHUNK_COMMIT, runId, g.key, number);
        if (created) {
            g.targetRecordId = record.recordId();
        }
    }

    /** Group keys compare without surrounding space and case. */
    static String groupKey(String recordGroup) {
        return recordGroup.strip().toLowerCase(Locale.ROOT);
    }
}
