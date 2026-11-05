package org.genealogyarchive.preservation;

import org.genealogyarchive.files.PortableRelativePath;

import java.time.Instant;
import java.util.UUID;

/**
 * Stored bytes, possibly shared by several Files (P2-B). {@code byteSize} and {@code sha256} are the
 * authoritative ingestion measurements; {@code currentPath} is the portable location of the current
 * generation inside {@code volumeId} (null once destroyed).
 */
public record FileContent(UUID contentId, UUID volumeId, long byteSize, String sha256, ContentState state,
                          int currentGeneration, PortableRelativePath currentPath, Instant createdAt,
                          Instant lastVerifiedAt, long version) {
}
