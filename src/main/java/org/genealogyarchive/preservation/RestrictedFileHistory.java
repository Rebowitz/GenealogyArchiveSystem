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
 * GAS-P2-003, GAS-P2-R02, GAS-P2-FV02). Applied at read time, so it follows the current restriction and
 * also covers entries written before a File was restricted.
 * <ul>
 *   <li>Entries about a restricted File are removed.</li>
 *   <li>In the remaining entries, a field value or reason is withheld (simply absent) when
 *     <ol>
 *       <li>an identifier written in it as a whole - a friendly ID, or a delimited UUID - does not name
 *           something the reader may see: an unrestricted File, content used only by unrestricted Files, or
 *           another object that has Change History (archive, volume, user). A restricted File and a File
 *           that does not exist are treated identically here, so writing a guessed identifier into a
 *           reason or a filename shows nothing about whether a restricted File has it; or</li>
 *       <li>any substring of it, in any spelling the application resolves ({@link FileIdentifiers}),
 *           is the UUID of a restricted File or of content one uses - so no spelling leaks one.</li>
 *     </ol></li>
 * </ul>
 * Text containing no identifier is never affected. The second rule can only be triggered by someone who
 * already knows a restricted File's UUID (UUIDs cannot be guessed); for such a person the File's own page
 * already answers "not found" where it used to show the File, so it reveals nothing more than the lookup.
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
        Set<UUID> primary = new HashSet<>();
        Set<Long> numbers = new HashSet<>();
        for (ChangeRecord r : kept) {
            collect(r.reason(), found, uuids, primary, numbers);
            for (FieldChange f : r.fieldChanges()) {
                collect(f.before(), found, uuids, primary, numbers);
                collect(f.after(), found, uuids, primary, numbers);
            }
        }
        if (found.isEmpty()) {
            return kept;
        }
        Set<Long> visibleNumbers = files.unrestrictedFriendlyNumbers(numbers);
        Set<UUID> visibleIds = new HashSet<>(files.unrestrictedFileIds(primary));
        visibleIds.addAll(files.contentsOfOnlyUnrestrictedFiles(primary));
        visibleIds.addAll(history.historyObjectsOtherThan(ArchivalFileRepository.OBJECT_TYPE, primary));
        Set<UUID> hidden = files.restrictedFilesOrTheirContents(uuids);

        List<ChangeRecord> out = new ArrayList<>(kept.size());
        for (ChangeRecord r : kept) {
            List<FieldChange> fields = r.fieldChanges().stream()
                    .filter(f -> shown(f.before(), found, visibleIds, visibleNumbers, hidden)
                            && shown(f.after(), found, visibleIds, visibleNumbers, hidden))
                    .toList();
            String reason = shown(r.reason(), found, visibleIds, visibleNumbers, hidden) ? r.reason() : null;
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
                                Set<UUID> primary, Set<Long> numbers) {
        if (text == null || text.isEmpty() || found.containsKey(text)) {
            return;
        }
        FileIdentifiers.References refs = FileIdentifiers.referencesIn(text);
        if (!refs.isEmpty()) {
            found.put(text, refs);
            uuids.addAll(refs.uuids());
            primary.addAll(refs.primaryUuids());
            numbers.addAll(refs.friendlyNumbers());
        }
    }

    private static boolean shown(String text, Map<String, FileIdentifiers.References> found, Set<UUID> visibleIds,
                                 Set<Long> visibleNumbers, Set<UUID> hidden) {
        FileIdentifiers.References refs = text == null ? null : found.get(text);
        if (refs == null) {
            return true;
        }
        return visibleNumbers.containsAll(refs.friendlyNumbers())
                && visibleIds.containsAll(refs.primaryUuids())
                && refs.uuids().stream().noneMatch(hidden::contains);
    }
}
