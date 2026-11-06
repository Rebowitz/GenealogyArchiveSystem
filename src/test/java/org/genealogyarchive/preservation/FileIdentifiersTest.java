package org.genealogyarchive.preservation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    // ---- GAS-P2-FV03: Unicode digits, full-width letters, mixed scripts, glued text ---------------------

    /** Replaces ASCII digits with the same digits of another script (e.g. U+0660 Arabic-Indic zero). */
    private static String digitsOf(String text, char zero) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? (char) (zero + (c - '0')) : c);
        }
        return sb.toString();
    }

    /** Replaces ASCII letters a-f with their full-width forms (U+FF41 ...), which the JDK accepts as hex. */
    private static String fullWidthHex(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c >= 'a' && c <= 'f' ? (char) ('ａ' + (c - 'a')) : c);
        }
        return sb.toString();
    }

    @Test
    void gasP2_FV03_unicodeDigitAndFullWidthSpellingsTheWebLayerAcceptsAreRecognised() {
        String canonical = ID.toString();
        List<String> spellings = List.of(
                digitsOf(canonical, '٠'),                 // Arabic-Indic
                digitsOf(canonical, '۰'),                 // Extended Arabic-Indic
                digitsOf(canonical, '०'),                 // Devanagari
                digitsOf(canonical, '０'),                 // full-width digits
                fullWidthHex(canonical),                       // full-width hex letters
                digitsOf(fullWidthHex(canonical), '൦'),   // Malayalam digits + full-width letters
                digitsOf(canonical.substring(0, 18), '٠') + canonical.substring(18)); // mixed scripts
        for (String spelling : spellings) {
            assertThat(java.util.UUID.fromString(spelling)).as("the JDK accepts %s", spelling).isEqualTo(ID);
            assertThat(FileIdentifiers.uuid(spelling)).as(spelling).hasValue(ID);
            assertThat(FileIdentifiers.referencesIn("note: " + spelling + ", end").uuids()).as(spelling).containsExactly(ID);
            assertThat(FileIdentifiers.referencesIn("x" + spelling + "y").uuids()).as("glued %s", spelling).containsExactly(ID);
        }
    }

    @Test
    void gasP2_FV03_lookAlikeLettersFromOtherScriptsResolveToNothingAndAreNotReferences() {
        String canonical = ID.toString();
        for (String spelling : List.of(canonical.replace('a', 'а'),   // Cyrillic a
                canonical.replace('0', 'Ο'),                           // Greek capital omicron
                canonical.replace('-', '‐'))) {                        // Unicode hyphen
            assertThat(FileIdentifiers.uuid(spelling)).as(spelling).isEmpty();
            assertThat(FileIdentifiers.referencesIn(spelling).uuids()).as(spelling).doesNotContain(ID);
        }
    }

    @Test
    void gasP2_FV03_friendlyIdsStayAsciiAsTheLookupHasAlwaysRequired() {
        for (String spelling : List.of(digitsOf("F1", '٠'), "Ｆ" + "1", "F" + digitsOf("00001", '０'))) {
            assertThat(FileIdentifiers.friendlyNumber(spelling)).as(spelling).isEmpty();
            assertThat(FileIdentifiers.referencesIn(spelling).friendlyNumbers()).as(spelling).isEmpty();
        }
    }

    @Test
    void gasP2_FV03_overlongAndGluedGroupsResolveAsTheJdkResolvesThem() {
        // The JDK keeps the low-order digits of an over-long group, so extra leading digits are ignored.
        assertThat(FileIdentifiers.uuid("ab0190a000-0-7000-8000-a")).hasValue(ID);
        assertThat(FileIdentifiers.referencesIn("ab0190a000-0-7000-8000-a").uuids()).containsExactly(ID);
        // IV02: glued trailing hex is read the way the JDK reads an over-long last group (its low-order
        // digits), so the text refers to that UUID - a reference like any other, never silently ignored.
        assertThat(FileIdentifiers.referencesIn(ID + "ff").uuids())
                .containsExactly(UUID.fromString("0190a000-0000-7000-8000-000000000aff"));
    }

    @Test
    void gasP2_IV02_windowsSharingAGroupWithACanonicalUuidAreNotReadSeparately() {
        assertThat(FileIdentifiers.referencesIn("deed-" + ID + "-copy").uuids()).containsExactly(ID);
        assertThat(FileIdentifiers.referencesIn(ID + "-" + ID).uuids()).containsExactly(ID);
        UUID other = UUID.fromString("0190a000-0000-7000-8000-00000000000b");
        assertThat(FileIdentifiers.referencesIn(ID + "-1-2-3-4-" + other).uuids()).containsExactly(ID, other);
    }

    @Test
    void gasP2_IV02_whatTextRefersToDependsOnTheTextAlone() {
        // Texts that differ only in which UUID they name are read alike: same number of references, and the
        // named UUID itself whenever it is written delimited.
        UUID a = UUID.fromString("0190a000-0000-7000-8000-0000000000a1");
        UUID b = UUID.fromString("0190a000-0000-7000-8000-0000000000b2");
        for (String template : List.of("%s", "see %s.", "ref-%s-end", "xx%sff", "(%s)", "copy of %s and F7", "deed-%s")) {
            var ra = FileIdentifiers.referencesIn(template.formatted(a));
            var rb = FileIdentifiers.referencesIn(template.formatted(b));
            assertThat(ra.uuids()).as(template).hasSameSizeAs(rb.uuids()).isNotEmpty();
            assertThat(ra.friendlyNumbers()).isEqualTo(rb.friendlyNumbers());
        }
    }

    /**
     * Independent check (IV02, FV03): for generated text mixing UUID spellings, scripts and junk, every text
     * that contains any substring the UUID conversion resolves yields at least one UUID reference, so the
     * allow-list decides it; and a resolvable substring delimited on both sides is read as exactly itself.
     */
    @Test
    void gasP2_IV02_everyTextContainingAResolvableSpellingIsGovernedByTheAllowList() {
        java.util.Random random = new java.util.Random(20261106L);
        String palette = "0123456789abcdefABCDEF-+ xzF.٠٥٩０ａＦа‐";
        for (int round = 0; round < 400; round++) {
            StringBuilder text = new StringBuilder();
            while (text.length() < 70) {
                switch (random.nextInt(4)) {
                    case 0 -> text.append(UUID.randomUUID());
                    case 1 -> text.append(digitsOf(UUID.randomUUID().toString(), random.nextBoolean() ? '٠' : '０'));
                    case 2 -> text.append(Integer.toHexString(random.nextInt(0x10000))).append('-');
                    default -> text.append(palette.charAt(random.nextInt(palette.length())));
                }
            }
            String t = text.toString();
            Set<UUID> found = FileIdentifiers.referencesIn(t).uuids();
            for (int i = 0; i < t.length(); i++) {
                for (int j = i + 1; j <= Math.min(t.length(), i + 36); j++) {
                    Optional<UUID> resolved;
                    try {
                        resolved = Optional.of(UUID.fromString(t.substring(i, j)));
                    } catch (IllegalArgumentException e) {
                        resolved = Optional.empty();
                    }
                    if (resolved.isEmpty()) {
                        continue;
                    }
                    assertThat(found).as("'%s' resolves inside '%s'", t.substring(i, j), t).isNotEmpty();
                    boolean delimited = (i == 0 || !isUuidChar(t.charAt(i - 1)))
                            && (j == t.length() || !isUuidChar(t.charAt(j)));
                    if (delimited) {
                        assertThat(found).as("delimited '%s' in '%s'", t.substring(i, j), t).contains(resolved.get());
                    }
                }
            }
        }
    }

    private static boolean isUuidChar(char c) {
        return c == '-' || c == '+' || Character.digit(c, 16) >= 0;
    }
}
