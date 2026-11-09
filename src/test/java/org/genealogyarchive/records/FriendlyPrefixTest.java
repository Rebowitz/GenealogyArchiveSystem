package org.genealogyarchive.records;

import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.preservation.FileIdentifiers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D3-07 compatibility check, pinned: the Phase 3 prefixes parse unambiguously under the existing allocator rules,
 * and the history filter's tokenization never reads one prefix as another.
 */
class FriendlyPrefixTest {

    @Test
    void phase3PrefixesParseUnambiguously() {
        assertThat(FriendlyId.parse("RP0001").prefix()).isEqualTo("RP");
        assertThat(FriendlyId.parse("R00001").prefix()).isEqualTo("R");
        assertThat(FriendlyId.parse("CS0001").prefix()).isEqualTo("CS");
        assertThat(FriendlyId.parse("CT00001").prefix()).isEqualTo("CT");
        assertThat(FriendlyId.parse("S00001").prefix()).isEqualTo("S");
        assertThat(new FriendlyId("R", 1842, 5).value()).isEqualTo("R01842");
        assertThat(new FriendlyId("RP", 7, 4).value()).isEqualTo("RP0007");
    }

    @Test
    void lookupsAcceptOnlyTheirOwnPrefix() {
        assertThat(RecordAccess.friendlyNumber(" r01842 ", "R")).hasValue(1842);
        assertThat(RecordAccess.friendlyNumber("RP0001", "R")).isEmpty();
        assertThat(RecordAccess.friendlyNumber("CS0001", "S")).isEmpty();
        assertThat(RecordAccess.friendlyNumber("CT00001", "S")).isEmpty();
        assertThat(RecordAccess.friendlyNumber("F00001", "R")).isEmpty();
    }

    @Test
    void historyTokenizationNeverReadsRpAsR() {
        assertThat(FileIdentifiers.referencesIn("see RP0001 and CS0002", "R").friendlyNumbers()).isEmpty();
        assertThat(FileIdentifiers.referencesIn("see R00003, RP0001", "R").friendlyNumbers()).containsExactly(3L);
    }
}
