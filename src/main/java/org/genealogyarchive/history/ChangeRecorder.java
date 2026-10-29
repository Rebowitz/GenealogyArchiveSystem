package org.genealogyarchive.history;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.data.ChangeHistoryRepository;
import org.genealogyarchive.identity.GlobalIds;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Writes researcher-facing Change History (BUILD-SPEC s.45). Callers invoke it inside the same
 * transaction as the change itself, so a change and its history commit or roll back together.
 * Attribution comes from the {@link Actor}; nobody types their own name (SEC-007). A reason is
 * optional and never required for ordinary work.
 * <p>
 * Change History is distinct from diagnostic logs (Logback) and from technical job state.
 */
public final class ChangeRecorder {

    private final ChangeHistoryRepository repository;
    private final Clock clock;

    public ChangeRecorder(ChangeHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public UUID record(Actor actor, String objectType, UUID objectId, String changeKind,
                       List<FieldChange> fieldChanges, String reason) {
        List<FieldChange> significant = fieldChanges.stream().filter(FieldChange::changed).toList();
        UUID changeId = GlobalIds.newId();
        repository.insert(new ChangeRecord(changeId, clock.instant(), actor.kind(), actor.userId(), actor.label(),
                objectType, objectId, changeKind, significant, blankToNull(reason)),
                JsonText.fieldChanges(significant));
        return changeId;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
