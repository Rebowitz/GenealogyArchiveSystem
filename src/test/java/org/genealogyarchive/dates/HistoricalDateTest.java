package org.genealogyarchive.dates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.temporal.JulianFields;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Historical Date model, calendar arithmetic, envelope, validation and parser (Phase 4 WP1; ADR-0012 s.2;
 * clarifications s.2 and s.10.5, tests HD-1...HD-5 and the unit part of HD-7; ACCEPTANCE-SPEC D-001...D-005).
 */
class HistoricalDateTest {

    private static final long Q_START = CalendarMath.jdn(CalendarSystem.GREGORIAN, 1840, 1, 1);
    private static final long Q_END = CalendarMath.jdn(CalendarSystem.GREGORIAN, 1850, 12, 31);

    static Stream<DateFixtures.EnvelopeExample> envelopeExamples() {
        return DateFixtures.envelopeExamples().stream();
    }

    static Stream<DateFixtures.ParserExample> parserExamples() {
        return DateFixtures.parserExamples().stream();
    }

    // ---- HD-1: the worked examples ---------------------------------------------------------------

    @Test
    void hd1_theFixtureHoldsAllThirtySevenWorkedExamples() {
        assertThat(DateFixtures.envelopeExamples()).hasSize(37);
    }

    @ParameterizedTest(name = "HD-1 example {0}")
    @MethodSource("envelopeExamples")
    void hd1_workedExampleEnvelopes(DateFixtures.EnvelopeExample e) {
        assertThat(DateValidation.validate(e.interpretation()).valid()).as(e.id() + " valid").isTrue();
        DateEnvelope env = DateEnvelopes.of(e.interpretation());
        assertThat(env.earliestJdn()).as(e.id() + " earliest").isEqualTo(e.earliest());
        assertThat(env.latestJdn()).as(e.id() + " latest").isEqualTo(e.latest());
        assertThat(env.approximate()).as(e.id() + " approximate").isEqualTo(e.approximate());
        assertThat(env.uncertain()).as(e.id() + " uncertain").isEqualTo(e.uncertain());
        boolean overlaps = !env.isNone()
                && (env.earliestJdn() == null || env.earliestJdn() <= Q_END)
                && (env.latestJdn() == null || env.latestJdn() >= Q_START);
        assertThat(overlaps).as(e.id() + " overlaps 1840-1850").isEqualTo(e.overlaps1840to1850());
    }

    // ---- HD-2: calendar algorithms ---------------------------------------------------------------

    @Test
    void hd2_fixedPointsOfTheCalendarChangeovers() {
        assertThat(CalendarMath.jdn(CalendarSystem.JULIAN, 1582, 10, 4)).isEqualTo(2_299_160L);
        assertThat(CalendarMath.jdn(CalendarSystem.GREGORIAN, 1582, 10, 15)).isEqualTo(2_299_161L);
        assertThat(CalendarMath.jdn(CalendarSystem.JULIAN, 1752, 9, 2)).isEqualTo(2_361_221L);
        assertThat(CalendarMath.jdn(CalendarSystem.GREGORIAN, 1752, 9, 14)).isEqualTo(2_361_222L);
        assertThat(CalendarMath.jdn(CalendarSystem.JULIAN, 1700, 2, 29))
                .isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, 1700, 3, 11));
    }

    @Test
    void hd2_gregorianAgreesWithJavaTime() {
        for (LocalDate d = LocalDate.of(1500, 1, 1); d.isBefore(LocalDate.of(2101, 1, 1)); d = d.plusDays(1)) {
            assertGregorian(d);
        }
        for (LocalDate d = LocalDate.of(1, 1, 1); d.getYear() <= 9999; d = d.plusDays(97)) {
            assertGregorian(d);
        }
    }

    @Test
    void hd2_julianAgreesWithAPureJulianCalendar() {
        GregorianCalendar julian = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        julian.setGregorianChange(new Date(Long.MAX_VALUE));
        julian.clear();
        julian.set(1500, Calendar.JANUARY, 1);
        while (julian.get(Calendar.YEAR) < 2101) {
            assertJulian(julian);
            julian.add(Calendar.DAY_OF_MONTH, 1);
        }
        julian.clear();
        julian.set(1, Calendar.JANUARY, 1);
        while (julian.get(Calendar.YEAR) <= 9999) {
            assertJulian(julian);
            julian.add(Calendar.DAY_OF_MONTH, 97);
        }
    }

    @Test
    void hd2_theCalendarDifferenceIsComputedNotAssumed() {
        // Differences on 1 March of the year: -1, 0, 1, 10, 11, 12, 13, 14, 15 and 73 days.
        int[][] expected = {{150, -1}, {250, 0}, {300, 1}, {1500, 10}, {1700, 11}, {1800, 12}, {1900, 13},
                {2100, 14}, {2200, 15}, {9999, 73}};
        for (int[] e : expected) {
            long difference = CalendarMath.jdn(CalendarSystem.JULIAN, e[0], 3, 1)
                    - CalendarMath.jdn(CalendarSystem.GREGORIAN, e[0], 3, 1);
            assertThat(difference).as("year " + e[0]).isEqualTo(e[1]);
        }
    }

    // ---- HD-3: envelope properties ---------------------------------------------------------------

    @Test
    void hd3_envelopeContainsEveryReadingAndModifiersNeverMoveIt() {
        Random random = new Random(20261116L);
        for (int i = 0; i < 20_000; i++) {
            DateInterpretation d = randomPoint(random);
            if (!DateValidation.validate(d).valid()) {
                continue;
            }
            DateEnvelope env = DateEnvelopes.of(d);
            for (DateReadings.Span s : DateReadings.spans(d.calendar(), d.yearStart(), d.first())) {
                assertThat(env.earliestJdn()).as(d.toString()).isLessThanOrEqualTo(s.first());
                assertThat(env.latestJdn()).as(d.toString()).isGreaterThanOrEqualTo(s.last());
            }
            for (DateModifier m : DateModifier.values()) {
                for (boolean uncertain : new boolean[]{false, true}) {
                    DatePoint p = d.first().withModifier(m);
                    DateInterpretation variant = new DateInterpretation(d.form(), d.calendar(), d.yearStart(),
                            uncertain ? p.asUncertain() : p, null);
                    DateEnvelope v = DateEnvelopes.of(variant);
                    assertThat(v.earliestJdn()).isEqualTo(env.earliestJdn());
                    assertThat(v.latestJdn()).isEqualTo(env.latestJdn());
                    assertThat(v.approximate()).isEqualTo(m.approximate());
                    assertThat(v.uncertain()).isEqualTo(uncertain);
                }
            }
        }
    }

    @Test
    void hd3_envelopeNeverNarrowerThanTheStatedUnit() {
        // D-001: "March 1847" covers the whole month under every reading, never 1 March alone.
        DateEnvelope march = DateEnvelopes.of(DateInterpretation.of(DateForm.POINT, DatePoint.month(1847, 3))
                .withCalendar(CalendarSystem.GREGORIAN).withYearStart(YearStart.JANUARY_1));
        assertThat(march.earliestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, 1847, 3, 1));
        assertThat(march.latestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, 1847, 3, 31));
        DateEnvelope year = DateEnvelopes.of(DateInterpretation.of(DateForm.POINT, DatePoint.year(1847))
                .withCalendar(CalendarSystem.GREGORIAN).withYearStart(YearStart.JANUARY_1));
        assertThat(year.latestJdn() - year.earliestJdn()).isEqualTo(364);
    }

    // ---- HD-4: year start and dual years (D4-28 as modified) -------------------------------------

    @Test
    void hd4_recordedConventionsAreRespectedInEveryYear() {
        for (int y : new int[]{1300, 1720, 1847, 1952, 2300}) {
            DateInterpretation jan = DateInterpretation.of(DateForm.POINT, DatePoint.year(y))
                    .withCalendar(CalendarSystem.GREGORIAN).withYearStart(YearStart.JANUARY_1);
            DateInterpretation mar = jan.withYearStart(YearStart.MARCH_25);
            assertThat(DateEnvelopes.of(jan).earliestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, y, 1, 1));
            assertThat(DateEnvelopes.of(jan).latestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, y, 12, 31));
            assertThat(DateEnvelopes.of(mar).earliestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, y, 3, 25));
            assertThat(DateEnvelopes.of(mar).latestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, y + 1, 3, 24));
        }
    }

    @Test
    void hd4_unspecifiedYearStartWidensInEveryPeriodWithNoCutOff() {
        for (int y : new int[]{1300, 1720, 1751, 1752, 1847, 1952, 2300, 9000}) {
            DateInterpretation d = DateInterpretation.of(DateForm.POINT, DatePoint.year(y))
                    .withCalendar(CalendarSystem.GREGORIAN);
            assertThat(DateEnvelopes.of(d).latestJdn()).as("year " + y)
                    .isEqualTo(CalendarMath.jdn(CalendarSystem.GREGORIAN, y + 1, 3, 24));
        }
        // From 25 March to 31 December the two styles agree, so there is no year-start widening (example 26).
        DateInterpretation may = DateInterpretation.of(DateForm.POINT, DatePoint.day(1952, 5, 10))
                .withCalendar(CalendarSystem.GREGORIAN);
        assertThat(DateEnvelopes.of(may).earliestJdn()).isEqualTo(DateEnvelopes.of(may).latestJdn());
    }

    @Test
    void hd4_aDualYearGivesTheMarch25Reading() {
        DateInterpretation dual = DateInterpretation.of(DateForm.POINT,
                DatePoint.day(1720, 2, 12).withDualYear(1721)).withCalendar(CalendarSystem.JULIAN);
        assertThat(DateEnvelopes.of(dual).earliestJdn()).isEqualTo(CalendarMath.jdn(CalendarSystem.JULIAN, 1721, 2, 12));
        assertThat(DateEnvelopes.of(dual.withYearStart(YearStart.MARCH_25)))
                .isEqualTo(DateEnvelopes.of(dual));
        DateValidation.Result contradiction = DateValidation.validate(dual.withYearStart(YearStart.JANUARY_1));
        assertThat(contradiction.valid()).isFalse();
        assertThat(contradiction.errors().get(0)).contains("25 March");
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT,
                DatePoint.year(1720).withDualYear(1722))).valid()).isFalse();
        // A dual year after 24 March is accepted with a warning, never refused.
        DateValidation.Result late = DateValidation.validate(DateInterpretation.of(DateForm.POINT,
                DatePoint.day(1720, 6, 1).withDualYear(1721)));
        assertThat(late.valid()).isTrue();
        assertThat(late.warnings()).hasSize(1);
    }

    @Test
    void hd4_aDualYearIsAcceptedInAnyCalendar() {
        for (CalendarSystem c : CalendarSystem.values()) {
            assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT,
                    DatePoint.day(1720, 2, 12).withDualYear(1721)).withCalendar(c)).valid()).as(c.name()).isTrue();
        }
    }

    // ---- HD-5: day validity per calendar ---------------------------------------------------------

    @Test
    void hd5_dayValidityFollowsTheStatedCalendar() {
        DatePoint leap1700 = DatePoint.day(1700, 2, 29);
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, leap1700)
                .withCalendar(CalendarSystem.JULIAN)).valid()).isTrue();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, leap1700)
                .withCalendar(CalendarSystem.GREGORIAN)).valid()).isFalse();
        // UNSPECIFIED: only the Julian 1 January reading exists (example 22).
        DateInterpretation unspecified = DateInterpretation.of(DateForm.POINT, leap1700);
        assertThat(DateValidation.validate(unspecified).valid()).isTrue();
        assertThat(DateReadings.spans(unspecified.calendar(), unspecified.yearStart(), leap1700)).hasSize(1);
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.day(1847, 2, 31)))
                .valid()).isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.day(1847, 4, 31)))
                .valid()).isFalse();
    }

    @Test
    void hd5_structuralRules() {
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT,
                new DatePoint(1847, null, 3, DateModifier.EXACT, false, null))).valid()).as("day without month").isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.month(1847, 13))).valid()).isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.year(0))).valid()).isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.year(10000))).valid()).isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.BETWEEN, DatePoint.year(1845))).valid())
                .as("range without second point").isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.year(1845),
                DatePoint.year(1849))).valid()).as("point with a second point").isFalse();
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.BETWEEN, DatePoint.year(1850),
                DatePoint.year(1840))).valid()).as("reversed range").isFalse();
        assertThat(DateValidation.validate(new DateInterpretation(DateForm.UNKNOWN, CalendarSystem.JULIAN,
                YearStart.UNSPECIFIED, null, null)).valid()).as("unknown with a calendar").isFalse();
        assertThat(DateValidation.validate(new DateInterpretation(DateForm.NOT_INTERPRETED, CalendarSystem.UNSPECIFIED,
                YearStart.UNSPECIFIED, DatePoint.year(1847), null)).valid()).as("uninterpreted with a point").isFalse();
        assertThat(DateValidation.validate(DateInterpretation.NOT_INTERPRETED).valid()).isTrue();
        assertThat(DateValidation.validate(DateInterpretation.UNKNOWN).valid()).isTrue();
        assertThatThrownBy(() -> DateEnvelopes.of(DateInterpretation.of(DateForm.POINT, DatePoint.month(1847, 13))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hd5_anExplicitGregorianDateBeforeTheReformIsProlepticWithANote() {
        DateValidation.Result r = DateValidation.validate(DateInterpretation.of(DateForm.POINT,
                DatePoint.day(1500, 6, 1)).withCalendar(CalendarSystem.GREGORIAN));
        assertThat(r.valid()).isTrue();
        assertThat(r.warnings()).singleElement().asString().contains("proleptic");
        assertThat(DateValidation.validate(DateInterpretation.of(DateForm.POINT, DatePoint.day(1500, 6, 1)))
                .warnings()).as("never noted when the calendar is not stated").isEmpty();
    }

    @Test
    void openFormsFollowOwnerDecisionGasP4_001() {
        DatePoint p = DatePoint.year(1840);
        DateEnvelope point = DateEnvelopes.of(DateInterpretation.of(DateForm.POINT, p));
        DateEnvelope from = DateEnvelopes.of(DateInterpretation.of(DateForm.FROM, p));
        DateEnvelope to = DateEnvelopes.of(DateInterpretation.of(DateForm.TO, p));
        assertThat(from.earliestJdn()).isEqualTo(point.earliestJdn());
        assertThat(from.latestJdn()).isNull();
        assertThat(to.earliestJdn()).isNull();
        assertThat(to.latestJdn()).isEqualTo(point.latestJdn());
    }

    // ---- Parser (D4-06, D4-26) and the unit part of HD-7 -----------------------------------------

    @ParameterizedTest(name = "parser: {0}")
    @MethodSource("parserExamples")
    void parserProposalsAndRefusals(DateFixtures.ParserExample e) {
        assertThat(DateExpressionParser.propose(e.expression()).orElse(null)).isEqualTo(e.expected());
    }

    @Test
    void hd7_theParserNeverProposesACalendarOrYearStart() {
        for (DateFixtures.ParserExample e : DateFixtures.parserExamples()) {
            DateExpressionParser.propose(e.expression()).ifPresent(p -> {
                assertThat(p.calendar()).as(e.expression()).isEqualTo(CalendarSystem.UNSPECIFIED);
                assertThat(p.yearStart()).as(e.expression()).isEqualTo(YearStart.UNSPECIFIED);
            });
        }
    }

    @Test
    void hd7_theEnvelopeDependsOnlyOnTheInterpretation() {
        // The envelope function takes nothing but the interpretation: no Record, Source, jurisdiction, Place or
        // period can influence it. Equal interpretations always give equal envelopes.
        for (DateFixtures.EnvelopeExample e : DateFixtures.envelopeExamples()) {
            DateInterpretation copy = new DateInterpretation(e.interpretation().form(), e.interpretation().calendar(),
                    e.interpretation().yearStart(), e.interpretation().first(), e.interpretation().second());
            assertThat(DateEnvelopes.of(copy)).isEqualTo(DateEnvelopes.of(e.interpretation()));
        }
        assertThat(DateEnvelopes.class.getDeclaredMethods())
                .filteredOn(m -> m.getName().equals("of"))
                .allSatisfy(m -> assertThat(m.getParameterTypes()).containsExactly(DateInterpretation.class));
    }

    @Test
    void d003_rangeIsProposedAsBetweenNeverAMidpoint() {
        DateInterpretation p = DateExpressionParser.propose("1845-1849").orElseThrow();
        assertThat(p.form()).isEqualTo(DateForm.BETWEEN);
        assertThat(p.first()).isEqualTo(DatePoint.year(1845));
        assertThat(p.second()).isEqualTo(DatePoint.year(1849));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static DateInterpretation randomPoint(Random r) {
        int year = 1 + r.nextInt(9999);
        Integer month = r.nextInt(3) == 0 ? null : 1 + r.nextInt(12);
        Integer day = month == null || r.nextBoolean() ? null : 1 + r.nextInt(31);
        Integer dual = r.nextInt(8) == 0 ? year + 1 : null;
        CalendarSystem c = CalendarSystem.values()[r.nextInt(3)];
        YearStart ys = dual != null ? (r.nextBoolean() ? YearStart.UNSPECIFIED : YearStart.MARCH_25)
                : YearStart.values()[r.nextInt(3)];
        return new DateInterpretation(DateForm.POINT, c, ys,
                new DatePoint(year, month, day, DateModifier.EXACT, false, dual), null);
    }

    private static void assertGregorian(LocalDate d) {
        assertThat(CalendarMath.jdn(CalendarSystem.GREGORIAN, d.getYear(), d.getMonthValue(), d.getDayOfMonth()))
                .as(d.toString()).isEqualTo(d.getLong(JulianFields.JULIAN_DAY));
    }

    private static void assertJulian(GregorianCalendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        long expected = Math.floorDiv(c.getTimeInMillis(), 86_400_000L) + 2_440_588L;
        assertThat(CalendarMath.jdn(CalendarSystem.JULIAN, y, m, d)).as(y + "-" + m + "-" + d).isEqualTo(expected);
        assertThat(CalendarMath.exists(CalendarSystem.JULIAN, y, m, d)).isTrue();
    }
}
