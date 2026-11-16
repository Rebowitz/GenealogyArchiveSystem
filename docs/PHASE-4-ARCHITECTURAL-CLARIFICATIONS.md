# Phase 4 architectural clarifications

**Status: PROPOSED. Planning only. No Phase 4 implementation is authorized.**

| Item | Value |
|---|---|
| Assignment | Phase 4 Architectural Clarification (owner, 2026-11-15) |
| Reviews | [`PHASE-4-PLAN.md`](PHASE-4-PLAN.md) and [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md) at `7a68d32`. Neither is approved. |
| Baseline | `phase-3-accepted` (`c494d6a`) and the accepted Phase 0–3 decisions, particularly D3-04 and the Phase 3 audit corrections GAS-P3-001…010 |
| Outputs | This analysis (§1–§6), newly found conflicts (§7), proposed decision revisions (§8; also applied, still PROPOSED, in the register) and coverage-matrix updates (§9; also applied in the plan) |

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
| Dual year | `dual_year = year + 1`, and only on a point with a year |
| Calendar consistency | A dual year requires `calendar` JULIAN or UNSPECIFIED. `year_start` MARCH_25 requires JULIAN or UNSPECIFIED. |
| Nothing interpreted for UNKNOWN or NOT_INTERPRETED | all point fields NULL |

Each of these is a database CHECK where it is expressible in one row: part nesting, ranges, form/point
presence, dual year. Month length, calendar rules and point ordering are enforced in the service.

### 2.3 Interval bounds without manufactured precision

**Principle.** The envelope is the *smallest interval guaranteed to contain every day the interpretation could
mean, under every reading the stored fields leave open.* It is used to **include** candidates in search, never to
state a date. A wider envelope is less precise, so it is always the safe direction. Approximation is **not**
widened in storage: no fixed "±N years" is invented. Search (Phase 7) applies a separately configured tolerance at
query time and labels such results "approximate".

**Algorithm** (pure, unit-tested):
1. **Unit span of a point.** Year only covers 1 Jan to 31 Dec of the year. Year and month cover the whole month.
   A full date covers that day. The span is computed in the point's own calendar.
2. **Year-start widening.** For JULIAN, or UNSPECIFIED before 1752, with `year_start` UNSPECIFIED and no dual year,
   a year-only or January–March point could be in either legal year. The span becomes the union of the 1 January
   and 25 March readings (for example, Julian "1720" = 1 Jan 1720 to 24 Mar 1721). With `year_start` MARCH_25, the
   legal year runs from 25 Mar. A dual year fixes the historical year, so no widening applies.
3. **Calendar widening.** Each span is converted to JDN. If `calendar` is UNSPECIFIED, the result is the union of
   the Gregorian and the Julian readings of the same written numbers. That is at most 13 days wider, and only in
   the direction the readings differ.
4. **Form.**
   - POINT, FROM and TO use p1's span.
   - BETWEEN and FROM_TO run from p1.earliest to p2.latest.
   - BEFORE: open start, ending at **p1.latest** (inclusive). AFTER: from **p1.earliest** (inclusive), open end.
     The inclusive edge is deliberate: "bef 1847" in a source is not reliably exclusive of 1847, and including a
     possible match is safe while excluding one is not.
   - UNKNOWN and NOT_INTERPRETED: no envelope, so the date is excluded from date filters. It is still found by its
     expression text (S-008-style discovery in Phase 7).
5. **Modifiers and uncertainty** do not move the envelope. They set the derived flags.

### 2.4 Worked examples

For readability the envelope is shown as proleptic-Gregorian calendar dates; the stored values are JDNs. "Matches
1840–1850" is an envelope-overlap search for 1 Jan 1840 to 31 Dec 1850.

| # | Expression as written | Stored interpretation (confirmed) | Envelope (earliest … latest) | Matches 1840–1850? | Never stored as |
|---|---|---|---|---|---|
| 1 | `12 March 1847` | POINT; p1 1847-03-12 EXACT; calendar UNSPECIFIED | 1847-03-12 … 1847-03-24 (Julian reading +12 d) | yes | — |
| 2 | `12 March 1847` (researcher confirms Gregorian) | as #1, calendar GREGORIAN | 1847-03-12 … 1847-03-12 | yes | — |
| 3 | `March 1847` (D-001) | POINT; p1 year 1847, month 3; day NULL | 1847-03-01 … 1847-04-12 | yes | 1847-03-01 as a date |
| 4 | `abt 1847` (D-002) | POINT; p1 1847, ABOUT | 1847-01-01 … 1848-01-12; approximate | yes (tolerance applies at query time) | 1847 EXACT |
| 5 | `bef 1847` (D-003) | BEFORE; p1 1847 | open … 1848-01-12 | yes (possible) | an exact or "between" date |
| 6 | `aft 1847` | AFTER; p1 1847 | 1847-01-01 … open | yes (possible) | — |
| 7 | `1845-1849` (D-003) | BETWEEN; p1 1845; p2 1849 (parser proposes BETWEEN; the researcher may choose FROM_TO, see §7 C-5) | 1845-01-01 … 1850-01-12 | yes | 1847 (midpoint) |
| 8 | `from 1845 to 1849` | FROM_TO; p1 1845; p2 1849 | 1845-01-01 … 1850-01-12 (same envelope, different meaning) | yes | — |
| 9 | `bet. abt 1840 and 1845?` | BETWEEN; p1 1840 ABOUT; p2 1845 uncertain | 1840-01-01 … 1846-01-12; approximate, uncertain | yes | — |
| 10 | `12 Feb 1720/21` (D-005) | POINT; p1 1720-02-12, dual year 1721; calendar JULIAN; year start MARCH_25 | 1721-02-23 … 1721-02-23 | no | 1720-02-12 or 1721-02-12 Gregorian |
| 11 | `1720` in a Julian-era source, year start not stated | POINT; p1 1720; JULIAN; year start UNSPECIFIED | 1720-01-12 … 1721-04-04 | no | 1720-01-01 … 1720-12-31 |
| 12 | `1847?` | POINT; p1 1847, uncertain | 1847-01-01 … 1848-01-12; uncertain | yes | 1847 certain |
| 13 | `the 3d day of March in the year 1803` | parser *proposes* POINT 1803-03-03; saved only on confirmation | 1803-03-03 … 1803-03-15 | no | — |
| 14 | `3/4/1803` | no proposal (day/month order ambiguous); NOT_INTERPRETED unless the researcher interprets | none | not by date | either 4 March or 3 April |
| 15 | `in the fifth year of the reign of King George II` | no proposal (regnal); the researcher may enter a BETWEEN with explicit points | none until interpreted | not by date | a guessed year |
| 16 | `Spring 1847` | no proposal (seasons are not modelled); the researcher may enter BETWEEN March 1847 and May 1847 ABOUT | none until interpreted | not by date | 1847-03-01 |
| 17 | `unknown` (the source says so) | UNKNOWN | none | not by date | — |
| 18 | `from 1840` (residence period) | FROM; p1 1840 | 1840-01-01 … open | yes | — |

Calendar note for #1 and #3: in 1847 the Julian calendar ran 12 days behind the Gregorian, so the same written
numbers read as Julian fall 12 days later on the Gregorian scale.

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
   Person. This is enforced in the service on every write, inside the same transaction that locks the Person row.
3. **Linking a restricted Person.** To connect a document to a restricted Person, an Administrator creates or uses
   a **RESTRICTED Mention** (Role-backed or free) and resolves it. The visible Role keeps its `party_as_written`,
   because that is Record content and not a Person. The restricted Mention is invisible to others.
4. **Restricting a Person who is already linked.** The Administrator sees a **preview** of every NORMAL Mention,
   collective membership, identity proposal and relationship that names the Person. On confirmation, in one
   transaction, those Mentions, collectives and proposals become RESTRICTED. Relationships need no change: their
   visibility is computed from both endpoints. This is a deliberate action with a preview, not automatic
   propagation (D3-04). Lifting a restriction never un-restricts the linked objects automatically (SEC-005).
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
| **C-4** | Calendar UNSPECIFIED: assuming Gregorian (say, for dates after 1752 in British-colonial sources) would manufacture certainty | Envelopes up to 13 days wider than "obvious" | Never assume. Widen the envelope; the researcher may set GREGORIAN explicitly (§2.3). |
| **C-5** | D-003 calls "1845-1849" a *range*, but sources also use the form for a *period* | Parser choice | The parser proposes BETWEEN. The confirmation screen offers FROM_TO. Never decided silently. |
| **C-6** | §16 "common state progression" against **collective membership**, which has many RESOLVED members and no SUPERSEDED | Framework variant | Accept it as a documented variant with the same states and meanings and different cardinality (§4.3). C-006 is still met for Places. |
| **C-7** | A SPOUSE relationship period and a future Phase 6 Marriage Event date are two places a marriage year can appear | Divergence | Label the period "relationship period as entered", never "marriage date". Phase 6 must not synchronise them. Coexistence is permitted (A-010). |
| **C-8** | Julian Old-Style **year start** (25 March) is absent from §12's list | Without it, year-only Julian dates would gain false precision | Add `year_start` (§2.1). It is required by §12's "never more precision". |
| **C-9** | §13's "physical Place" against counties as Places (D4-18 A) | Wording tension | Document that a JURISDICTIONAL Place's identity is the unit, not a fixed area. §13 already makes identity independent of coordinates. |
| **C-10** | Transitive identity display (P127=P133=P140) could read as an asserted decision | Implicit identity | Show it as navigation only, labelled "linked through". Never stored. Contradictions are flagged proportionately. |
| C-11 (known) | ACCEPTANCE-SPEC PR-008 example "Structure **S001**", while `S` is Source (Phase 3) | Phase 6 prefix | Phase 6 chooses another prefix; spec examples are illustrative. |

---

## 8. Proposed revisions to D4 decisions (all PROPOSED)

These revisions are applied, still marked PROPOSED, in [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md).

| Decision | Revision |
|---|---|
| D4-04 | Replace the date-level qualifier with `form` (POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM, TO, UNKNOWN, NOT_INTERPRETED) plus per-point `modifier` (EXACT, ABOUT, ESTIMATED, CALCULATED) and per-point `uncertain`. Add `year_start`. The expression is correctable with history; `interpreted_expression` exposes stale interpretations. |
| D4-05 | Envelope stored as Julian Day Numbers (`earliest_jdn`, `latest_jdn`; NULL = open) using the §2.3 algorithm. Approximation is never widened in storage; search tolerance is applied at query time (Phase 7). |
| D4-06 | Parser grammar and refusals as in §2.5. Confirmation records origin, actor, time and `interpreted_expression`. No background, import or migration parsing. |
| D4-09 | Person-identity proposals with PROPOSED, RESOLVED and REJECTED (no SUPERSEDED). No merge, no transitivity, contradictions flagged proportionately (§3.5). |
| D4-13 | State meanings and transitions as in §3.2–3.3, enforced by trigger. Resolving does not close other proposals. A deleted candidate is shown, not cleared. |
| D4-14 | Schema as in §4.2, including `collective_named_mention`, `count_as_written` and Role-backed collectives. Membership is the documented variant (C-6). |
| D4-16 | Policy S. Restriction on Person, Mention, Collective Reference and identity proposal. The NORMAL-never-targets-RESTRICTED invariant. Restricting a linked Person uses a previewed, explicit transaction (C-1, C-2). Several Mentions per Role (C-3). |
| D4-18 | Model A refined: place type category; link `jurisdiction_kind` and `basis`; optional `place_derivation_link`; a documented reading of "physical Place" (C-9). |
| D4-07 | Unchanged recommendation (A). Relationship periods are labelled "period as entered" (C-7). Test status as in §6.2. |
| D4-15 | Unchanged (reuse EDIT_RECORDS). VIEW_RESTRICTED_PEOPLE, Administrator only, is needed under D4-16. |
| D4-25 (new) | Calendar UNSPECIFIED is never assumed Gregorian or Julian; the envelope widens (C-4). |
| D4-26 (new) | Forms such as "1845-1849" are proposed as BETWEEN, with FROM_TO offered at confirmation (C-5). |

## 9. Proposed updates to the requirement-to-test matrix

These updates are applied, still PROPOSED, in [`PHASE-4-PLAN.md`](PHASE-4-PLAN.md) §9.1. The new or changed rows:

| Requirement | Test | Phase 4 coverage |
|---|---|---|
| D-001…D-005 | `HistoricalDateTest`: the 18 worked examples of §2.4 as table-driven cases (stored fields, envelope, "never stored as") | Full (D-005 round trip in Phase 8) |
| §12 "never more precision" | `HistoricalDateTest.envelopeNeverNarrowerThanStatedUnit` (property-based over generated dates), `…yearStartWidening`, `…unspecifiedCalendarWidening` | Full |
| A-006 (dates) | `HistoricalDateIT.parserProposalNotStoredUntilConfirmed`, `…noParsingOnMigrationOrImport`, `…correctedExpressionMarksInterpretationStale` | Full |
| C-003…C-005 | `CandidateResolutionIT.transitionsOnlyAsSpecified` (trigger), `…resolveDoesNotCloseOtherProposals`, `…reversalKeepsAttribution` | Full |
| P-010 | `PersonIdentityIT.resolvedIdentityChangesNoData`, `…noTransitiveInference`, `…contradictionsFlaggedNotBlocked`, `…noAutomaticProposalFromMatchingNamesOrDates` | Full via resolution |
| P-008 | `CollectiveReferenceIT.namedIndividualsAreMentions_unnamedAreNotPersons_membersExplicit_rejectedKept_countNotALimit` | Full |
| L-001, L-002 + D4-18 | `PlaceIT.localityChangesCounty`, `…countyBoundaryChangeKeepsIdentity`, `…overlappingAndDisputedJurisdictionsCoexist`, `…historicalAndModernNames`, `MentionIT.ambiguousJurisdictionWordingHasTwoProposals` | Full |
| SEC-004 (Phase 4 paths) | `PeopleRestrictionIT` tests 1–11 (§5.4) | Full |
| P-002, P-003, P-004, EV-003 | as in §6.2 | Status per §6.2 (Partial items named with their completing phase) |
