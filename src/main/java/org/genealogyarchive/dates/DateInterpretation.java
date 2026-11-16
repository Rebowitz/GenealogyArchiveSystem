package org.genealogyarchive.dates;

/**
 * A structured reading of a source expression (ADR-0012 s.2). It never replaces the expression, which stays the
 * authority (A-004). {@code second} is present only for BETWEEN and FROM_TO; neither point is present for UNKNOWN
 * and NOT_INTERPRETED, whose calendar and year start are UNSPECIFIED.
 */
public record DateInterpretation(DateForm form, CalendarSystem calendar, YearStart yearStart, DatePoint first,
                                 DatePoint second) {

    public static final DateInterpretation NOT_INTERPRETED =
            new DateInterpretation(DateForm.NOT_INTERPRETED, CalendarSystem.UNSPECIFIED, YearStart.UNSPECIFIED, null, null);

    public static final DateInterpretation UNKNOWN =
            new DateInterpretation(DateForm.UNKNOWN, CalendarSystem.UNSPECIFIED, YearStart.UNSPECIFIED, null, null);

    public DateInterpretation {
        if (form == null || calendar == null || yearStart == null) {
            throw new IllegalArgumentException("form, calendar and year start are required");
        }
    }

    /** A form with one point, calendar and year start UNSPECIFIED (as the parser proposes). */
    public static DateInterpretation of(DateForm form, DatePoint first) {
        return new DateInterpretation(form, CalendarSystem.UNSPECIFIED, YearStart.UNSPECIFIED, first, null);
    }

    /** A range or period, calendar and year start UNSPECIFIED. */
    public static DateInterpretation of(DateForm form, DatePoint first, DatePoint second) {
        return new DateInterpretation(form, CalendarSystem.UNSPECIFIED, YearStart.UNSPECIFIED, first, second);
    }

    public DateInterpretation withCalendar(CalendarSystem c) {
        return new DateInterpretation(form, c, yearStart, first, second);
    }

    public DateInterpretation withYearStart(YearStart y) {
        return new DateInterpretation(form, calendar, y, first, second);
    }
}
