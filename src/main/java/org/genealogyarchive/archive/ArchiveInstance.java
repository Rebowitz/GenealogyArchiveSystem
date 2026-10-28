package org.genealogyarchive.archive;

import java.time.Instant;
import java.util.UUID;

/**
 * The Archive Instance (BUILD-SPEC s.7).
 *
 * @param archiveId   persistent, globally unique, immutable Archive ID (UUIDv7). Not derived from any
 *                    host, path, database or repository fact; travels with backups of this archive.
 * @param archiveName archive-level preference: the display name (correctable)
 * @param createdAt   when the archive was initialized (immutable)
 * @param version     optimistic-concurrency version
 */
public record ArchiveInstance(UUID archiveId, String archiveName, Instant createdAt, long version) {
}
