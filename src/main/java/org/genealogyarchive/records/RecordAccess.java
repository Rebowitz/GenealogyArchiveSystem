package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileService;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * Visibility rules for Phase 3 (D3-04). A RESTRICTED Archival Record is visible only with
 * {@link Permission#VIEW_RESTRICTED_RECORDS}; for anyone else every route answers exactly as for a Record that
 * does not exist. Restricted Files keep the Phase 2 rule ({@link FileService#canSee}) everywhere a Record,
 * or Capture Session shows Files.
 */
public final class RecordAccess {

    private RecordAccess() {
    }

    public static boolean canSee(Actor actor, ArchivalRecord record) {
        return AccessPolicy.allows(actor, Permission.VIEW_ARCHIVE)
                && (!record.restricted() || AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_RECORDS));
    }

    public static boolean canSee(Actor actor, ArchivalFile file) {
        return FileService.canSee(actor, file);
    }

    public static boolean seesRestrictedRecords(Actor actor) {
        return AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_RECORDS);
    }

    /**
     * The friendly number a typed identifier resolves to for {@code prefix}: surrounding whitespace ignored,
     * letters in any case, ASCII digits with any number of leading zeros - the same rules as File IDs.
     */
    public static OptionalLong friendlyNumber(String text, String prefix) {
        if (text == null) {
            return OptionalLong.empty();
        }
        try {
            FriendlyId.Parsed parsed = FriendlyId.parse(text.strip().toUpperCase(Locale.ROOT));
            return prefix.equals(parsed.prefix()) ? OptionalLong.of(parsed.number()) : OptionalLong.empty();
        } catch (IllegalArgumentException e) {
            return OptionalLong.empty();
        }
    }
}
