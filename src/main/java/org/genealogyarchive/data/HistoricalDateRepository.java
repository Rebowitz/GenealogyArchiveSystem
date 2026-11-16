package org.genealogyarchive.data;

import org.genealogyarchive.dates.CalendarSystem;
import org.genealogyarchive.dates.DateEnvelope;
import org.genealogyarchive.dates.DateForm;
import org.genealogyarchive.dates.DateInterpretation;
import org.genealogyarchive.dates.DateModifier;
import org.genealogyarchive.dates.DatePoint;
import org.genealogyarchive.dates.InterpretationOrigin;
import org.genealogyarchive.dates.StoredHistoricalDate;
import org.genealogyarchive.dates.YearStart;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of Historical Dates (V0024; ADR-0012 s.2). Rows are never deleted. */
public final class HistoricalDateRepository {

    private static final String COLUMNS = """
            historical_date_id, expression, interpreted_expression, form, calendar, year_start,
            p1_year, p1_month, p1_day, p1_modifier, p1_uncertain, p1_dual_year,
            p2_year, p2_month, p2_day, p2_modifier, p2_uncertain, p2_dual_year,
            interpretation_origin, confirmed_by, confirmed_at, earliest_jdn, latest_jdn, approximate, uncertain_any,
            created_at, created_by, row_version""";

    private static final RowMapper<StoredHistoricalDate> DATE = HistoricalDateRepository::map;

    private final JdbcClient jdbc;

    public HistoricalDateRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void insert(StoredHistoricalDate d) {
        DateInterpretation i = d.interpretation();
        DatePoint p1 = i.first();
        DatePoint p2 = i.second();
        jdbc.sql("INSERT INTO gas.historical_date (" + COLUMNS + """
                ) VALUES (:id, :expression, :interpreted, :form, :calendar, :yearStart,
                          :p1y, :p1m, :p1d, :p1mod, :p1u, :p1dual, :p2y, :p2m, :p2d, :p2mod, :p2u, :p2dual,
                          :origin, :confirmedBy, :confirmedAt, :earliest, :latest, :approximate, :uncertain,
                          :createdAt, :createdBy, :version)""")
                .param("id", d.id()).param("expression", d.expression()).param("interpreted", d.interpretedExpression())
                .param("form", i.form().name()).param("calendar", i.calendar().name())
                .param("yearStart", i.yearStart().name())
                .param("p1y", p1 == null ? null : p1.year()).param("p1m", p1 == null ? null : p1.month())
                .param("p1d", p1 == null ? null : p1.day())
                .param("p1mod", p1 == null ? null : p1.modifier().name())
                .param("p1u", p1 != null && p1.uncertain()).param("p1dual", p1 == null ? null : p1.dualYear())
                .param("p2y", p2 == null ? null : p2.year()).param("p2m", p2 == null ? null : p2.month())
                .param("p2d", p2 == null ? null : p2.day())
                .param("p2mod", p2 == null ? null : p2.modifier().name())
                .param("p2u", p2 != null && p2.uncertain()).param("p2dual", p2 == null ? null : p2.dualYear())
                .param("origin", d.origin() == null ? null : d.origin().name())
                .param("confirmedBy", d.confirmedBy())
                .param("confirmedAt", Rows.param(d.confirmedAt()))
                .param("earliest", d.envelope().earliestJdn()).param("latest", d.envelope().latestJdn())
                .param("approximate", d.envelope().approximate()).param("uncertain", d.envelope().uncertain())
                .param("createdAt", Rows.param(d.createdAt())).param("createdBy", d.createdBy())
                .param("version", d.version())
                .update();
    }

    public Optional<StoredHistoricalDate> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.historical_date WHERE historical_date_id = :id")
                .param("id", id).query(DATE).optional();
    }

    /** Every date that carries an interpretation (for the envelope integrity scan, HD-6). */
    public List<StoredHistoricalDate> interpreted() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.historical_date WHERE form <> 'NOT_INTERPRETED'"
                + " ORDER BY historical_date_id").query(DATE).list();
    }

    private static StoredHistoricalDate map(ResultSet rs, int n) throws SQLException {
        DateInterpretation interpretation = new DateInterpretation(DateForm.valueOf(rs.getString("form")),
                CalendarSystem.valueOf(rs.getString("calendar")), YearStart.valueOf(rs.getString("year_start")),
                point(rs, "p1_"), point(rs, "p2_"));
        String origin = rs.getString("interpretation_origin");
        return new StoredHistoricalDate(rs.getObject("historical_date_id", UUID.class), rs.getString("expression"),
                rs.getString("interpreted_expression"), interpretation,
                origin == null ? null : InterpretationOrigin.valueOf(origin), rs.getObject("confirmed_by", UUID.class),
                Rows.instant(rs, "confirmed_at"),
                new DateEnvelope(rs.getObject("earliest_jdn", Long.class), rs.getObject("latest_jdn", Long.class),
                        rs.getBoolean("approximate"), rs.getBoolean("uncertain_any")),
                Rows.instant(rs, "created_at"), rs.getObject("created_by", UUID.class),
                rs.getLong("row_version"));
    }

    private static DatePoint point(ResultSet rs, String prefix) throws SQLException {
        Integer year = rs.getObject(prefix + "year", Integer.class);
        if (year == null) {
            return null;
        }
        return new DatePoint(year, rs.getObject(prefix + "month", Integer.class),
                rs.getObject(prefix + "day", Integer.class), DateModifier.valueOf(rs.getString(prefix + "modifier")),
                rs.getBoolean(prefix + "uncertain"), rs.getObject(prefix + "dual_year", Integer.class));
    }
}
