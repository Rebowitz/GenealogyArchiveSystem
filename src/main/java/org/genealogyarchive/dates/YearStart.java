package org.genealogyarchive.dates;

/**
 * The legal-year convention of a written year (D4-28, as modified). Recorded only by the researcher; never inferred
 * from period, jurisdiction, Record, Source or Place. UNSPECIFIED keeps both readings in every period.
 */
public enum YearStart {
    UNSPECIFIED,
    /** The year begins on 1 January. */
    JANUARY_1,
    /** The year begins on 25 March (Old Style legal year): 1 January to 24 March of written year Y fall in Y+1. */
    MARCH_25
}
