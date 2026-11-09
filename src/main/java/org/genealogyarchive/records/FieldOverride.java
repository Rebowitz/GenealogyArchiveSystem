package org.genealogyarchive.records;

/**
 * One field's override of its Capture Session's context (plan section 10.2): a mode, and a value present exactly
 * in VALUE mode.
 */
public record FieldOverride<T>(OverrideMode mode, T value) {

    public FieldOverride {
        if (mode == null || (mode == OverrideMode.VALUE) != (value != null)) {
            throw new IllegalArgumentException("An override has a value exactly when its mode is VALUE");
        }
    }

    public static <T> FieldOverride<T> inherit() {
        return new FieldOverride<>(OverrideMode.INHERIT, null);
    }

    public static <T> FieldOverride<T> blank() {
        return new FieldOverride<>(OverrideMode.BLANK, null);
    }

    public static <T> FieldOverride<T> of(T value) {
        return new FieldOverride<>(OverrideMode.VALUE, value);
    }
}
