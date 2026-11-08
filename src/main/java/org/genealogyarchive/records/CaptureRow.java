package org.genealogyarchive.records;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One File's place in a Capture Session: its sequence position, its overrides of the session context (null
 * inherits), and its courthouse-worksheet marks (D3-02). {@code appliedRecordId} is set once the row has been
 * grouped into a Record.
 */
public record CaptureRow(UUID fileId, UUID captureSessionId, int sequence, UUID overrideRepositoryId,
                         UUID overrideSourceId, LocalDate overrideCaptureDate, String overrideResearcher,
                         String overrideEquipmentMethod, String overrideNote, boolean startNewRecord, String recordGroup,
                         String pageDesignation, String classificationCode, UUID appliedRecordId, long version) {

    public boolean applied() {
        return appliedRecordId != null;
    }

    public boolean overridesAnything() {
        return overrideRepositoryId != null || overrideSourceId != null || overrideCaptureDate != null
                || overrideResearcher != null || overrideEquipmentMethod != null || overrideNote != null;
    }
}
