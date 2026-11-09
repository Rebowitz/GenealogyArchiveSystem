# Phase 3 Implementation Plan — Repository, Source and Archival Records

**Status: Phase 3 plan finalized by owner decision on 2026-11-08; implementation has not started.**

| Item | Value |
|---|---|
| Authorization | Owner authorized Phase 3 on 2026-11-08 |
| Starting baseline | `main` = `9bb2952be06554752d7b0752c1c14ecbacb4cd44` (Phase 2 merge; accepted implementation `464d2d0`) |
| Branch | `phase-3`, created from that commit |
| Governing order | archival invariants → ACCEPTANCE-SPEC v1.1 → BUILD-SPEC v1.1 → approved Phase 3 decisions → accepted Phase 0–2 implementation → convenience |

Items marked **[spec]** come from the governing specifications. Former **[proposal]** items are resolved by the owner's adopted decisions in §9. No further design authorization is implied beyond those decisions.

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
| Relationship-aware consolidation | P2-G; F-006, F-007 | **Deferred from Phase 3**, retained as an outstanding requirement for a later approved phase; no silent cancellation. |

### Explicit exclusions (later phases per §62)
- **Phase 4:** Person, Person Name, Person Relationship, Collective References, Historical Date,
  Historical Place, Place Name, Jurisdiction, Mentions, Candidate Resolution.
- **Phase 5:** Source Statement, Evidence, Claim, Conclusion, Expected/Absent Evidence.
- **Phase 6:** Organization (so the historical *creator* is not yet an Organization object), Structure, Property,
  Event, DNA Test, Research objects, Collections, Tags.
- **Phase 7:** global search, OCR, CSV import templates and preview, generic bulk operations, list-first refinement, including bulk reclassification if separately approved.
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
| `preservation` | Unchanged; Phase 3 must not introduce consolidation or alter preservation behavior. |
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
| Bulk and capture | BI-001, BI-002, UI-001…UI-005 for the new objects; F-005 grouping for viewing. F-006/F-007 consolidation and BI-007 bulk reclassification remain deferred, not Phase 3 gates. |
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
| 0 | Owner decisions in §9 are approved; record them in `PHASE-3-DECISIONS.md` and ADR-0011 (records model) before implementation | **Plan finalized; implementation requires separate authorization** |
| 1 | Migrations: vocabularies, Repository, Source, Capture Session, Record, links, Roles, Citation; `Phase3SchemaIT` | CI green; schema reviewable before services build on it |
| 2 | Services and repositories with history, concurrency and validation; restriction policy over links | CI green |
| 3 | Web: list-first pages, Record viewer, Capture Session page with inheritance and overrides | CI green |
| 4 | Courthouse grouping (sequential and explicit), resumable | R-001…R-006 green |
| 5 | Hardening: citation provenance, inheritance semantics, restriction traversal, replay-safe grouping, lifecycle and negative tests | CI green |
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

## 9. Owner decisions — approved 2026-11-08

The owner adopted all independent-review recommendations. These decisions supersede the earlier alternatives; no coding is authorized merely by finalizing the plan.

| ID | Final decision |
|---|---|
| D3-01 | Record Role stores configurable role code, party name **as written**, and note. Person or unresolved-Mention linkage is deferred to Phase 4. Do not conflate Record Role with Person Relationship or Event Role. |
| D3-02 | Implement a browser-based Capture Session grouping worksheet with Start New Record and explicit `record_group`; explicit group wins. Phase 7 CSV import is excluded. |
| D3-03 | **Defer relationship-aware File consolidation** beyond Phase 3. Preserve P2-G and F-006/F-007 as outstanding requirements for later owner-approved scheduling. |
| D3-04 | Support NORMAL/RESTRICTED Records, with Administrator-only access to restricted Records. Apply restriction checks to all Record, Citation, link, thumbnail, count, history and guessed-ID paths. A NORMAL Record must not disclose its RESTRICTED linked Files; do not automatically propagate File/Record restrictions. Comprehensive policy management remains Phase 9. |
| D3-05 | Store Record and filing date **source expressions as written** in separate fields. Capture date uses an ordinary calendar date. Structured historical date interpretation remains Phase 4 and must not overwrite the source expression. |
| D3-06 | Store historical creator, Access Provider and jurisdiction-as-written as separate descriptive Source fields, distinct from Repository custody. Do not create premature Organization, Jurisdiction or Structure objects; add structured links in later phases. |
| D3-07 | Use proposed friendly-ID prefixes: Record `R`, Repository `RP`, Source `S`, Capture Session `CS`, Citation `CT`, **subject to a pre-migration compatibility check** against the existing allocator and naming rules. If incompatible, stop and return the exact conflict for owner resolution rather than silently substitute identifiers. |
| D3-08 | Seed minimal classifications `DEED`, `DTRUST`; role codes grantor, grantee, testator, executor, witness, beneficiary, adjoining landholder, prior owner, surveyor. Administrators may add, relabel and retire entries; referenced codes cannot be deleted or reused. |
| D3-09 | No automatic managed filename changes tied to classification in Phase 3. Classification correction never renames/moves preservation originals. |
| D3-10 | Include separate researcher-entered **verbatim transcription** and **researcher-written abstract** fields on Records, clearly labelled; neither is OCR nor a Phase 5 Source Statement. Preserve original text and change attribution. |
| D3-11 | **Defer bulk reclassification** to Phase 7 or later owner-approved work. Phase 3 supports correctable individual Record classifications and R-006. |
| D3-12 | Record lifecycle ACTIVE/DELETED (recoverable); no purge or Record merge. IDs are never reused, and deletion preserves history and associations. Define restore and visibility semantics before coding. |

## 10. Required implementation clarifications and acceptance criteria

1. **Citation identity/provenance.** Define stable Citation identity and locator semantics for Source-only, Record-only and Source+Record citations. Citation edits and subsequent Record reclassification must preserve citation identity, original locator text, history and attribution. Avoid fabricating unknown source metadata.
2. **Capture Session override tri-state.** Model *inherit*, *override with value*, and *explicitly blank/unknown* distinctly for every overridable field. Read-time inheritance must not retroactively rewrite File history. Specify how session edits affect effective context and what history records about such edits; test override isolation and correction of inherited values.
3. **Restriction traversal.** A NORMAL Record with RESTRICTED Files must reveal no restricted File existence, page designations, link counts, thumbnails, grouping data, history, error behavior or guessed IDs to non-Administrators. RESTRICTED Records and their Citations are Administrator-only. Reuse the Phase 2 restriction policy and test direct and indirect paths, including mixed-visibility collections.
4. **Grouping idempotency and restart.** Define durable operation/worksheet identity, stable source-File ordering, per-group completion state and replay behavior. Retrying after a crash must not create duplicate Records or links; partial work must remain valid. Define behavior for conflicting edits and cross-batch groups. Use bounded transactions and test interruption/replay at transaction boundaries.
5. **Record lifecycle.** Define recoverable delete/restore and historical link visibility without physical deletion, cascading erasure or ID reuse. Ensure no hidden File leaks through tombstones or audit history.
6. **Scope control.** Do not implement File consolidation or generalized bulk reclassification in Phase 3. Keep both in the outstanding-requirements register for later planning. Phase 3 completion must not claim F-006/F-007 or BI-007 are implemented.
7. **Compatibility and gates.** Preserve V0001–V0020 and all Phase 0–2 tests. Run schema upgrade with existing File/quarantine/tombstone data, R-001…R-006, BI-001/002, O-003…O-006 Phase 3 portions, authorization and negative tests, and the complete Windows/Linux × PostgreSQL 16/18 matrix.

## 11. Authorization boundary

**Owner-approved plan; implementation not yet authorized.** The next step is to record the decisions and technical clarifications in reviewable documentation, then request explicit owner approval to begin Phase 3 coding. Do not merge, accept or tag Phase 3 without a separate owner instruction. The Phase 2 acceptance tag remains a separate outstanding push/verification task.
