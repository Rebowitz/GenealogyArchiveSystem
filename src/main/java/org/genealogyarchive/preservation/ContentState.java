package org.genealogyarchive.preservation;

/**
 * State of stored content. MISSING and DAMAGED are set by integrity checks and never change the
 * authoritative size or hash; DAMAGED returns to ACTIVE only through verified repair or a re-verify
 * whose fresh hash matches. DESTROY_PENDING blocks new sharing while bytes are being removed.
 */
public enum ContentState { ACTIVE, MISSING, DAMAGED, DESTROY_PENDING, DESTROYED }
