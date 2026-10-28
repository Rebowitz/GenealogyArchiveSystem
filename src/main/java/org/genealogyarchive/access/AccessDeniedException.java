package org.genealogyarchive.access;

/** The actor is not permitted to perform the operation. */
public class AccessDeniedException extends RuntimeException {

    private final Permission permission;

    public AccessDeniedException(Permission permission) {
        super("You do not have permission for this operation.");
        this.permission = permission;
    }

    public Permission permission() {
        return permission;
    }
}
