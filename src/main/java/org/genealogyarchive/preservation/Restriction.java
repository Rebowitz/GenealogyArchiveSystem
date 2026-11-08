package org.genealogyarchive.preservation;

/**
 * Object-level restriction (BUILD-SPEC s.43). Phase 2 rule (P2-H, B2): content, thumbnails,
 * filenames and duplicate/derivative notices of a RESTRICTED File are available to Administrators
 * only; everyone else gets the same answer as for a File that does not exist.
 */
public enum Restriction { NORMAL, RESTRICTED }
