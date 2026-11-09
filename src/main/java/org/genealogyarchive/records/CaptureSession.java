package org.genealogyarchive.records;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A Capture Session (BUILD-SPEC s.38): context entered once and inherited by its Files. {@code captureDate} is
 * the calendar date of capture, never a historical date (EV-003).
 */
public record CaptureSession(UUID captureSessionId, String friendlyId, String title, UUID repositoryId, UUID sourceId,
                             LocalDate captureDate, String researcher, String equipmentMethod, String notes,
                             Instant createdAt, long version) {
}
