package org.genealogyarchive.preservation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Media types are informational, identical on every host, and never derived from filenames. */
class MediaTypesTest {

    private static byte[] b(int... v) {
        byte[] r = new byte[v.length];
        for (int i = 0; i < v.length; i++) {
            r[i] = (byte) v[i];
        }
        return r;
    }

    @Test
    void recognisesCommonArchivalFormatsByContent() {
        assertThat(MediaTypes.sniff(b(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("image/jpeg");
        assertThat(MediaTypes.sniff(b(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo("image/png");
        assertThat(MediaTypes.sniff(b('I', 'I', 0x2A, 0x00))).isEqualTo("image/tiff");
        assertThat(MediaTypes.sniff(b('M', 'M', 0x00, 0x2A))).isEqualTo("image/tiff");
        assertThat(MediaTypes.sniff(b('%', 'P', 'D', 'F', '-', '1'))).isEqualTo("application/pdf");
    }

    @Test
    void unknownOrShortContentIsOctetStream() {
        assertThat(MediaTypes.sniff(new byte[0])).isEqualTo("application/octet-stream");
        assertThat(MediaTypes.sniff(b(0xFF, 0xD8))).isEqualTo("application/octet-stream");
        assertThat(MediaTypes.sniff("plain text".getBytes())).isEqualTo("application/octet-stream");
    }

    @Test
    void onlyJdkReadableRasterTypesGetThumbnails() {
        assertThat(MediaTypes.thumbnailable("image/jpeg")).isTrue();
        assertThat(MediaTypes.thumbnailable("application/pdf")).isFalse();
        assertThat(MediaTypes.thumbnailable("image/webp")).isFalse();
    }
}
