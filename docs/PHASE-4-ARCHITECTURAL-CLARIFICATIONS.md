# Phase 4 architectural clarifications

**Status: PROPOSED. Planning only. No Phase 4 implementation is authorized.**

| Item | Value |
|---|---|
| Assignment | Phase 4 Architectural Clarification (owner, 2026-11-15) |
| Reviews | [`PHASE-4-PLAN.md`](PHASE-4-PLAN.md) and [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md) at `7a68d32`. Neither is approved. |
| Baseline | `phase-3-accepted` (`c494d6a`) and the accepted Phase 0–3 decisions, particularly D3-04 and the Phase 3 audit corrections GAS-P3-001…010 |
| Outputs | This analysis (§1–§6), newly found conflicts (§7), proposed decision revisions (§8; also applied, still PROPOSED, in the register) and coverage-matrix updates (§9; also applied in the plan) |
| Revision 2 | Phase 4 Final Architectural Corrections (owner, 2026-11-15): date envelope corrected and verified (§2.2–§2.4, new §2.6); restriction transaction protocol (new §5.5); additional regression tests (new §10). Earlier text is replaced in place; the previous version is commit `adb49a5`. |

Every example below is invented. The place names used for illustration (Frederick County, Clarke County,
Winchester) stand for generic patterns of this kind; nothing here asserts a historical fact.

---

## 1. D4-18 — Historical Places and Jurisdictions

### 1.1 The two models

**Model A: jurisdictional units are Places.**
- A county, state or parish is a `place` row whose type has the category JURISDICTIONAL.
- "Lies within" is a time-bounded `jurisdiction_link` between two Places.
- Names belong to Places through `place_name`.

**Model B: a separate Jurisdiction entity.**
- `jurisdiction` (with its own names, hierarchy and lifecycle) represents administrative units.
- `place` represents localities only.
- `place_jurisdiction_link` (Place → Jurisdiction, time-bounded) and `jurisdiction_parent_link` (Jurisdiction →
  Jurisdiction, time-bounded) connect them.

Both keep Place identity independent of name, jurisdiction, coordinates and address (§13).

### 1.2 The five scenarios

**(a) A locality changes county without moving.** The village of Millbrook is in Frederick County until 1838, then
in Clarke County when Clarke is formed.

| Model A | Model B |
|---|---|
| `place` PL00010 Millbrook (LOCALITY); PL00002 Frederick County, PL00003 Clarke County (JURISDICTIONAL). Links: PL00010 within PL00002, valid-to "1838"; PL00010 within PL00003, valid-from "1838". | `place` PL00010 Millbrook; `jurisdiction` J0002 Frederick, J0003 Clarke; the same two links, as Place → Jurisdiction. |

Equivalent. Millbrook's identity is untouched in both (L-002).

**(b) A county's boundaries change while its identity continues.** Frederick County loses its eastern part to Clarke
in 1838 but remains Frederick County.

| Model A | Model B |
|---|---|
| PL00002's identity is the administrative unit. Its changing extent shows through its dated incoming links (localities joining or leaving), an optional dated "formed from part of" link (§1.4) and a boundary note. Nothing about PL00002 changes. | J0002's identity is the administrative unit; its extent shows the same way. |

Equivalent in expressive power. Model A needs one documented interpretation: for a JURISDICTIONAL Place, "physical
Place" (§13) means the unit, not a fixed area. §13 already makes Place identity independent of coordinates, so
this is consistent.

**(c) Overlapping or disputed jurisdictions.**
- *Overlapping by kind:* a farm lies at once in a civil county and an ecclesiastical parish.
- *Disputed:* a frontier settlement claimed by two colonies, each with its own county, between 1774 and 1780.

| Model A | Model B |
|---|---|
| Concurrent links with a `jurisdiction_kind` (CIVIL, ECCLESIASTICAL, JUDICIAL, …) and a `basis` (RECOGNISED, CLAIMED, DISPUTED, UNKNOWN). Both claims coexist with their own provenance (A-010). | The same attributes on the links, plus kind on the Jurisdiction itself. |

Equivalent. The `basis` attribute is new and needed by both; see revised D4-18.

**(d) Historical and modern names for one locality.** "Opequon" (18th-century documents) and "Millbrook" (current).

| Model A | Model B |
|---|---|
| Two `place_name` rows on PL00010, typed HISTORICAL and CURRENT, with valid-from/to dates. The preferred display name is a pointer, so identity is unchanged (L-001). A county renamed in 1785 uses the same mechanism. | Locality names in `place_name`, county names in a separate `jurisdiction_name` (or a shared names table with typed owners). |

Model A reuses one naming mechanism. Model B duplicates it.

**(e) A jurisdiction described ambiguously in a document.** A deed says "of the County of Frederick" without a state;
the archive has Frederick County in two states.

| Model A | Model B |
|---|---|
| A PLACE Mention, "the County of Frederick", on the Record, with two PROPOSED candidates PL00002 and PL00007, or left Unresolved (L-003). The Source's `jurisdiction_text` stays as written. | Needs a JURISDICTION Mention kind and a third typed candidate column, or Place Mentions that cannot target a jurisdiction. |

Model A handles it with the existing Place Mention. Model B needs a parallel resolution target.

### 1.3 Evaluation

| Criterion | Model A (jurisdiction as Place) | Model B (separate entity) |
|---|---|---|
| Identity | Unit identity survives renames and boundary changes. One object when a town is both a locality and a municipality, and the researcher may model them as two Places if their histories diverge. | Two objects are always required for a place that is also a jurisdiction; researchers must decide which identity a source means. |
| Historical accuracy | Equal. Dated, sourced, contradictory links are possible in both. | Equal, with marginally clearer type separation. |
| Temporal relationships | One link table; containment between units and localities uses the same mechanism. | Two link tables, kept consistent. |
| Schema complexity | 3 tables (place, place_name, jurisdiction_link) plus an optional derivation link | 5–6 tables (adding jurisdiction, its names and its parent link) |
| Mentions and resolution (§3) | One PLACE target | A JURISDICTION target as well, used ambiguously |
| Phase 6 Organizations (O-005) | "County Court of Frederick" (Organization) links to PL00002 as its jurisdiction; the courthouse is a Structure; the Repository is separate. All distinct. | Same, linking to J0002 |
| Fit with spec wording | §11 "Jurisdiction relationship/history" fits directly. §13 "separate … Jurisdiction" is met by the separate link concept and the JURISDICTIONAL category. | §13 fits directly; §11's "relationship/history" fits less well. |

### 1.4 Recommendation (PROPOSED, revised D4-18)

**Model A, refined.** It keeps historical meaning at least as well as B, with one naming mechanism, one Mention
target and fewer tables. The refinements:
1. **Place type category.** `place_type` has a category (LOCALITY, JURISDICTIONAL, OTHER). The category drives only
   UI grouping and lookup filters, never inference.
2. **Link attributes.** `jurisdiction_link` gains `jurisdiction_kind` (CIVIL, ECCLESIASTICAL, JUDICIAL, MILITARY,
   OTHER) and `basis` (RECOGNISED, CLAIMED, DISPUTED, UNKNOWN; default UNKNOWN). Overlaps and disputes coexist.
3. **Optional formation links.** `place_derivation_link` (Place "formed wholly or partly from" Place, dated, sourced)
   records county formation for scenario (b). It is a relationship, never an Event. The owner may defer it, and
   scenario (b) still works without it.
4. **Two Places when histories diverge.** A Place may be both a locality and a jurisdiction. When their histories
   diverge, the researcher creates two Places and relates them with a dated link of kind `CIVIL` and a note. That
   is an explicit choice, never automatic.
5. **Integrity.** No self-link. Cycles are checked by the service across overlapping validity periods. Because
   validity is historical-date based, a cycle is reported as a warning with the conflicting periods, not silently
   prevented, when the dates make it ambiguous.

---

## 2. D4-04 to D4-06 — Historical Dates

### 2.1 Representation (PROPOSED, revised §4.1)

A `historical_date` row is an owned value, one per dated field.

**The expression**
- `expression` (required): the source wording, exactly as entered.
- `interpreted_expression`: a snapshot of `expression` taken when the interpretation was confirmed. If the
  expression is later corrected, the mismatch is visible, so a stale interpretation is never silently re-applied
  to new wording.

**The form**
- `form`: one of
  - POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM (period, open end), TO (period, open start);
  - UNKNOWN (the source states the date is unknown);
  - NOT_INTERPRETED (no structured reading).

**Calendar**
- `calendar`: UNSPECIFIED, GREGORIAN or JULIAN.
- `year_start`: UNSPECIFIED, JANUARY_1 or MARCH_25. Needed for Julian Old-Style years; without it a year-only
  Julian date would gain false precision.

**Each point: `p1_…` (and `p2_…` for BETWEEN and FROM_TO)**
- `year`, `month`, `day`: each nullable. Precision is exactly the parts present.
- `modifier`: EXACT, ABOUT, ESTIMATED or CALCULATED. This is **per point**, so "between about 1840 and 1845" keeps
  its meaning.
- `uncertain`: boolean, per point ("1847?").
- `dual_year`: the alternate year of a dual-dated year ("1720/21" is year 1720 with dual year 1721).

**Provenance and search**
- `interpretation_origin`: RESEARCHER (typed in the structured editor) or PARSER_CONFIRMED (a parser proposal the
  researcher confirmed, possibly after editing).
- `confirmed_by` and `confirmed_at`: set by the system, never typed by the researcher (SEC-007).
- `earliest_jdn` and `latest_jdn`: the derived search envelope as Julian Day Numbers (calendar-neutral integers).
  NULL means an open end. **Non-authoritative**: recomputed from the interpretation, never edited, never displayed
  as the date.
- `approximate` and `uncertain_any`: derived flags so search can qualify matches.

The qualifier-and-points design replaces the single `qualifier` of the plan's §4.1. One date-level qualifier could
not express an approximate start with an exact end.

### 2.2 Validation

Structured interpretation is only accepted when it is internally valid. **The expression alone is always valid**
and is saved as NOT_INTERPRETED.

| Rule | Check |
|---|---|
| A day needs a month, and a month needs a year | point parts are nested |
| Month range | 1–12 |
| Day range | 1 to the month's length **in the stated calendar**. Julian leap rule for JULIAN (29 Feb 1700 is valid), Gregorian rule for GREGORIAN, and the permissive union for UNSPECIFIED. |
| Year range | 1–9999. Earlier years stay NOT_INTERPRETED (BCE is out of v1 scope). |
| Points per form | p2 present exactly for BETWEEN and FROM_TO. p1 present for every form except UNKNOWN and NOT_INTERPRETED. FROM uses only p1; TO uses only p1, as its end. |
| Ordering | For BETWEEN and FROM_TO, envelope(p1).earliest ≤ envelope(p2).latest. A reversed source ("between 1850 and 1840") is kept as the expression and is not interpretable as written. The researcher records the reading they mean. |
| Dual year | `dual_year = year + 1`, and only on a point with a year. A dual year is the writer's own statement that the legal year began on 25 March, so it is interpreted with `year_start` MARCH_25. If `year_start` is UNSPECIFIED it is read as MARCH_25 for that point; if the researcher set JANUARY_1, the interpretation is refused as self-contradictory. On a full date from 25 March to 31 December (where the two styles agree) a dual year is accepted with a warning. |
| Calendar consistency | Any calendar may carry a dual year or `year_start` MARCH_25: the year-start style is independent of the Julian/Gregorian calendar (some regions used 25 March with the Gregorian calendar). An explicit GREGORIAN reading of a date before 15 October 1582 is accepted as *proleptic Gregorian* (as modern transcriptions sometimes give) with a visible note; it is never assumed. |
| Year-start styles | Only JANUARY_1 and MARCH_25 are supported. A document known to use another style (1 March, Easter, 25 December, the Pisan reckoning) is kept as its expression, or the researcher enters explicit points; the system never maps it to a supported style. |
| Nothing interpreted for UNKNOWN or NOT_INTERPRETED | all point fields NULL |

Each of these is a database CHECK where it is expressible in one row: part nesting, ranges, form/point
presence, dual year. Month length, calendar rules and point ordering are enforced in the service.

### 2.3 Stored bounds: the calendar-unit envelope

**What the bounds are.** `earliest_jdn` and `latest_jdn` form the *calendar-unit envelope*: the smallest single
interval of days that contains **every day the stored interpretation can denote**, taking into account only
1. the precision actually stated (year, month or day),
2. every calendar reading the stored `calendar` leaves open, and
3. every legal-year reading the stored `year_start` (or dual year) leaves open.

Days are Julian Day Numbers: calendar-neutral integers.

**What the bounds are not.**
- **Not the date.** They are never displayed as the date, never exported as the date and never edited. The date is
  the expression plus its interpretation.
- **Not the approximation.** ABOUT, ESTIMATED and CALCULATED do not move the bounds. "abt 1847" and "1847" have the
  same envelope; they differ in their modifier, which search treats separately (§2.6).
- **Not uncertainty about the reading.** The "?" flag does not move the bounds either.
- **Not a probability or a weighting.** Every day inside is equally "possible"; nothing is more likely for being
  central.
- **Not a set of possible days.** The envelope is the convex hull of the possible readings. When readings are
  disjoint (a Julian 12 February with an unknown year start is either 12 Feb 1720 or 12 Feb 1721), the days between
  are inside the envelope but cannot be the date. Search may return them as candidates; nothing may present them as
  readings.
- **Not a correction of the source.** If the document's date is wrong, the envelope faithfully contains the wrong
  date.
- **Not authoritative.** Bounds are recomputed by one pure function whenever the interpretation changes, and a
  consistency check (Phase 4 integrity scan) recomputes every row and reports any mismatch rather than silently
  repairing it.

**Algorithm** (pure, deterministic, unit-tested; implemented once in the `dates` package):
1. **Readings of a point.** Enumerate the readings the stored fields leave open:
   - calendar: {GREGORIAN} or {JULIAN} if stated; {GREGORIAN, JULIAN} if UNSPECIFIED;
   - year start: {JANUARY_1} or {MARCH_25} if stated; MARCH_25 if a dual year is present;
     {JANUARY_1, MARCH_25} if UNSPECIFIED **and** the written year is at or before the configured
     `MARCH_25_LAST_YEAR` (proposed 1750: the last legal year in Britain and its colonies to begin on 25 March; see
     D4-28); otherwise {JANUARY_1}.
   Readings whose day does not exist in that calendar (29 February 1700 exists only in the Julian calendar) are
   dropped. If no reading remains, the interpretation is invalid (§2.2).
2. **Unit span of a reading.** Year only: the whole legal year (1 Jan to 31 Dec, or 25 Mar to 24 Mar of the next
   year). Year and month: the whole month, in the historical year that month falls in under that legal year (under
   MARCH_25, January to 24 March of written year Y fall in historical year Y+1; written March spans two historical
   years). Full date: that day, in the historical year it falls in.
3. **Convert** each span's first and last day to JDN with the algorithm for **its own calendar**. The
   Julian/Gregorian difference is **never a constant**: it follows from the two calendars' leap-year rules for the
   actual date. (The difference is −1 day in the 2nd century, 0 in the 3rd, 10 days from March 1500, 11 from March
   1700, 12 from March 1800, 13 from March 1900, 14 from March 2100, and 73 days by 9999.) The earlier statement
   that the readings differ by "at most 13 days" was wrong and is withdrawn.
4. **Point envelope** = (minimum first day, maximum last day) over all readings.
5. **Form.**
   - POINT, FROM and TO use p1's envelope.
   - BETWEEN and FROM_TO run from p1.earliest to p2.latest.
   - BEFORE: open start, ending at **p1.latest**; AFTER: from **p1.earliest**, open end. The inclusive edge is
     deliberate: including a possible match is safe, excluding one is not.
   - UNKNOWN and NOT_INTERPRETED: no envelope (both NULL). Found by expression text only.
6. **Flags.** `approximate` is true if any point has ABOUT, ESTIMATED or CALCULATED; `uncertain_any` is true if any
   point has "?". Neither changes the bounds.

**Calendar algorithms and their verification.** Both conversions use the standard integer JDN formulas (Gregorian
and Julian variants of the Fliegel–Van Flandern method), valid for years 1–9999. Unit tests pin them to fixed points:
- Julian 4 Oct 1582 = JDN 2 299 160 and Gregorian 15 Oct 1582 = JDN 2 299 161 (consecutive days);
- Julian 2 Sep 1752 = JDN 2 361 221 and Gregorian 14 Sep 1752 = JDN 2 361 222 (consecutive days);
- Julian 29 Feb 1700 = Gregorian 11 Mar 1700.

The Gregorian side is cross-checked against `java.time.temporal.JulianFields.JULIAN_DAY` for every day of selected
centuries. The Julian side is cross-checked against `java.util.GregorianCalendar` configured as pure Julian
(`setGregorianChange(new Date(Long.MAX_VALUE))`). No library's *hybrid* calendar (which switches in 1582) is used
for interpretation, because historical adoption dates vary by region and a fixed switch date would be an assumption.

**Why the unspecified calendar is never narrowed to one reading by era.** Gregorian dates are historically impossible
before 15 October 1582, but modern transcriptions and indexes do sometimes convert old dates to proleptic Gregorian,
and the archive cannot know which a typed date is. Narrowing by era would therefore be a guess. For an UNSPECIFIED
calendar both readings are kept; the researcher removes one by stating the calendar. (Owner may decide otherwise:
D4-25.)

### 2.4 Worked examples (values verified by calculation)

Envelopes are shown as proleptic-Gregorian dates with the stored JDNs in brackets. "1840–1850?" means the envelope
overlaps 1 Jan 1840 to 31 Dec 1850 (JDN 2 393 106 … 2 397 122). Every value below was computed with the §2.3
algorithm and the fixed points above; the same table is the `HistoricalDateTest` fixture.

| # | Expression as written | Stored interpretation (confirmed) | Envelope | 1840–1850? | Never stored as |
|---|---|---|---|---|---|
| 1 | `12 March 1847` | POINT; 1847-03-12 EXACT; calendar UNSPECIFIED | 1847-03-12 … 1847-03-24 [2395733 … 2395745] (Julian reading is 12 days later) | yes | — |
| 2 | `12 March 1847` | as #1, calendar GREGORIAN | 1847-03-12 [2395733 … 2395733] | yes | — |
| 3 | `March 1847` (D-001) | POINT; 1847-03; UNSPECIFIED | 1847-03-01 … 1847-04-12 [2395722 … 2395764] | yes | 1847-03-01 |
| 4 | `abt 1847` (D-002) | POINT; 1847 ABOUT; UNSPECIFIED | 1847-01-01 … 1848-01-12 [2395663 … 2396039]; `approximate` | yes, exact overlap (tolerance not needed) | 1847 EXACT; a widened year range |
| 5 | `bef 1847` (D-003) | BEFORE; 1847 | open … 1848-01-12 [NULL … 2396039] | yes (possible) | an exact or "between" date |
| 6 | `aft 1847` | AFTER; 1847 | 1847-01-01 … open [2395663 … NULL] | yes (possible) | — |
| 7 | `1845-1849` (D-003) | BETWEEN (FROM_TO offered, D4-26); 1845, 1849 | 1845-01-01 … 1850-01-12 [2394933 … 2396770] | yes | 1847 (midpoint) |
| 8 | `from 1845 to 1849` | FROM_TO; 1845, 1849 | same as #7; different meaning | yes | — |
| 9 | `bet. abt 1840 and 1845?` | BETWEEN; p1 1840 ABOUT; p2 1845 uncertain | 1840-01-01 … 1846-01-12 [2393106 … 2395309]; `approximate`, `uncertain_any` | yes | — |
| 10 | `12 Feb 1720/21` (D-005) | POINT; 1720-02-12, dual 1721; JULIAN; year start read as MARCH_25 | 1721-02-23 [2349696 … 2349696] | no | 1720-02-12 or 1721-02-12 Gregorian |
| 10b | `12 Feb 1720/21` | as #10, calendar UNSPECIFIED | 1721-02-12 … 1721-02-23 [2349685 … 2349696] | no | — |
| 11 | `1720`, Julian source, year start not stated | POINT; 1720; JULIAN; year start UNSPECIFIED (≤ 1750, so both styles) | 1720-01-12 … 1721-04-04 [2349288 … 2349736] | no | 1720-01-01 … 1720-12-31 |
| 11b | `1720`, Julian, researcher states 25 March | as #11, year start MARCH_25 | 1720-04-05 … 1721-04-04 [2349372 … 2349736] | no | — |
| 11c | `1720`, nothing stated | POINT; 1720; calendar and year start UNSPECIFIED | 1720-01-01 … 1721-04-04 [2349277 … 2349736] | no | — |
| 12 | `1847?` | POINT; 1847 uncertain | as #4 [2395663 … 2396039]; `uncertain_any` | yes | 1847 certain |
| 13 | `the 3d day of March in the year 1803` | parser proposes POINT 1803-03-03, UNSPECIFIED; saved only on confirmation | 1803-03-03 … 1803-03-15 [2379653 … 2379665] | no | — |
| 14 | `3/4/1803` | no proposal; NOT_INTERPRETED | none | not by date | 4 March or 3 April |
| 15 | `in the fifth year of the reign of King George II` | no proposal (regnal) | none until interpreted | not by date | a guessed year |
| 16 | `Spring 1847` | no proposal (seasons not modelled) | none until interpreted | not by date | 1847-03-01 |
| 17 | `unknown` | UNKNOWN | none | not by date | — |
| 18 | `from 1840` | FROM; 1840 | 1840-01-01 … open [2393106 … NULL] (a FROM period's start is p1's span; its end is open) | yes | — |
| 19 | `12 Feb 1720`, Julian, year start not stated | POINT; 1720-02-12; JULIAN; year start UNSPECIFIED | 1720-02-23 … 1721-02-23 [2349330 … 2349696]: two possible days, a year apart; the days between are envelope only | no | either single day |
| 20 | `March 1720`, Julian, year start not stated | POINT; 1720-03; JULIAN; UNSPECIFIED | 1720-03-12 … 1721-04-04 [2349348 … 2349736] | no | — |
| 21 | `1 March 1500` | POINT; UNSPECIFIED calendar | 1500-03-01 … 1500-03-11 [2268983 … 2268993] (difference 10 days) | no | — |
| 22 | `29 Feb 1700` | POINT; UNSPECIFIED; only the Julian reading exists | 1700-03-11 [2342042 … 2342042] | no | an invalid Gregorian day |
| 23 | `10 May 2200` (calendar unstated) | POINT; UNSPECIFIED | 2200-05-10 … 2200-05-25 [2524723 … 2524738] (difference 15 days) | no | — |
| 24 | `10 May 250` | POINT; UNSPECIFIED | 0250-05-10 [1812500 … 1812500] (calendars agree in the 3rd century) | no | — |
| 25 | `10 May 150` | POINT; UNSPECIFIED | 0150-05-09 … 0150-05-10 [1775975 … 1775976] (Julian reading is one day *earlier*) | no | — |

Examples 21–25 exist to show the difference is computed, not assumed: it is negative, zero, 10, 13 or 15 days
depending on the date.

### 2.5 Parser and confirmation (PROPOSED, revised D4-06)
- **What the parser recognises.** A deterministic grammar for English forms only:
  - qualifiers: abt, about, c., ca., circa, est., cal., bef(ore), aft(er), bet(ween) … and …, from … to …,
    `YYYY-YYYY`, a trailing `?`;
  - day-month-year written with month names; month-year; year; `YYYY/YY` dual years; ISO `YYYY-MM-DD`;
  - ordinal phrases ("the 3d day of March in the year 1803").
- **What it refuses.** Anything else gets *no* proposal: numeric day/month order, regnal years, seasons, feast days,
  partial words. It never guesses.
- **A proposal is never saved.** The UI shows it in words ("about March 1847: month precision, calendar not
  stated") beside the expression. Only **Confirm**, or editing then confirming, writes it, with
  `interpretation_origin = PARSER_CONFIRMED`, `confirmed_by`, `confirmed_at` and `interpreted_expression`.
- **No background, import or migration parsing** (UP-006). Existing Phase 3 date text stays NOT_INTERPRETED until a
  researcher interprets it.
- **Correcting the expression** keeps the old interpretation but marks it "confirmed against earlier wording" (the
  `interpreted_expression` mismatch) until it is re-confirmed or cleared. Nothing is silently re-parsed.

### 2.6 Approximate-date tolerance in Phase 7 search (PROPOSED)

Phase 4 stores no tolerance. Phase 7 may apply one at **query time** only:
- **Configuration.** A versioned, archive-wide setting `date_search_tolerance` with one value per modifier: ABOUT,
  ESTIMATED, CALCULATED, and one for the uncertain flag. Units are whole years or days. Defaults are an owner
  decision for Phase 7; Phase 4 proposes none. Changing the setting is recorded in Change History.
- **Application.** For each date, the effective search interval is `[earliest − tol(p1), latest + tol(pN)]`, where
  `tol(p)` is the largest tolerance among that endpoint's modifier and uncertain flag. An open end stays open. Dates
  with no envelope are never date-matched.
- **Labelling.** A result that matches only because of tolerance is labelled "approximate match", with the stored
  expression and the tolerance that admitted it. A result inside the stored envelope is a "possible match" (the
  envelope, not the date, overlaps the query).
- **No write-back.** Tolerance never alters `historical_date`, its envelope, the interpretation or Change History of
  the date. Changing the setting changes search results only, and the next search re-applies it.
- **No precision gain.** Tolerance can only widen. A query can never narrow a stored envelope.

---

## 3. D4-09 and D4-13 — Identity and Candidate Resolution

### 3.1 Two subjects, one framework
- **Mention resolution**: which entity a source's wording refers to (`candidate_resolution`: Mention → Person or
  Place).
- **Person-identity proposal**: whether two Person records represent one historical individual
  (`person_identity_proposal`: Person ↔ Person, unordered).

Both use the state progression of §16. The meanings are fixed below.

### 3.2 State meanings

| State | Mention resolution | Person-identity proposal | Effect on data |
|---|---|---|---|
| **PROPOSED** | A researcher records that candidate X *may* be what this wording refers to. Several may coexist (C-002). | A researcher records that P-a and P-b *may* be the same individual. | None. Interpretive and non-binding. No Names, relationships or Mentions change. |
| **RESOLVED** | The researcher's deliberate current decision that X *is* the referent (C-004). At most one per Mention. | The researcher's deliberate current decision that P-a and P-b *are* the same individual. At most one non-terminal row per pair. | Display only: "resolved", shown on the Mention, the Record Role and the candidate's "Mentioned in" list. **No data is copied, merged or re-pointed.** The wording stays as written. |
| **REJECTED** | The researcher decides X is *not* the referent: a proposal judged wrong, or a resolution later judged wrong (C-005). | The researcher decides they are *not* the same individual. | Kept, visible as history. Terminal: re-proposing X later creates a **new** row, so the earlier rejection stays on record. |
| **SUPERSEDED** | A RESOLVED decision replaced by a newer RESOLVED decision for a *different* candidate, without asserting the old one was wrong. | Not used (the pair has no alternative candidate). Reversal is REJECTED. | Kept, linked by `superseded_by`. Terminal. |

**Derived Mention state** (never stored):
- *Resolved*: one RESOLVED row is visible to the reader.
- *Proposed*: no RESOLVED row, at least one PROPOSED.
- *Unresolved*: anything else, including Mentions with only rejected or superseded rows.

### 3.3 Transitions and attribution

| From | To | How | Recorded |
|---|---|---|---|
| (new) | PROPOSED | **Propose** | `proposed_by`, `proposed_at`, rationale |
| (new) | RESOLVED | **Resolve directly** (an explicit action; D4-24 "create from Mention" also uses it) | `resolved_by`, `resolved_at`, rationale |
| PROPOSED | RESOLVED | **Resolve** (an explicit confirmation) | `resolved_by`, `resolved_at`, rationale |
| PROPOSED | REJECTED | **Reject** | `closed_by`, `closed_at`, rationale |
| RESOLVED | REJECTED | **Reject resolution** (the Mention becomes Proposed or Unresolved) | `closed_by`, `closed_at`, rationale |
| RESOLVED | SUPERSEDED | Only as part of **Resolve** to another candidate, in the same transaction; the researcher sees which decision is being replaced and may instead choose to reject it | `closed_by`, `closed_at`, `superseded_by` |
| REJECTED or SUPERSEDED | — | none (terminal) | — |

**Database enforcement:**
- A trigger allows only these transitions.
- Each attribution column may be set once and never changed.
- The Mention, candidate and proposal fields are immutable identity.
- Rows are never deleted.

Change History records each transition as well, so the full trail survives without Phase 5. Phase 5 adds Evidence
links to the rows and does not rewrite them (§62 "Candidate Resolution history").

There is **no code path** from PROPOSED to RESOLVED other than the explicit Resolve action. A test enumerates every
service method to prove it (A-006).

### 3.4 Conflicting candidates and reversal
- **Several PROPOSED candidates** coexist. Resolving one does **not** reject the others; they stay PROPOSED as open
  alternatives until the researcher closes them. Nothing is automatic.
- **Reversal** (C-005): the old RESOLVED row becomes REJECTED or SUPERSEDED, and the new decision is a new row. The
  earlier decision, who made it and when stay visible: "Resolved to P00127 by editor1 on … — rejected by editor2
  on …: later deed shows a different Robert".
- **A deleted candidate Person** leaves its resolution in place, shown as "resolved to P00127 (deleted)". The
  researcher decides what to do; nothing is automatic.

### 3.5 An accepted identity hypothesis vs. a physical merge

| | RESOLVED identity proposal (Phase 4) | Physical merge (not in Phase 4) |
|---|---|---|
| Person records | Both P00127 and P00133 remain ACTIVE with their own IDs, Names, relationships and Mentions | One survives; the other is retired (MERGED_INTO) and its data re-pointed |
| Reversible | Yes: reject the proposal and nothing else changes | Requires an un-merge design |
| Display | Each Person page shows "Resolved as the same individual as P00133 (decision by …, date, rationale)", with navigation to the other | One page |
| Transitivity | **None stored or inferred.** P127=P133 and P133=P140 does not create P127=P140. The UI may list the chain as navigation, labelled "linked through", never as a decision. | — |
| Contradictions | Allowed: P127=P133 resolved, P133=P140 resolved, P127≠P140 rejected. The UI flags this proportionately for attention, never as an error (A-010, UI-006). | — |

**No automatic merge, proposal or resolution exists anywhere**: not from matching Names, dates, relationships or
lookup results (P-010, §61). A later physical merge, if the owner ever wants one, is a separate design that uses
RESOLVED identity proposals as its authorization record.

---

## 4. D4-14 — Collective references

### 4.1 Distinctions preserved

Example will (invented), Record R00120:

> "…to my grandchildren John and Mary Smith, and to the three children of my deceased son John, equally…"

| What the source contains | How it is stored | What is never created |
|---|---|---|
| **Individuals explicitly named** ("John", "Mary Smith") | Two PERSON Mentions, M00301 "my grandchildren John" and M00302 "Mary Smith", each resolvable on its own. They are **not** part of the collective unless the source makes them so. | Persons (C-001) |
| **A collective with an anchor** ("the three children of my deceased son John") | `collective_reference` CP0012: wording as written; anchor Mention M00303 "my deceased son John"; anchor phrase "children of"; `stated_count` 3 with `count_as_written` "three" | three placeholder Persons; a PARENT_CHILD link from John to anyone (P-008, A-009) |
| **Unnamed members** | Nothing beyond the collective itself. The stated count is a fact about the wording, not a list of slots. | member rows for unidentified people |
| **Individuals later identified by research** | `collective_member` rows, CP0012 → P00140 and CP0012 → P00141, each PROPOSED, then RESOLVED by explicit action | relationships between members, to the anchor, or to the testator |
| **Conflicting or withdrawn identifications** | A member row RESOLVED → REJECTED with a rationale, kept. A rival candidate for the same collective is another row, so both can be PROPOSED. | deletion of the earlier identification |
| **A named individual who is also a member** (sources sometimes do both) | A `collective_named_mention` row links M00301 to CP0012 explicitly, when the source wording makes the named person part of the group | an inferred link |

### 4.2 Schema (PROPOSED)
- **`collective_reference`** (`CP`)
  - `record_id` (anchor Record, required)
  - either `record_role_id` (a Role-backed collective, e.g. grantee "the heirs of John Smith") or
    `wording_as_written`. CHECK: exactly one.
  - `anchor_mention_id` (optional, a PERSON Mention) and `anchor_phrase_as_written` (optional)
  - `stated_count` (optional, ≥ 1) with `count_as_written` (required when `stated_count` is set)
  - `restriction` (see §5), lifecycle ACTIVE or DELETED, note, attribution, `row_version`
- **`collective_named_mention`** (`collective_id`, `mention_id`): explicit only. A Mention may belong to several
  collectives.
- **`collective_member`**
  - `collective_id`, `person_id`
  - `state`: PROPOSED, RESOLVED or REJECTED
  - rationale and attribution per transition, as in §3.3

### 4.3 Lifecycle and Candidate Resolution
- **Same states, different cardinality.** Membership uses PROPOSED, RESOLVED and REJECTED with the same meanings and
  terminal rules as §3. **SUPERSEDED does not apply** and **several RESOLVED members are normal**: a collective has
  many members, while a Mention has one referent. At most one non-terminal row per (collective, Person). This is a
  deliberate variant of the framework, recorded as conflict C-6 in §7.
- **Stated count is not a limit.** If RESOLVED members outnumber `stated_count` (three stated, four identified),
  the page shows a proportionate note ("4 identified; source states three"), never an error and never a block
  (A-010, UI-006).
- **Deleting a collective** (recoverable) keeps its members and history. A deleted Person shown as a member is marked
  as deleted.
- **Membership creates nothing else.** No relationships, Names or Persons. Adding P00140 as a member does not add
  "child of" John; the researcher enters relationships separately if the evidence supports them.

---

## 5. D4-15 and D4-16 — Privacy and authorization

### 5.1 Two policies compared

| | **S: complete suppression** (hidden equals nonexistent) | **R: redacted representation** ("restricted person" placeholder) |
|---|---|---|
| Consistency with Phase 3 | Same as D3-04 and the accepted GAS-P3-001…010 corrections | Breaks the accepted hidden-equals-nonexistent rule |
| What a non-Administrator learns | Nothing about a restricted Person, including that one exists or is linked to a visible document | That a restricted Person exists and where it is linked: to which Records, how many relationships, which collectives. For living-person and adoption privacy, *that link is often the sensitive fact.* |
| State coherence | Needs care: a visible Mention resolved to a restricted Person would look Unresolved, and an editor's own resolve would collide with the hidden decision (see C-1) | Simple: the editor sees "Resolved: restricted" and cannot overwrite it |
| Counts and identifiers | All counts over visible objects only. Friendly-ID gaps accepted (Phase 2 and 3 precedent). | Counts include placeholders |

### 5.2 Recommendation: policy S, made coherent by *Mention restriction* (PROPOSED, revised D4-16)

Policy S is consistent with Phase 3 and discloses least. Its single weakness is the hidden-resolution collision.
That is solved by giving the connecting objects their own explicit restriction:
1. **Persons** are NORMAL or RESTRICTED. Only Administrators (VIEW_RESTRICTED_PEOPLE) see restricted ones.
2. **Mentions, Collective References and person-identity proposals** also carry NORMAL or RESTRICTED. **Invariant:**
   a NORMAL Mention, collective or identity proposal never has a candidate, member or party that is a RESTRICTED
   Person (in any state, including REJECTED and SUPERSEDED rows, which are history), and a NORMAL collective never
   has a RESTRICTED anchor or named Mention. This is enforced in the service on every write, under the lock order of
   §5.5, **and** by deferred constraint triggers checked at commit (§5.5 step 6), so no transaction can commit a
   violating state even if a service path is wrong.
3. **Linking a restricted Person.** To connect a document to a restricted Person, an Administrator creates or uses
   a **RESTRICTED Mention** (Role-backed or free) and resolves it. The visible Role keeps its `party_as_written`,
   because that is Record content and not a Person. The restricted Mention is invisible to others.
4. **Restricting a Person who is already linked.** The Administrator sees a **preview** of every NORMAL Mention,
   collective membership, identity proposal and relationship that names the Person. On confirmation, in one
   transaction, those Mentions, collectives and proposals become RESTRICTED. Relationships need no change: their
   visibility is computed from both endpoints. This is a deliberate action with a preview, not automatic
   propagation (D3-04). Lifting a restriction never un-restricts the linked objects automatically (SEC-005). The
   complete protocol (preview, revalidation, locking, concurrency, atomicity, failure and size bounds) is §5.5.
5. **Several Mentions per Role.** A Role may have more than one Mention, so an Editor who sees no Mention on a Role
   can create their own NORMAL one without colliding with a hidden RESTRICTED one. Administrators see both,
   labelled. This is the cost of policy S (C-3).

### 5.3 Visibility rules for each surface (policy S)

For a reader without VIEW_RESTRICTED_PEOPLE ("non-admin"):

| Surface | Rule | Identical-answer requirement |
|---|---|---|
| Person page, `P…` lookup, guessed UUID | A restricted Person is not found | Same status, message and template as a nonexistent ID (GAS-P3-001 pattern) |
| Person list, name lookup, candidate pickers (D4-21) | Restricted Persons and **all their Names** excluded **before** limits and ordering (GAS-P3-010 lesson) | Result sets and counts equal those of an archive without them |
| A visible Person's relationships | A relationship is shown only if both Persons are visible. Counts and grouping headers come from visible rows only. | No "1 more" or empty group header |
| Names | Visible only through a visible Person | — |
| Mentions on a Record | RESTRICTED Mentions omitted. Each visible Mention's derived state is computed from visible candidate rows only, which by the invariant are all NORMAL Persons. | — |
| Resolution history | Rows on RESTRICTED Mentions omitted. Change History entries about restricted Persons, Names, Mentions, collectives and proposals are removed whole. Values naming `P`, `M` or `CP` IDs or UUIDs of hidden objects are withheld; tokenization is extended and parse-safe (`PL` is never `P`). | — |
| A visible Person's "Mentioned in" | Only NORMAL Mentions on visible Records | — |
| Collective references | RESTRICTED collectives omitted. On a visible collective, members are by the invariant all visible, and member counts come from visible rows. `stated_count` and the wording are Record content and are shown. | — |
| Person-identity proposals | RESTRICTED proposals omitted. A visible Person's page never hints at one. | — |
| Record Roles | Always shown with `party_as_written`, as Record content. A RESTRICTED Mention on the Role is not shown; a NORMAL one is. | Creating a NORMAL Mention on that Role succeeds exactly as on a Role without a hidden one |
| Writes that name a hidden Person (add relationship, propose candidate, add member, identity proposal) | Answered exactly as for a nonexistent Person ID | Same exception type and message |
| Counts on lists and Records | Computed after visibility filtering | — |
| Restricted Records (Phase 3) | Unchanged: every Mention, collective and resolution on a restricted Record is hidden with it | — |

**Residual disclosure.** When a Person is restricted after being linked (rule 4), a non-admin who had seen those
Mentions will notice they are gone. This is the same class of after-the-fact disappearance the Phase 3 re-audit
accepted for grouping runs (GAS-P3-003). It is recorded, not hidden.

### 5.4 Regression tests against indirect disclosure (`PeopleRestrictionIT`)

Each test compares a non-admin's answer against an archive identical except that the restricted object never
existed. Equality is the assertion.
1. Person page, `P` lookup and random UUID give identical status and message. So do `P99999` versus a restricted
   Person's friendly ID.
2. Name lookup and candidate picker results are equal, with the limit filled by visible Persons only (more than 50
   matching visible Persons plus restricted ones).
3. A visible Person related to a restricted one shows no relationship, the same count and no empty group header.
4. A Record with a RESTRICTED Mention on a Role: the Role shows `party_as_written` and no Mention, and an Editor
   creating a NORMAL Mention on that Role succeeds exactly as on a fresh Role.
5. An Editor proposing or resolving the restricted Person on any Mention gets the same answer as for a nonexistent
   Person.
6. Writing a NORMAL Mention with a restricted candidate is refused for Administrators too, which enforces the
   invariant.
7. The restrict-a-linked-Person flow: the preview lists the linked objects; afterwards each is RESTRICTED, and a
   non-admin sees the equal-to-nonexistent answers above.
8. A collective with a restricted member is impossible on a NORMAL collective. A RESTRICTED collective is omitted.
9. Change History (object and recent) contains no entry about the hidden objects, and no `P`/`M`/`CP` ID or UUID of
   one in any value or reason. A parse-safety test confirms `PL` is never read as `P`.
10. Identity proposals naming a restricted Person never appear, and no count or banner on a visible Person
    reflects them.
11. The Phase 3 restriction suites still pass unchanged.

### 5.5 Restriction transaction protocol (PROPOSED, D4-16 and D4-27)

This protocol is the only way an already-linked Person becomes restricted. It is an explicit operation confirmed by
an Administrator, which distinguishes it from the automatic propagation D3-04 prohibits:
- nothing runs on a trigger, schedule or side effect of another edit;
- the exact affected set is shown before anything changes;
- the Administrator's confirmation and reason are recorded against every changed object.

#### 5.5.1 Objects

**`restriction_operation`**: Administrator-only, never deleted.

| Column | Meaning |
|---|---|
| `operation_id` | UUIDv7 |
| `person_id` | the Person being restricted |
| `person_row_version_at_preview` | the Person's `row_version` when the preview was taken |
| `state` | PREVIEWED, APPLIED, STALE, EXPIRED or CANCELLED |
| `reason` | required |
| `previewed_by`, `previewed_at` | system-set |
| `expires_at` | proposed: preview plus 30 minutes |
| `applied_by`, `applied_at` | system-set |
| `stale_detail` | what differed, if the operation went STALE |
| `item_count` | number of items |

**`restriction_operation_item`**: one row per object in the preview.

| Column | Meaning |
|---|---|
| `operation_id`, `object_type`, `object_id` | the object |
| `row_version_at_preview` | its `row_version` when the preview was taken |
| `action` | what the confirmation does to it (below) |

`action` is one of:
- RESTRICT: the object will be changed.
- ALREADY_RESTRICTED: listed for completeness; no change.
- HIDDEN_BY_ENDPOINT: relationships and Names. No change, because their visibility follows the Person.

#### 5.5.2 The affected set (a deterministic closure)

For Person P, computed by one query function used both at preview and at confirmation:
1. **Mentions:** every Mention with any `candidate_resolution` row targeting P, *in any state*. REJECTED and
   SUPERSEDED rows are history that names P.
2. **Collective references:** every collective with any `collective_member` row for P (any state), or whose anchor
   Mention or any named Mention is in set 1.
3. **Identity proposals:** every `person_identity_proposal` with P on either side (any state).
4. **Informational** (HIDDEN_BY_ENDPOINT): relationships with P as an endpoint, and P's Names.

The closure is complete after step 2, because nothing depends on collectives or proposals. **DELETED objects and
objects on restricted Records are included**: restoring or unrestricting them later must not expose P.

**Collateral.** For each item, the preview also lists the *other* visible Persons whose pages will lose it. For
example: "M00303 also has candidate P00140; non-administrators will no longer see this Mention on P00140's page."
Restriction is per object, so hiding a Mention hides it whole (conflict C-12).

#### 5.5.3 Global lock order

Every Phase 4 write that touches these objects acquires row locks in this order. Within a level, rows are locked in
ascending UUID order.

| Level | Rows | Lock |
|---|---|---|
| L0 | `restriction_operation` | taken only by confirm and cancel |
| L1 | Phase 3 objects (Record, Record Role) when the write needs them | as Phase 3 |
| L2 | **Person** rows | `FOR SHARE` by any write that creates or changes a link to the Person (candidate, member, identity proposal, relationship); `FOR UPDATE` by a change to the Person's own restriction or lifecycle |
| L3 | Mentions | `FOR UPDATE` |
| L4 | Collective references | `FOR UPDATE` |
| L5 | Identity proposals | `FOR UPDATE` |
| L6 | Inserted or updated child rows | after their parents |

**Every** candidate, member or identity-proposal write bumps its parent's `row_version`. That includes propose,
resolve, reject and supersede. The same ordering is used by the interactive service and by the background job.

#### 5.5.4 Preview
1. The Administrator chooses **Restrict** on P and enters a reason.
2. The service computes the affected set in a single `REPEATABLE READ READ ONLY` snapshot.
3. In a separate short transaction, it inserts the operation (PREVIEWED) and its items with their `row_version`s.
4. The preview page lists:
   - counts by type;
   - every item (paged);
   - the collateral;
   - objects that are already restricted;
   - deleted objects, and objects on restricted Records;
   - the expiry time.
5. Nothing visible to anyone else has changed. The preview itself is Administrator-only.

#### 5.5.5 Confirmation: one transaction

`READ COMMITTED`, with `SET LOCAL lock_timeout` (proposed 5 s) and `statement_timeout` (§5.5.8).

1. **Lock the operation** (L0, `FOR UPDATE`).
   - APPLIED: return its result page. Nothing is re-applied, so a double submit is idempotent.
   - STALE, EXPIRED or CANCELLED: refuse with "re-preview".
   - Past `expires_at`: refuse, and mark it EXPIRED (see step 7).
2. **Lock P** (L2, `FOR UPDATE`), then check `row_version = person_row_version_at_preview` and that P is still
   NORMAL. If either check fails, go to step 7 (STALE).
   - Any write that is creating a link to P already holds P `FOR SHARE`, so confirmation waits until that write
     commits or rolls back. Its result is then visible to step 3.
   - Any link write that starts after this point waits for the confirmation. It then reads P as RESTRICTED and must
     refuse a NORMAL link.
3. **Recompute the affected set** with the same closure function, under the P lock. The set of (type, id) must equal
   the preview's items exactly. If it does not, go to step 7.
4. **Lock every RESTRICT item** at L3–L5, in order, `FOR UPDATE`. Each `row_version` must equal its
   `row_version_at_preview`. Informational items are compared by presence and `row_version`. If any differs, go to
   step 7.
5. **Apply.** All of these happen in the same transaction:
   - each RESTRICT item becomes RESTRICTED, with its `row_version` incremented and system-set attribution
     (`updated_by` is the confirming Administrator);
   - P becomes RESTRICTED;
   - one Change History entry is written per changed object, carrying the reason and `operation_id`;
   - the operation becomes APPLIED, with `applied_by` and `applied_at`.
6. **Commit-time integrity check.** `DEFERRABLE INITIALLY DEFERRED` constraint triggers on `person`, `mention`,
   `collective_reference`, `person_identity_proposal`, `candidate_resolution`, `collective_member` and
   `collective_named_mention` re-check the invariant of §5.2 rule 2 for every row the transaction touched.
   - On any violation, the **whole transaction rolls back**.
   - The Administrator sees "integrity check failed; nothing was changed", and the failure is logged as an issue.
     This fails visibly (CLAUDE.md).
   - The same triggers guard every other write path, so the invariant cannot be broken by any code path.
7. **Stale or expired path.** Roll back the confirmation transaction entirely. Then, in a separate one-statement
   transaction, set the operation to STALE (with `stale_detail`, e.g. "2 Mentions added, 1 collective modified")
   or EXPIRED. The Administrator is shown the differences and offered a new preview. **Nothing is ever applied from
   a stale preview, even partially.**

#### 5.5.6 Failure, rollback and retry

| Failure | Result | Operation state | Retry |
|---|---|---|---|
| Lock timeout (`55P03`), deadlock (`40P01`) or serialization failure (`40001`) | Entire transaction rolled back | Stays PREVIEWED, because its update was in the rolled-back transaction | One automatic retry for a deadlock. Otherwise the Administrator retries. Every retry re-runs steps 1–6, including revalidation. |
| Integrity trigger violation | Rolled back | PREVIEWED; the issue is logged | Only after investigation |
| Statement timeout or connection loss | Rolled back (PostgreSQL guarantees this) | PREVIEWED | Retry, or use the job path (§5.5.8) |
| Application crash after commit | Committed state is complete | APPLIED | Re-submitting shows the result (idempotent) |

Partial restriction is impossible:
- the only writes happen in one transaction;
- PostgreSQL commits it atomically;
- the deferred triggers reject any committed state in which a NORMAL object targets a RESTRICTED Person.

#### 5.5.7 Concurrency cases

| Case | Behaviour |
|---|---|
| An Editor adds a candidate P to a Mention after the preview, before confirmation | The set changed, so STALE. The new preview includes it. |
| An Editor resolves, rejects or edits an affected Mention after the preview | Its `row_version` changed, so STALE |
| An Editor deletes or restores an affected collective after the preview | STALE (deleted objects are in the set) |
| An Editor is mid-way linking P when confirmation starts | Confirmation waits on the P lock, then sees the link: STALE |
| An Editor tries to link P while confirmation holds the lock | The Editor waits. After the commit, P is RESTRICTED and the Editor gets the same not-found answer as for a nonexistent Person. If the wait exceeds `lock_timeout`, the Editor gets the archive's generic "busy, try again" message, used for every lock timeout and naming no object. |
| Two Administrators restrict P concurrently | The first is APPLIED. The second fails step 2 (`row_version` and state) and is STALE. |
| Writes on unaffected objects | No interaction |
| Non-administrator reads during confirmation | Each page that combines Person, Mention and collective visibility is assembled in one `REPEATABLE READ READ ONLY` snapshot (or one statement), so it shows entirely the pre-restriction or entirely the post-restriction state. Pre-restriction content was legitimately visible at that moment. |

A Person whose links change constantly could keep making previews stale. That is acceptable at the v1 scale of
concurrent editing. It fails visibly (STALE) rather than applying something unreviewed (C-13).

#### 5.5.8 Operations beyond safe transaction bounds
- **Interactive limit.** Proposed: 1,000 RESTRICT items, or the confirmation exceeding `statement_timeout`
  (proposed 30 s). Within it, confirmation runs in the web request.
- **Above the interactive limit.** Confirmation runs as a **single transaction** inside the Phase 0 background-job
  mechanism, with a larger configured timeout (proposed 10 minutes). It takes the same steps 1–7 and the same lock
  order. The Administrator sees the job's progress and outcome. Link writes to P wait or receive the generic busy
  message while it runs.
- **Never chunked.** Splitting the work into batches would commit intermediate states, either:
  - P RESTRICTED with NORMAL Mentions still targeting it (the C-1 collision leak), or
  - some Mentions restricted while P is still NORMAL and visible (a disclosure pattern).
  The deferred triggers would reject the first of these anyway.
- **Absolute ceiling.** Proposed: 50,000 items. The operation is refused at preview with the counts and a message
  that an owner-approved maintenance procedure is required. This fails visibly; nothing is partially applied.

#### 5.5.9 What the protocol does not do
- **Unrestricting P** is a single-row change. No linked object is unrestricted (SEC-005). An Administrator may
  unrestrict objects one by one, and the invariant refuses any whose candidate, member or anchor is still a
  RESTRICTED Person.
- **Restricting a Mention, collective or proposal directly** (not through a Person) is an ordinary single-object
  Administrator action with no cascade.
- **No disclosure through the operation.** Operations, items, failures and job records are Administrator-only. Their
  Change History entries are hidden from non-administrators like all history about restricted objects.

---

## 6. D4-07 — Vital dates and Phase 6 Events

### 6.1 How Phase 4 meets its relationship tests without vital-event models
- **No vital Event models.** Phase 4 adds no birth, marriage or death Event models and no vital-date fields on
  Person (recommended D4-07 A).
- **What relationships carry.** Every relationship may carry an optional *relationship period* (Historical Dates,
  labelled "period as entered", not "marriage date"). A SPOUSE period is not a Marriage Event and is never
  synchronised with one later.
- **No chronology logic anywhere.** No date comparison exists between Persons, relationships or Records, so no
  warning can be produced from chronology (P-003). That is asserted structurally: no validator compares dates of
  different objects.
- **No relationship inference.** Relationships are entered explicitly, and none is derived from another (§61).

### 6.2 Test status

| Test | Phase 4 status | What Phase 4 verifies | What later phases must verify |
|---|---|---|---|
| P-001 multiple names | **Complete** | Names coexist with provenance; changing the preferred Name keeps the others | Phase 7: S-002 search by alternate Name |
| P-002 parents never married | **Complete for Phase 4 objects** | Two PARENT_CHILD links accepted; no SPOUSE link and no warning | **Phase 6:** no Marriage Event is created or required from shared parentage |
| P-003 child predates marriage | **Partial** | A SPOUSE relationship with period "from 1852" and a child's parentage links coexist with no warning; no cross-object date validation exists | **Phase 6:** Birth Event (1849) and Marriage Event (1852): valid, with no warning from chronology alone |
| P-004 remarriage | **Complete for relationships** | A–B and A–C SPOUSE links; parentage attaches only to the specified Persons | **Phase 6:** Marriage Events never assign children |
| P-005 competing parentage | **Complete for relationships** | Two candidate fathers coexist; no one-parent constraint | **Phase 5:** competing Claims and Evidence coexist (hypotheses) |
| P-006 guardian ≠ parent | **Complete** | GUARDIAN_WARD creates no parentage | — |
| P-007 apprenticeship | **Complete** (MASTER_APPRENTICE type) | No parent, guardian or household link generated | **Phase 6:** an apprentice master who is an Organization role generates no relationship (O-002) |
| P-008 collective reference | **Complete** | §4 | Phase 5: the collective's Source Statement |
| P-009 direct grandparent | **Complete** | GRANDPARENT_GRANDCHILD without an intermediate Person | — |
| P-010 duplicate Persons | **Complete via "resolution"** (D4-09 A) | Coexistence, identity proposals, no automatic merge | Physical merge only if separately approved |
| EV-003 date semantics | **Partial** | Record date, filing date and capture date are distinct objects or fields | **Phase 6:** Event date distinct from all three |
| A-009 no relationship by coincidence | **Complete for Records, Roles, Mentions, collectives** | | **Phase 6:** Events, Property, Organization, household |
| SEC-006 living status | **Complete** | Explicit field, never inferred | Phase 9: privacy policy use |

---

## 7. Newly discovered architectural conflicts

| ID | Conflict | Effect | Proposed resolution (PROPOSED) |
|---|---|---|---|
| **C-1** | The **one-RESOLVED-per-Mention** rule (D4-13) against **hidden-equals-nonexistent** (D3-04) once restricted Persons exist (D4-16) | A non-admin's resolve would either collide with a hidden RESOLVED row (a leak through the error) or supersede an Administrator's restricted decision (a hidden mutation) | Mention restriction plus the invariant in §5.2: NORMAL Mentions never target restricted Persons. Restricting a linked Person restricts its Mentions in the same transaction, after a preview. |
| **C-2** | Rule 4 of §5.2 is a form of propagation, but D3-04 says "no automatic propagation" and SEC-005 is Phase 9 | Needs owner sanction | It is deliberate, previewed and explicit, so not automatic. Owner to confirm (revised D4-16). |
| **C-3** | Policy S requires **several Mentions per Role** | Possible duplicate Mentions for one Role, seen together by Administrators | No uniqueness of Mention per Role. Administrator UI labels them. Normal (non-restricted) workflows reuse the Role's visible Mention. |
| **C-4** | Calendar UNSPECIFIED: assuming Gregorian (say, for dates after 1752 in British-colonial sources) would manufacture certainty | Envelopes wider than "obvious" by the actual Julian/Gregorian difference for that date (computed, not a constant: −1 to 73 days over years 1–9999; 10–13 days for 1500–2099) | Never assume. Keep both readings; the researcher may state the calendar (§2.3). *Revision 2: the earlier "up to 13 days" was wrong.* |
| **C-5** | D-003 calls "1845-1849" a *range*, but sources also use the form for a *period* | Parser choice | The parser proposes BETWEEN. The confirmation screen offers FROM_TO. Never decided silently. |
| **C-6** | §16 "common state progression" against **collective membership**, which has many RESOLVED members and no SUPERSEDED | Framework variant | Accept it as a documented variant with the same states and meanings and different cardinality (§4.3). C-006 is still met for Places. |
| **C-7** | A SPOUSE relationship period and a future Phase 6 Marriage Event date are two places a marriage year can appear | Divergence | Label the period "relationship period as entered", never "marriage date". Phase 6 must not synchronise them. Coexistence is permitted (A-010). |
| **C-8** | Julian Old-Style **year start** (25 March) is absent from §12's list | Without it, year-only Julian dates would gain false precision | Add `year_start` (§2.1). It is required by §12's "never more precision". |
| **C-9** | §13's "physical Place" against counties as Places (D4-18 A) | Wording tension | Document that a JURISDICTIONAL Place's identity is the unit, not a fixed area. §13 already makes identity independent of coordinates. |
| **C-10** | Transitive identity display (P127=P133=P140) could read as an asserted decision | Implicit identity | Show it as navigation only, labelled "linked through". Never stored. Contradictions are flagged proportionately. |
| C-11 (known) | ACCEPTANCE-SPEC PR-008 example "Structure **S001**", while `S` is Source (Phase 3) | Phase 6 prefix | Phase 6 chooses another prefix; spec examples are illustrative. |
| **C-12** (Rev. 2) | Restriction is per object, so restricting a Mention hides it **whole**, including its NORMAL candidates | Non-administrators lose that Mention from *other* Persons' pages | The preview shows this collateral (§5.5.2). The alternative, splitting the Mention, would rewrite source interpretation and is not proposed. |
| **C-13** (Rev. 2) | Strict revalidation (any change means STALE) against a Person whose links change constantly | Confirmation could be repeatedly invalidated | Accepted at v1 scale. It fails visibly and never applies an unreviewed set. |
| **C-14** (Rev. 2) | Year-start widening for UNSPECIFIED needs a cut-off year; 1750 is the British/colonial last 25 March legal year, a **regional** fact | Sources from regions that kept 25 March later would be under-widened after 1750 | A configurable `MARCH_25_LAST_YEAR` (D4-28). An explicit MARCH_25 is accepted for any year. Owner chooses the value. |
| **C-15** (Rev. 2) | BUILD-SPEC §12 lists no year-start styles other than the calendar; non-supported styles (1 March, Easter, 25 December) | Not representable structurally | Kept as the expression or entered as explicit points; never mapped (§2.2). |

---

## 8. Proposed revisions to D4 decisions (all PROPOSED)

These revisions are applied, still marked PROPOSED, in [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md).

| Decision | Revision |
|---|---|
| D4-04 (Rev. 2) | Replace the date-level qualifier with `form` (POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM, TO, UNKNOWN, NOT_INTERPRETED) plus per-point `modifier` (EXACT, ABOUT, ESTIMATED, CALCULATED) and per-point `uncertain`. Add `year_start`. The expression is correctable with history; `interpreted_expression` exposes stale interpretations. Rev. 2: a dual year implies MARCH_25 (contradiction with an explicit JANUARY_1 is refused); any calendar may carry a dual year or MARCH_25; explicit GREGORIAN before 15 Oct 1582 is proleptic with a note; unsupported year-start styles are not mapped (§2.2). |
| D4-05 (Rev. 2) | Calendar-unit envelope stored as Julian Day Numbers (`earliest_jdn`, `latest_jdn`; NULL = open): the convex hull of all readings left open by precision, calendar and year start, computed per date by the Julian and Gregorian algorithms (no constant offset). Modifiers and "?" never move it. What it does and does not represent is fixed in §2.3. Phase 7 tolerance per §2.6, query-time only. |
| D4-06 | Parser grammar and refusals as in §2.5. Confirmation records origin, actor, time and `interpreted_expression`. No background, import or migration parsing. |
| D4-09 | Person-identity proposals with PROPOSED, RESOLVED and REJECTED (no SUPERSEDED). No merge, no transitivity, contradictions flagged proportionately (§3.5). |
| D4-13 | State meanings and transitions as in §3.2–3.3, enforced by trigger. Resolving does not close other proposals. A deleted candidate is shown, not cleared. |
| D4-14 | Schema as in §4.2, including `collective_named_mention`, `count_as_written` and Role-backed collectives. Membership is the documented variant (C-6). |
| D4-16 (Rev. 2) | Policy S. Restriction on Person, Mention, Collective Reference and identity proposal. The NORMAL-never-targets-RESTRICTED invariant. Restricting a linked Person uses a previewed, explicit transaction (C-1, C-2). Several Mentions per Role (C-3). Rev. 2: invariant extended to collective anchors and named Mentions and enforced by deferred commit-time triggers; the full restriction protocol is §5.5. |
| D4-18 | Model A refined: place type category; link `jurisdiction_kind` and `basis`; optional `place_derivation_link`; a documented reading of "physical Place" (C-9). |
| D4-07 | Unchanged recommendation (A). Relationship periods are labelled "period as entered" (C-7). Test status as in §6.2. |
| D4-15 | Unchanged (reuse EDIT_RECORDS). VIEW_RESTRICTED_PEOPLE, Administrator only, is needed under D4-16. |
| D4-25 (new, Rev. 2) | Calendar UNSPECIFIED is never assumed Gregorian or Julian, nor narrowed by era; both readings are kept and the difference is computed for the actual date (C-4). |
| D4-26 (new) | Forms such as "1845-1849" are proposed as BETWEEN, with FROM_TO offered at confirmation (C-5). |
| D4-27 (new, Rev. 2) | Restricting a linked Person follows the §5.5 protocol: persisted preview, single-transaction confirmation with revalidation (any difference means STALE), global lock order, deferred integrity triggers, idempotent retry, a job path that is still one transaction, an absolute ceiling, never chunked. Distinct from prohibited automatic propagation. |
| D4-28 (new, Rev. 2) | `MARCH_25_LAST_YEAR` (proposed 1750) bounds year-start widening for UNSPECIFIED year start (C-14). |
| D4-29 (new, Rev. 2) | Re-proposing a previously rejected candidate (Mention, identity pair or collective member) creates a new attributed row and **requires a rationale**; the earlier rejection is shown, never blocks (§10.2). |

## 9. Proposed updates to the requirement-to-test matrix

These updates are applied, still PROPOSED, in [`PHASE-4-PLAN.md`](PHASE-4-PLAN.md) §9.1a. Revision 2 adds the suites of §10 (CR, RP, CL, RO, HD). The new or changed rows:

| Requirement | Test | Phase 4 coverage |
|---|---|---|
| D-001…D-005 | `HistoricalDateTest`: the worked examples of §2.4 (25 rows with JDNs after Revision 2) as table-driven cases (stored fields, envelope, "never stored as") | Full (D-005 round trip in Phase 8) |
| §12 "never more precision" | `HistoricalDateTest.envelopeNeverNarrowerThanStatedUnit` (property-based over generated dates), `…yearStartWidening`, `…unspecifiedCalendarWidening` | Full |
| A-006 (dates) | `HistoricalDateIT.parserProposalNotStoredUntilConfirmed`, `…noParsingOnMigrationOrImport`, `…correctedExpressionMarksInterpretationStale` | Full |
| C-003…C-005 | `CandidateResolutionIT.transitionsOnlyAsSpecified` (trigger), `…resolveDoesNotCloseOtherProposals`, `…reversalKeepsAttribution` | Full |
| P-010 | `PersonIdentityIT.resolvedIdentityChangesNoData`, `…noTransitiveInference`, `…contradictionsFlaggedNotBlocked`, `…noAutomaticProposalFromMatchingNamesOrDates` | Full via resolution |
| P-008 | `CollectiveReferenceIT.namedIndividualsAreMentions_unnamedAreNotPersons_membersExplicit_rejectedKept_countNotALimit` | Full |
| L-001, L-002 + D4-18 | `PlaceIT.localityChangesCounty`, `…countyBoundaryChangeKeepsIdentity`, `…overlappingAndDisputedJurisdictionsCoexist`, `…historicalAndModernNames`, `MentionIT.ambiguousJurisdictionWordingHasTwoProposals` | Full |
| SEC-004 (Phase 4 paths) | `PeopleRestrictionIT` tests 1–11 (§5.4) | Full |
| P-002, P-003, P-004, EV-003 | as in §6.2 | Status per §6.2 (Partial items named with their completing phase) |

---

## 10. Additional regression-test definitions (Revision 2, PROPOSED)

Each test is an integration test against PostgreSQL (16 and 18) on Windows and Linux, unless marked as a unit test.
Concurrency tests use separate connections synchronised by a barrier, and assert database state afterwards. They
never rely on timing alone.

### 10.1 Concurrent resolution of one Mention (`CandidateResolutionConcurrencyIT`)

| ID | Setup and action | Expected |
|---|---|---|
| CR-1 | Mention M with PROPOSED candidates X and Y. Editors A and B both load M (same `row_version`) and concurrently **Resolve** to X and to Y. | Exactly one commits. The other gets the SEC-008 conflict with no change and is shown the current decision. Exactly one RESOLVED row. Zero SUPERSEDED. One Change History transition. |
| CR-2 | As CR-1, but both resolve to X | One success, one conflict. No duplicate RESOLVED or PROPOSED rows for X. |
| CR-3 | M RESOLVED to X. A resolves to Y (superseding X) while B rejects X | One commits. The other conflicts. The final state is either X SUPERSEDED by Y, or X REJECTED with M Proposed, never both and never a mixture. |
| CR-4 | Two raw SQL transactions, bypassing the service, each insert or update a RESOLVED row for M | The second fails on the partial unique index. The database guarantees one RESOLVED row without the service. |
| CR-5 | A proposes Z on M while B resolves X on M | Proposing bumps M's `row_version`, so one conflicts. Neither is silently lost. |
| CR-6 | A resolves M to P while an Administrator confirms the restriction of P (§5.5) | Either A commits first and the confirmation is STALE, or the confirmation commits first and A gets the not-found answer. No committed state has a NORMAL M targeting a RESTRICTED P. |
| CR-7 | CR-1 for a person-identity proposal pair and for a collective member | The same single-winner result |

### 10.2 Re-proposing a rejected candidate (`CandidateReproposalIT`)

| ID | Setup and action | Expected |
|---|---|---|
| RP-1 | Candidate X on M is REJECTED by A. Anyone tries to change that row to PROPOSED or RESOLVED (service and raw SQL). | Refused by the trigger. The row is byte-identical afterwards. |
| RP-2 | B re-proposes X on M | A **new** row with a new id, `proposed_by` B and `proposed_at` now. A rationale is required (D4-29). The old REJECTED row is unchanged. History shows both rows in order with their own attribution. |
| RP-3 | The re-proposal form, and the new row's display | Shows the earlier rejection (who, when, rationale) proportionately. Never blocks. |
| RP-4 | B re-proposes X while a PROPOSED X row already exists | Refused ("already proposed"). The partial unique index allows one non-terminal row per (Mention, candidate). |
| RP-5 | The re-proposed X is then resolved | M is Resolved. The earlier REJECTED row remains, visible as history. |
| RP-6 | X RESOLVED, then SUPERSEDED by Y, then X proposed again | A new PROPOSED row. The SUPERSEDED row is unchanged. |
| RP-7 | RP-1 to RP-5 for identity proposals (pair P-a, P-b) and collective members | The same results |
| RP-8 | Re-proposal of a candidate who is now RESTRICTED, on a NORMAL Mention | Answered as for a nonexistent Person (the invariant). An Administrator may do it only on a RESTRICTED Mention. |

### 10.3 Restricted and deleted collective references (`CollectiveRestrictionLifecycleIT`)

| ID | Setup and action | Expected |
|---|---|---|
| CL-1 | Delete collective CP0012 (members, named Mentions, history) | Hidden from the default Record view and shown in the deleted view, as for other deleted objects (D3-12). Members, named-Mention links and history are kept. No member row, Person, Mention or relationship changes. |
| CL-2 | Restore CP0012 | Membership, states, attribution and history are identical to before the deletion |
| CL-3 | Membership writes on a deleted collective | Follow the Phase 3 lifecycle rule for deleted objects (D3-12); no write bypasses it |
| CL-4 | A member Person is deleted | Membership is kept and shown as "(deleted)"; nothing is removed |
| CL-5 | Restricted collective, non-administrator | Absent from the Record page, the Person's "member of", counts and lists. CP friendly ID and UUID give the same answer as a nonexistent ID. No history entry or value naming it. Writes naming it get the nonexistent answer. |
| CL-6 | Restricted **and** deleted collective | Non-administrator: absent, including from the deleted view. Administrator: both labels shown. Restore keeps RESTRICTED; unrestricting keeps DELETED. |
| CL-7 | Adding a RESTRICTED member, anchor or named Mention to a NORMAL collective, by any role including Administrator | Refused. Committing it by raw SQL fails at commit (deferred trigger). |
| CL-8 | Unrestricting a collective that still has a RESTRICTED member, anchor or named Mention | Refused |
| CL-9 | Restriction operation for P where P's Mention is the anchor of NORMAL CP0012 | CP0012 is in the preview (closure) and is RESTRICTED on confirmation |
| CL-10 | A restricted collective's `stated_count` and wording | Not shown anywhere to non-administrators. The Record's own text and Role wording, which are Record content, are unchanged. |

### 10.4 Restriction operation protocol (`PersonRestrictionOperationIT`)

| ID | Expected |
|---|---|
| RO-1 | The preview lists the full closure (§5.5.2), including deleted objects, objects on restricted Records and collateral. Nothing visible to others changes. |
| RO-2 | **Atomicity:** a test-only fault hook fails after *n* of *m* updates, for several *n*. Nothing changes, Change History is unchanged and the operation stays PREVIEWED. |
| RO-3 | A link created after the preview makes the operation STALE with `stale_detail`; nothing is applied; a new preview includes the link |
| RO-4 | An affected object modified after the preview makes it STALE |
| RO-5 | A writer holding P `FOR SHARE` delays confirmation, which then goes STALE. Conversely, a writer arriving during confirmation gets the not-found answer after the commit. |
| RO-6 | Two Administrators: one APPLIED, one STALE |
| RO-7 | A double submit is idempotent |
| RO-8 | An expired preview is refused and marked EXPIRED |
| RO-9 | An injected lock timeout or deadlock rolls everything back, leaves the operation PREVIEWED and shows the generic busy message. A retry succeeds. |
| RO-10 | Raw SQL committing a NORMAL Mention with a RESTRICTED candidate fails at commit |
| RO-11 | A non-administrator page assembled during confirmation shows either the full pre-state or the full post-state |
| RO-12 | Above the interactive limit, confirmation runs as one job transaction (the fault hook near the end leaves nothing changed). Above the ceiling, the operation is refused at preview. |
| RO-13 | Unrestricting P changes no linked object |
| RO-14 | One Change History entry per changed object, with `operation_id`, Administrator attribution and the reason. All are hidden from non-administrators. |

### 10.5 Date envelope (`HistoricalDateTest`, unit, additions)

| ID | Expected |
|---|---|
| HD-1 | Every row of §2.4, including the JDNs, as table-driven cases |
| HD-2 | Calendar fixed points of §2.3, cross-checked against `java.time` (Gregorian) and pure-Julian `GregorianCalendar` for every day of 1–9999 at a sampled stride, and for every day of 1500–2100 |
| HD-3 | Property-based: for random valid interpretations, the envelope contains the span of every enumerated reading, and changing a modifier or the uncertain flag never changes the envelope |
| HD-4 | Year start: MARCH_25 legal-year spans; a dual year forces MARCH_25; a dual year with an explicit JANUARY_1 is refused; widening stops after the configured `MARCH_25_LAST_YEAR` |
| HD-5 | Day validity per calendar: 29 Feb 1700 is valid in Julian, invalid in Gregorian, and has a Julian-only reading when the calendar is UNSPECIFIED |
| HD-6 | The integrity scan recomputes envelopes and reports a deliberately corrupted row without repairing it |
