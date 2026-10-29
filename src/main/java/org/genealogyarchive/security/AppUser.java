package org.genealogyarchive.security;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * A local user account. Never deleted (only disabled), so Change History attribution stays
 * resolvable. The password hash is deliberately not part of this record.
 */
public record AppUser(UUID userId, String username, String displayName, Role role, boolean enabled,
                      Instant createdAt, Instant updatedAt, long version) {

    public Actor asActor() {
        return Actor.user(userId, username, role);
    }
}
