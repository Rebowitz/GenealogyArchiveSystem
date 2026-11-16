package org.genealogyarchive.dates;

/**
 * Julian Day Number arithmetic for the Gregorian and Julian calendars (integer Fliegel-Van Flandern formulas),
 * valid for years 1 to 10000. Each calendar is computed with its own rules; there is never a constant offset
 * between them (clarifications s.2.3). No hybrid calendar is used: adoption dates vary by region.
 */
public final class CalendarMath {

    private static final int[] DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private CalendarMath() {
    }

    /** True if {@code year} is a leap year in {@code calendar} (GREGORIAN or JULIAN). */
    public static boolean isLeap(CalendarSystem calendar, int year) {
        if (calendar == CalendarSystem.JULIAN) {
            return year % 4 == 0;
        }
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    /** Length of a month in {@code calendar} (GREGORIAN or JULIAN). */
    public static int monthLength(CalendarSystem calendar, int year, int month) {
        requireConcrete(calendar);
        return month == 2 && isLeap(calendar, year) ? 29 : DAYS[month - 1];
    }

    /** True if the day exists in {@code calendar}. */
    public static boolean exists(CalendarSystem calendar, int year, int month, int day) {
        return month >= 1 && month <= 12 && day >= 1 && day <= monthLength(calendar, year, month);
    }

    /** The Julian Day Number of a day in {@code calendar} (GREGORIAN or JULIAN). */
    public static long jdn(CalendarSystem calendar, int year, int month, int day) {
        requireConcrete(calendar);
        long a = (14 - month) / 12;
        long y = year + 4800L - a;
        long m = month + 12 * a - 3;
        long base = day + (153 * m + 2) / 5 + 365 * y + y / 4;
        return calendar == CalendarSystem.GREGORIAN ? base - y / 100 + y / 400 - 32045 : base - 32083;
    }

    private static void requireConcrete(CalendarSystem calendar) {
        if (calendar != CalendarSystem.GREGORIAN && calendar != CalendarSystem.JULIAN) {
            throw new IllegalArgumentException("A concrete calendar is required: " + calendar);
        }
    }
}
