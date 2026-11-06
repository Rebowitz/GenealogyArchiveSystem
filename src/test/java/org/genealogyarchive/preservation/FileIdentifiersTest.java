package org.genealogyarchive.preservation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** GAS-P2-R02: the canonical File identifier rules shared by the lookup and Change History authorization. */
class FileIdentifiersTest {

    private static final UUID ID = UUID.fromString("0190a000-0000-7000-8000-00000000000a");

    @ParameterizedTest
    @ValueSource(strings = {"F00001", "f00001", "F1", "f1", "F01", " F00001 ", "F0000000000000000001"})
    void gasP2_R02_everyAcceptedFriendlySpellingResolvesToTheSameNumber(String spelling) {
        assertThat(FileIdentifiers.friendlyNumber(spelling)).hasValue(1);
        assertThat(FileIdentifiers.referencesIn(spelling).friendlyNumbers()).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"P00001", "FF1", "F", "F1abc", "F-1", "F 1", "F99999999999999999999", "", "1"})
    void gasP2_R02_spellingsTheLookupRejectsAreNotFileReferences(String spelling) {
        assertThat(FileIdentifiers.friendlyNumber(spelling)).isEmpty();
        assertThat(FileIdentifiers.referencesIn(spelling).friendlyNumbers()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0190a000-0000-7000-8000-00000000000a", "0190A000-0000-7000-8000-00000000000A",
            "190a000-0-7000-8000-a", "+190a000-0-7000-8000-a"})
    void gasP2_R02_everyUuidSpellingTheWebLayerAcceptsIsRecognised(String spelling) {
        assertThat(FileIdentifiers.uuid(spelling)).as("accepted as a request parameter").hasValue(ID);
        assertThat(FileIdentifiers.referencesIn("see " + spelling + ".").uuids()).containsExactly(ID);
    }

    @Test
    void gasP2_R02_embeddedReferencesAreFound() {
        assertThat(FileIdentifiers.referencesIn("derived from f1, compare (F00002); also F3-F4 and x+f5")
                .friendlyNumbers()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        assertThat(FileIdentifiers.referencesIn("ref-0190A000-0000-7000-8000-00000000000A-end").uuids()).containsExactly(ID);
    }

    @Test
    void gasP2_R02_ordinaryTextIsNotAReference() {
        assertThat(FileIdentifiers.referencesIn("Fred filed 12 deeds in folder F-1990 for Fairfax County, 1st FFA").isEmpty())
                .isTrue();
        assertThat(FileIdentifiers.referencesIn(null).isEmpty()).isTrue();
    }
}
