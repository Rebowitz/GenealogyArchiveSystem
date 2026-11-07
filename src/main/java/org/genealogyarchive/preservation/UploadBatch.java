package org.genealogyarchive.preservation;

import java.time.Instant;
import java.util.UUID;

/** A browser upload batch (V0009): groups the items one user chose together, with an optional note. */
public record UploadBatch(UUID batchId, UUID createdBy, UUID volumeId, Instant createdAt, String note) {
}
