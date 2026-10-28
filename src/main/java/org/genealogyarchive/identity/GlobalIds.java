package org.genealogyarchive.identity;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * Immutable globally unique machine identity for durable archive objects (BUILD-SPEC s.8).
 * <p>
 * UUIDv7 (RFC 9562): 48-bit Unix-millisecond timestamp plus random bits, generated in the
 * application on any host without a coordinating service. The canonical text form
 * ({@link UUID#toString()}) is lower-case hexadecimal and identical on Windows and Linux. A global
 * ID never encodes a host name, path, database name or other machine fact.
 */
public final class GlobalIds {

    private GlobalIds() {
    }

    /** A new UUIDv7. */
    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /** True if {@code id} is an RFC 9562 version 7 UUID. */
    public static boolean isVersion7(UUID id) {
        return id != null && id.version() == 7 && id.variant() == 2;
    }
}
