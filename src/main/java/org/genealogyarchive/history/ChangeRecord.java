package org.genealogyarchive.history;

import org.genealogyarchive.access.Actor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recorded significant change: who, when, which object, what changed, and an optional reason.
 */
public record ChangeRecord(
        UUID changeId,
        Instant occurredAt,
        Actor.Kind actorKind,
        UUID actorUserId,
        String actorLabel,
        String objectType,
        UUID objectId,
        String changeKind,
        List<FieldChange> fieldChanges,
        String reason) {

    public ChangeRecord {
        fieldChanges = List.copyOf(fieldChanges);
    }
}
