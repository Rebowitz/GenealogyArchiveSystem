package org.genealogyarchive.history;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.ChangeHistoryRepository;

import java.util.List;
import java.util.UUID;

/** Read access to Change History for signed-in users. */
public final class HistoryService {

    private final ChangeHistoryRepository repository;

    public HistoryService(ChangeHistoryRepository repository) {
        this.repository = repository;
    }

    public List<ChangeRecord> forObject(Actor actor, String objectType, UUID objectId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return repository.forObject(objectType, objectId);
    }

    public List<ChangeRecord> recent(Actor actor, int limit) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return repository.recent(Math.max(1, Math.min(limit, 500)));
    }
}
