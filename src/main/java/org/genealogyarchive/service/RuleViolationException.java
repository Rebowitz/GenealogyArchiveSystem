package org.genealogyarchive.service;

/** A request was understood but violates a rule; the message is safe to show to the user. */
public class RuleViolationException extends RuntimeException {
    public RuleViolationException(String message) {
        super(message);
    }
}
