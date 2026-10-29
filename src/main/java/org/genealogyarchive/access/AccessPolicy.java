package org.genealogyarchive.access;

/** Service-boundary authorization (BUILD-SPEC s.43, s.57). */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static boolean allows(Actor actor, Permission permission) {
        if (actor == null) {
            return false;
        }
        if (actor.isSystem()) {
            return true;
        }
        return permission != Permission.INITIALIZE_ARCHIVE && actor.role().grants(permission);
    }

    /** @throws AccessDeniedException if {@code actor} lacks {@code permission} */
    public static void require(Actor actor, Permission permission) {
        if (!allows(actor, permission)) {
            throw new AccessDeniedException(permission);
        }
    }
}
