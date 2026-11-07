package org.genealogyarchive.preservation;

import java.time.Instant;
import java.util.UUID;

/** One integrity discrepancy (V0012). Findings are never deleted; only their resolution is recorded. */
public record IntegrityFinding(UUID findingId, UUID scanId, FindingKind kind, UUID volumeId, UUID contentId,
                               Integer generation, String storagePath, UUID operationId, Long observedSize,
                               String observedSha256, String detail, Instant foundAt, boolean open,
                               String resolution, Instant resolvedAt) {
}
