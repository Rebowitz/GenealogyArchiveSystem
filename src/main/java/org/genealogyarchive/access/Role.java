package org.genealogyarchive.access;

import java.util.EnumSet;
import java.util.Set;

/**
 * The three v1 roles (BUILD-SPEC s.43). Each phase grants only the permissions its operations need.
 */
public enum Role {
    ADMINISTRATOR(EnumSet.of(
            Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD,
            Permission.CONFIGURE_ARCHIVE, Permission.MANAGE_USERS, Permission.MANAGE_STORAGE_VOLUMES,
            Permission.VIEW_OPERATIONS, Permission.RETRY_JOBS,
            Permission.INGEST_FILES, Permission.EDIT_FILES, Permission.DESTROY_FILES,
            Permission.MANAGE_PRESERVATION, Permission.VIEW_RESTRICTED_FILES)),
    /** Phase 2: Editors ingest and correct Files and use recoverable deletion; never destroy (SEC-002). */
    EDITOR(EnumSet.of(Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD,
            Permission.INGEST_FILES, Permission.EDIT_FILES)),
    VIEWER(EnumSet.of(Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }
}
