# Phase 4 Implementation Plan — People, Dates and Places

**Status: accepted architectural planning baseline (independent review accepted 2026-11-15), aligned with the owner-approved decision register. Planning only; no Phase 4 implementation is authorized.**

| Item | Value |
|---|---|
| Authorization | Phase 4 planning only (owner assignment, 2026-11-15) |
| Accepted baseline | Tag `phase-3-accepted` → commit `c494d6a` (Phase 3 merge, PR #6); `main` = `20d68c1` (adds only CLAUDE.md documentation commits) |
| Branch | `phase-4-plan` from `main` `20d68c1` (planning documents only) |
| Governing order | archival invariants → ACCEPTANCE-SPEC v1.1 → BUILD-SPEC v1.1 → approved Phase 0–3 decisions → accepted implementation → convenience |
| Companion documents | [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md): the owner-decision register, D4-01…D4-30 and U-1…U-3, **approved 2026-11-15** (D4-28 modified, D4-30 = B). [`ADR-0012`](architecture/ADR-0012-people-dates-places.md): the architecture, **accepted — architectural planning, 2026-11-15**. [`PHASE-4-PLAN-REVIEW-CORRECTIONS.md`](PHASE-4-PLAN-REVIEW-CORRECTIONS.md): the review record. [`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md`](PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md): the detailed semantics ("Cl. §n"). |

Where this plan depends on a choice the specifications leave open, it names the approved decision (**D4-nn**).
The consistency review's items are in §11.2: U-1…U-3 are resolved; U-4 and U-5 are notes for Phases 7 and 6.
Approval of the decisions does not authorize implementation.

---

## 1. Scope

BUILD-SPEC §62:

> Phase 4 - People, Dates and Places: Person, Names, relationships, collective references, Historical Date,
> Place, Place Name, Jurisdiction, unresolved Mentions, Candidate Resolution foundation. Gate: Person/date/place
> tests pass, especially marriage-parentage invariants.

| Element | Governing text | Phase 4 content |
|---|---|---|
| Person | §11, §14; A-001…A-003, P-001, P-010, SEC-006, SEC-008 | A historical individual reconstructed progressively. Identity never depends on name, dates, relationships, surname, household or family grouping. Friendly ID `P…` (spec example `P00127`). |
| Person Name | §11, §14; P-001, S-002 (Phase 7) | Unlimited Names, each with type, provenance and context. A preferred display Name never replaces the others. |
| Person Relationship | §15, §61; P-002…P-007, P-009, A-009 | Direct Person-to-Person relationships. Parentage is independent of marriage. Parentage qualifiers. Competing parentage may coexist. Guardian, apprentice and household are not parentage. A direct grandparent link needs no intermediate Person. |
| Collective Person Reference | §11, §14; P-008 | A group of people preserved as written ("the children of my deceased son John") without creating individual Persons. |
| Historical Date | §12; A-004, A-005, D-001…D-005, EV-003, EX-007 | Source expression kept separately from a structured interpretation. Supports exact, partial, approximate, before, after, range, uncertain, unknown, Julian, Gregorian and dual dates. Never more precision than supplied. Conflicting dates coexist. Carries interval bounds for later interval search. |
| Historical Place, Place Name, Jurisdiction | §11, §13; L-001…L-003 | Place identity independent of name, jurisdiction, coordinates and address. Historical names and variants. Approximate location. Time-dependent jurisdiction. Source wording preserved. |
| Mention | §11, §16; C-001, L-003, S-008 (Phase 7) | Source wording referring to a Person or Place. It may stay unresolved indefinitely and never requires creating a Person or Place. |
| Candidate Resolution foundation | §16; A-006, C-001…C-006, P-010 | Unresolved → Proposed → Resolved. Multiple candidates. Rejected and superseded resolutions stay recorded. Proposed never silently becomes Resolved. One framework for Persons and Places (C-006). |
| Record Role links | D3-01 (Phase 3) | A Phase 3 Record Role may refer to a Person through a Mention. The party as written is preserved. |
| Date interpretation of Phase 3 fields | D3-05 (Phase 3), V0022 comment | Optional structured interpretation alongside `record_date_text` and `filing_date_text`, never replacing them. |

### Explicit exclusions (later phases per §62)

| Later phase | Excluded from Phase 4 | Phase 4 obligation |
|---|---|---|
| **Phase 5** | Source Statement, Evidence, Claim, Conclusion, Expected/Absent Evidence, and **evidence-linked Candidate Resolution history** | Phase 4 relationships, Names and resolutions are researcher-entered, interpretive information with attribution. Phase 5 attaches Evidence to them and never rewrites them (D4-08). |
| **Phase 6** | Event (birth, marriage, death, migration), Event Role, Organization, Property, Structure, DNA Test, Research objects, Collections, Tags; **structured county-formation and boundary-change relationships (D4-30 B)** | No vital-event fields that would pre-empt the Event model (D4-07). Place must stay a separate concept from Property and Structure (L-004). Jurisdiction change is kept as dated containment links plus sourced notes; notes are never converted automatically (U-5). |
| **Phase 7** | Global search, fuzzy search, OCR discovery, import templates, preview, bulk import (BI-003…BI-006), bulk operations including BI-007 | A simple find-by-documented-name lookup to choose candidates (D4-21). It never ranks, matches or resolves anything. |
| **Phase 8** | Backup, restore, Complete Archive Export, Transfer Packages (EX-007, EX-008, TP-003) | The data model must export these meanings faithfully: date form, per-point precision and modifiers, calendar, year start, Proposed vs Resolved, unresolved Mentions, retired IDs. The data dictionary entries are drafted with the ADR. |
| **Phase 9** | Comprehensive restriction management, automatic restriction propagation (SEC-005), living-person policy | Phase 4 objects follow the Phase 3 restriction pattern so nothing leaks (D4-15, D4-16). The only cascade in Phase 4 is the explicit, previewed D4-27 operation. |
| **Later phase, unassigned** (D4-28) | Jurisdiction-specific, sourced calendar and year-start conventions | None. Phase 4 has no convention table and infers nothing (Cl. §2.3). |
| **Still deferred** | F-006 and F-007 (File consolidation, P2-G, D3-03); BI-007 (bulk reclassification, D3-11) | Not built and not claimed. |

### Not changed in Phase 4
- V0001–V0023 and all accepted Phase 0–3 behaviour.
- Preservation originals: no Phase 4 operation reads or writes File bytes, names or paths.
- Existing text fields stay authoritative as written: `record_role.party_as_written`, `archival_record.record_date_text`
  and `filing_date_text`, and `source.jurisdiction_text`.

---

## 2. Existing interfaces Phase 4 extends

| Accepted item | Where | Phase 4 extension |
|---|---|---|
| `record_role` (role code, `party_as_written`, note) | V0022; `RecordService` roles; `record.html` | Optional link to a Person Mention (D4-12). `party_as_written` is untouched and stays the authority for wording. **"Remove role" becomes "Withdraw role", with a new "Restore role"; Roles are never deleted again** (GAS-P4-PLAN-001 C; Cl. §11). |
| `archival_record.record_date_text`, `filing_date_text` | V0022; `RecordService`; `record.html` | Optional structured Historical Date interpretation beside each (D4-05). No automatic parsing of existing rows (D4-06). |
| `source.jurisdiction_text` | V0021; `CatalogService` | None in Phase 4 (D4-19 A); the text stays as written. |
| `friendly_id_series` and `FriendlyIdSeriesRepository` | V0007 | New series `P`, `PL`, `M`, `CP` (D4-02 A). |
| Vocabulary pattern: code, label, retired; never deleted | V0021; `VocabularyService`; `/vocabularies` | Name types, relationship types, parentage qualifiers and place types use the same pattern (D4-11). |
| Change History and history visibility | V0004; `ChangeRecorder`; `RecordsHistoryVisibility`, `RestrictedFileHistory` | New object types and a third visibility layer for restricted Persons, Mentions, collectives, identity proposals and restriction operations (D4-16). |
| `AccessPolicy`, `Permission`, `Role` | `access` | Reuse EDIT_RECORDS; add VIEW_RESTRICTED_PEOPLE (Administrator only) (D4-15 A). Vocabulary management stays with MANAGE_VOCABULARIES. |
| Restriction pattern | `Restriction`; `RecordAccess`; ADR-0011 §8 | Policy S (D4-16): NORMAL/RESTRICTED on Person, Mention, collective and identity proposal, with the commit-time invariant; relationships and Names follow their Persons. |
| Optimistic concurrency, identity triggers | `row_version`; `reject_identity_change`, `reject_removal` | Same on every new table. |
| Reference Acceptance Archive | `src/test/resources/reference-archive/` | New synthetic fixtures for people, dates and places (§9). |
| `FileIdentifiers.referencesIn` and `RecordAccess.friendlyNumber` | history tokenization | Extended to the new prefixes, with parse-safety tests (D4-02). |

---

## 3. Architecture

- **Structure.** The modular monolith is unchanged. A new `people` package holds Person, Name, Relationship and
  Collective Reference; a `places` package holds Place, Place Name and Jurisdiction; `dates` holds the Historical
  Date value model, its parser and validation; `resolution` holds Mention and Candidate Resolution. Each package
  has services under `AccessPolicy`, with all SQL in `data` repositories (ArchitectureTest).
- **No new infrastructure.** PostgreSQL only. No graph database, no search engine, no GIS. Interval bounds are
  plain integer (JDN) columns with B-tree indexes (D4-05).
- **Explicit tables, never EAV (§10, §60).** Every concept has its own table and typed foreign keys. Candidate
  Resolution uses typed, nullable target columns with a CHECK that exactly one is set, not a free-form
  `(entity_type, entity_id)` pair (D4-13).
- **ADR-0012 (accepted — architectural planning, 2026-11-15).** [`ADR-0012`](architecture/ADR-0012-people-dates-places.md):
  People, Dates, Places and Candidate Resolution, written from the approved register in the same form as ADR-0011,
  including data-dictionary drafts for the Phase 8 export. It is the governing Phase 4 planning baseline. Any
  substantive architectural change requires a separate review.

---

## 4. Data model (approved decisions; migrations V0024 onward)

Every table follows the accepted conventions:
- UUIDv7 primary key; a friendly number where the object type has a series;
- `created_at` and `created_by`, never typed by the user (SEC-007);
- `row_version` for optimistic concurrency (SEC-008);
- an identity-immutability trigger;
- no physical delete or truncate of durable objects (`reject_removal`);
- Change History in the same transaction.

This section reflects the approved register (2026-11-15). Detailed semantics are in
[`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md`](PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md), cited as "Cl. §n".

### 4.1 Historical Date (`historical_date`): D4-04 (B), D4-05, D4-06, D4-25, D4-26, D4-28 (modified)

An owned value object: one row per dated field, never shared between owners.

| Column | Meaning |
|---|---|
| `expression` | The source wording, exactly as entered. Required when the date has any content (A-004). Correctable with history. |
| `interpreted_expression` | Snapshot of `expression` at confirmation. A mismatch marks the interpretation "confirmed against earlier wording" (Cl. §2.5). |
| `form` | POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM, TO, UNKNOWN or NOT_INTERPRETED |
| `calendar` | UNSPECIFIED, GREGORIAN or JULIAN. UNSPECIFIED is never assumed or narrowed by era (D4-25). |
| `year_start` | UNSPECIFIED, JANUARY_1 or MARCH_25. Set only by the researcher; never inferred from period, jurisdiction, Record, Source or Place (D4-28). |
| `p1_year`, `p1_month`, `p1_day`, `p1_modifier`, `p1_uncertain`, `p1_dual_year` | First (or only) point. `modifier` is EXACT, ABOUT, ESTIMATED or CALCULATED. Precision is exactly the parts present (D-001). |
| `p2_…` (same columns) | Second point, for BETWEEN and FROM_TO only |
| `interpretation_origin` | RESEARCHER or PARSER_CONFIRMED (D4-06) |
| `confirmed_by`, `confirmed_at` | System-set (SEC-007) |
| `earliest_jdn`, `latest_jdn` | The derived **calendar-unit envelope** (Cl. §2.3): the convex hull of every reading left open by precision, calendar and year start, as Julian Day Numbers; NULL = open. Computed per date with the Julian and Gregorian algorithms. Never moved by modifiers or "?". Non-authoritative, never edited or displayed as the date. |
| `approximate`, `uncertain_any` | Derived flags |

**Validation** (Cl. §2.2):
- **Database CHECKs** where expressible in one row: part nesting; month and day ranges; year 1–9999; form/point
  presence; `dual_year = year + 1`; no interpretation for UNKNOWN or NOT_INTERPRETED.
- **Service rules:**
  - month length in the stated calendar, with the union of valid readings for UNSPECIFIED;
  - point ordering;
  - a dual year gives the MARCH_25 reading, and is refused together with an explicit JANUARY_1;
  - an explicit pre-1582 GREGORIAN is proleptic and noted;
  - unsupported year-start styles are never mapped.

**Envelope algorithm** (Cl. §2.3, approved D4-28):
- Enumerate the readings:
  - calendar: {stated}, or {GREGORIAN, JULIAN};
  - year start: {recorded}, or {MARCH_25} when a dual year is present, or {JANUARY_1, MARCH_25} when UNSPECIFIED.
    This applies **in every period, with no cut-off year**.
- Drop readings whose day does not exist.
- Take each reading's unit span in its own calendar, convert it to JDN, and take the minimum and maximum.
- Apply the form: BEFORE and TO run from an open start to p1.latest; AFTER and FROM run from p1.earliest to an open
  end; BETWEEN and FROM_TO run from p1.earliest to p2.latest (FROM and TO per owner decision GAS-P4-001).

**Phase 7 tolerance** is query-time only, configured per modifier, and never written back (Cl. §2.6).

Conflicting dates coexist because dates are owned by separate statements and are never unique per Person or Record
(D-004, A-010).

### 4.2 People
- **`person`** (`P`)
  - `display_name_id` (FK → `person_name`, nullable)
  - `living_status`: UNKNOWN default, LIVING or DECEASED, as entered and never inferred (D4-17)
  - `restriction`: NORMAL or RESTRICTED (D4-16)
  - `lifecycle`: ACTIVE or DELETED (D4-03; no MERGED_INTO, since D4-09 is A)
  - `note`
- **`person_name`**
  - `person_id`
  - `name_as_written` (required)
  - optional structured parts: given, surname, prefix, suffix (D4-10)
  - `name_type` (vocabulary; seeds BIRTH, MARRIED, NICKNAME, VARIANT; D4-11)
  - optional valid-from / valid-to Historical Dates
  - provenance: optional Record, Citation and Mention references plus a note (D4-10)
  - `sequence_number`, lifecycle

  A preferred display Name is a pointer on `person`. Changing it never touches the other Names (P-001).
- **`person_relationship`**
  - `relationship_type` (vocabulary; D4-11)
  - `person_a_id` and `person_b_id`, ordered by the type's direction (for PARENT_CHILD, A is the parent)
  - `parentage_qualifier`, only for parentage types: UNSPECIFIED, BIOLOGICAL, ADOPTIVE, STEP, FOSTER (§15; fixed)
  - an optional "relationship period as entered" (start and end Historical Dates). It is never called a marriage
    date and never synchronised with Phase 6 Events (D4-07).
  - provenance (Record, Citation) and note
  - lifecycle: ACTIVE or WITHDRAWN (D4-08)

  CHECKs:
  - a Person never relates to itself;
  - the qualifier is present exactly for parentage types.

  **No uniqueness on "one mother/one father"** (P-005). Competing relationships coexist. A relationship is visible
  only if both Persons are visible (D4-16).
- **`relationship_type`** (vocabulary): `code`, `label`, `inverse_label`, `directed`, and `category` (PARENTAGE,
  SPOUSE, SIBLING, GUARDIANSHIP, DIRECT_ANCESTRY, SOCIAL or OTHER). The category decides only whether
  `parentage_qualifier` applies. It never triggers inference (§61). SIBLING has no qualifier (D4-23).
- **`person_identity_proposal`** (D4-09)
  - an unordered pair of Persons (stored canonically, lower UUID first)
  - `state`: PROPOSED, RESOLVED or REJECTED (no SUPERSEDED)
  - rationale; transition attribution set once
  - `restriction`: NORMAL or RESTRICTED (D4-16)
  - at most one non-terminal row per pair
  - re-proposal after rejection is a new row with a required rationale (D4-29)

  It never merges, re-points or infers transitivity (Cl. §3.5).

### 4.3 Places (D4-18 A with refinements; D4-30 B)
- **`place`** (`PL`)
  - `place_type`, **optional** (vocabulary with `category`: LOCALITY, JURISDICTIONAL or OTHER; seeds COUNTY and STATE,
    both JURISDICTIONAL; U-1)
  - `approximate_location` (text), with optional latitude, longitude and precision note (D4-20)
  - lifecycle, note
  - no restriction in Phase 4 (D4-16)

  Identity never derives from name, jurisdiction or coordinates (§13). For a jurisdictional Place, identity is the
  administrative unit, not a fixed area (C-9).
- **`place_name`**
  - `place_id`, `name_as_written`, optional `name_type` (vocabulary `place_name_type`, seeds HISTORICAL, CURRENT,
    VARIANT; U-2)
  - optional valid-from / valid-to Historical Dates
  - provenance and note
  - preferred-name pointer on `place`, as for Person
- **`jurisdiction_link`**
  - `place_id` lies within `jurisdiction_place_id`
  - `jurisdiction_kind`: CIVIL, ECCLESIASTICAL, JUDICIAL, MILITARY or OTHER
  - `basis`: RECOGNISED, CLAIMED, DISPUTED or UNKNOWN (default UNKNOWN)
  - valid-from / valid-to Historical Dates
  - provenance and note

  CHECKs and rules:
  - no self-link;
  - cycles across overlapping validity periods are reported as warnings by the service (Cl. §1.4).

  Overlapping, disputed or contradictory links coexist (A-010). A county change is a new dated link; the Place's
  identity is unchanged (L-002).
- **No `place_derivation_link` in Phase 4** (D4-30 B). County formation and boundary changes are recorded as dated
  containment links plus sourced notes on the jurisdictional Place. Structured formation and boundary-change
  relationships are Phase 6. Notes are never converted automatically.

### 4.4 Mentions, Candidate Resolution, collectives and restriction operations
- **`mention`** (`M`; D4-12 B, D4-13, D4-16)
  - `mention_kind`: PERSON or PLACE
  - `record_id` anchor
  - either `record_role_id` (Role-backed; wording is the Role's `party_as_written`) or `wording_as_written` (free).
    CHECK: exactly one.
  - **several Mentions per Role are allowed** (C-3)
  - `restriction`: NORMAL or RESTRICTED
  - context note, lifecycle
  - **No resolution state is stored.** It is derived from the visible candidate rows: Resolved, Proposed or
    Unresolved (Cl. §3.2).
- **`candidate_resolution`** (D4-13 A with refinements)
  - `mention_id`
  - exactly one of `candidate_person_id` or `candidate_place_id`, matching `mention_kind`
  - `state`: PROPOSED, RESOLVED, REJECTED or SUPERSEDED
  - `proposed_by`/`at`, `resolved_by`/`at`, `closed_by`/`at`: each set once
  - rationale; `superseded_by` (self-FK)

  Constraints:
  - partial unique index: **at most one RESOLVED per Mention**;
  - partial unique index: at most one PROPOSED or RESOLVED row per (Mention, candidate);
  - a trigger allows only P→R, P→X, R→X, and R→S as part of Resolve; rows are never deleted;
  - every write bumps the Mention's `row_version`;
  - there is no code path from PROPOSED to RESOLVED except the explicit Resolve action (A-006);
  - re-proposing a rejected candidate is a new row with a required rationale (D4-29).
- **`collective_reference`** (`CP`; D4-14 A with refinements)
  - `record_id`
  - either `record_role_id` or `wording_as_written` (exactly one)
  - optional `anchor_mention_id` and `anchor_phrase_as_written`
  - optional `stated_count` (with `count_as_written`, required when the count is set)
  - `restriction`, lifecycle, note
- **`collective_named_mention`** (`collective_id`, `mention_id`): explicit links only.
- **`collective_member`** (`collective_id`, `person_id`)
  - `state`: PROPOSED, RESOLVED or REJECTED; several RESOLVED allowed; no SUPERSEDED
  - attribution per transition
  - at most one non-terminal row per (collective, Person)

  Membership creates nothing else, and the stated count is not a limit (Cl. §4).
- **Restriction invariant** (D4-16): no NORMAL Mention, collective or identity proposal references a RESTRICTED
  Person (in any row state), and no NORMAL collective has a RESTRICTED anchor or named Mention. Enforced by services
  and by `DEFERRABLE INITIALLY DEFERRED` constraint triggers on every table involved (Cl. §5.5.5 step 6).
- **`restriction_operation`** and **`restriction_operation_item`** (D4-27): the persisted preview and outcome of
  restricting a linked Person (Cl. §5.5.1). Administrator-only, never deleted.

### 4.5 Extensions to accepted tables (additive only)
- `record_role`: linkage goes from `mention.record_role_id` (D4-12 B). Additively, `lifecycle` (ACTIVE or WITHDRAWN,
  default ACTIVE, no row rewrite), `withdrawn_at`, `withdrawn_by`, and a `BEFORE DELETE` `reject_removal()` trigger
  (GAS-P4-PLAN-001 C; Cl. §11.1).
- `archival_record`: `record_date_id` and `filing_date_id`, nullable FKs to `historical_date`. The text columns are
  untouched and authoritative (D4-05).
- `source`: none (D4-19 A).

### 4.6 Friendly-ID prefix compatibility (D4-02)
Prefixes already in use: F, R, RP, S, CS, CT.

| Object | Prefix (D4-02 A) | Notes |
|---|---|---|
| Person | `P`, 5 digits | The spec's own example is `P00127`. |
| Place | `PL`, 5 digits | |
| Mention | `M`, 5 digits | |
| Collective Reference | `CP`, 4 digits | |

The same checks as D3-07 apply:
- 1–4 upper-case letters;
- unique in `friendly_id_series`;
- greedy letter parse: `PL00001` is never `P`, and `CP0001` is never `CS` or `CT`.

`PR` stays reserved for Property (spec example `PR0041`). A unit test pins the parse and history-tokenization
cases.

---

## 5. Application services and invariants

| Service | Responsibilities | Invariants enforced (service and DB where safe, §65) |
|---|---|---|
| `HistoricalDates` (pure) | Validate an interpretation (Cl. §2.2); compute the calendar-unit envelope with the Julian and Gregorian JDN algorithms (Cl. §2.3); a parser that only **proposes** (Cl. §2.5) | Never adds precision. Forms and per-point modifiers stay distinct. Calendar and year start are never assumed or inferred (D4-25, D4-28); UNSPECIFIED keeps every reading in every period. A dual year gives the MARCH_25 reading. The parser always proposes `year_start` UNSPECIFIED. An unparseable expression stays NOT_INTERPRETED and is valid. |
| `PersonService` | Create, correct and delete or restore a Person; Names; preferred Name; living status | Creating a Person needs nothing (A-003). Changing the preferred Name never deletes other Names (P-001). Living status is never derived (SEC-006). |
| `RelationshipService` | Add, correct and withdraw relationships | Adding SPOUSE never creates parentage. Adding parentage never creates SPOUSE, sibling, household or Event. No chronology validation between relationships (P-003). No count limit on parents (P-005). No intermediate Persons (P-009). No relationship from Role or Record co-occurrence (A-009). |
| `PlaceService` | Places, Place Names, jurisdiction links (kind, basis, dates, provenance) | Renames and jurisdiction changes never change Place identity (L-001, L-002). Overlapping, disputed and contradictory links coexist. Cycles are warned, not silently prevented. No formation links (D4-30). |
| `MentionService` | Create Mentions (free or Role-backed) on a Record | Saving a Mention never requires a Person or Place (C-001, L-003). Wording stays as written. |
| `CandidateResolutionService` | Propose, resolve, reject; supersede only within Resolve; re-propose | Transitions per Cl. §3.3, trigger-backed. Multiple PROPOSED (C-002). RESOLVED only through Resolve (C-004, A-006). Resolving does not close other proposals. Rejected and superseded rows are kept (C-005). Re-proposal is a new row with a rationale (D4-29). Concurrent resolves have a single winner; the loser gets SEC-008. Place targets use the same code (C-006). |
| `CollectiveReferenceService` | Collective references, named-Mention links and explicit members | Never creates Persons or relationships (P-008). The stated count is not a limit. |
| `PersonIdentityService` | Person-identity proposals (D4-09) | PROPOSED, RESOLVED or REJECTED; no merge, no data change, no transitivity. Contradictions are flagged. |
| `PersonRestrictionService` | Restrict a linked Person (D4-27), restrict or unrestrict single objects | The protocol of Cl. §5.5. The commit-time invariant is never broken; nothing is chunked; nothing cascades on unrestrict. |

**Lifecycle.**
- Persons, Places, Mentions and Collective References are ACTIVE or DELETED (recoverable), as for Records (D3-12).
  There is no MERGED_INTO (D4-09 A).
- A deleted object can only be restored.
- Identifiers are never reused (A-002).

---

## 6. Authorization, attribution, concurrency, restriction (D4-15, D4-16)

- **Roles.** Viewer reads. Editor creates and changes people, names, relationships, places, mentions and
  resolutions. Administrator also manages vocabularies (MANAGE_VOCABULARIES, existing) and restricted Persons.
- **Attribution and concurrency.** Every change is attributed automatically (SEC-007) and protected by
  `row_version` (SEC-008, with an explicit P00127 stale-save test).
- **Visibility derives from what a row connects:**
  - A Mention, collective or identity proposal is visible only if it is NORMAL (or the reader may see restricted
    people) and its Record is visible (D3-04).
  - A relationship or Name is visible only if every Person it names is visible.
  - By the invariant, every candidate and member row of a visible Mention or collective names only visible
    Persons. Counts are computed after filtering (Cl. §5.3).
  - Hidden and nonexistent IDs give identical answers, and history entries about hidden objects are dropped whole.
    This extends the Phase 3 traversal tests to the new paths, including guessed `P…`, `PL…` and `M…` IDs.
- **Restriction (D4-16 A, policy S).** Restriction applies to Person, Mention, collective and identity proposal.
  Only Administrators set it or see restricted objects. Restricting an already-linked Person uses the explicit,
  previewed, single-transaction protocol (D4-27, Cl. §5.5): global lock order, revalidation, idempotent retry, never
  chunked. There is no automatic propagation, and unrestricting never cascades. The living-person policy and
  automatic propagation (SEC-005) stay in Phase 9.
- **Consistent reads.** Pages combining Person, Mention and collective visibility read one `REPEATABLE READ READ
  ONLY` snapshot (Cl. §5.5.7).

---

## 7. Browser workflows (list-first, UI-001…UI-006)

1. **People list.** One line per Person: preferred Name, other Names in brief, living status if known, `P` ID.
   Filter by any documented Name (D4-21), a simple contains match with no ranking. Minimal create needs nothing
   (A-003).
2. **Person page.** Names (add, correct, set preferred, each with type, dates and provenance); relationships grouped
   by type with their qualifiers and dates; "Mentioned in" (resolved and proposed Mentions, labelled distinctly);
   history, revealed progressively.
3. **Add relationship.** Choose the type, the other Person (by name lookup or `P` ID), the parentage qualifier
   where it applies, optional dates and provenance. It never offers to add a spouse because a child is shared.
4. **Places list and page.** Names over time, jurisdiction links over time (as a dated list), approximate
   location, "Mentioned in".
5. **Record page additions.**
   - Active Roles are listed as in Phase 3, with "Withdraw role" in place of "Remove role". Withdrawn Roles are in a
     collapsed "Withdrawn roles" group with "Restore role"; their visible Mentions are labelled "role withdrawn"
     (Cl. §11.3).
   - Each Role shows its party as written and, if linked, the Mention's state: "unresolved", "proposed: P00127,
     P00133" (visually distinct) or "resolved: P00127".
   - "Add Mention" holds free wording, a Person or Place kind and a note.
   - Collective references.
   - A Historical Date editor next to the record and filing dates, showing the expression as written above the
     interpretation.
6. **Resolve a Mention.** Lists the current candidates by state. The actions are Propose candidate, Resolve (an
   explicit confirmation with an optional rationale; resolving to another candidate shows which decision it
   supersedes), Reject and Re-propose (with the earlier rejection shown and a rationale required, D4-29). "Create a new Person from this
   Mention" is a separate deliberate action (C-001, BI-006 semantics), never a default.
7. **Historical Date editor.** The expression field is always first. An optional "interpret" step shows the
   parser's proposal (D4-06), which the researcher confirms or edits. Form, precision, modifiers, calendar and year
   start are shown in words ("about March 1847, month precision, calendar not stated, year start not stated").
   Calendar and year start default to UNSPECIFIED and are changed only by the researcher (D4-25, D4-28). The screen
   shows how a recorded convention would narrow the envelope.
8. **Vocabularies page.** Adds name types, relationship types, parentage qualifiers (fixed by spec, D4-11) and
   place types with their category, and place-name types (seeds per U-1, U-2).
9. **Restrict Person (Administrator).** Reason, then a preview of the affected set and collateral, then Confirm. The
   page shows STALE, EXPIRED and busy outcomes plainly (Cl. §5.5).

---

## 8. Migrations, upgrade compatibility and failure handling

| Migration | Content |
|---|---|
| V0024 | Vocabularies (person name types, relationship types with category, parentage qualifiers, place types with category, place-name types; spec-named seeds only, D4-11; place types COUNTY and STATE per U-1; place-name types HISTORICAL, CURRENT, VARIANT per U-2) and `historical_date` (§4.1) |
| V0025 | **WP1 audit correction WP1-R2:** NULL-safe Historical Date point constraints (forward-only; V0024 unchanged). Migration numbers from here on are one higher than in the accepted plan; nothing else changes. |
| V0026 | `person`, `person_name`, `person_relationship`, `person_identity_proposal`; series `P` |
| V0027 | `place`, `place_name`, `jurisdiction_link` (kind, basis); series `PL`. No formation table (D4-30). |
| V0028 | `mention`, `candidate_resolution`, `collective_reference`, `collective_named_mention`, `collective_member` (series `M`, `CP`); `restriction_operation` and items; deferred restriction-invariant triggers; `archival_record.record_date_id` and `filing_date_id`; `record_role` lifecycle columns and `BEFORE DELETE` trigger (GAS-P4-PLAN-001 C) |

- **Forward-only and additive.** Migrations only add tables, columns, nullable foreign keys, triggers and
  vocabulary seeds. **No accepted row is updated or reinterpreted** (UP-006):
  - existing date text is not parsed (D4-06);
  - existing `party_as_written` is not turned into Mentions automatically (D4-12).
- **Transactional.** Each migration is one PostgreSQL transaction, as accepted in ADR-0005. A failure leaves the
  database at the previous version. `MigrationFailureIT` (UP-005) keeps covering the mechanism, and
  `PostMigrationValidator` runs after upgrade.
- **Pre-upgrade checkpoint (§56).** Phase 4 is non-destructive, so a transaction rollback is the recovery path. The
  installation guide keeps the existing instruction to take a PostgreSQL backup before upgrading; the
  backup/restore product feature remains Phase 8.
- **Upgrade test (`Phase4SchemaIT`).** A V0023 database with Records, Roles (with and without a party as written),
  Citations, grouping runs, applied worksheet rows, Files, a tombstone and a quarantine object upgrades to V0028.
  Every accepted table snapshot is unchanged and the new tables are empty.
- **Schema pinning.** `Phase3SchemaIT` is pinned to V0023, following the accepted pattern.
- **Rollback of the feature itself.** No Phase 4 data is written by migration, so declining Phase 4 means restoring
  the pre-upgrade backup. That is documented, not automated.

---

## 9. Tests

### 9.1 Requirement-to-test coverage matrix

§9.1a and the clarification test suites (Cl. §10) **supersede** any §9.1 row for the same requirement. The gate
(D4-01 A) is every Full row of §9.1 and §9.1a.

Status: **Full** = the test fully exercises the requirement in Phase 4; **Partial** = Phase 4 covers its own part
and the rest lands in the named phase; **Foundation** = the Phase 4 data model is tested now and the behavioural
test belongs to the later phase.

| Requirement | Planned tests (class.method) | Phase 4 coverage |
|---|---|---|
| A-001 identity survives change | `PersonIT.a001_identitySurvivesNameAndDataChanges`, `PlaceIT.a001_…` | Full for Person and Place |
| A-002 IDs never reused | `PersonIT.a002_deletedAndMergedIdsNeverReused`, `Phase4SchemaIT` (NO CYCLE) | Full |
| A-003 incomplete is valid | `PersonIT.a003_personWithNothingKnown`, `MentionIT.a003_mentionWithoutEntity`, `HistoricalDateTest.unknownDateValid` | Full |
| A-004 original expression preserved | `HistoricalDateIT.a004_expressionKeptBesideInterpretation`, `RecordDatesIT.a004_recordDateTextUntouched` | Full for dates and Mentions |
| A-005 uncertainty remains | `HistoricalDateTest.approximateStaysApproximate`, `CandidateResolutionIT.proposedStaysProposed` | Partial (export, backup, transfer in Phase 8) |
| A-006 suggestions never conclusions | `CandidateResolutionIT.a006_noPathFromProposedToResolvedWithoutAction`, `HistoricalDateIT.parserProposalNotSavedUntilConfirmed` | Full for Phase 4 |
| A-009 no relationship by coincidence | `ProhibitedInferenceIT.a009_coRolePartiesGetNoRelationship`, `…sameRecordMentionsNoRelationship` | Full for Person and Record (Events and Property in Phase 6) |
| A-010 contradictions permitted | `PersonIT.a010_twoBirthNamesCoexist`, `PlaceIT.a010_conflictingJurisdictionLinks`, `RelationshipIT.p005_…` | Partial (conflicting source *statements* are Claims, Phase 5) |
| P-001 multiple names | `PersonIT.p001_birthMarriedNicknameVariantCoexist_preferredChangeKeepsOthers` | Full |
| P-002 parents never married | `RelationshipIT.p002_twoParentsNoSpouseNoMarriage` + `ProhibitedInferenceIT` | Full |
| P-003 child predates marriage | `RelationshipIT.p003_noChronologyWarning` | Partial: no chronology rule exists; birth date per D4-07 |
| P-004 remarriage | `RelationshipIT.p004_parentageStaysWithPersons` | Full |
| P-005 competing parentage | `RelationshipIT.p005_twoCandidateFathersCoexist` | Full for relationships (Claims in Phase 5) |
| P-006 guardian is not parent | `ProhibitedInferenceIT.p006_guardianCreatesNoParentage` | Full |
| P-007 apprenticeship is not family | `ProhibitedInferenceIT.p007_apprenticeCreatesNoFamilyLinks` | Full |
| P-008 collective reference | `CollectiveReferenceIT.p008_childrenOfDeceasedSonJohn_noPersonsCreated` | Full |
| P-009 direct grandparent | `RelationshipIT.p009_grandparentWithoutIntermediate` | Full |
| P-010 duplicate Persons | `PersonIdentityIT.p010_duplicatesCoexist_noAutoMerge_…` | Full via resolution (D4-09 A) |
| D-001 partial date | `HistoricalDateTest.d001_march1847IsMonthPrecision_notMarch1` | Full |
| D-002 approximate | `HistoricalDateTest.d002_about1847RemainsApproximate` | Full |
| D-003 before, after, range | `HistoricalDateTest.d003_structurallyDistinct` | Full |
| D-004 conflicting dates | `HistoricalDateIT.d004_conflictingDatesCoexist` | Full |
| D-005 calendar distinction | `HistoricalDateTest.d005_julianGregorianDualPreserved` | Partial (round trip in Phase 8) |
| L-001 historical place name | `PlaceIT.l001_namesOverTimeSameIdentity` | Full |
| L-002 jurisdiction change | `PlaceIT.l002_countyChangeKeepsPlaceIdentity` | Full |
| L-003 unresolved place | `MentionIT.l003_placeWordingUnresolvedWithoutPlace` | Full |
| L-004 Property is not Place | `PlaceIT.l004_placeHasNoPropertyColumns` (structural) | Foundation (behaviour in Phase 6) |
| C-001 unresolved Person Mention | `CandidateResolutionIT.c001_myNephewRobertStaysUnresolved` | Full |
| C-002 multiple candidates | `CandidateResolutionIT.c002_threeRobertAndrewsProposed` | Full |
| C-003 Proposed vs Resolved | `CandidateResolutionIT.c003_stateDistinct` + `PeopleWebIT.c003_visiblyDistinct` | Full |
| C-004 explicit resolution | `CandidateResolutionIT.c004_resolveIsDeliberateAndAttributed_wordingKept` | Full |
| C-005 reconsidered | `CandidateResolutionIT.c005_supersededAndRejectedRemainVisible` | Full (Evidence in Phase 5) |
| C-006 cross-domain | `CandidateResolutionIT.c006_placeUsesSameFramework` | Full for Place (Property and others in Phase 6) |
| EV-003 date semantics | `RecordDatesIT.ev003_recordFilingCaptureDistinct` | Partial (Event date in Phase 6) |
| EV-004 Role vs Relationship | `ProhibitedInferenceIT.ev004_witnessRoleCreatesNothing` | Partial (Event Role in Phase 6) |
| S-002 alternate Name | `PeopleWebIT.findByAnyDocumentedName` | Foundation (search in Phase 7) |
| S-005 date search | `HistoricalDateTest.intervalBoundsMatchPrecision` | Foundation |
| S-008 unresolved Mention search | `MentionIT.wordingStoredForDiscovery` | Foundation |
| SEC-002, SEC-003 roles | `PeopleAuthorizationIT` | Full for Phase 4 objects |
| SEC-004 restricted object | `PeopleRestrictionIT` (hidden equals nonexistent on every path) | Full for Phase 4 paths (D4-16) |
| SEC-006 living status | `ProhibitedInferenceIT.sec006_noLivingInference` | Full |
| SEC-007 attribution | `PersonIT.sec007_automaticAttribution` | Full |
| SEC-008 concurrent editing | `PersonIT.sec008_staleSaveOfP00127Conflicts` | Full |
| UP-004, UP-005, UP-006 | `Phase4SchemaIT` (upgrade unchanged), `MigrationFailureIT` (existing) | Full |
| UI-001, UI-003, UI-006 | `PeopleWebIT` (one-line summaries, minimal create, unresolved shown calmly) | Full for Phase 4 pages |
| EX-006, EX-007, EX-008 | data-dictionary drafts in ADR-0012 | Foundation (Phase 8) |
| BI-005, BI-006 | none | Deferred to Phase 7; the Phase 4 framework is what they will use |

#### 9.1a Matrix updates from the architectural clarification (aligned with the approved register, 2026-11-15)

These rows add to or replace the rows above where they cover the same requirement. Details are in
[`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md`](PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md) §9.

| Requirement | Test | Phase 4 coverage |
|---|---|---|
| D-001…D-005 | `HistoricalDateTest`: the worked examples of Cl. §2.4 (37 rows after Revision 4) as table-driven cases: stored fields, envelope, "never stored as" | Full (D-005 round trip in Phase 8) |
| §12 no false precision | `HistoricalDateTest.envelopeNeverNarrowerThanStatedUnit` (property-based), `…yearStartWidening`, `…unspecifiedCalendarWidening`, `…validationPerCalendar` | Full |
| A-006 (dates) | `HistoricalDateIT.parserProposalNotStoredUntilConfirmed`, `…noParsingOnMigrationOrImport`, `…correctedExpressionMarksInterpretationStale` | Full |
| C-003…C-005 | `CandidateResolutionIT.transitionsOnlyAsSpecified` (trigger), `…attributionSetOnce`, `…resolveDoesNotCloseOtherProposals`, `…reversalKeepsAttribution`, `…deletedCandidateShownNotCleared` | Full |
| A-006 (resolution) | `CandidateResolutionIT.noServicePathToResolvedExceptResolve` (enumerates service methods) | Full |
| P-010 | `PersonIdentityIT.resolvedIdentityChangesNoData`, `…noTransitiveInference`, `…contradictionsFlaggedNotBlocked`, `…noAutomaticProposalFromMatchingNamesOrDates` | Full via resolution (D4-09) |
| P-008 | `CollectiveReferenceIT`: named individuals are Mentions; unnamed members create no Persons; members explicit; rejected identifications kept; stated count not a limit; membership creates no relationship | Full |
| L-001, L-002, D4-18 | `PlaceIT.localityChangesCounty`, `…countyBoundaryChangeKeepsIdentity`, `…overlappingAndDisputedJurisdictionsCoexist`, `…historicalAndModernNames`; `MentionIT.ambiguousJurisdictionWordingHasTwoProposals` | Full |
| SEC-004 (Phase 4 paths) | `PeopleRestrictionIT` tests 1–11 (clarifications §5.4), each asserting equality with an archive where the restricted object never existed | Full |
| P-002, P-004 | as above | Complete for Phase 4 relationships; **Phase 6** verifies no Marriage Event is created, required or used to assign children |
| P-003 | `ProhibitedInferenceIT.p003_noCrossObjectDateValidation` | **Partial**; **Phase 6** verifies Birth 1849 / Marriage 1852 with no warning |
| P-005 | as above | Complete for relationships; **Phase 5** verifies competing Claims |
| EV-003 | `HistoricalDateIT.recordFilingCaptureDatesDistinct` | **Partial**; **Phase 6** adds Event date |
| D-001…D-005, §12, D4-25, D4-28 | `HistoricalDateTest` HD-1…HD-7 (Cl. §10.5): the 37 worked examples of Cl. §2.4 with JDNs; calendar fixed points and cross-checks; envelope properties; year start respected when recorded and widened in every period when UNSPECIFIED; dual year; per-calendar day validity; integrity scan; **HD-7, no inferred convention** | Full |
| C-004, SEC-008 (Rev. 2) | `CandidateResolutionConcurrencyIT` CR-1…CR-7 (§10.1) | Full |
| C-005, A-006 (Rev. 2) | `CandidateReproposalIT` RP-1…RP-8 (§10.2) | Full |
| P-008, SEC-004 (Rev. 2, 3) | `CollectiveRestrictionLifecycleIT` CL-1…CL-11 (Cl. §10.3; CL-11 in §10.6) | Full |
| SEC-004, D4-27 (Rev. 2, 3), U-3 | `PersonRestrictionOperationIT` RO-1…RO-18 (Cl. §10.4, §10.6; RO-18 = configuration bounds and the 50,000 cap) | Full |
| Supplement area 1: concurrent resolution of one Mention | CR-1, CR-2, CR-3, CR-4, CR-5, CR-7 | Full |
| Supplement area 2: re-proposal of a rejected candidate | RP-1…RP-8 | Full |
| Supplement area 3: restricted or deleted collectives keep membership and history | CL-1, CL-2, CL-3, CL-4, CL-6, CL-9 | Full |
| Supplement area 4: counts, identifiers and history hidden | CL-5, CL-10, CL-11; `PeopleRestrictionIT` 1, 2, 3, 8, 9, 10; RO-14 | Full |
| Supplement area 5: concurrent relationship or Mention creation during restriction | RO-15, RO-16, RO-5, RO-3, CR-6 | Full |
| Supplement area 6: failed restriction leaves the prior state intact | RO-17, RO-2, RO-9, RO-10, RO-12 | Full |
| GAS-P4-PLAN-001 C: Record Role withdrawal (Cl. §11) | `RecordRoleWithdrawalIT` RW-1…RW-11 (upgrade, no deletion, withdraw, restore, non-disclosure, frozen references, visibility, wording correction, restriction interplay, concurrency, browser) | Full |
| PR4-R3: one global lock order for every writer (Cl. §5.5.3, W1–W14) | `LockSequenceTest` LO-1…LO-3 (recorded sequences, runtime guard, no unregistered locks); `LockOrderIT` LO-4…LO-7 (pairwise races including the job path and Phase 3 Record paths, Phase 3 unaffected, closure growth, implicit foreign-key locks) | Full |
| L-002, D4-30 (Rev. 3) | `PlaceIT.countyFormationExpressedByContainmentAndNote` | Full (formation links deferred) |

### 9.2 Negative tests for prohibited historical inference (`ProhibitedInferenceIT`)

Each test performs the triggering action and then asserts that **no** derived row exists anywhere (relationships,
Names, Persons, Places, resolutions) and that no Change History entry of a forbidden kind was written.

1. Two Persons share a child: no SPOUSE relationship and no Event-like row (§61; P-002).
2. A SPOUSE relationship exists: no parentage to their children, and no child assigned on remarriage (P-004).
3. Two children of the same parents: no SIBLING relationship.
4. A guardian, a master (apprentice) and a household member: no parentage, guardianship or household links beyond
   what was entered (P-006, P-007).
5. A direct grandparent: no intermediate Person or parent links (P-009).
6. Saving a Person Mention or Place Mention: no Person or Place created (C-001, L-003).
7. A Mention whose wording matches an existing Person's Name exactly: no proposal, no resolution (A-006, C-002).
8. Two Persons with identical Names and dates: no merge and no identity proposal (P-010).
9. The PROPOSED → RESOLVED transition happens only through `resolve(...)`; every other service call leaves state
   unchanged (A-006).
10. Saving "March 1847", "abt 1847", "bef 1847" or "1845-1849": no day, month or exactness added; the envelope
    covers every reading of the whole stated period, including both year-start readings when the year start is
    not recorded (D-001…D-003, D4-28).
11. Saving "12 Feb 1720/21 (Julian)": the calendar stays Julian and the dual year is kept. No conversion is stored
    as the interpretation.
12. A Person with no death date: living status stays UNKNOWN (SEC-006).
13. Co-parties on one Record (grantor and grantee, two witnesses): no relationship (A-009, EV-004).
14. A collective reference: no Persons; members only as explicitly added (P-008).
15. A jurisdiction or name change: Place identity unchanged and no new Place created (L-001, L-002).
16. Upgrade from V0023: no Mention, Person, Place or date interpretation created from existing text (UP-006).
17. Interpreting a Record date: `record_date_text` is byte-identical afterwards (A-004, D3-05).
18. No calendar or year-start convention is set from period, jurisdiction, Record, Source or Place; the parser
    proposes both as UNSPECIFIED (D4-25, D4-28; HD-7).
19. A sourced note on a jurisdictional Place ("formed 1836 from part of …") creates no link, Place or structured
    assertion (D4-30).
20. A RESOLVED identity proposal changes no Name, relationship or Mention, and creates no transitive proposal
    (D4-09).

### 9.3 Other test layers
- **Unit:** `HistoricalDateTest` (HD-1…HD-7: parser proposals, precision, envelope, calendars, year start, dual years, validation),
  `FriendlyPrefixTest` additions.
- **PostgreSQL integration:** constraints and triggers per table (`Phase4SchemaIT`), concurrency, transactions.
- **Service:** the classes in §9.1.
- **Security:** `PeopleRestrictionIT` (Cl. §5.4), which extends the Phase 3 traversal suite to Person, Name,
  Relationship, Place, Mention, resolution, collective, identity proposal, history and guessed IDs;
  `CollectiveRestrictionLifecycleIT` (CL-1…CL-11).
- **Concurrency and transactions:** `CandidateResolutionConcurrencyIT` (CR-1…CR-7), `CandidateReproposalIT`
  (RP-1…RP-8), `PersonRestrictionOperationIT` (RO-1…RO-18).
- **Browser:** `PeopleWebIT` and `PlacesWebIT` through the real web process, for every role.
- **Reference Acceptance Archive (§64):** `reference-archive/people-dates-places.yaml`, synthetic and invented
  only. It contains Persons with multiple Names; biological, adoptive, step and guardian relationships; parents
  who never married; remarriage; competing parentage; a collective reference; duplicate Person candidates;
  uncertain dates; a historical Place with a jurisdiction change; an unresolved Place; and unresolved, proposed
  and resolved Candidate cases. `ReferencePeoplePlacesIT` builds it through the services.
- **Regression:** all Phase 0–3 tests unchanged except the documented schema pin. The Windows/Linux × PostgreSQL
  16/18 matrix, the smoke test, native service tests and the worker kill/restart test stay as they are.

---

## 10. Work packages and gates

| WP | Content | Gate |
|---|---|---|
| WP0 | Decisions and U-1…U-3 approved (2026-11-15). [ADR-0012](architecture/ADR-0012-people-dates-places.md) and its data-dictionary drafts are written. Run the prefix compatibility check in WP1. | **Final independent architectural review: SATISFIED** (accepted 2026-11-15; PR4-R1…PR4-R5 closed; GAS-P4-PLAN-001 resolved). **Remaining: a separate owner implementation authorization.** |
| WP1 | Historical Date model, JDN calendar algorithms, envelope, parser and validation (unit-heavy); the `LockSequence` helper with the Phase 3 locks registered at L1 (LO-1…LO-3); V0024 | `HistoricalDateTest` HD-1…HD-7 green; LO-1…LO-3 for Phase 3 paths; prohibited-inference items 10, 11, 18 |
| WP2 | People: Person, Names, relationships, identity proposals; V0026 | P-001…P-007, P-009, P-010; prohibited-inference items 1–5, 8, 12, 13, 20 |
| WP3 | Places: Place, Names, jurisdiction links (kind, basis); V0027; no formation links | L-001…L-003; `PlaceIT` D4-18 scenarios; `countyFormationExpressedByContainmentAndNote`; items 15, 19 |
| WP4 | Mentions, Candidate Resolution, collective references, Record Role links, **Record Role withdrawal and restoration** (GAS-P4-PLAN-001 C), Record date interpretation, restriction tables and deferred invariant triggers; V0028 | C-001…C-006, A-006, P-008; CR-1…CR-7, RP-1…RP-8, RW-1…RW-10; items 6, 7, 9, 14, 16, 17 |
| WP5 | Browser workflows, restriction (policy S and the D4-27 protocol), history visibility | `PeopleWebIT`, `PlacesWebIT`, `PeopleRestrictionIT` 1–11, CL-1…CL-11, RO-1…RO-18, LO-4…LO-7, RW-11 (Role withdraw and restore controls) |
| WP6 | Reference fixture, full CI matrix, completion report (`PHASE-4-COMPLETION.md`), draft PR, then stop for the independent Codex audit | All four jobs green, every job's results checked individually |

The phase gate (§62) is all of §9.1 marked Full, plus §9.2, passing on every job. **No merge, acceptance or tag
without a separate owner instruction.**

### 10.1 WP1 architectural status (owner decision D4-31, 2026-11-17)

The WP1 locking and maintenance protocol (WP1-R1, GAS-P4-002) is governed by owner decision **D4-31**:
Architecture C (hybrid, with explicit offline maintenance and recovery) and Rules C-01…C-12 in
[`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md). The WP1 gate in the table above is unchanged.

| Item | Status |
|---|---|
| WP1 architectural direction | **SIMPLIFY — OWNER APPROVED** (Architecture C) |
| Replacement WP1 architectural contract | **ACCEPTED** at Gate A, 2026-11-18 (D4-33): [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md) Draft 7 at `5cfb0b4` |
| Gate B feasibility plan | **ACCEPTED**, 2026-11-19 (D4-36): [`PHASE-4-WP1-GATE-B-PLAN.md`](PHASE-4-WP1-GATE-B-PLAN.md) Revision 6 at `709da2c`; Codex ACCEPT CORRECTIONS; GB-P1, GB-N1, GB-N2 CLOSED |
| Feasibility verification (Gate B proofs) | **NOT EXECUTED**. Gate B NOT ACCEPTED. |
| WP1 implementation (of the Architecture C replacement design) | Not authorized |
| WP1 acceptance | Pending |
| WP2 | HOLD |

[`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md) revision 3.9 is superseded as the
intended architectural direction by the owner's Architecture C decision and retained as historical design and review
evidence. It is neither accepted nor implemented.

The technical basis is
[`PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md`](PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md).
The WP1 work already committed on `phase-4` (the Historical Date work and the WP1-R2 and WP1-R3 corrections) is
unchanged and remains unaccepted.

**Gate B execution handoff** (references only; the accepted plan governs and nothing here adds a requirement):

| Topic | Where in [`PHASE-4-WP1-GATE-B-PLAN.md`](PHASE-4-WP1-GATE-B-PLAN.md) Revision 6 |
|---|---|
| Proof matrix and scope (P1–P3, V-R, V-H, Proof 1, Proof 2 (a)–(q), §12.1 A–C, F-O, F-T, F-R); Gate C retentions | §3, §5.1–§5.9 |
| Environments: Windows and Linux × PostgreSQL 16 and 18; local storage baseline; NFS as initial NAS target | §4.1, §6 |
| Prototype boundary, open-decision stand-ins, scratch databases, observer, process-death instrumentation | §4.2–§4.5 |
| Evidence content and outcome categories (PASS, FAIL, INVALID, INCONCLUSIVE, NOT RUN) | §4.6, §9 |
| Execution order and dependency stops | §7 |
| Results review and failed-proof handling (no automatic redesign) | §8.5, §8.6 |
| Authorizations still required before execution | §10 (AU-3 to AU-7, AU-9, AU-10; see D4-36) |
| F-R timebase | D4-35 |

---

## 11. Assumptions, unresolved questions and acceptance risks

### 11.1 Assumptions (confirmed by the approved register)
1. The Phase 4 gate is the People (P-…), Dates and Places (D-…, L-…) and Candidate Resolution (C-…) tests, to the
   coverage stated in §9.1. Items marked Partial or Foundation are completed in the named later phases and are
   not Phase 4 defects.
2. Phase 4 relationships, Names and resolutions are researcher-entered interpretive information (§9). Phase 5 adds
   Source Statements, Evidence and Claims and links them **without rewriting** Phase 4 rows.
3. Persons and Places use the Phase 3 lifecycle (ACTIVE/DELETED, restore only). There is no MERGED_INTO (D4-09 A).
4. Synthetic test data only. No real genealogy data goes in the repository.
5. The browser stays server-rendered Thymeleaf as in Phases 0–3. No client framework is added.

### 11.2 Specification questions: resolved, and open items

The seven questions originally listed here were resolved by the approved register (2026-11-15):
- vital dates: D4-07 A;
- jurisdiction model: D4-18 A;
- Person merge: D4-09 A (resolution, no merge);
- relationship meaning: D4-08 A;
- parser proposals: D4-06 A;
- restriction and living status: D4-16 A and D4-17 A;
- sex or gender: D4-22 A (not added).

The consistency review found five open items. **U-1…U-3 were resolved by the owner on 2026-11-15**; U-4 and U-5 are
notes:

| ID | Item | Resolution or plan impact |
|---|---|---|
| U-1 | Place-type seeds; mandatory type | **Resolved:** seed COUNTY and STATE (JURISDICTIONAL); `place_type` optional. V0024 seeds them. |
| U-2 | Place-name types | **Resolved:** `place_name_type` vocabulary seeded HISTORICAL, CURRENT, VARIANT; optional on a name. V0024. |
| U-3 | D4-27 numeric values | **Resolved:** configurable installation settings `people.restriction.*` with defaults 30 min, 5 s, 30 s, 1,000, 10 min, 50,000; validated by `check-config` (ADR-0012 §8). The ceiling is capped at 50,000; raising it needs an approved ADR revision (PR4-R1). Tests set their own values within the cap. |
| U-4 | Year-start widening in every period (approved behaviour) | Phase 7 search design; no Phase 4 change |
| U-5 | Phase 6 must explicitly carry formation and boundary-change relationships | Phase 6 planning |
| GAS-P4-PLAN-001 | **Resolved: Option C approved (2026-11-15).** Record Role withdrawal replaces physical deletion. | V0028 additions; WP4 implements withdraw and restore with RW-1…RW-11; Cl. §11 |

### 11.3 Acceptance risks
| Risk | Effect | Mitigation |
|---|---|---|
| Phase 5 must attach Evidence to Phase 4 relationships, Names and resolutions without reinterpreting them | Rework, or a UP-006 violation | Keep Phase 4 rows interpretive with stable identity and history. ADR-0012 records the attachment points (D4-08). |
| A relationship period and a Phase 6 Marriage Event both carry a year (C-7) | Divergent values | D4-07 A: no vital fields; the period is labelled "as entered" and never synchronised. Coexistence is permitted (A-010). |
| A parser makes a precision mistake, or a convention is assumed | False precision, violating §12 and D-001 | Proposals only, with researcher confirmation. Calendar and year start are never inferred (HD-7). Exhaustive precision tests. The envelope always covers every reading. |
| A restriction leak through relationships, Names, Mentions or history | A security finding, as in Phase 3 audits 1 and 2 | Reuse the Phase 3 hidden-equals-nonexistent test harness for every new path from the start. Restriction traversal is a WP5 gate. |
| Lock-order and concurrency defects between resolution, restriction and Record deletion | GAS-P3-006-class defects | One global lock order (Cl. §5.5.3: Phase 3 objects, then Persons, Mentions, collectives, identity proposals, each by UUID). Deferred invariant triggers. Race tests CR-*, RO-*. |
| **Prefix collision in a later phase**: ACCEPTANCE-SPEC PR-008 uses "Mill Structure **S001**", but Phase 3 assigned `S` to Source | Phase 6 cannot use `S` for Structure | Not a Phase 4 issue. Recorded here so Phase 6 chooses another prefix (spec examples are illustrative). `PR` is reserved for Property. |
| Scope creep into search, import or Events | Delays the phase gate | §1 exclusions. The name lookup is deliberately minimal. |
| UI friction from mandatory steps | UI-003 and UI-006 failures | Nothing beyond what integrity requires. Unresolved is shown as normal state, not as an error. |
