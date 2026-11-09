package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/** A recent upload batch offered when adding Files to a session (no File counts: they could reveal restricted Files). */
public record UploadBatchSummary(UUID batchId, Instant createdAt, String createdByLabel, String note) {
}
