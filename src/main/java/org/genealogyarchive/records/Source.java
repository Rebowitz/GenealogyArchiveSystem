package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/**
 * A Source or record book (BUILD-SPEC s.19). The Repository is optional (O-003). Creator, Access Provider and
 * jurisdiction are kept as written and never stored in the Repository (O-004...O-006, D3-06).
 */
public record Source(UUID sourceId, String friendlyId, String title, UUID repositoryId, String sourceType,
                     String volumeDesignation, String creatorText, String accessProviderText, String jurisdictionText,
                     String notes, Instant createdAt, long version) {
}
