package org.genealogyarchive.preservation;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.HistoryVisibility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps restricted Archival Files out of Change History for readers who may not see them (P2-H, B2,
 * GAS-P2-003). Applied at read time, so it follows the current restriction and also covers entries
 * written before a File was restricted:
 * <ul>
 *   <li>entries about a restricted File are removed;</li>
 *   <li>in the remaining entries, a field whose value names a restricted File (its UUID or friendly ID)
 *       or content a restricted File uses is withheld, and so is a reason that names one.</li>
 * </ul>
 * A withheld field or reason is simply absent, exactly as if it had never been recorded, so nothing
 * distinguishes a hidden reference from no reference.
 */
public final class RestrictedFileHistory implements HistoryVisibility {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern FRIENDLY = Pattern.compile("\\bF(\\d{5,})\\b");

    private final ArchivalFileRepository files;

    public RestrictedFileHistory(ArchivalFileRepository files) {
        this.files = files;
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
        Map<UUID, Long> restricted = files.restrictedFiles();
        if (restricted.isEmpty()) {
            return records;
        }
        Set<UUID> hiddenIds = new HashSet<>(restricted.keySet());
        hiddenIds.addAll(files.contentsOfRestrictedFiles());
        Set<Long> hiddenNumbers = new HashSet<>(restricted.values());
        List<ChangeRecord> out = new ArrayList<>(records.size());
        for (ChangeRecord r : records) {
            if (ArchivalFileRepository.OBJECT_TYPE.equals(r.objectType()) && restricted.containsKey(r.objectId())) {
                continue;
            }
            List<FieldChange> fields = r.fieldChanges().stream()
                    .filter(f -> !refersToHidden(f.before(), hiddenIds, hiddenNumbers)
                            && !refersToHidden(f.after(), hiddenIds, hiddenNumbers))
                    .toList();
            String reason = refersToHidden(r.reason(), hiddenIds, hiddenNumbers) ? null : r.reason();
            if (fields.size() == r.fieldChanges().size() && java.util.Objects.equals(reason, r.reason())) {
                out.add(r);
            } else {
                out.add(new ChangeRecord(r.changeId(), r.occurredAt(), r.actorKind(), r.actorUserId(), r.actorLabel(),
                        r.objectType(), r.objectId(), r.changeKind(), fields, reason));
            }
        }
        return out;
    }

    static boolean refersToHidden(String text, Set<UUID> hiddenIds, Set<Long> hiddenNumbers) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Matcher m = UUID_TEXT.matcher(text);
        while (m.find()) {
            if (hiddenIds.contains(UUID.fromString(m.group().toLowerCase(Locale.ROOT)))) {
                return true;
            }
        }
        Matcher f = FRIENDLY.matcher(text);
        while (f.find()) {
            try {
                if (hiddenNumbers.contains(Long.parseLong(f.group(1)))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // longer than a long: cannot be a friendly number
            }
        }
        return false;
    }
}
