package org.genealogyarchive.records;

import org.genealogyarchive.service.RuleViolationException;

/**
 * Input normalization for researcher-entered text. Blank means "not known" (stored as absent, never as a
 * placeholder, A-003). Limits match the database constraints.
 */
final class Texts {

    private Texts() {
    }

    /** Stripped text, or null when blank. Line breaks and tabs are allowed only where {@code multiline}. */
    static String optional(String value, String what, int max, boolean multiline) {
        if (value == null) {
            return null;
        }
        String s = value.strip();
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > max) {
            throw new RuleViolationException(what + " can be at most " + max + " characters.");
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean lineBreak = c == '\n' || c == '\r' || c == '\t';
            if ((c < 0x20 && !(multiline && lineBreak)) || c == 0x7F) {
                throw new RuleViolationException(what + " contains a control character.");
            }
        }
        return s;
    }

    static String optional(String value, String what, int max) {
        return optional(value, what, max, false);
    }

    static String required(String value, String what, int max) {
        String s = optional(value, what, max, false);
        if (s == null) {
            throw new RuleViolationException(what + " is needed.");
        }
        return s;
    }
}
