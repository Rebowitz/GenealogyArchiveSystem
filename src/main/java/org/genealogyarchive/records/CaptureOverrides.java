package org.genealogyarchive.records;

import java.time.LocalDate;
import java.util.UUID;

/** All of one File's overrides of its Capture Session's context (plan section 10.2; ADR-0011 s.3). */
public record CaptureOverrides(FieldOverride<UUID> repository, FieldOverride<UUID> source,
                               FieldOverride<LocalDate> captureDate, FieldOverride<String> researcher,
                               FieldOverride<String> equipmentMethod) {

    public static CaptureOverrides of(CaptureRow r) {
        return new CaptureOverrides(new FieldOverride<>(r.repositoryMode(), r.overrideRepositoryId()),
                new FieldOverride<>(r.sourceMode(), r.overrideSourceId()),
                new FieldOverride<>(r.captureDateMode(), r.overrideCaptureDate()),
                new FieldOverride<>(r.researcherMode(), r.overrideResearcher()),
                new FieldOverride<>(r.equipmentMethodMode(), r.overrideEquipmentMethod()));
    }

    public static CaptureOverrides inheritAll() {
        return new CaptureOverrides(FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(),
                FieldOverride.inherit(), FieldOverride.inherit());
    }
}
