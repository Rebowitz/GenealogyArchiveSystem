package org.genealogyarchive.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GAS-P2-FV03: the application's UUID conversion is the shared identifier definition and accepts exactly
 * what Spring's default conversion accepted, so no previously working URL changes behaviour.
 */
class IdentifierConversionTest {

    private static Optional<UUID> spring(String s) {
        try {
            return Optional.ofNullable(DefaultConversionService.getSharedInstance().convert(s, UUID.class));
        } catch (RuntimeException e) {
            return Optional.of(new UUID(-1, -1)); // marks "refused"
        }
    }

    private static Optional<UUID> ours(String s) {
        try {
            return Optional.ofNullable(IdentifierConversion.STRING_TO_UUID.convert(s));
        } catch (IllegalArgumentException e) {
            return Optional.of(new UUID(-1, -1));
        }
    }

    @Test
    void gasP2_FV03_conversionAgreesWithSpringsDefaultOnEverySpelling() {
        String c = "0190a000-0000-7000-8000-00000000000a";
        StringBuilder arabic = new StringBuilder();
        for (char ch : c.toCharArray()) {
            arabic.append(ch >= '0' && ch <= '9' ? (char) ('٠' + (ch - '0')) : ch);
        }
        List<String> corpus = List.of(c, c.toUpperCase(), " " + c + " ", "190a000-0-7000-8000-a", "+190a000-0-7000-8000-a",
                "ab0190a000-0-7000-8000-a", arabic.toString(), c.replace('a', 'ａ'), c.replace('a', 'а'),
                c.replace('-', '‐'), c + "0", "", "   ", " " + c, "not-a-uuid", "1-2-3-4", "1-2-3-4-5-6");
        for (String s : corpus) {
            assertThat(ours(s)).as("'%s'", s).isEqualTo(spring(s));
        }
    }
}
