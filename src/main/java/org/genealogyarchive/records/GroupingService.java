package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.data.OperationLocks;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        if (throughFileId != null) {
            boolean ok = ctx.sessions().row(throughFileId).filter(r -> r.captureSessionId().equals(sessionId)).isPresent()
                    && ctx.files().find(throughFileId).filter(f -> RecordAccess.canSee(actor, f)).isPresent();
            if (!ok) {
                throw new NotFoundException("The file");
            }
        }
        Optional<OperationLocks.Held> held = ctx.locks().tryLock(LOCK_NAMESPACE, sessionId);
        if (held.isEmpty()) {
            throw new RuleViolationException("Grouping is already running for this capture session.");
        }
        try (OperationLocks.Held lock = held.get()) {
            UUID runId = GlobalIds.newId();
            boolean restrictedView = AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES);
            ctx.tx().executeWithoutResult(status -> {
                ctx.runs().closeStale(sessionId, ctx.clock().instant());
                ctx.runs().start(runId, sessionId, restrictedView, ctx.clock().instant(), actor.userId());
            });
            try {
                Plan plan = plan(actor, sessionId, throughFileId);
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

    /** Runs of the session the actor may see (a run started with restricted view only for those who have it). */
    public List<GroupingRun> runs(Actor actor, UUID sessionId) {
        sessions.get(actor, sessionId);
        return ctx.runs().ofSession(sessionId, AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES), 50);
    }

    // ---- planning --------------------------------------------------------------------------------

    private Plan plan(Actor actor, UUID sessionId, UUID throughFileId) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Group current = null;
        int ungrouped = 0;
        boolean reachedBound = false;
        for (CaptureRow row : ctx.sessions().rows(sessionId)) {
            if (reachedBound) {
                break;
            }
            reachedBound = row.fileId().equals(throughFileId);
            ArchivalFile file = ctx.files().find(row.fileId()).orElseThrow();
            boolean visible = RecordAccess.canSee(actor, file);
            if (row.applied()) {
                // Rule 3: following blank rows continue this row's Record - held unless the actor may see both the
                // row and the Record, and the Record is ACTIVE.
                Optional<ArchivalRecord> rec = ctx.records().find(row.appliedRecordId());
                Group g = groups.computeIfAbsent("r:" + row.appliedRecordId(), Group::new);
                g.targetRecordId = row.appliedRecordId();
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
        return new Plan(List.copyOf(groups.values()), ungrouped);
    }

    // ---- applying --------------------------------------------------------------------------------

    private void applyGroup(Actor actor, UUID sessionId, UUID runId, Group g) {
        List<PlanRow> rows = g.applicable();
        String classification = rows.stream().map(r -> r.row().classificationCode()).filter(Objects::nonNull)
                .findFirst().orElse(null);
        int chunkNumber = 0;
        for (int from = 0; from < rows.size(); from += CHUNK_ROWS) {
            List<PlanRow> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK_ROWS));
            boolean last = from + CHUNK_ROWS >= rows.size();
            int number = ++chunkNumber;
            faults.reach(GroupingFaults.Point.BEFORE_CHUNK, runId, g.key, number);
            try {
                ctx.tx().executeWithoutResult(status ->
                        applyChunk(actor, sessionId, runId, g, chunk, number, last, classification));
            } catch (Conflict c) {
                int remaining = rows.size() - from;
                ctx.tx().executeWithoutResult(status -> ctx.runs().conflicted(runId, remaining));
                return;
            }
            faults.reach(GroupingFaults.Point.AFTER_CHUNK, runId, g.key, number);
        }
    }

    private void applyChunk(Actor actor, UUID sessionId, UUID runId, Group g, List<PlanRow> chunk, int number,
                            boolean last, String classification) {
        CaptureSession session = ctx.sessions().lock(sessionId).orElseThrow(() -> new NotFoundException("The capture session"));
        for (PlanRow p : chunk) {
            CaptureRow now = ctx.sessions().lockRow(p.row().fileId()).orElse(null);
            if (now == null || now.applied() || now.version() != p.row().version()
                    || !now.captureSessionId().equals(sessionId)) {
                throw new Conflict();
            }
        }
        UUID target = g.targetRecordId;
        if (target == null) {
            target = ctx.sessions().groupRecord(sessionId, g.key).orElse(null);
        }
        ArchivalRecord record;
        boolean created = false;
        if (target != null) {
            record = ctx.records().lock(target)
                    .filter(r -> RecordAccess.canSee(actor, r) && r.lifecycle() == RecordLifecycle.ACTIVE)
                    .orElseThrow(Conflict::new);
        } else {
            record = records.insert(actor, new ArchivalRecordRepository.RecordFields(classification, null,
                    session.sourceId(), null, null, null, null, null, Restriction.NORMAL), sessionId);
            ctx.sessions().insertGroup(sessionId, g.key, record.recordId(), ctx.clock().instant());
            created = true;
        }
        String reason = "Courthouse grouping in " + session.friendlyId();
        int sequence = ctx.records().maxLinkSequence(record.recordId());
        for (PlanRow p : chunk) {
            if (records.insertLink(actor, record, p.file(), sequence + 1, p.row().pageDesignation(), null, reason)) {
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
