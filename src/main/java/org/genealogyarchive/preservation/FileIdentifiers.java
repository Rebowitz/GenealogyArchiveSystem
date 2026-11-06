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
     * @param uuids           the UUIDs the text's identifier-shaped groups are read as (see {@link #referencesIn})
     * @param friendlyNumbers friendly numbers written as a whole, delimited identifier
     */
    public record References(Set<UUID> uuids, Set<Long> friendlyNumbers) {
        public boolean isEmpty() {
            return uuids.isEmpty() && friendlyNumbers.isEmpty();
        }
    }

    private static final int[] GROUP_DIGITS = {8, 4, 4, 4, 12};

    /**
     * Every File identifier the text refers to, read from the text alone (GAS-P2-IV02: nothing here depends
     * on which Files exist, so callers can decide visibility without revealing existence).
     * <ul>
     *   <li>Friendly IDs: each maximal run of letters and digits (any script) that the lookup resolves.</li>
     *   <li>UUIDs: in each maximal run of characters a UUID can contain ({@link Character#digit(char, int)}
     *       hex digits of any script, {@code +}, {@code -}), every five consecutive hyphen-separated groups
     *       form a window. A window with the canonical 8-4-4-4-12 shape is always read; any other window is
     *       read unless it shares a group with a canonical one. A window is read as the JDK reads it: the
     *       hex digits ending the first group and starting the last group, each group keeping its low-order
     *       digits. A delimited spelling the web layer accepts is therefore read as exactly the UUID it
     *       resolves to, and any text containing a resolvable spelling yields at least one reference.</li>
     * </ul>
     */
    public static References referencesIn(String text) {
        Set<UUID> uuids = new LinkedHashSet<>();
        Set<Long> numbers = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return new References(uuids, numbers);
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
            uuidsIn(text.substring(i, j), uuids);
            i = j;
        }
        return new References(uuids, numbers);
    }

    private static boolean uuidChar(char c) {
        return c == '-' || c == '+' || Character.digit(c, 16) >= 0;
    }

    private static void uuidsIn(String run, Set<UUID> out) {
        String[] groups = run.split("-", -1);
        int windows = groups.length - GROUP_DIGITS.length + 1;
        if (windows <= 0) {
            return;
        }
        UUID[] read = new UUID[windows];
        boolean[] canonical = new boolean[windows];
        boolean[] coveredByCanonical = new boolean[groups.length];
        for (int w = 0; w < windows; w++) {
            read[w] = window(groups, w);
            canonical[w] = read[w] != null && canonicalShape(groups, w);
            if (canonical[w]) {
                for (int g = w; g < w + GROUP_DIGITS.length; g++) {
                    coveredByCanonical[g] = true;
                }
            }
        }
        for (int w = 0; w < windows; w++) {
            if (read[w] == null) {
                continue;
            }
            boolean overlaps = false;
            for (int g = w; g < w + GROUP_DIGITS.length && !canonical[w]; g++) {
                overlaps |= coveredByCanonical[g];
            }
            if (canonical[w] || !overlaps) {
                out.add(read[w]);
            }
        }
    }

    private static boolean canonicalShape(String[] groups, int w) {
        for (int k = 0; k < GROUP_DIGITS.length; k++) {
            if (groups[w + k].length() != GROUP_DIGITS[k]) {
                return false;
            }
        }
        return true;
    }

    /** The UUID a window reads as, or null if no substring spanning its groups resolves. */
    private static UUID window(String[] groups, int w) {
        StringBuilder canonical = new StringBuilder(36);
        for (int k = 0; k < GROUP_DIGITS.length; k++) {
            String group = groups[w + k];
            String digits;
            if (k == 0) {
                int start = group.length();
                while (start > 0 && Character.digit(group.charAt(start - 1), 16) >= 0) {
                    start--;
                }
                digits = group.substring(start);
            } else if (k == GROUP_DIGITS.length - 1) {
                int from = group.startsWith("+") ? 1 : 0;
                int end = from;
                while (end < group.length() && Character.digit(group.charAt(end), 16) >= 0) {
                    end++;
                }
                digits = group.substring(from, end);
            } else {
                String body = group.startsWith("+") ? group.substring(1) : group;
                for (int c = 0; c < body.length(); c++) {
                    if (Character.digit(body.charAt(c), 16) < 0) {
                        return null;
                    }
                }
                digits = body;
            }
            if (digits.isEmpty()) {
                return null;
            }
            int keep = GROUP_DIGITS[k];
            String low = digits.length() > keep ? digits.substring(digits.length() - keep) : digits;
            StringBuilder hex = new StringBuilder(keep);
            for (int c = 0; c < low.length(); c++) {
                hex.append(Character.forDigit(Character.digit(low.charAt(c), 16), 16));
            }
            while (hex.length() < keep) {
                hex.insert(0, '0');
            }
            if (k > 0) {
                canonical.append('-');
            }
            canonical.append(hex);
        }
        return UUID.fromString(canonical.toString());
    }
}
