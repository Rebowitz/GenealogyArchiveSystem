package org.genealogyarchive.preservation;

import java.time.Instant;
import java.util.UUID;

/**
 * An archival File (BUILD-SPEC s.26): permanent identity, immutable capture provenance and a link to
 * its stored content. Physical location is a property of the content, never of the File's identity.
 *
 * @param friendlyId          display identifier such as {@code F00042}
 * @param originalFilename    filename as captured (immutable provenance)
 * @param managedFilename     correctable display/export filename
 * @param ingestedByLabel     username of the ingesting user (for display)
 */
public record ArchivalFile(UUID fileId, String friendlyId, UUID contentId, UUID ingestOperationId,
                           String originalFilename, String managedFilename, String mediaType,
                           Instant ingestedAt, UUID ingestedBy, String ingestedByLabel, UUID batchId,
                           Instant clientLastModified, String sourceNote, FileLifecycle lifecycle,
                           Restriction restriction, int displayRotation, Instant deletedAt, Instant destroyedAt,
                           String destructionReason, UUID replacementFileId, long version) {

    public boolean restricted() {
        return restriction == Restriction.RESTRICTED;
    }
}
