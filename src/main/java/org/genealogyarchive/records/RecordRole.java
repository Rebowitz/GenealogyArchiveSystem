package org.genealogyarchive.records;

import java.util.UUID;

/**
 * A role in a Record (BUILD-SPEC s.19; D3-01): a role code and the party as written (optional: a party may be
 * unreadable). Never a Person Relationship or Event Role (A-009, EV-004).
 */
public record RecordRole(UUID roleId, UUID recordId, String roleCode, String partyAsWritten, String note, int sequence,
                         long version) {
}
