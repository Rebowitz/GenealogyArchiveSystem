package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/**
 * A File representing (part of) a Record (BUILD-SPEC s.19). {@code sequence} orders images within the Record;
 * {@code pageDesignation} is the court/source page as written, never derived from the sequence (R-001).
 */
public record RecordFileLink(UUID recordId, UUID fileId, int sequence, String pageDesignation, String note,
                             Instant linkedAt) {
}
