package org.genealogyarchive.access;

import java.util.EnumSet;
import java.util.Set;

/**
 * The three v1 roles (BUILD-SPEC s.43). Phase 1 grants only the permissions that Phase 1 operations
 * need; later phases add permissions (for example research editing) as their operations appear.
 */
public enum Role {
    ADMINISTRATOR(EnumSet.of(
            Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD,
            Permission.CONFIGURE_ARCHIVE, Permission.MANAGE_USERS, Permission.MANAGE_STORAGE_VOLUMES,
            Permission.VIEW_OPERATIONS, Permission.RETRY_JOBS)),
    EDITOR(EnumSet.of(Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD)),
    VIEWER(EnumSet.of(Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }
}
