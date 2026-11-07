package org.genealogyarchive.preservation;

/**
 * Physical generation of a content: CURRENT; a superseded damaged copy awaiting confirmation that it
 * is in quarantine (QUARANTINE_PENDING - written only before V0019, still accounted for); QUARANTINED (with
 * its quarantine location); MISSING (superseded while nothing was at its location); DESTROYED; or UNRESOLVED
 * (V0019: superseded while an object the archive cannot identify occupies its location - left untouched and
 * recorded in unresolved_object until it is verifiably gone or an Administrator resolves it).
 */
public enum GenerationState { CURRENT, QUARANTINE_PENDING, QUARANTINED, MISSING, DESTROYED, UNRESOLVED }
