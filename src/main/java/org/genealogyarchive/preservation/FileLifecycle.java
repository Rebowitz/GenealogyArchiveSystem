package org.genealogyarchive.preservation;

/** Archival lifecycle of a File (BUILD-SPEC s.34, s.37). DESTROYED is final: the row is the tombstone. */
public enum FileLifecycle { ACTIVE, DELETED, DESTROYED }
