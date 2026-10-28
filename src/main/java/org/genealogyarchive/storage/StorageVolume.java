package org.genealogyarchive.storage;

import java.time.Instant;
import java.util.UUID;

/**
 * A Storage Volume (BUILD-SPEC s.28): archive-level identity and description of a filesystem-backed
 * storage location. Its native root directory is installation configuration ({@code storageRoots}
 * keyed by {@link #volumeId}), never part of this record.
 *
 * @param volumeId    immutable global ID; the key used in installation configuration
 * @param name        descriptive name, unique among active volumes
 * @param purpose     open descriptive role code such as {@code primary}, {@code backup} or
 *                    {@code removable-backup} (lower-case letters, digits, '-')
 * @param description optional free text
 * @param required    whether normal archival operation needs this volume to be available
 * @param createdAt   registration time
 * @param retiredAt   when retired (never deleted), or null while active
 * @param version     optimistic-concurrency version
 */
public record StorageVolume(UUID volumeId, String name, String purpose, String description, boolean required,
                            Instant createdAt, Instant retiredAt, long version) {

    public boolean active() {
        return retiredAt == null;
    }
}
