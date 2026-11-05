package org.genealogyarchive.preservation;

/** Why an operation stopped for a person to decide. Every block is visible and recoverable. */
public enum BlockedReason {
    /** Something already exists at the planned target; it was not touched. */
    COLLISION,
    /** The preservation read-only protection could not be applied or verified. */
    PROTECTION,
    /** The staged file is missing, unsafe, or does not match what the receiver measured. */
    STAGING_INVALID,
    /** The written copy did not verify against the staged size and SHA-256. */
    VERIFY_MISMATCH,
    /** A managed path component is a link, junction or reparse point. */
    UNSAFE_PATH,
    /** Recovery could not determine the state safely; nothing is guessed. */
    STATE_UNKNOWN
}
