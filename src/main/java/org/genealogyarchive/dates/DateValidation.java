package org.genealogyarchive.dates;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation of a structured interpretation (clarifications s.2.2). The expression alone is always valid: an
 * interpretation that fails here is simply not stored, and the date stays NOT_INTERPRETED.
 */
public final class DateValidation {

    /** The first day of the Gregorian calendar (15 October 1582) as a Julian Day Number. */
    static final long GREGORIAN_REFORM_JDN = 2_299_161L;

    /** Errors block the interpretation; warnings are shown proportionately and never block. */
    public record Result(List<String> errors, List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private DateValidation() {
    }

    public static Result validate(DateInterpretation d) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        DateForm form = d.form();
        if (!form.hasFirstPoint()) {
            if (d.first() != null || d.second() != null) {
                errors.add("An unknown or uninterpreted date has no structured points.");
            }
            if (d.calendar() != CalendarSystem.UNSPECIFIED || d.yearStart() != YearStart.UNSPECIFIED) {
                errors.add("An unknown or uninterpreted date has no calendar or year start.");
            }
            return new Result(List.copyOf(errors), List.of());
        }
        if (d.first() == null) {
            errors.add("This form of date needs a year.");
            return new Result(List.copyOf(errors), List.of());
        }
        if (form.hasSecondPoint() && d.second() == null) {
            errors.add("A range or period needs a second year.");
        }
        if (!form.hasSecondPoint() && d.second() != null) {
            errors.add("Only a range or a from-to period has a second point.");
        }
        checkPoint(d, d.first(), "The date", errors, warnings);
        if (d.second() != null) {
            checkPoint(d, d.second(), "The second date", errors, warnings);
        }
        if (errors.isEmpty() && form.hasSecondPoint()) {
            DateReadings.Span a = DateReadings.hull(d.calendar(), d.yearStart(), d.first());
            DateReadings.Span b = DateReadings.hull(d.calendar(), d.yearStart(), d.second());
            if (a.first() > b.last()) {
                errors.add("The first date of a range or period cannot fall after the second.");
            }
        }
        return new Result(List.copyOf(errors), List.copyOf(warnings));
    }

    private static void checkPoint(DateInterpretation d, DatePoint p, String what, List<String> errors,
                                   List<String> warnings) {
        if (p.year() == null) {
            errors.add(what + " needs a year.");
            return;
        }
        if (p.year() < 1 || p.year() > 9999) {
            errors.add(what + ": the year must be between 1 and 9999.");
            return;
        }
        if (p.month() != null && (p.month() < 1 || p.month() > 12)) {
            errors.add(what + ": the month must be between 1 and 12.");
            return;
        }
        if (p.day() != null && p.month() == null) {
            errors.add(what + ": a day needs a month.");
            return;
        }
        if (p.day() != null && (p.day() < 1 || p.day() > 31)) {
            errors.add(what + ": the day must be between 1 and 31.");
            return;
        }
        if (p.dualYear() != null) {
            if (p.dualYear() != p.year() + 1) {
                errors.add(what + ": a dual year must be the following year (for example 1720/21).");
                return;
            }
            if (d.yearStart() == YearStart.JANUARY_1) {
                errors.add(what + ": a dual year means the year began on 25 March, which contradicts a year"
                        + " start of 1 January.");
                return;
            }
            if (p.month() != null && (p.month() > 3 || (p.month() == 3 && p.day() != null && p.day() >= 25))) {
                warnings.add(what + ": a dual year is unusual after 24 March, when both year styles agree.");
            }
        }
        if (DateReadings.spans(d.calendar(), d.yearStart(), p).isEmpty()) {
            errors.add(what + ": that day does not exist in the "
                    + (d.calendar() == CalendarSystem.UNSPECIFIED ? "Julian or Gregorian" : d.calendar().name().toLowerCase())
                    + " calendar.");
            return;
        }
        if (d.calendar() == CalendarSystem.GREGORIAN) {
            DateReadings.Span s = DateReadings.hull(d.calendar(), d.yearStart(), p);
            if (s.first() < GREGORIAN_REFORM_JDN) {
                warnings.add(what + " is before the Gregorian calendar began (15 October 1582); it is read as a"
                        + " proleptic Gregorian date.");
            }
        }
    }
}
