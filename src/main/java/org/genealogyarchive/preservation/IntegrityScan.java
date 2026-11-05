package org.genealogyarchive.preservation;

import java.time.Instant;
import java.util.UUID;

/** Lightweight scan history entry (IV-006). */
public record IntegrityScan(UUID scanId, String kind, String state, UUID requestedBy, Instant requestedAt,
                            Instant startedAt, Instant finishedAt, int contentsChecked, int findingsCount,
                            String message) {
}
