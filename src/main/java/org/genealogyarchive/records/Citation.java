package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/**
 * Citation foundation (BUILD-SPEC s.17; ADR-0011 s.6): a Source and/or a Record (both immutable), the locator exactly
 * as first entered ({@code originalLocator}, immutable), the current locator, and the citation as entered. A
 * Record-only Citation has no Source of its own; none is inferred.
 */
public record Citation(UUID citationId, String friendlyId, UUID sourceId, UUID recordId, String originalLocator,
                       String locator, String citationText, String note, RecordLifecycle lifecycle, Instant createdAt,
                       UUID createdBy, long version) {
}
