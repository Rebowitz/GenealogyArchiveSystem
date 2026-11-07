package org.genealogyarchive.preservation;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers (JDK only). Hashes are held as 64-character lower-case hex in Java. */
public final class Sha256 {

    private Sha256() {
    }

    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    public static String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }

    public static byte[] bytes(String hex) {
        byte[] b = HexFormat.of().parseHex(hex);
        if (b.length != 32) {
            throw new IllegalArgumentException("not a SHA-256 value");
        }
        return b;
    }
}
