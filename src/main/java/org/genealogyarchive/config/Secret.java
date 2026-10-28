package org.genealogyarchive.config;

import java.util.Objects;

/**
 * A secret value (for example the database password) that must never appear in logs,
 * exception messages, or diagnostic output. {@link #toString()} is always masked;
 * callers must call {@link #reveal()} deliberately at the single point of use.
 */
public final class Secret {

    private static final String MASK = "******";

    private final String value;

    private Secret(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static Secret of(String value) {
        return new Secret(value);
    }

    /** Returns the raw secret. Use only when handing it to the component that needs it. */
    public String reveal() {
        return value;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public String toString() {
        return MASK;
    }

    // Deliberately identity-based equals/hashCode: secrets are not compared or used as keys.
}
