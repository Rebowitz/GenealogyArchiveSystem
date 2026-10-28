package org.genealogyarchive.files;

/** A path violates the portable relative path rules. The message names the specific rule. */
public class InvalidPortablePathException extends IllegalArgumentException {

    private final String rejectedPath;
    private final String reason;

    public InvalidPortablePathException(String rejectedPath, String reason) {
        super("Invalid portable path '" + rejectedPath + "': " + reason);
        this.rejectedPath = rejectedPath;
        this.reason = reason;
    }

    public String rejectedPath() {
        return rejectedPath;
    }

    public String reason() {
        return reason;
    }
}
