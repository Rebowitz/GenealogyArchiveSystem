package org.genealogyarchive.records;

import org.genealogyarchive.preservation.Restriction;

import java.time.Instant;
import java.util.UUID;

/**
 * An Archival Record, the principal logical documentary object (BUILD-SPEC s.19). Everything beyond its identity
 * is optional (A-003). Dates are source expressions as written (A-004, D3-05); the transcription is
 * researcher-entered: a verbatim transcription and a separate abstract, neither OCR (D3-10).
 */
public record ArchivalRecord(UUID recordId, String friendlyId, String classificationCode, String summary, UUID sourceId,
                             UUID captureSessionId, String sourceLocator, String recordDateText, String filingDateText,
                             String transcriptionText, String abstractText, Restriction restriction,
                             RecordLifecycle lifecycle,
                             Instant createdAt, String createdByLabel, long version) {

    public boolean restricted() {
        return restriction == Restriction.RESTRICTED;
    }
}
