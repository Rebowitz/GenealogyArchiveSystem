# Phase 3 Implementation Plan — Repository, Source and Archival Records

**Status: proposal for owner review. No Phase 3 implementation has started.**

| Item | Value |
|---|---|
| Authorization | Owner authorized Phase 3 on 2026-11-08 |
| Starting baseline | `main` = `9bb2952be06554752d7b0752c1c14ecbacb4cd44` (Phase 2 merge; accepted implementation `464d2d0`) |
| Branch | `phase-3`, created from that commit |
| Governing order | archival invariants → ACCEPTANCE-SPEC v1.1 → BUILD-SPEC v1.1 → approved Phase 3 decisions → accepted Phase 0–2 implementation → convenience |

Items marked **[spec]** come from the governing specifications. Items marked **[proposal]** are where the
specifications are silent or leave a choice; each is listed again under *Decisions requested* and is not
built until the owner decides.

## 1. Scope

BUILD-SPEC §62: *"Phase 3 - Repository, Source and Archival Records: Repository, Source, Capture Session,
Archival Record, Citation foundation, File-to-Record many-to-many, courthouse grouping, classifications,
Record roles. Gate: courthouse workflow tests pass."*

| Element | Governing text | Phase 3 content |
|---|---|---|
| Repository | §11, §17, §22; O-003…O-006 | Custody/stewardship object, distinct from creator, Access Provider, Jurisdiction and building. Optional everywhere: an unknown Repository never blocks entry. |
| Source | §11, §17, §19, §22 | Source / record book (for courthouse work, *Repository → Source/Record Book → Archival Record → Files*); Repository optional. |
| Capture Session | §38; BI-001, UI-004, UI-005 | Context entered once and inherited by many Files (Repository, Source/book, capture date, researcher, equipment/method, notes), with per-File overrides that leave other Files unchanged. |
| Archival Record | §19; R-001…R-006; A-001…A-003, A-008 | Principal logical documentary object; friendly ID `R…` [spec: R01842]; correctable classification; Record date and filing date distinct from capture date (EV-003). |
| File-to-Record | §19, §40; R-001…R-003, F-005 | Many-to-many; court/source page designation distinct from image sequence; several captures of one page; one File in several Records. |
| Courthouse grouping | §38, §40; R-004, R-005 | Sequential grouping (Start New Record marker; blank continues the preceding Record) and explicit `record_group`, which wins when both are present; partial completion valid. |
| Classifications | §25; CT-003, CT-004; R-006 | Controlled, configurable, correctable vocabulary for Record type (e.g. DEED, DTRUST); changes never alter identity or physical storage. |
| Record Roles | §19; EV-004; A-009 | Configurable role vocabulary (grantor, grantee, testator, executor, witness, beneficiary, adjoining landholder, prior owner, surveyor…), distinct from Person Relationship and Event Role. |
| Citation foundation | §11, §17; E-012 (later) | Minimal Citation pointing at a Source and/or Record with a locator, so Phase 5 Source Statements can cite it. |
| Relationship-aware consolidation | P2-G (deferred to Phase 3); F-006, F-007 | **[proposal, D3-03]** Researcher-driven consolidation of redundant captures now that Record links exist. |

### Explicit exclusions (later phases per §62)
- **Phase 4:** Person, Person Name, Person Relationship, Collective References, Historical Date,
  Historical Place, Place Name, Jurisdiction, Mentions, Candidate Resolution.
- **Phase 5:** Source Statement, Evidence, Claim, Conclusion, Expected/Absent Evidence.
- **Phase 6:** Organization (so the historical *creator* is not yet an Organization object), Structure, Property,
  Event, DNA Test, Research objects, Collections, Tags.
- **Phase 7:** global search, OCR, CSV import templates and preview, generic bulk operations, list-first refinement.
- **Phase 8:** backup, restore, Complete Archive Export, Transfer Packages.
- **Phase 9:** comprehensive restriction management (P2-H), permission traversal across all objects, deployment hardening.

### Not changed in Phase 3
- **Preservation behaviour:** ingestion, recovery, integrity, quarantine, destruction, V0020 evidence and P2-I.
- **Phase 2 File provenance:** `archival_file` keeps its immutable columns, and preservation originals are never
  renamed or moved (A-008, CT-004).
- **Migrations V0001–V0020** are not modified.

## 2. Dependencies on the accepted Phase 2 implementation

| Phase 2 element | Use in Phase 3 |
|---|---|
| `archival_file` (UUIDv7 + `F` friendly ID, lifecycle ACTIVE/DELETED/DESTROYED, restriction, `row_version`) | Target of Record links and Capture Session context; DELETED and DESTROYED Files remain referable as history but are shown as such. |
| File restriction policy (ADR-0009 "Restriction"; restricted Files Administrator-only, no existence oracle, Change History filtering) | Every Record, Source and Capture Session view, list, count and history entry applies the same policy to linked Files. |
| `file_upload_batch` (immutable) | A batch may belong to a Capture Session; the batch itself is not altered. |
| Thumbnails, display rotation | Courthouse review and grouping views. |
| `friendly_id_series` (Phase 1) | New series: `R` for Records [spec]; others per D3-07. |
| Change History, `FieldClass` and optimistic concurrency (Phase 1, ADR-0008) | All new objects. |
| Destruction and tombstones | A destroyed File's Record links stay as references to its tombstone; no link is removed silently. |

## 3. Data model and migrations (proposed; V0021 onward, no changes to V0001–V0020)

Each object gets a UUIDv7 primary key, optional friendly number, immutable-identity triggers, a no-delete
rule (identifiers are never reused, A-002), `row_version` and Change History.

| Proposed table | Purpose / key columns | Notes |
|---|---|---|
| `record_classification` | `code` (e.g. `DEED`), label, description, `retired` | Archive configuration; codes are never deleted, only retired. Seed values per D3-08. |
| `record_role_type` | `code`, label, `retired` | Archive configuration. Seed values per D3-08. |
| `repository` | name, description, address/contact text | Correctable; no relationship to Organization until Phase 6. |
| `source` | title, `repository_id` (nullable), source-type text, volume/book designation, creator text, access-provider text, notes | Repository optional (O-003). Creator and Access Provider kept separate from Repository (O-004, O-006) per D3-06. |
| `capture_session` | Repository, Source, capture date, captured-by, equipment/method, notes | Context is correctable. |
| `file_capture_context` | `file_id` (PK), `capture_session_id`, nullable override columns | Inherited values are resolved when read, so correcting a session updates every File that doesn't override it. An override changes only its own File (BI-001). |
| `archival_record` | friendly `R` number, `classification_code` (nullable: awaiting classification is valid, A-003), title/summary, Source, Record-date and filing-date source expressions (D3-05), transcription/abstract (D3-10), `lifecycle` ACTIVE/DELETED, restriction (D3-04) | Classification correction keeps identity (R-006). |
| `record_file` | `record_id`, `file_id`, image sequence within the Record, court/source page designation (text, e.g. `227`, `227A`, `227-228`), note | Many-to-many (R-003). Several Files may share a page (R-002). The page is never derived from the sequence. |
| `record_role` | `record_id`, `role_code`, party as written in the source, note; Person link deferred (D3-01) | No Person Relationship is created (A-009, EV-004). |
| `citation` | target Source and/or Record (at least one), locator text, citation text as entered | Foundation only: no citation-style formatting. |
| `file_consolidation`, `file_consolidation_member` (if D3-03 approved) | preferred File, members, preview snapshot, decided by/at | Members go through recoverable deletion. A Record link is carried to the preferred File as a new link noting the consolidation, never by rewriting the original link's provenance (F-006). |

**PostgreSQL 16/18:** only features already in use (constraints, triggers, sequences, `jsonb`); no
extensions. Migration-upgrade test `Phase3SchemaIT`: upgrade a V0020 database holding Files, unresolved
records and quarantine entries; verify that all Phase 2 data is unchanged.

## 4. Application components and interfaces

The project's existing conventions continue: a modular monolith, plain SQL through `JdbcClient`, no ORM, and Thymeleaf pages.

| Package | Content |
|---|---|
| `records` (new) | Repository, Source, Capture Session, Archival Record, Record-File link, Record Role and Citation services and repositories, with validation that permits incompleteness (A-003). |
| `records.grouping` | Courthouse grouping: applies a Start-New-Record / `record_group` worksheet to a Capture Session's ordered Files. Idempotent and resumable, in bounded transactions (PF-002), with no single giant transaction. |
| `config` (vocabularies) | Administrator management of classifications and role types (add, relabel, retire; never delete). |
| `preservation` (unchanged except consolidation) | Consolidation service if D3-03 is approved; reuses recoverable deletion and destruction. |
| Web | List-first pages for Repositories, Sources, Capture Sessions and Records, each with a one-line summary (UI-001) and a detail page (UI-002). The Capture Session page has a thumbnail grid with grouping controls and per-File overrides. The Record page has an ordered File viewer with page designations, Roles and Citations. File details show "Used in Records". |
| CLI | None beyond existing commands (vocabulary seeding happens in migrations or setup, per D3-08). |

## 5. Security and authorization
- **Roles [spec §43]:**
  - Viewers read.
  - Editors create and edit Repositories, Sources, Capture Sessions, Records, links, Roles and Citations.
  - Administrators also manage vocabularies and perform File destruction (unchanged).
  - Viewers cannot modify anything (SEC-003).
- **Restricted Files** stay invisible to non-Administrators through every new path: Record viewers, link counts,
  Capture Session grids, Citations, grouping results, consolidation previews, Change History and guessed IDs.
  A Record that links a restricted File reveals neither the link nor its count (no existence oracle).
- **Object-level restriction of Records/Sources** follows D3-04.
- **CSRF, transport boundary and sessions:** unchanged (Phase 1).
- **Attribution [SEC-007]:** automatic for every change.
- **Optimistic concurrency [SEC-008]:** every editable object and the grouping worksheet; stale saves receive
  understandable conflict handling.
- **Logs [SEC-010]:** transcriptions and notes are never written to diagnostic logs.

## 6. Tests

Test names carry Acceptance IDs.

| Gate (courthouse workflow) | Tests |
|---|---|
| R-001 | 3-page deed: one Record, three Files, page 227–229 distinct from image sequence |
| R-002 | Two captures of page 227 on the same Record and page, shown together |
| R-003 | One File linked to Deed A and Deed B |
| R-004 | Sequential worksheet with Start New Record; blank continues the preceding Record; explicit `record_group` wins |
| R-005 | Stop after 83 of 200; the 117 remaining Files stay valid, ungrouped and unclassified |
| R-006 | Reclassify R01842 DEED → DTRUST: Record and File IDs unchanged; no physical rename (checked on disk) |

| Also in Phase 3 | Tests |
|---|---|
| Global | A-001, A-002, A-003, A-008, A-009 (Records and Capture Sessions create no relationships) |
| Repositories | O-003, O-004, O-005, O-006 (the Phase 3 parts; Organization and Structure arrive in Phase 6) |
| Classification | CT-003, CT-004 (Records); CT-001/002/005 are Phase 6 Collections and Tags |
| Bulk and capture | BI-001, BI-002, UI-001…UI-005 for the new objects; F-005 grouping for viewing; F-006/F-007 if D3-03 approved; BI-007 if D3-11 approved |
| Dates | EV-003 (Record, filing and capture dates stay distinct; the Event part waits for Phase 6) |
| Security and transactions | SEC-001…SEC-004 and SEC-007, SEC-008 for the new objects; restricted-File leakage through every new path; TX-001 rollback |
| Scale | PF-002 partly: grouping over 1,000 Files without one transaction or holding them all in the browser |
| Schema | `Phase3SchemaIT` upgrade and constraints (no-delete, immutability, vocabulary retirement) |
| Regression | All Phase 0–2 tests unchanged; Phase 2 audit regressions kept |

- **Fixture:** a synthetic courthouse deed-book fixture (no real data) starts the Reference Acceptance Archive (§64).
- **CI:** the existing Windows/Linux × PostgreSQL 16/18 matrix, smoke and native-service tests, and the real worker
  JVM kill/restart test. Phase 3 adds no filesystem behaviour, but tests assert on both platforms that no
  preservation original is renamed or moved by Phase 3 operations.

## 7. Work packages and checkpoints

| # | Work package | Checkpoint |
|---|---|---|
| 0 | Owner decisions on §9 recorded in `PHASE-3-DECISIONS.md`; ADR-0011 (records model) | **Owner review (this document)** |
| 1 | Migrations: vocabularies, Repository, Source, Capture Session, Record, links, Roles, Citation; `Phase3SchemaIT` | CI green; schema reviewable before services build on it |
| 2 | Services and repositories with history, concurrency and validation; restriction policy over links | CI green |
| 3 | Web: list-first pages, Record viewer, Capture Session page with inheritance and overrides | CI green |
| 4 | Courthouse grouping (sequential and explicit), resumable | R-001…R-006 green |
| 5 | Consolidation (if D3-03), bulk reclassification (if D3-11) | CI green |
| 6 | Synthetic courthouse fixture; completion report (§72 format) | **Builder completion report → independent Codex audit → correction rounds → owner acceptance → merge (regular merge commit) → tag on owner authorization** |

## 8. Risks
- **Scope creep** into Persons, Dates, Places or search: mitigated by text-only "as written" fields and explicit
  exclusions, with later phases adding structure without rewriting Phase 3 data.
- **Leaking restricted Files** through Record links, counts or history: one shared visibility policy, plus
  tests on every new path.
- **Data written as text now** (dates as written, creator and Access Provider text) needs a careful
  interpretation step when Phase 4 and Phase 6 add structured objects. The original text must be preserved
  (A-004), never replaced.
- **Large batches:** grouping and reclassification run in bounded, resumable steps; the browser pages through the grid.
- **Vocabulary changes:** retiring a code never invalidates existing Records; codes are never deleted.
- **Consolidation (if approved):** previews and provenance must not rewrite one capture's relationships as another's (F-006).

## 9. Decisions requested from the owner

| ID | Question | Recommendation |
|---|---|---|
| D3-01 | Record Roles before Persons exist (Phase 4). | Role = role code + party *as written* in the source + note. Phase 4 adds an optional link to a Person or an unresolved Mention. Alternative: defer Roles to Phase 4. |
| D3-02 | How courthouse grouping (R-004) is entered before CSV import templates (Phase 7). | A browser grouping worksheet on the Capture Session page (Start New Record marker, `record_group` column), with the same rules a Phase 7 CSV import will apply. |
| D3-03 | Relationship-aware consolidation (P2-G deferred it to Phase 3). | Include a minimal F-006/F-007 consolidation now, because Record links make it meaningful. Alternative: defer to Phase 7 with bulk workflows. |
| D3-04 | Object-level restriction for Records/Sources (P2-H puts comprehensive restriction in Phase 9). | Phase 3: Records carry NORMAL/RESTRICTED with Administrator-only visibility, matching Files. No automatic propagation between Records and Files beyond never exposing restricted Files. Alternative: NORMAL-only until Phase 9. |
| D3-05 | Record date and filing date before the Historical Date model (Phase 4). | Store the source expression text only (A-004). Phase 4 adds structured interpretation alongside. Capture date is an ordinary calendar date. |
| D3-06 | Historical creator, Access Provider, Jurisdiction and building before Organization/Place/Structure (Phases 4/6). | Separate descriptive text fields on Source (creator, access provider, jurisdiction as written), never stored in the Repository. Later phases add object links. |
| D3-07 | Friendly-ID prefixes beyond `R` (Record). | Proposed: Repository `RP`, Source `S`, Capture Session `CS`, Citation `CT`. The owner may choose others; Repositories and Citations could also have none. |
| D3-08 | Initial vocabulary contents. | Seed only the values the specifications name: classifications `DEED`, `DTRUST`; roles grantor, grantee, testator, executor, witness, beneficiary, adjoining landholder, prior owner, surveyor. Administrators add the rest. |
| D3-09 | Managed filename naming rules tied to classification (R-006 "may update"). | Not in Phase 3: managed names stay as entered. The optional rule is deferred until requested. |
| D3-10 | Transcription/abstract text on a Record (§38 lists transcription in bulk processing). | Include one researcher-entered transcription/abstract field, labelled as researcher-entered (not OCR). Structured Source Statements come in Phase 5. |
| D3-11 | Bulk reclassification (BI-007) now or with Phase 7 bulk operations. | Include transactional bulk reclassification of selected Records (with Change History) because R-006/CT-003 introduce classification now. Alternative: Phase 7. |
| D3-12 | Record lifecycle. | ACTIVE and DELETED (recoverable) only. No purge or merge of Records in Phase 3. Identifiers retired permanently. |
