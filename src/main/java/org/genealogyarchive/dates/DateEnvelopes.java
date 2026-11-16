package org.genealogyarchive.dates;

/**
 * Computes the calendar-unit envelope of a valid interpretation (D4-05; clarifications s.2.3). Pure and
 * deterministic: the same interpretation always gives the same envelope, whatever Record, Source, jurisdiction or
 * Place it belongs to (D4-28; HD-7).
 *
 * <ul>
 *   <li>POINT: the first point's envelope.</li>
 *   <li>BEFORE: open start to the first point's last day. AFTER: the first point's first day to an open end. The
 *       inclusive edge is deliberate: including a possible match is safe, excluding one is not.</li>
 *   <li>BETWEEN and FROM_TO: first point's first day to second point's last day.</li>
 *   <li>FROM: first point's first day to an open end. TO: open start to the first point's last day (owner
 *       decision GAS-P4-001, 2026-11-16, following verified worked example 18).</li>
 *   <li>UNKNOWN and NOT_INTERPRETED: no envelope.</li>
 * </ul>
 */
public final class DateEnvelopes {

    /** Version of this algorithm, exported with derived envelopes (ADR-0012 s.11). */
    public static final int ALGORITHM_VERSION = 1;

    private DateEnvelopes() {
    }

    /**
     * @throws IllegalArgumentException if the interpretation is not valid (see {@link DateValidation})
     */
    public static DateEnvelope of(DateInterpretation d) {
        DateValidation.Result check = DateValidation.validate(d);
        if (!check.valid()) {
            throw new IllegalArgumentException("Invalid interpretation: " + check.errors());
        }
        if (!d.form().hasFirstPoint()) {
            return DateEnvelope.NONE;
        }
        DateReadings.Span p1 = DateReadings.hull(d.calendar(), d.yearStart(), d.first());
        boolean approximate = d.first().modifier().approximate();
        boolean uncertain = d.first().uncertain();
        DateReadings.Span p2 = null;
        if (d.form().hasSecondPoint()) {
            p2 = DateReadings.hull(d.calendar(), d.yearStart(), d.second());
            approximate |= d.second().modifier().approximate();
            uncertain |= d.second().uncertain();
        }
        return switch (d.form()) {
            case POINT -> new DateEnvelope(p1.first(), p1.last(), approximate, uncertain);
            case BEFORE, TO -> new DateEnvelope(null, p1.last(), approximate, uncertain);
            case AFTER, FROM -> new DateEnvelope(p1.first(), null, approximate, uncertain);
            case BETWEEN, FROM_TO -> new DateEnvelope(p1.first(), p2.last(), approximate, uncertain);
            case UNKNOWN, NOT_INTERPRETED -> DateEnvelope.NONE;
        };
    }
}
