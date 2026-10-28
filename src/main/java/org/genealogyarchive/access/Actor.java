package org.genealogyarchive.access;

import java.util.Objects;
import java.util.UUID;

/**
 * Who performs an operation. Every service operation that changes the archive takes an Actor
 * explicitly, so attribution is automatic (SEC-007) and authorization happens at the service
 * boundary rather than in the UI.
 *
 * @param kind   USER (a signed-in local user) or SYSTEM (an administrator at the server's command line)
 * @param userId the user's global ID; null for SYSTEM
 * @param label  username, or a SYSTEM description such as "command line"
 * @param role   the user's current role; null for SYSTEM
 */
public record Actor(Kind kind, UUID userId, String label, Role role) {

    public enum Kind { USER, SYSTEM }

    public Actor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        if ((kind == Kind.USER) != (userId != null && role != null)) {
            throw new IllegalArgumentException("A USER actor needs a user ID and role; a SYSTEM actor has neither");
        }
    }

    public static Actor user(UUID userId, String username, Role role) {
        return new Actor(Kind.USER, userId, username, role);
    }

    /**
     * The command-line administrator. Only server-side commands (which already hold the database
     * credentials) construct this actor; it is never created from a web request.
     */
    public static Actor commandLine() {
        return new Actor(Kind.SYSTEM, null, "command line", null);
    }

    public boolean isSystem() {
        return kind == Kind.SYSTEM;
    }
}
