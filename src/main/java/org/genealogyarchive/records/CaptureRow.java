package org.genealogyarchive.records;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One File's place in a Capture Session: its sequence position, its override of each context field (mode plus
 * value; the value is present only in VALUE mode), and its courthouse-worksheet marks (D3-02).
 * Once the row has been grouped, {@code applied} is true and {@code appliedRecordId}/{@code appliedRunId} name the
 * Record and run, permanently. A row returned to a reader is projected by {@link #forReader}: the Record is named
 * only if the reader may see it, while {@code applied} still shows that the row is grouped (GAS-P3-001).
 */
public record CaptureRow(UUID fileId, UUID captureSessionId, int sequence,
                         OverrideMode repositoryMode, UUID overrideRepositoryId,
                         OverrideMode sourceMode, UUID overrideSourceId,
                         OverrideMode captureDateMode, LocalDate overrideCaptureDate,
                         OverrideMode researcherMode, String overrideResearcher,
                         OverrideMode equipmentMethodMode, String overrideEquipmentMethod,
                         boolean startNewRecord, String recordGroup, String pageDesignation, String classificationCode,
                         UUID appliedRecordId, UUID appliedRunId, boolean applied, long version) {

    /**
     * This row as shown to a reader: the applied Record is named only if {@code recordVisible}; the run is never
     * named (run visibility is decided separately, GAS-P3-003). The applied marker is kept.
     */
    public CaptureRow forReader(boolean recordVisible) {
        return new CaptureRow(fileId, captureSessionId, sequence, repositoryMode, overrideRepositoryId, sourceMode,
                overrideSourceId, captureDateMode, overrideCaptureDate, researcherMode, overrideResearcher,
                equipmentMethodMode, overrideEquipmentMethod, startNewRecord, recordGroup, pageDesignation,
                classificationCode, recordVisible ? appliedRecordId : null, null, applied, version);
    }

    public boolean overridesAnything() {
        return repositoryMode != OverrideMode.INHERIT || sourceMode != OverrideMode.INHERIT
                || captureDateMode != OverrideMode.INHERIT || researcherMode != OverrideMode.INHERIT
                || equipmentMethodMode != OverrideMode.INHERIT;
    }
}
