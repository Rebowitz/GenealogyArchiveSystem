# ADR-0012: People, Historical Dates, Places and Candidate Resolution (Phase 4)

## Status
**Prepared for final independent architectural review (2026-11-15).**
- All 30 Phase 4 decisions are approved by the owner, with D4-28 modified and D4-30 = B. Open items U-1…U-3 were
  also resolved by the owner on 2026-11-15. See [`PHASE-4-DECISIONS.md`](../PHASE-4-DECISIONS.md).
- This ADR records the architecture those decisions produce.
- **Phase 4 implementation is not authorized.** It requires this ADR to pass the final independent review and a
  separate owner instruction.
- Merge, formal acceptance and tagging require separate owner approval.

## Context
Governing requirements:
- BUILD-SPEC §8–§16, §21, §43, §56, §60–§62, §65.
- ACCEPTANCE-SPEC:
  - A-001…A-010;
  - P-001…P-010, D-001…D-005, L-001…L-004, C-001…C-006;
  - SEC-002…SEC-008;
  - the Phase 4 parts of EV-003, EV-004, EX-006…EX-008 and UP-004…UP-006.

Phase 4 builds on Phase 3's Archival Records, Record Roles, Sources and Citations (ADR-0011). It must leave room
for:
- Phase 5: Source Statement, Evidence, Claim and Conclusion;
- Phase 6: Event, Organization, Property and Structure, and **county-formation and boundary-change relationships
  (D4-30)**;
- Phase 7: search;
- Phase 8: export, backup and transfer;
- Phase 9: privacy policy and automatic propagation.

Detailed semantics, worked examples and test definitions are in
[`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md`](../PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md) (cited "Cl. §n"). The
work plan is [`PHASE-4-PLAN.md`](../PHASE-4-PLAN.md). Where this ADR and the clarifications differ, the approved
register governs, then this ADR.

## 1. Packages, objects and identity
- **Packages.** `people` (Person, Name, Relationship, identity proposal), `places` (Place, Place Name, jurisdiction
  link), `dates` (Historical Date model, calendar algorithms, envelope, parser), `resolution` (Mention, Candidate
  Resolution, Collective Reference, restriction operation).
  - Services sit under `AccessPolicy`.
  - All SQL lives in `data` repositories, and `web` never depends on `data` (ArchitectureTest).
- **Identity.**
  - UUIDv7 keys, with identity columns made immutable by trigger.
  - No physical delete (`reject_removal`).
  - `row_version` on every correctable row; Change History in the same transaction.
- **Friendly IDs (D4-02).**

  | Prefix | Object | Digits |
  |---|---|---|
  | `P` | Person | 5 |
  | `PL` | Place | 5 |
  | `M` | Mention | 5 |
  | `CP` | Collective Reference | 4 |

  - Series use `NO CYCLE` sequences, so numbers are never reused (A-002).
  - The greedy letter parse is pinned by test: `PL…` is never `P`, and `CP…` is never `CS` or `CT`.
  - `PR` stays reserved for Property.
- **Lifecycle (D4-03).** Person, Place, Mention and Collective Reference are ACTIVE or DELETED (recoverable,
  restore only; D3-12). There is no merged state (D4-09 A). Person Relationships are ACTIVE or WITHDRAWN (D4-08).
- **Explicit tables only.** No EAV, and no polymorphic `(type, id)` target (§10, §60; D4-13).

## 2. Historical Date (D4-04 B, D4-05, D4-06, D4-25, D4-26, D4-28 modified)
- **An owned value.**
  - `historical_date` holds one row per dated field and is never shared between owners.
  - `archival_record` gains nullable `record_date_id` and `filing_date_id`. `record_date_text` and
    `filing_date_text` are untouched and stay authoritative (D3-05, A-004).
- **Representation** (Cl. §2.1):
  - `expression`: the source wording, correctable with history.
  - `interpreted_expression`: a snapshot taken at confirmation.
  - `form`: POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM, TO, UNKNOWN or NOT_INTERPRETED.
  - `calendar`: UNSPECIFIED, GREGORIAN or JULIAN.
  - `year_start`: UNSPECIFIED, JANUARY_1 or MARCH_25.
  - Points p1 and p2, each with nullable year, month and day, a `modifier` (EXACT, ABOUT, ESTIMATED,
    CALCULATED), an `uncertain` flag and a `dual_year`.
  - Origin (RESEARCHER or PARSER_CONFIRMED), with system-set `confirmed_by` and `confirmed_at`.
- **Validation** (Cl. §2.2).
  - **Single-row checks are database CHECKs:** part nesting, ranges, year 1–9999, form/point presence,
    `dual_year = year + 1`, and empty points for UNKNOWN and NOT_INTERPRETED.
  - **Calendar month length, point ordering and dual-year consistency are service rules.** A dual year gives the
    MARCH_25 reading and is refused together with an explicit JANUARY_1.
  - An explicit GREGORIAN date before 15 October 1582 is proleptic and shown with a note.
  - Unsupported year-start styles are never mapped to a supported one.
- **No inferred conventions (D4-25, D4-28).**
  - `calendar` and `year_start` change only when the researcher records a value. Nothing infers them from period,
    jurisdiction, Record, Source or Place.
  - There is **no cut-off year**; the earlier proposed `MARCH_25_LAST_YEAR` was not approved.
  - Jurisdiction-specific, sourced conventions are a possible later-phase enhancement and are **not** part of this
    architecture.
- **Calendar-unit envelope (D4-05; Cl. §2.3).** `earliest_jdn` and `latest_jdn` are Julian Day Numbers; NULL
  means an open end. The calculation:
  1. Enumerate the readings the stored fields leave open:
     - calendar: the stated one, or both;
     - year start: the recorded one; or MARCH_25 when a dual year is present; or both when UNSPECIFIED, in every
       period.
  2. Drop readings whose day does not exist in that calendar.
  3. Convert each reading's unit span to JDN with **that calendar's own algorithm**. There is never a constant
     offset.
  4. Take the minimum and maximum, then apply the form.

  The envelope:
  - is derived, non-authoritative and never edited or displayed as the date;
  - is not moved by modifiers or "?";
  - is checked by an integrity scan that recomputes it and *reports* any mismatch rather than repairing it.

  The calendar algorithms are pinned by test to the 1582 and 1752 changeover days and cross-checked against
  `java.time` (Gregorian) and a pure-Julian `GregorianCalendar`. No hybrid calendar is used.
- **Parser (D4-06, D4-26).**
  - A deterministic English grammar that only *proposes*. It refuses ambiguous numeric dates, regnal years and
    seasons.
  - It always proposes calendar and year start as UNSPECIFIED.
  - "YYYY-YYYY" is proposed as BETWEEN, with FROM_TO offered at confirmation.
  - Nothing is saved until the researcher confirms.
  - No background, import or migration parsing (UP-006).
- **Phase 7 tolerance** is query-time only, configured per modifier, labelled in results and never written back
  (Cl. §2.6).
- **Expected effect, U-4.** Undated conventions widen many envelopes by about a year. This is approved behaviour,
  and the Phase 7 search design must expect it.

## 3. People and relationships (D4-07, D4-08, D4-10, D4-11, D4-17, D4-22, D4-23)
- **`person`**:
  - `display_name_id` (a pointer);
  - `living_status`: UNKNOWN, LIVING or DECEASED, as entered, never inferred (SEC-006);
  - `restriction`: NORMAL or RESTRICTED;
  - lifecycle and note.

  Creating a Person needs nothing (A-003). There is no sex or gender field (D4-22) and no vital-date field
  (D4-07).
- **`person_name`**:
  - `name_as_written` (required, authoritative), optional parts and `name_type`;
  - optional validity dates;
  - provenance: Record, Citation or Mention, plus a note.

  Changing the preferred Name never touches the others (P-001).
- **`person_relationship`**:
  - a type and ordered Persons A and B;
  - `parentage_qualifier` only for parentage types (fixed: UNSPECIFIED, BIOLOGICAL, ADOPTIVE, STEP, FOSTER);
  - an optional **"relationship period as entered"**, never a marriage date and never synchronised with Phase 6
    Events (C-7);
  - provenance, note, and ACTIVE or WITHDRAWN.

  Rules:
  - There is no one-mother or one-father uniqueness (P-005).
  - No relationship is ever derived (§61): not from shared children, marriage, co-occurrence, collective
    membership, identity resolution or dates.
  - There is no chronology validation between objects (P-003).
  - SIBLING has no qualifier.
- **`relationship_type`** (vocabulary): code, label, inverse label, directed, and a category that only decides
  whether a parentage qualifier applies.
- **Vocabulary seeds (D4-11 A).**

  | Vocabulary | Seeds |
  |---|---|
  | Relationship types | PARENT_CHILD, SPOUSE, GUARDIAN_WARD, SIBLING, GRANDPARENT_GRANDCHILD, MASTER_APPRENTICE, HOUSEHOLD_MEMBER |
  | Person name types | BIRTH, MARRIED, NICKNAME, VARIANT |

  Administrators add, relabel and retire values; codes are never deleted or reused (D3-08).

## 4. Places and jurisdictions (D4-18 A, D4-19, D4-20, D4-21, D4-30 B; U-1, U-2)
- **`place`**:
  - optional `place_type`;
  - `approximate_location`, with optional latitude, longitude and precision note (no GIS);
  - lifecycle and note;
  - no restriction (D4-16).

  Identity never derives from name, jurisdiction, coordinates or address (§13). For a jurisdictional Place,
  identity is the administrative unit, not a fixed area (C-9).
- **`place_type`** (vocabulary): code, label, retired, and a `category` of LOCALITY, JURISDICTIONAL or OTHER.
  - The category drives only grouping and lookup filters, never inference.
  - **Seeds (U-1, resolved): COUNTY and STATE**, both JURISDICTIONAL (named by L-002).
  - **`place_type` is optional**, so a Place can be saved without a type (A-003). No administrator setup is needed
    before the first Place.
- **`place_name`**: `name_as_written`, an optional `name_type`, optional validity dates, provenance and note.
  There is a preferred-name pointer on `place`.
  - **`place_name_type`** is an administrator-extensible vocabulary.
  - **Seeds (U-2, resolved): HISTORICAL, CURRENT and VARIANT.** VARIANT means an alternate spelling (§13; L-001).
- **`jurisdiction_link`**:
  - `place_id` lies within `jurisdiction_place_id`;
  - `jurisdiction_kind`: CIVIL, ECCLESIASTICAL, JUDICIAL, MILITARY or OTHER;
  - `basis`: RECOGNISED, CLAIMED, DISPUTED or UNKNOWN (default UNKNOWN);
  - validity dates, provenance and note.

  Rules:
  - No self-link.
  - Cycles across overlapping validity are *warned*, not silently prevented.
  - Overlapping, disputed and contradictory links coexist (A-010).
- **County formation (D4-30 B).**
  - No formation or boundary-change table in Phase 4.
  - Changes are recorded as dated containment links plus a sourced note on the jurisdictional Place.
  - **Notes are never converted automatically**, in Phase 6 or later.
  - Phase 6 introduces the structured relationships.
- **Source jurisdiction (D4-19 A).** `source.jurisdiction_text` is unchanged and unlinked in Phase 4.
- **Finding candidates (D4-21 A).**
  - A case-insensitive *contains* lookup over documented Person and Place Names, alphabetical, with no scoring.
  - Restricted Persons are excluded **before** limits and ordering.

## 5. Mentions and Candidate Resolution (D4-12 B, D4-13 A, D4-24 A, D4-29 A)
- **`mention`**:
  - `mention_kind`: PERSON or PLACE;
  - a required `record_id`;
  - exactly one of `record_role_id` (Role-backed, with wording read from `party_as_written`) or
    `wording_as_written` (free).

  Further rules:
  - **Several Mentions per Role are allowed.**
  - `restriction` is NORMAL or RESTRICTED.
  - No state is stored on the Mention. Its state (Resolved, Proposed or Unresolved) is derived from the visible
    candidate rows.
  - `record_role` is unchanged, and no Mention is created by migration.
- **`candidate_resolution`**:
  - exactly one of `candidate_person_id` or `candidate_place_id`, matching the kind;
  - `state`: PROPOSED, RESOLVED, REJECTED or SUPERSEDED;
  - `proposed_by`/`at`, `resolved_by`/`at` and `closed_by`/`at`, each set once;
  - rationale and `superseded_by`.
- **Integrity.**
  - Partial unique indexes allow **at most one RESOLVED row per Mention** and at most one PROPOSED or RESOLVED row
    per (Mention, candidate).
  - A trigger allows only P→R, P→X, R→X, and R→S (the last only within a Resolve to another candidate). REJECTED
    and SUPERSEDED are terminal.
  - Rows are never deleted.
  - Every candidate write bumps the Mention's `row_version`, so concurrent resolves have a single winner and the
    loser receives the SEC-008 conflict.
- **Meaning** (Cl. §3.2).
  - RESOLVED is a deliberate, attributed display decision. It copies, merges and re-points nothing.
  - Resolving one candidate does not close the others.
  - A deleted candidate is shown as deleted, not cleared.
  - The **only** path to RESOLVED is the explicit Resolve action, including D4-24's explicit "create from Mention"
    (A-006).
- **Re-proposal (D4-29).** A new row with new attribution and a **required rationale**. The earlier rejection is
  shown and never blocks.

## 6. Person identity (D4-09 A)
- **`person_identity_proposal`**:
  - an unordered pair, stored lower UUID first;
  - PROPOSED, RESOLVED or REJECTED, with **no SUPERSEDED**;
  - per-transition attribution and rationale;
  - a `restriction`;
  - at most one non-terminal row per pair.
- **No merge.** Persons always coexist, and RESOLVED changes no data.
- **No transitivity.** None is stored or inferred. Chains are shown only as navigation labelled "linked through".
- **Contradictions are flagged proportionately, never blocked.** Re-proposal follows D4-29.

## 7. Collective references (D4-14 A)
- **`collective_reference`** (`CP`):
  - `record_id`;
  - exactly one of `record_role_id` or `wording_as_written`;
  - optional `anchor_mention_id` and `anchor_phrase_as_written`;
  - optional `stated_count`, with `count_as_written` required when the count is set;
  - restriction, lifecycle and note.
- **`collective_named_mention`**: explicit links from a collective to individually named Mentions.
- **`collective_member`** (collective, Person):
  - PROPOSED, RESOLVED or REJECTED;
  - **several RESOLVED** allowed, no SUPERSEDED (C-6);
  - at most one non-terminal row per pair.
- **Effects.** Membership creates no Person, Name or relationship. The stated count is not a limit; any excess is
  shown as a proportionate note.

## 8. Restriction (D4-15 A, D4-16 A, D4-27 A; U-3)
- **Policy S: complete suppression.** For readers without VIEW_RESTRICTED_PEOPLE (Administrator only):
  - restricted objects, and everything reachable only through them, give answers identical to nonexistent ones on
    every path (Cl. §5.3);
  - counts, lists and limits are computed after filtering.
- **Restrictable objects:** Person, Mention, Collective Reference and identity proposal. Places are not
  restrictable. Relationships and Names follow their Persons.
- **Invariant.**
  - No NORMAL Mention, collective or identity proposal references a RESTRICTED Person, in any row state.
  - No NORMAL collective has a RESTRICTED anchor or named Mention.

  It is enforced in the services **and** by `DEFERRABLE INITIALLY DEFERRED` constraint triggers on every table
  involved, so no transaction can commit a violating state.
- **Restricting a linked Person: the D4-27 protocol** (Cl. §5.5).
  1. **Preview.** A persisted `restriction_operation` with items and their `row_version`s, the closure (§5.5.2:
     including DELETED objects and those on restricted Records) and the collateral it affects.
  2. **Confirm in one transaction.** Lock the operation, then the Person `FOR UPDATE`. Recompute the closure and
     lock each item in global order; any difference makes the operation **STALE** and nothing is applied. Apply
     all changes, Change History and the APPLIED state. The deferred triggers check the result at commit.
  3. **Failures** (lock timeout, deadlock, serialization, statement timeout, connection loss, trigger violation)
     roll back entirely and leave the operation PREVIEWED. Retries re-run all validation, and confirmation is
     idempotent.
  4. **Size.** Never chunked. Above the interactive limit, confirmation runs as **one** transaction in the
     background-job mechanism. Above the ceiling it is refused at preview.

  Unrestricting never cascades (SEC-005). There is no automatic propagation (D3-04); the explicit, previewed
  operation is the only cascade.
- **Global lock order:**
  1. the operation;
  2. Phase 3 objects (Record, Role);
  3. Persons (`FOR SHARE` to link, `FOR UPDATE` to restrict);
  4. Mentions;
  5. collectives;
  6. identity proposals;
  7. child rows.

  Within a level, rows are locked in UUID order. All writers use this order.
- **Consistent reads.** Pages combining these visibilities read one `REPEATABLE READ READ ONLY` snapshot.
- **Configuration (U-3, resolved).** Installation configuration with these defaults:

  | Key | Default | Rule |
  |---|---|---|
  | `people.restriction.previewExpiry` | `PT30M` | > 0 |
  | `people.restriction.lockTimeout` | `PT5S` | > 0 |
  | `people.restriction.interactiveStatementTimeout` | `PT30S` | > 0 |
  | `people.restriction.interactiveItemLimit` | `1000` | ≥ 1 and ≤ `itemCeiling` |
  | `people.restriction.jobTimeout` | `PT10M` | ≥ `interactiveStatementTimeout` |
  | `people.restriction.itemCeiling` | `50000` | ≥ `interactiveItemLimit` and **≤ 50,000** (architectural maximum) |

  - The section is optional and is validated by `check-config` as in ADR-0004. Unknown keys and invalid values are
    errors.
  - **50,000 is the maximum permitted `itemCeiling` under this architecture.** A configured value above it is a
    validation error, not a warning. The ceiling may be lowered, never raised by configuration. Raising it requires
    a separately reviewed and approved revision of this ADR, because it changes the bound on single-transaction
    size and lock duration that the protocol's safety argument relies on.
  - The values are operational. They change no archival meaning, are not archive data, and never travel in
    exports.
  - The archive's generic "busy, try again" message is used for every lock timeout and names no object.
- **History visibility.** A third visibility layer drops whole Change History entries about hidden objects. It
  withholds `P`, `M` and `CP` identifiers and UUIDs of hidden objects from values and reasons. Tokenization is
  extended parse-safely. Operations and their history are Administrator-only.

## 9. Authorization and attribution (D4-15 A)
- **Permissions.**
  - EDIT_RECORDS (Editor) covers Persons, Names, relationships, Places, Mentions, resolutions, collectives and
    identity proposals.
  - MANAGE_VOCABULARIES (Administrator) covers the vocabularies.
  - VIEW_RESTRICTED_PEOPLE (Administrator) covers restricted objects and the restriction protocol.
- **Attribution.** All attribution is system-set (SEC-007). All correctable rows use `row_version` (SEC-008).

## 10. Migrations and compatibility (UP-004…UP-006)

| Migration | Content |
|---|---|
| V0024 | Vocabularies: relationship types, person name types, parentage qualifiers, place types with category, place-name types; seeds per §3 and §4. Also `historical_date`. |
| V0025 | `person`, `person_name`, `person_relationship`, `person_identity_proposal`; series `P` |
| V0026 | `place`, `place_name`, `jurisdiction_link`; series `PL` |
| V0027 | `mention`, `candidate_resolution`, `collective_reference`, `collective_named_mention`, `collective_member` (series `M`, `CP`); `restriction_operation` and items; deferred invariant triggers; `archival_record.record_date_id` and `filing_date_id` |

- **Forward-only, additive and transactional** (ADR-0005). No accepted row is updated or reinterpreted:
  - no date text is parsed;
  - no `party_as_written` becomes a Mention;
  - no note becomes a structured assertion.
- **Tests.** `Phase4SchemaIT` upgrades a populated V0023 database and proves every accepted table is unchanged.
  `Phase3SchemaIT` stays pinned to V0023.

## 11. Data-dictionary drafts for export (EX-006…EX-008; Phase 8 implements)
These meanings must survive export, backup and transfer without reinterpretation:
- **Historical Date.**
  - `expression` is authoritative.
  - The interpretation fields are exported as stored, including UNSPECIFIED calendar and year start.
  - The envelope is exported as *derived*, together with an algorithm-version identifier, and may be recomputed
    by an importer. It is never the date.
- **Candidate Resolution, identity proposals and membership.**
  - States, attribution and rationale for every row, terminal rows included.
  - `superseded_by` links.
  - The derived Mention state is **not** exported as data; it is recomputed.
- **Restriction.**
  - Object restriction values are exported.
  - Restriction operations are exported as administrative history.
  - Policy S applies to any restricted-aware export (Phase 8 and 9 design).
- **Jurisdiction.** Links with kind, basis, dates and provenance. Notes are exported as notes, never as structured
  formation assertions (D4-30).
- **Identifiers.** Friendly IDs and UUIDs, including retired and deleted objects (A-002).

## 12. Scope control
**Not in Phase 4:**
- Events and vital dates, Organization, Property and Structure (Phase 6);
- formation and boundary-change relationships (Phase 6, D4-30);
- Evidence, Claims and Conclusions (Phase 5);
- search, ranking, OCR and bulk import (Phase 7);
- export, backup and transfer (Phase 8);
- privacy policy and automatic propagation (Phase 9);
- sex or gender (D4-22);
- Person merge (D4-09);
- sourced calendar conventions (D4-28, later-phase enhancement);
- F-006, F-007 and BI-007 (still deferred).

## 13. Gates
- **Phase gate (D4-01 A).** Every Full row of plan §9.1 and §9.1a, plus §9.2, on every job of the Windows/Linux ×
  PostgreSQL 16/18 matrix. Each job's results are checked individually.
- **Test suites:**
  - `HistoricalDateTest` HD-1…HD-7;
  - `CandidateResolutionConcurrencyIT` CR-1…CR-7;
  - `CandidateReproposalIT` RP-1…RP-8;
  - `CollectiveRestrictionLifecycleIT` CL-1…CL-11;
  - `PersonRestrictionOperationIT` RO-1…RO-18 (RO-18: configuration bounds, including the 50,000 cap);
  - `PeopleRestrictionIT` 1–11;
  - `ProhibitedInferenceIT` 1–20.
- **Implementation gate.** Final independent architectural review of this ADR and the three Phase 4 documents,
  then a separate owner instruction.

## Consequences
- **Uncertainty is stored, not resolved.** Dates keep every reading they leave open, and identity and membership
  stay explicit researcher decisions.
- **Search is coarser by design.** Undated conventions widen date envelopes (U-4), and Phase 7 must present
  "possible" and "approximate" matches honestly.
- **Restriction is leak-resistant and coherent.** The cost is several Mentions per Role, collateral hiding of a
  restricted Mention's other candidates (C-12), and strict stale-preview handling (C-13).
- **No physical merge.** Duplicate Persons persist until a separately designed merge, if one is ever approved.
- **Phase 6 has inherited obligations.** It must add structured formation and boundary-change relationships
  without converting notes automatically (U-5). It must also keep relationship periods separate from Marriage
  Events.
