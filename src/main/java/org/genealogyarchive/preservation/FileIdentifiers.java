package org.genealogyarchive.preservation;

import org.genealogyarchive.data.ArchivalFileRepository;
import org.genealogyarchive.identity.FriendlyId;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one definition of which spellings identify an Archival File (GAS-P2-R02). Everything that accepts
 * a File identifier - the friendly-ID lookup and UUID request parameters - and everything that must
 * recognise one - Change History authorization - uses these rules, so a reference the application would
 * resolve can never escape the history filter.
 * <ul>
 *   <li>Friendly ID: as accepted by the lookup - surrounding whitespace ignored, letters in any case, any
 *       number of leading zeros ({@code F00042}, {@code f00042}, {@code F42}, {@code f42}).</li>
 *   <li>UUID: as accepted for request parameters ({@code java.util.UUID.fromString} after trimming, the
 *       conversion Spring applies) - any case and the abbreviated forms it accepts.</li>
 * </ul>
 */
public final class FileIdentifiers {

    /** Runs of characters that can belong to an identifier spelling; anything else separates them. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9+-]+");
    private static final int UUID_GROUPS = 5;

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

    /** The UUID a request parameter resolves to, exactly as the web layer converts it. */
    public static Optional<UUID> uuid(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(text.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Identifiers that text refers to, by the rules above. */
    public record References(Set<UUID> uuids, Set<Long> friendlyNumbers) {
        public boolean isEmpty() {
            return uuids.isEmpty() && friendlyNumbers.isEmpty();
        }
    }

    /**
     * Every File identifier spelling embedded in free text: each run of letters, digits, '+' and '-' is
     * tried whole, each of its parts between '+' and '-' is tried as a friendly ID, and each window of
     * five consecutive hyphen-separated parts as a UUID. Ordinary words never resolve, so they are never reported.
     */
    public static References referencesIn(String text) {
        Set<UUID> uuids = new LinkedHashSet<>();
        Set<Long> numbers = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return new References(uuids, numbers);
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String token = m.group();
            friendlyNumber(token).ifPresent(numbers::add);
            uuid(token).ifPresent(uuids::add);
            for (String part : token.split("[+-]")) {
                friendlyNumber(part).ifPresent(numbers::add);
            }
            String[] parts = token.split("-", -1);
            for (int i = 0; i + UUID_GROUPS <= parts.length; i++) {
                uuid(String.join("-", java.util.Arrays.copyOfRange(parts, i, i + UUID_GROUPS))).ifPresent(uuids::add);
            }
        }
        return new References(uuids, numbers);
    }
}
