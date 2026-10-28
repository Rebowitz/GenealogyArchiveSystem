package org.genealogyarchive.service;

/**
 * Optimistic-concurrency conflict (BUILD-SPEC s.44, SEC-008): the record was changed by someone
 * else after it was read. Nothing was overwritten.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String what) {
        super(what + " was changed by someone else after you opened it. Nothing was saved; "
                + "reload to see the current version and apply your change again.");
    }
}
