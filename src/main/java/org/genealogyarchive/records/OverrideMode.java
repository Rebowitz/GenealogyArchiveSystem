package org.genealogyarchive.records;

/**
 * How one File treats one field of its Capture Session's context (plan section 10.2): inherit the session's value
 * (resolved when read), use its own value, or record that the value is not known for this File even though the
 * session has one.
 */
public enum OverrideMode { INHERIT, VALUE, BLANK }
