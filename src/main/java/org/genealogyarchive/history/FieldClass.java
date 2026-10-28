package org.genealogyarchive.history;

/**
 * The three mutability classes (BUILD-SPEC s.9). Every field recorded in Change History is declared
 * with its class.
 */
public enum FieldClass {
    /** Permanent identity and provenance. May be set once (at creation) and never changed. */
    IMMUTABLE,
    /** May be corrected; significant corrections keep before/after history. */
    CORRECTABLE,
    /** Research interpretation that evolves with evidence (no Phase 1 fields use this). */
    INTERPRETIVE
}
