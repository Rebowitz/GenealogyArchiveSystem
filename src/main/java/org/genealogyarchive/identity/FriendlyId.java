package org.genealogyarchive.identity;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Archive-local, human-friendly identifier such as {@code P00127} or {@code PR0041}
 * (BUILD-SPEC s.8). A presentation and researcher identifier only: it is never the underlying
 * object identity, which is always the object's global UUIDv7. Numbers come from a PostgreSQL
 * sequence per object type and are never reused; gaps are valid and are never renumbered.
 *
 * @param prefix    upper-case letters registered for the object type (1-4 letters)
 * @param number    sequence number, at least 1
 * @param minDigits minimum zero-padded width; larger numbers simply use more digits
 */
public record FriendlyId(String prefix, long number, int minDigits) {

    private static final Pattern PREFIX = Pattern.compile("[A-Z]{1,4}");
    private static final Pattern TEXT = Pattern.compile("([A-Z]{1,4})([0-9]{1,19})");

    public FriendlyId {
        Objects.requireNonNull(prefix, "prefix");
        if (!PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Friendly ID prefix must be 1-4 upper-case letters");
        }
        if (number < 1) {
            throw new IllegalArgumentException("Friendly ID number must be positive");
        }
        if (minDigits < 1 || minDigits > 9) {
            throw new IllegalArgumentException("Friendly ID width must be 1-9 digits");
        }
    }

    /** Display form, e.g. {@code P00127}; {@code P100000} once numbers exceed the minimum width. */
    public String value() {
        String digits = Long.toString(number);
        return prefix + "0".repeat(Math.max(0, minDigits - digits.length())) + digits;
    }

    /** Splits a displayed friendly ID into prefix and number (the width is not recoverable). */
    public static Parsed parse(String text) {
        Matcher m = TEXT.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a friendly identifier");
        }
        return new Parsed(m.group(1), Long.parseLong(m.group(2)));
    }

    /** Prefix and number of a displayed friendly ID. */
    public record Parsed(String prefix, long number) {
    }

    @Override
    public String toString() {
        return value();
    }
}
