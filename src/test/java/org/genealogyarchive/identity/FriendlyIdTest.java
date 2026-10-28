package org.genealogyarchive.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Friendly IDs are presentation identifiers with stable formatting (BUILD-SPEC s.8). */
class FriendlyIdTest {

    @Test
    void formatsWithZeroPaddingToMinimumWidth() {
        assertThat(new FriendlyId("P", 127, 5).value()).isEqualTo("P00127");
        assertThat(new FriendlyId("F", 3711, 5).value()).isEqualTo("F03711");
        assertThat(new FriendlyId("PR", 41, 4).value()).isEqualTo("PR0041");
    }

    @Test
    void numbersBeyondTheMinimumWidthGrowRatherThanWrap() {
        assertThat(new FriendlyId("P", 100_000, 5).value()).isEqualTo("P100000");
    }

    @Test
    void parsesDisplayedForm() {
        FriendlyId.Parsed parsed = FriendlyId.parse("PR0041");
        assertThat(parsed.prefix()).isEqualTo("PR");
        assertThat(parsed.number()).isEqualTo(41);
        assertThatThrownBy(() -> FriendlyId.parse("p00127")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FriendlyId.parse("P-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidParts() {
        assertThatThrownBy(() -> new FriendlyId("p", 1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FriendlyId("ABCDE", 1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FriendlyId("P", 0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FriendlyId("P", 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
