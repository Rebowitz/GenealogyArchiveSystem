package org.genealogyarchive.migration;

import java.util.List;

/**
 * Where the database schema stands relative to the migrations shipped in this application build.
 *
 * @param state           overall state
 * @param currentVersion  latest successfully applied migration version, or null
 * @param pendingVersions migrations in this build not yet applied
 * @param messages        administrator-facing explanation (no secrets)
 */
public record SchemaStatus(State state, String currentVersion, List<String> pendingVersions, List<String> messages) {

    public SchemaStatus {
        pendingVersions = List.copyOf(pendingVersions);
        messages = List.copyOf(messages);
    }

    public enum State {
        /** Schema matches this build exactly. The only state in which the application is ready. */
        CURRENT,
        /** No migrations applied yet: run 'migrate'. */
        NOT_INITIALIZED,
        /** This build contains migrations not yet applied: run 'migrate'. */
        BEHIND,
        /** Database was migrated by a newer build: do not run this older build against it. */
        AHEAD,
        /** A previous migration failed: restore from the pre-upgrade backup or repair deliberately. */
        FAILED,
        /** Applied migrations do not match this build (e.g. edited migration file): investigate. */
        INVALID,
        /** The database could not be reached or inspected. */
        UNAVAILABLE
    }

    public boolean isCurrent() {
        return state == State.CURRENT;
    }

    static SchemaStatus of(State state, String currentVersion, List<String> pending, String message) {
        return new SchemaStatus(state, currentVersion, pending, List.of(message));
    }

    public static SchemaStatus unavailable(String message) {
        return new SchemaStatus(State.UNAVAILABLE, null, List.of(), List.of(message));
    }
}
