package org.genealogyarchive.records;

import java.time.Instant;
import java.util.UUID;

/** A Repository: custody/stewardship of source material (BUILD-SPEC s.22). Not a creator or Access Provider. */
public record Repository(UUID repositoryId, String friendlyId, String name, String description, String locationText,
                         Instant createdAt, long version) {
}
