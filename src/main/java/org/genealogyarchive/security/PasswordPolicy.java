package org.genealogyarchive.security;

import org.genealogyarchive.service.RuleViolationException;

import java.nio.charset.StandardCharsets;

/**
 * Minimum password rules. bcrypt only uses the first 72 bytes, so longer passwords are rejected
 * rather than silently truncated.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String username, String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new RuleViolationException("Passwords must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new RuleViolationException("Passwords must be at most " + MAX_UTF8_BYTES + " bytes (about 72 ordinary characters).");
        }
        if (password.isBlank() || (username != null && password.equalsIgnoreCase(username))) {
            throw new RuleViolationException("The password must not be blank or the same as the username.");
        }
    }
}
