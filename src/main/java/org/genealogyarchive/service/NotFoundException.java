package org.genealogyarchive.service;

/** The requested object does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String what) {
        super(what + " was not found.");
    }
}
