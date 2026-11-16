package org.genealogyarchive.dates;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proposes an interpretation for common English date expressions (D4-06; clarifications s.2.5). It only proposes:
 * nothing is stored until the researcher confirms. It never guesses: numeric day/month orders ("3/4/1803"), regnal
 * years, seasons, feast days and word ordinals get no proposal. Calendar and year start are always proposed as
 * UNSPECIFIED (D4-25, D4-28); "YYYY-YYYY" is proposed as BETWEEN, with FROM_TO offered at confirmation (D4-26).
 *
 * <p>Recognised: the qualifiers abt, about, c., ca., circa, est., cal., bef(ore), aft(er), bet(ween) ... and ...,
 * from ... to ..., YYYY-YYYY and a trailing "?" on a point; day-month-year and month-day-year with month names,
 * month-year, year, YYYY/YY and YYYY/YYYY dual years, ISO YYYY-MM-DD, and ordinal phrases such as "the 3d day of
 * March in the year 1803".
 */
public final class DateExpressionParser {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1), Map.entry("jan", 1), Map.entry("february", 2), Map.entry("feb", 2),
            Map.entry("march", 3), Map.entry("mar", 3), Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("june", 6), Map.entry("jun", 6), Map.entry("july", 7),
            Map.entry("jul", 7), Map.entry("august", 8), Map.entry("aug", 8), Map.entry("september", 9),
            Map.entry("sep", 9), Map.entry("sept", 9), Map.entry("october", 10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11), Map.entry("december", 12), Map.entry("dec", 12));

    private static final String YEAR = "(\\d{1,4})(?:/(\\d{1,4}))?";
    private static final String MONTH = "([a-z]+)\\.?";
    private static final String DAY = "(\\d{1,2})(?:st|nd|rd|th|d)?";

    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile(DAY + " " + MONTH + ",? " + YEAR);
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile(MONTH + " " + DAY + ", ?" + YEAR);
    private static final Pattern MONTH_YEAR = Pattern.compile(MONTH + ",? " + YEAR);
    private static final Pattern BARE_YEAR = Pattern.compile("(\\d{3,4})(?:/(\\d{1,4}))?");
    private static final Pattern ORDINAL = Pattern.compile(
            "(?:the )?" + DAY + " day of " + MONTH + ",? (?:in the year (?:of our lord )?)?" + YEAR);
    private static final Pattern YEAR_RANGE = Pattern.compile("(\\d{3,4}) ?- ?(\\d{3,4})");
    private static final Pattern BETWEEN = Pattern.compile("(?:bet\\.?|between) (.+) and (.+)");
    private static final Pattern FROM_TO = Pattern.compile("from (.+) to (.+)");
    private static final Pattern BEFORE = Pattern.compile("(?:bef\\.?|before) (.+)");
    private static final Pattern AFTER = Pattern.compile("(?:aft\\.?|after) (.+)");
    private static final Pattern MODIFIED = Pattern.compile(
            "(abt|about|c|ca|circa|est|cal)(?:\\.|\\b) ?(.+)");

    private DateExpressionParser() {
    }

    /** A proposal for {@code expression}, or empty when the parser does not recognise it unambiguously. */
    public static Optional<DateInterpretation> propose(String expression) {
        if (expression == null) {
            return Optional.empty();
        }
        String s = expression.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return Optional.empty();
        }
        DateInterpretation proposal = parse(s);
        if (proposal == null || !DateValidation.validate(proposal).valid()) {
            return Optional.empty();
        }
        return Optional.of(proposal);
    }

    private static DateInterpretation parse(String s) {
        Matcher m = YEAR_RANGE.matcher(s);
        if (m.matches()) {
            return DateInterpretation.of(DateForm.BETWEEN, DatePoint.year(Integer.parseInt(m.group(1))),
                    DatePoint.year(Integer.parseInt(m.group(2))));
        }
        m = BETWEEN.matcher(s);
        if (m.matches()) {
            return range(DateForm.BETWEEN, m.group(1), m.group(2));
        }
        m = FROM_TO.matcher(s);
        if (m.matches()) {
            return range(DateForm.FROM_TO, m.group(1), m.group(2));
        }
        m = BEFORE.matcher(s);
        if (m.matches()) {
            return single(DateForm.BEFORE, m.group(1));
        }
        m = AFTER.matcher(s);
        if (m.matches()) {
            return single(DateForm.AFTER, m.group(1));
        }
        return single(DateForm.POINT, s);
    }

    private static DateInterpretation range(DateForm form, String a, String b) {
        DatePoint p1 = point(a.trim());
        DatePoint p2 = point(b.trim());
        return p1 == null || p2 == null ? null : DateInterpretation.of(form, p1, p2);
    }

    private static DateInterpretation single(DateForm form, String text) {
        DatePoint p = point(text.trim());
        return p == null ? null : DateInterpretation.of(form, p);
    }

    /** A point with an optional leading modifier and trailing "?". */
    private static DatePoint point(String text) {
        String t = text;
        DateModifier modifier = DateModifier.EXACT;
        Matcher mod = MODIFIED.matcher(t);
        if (mod.matches()) {
            String word = mod.group(1).replace(".", "");
            modifier = switch (word) {
                case "abt", "about", "c", "ca", "circa" -> DateModifier.ABOUT;
                case "est" -> DateModifier.ESTIMATED;
                case "cal" -> DateModifier.CALCULATED;
                default -> null;
            };
            if (modifier == null) {
                return null;
            }
            t = mod.group(2).trim();
        }
        boolean uncertain = false;
        if (t.endsWith("?")) {
            uncertain = true;
            t = t.substring(0, t.length() - 1).trim();
        }
        DatePoint core = core(t);
        if (core == null) {
            return null;
        }
        DatePoint p = core.withModifier(modifier);
        return uncertain ? p.asUncertain() : p;
    }

    private static DatePoint core(String t) {
        Matcher m = ISO.matcher(t);
        if (m.matches()) {
            return DatePoint.day(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
        }
        m = ORDINAL.matcher(t);
        if (m.matches()) {
            return dated(m.group(3), m.group(4), Integer.parseInt(m.group(1)), month(m.group(2)));
        }
        m = DAY_MONTH_YEAR.matcher(t);
        if (m.matches()) {
            return dated(m.group(3), m.group(4), Integer.parseInt(m.group(1)), month(m.group(2)));
        }
        m = MONTH_DAY_YEAR.matcher(t);
        if (m.matches()) {
            return dated(m.group(3), m.group(4), Integer.parseInt(m.group(2)), month(m.group(1)));
        }
        m = MONTH_YEAR.matcher(t);
        if (m.matches()) {
            return dated(m.group(2), m.group(3), null, month(m.group(1)));
        }
        m = BARE_YEAR.matcher(t);
        if (m.matches()) {
            return dated(m.group(1), m.group(2), null, null);
        }
        return null;
    }

    /** Builds a point; a month name that is not recognised, or an inconsistent dual year, gives no proposal. */
    private static DatePoint dated(String yearText, String dualText, Integer day, Integer month) {
        if (month != null && month < 0) {
            return null;
        }
        int year = Integer.parseInt(yearText);
        Integer dual = null;
        if (dualText != null) {
            dual = dualYear(year, dualText);
            if (dual == null) {
                return null;
            }
        }
        return new DatePoint(year, month, day, DateModifier.EXACT, false, dual);
    }

    private static Integer month(String word) {
        Integer m = MONTHS.get(word);
        return m == null ? -1 : m;
    }

    /** "1720/21" or "1720/1721": the alternate year must be the following year, written in full or by its last digits. */
    static Integer dualYear(int year, String text) {
        int next = year + 1;
        String full = Integer.toString(next);
        if (text.length() < Math.min(2, full.length()) || text.length() > full.length() || !full.endsWith(text)) {
            return null;
        }
        return next;
    }
}
