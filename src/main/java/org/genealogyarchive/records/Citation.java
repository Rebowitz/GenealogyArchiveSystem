package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/** Citation foundation (BUILD-SPEC s.17): a Source and/or Record, a locator and the citation as entered. */
public record Citation(UUID citationId, String friendlyId, UUID sourceId, UUID recordId, String locator,
                       String citationText, String note, RecordLifecycle lifecycle, Instant createdAt, long version) {
}
