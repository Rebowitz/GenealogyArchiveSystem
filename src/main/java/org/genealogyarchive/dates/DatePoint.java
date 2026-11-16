package org.genealogyarchive.dates;

/**
 * One point of an interpretation. Precision is exactly the parts present: a year alone, a year and month, or a full
 * date. {@code dualYear} is the alternate year of a dual-dated year ("1720/21" has year 1720 and dual year 1721).
 *
 * @param year      1-9999, required
 * @param month     1-12 or null
 * @param day       1-31 or null; requires a month
 * @param modifier  never null
 * @param uncertain the source or researcher marks the point as doubtful ("1847?")
 * @param dualYear  null, or year + 1
 */
public record DatePoint(Integer year, Integer month, Integer day, DateModifier modifier, boolean uncertain,
                        Integer dualYear) {

    public DatePoint {
        if (modifier == null) {
            modifier = DateModifier.EXACT;
        }
    }

    public static DatePoint year(int year) {
        return new DatePoint(year, null, null, DateModifier.EXACT, false, null);
    }

    public static DatePoint month(int year, int month) {
        return new DatePoint(year, month, null, DateModifier.EXACT, false, null);
    }

    public static DatePoint day(int year, int month, int day) {
        return new DatePoint(year, month, day, DateModifier.EXACT, false, null);
    }

    public DatePoint withModifier(DateModifier m) {
        return new DatePoint(year, month, day, m, uncertain, dualYear);
    }

    public DatePoint asUncertain() {
        return new DatePoint(year, month, day, modifier, true, dualYear);
    }

    public DatePoint withDualYear(Integer dual) {
        return new DatePoint(year, month, day, modifier, uncertain, dual);
    }
}
