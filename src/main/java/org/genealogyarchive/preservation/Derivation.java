package org.genealogyarchive.preservation;

import java.time.Instant;
import java.util.UUID;

/** A derivative's link to its parent File (BUILD-SPEC s.30, F-009). */
public record Derivation(UUID fileId, UUID parentFileId, String method, Instant createdAt) {
}
