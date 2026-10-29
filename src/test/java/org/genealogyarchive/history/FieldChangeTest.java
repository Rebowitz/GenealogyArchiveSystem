package org.genealogyarchive.history;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BUILD-SPEC s.9 mutability classes as enforced by the history mechanism. */
class FieldChangeTest {

    @Test
    void immutableFieldMayBeSetOnceButNeverChanged() {
        assertThat(FieldChange.set("archive_id", FieldClass.IMMUTABLE, "a").changed()).isTrue();
        assertThat(FieldChange.of("archive_id", FieldClass.IMMUTABLE, "a", "a").changed()).isFalse();
        assertThatThrownBy(() -> FieldChange.of("archive_id", FieldClass.IMMUTABLE, "a", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Immutable");
    }

    @Test
    void correctableAndInterpretiveFieldsMayChange() {
        assertThat(FieldChange.of("archive_name", FieldClass.CORRECTABLE, "a", "b").changed()).isTrue();
        assertThat(FieldChange.of("conclusion", FieldClass.INTERPRETIVE, "a", "b").changed()).isTrue();
    }

    @Test
    void fieldNamesAreValidated() {
        assertThatThrownBy(() -> FieldChange.set("Archive Name", FieldClass.CORRECTABLE, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jsonRenderingEscapesUserText() {
        String json = JsonText.fieldChanges(List.of(
                FieldChange.of("archive_name", FieldClass.CORRECTABLE, "Smith \"Family\"\\Archive", "line1\nline2\u0001")));
        assertThat(json).isEqualTo("[{\"field\":\"archive_name\",\"class\":\"CORRECTABLE\","
                + "\"before\":\"Smith \\\"Family\\\"\\\\Archive\",\"after\":\"line1\\nline2\\u0001\"}]");
        assertThat(JsonText.fieldChanges(List.of(FieldChange.set("description", FieldClass.CORRECTABLE, null))))
                .contains("\"before\":null,\"after\":null");
    }
}
