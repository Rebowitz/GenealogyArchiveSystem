package org.genealogyarchive.preservation;

import java.time.Instant;

/** One entry of a file operation's append-only history (V0009 file_operation_event). */
public record OperationEvent(int seq, Instant occurredAt, String fromState, String toState, String detail) {
}
