package org.genealogyarchive.records;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.data.ArchivalRecordRepository;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.HistoryVisibility;
import org.genealogyarchive.preservation.FileIdentifiers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps restricted Archival Records out of Change History for readers who may not see them (D3-04). Applied when
 * history is read, after the File filter it wraps, so it follows the current restriction:
 * <ul>
 *   <li>entries about a restricted Record, or about a Citation of one, are removed;</li>
 *   <li>in the remaining entries a value or reason is withheld (simply absent) unless every Record it refers to is
 *       visible: an {@code R} friendly ID must name an existing unrestricted Record, and a UUID must not be a
 *       restricted Record's or such a Citation's.</li>
 * </ul>
 * A restricted Record and a nonexistent one are never distinguished: an {@code R} ID that names neither visible
 * Record is withheld either way, and a UUID that is not a visible File, content or object with history is already
 * withheld by the File filter.
 */
public final class RecordsHistoryVisibility implements HistoryVisibility {

    private final HistoryVisibility inner;
    private final ArchivalRecordRepository records;

    public RecordsHistoryVisibility(HistoryVisibility inner, ArchivalRecordRepository records) {
        this.inner = inner;
        this.records = records;
    }

    @Override
    public boolean seesEverything(Actor actor) {
        return inner.seesEverything(actor) && RecordAccess.seesRestrictedRecords(actor);
    }

    @Override
    public List<ChangeRecord> visibleTo(Actor actor, List<ChangeRecord> history) {
        List<ChangeRecord> filtered = inner.visibleTo(actor, history);
        if (RecordAccess.seesRestrictedRecords(actor) || filtered.isEmpty()) {
            return filtered;
        }
        Set<UUID> recordObjects = new HashSet<>();
        Set<UUID> citationObjects = new HashSet<>();
        for (ChangeRecord r : filtered) {
            if (ArchivalRecordRepository.OBJECT_TYPE.equals(r.objectType())) {
                recordObjects.add(r.objectId());
            } else if (ArchivalRecordRepository.CITATION_TYPE.equals(r.objectType())) {
                citationObjects.add(r.objectId());
            }
        }
        Set<UUID> hiddenObjects = new HashSet<>(records.restrictedAmong(recordObjects));
        hiddenObjects.addAll(records.citationsOfRestrictedRecords(citationObjects));
        List<ChangeRecord> kept = filtered.stream().filter(r -> !hiddenObjects.contains(r.objectId())).toList();

        Map<String, FileIdentifiers.References> found = new HashMap<>();
        Set<UUID> uuids = new HashSet<>();
        Set<Long> numbers = new HashSet<>();
        for (ChangeRecord r : kept) {
            collect(r.reason(), found, uuids, numbers);
            for (FieldChange f : r.fieldChanges()) {
                collect(f.before(), found, uuids, numbers);
                collect(f.after(), found, uuids, numbers);
            }
        }
        if (found.isEmpty()) {
            return kept;
        }
        Set<UUID> hiddenIds = new HashSet<>(records.restrictedAmong(uuids));
        hiddenIds.addAll(records.citationsOfRestrictedRecords(uuids));
        Set<Long> visibleNumbers = records.unrestrictedNumbers(numbers);
        List<ChangeRecord> out = new ArrayList<>(kept.size());
        for (ChangeRecord r : kept) {
            List<FieldChange> fields = r.fieldChanges().stream()
                    .filter(f -> shown(f.before(), found, hiddenIds, visibleNumbers)
                            && shown(f.after(), found, hiddenIds, visibleNumbers))
                    .toList();
            String reason = shown(r.reason(), found, hiddenIds, visibleNumbers) ? r.reason() : null;
            if (fields.size() == r.fieldChanges().size() && Objects.equals(reason, r.reason())) {
                out.add(r);
            } else {
                out.add(new ChangeRecord(r.changeId(), r.occurredAt(), r.actorKind(), r.actorUserId(), r.actorLabel(),
                        r.objectType(), r.objectId(), r.changeKind(), fields, reason));
            }
        }
        return out;
    }

    private static void collect(String text, Map<String, FileIdentifiers.References> found, Set<UUID> uuids,
                                Set<Long> numbers) {
        if (text == null || text.isEmpty() || found.containsKey(text)) {
            return;
        }
        FileIdentifiers.References refs = FileIdentifiers.referencesIn(text, ArchivalRecordRepository.PREFIX);
        if (!refs.isEmpty()) {
            found.put(text, refs);
            uuids.addAll(refs.uuids());
            numbers.addAll(refs.friendlyNumbers());
        }
    }

    private static boolean shown(String text, Map<String, FileIdentifiers.References> found, Set<UUID> hiddenIds,
                                 Set<Long> visibleNumbers) {
        FileIdentifiers.References refs = text == null ? null : found.get(text);
        return refs == null || (visibleNumbers.containsAll(refs.friendlyNumbers())
                && refs.uuids().stream().noneMatch(hiddenIds::contains));
    }
}
