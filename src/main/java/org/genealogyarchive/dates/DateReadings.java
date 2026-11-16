package org.genealogyarchive.dates;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the readings a point leaves open and their day spans (clarifications s.2.3 steps 1-3).
 *
 * <ul>
 *   <li>Calendar: the stated one, or both GREGORIAN and JULIAN when UNSPECIFIED (D4-25).</li>
 *   <li>Year start: the recorded one; MARCH_25 when the point has a dual year; both JANUARY_1 and MARCH_25 when
 *       UNSPECIFIED, in every period, with no cut-off year and nothing inferred (D4-28 as modified).</li>
 *   <li>Readings whose day does not exist in that calendar and historical year are dropped.</li>
 * </ul>
 */
final class DateReadings {

    /** A reading's first and last day as Julian Day Numbers. */
    record Span(long first, long last) {
    }

    private DateReadings() {
    }

    static List<CalendarSystem> calendars(CalendarSystem calendar) {
        return calendar == CalendarSystem.UNSPECIFIED
                ? List.of(CalendarSystem.GREGORIAN, CalendarSystem.JULIAN) : List.of(calendar);
    }

    static List<YearStart> yearStarts(YearStart yearStart, DatePoint point) {
        if (point.dualYear() != null) {
            return List.of(YearStart.MARCH_25);
        }
        return yearStart == YearStart.UNSPECIFIED
                ? List.of(YearStart.JANUARY_1, YearStart.MARCH_25) : List.of(yearStart);
    }

    /** Every valid reading's span; empty if the point denotes no existing day under any reading. */
    static List<Span> spans(CalendarSystem calendar, YearStart yearStart, DatePoint point) {
        List<Span> spans = new ArrayList<>();
        for (CalendarSystem c : calendars(calendar)) {
            for (YearStart ys : yearStarts(yearStart, point)) {
                Span s = span(c, ys, point.year(), point.month(), point.day());
                if (s != null) {
                    spans.add(s);
                }
            }
        }
        return spans;
    }

    /** The point's envelope over all readings, or null if no reading is valid. */
    static Span hull(CalendarSystem calendar, YearStart yearStart, DatePoint point) {
        List<Span> spans = spans(calendar, yearStart, point);
        if (spans.isEmpty()) {
            return null;
        }
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (Span s : spans) {
            first = Math.min(first, s.first());
            last = Math.max(last, s.last());
        }
        return new Span(first, last);
    }

    /** One reading's span, or null if the day does not exist in that reading. */
    static Span span(CalendarSystem c, YearStart ys, int year, Integer month, Integer day) {
        if (ys == YearStart.JANUARY_1) {
            if (day != null) {
                return CalendarMath.exists(c, year, month, day) ? single(c, year, month, day) : null;
            }
            if (month != null) {
                return new Span(CalendarMath.jdn(c, year, month, 1),
                        CalendarMath.jdn(c, year, month, CalendarMath.monthLength(c, year, month)));
            }
            return new Span(CalendarMath.jdn(c, year, 1, 1), CalendarMath.jdn(c, year, 12, 31));
        }
        // MARCH_25: legal year Y runs from 25 March Y to 24 March Y+1.
        if (day != null) {
            int historical = month < 3 || (month == 3 && day < 25) ? year + 1 : year;
            return CalendarMath.exists(c, historical, month, day) ? single(c, historical, month, day) : null;
        }
        if (month != null) {
            if (month < 3) {
                return new Span(CalendarMath.jdn(c, year + 1, month, 1),
                        CalendarMath.jdn(c, year + 1, month, CalendarMath.monthLength(c, year + 1, month)));
            }
            if (month == 3) {
                // Written March spans two historical years: 25-31 March Y and 1-24 March Y+1.
                return new Span(CalendarMath.jdn(c, year, 3, 25), CalendarMath.jdn(c, year + 1, 3, 24));
            }
            return new Span(CalendarMath.jdn(c, year, month, 1),
                    CalendarMath.jdn(c, year, month, CalendarMath.monthLength(c, year, month)));
        }
        return new Span(CalendarMath.jdn(c, year, 3, 25), CalendarMath.jdn(c, year + 1, 3, 24));
    }

    private static Span single(CalendarSystem c, int year, int month, int day) {
        long j = CalendarMath.jdn(c, year, month, day);
        return new Span(j, j);
    }
}
