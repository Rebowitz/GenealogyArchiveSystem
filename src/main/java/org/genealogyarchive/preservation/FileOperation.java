package org.genealogyarchive.preservation;

import java.time.Instant;
import java.util.UUID;

/** A persistent archival file operation (V0009). See {@link OperationState}. */
public record FileOperation(UUID operationId, OperationKind kind, OperationState state, String actorLabel,
                            UUID actorUserId, UUID volumeId, UUID batchId, String itemKey, String stagingName,
                            UUID contentId, Integer targetGeneration, String targetPath, UUID fileId,
                            String originalFilename, Instant clientLastModified, String sourceNote,
                            Restriction requestedRestriction, UUID parentFileId, String derivationMethod,
                            Long expectedSize, String expectedSha256, long receivedBytes, Instant lastProgressAt,
                            String outcome, BlockedReason blockedReason, String message, String quarantinePath,
                            boolean stagingCleaned, UUID findingId, int attempts, Instant createdAt,
                            Instant updatedAt, Instant completedAt) {
}
