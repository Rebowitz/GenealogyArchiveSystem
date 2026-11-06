package org.genealogyarchive.preservation;

import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.identity.FriendlyId;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * The one definition of which spellings identify an Archival File (GAS-P2-R02, GAS-P2-FV03). The
 * friendly-ID lookup, every UUID request parameter (the web layer's String-to-UUID conversion is this
 * class, see {@code IdentifierConversion}) and Change History authorization all use these rules, so a
 * spelling the application resolves is always recognised as a reference.
 * <ul>
 *   <li><b>Friendly ID</b> - unchanged from the lookup's earlier behaviour: surrounding whitespace ignored,
 *       letters in any case, ASCII digits with any number of leading zeros ({@code F00042}, {@code f42}).
 *       Other scripts' digits were never accepted and still are not.</li>
 *   <li><b>UUID</b> - unchanged from Spring's earlier conversion ({@code UUID.fromString} after trimming):
 *       any case, abbreviated groups, a leading {@code +} on a group, over-long groups (the JDK keeps the
 *       low-order digits), and any Unicode decimal digit or full-width Latin letter that
 *       {@link Character#digit(char, int)} accepts. Narrowing this would change which URLs resolve, so it
 *       is kept; reference detection instead covers all of it (FV03).</li>
 * </ul>
 */
public final class FileIdentifiers {

    private static final int UUID_GROUPS = 5;
    /** {@code UUID.fromString} refuses longer input. */
    private static final int UUID_MAX_LENGTH = 36;

    private FileIdentifiers() {
    }

    /** The File friendly number a typed identifier resolves to, exactly as the lookup resolves it. */
    public static OptionalLong friendlyNumber(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        try {
            FriendlyId.Parsed parsed = FriendlyId.parse(text.strip().toUpperCase(Locale.ROOT));
            return ArchivalFileRepository.PREFIX.equals(parsed.prefix()) ? OptionalLong.of(parsed.number()) : OptionalLong.empty();
        } catch (IllegalArgumentException e) {
            return OptionalLong.empty();
        }
    }

    /** The UUID a request parameter resolves to; the web layer converts with exactly this. */
    public static Optional<UUID> uuid(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim();
        if (t.isEmpty() || t.length() > UUID_MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(t));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Identifiers that text refers to, by the rules above.
     *
     * @param uuids           every UUID any substring of the text resolves to (complete: used to withhold
     *                        any text that contains a hidden File's identifier in any spelling)
     * @param primaryUuids    UUIDs written as a whole, delimited identifier - a maximal run of identifier
     *                        characters that resolves as it stands (what a reader takes to be "the" UUID)
     * @param friendlyNumbers friendly numbers written as a whole, delimited identifier
     */
    public record References(Set<UUID> uuids, Set<UUID> primaryUuids, Set<Long> friendlyNumbers) {
        public boolean isEmpty() {
            return uuids.isEmpty() && friendlyNumbers.isEmpty();
        }
    }

    /**
     * Every File identifier spelling contained in text.
     * <ul>
     *   <li>Friendly IDs: each maximal run of letters and digits (any script) that the lookup resolves.</li>
     *   <li>UUIDs: every substring the UUID conversion resolves. Such a substring consists only of
     *       characters {@link Character#digit(char, int)} accepts in base 16, {@code +} and {@code -}, so
     *       each maximal run of those is examined: every five consecutive hyphen-separated groups, with
     *       every suffix of the first group and every prefix of the last (groups in between are whole).
     *       A run that resolves as a whole is also a primary reference.</li>
     * </ul>
     * Text that resolves to nothing yields no references.
     */
    public static References referencesIn(String text) {
        Set<UUID> uuids = new LinkedHashSet<>();
        Set<UUID> primary = new LinkedHashSet<>();
        Set<Long> numbers = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return new References(uuids, primary, numbers);
        }
        int i = 0;
        while (i < text.length()) {
            if (!Character.isLetterOrDigit(text.charAt(i))) {
                i++;
                continue;
            }
            int j = i;
            while (j < text.length() && Character.isLetterOrDigit(text.charAt(j))) {
                j++;
            }
            friendlyNumber(text.substring(i, j)).ifPresent(numbers::add);
            i = j;
        }
        i = 0;
        while (i < text.length()) {
            if (!uuidChar(text.charAt(i))) {
                i++;
                continue;
            }
            int j = i;
            while (j < text.length() && uuidChar(text.charAt(j))) {
                j++;
            }
            String run = text.substring(i, j);
            uuid(run).ifPresent(primary::add);
            uuidsIn(run, uuids);
            i = j;
        }
        uuids.addAll(primary);
        return new References(uuids, primary, numbers);
    }

    private static boolean uuidChar(char c) {
        return c == '-' || c == '+' || Character.digit(c, 16) >= 0;
    }

    private static void uuidsIn(String run, Set<UUID> out) {
        String[] groups = run.split("-", -1);
        for (int g = 0; g + UUID_GROUPS <= groups.length; g++) {
            String first = groups[g];
            String last = groups[g + UUID_GROUPS - 1];
            String middle = String.join("-", groups[g + 1], groups[g + 2], groups[g + 3]);
            if (middle.length() + 4 > UUID_MAX_LENGTH) {
                continue;
            }
            // Only the last 36 characters of the first group and the first 36 of the last can fit.
            for (int s = Math.max(0, first.length() - UUID_MAX_LENGTH); s < first.length(); s++) {
                String head = first.substring(s);
                for (int p = Math.min(last.length(), UUID_MAX_LENGTH); p > 0; p--) {
                    int length = head.length() + middle.length() + p + 2;
                    if (length > UUID_MAX_LENGTH) {
                        continue;
                    }
                    uuid(head + "-" + middle + "-" + last.substring(0, p)).ifPresent(out::add);
                }
            }
        }
    }
}
