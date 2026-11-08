package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileLifecycle;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Courthouse grouping (BUILD-SPEC s.38, s.40; R-004, R-005, BI-008, PF-002; D3-02). Rows are taken in session
 * order, considering only rows whose File the actor may see and that is not deleted or destroyed:
 * <ul>
 *   <li>a row with {@code record_group} belongs to that group - explicit groups are authoritative when both marks
 *       are present - and a group key keeps meaning the same Record for the whole session;</li>
 *   <li>otherwise a Start New Record row starts a new Record;</li>
 *   <li>otherwise a blank row continues the preceding row's Record (a Record created earlier included);</li>
 *   <li>blank rows with nothing before them stay ungrouped: valid archived material (R-005).</li>
 * </ul>
 * Each Record is created or extended in its own transaction, and its rows are marked applied there, so an
 * interruption loses at most the group in progress and a rerun never duplicates (BI-008). Nothing here touches
 * preservation originals.
 */
public final class GroupingService {

    private final RecordsContext ctx;
    private final RecordService records;
    private final CaptureSessionService sessions;

    public GroupingService(RecordsContext ctx, RecordService records, CaptureSessionService sessions) {
        this.ctx = ctx;
        this.records = records;
        this.sessions = sessions;
    }

    /** What applying the worksheet did. */
    public record Result(int recordsCreated, int recordsExtended, int rowsApplied, int rowsUngrouped) {
    }

    /** Where a row goes: an existing Record or a new one identified by a key. */
    private record Target(UUID existingRecordId, String newKey, String explicitKey) {
    }

    /** One group to apply: its target and its rows, in order. */
    private record Group(Target target, List<CapturePlanRow> rows) {
    }

    private record CapturePlanRow(CaptureRow row, ArchivalFile file) {
    }

    public Result apply(Actor actor, UUID sessionId) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        CaptureSession session = sessions.get(actor, sessionId);
        Map<String, Group> groups = new LinkedHashMap<>();
        Target current = null;
        int ungrouped = 0;
        for (CaptureSessionService.WorksheetRow wr : sessions.visibleRows(actor, sessionId)) {
            CaptureRow row = wr.row();
            if (wr.file().lifecycle() != FileLifecycle.ACTIVE) {
                continue;
            }
            if (row.applied()) {
                // Continuation is possible only into a Record this actor may see (otherwise it is not "there").
                current = ctx.records().find(row.appliedRecordId())
                        .filter(r -> RecordAccess.canSee(actor, r) && r.lifecycle() == RecordLifecycle.ACTIVE)
                        .map(r -> new Target(r.recordId(), null, null)).orElse(null);
                continue;
            }
            Target target;
            if (row.recordGroup() != null) {
                String key = groupKey(row.recordGroup());
                Optional<UUID> existing = ctx.sessions().groupRecord(sessionId, key);
                target = existing.isPresent() ? new Target(existing.get(), null, key) : new Target(null, "g:" + key, key);
            } else if (row.startNewRecord()) {
                target = new Target(null, "s:" + row.fileId(), null);
            } else if (current != null) {
                target = current;
            } else {
                ungrouped++;
                continue;
            }
            current = target;
            String mapKey = target.existingRecordId() != null ? "r:" + target.existingRecordId() : target.newKey();
            groups.computeIfAbsent(mapKey, k -> new Group(target, new ArrayList<>())).rows().add(new CapturePlanRow(row, wr.file()));
        }
        int created = 0;
        int extended = 0;
        int applied = 0;
        for (Group g : groups.values()) {
            int[] outcome = applyGroup(actor, session.captureSessionId(), g);
            created += outcome[0];
            extended += outcome[1];
            applied += outcome[2];
        }
        return new Result(created, extended, applied, ungrouped);
    }

    /** Applies one group in its own transaction. @return {created, extended, rowsApplied} */
    private int[] applyGroup(Actor actor, UUID sessionId, Group g) {
        int[] out = ctx.tx().execute(status -> {
            CaptureSession session = ctx.sessions().lock(sessionId).orElseThrow(() -> new NotFoundException("The capture session"));
            // Re-read the rows under the session lock: rows applied meanwhile (another run) are skipped.
            List<CapturePlanRow> rows = new ArrayList<>();
            for (CapturePlanRow p : g.rows()) {
                ctx.sessions().lockRow(p.row().fileId())
                        .filter(r -> r.captureSessionId().equals(sessionId) && !r.applied())
                        .ifPresent(r -> rows.add(new CapturePlanRow(r, p.file())));
            }
            if (rows.isEmpty()) {
                return new int[]{0, 0, 0};
            }
            ArchivalRecord record;
            boolean createdNow = false;
            UUID existing = g.target().existingRecordId();
            if (existing == null && g.target().explicitKey() != null) {
                existing = ctx.sessions().groupRecord(sessionId, g.target().explicitKey()).orElse(null);
            }
            if (existing != null) {
                Optional<ArchivalRecord> found = ctx.records().lock(existing)
                        .filter(r -> RecordAccess.canSee(actor, r) && r.lifecycle() == RecordLifecycle.ACTIVE);
                if (found.isEmpty()) {
                    // Not available to this actor (or deleted): its rows stay as they are, ungrouped for now.
                    return new int[]{0, 0, 0};
                }
                record = found.get();
            } else {
                String classification = rows.stream().map(p -> p.row().classificationCode())
                        .filter(c -> c != null).findFirst().orElse(null);
                record = records.insert(actor, new ArchivalRecordRepository.RecordFields(classification, null,
                        session.sourceId(), null, null, null, null, Restriction.NORMAL), sessionId);
                if (g.target().explicitKey() != null) {
                    ctx.sessions().insertGroup(sessionId, g.target().explicitKey(), record.recordId(), ctx.clock().instant());
                }
                createdNow = true;
            }
            int sequence = ctx.records().maxLinkSequence(record.recordId());
            int applied = 0;
            for (CapturePlanRow p : rows) {
                if (records.insertLink(actor, record, p.file(), sequence + 1, p.row().pageDesignation(), null, null, null,
                        "Courthouse grouping in " + session.friendlyId())) {
                    sequence++;
                }
                ctx.sessions().markApplied(p.row().fileId(), record.recordId());
                applied++;
            }
            ArchivalRecord fresh = ctx.records().find(record.recordId()).orElseThrow();
            records.touch(fresh);
            return new int[]{createdNow ? 1 : 0, createdNow ? 0 : 1, applied};
        });
        return out == null ? new int[]{0, 0, 0} : out;
    }

    /** Group keys compare without surrounding space and case. */
    static String groupKey(String recordGroup) {
        return recordGroup.strip().toLowerCase(Locale.ROOT);
    }
}
