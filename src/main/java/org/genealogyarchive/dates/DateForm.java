package org.genealogyarchive.dates;

/**
 * The structure of a Historical Date interpretation (ADR-0012 s.2; clarifications s.2.1). The form never adds
 * precision: it records how the source frames the date, not a normalised range.
 */
public enum DateForm {
    /** A single point ("12 March 1847", "March 1847", "1847"). */
    POINT,
    /** "bef 1847": open start, ending with the point's span. */
    BEFORE,
    /** "aft 1847": starting with the point's span, open end. */
    AFTER,
    /** "bet 1845 and 1849", "1845-1849": the date lies somewhere in the range. */
    BETWEEN,
    /** "from 1845 to 1849": a period lasting from the first point to the second. */
    FROM_TO,
    /** "from 1840": a period starting at the point, with an open end. */
    FROM,
    /** A period ending at the point, with an open start. */
    TO,
    /** The source states that the date is unknown. */
    UNKNOWN,
    /** No structured reading has been confirmed; the expression alone is the date. */
    NOT_INTERPRETED;

    /** True if the form carries a first point. */
    public boolean hasFirstPoint() {
        return this != UNKNOWN && this != NOT_INTERPRETED;
    }

    /** True if the form carries a second point (BETWEEN and FROM_TO only). */
    public boolean hasSecondPoint() {
        return this == BETWEEN || this == FROM_TO;
    }
}
