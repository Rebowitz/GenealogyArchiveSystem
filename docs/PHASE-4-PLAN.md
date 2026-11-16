# Phase 4 Implementation Plan — People, Dates and Places

**Status: PROPOSED for owner review. Planning only; no Phase 4 implementation is authorized.**

| Item | Value |
|---|---|
| Authorization | Phase 4 planning only (owner assignment, 2026-11-15) |
| Accepted baseline | Tag `phase-3-accepted` → commit `c494d6a` (Phase 3 merge, PR #6); `main` = `20d68c1` (adds only CLAUDE.md documentation commits) |
| Branch | `phase-4-plan` from `main` `20d68c1` (planning documents only) |
| Governing order | archival invariants → ACCEPTANCE-SPEC v1.1 → BUILD-SPEC v1.1 → approved Phase 0–3 decisions → accepted implementation → convenience |
| Companion document | [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md): the numbered owner-decision register (D4-01 … D4-24), every entry **PROPOSED** |

Where this plan depends on a choice the specifications leave open, the text says so and names the decision
(**D4-nn**). Nothing marked "recommended" is decided until the owner approves the register.

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
| **Phase 6** | Event (birth, marriage, death, migration), Event Role, Organization, Property, Structure, DNA Test, Research objects, Collections, Tags | No vital-event fields that would pre-empt the Event model (D4-07). Place must stay a separate concept from Property and Structure (L-004). |
| **Phase 7** | Global search, fuzzy search, OCR discovery, import templates, preview, bulk import (BI-003…BI-006), bulk operations including BI-007 | A simple find-by-documented-name lookup to choose candidates (D4-21). It never ranks, matches or resolves anything. |
| **Phase 8** | Backup, restore, Complete Archive Export, Transfer Packages (EX-007, EX-008, TP-003) | The data model must export these meanings faithfully: date precision and qualifier, Proposed vs Resolved, unresolved Mentions, retired IDs. The data dictionary entries are drafted with the ADR. |
| **Phase 9** | Comprehensive restriction management, restriction propagation (SEC-005), living-person policy | Phase 4 objects follow the Phase 3 restriction pattern so nothing leaks (D4-15, D4-16). |
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
| `record_role` (role code, `party_as_written`, note) | V0022; `RecordService` roles; `record.html` | Optional link to a Person Mention (D4-12). `party_as_written` is untouched and stays the authority for wording. |
| `archival_record.record_date_text`, `filing_date_text` | V0022; `RecordService`; `record.html` | Optional structured Historical Date interpretation beside each (D4-05). No automatic parsing of existing rows (D4-06). |
| `source.jurisdiction_text` | V0021; `CatalogService` | Optional link to a jurisdiction Place (D4-19, recommended for a later phase). |
| `friendly_id_series` and `FriendlyIdSeriesRepository` | V0007 | New series for Person, Place and possibly Mention and Collective Reference (D4-02). |
| Vocabulary pattern: code, label, retired; never deleted | V0021; `VocabularyService`; `/vocabularies` | Name types, relationship types, parentage qualifiers and place types use the same pattern (D4-11). |
| Change History and history visibility | V0004; `ChangeRecorder`; `RecordsHistoryVisibility`, `RestrictedFileHistory` | New object types and a third visibility layer for restricted Persons, hidden Mentions and resolutions (D4-15). |
| `AccessPolicy`, `Permission`, `Role` | `access` | New permissions EDIT_PEOPLE_PLACES and VIEW_RESTRICTED_PEOPLE (or reuse EDIT_RECORDS, see D4-15). Vocabulary management stays with MANAGE_VOCABULARIES. |
| Restriction pattern | `Restriction`; `RecordAccess`; ADR-0011 §8 | Restricted Persons (if approved, D4-16). Visibility of Mentions, relationships and resolutions derives from their endpoints. |
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
  plain integer columns with B-tree indexes (D4-05).
- **Explicit tables, never EAV (§10, §60).** Every concept has its own table and typed foreign keys. Candidate
  Resolution uses typed, nullable target columns with a CHECK that exactly one is set, not a free-form
  `(entity_type, entity_id)` pair (D4-13).
- **ADR-0012 (proposed).** People, Dates, Places and Candidate Resolution. Written in WP0 after the decision
  register is approved, in the same form as ADR-0011, including data-dictionary text for the Phase 8 export.

---

## 4. Data model (proposed; migrations V0024 onward)

Every table follows the accepted conventions:
- UUIDv7 primary key; a friendly number where the object type has a series;
- `created_at` and `created_by`, never typed by the user (SEC-007);
- `row_version` for optimistic concurrency (SEC-008);
- an identity-immutability trigger;
- no physical delete or truncate of durable objects (`reject_removal`);
- Change History in the same transaction.

Details marked D4-nn depend on that decision.

### 4.1 Historical Date (`historical_date`) — D4-04, D4-05, D4-06

An owned value object: one row per dated field, never shared between owners.

| Column | Meaning |
|---|---|
| `expression` | The source wording, exactly as entered. Required when the date has any content (A-004). Correctable with history, or immutable: D4-04 (recommended correctable, with history). |
| `qualifier` | EXACT, ABOUT, ESTIMATED, CALCULATED, BEFORE, AFTER, BETWEEN, FROM_TO, UNKNOWN, NOT_INTERPRETED. Vocabulary per D4-04. |
| `uncertain` | The source or researcher marks the date as doubtful ("1847?"). Separate from ABOUT. |
| `calendar` | UNSPECIFIED, GREGORIAN, JULIAN. |
| `start_year`, `start_month`, `start_day` | First (or only) point, each nullable. Precision is whichever parts are present, so March 1847 is year and month only (D-001). |
| `end_year`, `end_month`, `end_day` | Second point for BETWEEN and FROM_TO. |
| `dual_year_start`, `dual_year_end` | The alternate year of a dual-dated expression ("12 Feb 1720/21"). |
| `interpretation_origin` | RESEARCHER or PARSER_CONFIRMED (D4-06). |
| `earliest_day`, `latest_day` | Derived, non-authoritative interval bounds (proleptic Gregorian day numbers) for later search (S-005, Phase 7). Recomputed from the interpretation, never edited. |

**CHECKs:**
- the parts form a valid partial date (a day requires a month; month 1–12; day valid for the month);
- the end point is present exactly for BETWEEN and FROM_TO;
- the dual year is present only with a year;
- nothing is interpreted when the qualifier is NOT_INTERPRETED.

Conflicting dates coexist because dates are owned by separate statements and are never unique per Person or
Record (D-004, A-010).

### 4.2 People
- **`person`** (`P`)
  - `display_name_id` (FK → `person_name`, nullable)
  - `living_status`: UNKNOWN default, LIVING or DECEASED, as entered and never inferred (D4-17)
  - `restriction` (D4-16)
  - `lifecycle`: ACTIVE, DELETED, MERGED_INTO (D4-09)
  - `note`
- **`person_name`**
  - `person_id`
  - `name_as_written` (required)
  - optional structured parts: given, surname, prefix, suffix (D4-10)
  - `name_type` (vocabulary: BIRTH, MARRIED, NICKNAME, VARIANT, ALIAS, …; D4-11)
  - optional valid-from / valid-to Historical Dates
  - provenance: optional Record, Citation and Mention references plus a note (D4-10)
  - `sequence_number`, lifecycle

  A preferred display Name is a pointer on `person`. Changing it never touches the other Names (P-001).
- **`person_relationship`**
  - `relationship_type` (vocabulary; D4-11)
  - `person_a_id` and `person_b_id`, ordered by the type's direction (for PARENT_CHILD, A is the parent)
  - `parentage_qualifier`, only for parentage types: UNSPECIFIED, BIOLOGICAL, ADOPTIVE, STEP, FOSTER (§15)
  - optional start/end Historical Dates (D4-07)
  - provenance (Record, Citation) and note
  - lifecycle: ACTIVE or WITHDRAWN (D4-08)

  CHECKs:
  - a Person never relates to itself;
  - the qualifier is present exactly for parentage types.

  **No uniqueness on "one mother/one father"** (P-005). Duplicates are allowed, so competing relationships with
  different provenance coexist.
- **`relationship_type`** (vocabulary)
  - `code`, `label`, `inverse_label`, `directed` flag
  - `category`: PARENTAGE, SPOUSE, SIBLING, GUARDIANSHIP, DIRECT_ANCESTRY, SOCIAL or OTHER

  `category` decides only whether `parentage_qualifier` applies. It never triggers inference (§61).
- **`collective_reference`** (D4-14)
  - `record_id` anchor
  - `wording_as_written`
  - optional anchor Person or Mention plus an "of whom" phrase as written
  - optional stated count
  - note
- **`collective_member`**: an explicit, researcher-entered link to a Person, with its own Candidate Resolution
  state.

### 4.3 Places
- **`place`** (`PL`; D4-02)
  - `place_type` (vocabulary: COUNTY, PARISH, TOWN, …; D4-11)
  - `approximate_location` (text; optional coordinates per D4-20)
  - `restriction`? (recommended: none in Phase 4; D4-16)
  - lifecycle, note

  Identity never derives from name, jurisdiction or coordinates (§13).
- **`place_name`**
  - `place_id`, `name_as_written`, `name_type` (HISTORICAL, CURRENT, VARIANT, …)
  - optional valid-from / valid-to Historical Dates
  - provenance and note
  - preferred-name pointer on `place`, as for Person
- **`jurisdiction_link`** (D4-18; recommended model A)
  - `place_id` lies within `jurisdiction_place_id`
  - `jurisdiction_kind` (COUNTY, STATE, PARISH, …)
  - valid-from / valid-to Historical Dates
  - provenance and note

  CHECKs:
  - no self-link;
  - no cycle, checked in the service (a DB trigger is optional).

  Overlapping or contradictory links coexist (A-010). A county change is a new link with dates; the Place's
  identity is unchanged (L-002).

### 4.4 Mentions and Candidate Resolution
- **`mention`** (D4-12, D4-13)
  - `mention_kind`: PERSON or PLACE (extensible in later phases)
  - `record_id` anchor (D4-12)
  - either `record_role_id` (a Role-backed Person Mention, whose wording is the Role's `party_as_written`) or
    `wording_as_written` (free Mention, e.g. "my nephew Robert"). CHECK: exactly one source of wording.
  - context note, lifecycle
  - **No resolution state is stored on the Mention.** It is derived from the resolutions: Unresolved, Proposed or
    Resolved (C-003).
- **`candidate_resolution`**
  - `mention_id`
  - one of `candidate_person_id` or `candidate_place_id` (typed, nullable; CHECK exactly one, matching
    `mention_kind`)
  - `state`: PROPOSED, RESOLVED, REJECTED or SUPERSEDED
  - rationale note
  - `decided_by` and `decided_at`
  - `superseded_by` (self-FK)

  **Partial unique index: at most one RESOLVED row per Mention** (D4-13).

  Rows are never deleted. A state change is an update that history records. A resolution that is rejected or
  superseded stays visible (C-005).

  **There is no code path from PROPOSED to RESOLVED except an explicit "resolve" action by an editor**, and a test
  asserts that (C-003, C-004, A-006).
- **Person–Person identity (P-010).** Choices are D4-09. The recommendation is a Person–Person "same as" proposal
  table using the same states, with merge deferred.

### 4.5 Extensions to accepted tables (additive only)
- `record_role`: none. Linkage goes from `mention.record_role_id` to the Role, so the accepted table is unchanged
  (D4-12, recommended B).
- `archival_record`: `record_date_id` and `filing_date_id` (nullable FKs to `historical_date`). The text columns are
  untouched (D4-05).
- `source`: no change in Phase 4 if D4-19 adopts the recommendation.

### 4.6 Friendly-ID prefix compatibility (D4-02)
Prefixes already in use: F, R, RP, S, CS, CT.

| Object | Proposed prefix | Notes |
|---|---|---|
| Person | `P`, 5 digits | The spec's own example is `P00127`. |
| Place | `PL`, 5 digits | |
| Mention | `M`, 5 digits (optional, D4-02) | |
| Collective Reference | `CP`, 4 digits (optional, D4-02) | |

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
| `HistoricalDates` (pure) | Validate a structured interpretation; compute interval bounds; optional parser that **proposes** an interpretation for common forms (D4-06) | Never adds precision ("March 1847" → year and month only). ABOUT, BEFORE, AFTER, BETWEEN and FROM_TO stay distinct. A Julian date keeps its calendar. Dual years are kept. An unparseable expression stays NOT_INTERPRETED and is still valid. |
| `PersonService` | Create, correct and delete or restore a Person; Names; preferred Name; living status | Creating a Person needs nothing (A-003). Changing the preferred Name never deletes other Names (P-001). Living status is never derived (SEC-006). |
| `RelationshipService` | Add, correct and withdraw relationships | Adding SPOUSE never creates parentage. Adding parentage never creates SPOUSE, sibling, household or Event. No chronology validation between relationships (P-003). No count limit on parents (P-005). No intermediate Persons (P-009). No relationship from Role or Record co-occurrence (A-009). |
| `PlaceService` | Places, Place Names, jurisdiction links | Renames and jurisdiction changes never change Place identity (L-001, L-002). Contradictory jurisdiction links coexist. |
| `MentionService` | Create Mentions (free or Role-backed) on a Record | Saving a Mention never requires a Person or Place (C-001, L-003). Wording stays as written. |
| `CandidateResolutionService` | Propose, resolve, reject and supersede | Multiple PROPOSED (C-002). RESOLVED only through an explicit action, recorded with actor and time (C-004). A new RESOLVED supersedes the old one, which is kept (C-005). Place targets use the same code (C-006). |
| `CollectiveReferenceService` | Collective references and explicit members | Never creates Persons (P-008). |

**Lifecycle.**
- Persons, Places, Mentions and Collective References are ACTIVE or DELETED (recoverable), as for Records (D3-12),
  plus MERGED_INTO for Persons if D4-09 option B is chosen.
- A deleted object can only be restored.
- Identifiers are never reused (A-002).

---

## 6. Authorization, attribution, concurrency, restriction (D4-15, D4-16)

- **Roles.** Viewer reads. Editor creates and changes people, names, relationships, places, mentions and
  resolutions. Administrator also manages vocabularies (MANAGE_VOCABULARIES, existing) and restricted Persons.
- **Attribution and concurrency.** Every change is attributed automatically (SEC-007) and protected by
  `row_version` (SEC-008, with an explicit P00127 stale-save test).
- **Visibility derives from what a row connects:**
  - A Mention is visible only if its Record is visible (D3-04).
  - A resolution is visible only if its Mention and its candidate are visible.
  - A relationship, Name or Collective member is visible only if every Person it names is visible.
  - Hidden and nonexistent IDs give identical answers, and history entries about hidden objects are dropped whole.
    This extends the Phase 3 traversal tests to the new paths, including guessed `P…`, `PL…` and `M…` IDs.
- **Restricted Persons (D4-16).** Recommended: NORMAL/RESTRICTED on Person, Administrator-only, no propagation,
  matching D3-04. The living-person policy and propagation (SEC-005) stay in Phase 9.

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
   - Each Role shows its party as written and, if linked, the Mention's state: "unresolved", "proposed: P00127,
     P00133" (visually distinct) or "resolved: P00127".
   - "Add Mention" holds free wording, a Person or Place kind and a note.
   - Collective references.
   - A Historical Date editor next to the record and filing dates, showing the expression as written above the
     interpretation.
6. **Resolve a Mention.** Lists the current candidates by state. The actions are Propose candidate, Resolve (an
   explicit confirmation with an optional rationale), Reject and Supersede. "Create a new Person from this
   Mention" is a separate deliberate action (C-001, BI-006 semantics), never a default.
7. **Historical Date editor.** The expression field is always first. An optional "interpret" step shows the
   parser's proposal (D4-06), which the researcher confirms or edits. Precision and qualifier are shown in words
   ("about March 1847, month precision").
8. **Vocabularies page.** Adds name types, relationship types, parentage qualifiers (fixed by spec, D4-11) and
   place types.

---

## 8. Migrations, upgrade compatibility and failure handling

| Migration | Content |
|---|---|
| V0024 | Vocabularies (name, relationship, parentage qualifier, place types, with spec-named seeds only, D4-11) and `historical_date` |
| V0025 | Person, `person_name`, `person_relationship`, collective reference and members; series `P` (and `CP`) |
| V0026 | Place, `place_name`, `jurisdiction_link`; series `PL` |
| V0027 | Mention and `candidate_resolution` (series `M`); `archival_record.record_date_id` and `filing_date_id` |

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
  Citations, grouping runs, applied worksheet rows, Files, a tombstone and a quarantine object upgrades to V0027.
  Every accepted table snapshot is unchanged and the new tables are empty.
- **Schema pinning.** `Phase3SchemaIT` is pinned to V0023, following the accepted pattern.
- **Rollback of the feature itself.** No Phase 4 data is written by migration, so declining Phase 4 means restoring
  the pre-upgrade backup. That is documented, not automated.

---

## 9. Tests

### 9.1 Requirement-to-test coverage matrix

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
| P-010 duplicate Persons | `PersonIdentityIT.p010_duplicatesCoexist_noAutoMerge_…` | Full or Partial per D4-09 |
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
10. Saving "March 1847", "abt 1847", "bef 1847" or "1845-1849": no day, month or exactness added; interval bounds
    span the whole period (D-001…D-003).
11. Saving "12 Feb 1720/21 (Julian)": the calendar stays Julian and the dual year is kept. No conversion is stored
    as the interpretation.
12. A Person with no death date: living status stays UNKNOWN (SEC-006).
13. Co-parties on one Record (grantor and grantee, two witnesses): no relationship (A-009, EV-004).
14. A collective reference: no Persons; members only as explicitly added (P-008).
15. A jurisdiction or name change: Place identity unchanged and no new Place created (L-001, L-002).
16. Upgrade from V0023: no Mention, Person, Place or date interpretation created from existing text (UP-006).
17. Interpreting a Record date: `record_date_text` is byte-identical afterwards (A-004, D3-05).

### 9.3 Other test layers
- **Unit:** `HistoricalDateTest` (parser proposals, precision, interval bounds, calendars, dual years, validation),
  `FriendlyPrefixTest` additions.
- **PostgreSQL integration:** constraints and triggers per table (`Phase4SchemaIT`), concurrency, transactions.
- **Service:** the classes in §9.1.
- **Security:** `PeopleRestrictionIT`, which extends the Phase 3 traversal suite to Person, Name, Relationship,
  Place, Mention, resolution, history and guessed IDs.
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
| WP0 | Owner approves `PHASE-4-DECISIONS.md`; write ADR-0012 and the data-dictionary drafts; run the prefix compatibility check | Owner approval of decisions and ADR |
| WP1 | Historical Date model, parser and validation (unit-heavy); V0024 | `HistoricalDateTest` green |
| WP2 | People: Person, Names, relationships, collective references; V0025 | P-001…P-010 and prohibited-inference items 1–5, 8, 12–14 |
| WP3 | Places: Place, Names, jurisdiction links; V0026 | L-001…L-003 |
| WP4 | Mentions and Candidate Resolution, Record Role links, Record date interpretation; V0027 | C-001…C-006, A-006, items 6, 7, 9, 16, 17 |
| WP5 | Browser workflows, restriction traversal, history visibility | `PeopleWebIT`, `PlacesWebIT`, `PeopleRestrictionIT` |
| WP6 | Reference fixture, full CI matrix, completion report (`PHASE-4-COMPLETION.md`), draft PR, then stop for the independent Codex audit | All four jobs green, every job's results checked individually |

The phase gate (§62) is all of §9.1 marked Full, plus §9.2, passing on every job. **No merge, acceptance or tag
without a separate owner instruction.**

---

## 11. Assumptions, unresolved questions and acceptance risks

### 11.1 Assumptions (to confirm with the decision register)
1. The Phase 4 gate is the People (P-…), Dates and Places (D-…, L-…) and Candidate Resolution (C-…) tests, to the
   coverage stated in §9.1. Items marked Partial or Foundation are completed in the named later phases and are
   not Phase 4 defects.
2. Phase 4 relationships, Names and resolutions are researcher-entered interpretive information (§9). Phase 5 adds
   Source Statements, Evidence and Claims and links them **without rewriting** Phase 4 rows.
3. Persons and Places use the Phase 3 lifecycle (ACTIVE/DELETED, restore only) unless D4-09 adds MERGED_INTO.
4. Synthetic test data only. No real genealogy data goes in the repository.
5. The browser stays server-rendered Thymeleaf as in Phases 0–3. No client framework is added.

### 11.2 Unresolved specification questions (for owner decision; all in the register)
1. **Birth, marriage and death dates before the Event model** (D4-07). P-003's chronology needs a marriage and a
   birth date. Events are Phase 6. Either Phase 4 adds no vital-event fields and P-003 is Partial, or it adds
   interim fields that Phase 6 must then reconcile.
2. **Jurisdiction as an entity or a relationship** (D4-18). §13 says "separate … Jurisdiction", §11 says
   "Jurisdiction relationship/history" and O-005 keeps Jurisdiction distinct from Organization and Structure.
3. **Person merge** (D4-09). P-010 allows "controlled merge **or** resolution"; §62 puts Candidate Resolution
   *history* in Phase 5.
4. **What Phase 4 relationships *are*** before Claims exist (D4-08). Is a Phase 4 relationship a conclusion, an
   assertion or a Claim placeholder?
5. **Parser-proposed date interpretations** (D4-06). Is normalisation by software an "automatic suggestion" under
   A-006?
6. **Restricted Persons and living people** (D4-16, D4-17). SEC-006 forbids inference; privacy policy is Phase 9.
7. **Sex or gender on Person.** The specifications never mention it. Recommended: not added in Phase 4 (D4-22).

### 11.3 Acceptance risks
| Risk | Effect | Mitigation |
|---|---|---|
| Phase 5 must attach Evidence to Phase 4 relationships, Names and resolutions without reinterpreting them | Rework, or a UP-006 violation | Keep Phase 4 rows interpretive with stable identity and history. ADR-0012 records the attachment points (D4-08). |
| Interim vital dates (if D4-07 option B is chosen) collide with Phase 6 Events | Two sources of truth for a birth date | Recommend option A. If B, label the fields "summary as entered", never derived, with a planned Phase 6 reconciliation. |
| A parser makes a precision mistake | False precision, violating §12 and D-001 | Proposals only, with researcher confirmation. Exhaustive precision tests. Interval bounds always cover the whole stated period. |
| A restriction leak through relationships, Names, Mentions or history | A security finding, as in Phase 3 audits 1 and 2 | Reuse the Phase 3 hidden-equals-nonexistent test harness for every new path from the start. Restriction traversal is a WP5 gate. |
| Lock-order and concurrency defects between resolution and Record deletion | GAS-P3-006-class defects | Lock the parent Record before changing its Mentions or resolutions, using one documented lock order. Race tests. |
| **Prefix collision in a later phase**: ACCEPTANCE-SPEC PR-008 uses "Mill Structure **S001**", but Phase 3 assigned `S` to Source | Phase 6 cannot use `S` for Structure | Not a Phase 4 issue. Recorded here so Phase 6 chooses another prefix (spec examples are illustrative). `PR` is reserved for Property. |
| Scope creep into search, import or Events | Delays the phase gate | §1 exclusions. The name lookup is deliberately minimal. |
| UI friction from mandatory steps | UI-003 and UI-006 failures | Nothing beyond what integrity requires. Unresolved is shown as normal state, not as an error. |
