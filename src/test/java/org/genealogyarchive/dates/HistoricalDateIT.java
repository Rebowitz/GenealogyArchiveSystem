package org.genealogyarchive.dates;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.HistoricalDateRepository;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Historical Dates in PostgreSQL (Phase 4 WP1): every worked example round-trips through the repository with its
 * envelope, the database accepts exactly what the model accepts, and the envelope integrity scan reports a
 * corrupted row without repairing it (HD-6).
 */
class HistoricalDateIT {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z").truncatedTo(ChronoUnit.MICROS);

    private TestDatabase db;
    private HikariDataSource ds;
    private HistoricalDateRepository repository;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds).migrate();
        repository = new HistoricalDateRepository(ds);
        jdbc = JdbcClient.create(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    @Test
    void hd1_everyWorkedExampleRoundTripsWithItsEnvelope() {
        List<StoredHistoricalDate> stored = new ArrayList<>();
        for (DateFixtures.EnvelopeExample e : DateFixtures.envelopeExamples()) {
            StoredHistoricalDate d = e.interpretation().form() == DateForm.NOT_INTERPRETED
                    ? StoredHistoricalDate.uninterpreted(UUID.randomUUID(), "example " + e.id(), NOW, null)
                    : StoredHistoricalDate.confirmed(UUID.randomUUID(), "example " + e.id(), e.interpretation(),
                    InterpretationOrigin.RESEARCHER, NOW, null);
            repository.insert(d);
            stored.add(d);
            StoredHistoricalDate read = repository.find(d.id()).orElseThrow();
            assertThat(read).as(e.id()).isEqualTo(d);
            assertThat(read.envelope().earliestJdn()).as(e.id()).isEqualTo(e.earliest());
            assertThat(read.envelope().latestJdn()).as(e.id()).isEqualTo(e.latest());
        }
        assertThat(HistoricalDateIntegrity.check(repository.interpreted())).isEmpty();
        assertThat(repository.interpreted()).hasSize((int) stored.stream()
                .filter(d -> d.interpretation().form() != DateForm.NOT_INTERPRETED).count());
    }

    @Test
    void a004_theExpressionIsKeptExactlyAsWritten() {
        String written = "  the 3d day of March in the year 1803  ";
        StoredHistoricalDate d = StoredHistoricalDate.confirmed(UUID.randomUUID(), written,
                DateExpressionParser.propose(written).orElseThrow(), InterpretationOrigin.PARSER_CONFIRMED, NOW, null);
        repository.insert(d);
        StoredHistoricalDate read = repository.find(d.id()).orElseThrow();
        assertThat(read.expression()).isEqualTo(written);
        assertThat(read.interpretedExpression()).isEqualTo(written);
        assertThat(read.origin()).isEqualTo(InterpretationOrigin.PARSER_CONFIRMED);
        assertThat(read.interpretation().calendar()).isEqualTo(CalendarSystem.UNSPECIFIED);
        assertThat(read.interpretation().yearStart()).isEqualTo(YearStart.UNSPECIFIED);
    }

    @Test
    void correctedExpressionMarksTheInterpretationStale() {
        StoredHistoricalDate d = StoredHistoricalDate.confirmed(UUID.randomUUID(), "12 Mrach 1847",
                DateInterpretation.of(DateForm.POINT, DatePoint.day(1847, 3, 12)), InterpretationOrigin.RESEARCHER,
                NOW, null);
        repository.insert(d);
        jdbc.sql("UPDATE gas.historical_date SET expression = '12 March 1847', row_version = row_version + 1"
                + " WHERE historical_date_id = :id").param("id", d.id()).update();
        StoredHistoricalDate read = repository.find(d.id()).orElseThrow();
        assertThat(read.interpretationStale()).isTrue();
        assertThat(read.interpretedExpression()).isEqualTo("12 Mrach 1847");
        assertThat(read.interpretation()).isEqualTo(d.interpretation());
    }

    @Test
    void hd6_theIntegrityScanReportsACorruptedEnvelopeAndRepairsNothing() {
        StoredHistoricalDate good = StoredHistoricalDate.confirmed(UUID.randomUUID(), "abt 1847",
                DateInterpretation.of(DateForm.POINT, DatePoint.year(1847).withModifier(DateModifier.ABOUT)),
                InterpretationOrigin.RESEARCHER, NOW, null);
        StoredHistoricalDate victim = StoredHistoricalDate.confirmed(UUID.randomUUID(), "March 1847",
                DateInterpretation.of(DateForm.POINT, DatePoint.month(1847, 3)), InterpretationOrigin.RESEARCHER,
                NOW, null);
        repository.insert(good);
        repository.insert(victim);
        assertThat(HistoricalDateIntegrity.check(repository.interpreted())).isEmpty();

        jdbc.sql("UPDATE gas.historical_date SET latest_jdn = latest_jdn - 1 WHERE historical_date_id = :id")
                .param("id", victim.id()).update();
        StoredHistoricalDate corrupted = repository.find(victim.id()).orElseThrow();

        List<HistoricalDateIntegrity.Finding> findings = HistoricalDateIntegrity.check(repository.interpreted());
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.historicalDateId()).isEqualTo(victim.id());
            assertThat(f.problem()).contains("differs from recomputed");
        });
        assertThat(repository.find(victim.id()).orElseThrow()).as("nothing repaired").isEqualTo(corrupted);
    }

    @Test
    void anInvalidInterpretationIsNeverStored() {
        assertThatThrownBy(() -> StoredHistoricalDate.confirmed(UUID.randomUUID(), "31 Feb 1847",
                DateInterpretation.of(DateForm.POINT, DatePoint.day(1847, 2, 31)), InterpretationOrigin.RESEARCHER,
                NOW, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StoredHistoricalDate.uninterpreted(UUID.randomUUID(), " ", NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
