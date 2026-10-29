package org.genealogyarchive.identity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** BUILD-SPEC s.8: immutable globally unique identity, UUIDv7, identical text form on every host. */
class GlobalIdsTest {

    @Test
    void newIdsAreRfc9562Version7() {
        UUID id = GlobalIds.newId();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(GlobalIds.isVersion7(id)).isTrue();
        assertThat(GlobalIds.isVersion7(UUID.randomUUID())).as("a v4 UUID is not v7").isFalse();
    }

    @Test
    void canonicalTextFormIsLowerCaseHyphenatedAndRoundTrips() {
        UUID id = GlobalIds.newId();
        String text = id.toString();
        assertThat(text).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(UUID.fromString(text)).isEqualTo(id);
    }

    @Test
    void manyIdsGeneratedWithoutCoordinationAreDistinct() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(GlobalIds.newId());
        }
        assertThat(ids).hasSize(100_000);
    }

    @Test
    void embeddedTimestampIsTheGenerationTimeInMilliseconds() {
        long before = System.currentTimeMillis();
        UUID id = GlobalIds.newId();
        long after = System.currentTimeMillis();
        long millis = id.getMostSignificantBits() >>> 16;
        // The generator may advance the timestamp by 1 ms if its counter overflows within a millisecond.
        assertThat(millis).isBetween(before, after + 1);
    }
}
