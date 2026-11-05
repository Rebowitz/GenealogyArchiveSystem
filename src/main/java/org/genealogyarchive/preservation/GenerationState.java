package org.genealogyarchive.preservation;

/**
 * Physical generation of a content: CURRENT; a superseded damaged copy awaiting confirmation that it
 * is in quarantine (QUARANTINE_PENDING, still accounted for); QUARANTINED (with its quarantine
 * location); MISSING (superseded by a repair while absent from disk); or DESTROYED.
 */
public enum GenerationState { CURRENT, QUARANTINE_PENDING, QUARANTINED, MISSING, DESTROYED }
