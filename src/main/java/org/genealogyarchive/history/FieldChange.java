package org.genealogyarchive.history;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One field's change within a Change History entry. Values are plain text renderings for people;
 * secrets (passwords, hashes) are never recorded as field values.
 *
 * @param field      field name, lower_snake_case
 * @param fieldClass mutability class
 * @param before     value before the change, or null when set for the first time
 * @param after      value after the change, or null when cleared
 */
public record FieldChange(String field, FieldClass fieldClass, String before, String after) {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public FieldChange {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(fieldClass, "fieldClass");
        if (!NAME.matcher(field).matches()) {
            throw new IllegalArgumentException("Field names are lower_snake_case");
        }
        if (fieldClass == FieldClass.IMMUTABLE && before != null && !before.equals(after)) {
            throw new IllegalArgumentException("Immutable field '" + field + "' cannot be changed once set");
        }
    }

    public static FieldChange set(String field, FieldClass fieldClass, Object value) {
        return new FieldChange(field, fieldClass, null, value == null ? null : value.toString());
    }

    public static FieldChange of(String field, FieldClass fieldClass, Object before, Object after) {
        return new FieldChange(field, fieldClass, before == null ? null : before.toString(),
                after == null ? null : after.toString());
    }

    public boolean changed() {
        return !Objects.equals(before, after);
    }
}
