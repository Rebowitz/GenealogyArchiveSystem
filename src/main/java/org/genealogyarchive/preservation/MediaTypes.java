package org.genealogyarchive.preservation;

/**
 * Informational media type from the first bytes of the content. Deliberately small and identical on
 * every host: the operating system's own type registry differs between Windows and Linux. Never used
 * for identity or for deciding how bytes are preserved; unknown content is
 * {@code application/octet-stream}.
 */
public final class MediaTypes {

    private MediaTypes() {
    }

    public static String sniff(byte[] head) {
        if (starts(head, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (starts(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (starts(head, 'G', 'I', 'F', '8')) {
            return "image/gif";
        }
        if (starts(head, 'I', 'I', 0x2A, 0x00) || starts(head, 'M', 'M', 0x00, 0x2A)) {
            return "image/tiff";
        }
        if (starts(head, 'B', 'M')) {
            return "image/bmp";
        }
        if (starts(head, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        if (head.length >= 12 && starts(head, 'R', 'I', 'F', 'F') && head[8] == 'W' && head[9] == 'E' && head[10] == 'B'
                && head[11] == 'P') {
            return "image/webp";
        }
        if (starts(head, 'P', 'K', 0x03, 0x04)) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    /** True for raster types the JDK can read for thumbnails. */
    public static boolean thumbnailable(String mediaType) {
        return switch (mediaType) {
            case "image/jpeg", "image/png", "image/gif", "image/tiff", "image/bmp" -> true;
            default -> false;
        };
    }

    private static boolean starts(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
