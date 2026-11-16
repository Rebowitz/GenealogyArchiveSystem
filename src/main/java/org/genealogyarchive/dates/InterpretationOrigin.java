package org.genealogyarchive.dates;

/** Who produced a confirmed interpretation (D4-06). A parser proposal is never stored unconfirmed. */
public enum InterpretationOrigin {
    /** Entered by the researcher in the structured editor. */
    RESEARCHER,
    /** Proposed by the parser and confirmed (possibly after editing) by the researcher. */
    PARSER_CONFIRMED
}
