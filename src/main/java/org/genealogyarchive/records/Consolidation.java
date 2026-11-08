package org.genealogyarchive.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A researcher's consolidation of redundant captures (BUILD-SPEC s.33, F-006): the preferred File and the others. */
public record Consolidation(UUID consolidationId, UUID preferredFileId, List<UUID> redundantFileIds, String reason,
                            Instant decidedAt, String decidedByLabel) {
}
