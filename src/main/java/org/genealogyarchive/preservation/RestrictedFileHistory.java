package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.data.ChangeHistoryRepository;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.HistoryVisibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps restricted Archival Files out of Change History for readers who may not see them (P2-H, B2,
 * GAS-P2-003, GAS-P2-R02, GAS-P2-FV02, GAS-P2-IV02). Applied at read time, so it follows the current
 * restriction and also covers entries written before a File was restricted.
 * <ul>
 *   <li>Entries about a restricted File are removed.</li>
 *   <li>In the remaining entries - reasons, filenames and every other before/after value - a value is
 *       withheld (simply absent) unless every File identifier it refers to, whole or embedded and in any
 *       spelling the application resolves ({@link FileIdentifiers#referencesIn}), names something the
 *       reader may see: an unrestricted File, content used only by unrestricted Files, or another object
 *       that has Change History (archive, volume, user).</li>
 * </ul>
 * <p>One visibility policy for whole and embedded identifiers: which identifiers a text refers to is read
 * from the text alone, and the decision asks only whether each is visible. A restricted File and a File
 * that does not exist are never distinguished, so writing guessed identifiers - friendly IDs or UUIDs, in
 * any spelling or embedding - into a reason or filename shows nothing about whether a restricted File has
 * them. Text containing no identifier is never affected.
 */
public final class RestrictedFileHistory implements HistoryVisibility {

    private final ArchivalFileRepository files;
    private final ChangeHistoryRepository history;

    public RestrictedFileHistory(ArchivalFileRepository files, ChangeHistoryRepository history) {
        this.files = files;
        this.history = history;
    }

    @Override
    public boolean seesEverything(Actor actor) {
        return AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_FILES);
    }

    @Override
    public List<ChangeRecord> visibleTo(Actor actor, List<ChangeRecord> records) {
        if (seesEverything(actor) || records.isEmpty()) {
            return records;
        }
        Set<UUID> fileObjects = new HashSet<>();
        for (ChangeRecord r : records) {
            if (ArchivalFileRepository.OBJECT_TYPE.equals(r.objectType())) {
                fileObjects.add(r.objectId());
            }
        }
        Set<UUID> visibleFileObjects = files.unrestrictedFileIds(fileObjects);
        List<ChangeRecord> kept = records.stream()
                .filter(r -> !ArchivalFileRepository.OBJECT_TYPE.equals(r.objectType())
                        || visibleFileObjects.contains(r.objectId()))
                .toList();

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
        Set<Long> visibleNumbers = files.unrestrictedFriendlyNumbers(numbers);
        Set<UUID> visibleIds = new HashSet<>(files.unrestrictedFileIds(uuids));
        visibleIds.addAll(files.contentsOfOnlyUnrestrictedFiles(uuids));
        visibleIds.addAll(history.historyObjectsOtherThan(ArchivalFileRepository.OBJECT_TYPE, uuids));

        List<ChangeRecord> out = new ArrayList<>(kept.size());
        for (ChangeRecord r : kept) {
            List<FieldChange> fields = r.fieldChanges().stream()
                    .filter(f -> shown(f.before(), found, visibleIds, visibleNumbers)
                            && shown(f.after(), found, visibleIds, visibleNumbers))
                    .toList();
            String reason = shown(r.reason(), found, visibleIds, visibleNumbers) ? r.reason() : null;
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
        FileIdentifiers.References refs = FileIdentifiers.referencesIn(text);
        if (!refs.isEmpty()) {
            found.put(text, refs);
            uuids.addAll(refs.uuids());
            numbers.addAll(refs.friendlyNumbers());
        }
    }

    /** Shown only if everything the text refers to is visible to the reader (never "unless it is hidden"). */
    private static boolean shown(String text, Map<String, FileIdentifiers.References> found, Set<UUID> visibleIds,
                                 Set<Long> visibleNumbers) {
        FileIdentifiers.References refs = text == null ? null : found.get(text);
        return refs == null || (visibleNumbers.containsAll(refs.friendlyNumbers()) && visibleIds.containsAll(refs.uuids()));
    }
}
