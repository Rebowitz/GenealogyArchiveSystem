package org.genealogyarchive.records;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One File's place in a Capture Session: its sequence position, its override of each context field (mode plus
 * value; the value is present only in VALUE mode), and its courthouse-worksheet marks (D3-02).
 * {@code appliedRecordId} and {@code appliedRunId} are set, permanently, once the row has been grouped.
 */
public record CaptureRow(UUID fileId, UUID captureSessionId, int sequence,
                         OverrideMode repositoryMode, UUID overrideRepositoryId,
                         OverrideMode sourceMode, UUID overrideSourceId,
                         OverrideMode captureDateMode, LocalDate overrideCaptureDate,
                         OverrideMode researcherMode, String overrideResearcher,
                         OverrideMode equipmentMethodMode, String overrideEquipmentMethod,
                         boolean startNewRecord, String recordGroup, String pageDesignation, String classificationCode,
                         UUID appliedRecordId, UUID appliedRunId, long version) {

    public boolean applied() {
        return appliedRecordId != null;
    }

    public boolean overridesAnything() {
        return repositoryMode != OverrideMode.INHERIT || sourceMode != OverrideMode.INHERIT
                || captureDateMode != OverrideMode.INHERIT || researcherMode != OverrideMode.INHERIT
                || equipmentMethodMode != OverrideMode.INHERIT;
    }
}
