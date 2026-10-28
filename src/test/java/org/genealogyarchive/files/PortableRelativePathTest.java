package org.genealogyarchive.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PHASE-0: "path/configuration abstractions do not require POSIX-only or Windows-only absolute
 * paths"; BUILD-SPEC s.63A detection of separator, drive-letter and mount-root assumptions;
 * foundation for UP-010 and EX-010. The identical assertions run on Windows and Linux CI.
 */
class PortableRelativePathTest {

    @TempDir
    Path volumeRoot;

    @Test
    void canonicalFormAlwaysUsesForwardSlash() {
        PortableRelativePath p = PortableRelativePath.ofSegments("originals", "01", "92", "0192f0c4.tif");
        assertThat(p.value()).isEqualTo("originals/01/92/0192f0c4.tif");
        assertThat(p.segments()).containsExactly("originals", "01", "92", "0192f0c4.tif");
        assertThat(p.fileName()).isEqualTo("0192f0c4.tif");
        assertThat(p.parent()).contains(PortableRelativePath.of("originals/01/92"));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
            "/srv/archive/file.tif",          // POSIX absolute
            "C:/Archive/file.tif",            // drive letter
            "C:file.tif",                     // drive-relative
            "originals\\01\\file.tif",        // backslash separator
            "\\\\nas\\share\\file.tif",       // UNC
            "originals//file.tif",            // empty segment
            "originals/../secrets",           // traversal
            "./originals",                    // dot segment
            "originals/",                     // trailing separator
            "originals/file.tif:stream",      // NTFS alternate stream / colon
            "originals/a<b",                  // Windows-illegal
            "originals/a|b",
            "originals/a?b",
            "originals/a*b",
            "originals/a\"b",
            "originals/CON",                  // reserved device names, any case, with extension
            "originals/nul.txt",
            "originals/Com1.tif",
            "originals/LPT9",
            "originals/name.",                // trailing dot is stripped by Windows
            "originals/name ",                // trailing space
            " originals/name",                // leading space
            "originals/tab\there",            // control character
            ""
    })
    void rejectsNonPortableForms(String candidate) {
        assertThatThrownBy(() -> PortableRelativePath.of(candidate))
                .isInstanceOf(InvalidPortablePathException.class);
    }

    @Test
    void rejectsNonNfcUnicodeInsteadOfSilentlyRewriting() {
        String decomposed = Normalizer.normalize("Müller", Normalizer.Form.NFD);
        assertThatThrownBy(() -> PortableRelativePath.of("names/" + decomposed))
                .isInstanceOf(InvalidPortablePathException.class)
                .hasMessageContaining("NFC");
        assertThat(PortableRelativePath.of("names/" + Normalizer.normalize("Müller", Normalizer.Form.NFC)).value())
                .isEqualTo("names/Müller");
    }

    @Test
    void rejectsOverlongSegment() {
        assertThatThrownBy(() -> PortableRelativePath.of("a/" + "x".repeat(256)))
                .isInstanceOf(InvalidPortablePathException.class)
                .hasMessageContaining("255");
    }

    @Test
    void resolvesOntoNativeRootUsingHostRules() throws IOException {
        PortableRelativePath p = PortableRelativePath.of("originals/01/92/sample.bin");
        Path nativePath = p.toNative(volumeRoot);

        assertThat(nativePath.startsWith(volumeRoot.toAbsolutePath().normalize())).isTrue();
        Files.createDirectories(nativePath.getParent());
        Files.writeString(nativePath, "bytes");
        assertThat(Files.readString(nativePath)).isEqualTo("bytes");
        // Native separator is used on disk, but never leaks into the portable form.
        assertThat(nativePath.toString()).contains(volumeRoot.getFileSystem().getSeparator());
    }

    @Test
    void roundTripsBetweenNativeAndPortableForms() {
        PortableRelativePath original = PortableRelativePath.of("derivatives/ab/cd/file-01.jpg");
        Path nativePath = original.toNative(volumeRoot);
        assertThat(PortableRelativePath.fromNative(volumeRoot, nativePath)).isEqualTo(original);
    }

    @Test
    void persistedFormIsIndependentOfVolumeRoot(@TempDir Path otherRoot) {
        PortableRelativePath p = PortableRelativePath.of("originals/01/file.tif");
        PortableRelativePath fromFirst = PortableRelativePath.fromNative(volumeRoot, p.toNative(volumeRoot));
        PortableRelativePath fromSecond = PortableRelativePath.fromNative(otherRoot, p.toNative(otherRoot));
        assertThat(fromFirst).isEqualTo(fromSecond);
        assertThat(fromFirst.value()).doesNotContain(volumeRoot.toString()).doesNotContain(otherRoot.toString());
    }

    @Test
    void nativePathOutsideRootIsRejected(@TempDir Path elsewhere) {
        assertThatThrownBy(() -> PortableRelativePath.fromNative(volumeRoot, elsewhere.resolve("x")))
                .isInstanceOf(InvalidPortablePathException.class);
    }

    @Test
    void caseFoldKeyDetectsNamesThatCollideOnCaseInsensitiveFilesystems() {
        PortableRelativePath a = PortableRelativePath.of("originals/Deed-227.tif");
        PortableRelativePath b = PortableRelativePath.of("originals/deed-227.TIF");
        assertThat(a).as("distinct portable paths").isNotEqualTo(b);
        assertThat(a.caseFoldKey()).as("but they would collide on NTFS").isEqualTo(b.caseFoldKey());
    }
}
