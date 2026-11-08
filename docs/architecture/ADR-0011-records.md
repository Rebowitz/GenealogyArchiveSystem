# ADR-0011: Repository, Source, Capture Session and Archival Record model (Phase 3)

## Status
Accepted for implementation (owner decisions D3-01…D3-12, [`PHASE-3-DECISIONS.md`](../PHASE-3-DECISIONS.md)).

## Context
BUILD-SPEC §19, §22, §25, §38–§40 and ACCEPTANCE-SPEC R-001…R-006, O-003…O-006, BI-001, BI-002, BI-007,
CT-003, CT-004, F-005…F-007. For courthouse work the chain is *Repository → Source/Record Book → Archival
Record → File representations*. A File may represent parts of several Records, and a Record may use several
Files. Phase 3 precedes the Person, Date and Place models (Phase 4), the evidence model (Phase 5) and
Organization/Structure (Phase 6).

## Decisions

### Objects and identity
- **Identity.** Repository (`RP`), Source (`S`), Capture Session (`CS`), Archival Record (`R`) and Citation
  (`CT`) each have a UUIDv7 primary key and a friendly number from their own never-cycling sequence. Rows are
  never deleted (A-002); identity columns are immutable by trigger. Correctable attributes carry `row_version`
  for optimistic concurrency (SEC-008), and every change writes Change History in the same transaction.
- **Incompleteness is valid (A-003).** A Record needs nothing beyond its identity: classification, Source,
  dates, summary and Files are all optional. Repositories, Sources and Capture Sessions need only a
  researcher-facing name or title, which is a label, not a historical fact. A Source's Repository is
  optional (O-003).
- **Kept distinct (O-004…O-006, D3-06).** A Source keeps the historical creator, Access Provider and
  jurisdiction as written in separate fields, never in the Repository.
- **Dates (EV-003, D3-05).** A Record's date and filing date are the source expression as written. The Capture
  Session's capture date is a calendar date. The three never share a field.

### Vocabularies (D3-08)
- `record_classification` and `record_role_type` are archive configuration: a code (immutable), a label and
  a retired flag.
- Codes are never deleted. A retired code stays valid on existing Records and Roles but is not offered for new use.
- Classification is correctable and never changes identity, provenance or physical storage (R-006,
  CT-003, CT-004): nothing in Phase 3 touches preservation originals.

### Files and Records (R-001…R-003, F-005)
- `record_file` links a Record and a File with an image sequence (display order within the Record), a
  court/source page designation (text such as `227`, `227A`, `227-228`) and a note.
- The page is never derived from the sequence. Several Files may share a page, and one File may be linked to
  several Records.
- Links are correctable associations: they can be added, re-paged or removed, with history. Links to Files later
  deleted or destroyed remain and show the File's state.

### Capture Sessions (BI-001, UI-004, UI-005)
- **Context.** A session holds Repository, Source, capture date, researcher, equipment/method and notes.
- **Membership.** `file_capture_context` places a File in one session at a sequence position. Its override
  columns are NULL to inherit, so the effective value is the override or the session value, resolved when read.
  - Correcting the session changes every File that does not override it.
  - An override changes only its own File.
  - Files are added explicitly: from an upload batch or by friendly ID. Ingestion is unchanged.
- **Worksheet (D3-02).** Each row stores its grouping marks: Start New Record, `record_group`, page designation
  and classification. Saving rows is a small transaction per page, so a half-finished worksheet is ordinary
  saved state (R-005).

### Courthouse grouping (R-004, R-005, PF-002, D3-02)
- **Order.** Rows are taken in session order, counting only those visible to the person applying.
- **Group rules.**
  - A row with `record_group` belongs to that group: an explicit `record_group` overrides Start New Record.
  - Otherwise a Start New Record row starts a group.
  - Otherwise a blank row continues the preceding row's group, or that row's Record if it was already applied.
  - Leading blank rows stay ungrouped and remain valid archived material.
- **Session-scoped groups.** `capture_session_group` maps a session's group keys to the Records they created, so
  later rows with the same key join the same Record.
- **Applying.** Each group is applied in its own transaction: create or extend the Record, link its Files in
  order with their pages, and mark the rows applied. A rerun skips rows already applied, so an interruption
  never duplicates (BI-008). A new group's classification comes from its first row that has one.

### Roles (D3-01; EV-004, A-009)
`record_role` holds a role code, the party as written (optional), a note and a display sequence. Roles create
no Person, Person Relationship or Event; Phase 4 adds an optional link.

### Citation foundation
A Citation refers to a Source and/or a Record (at least one), with a locator and the citation text as
entered. There is no style formatting. Phase 5 Source Statements will cite Citations.

### Restriction (D3-04, P2-H)
- **Record restriction.** Records carry NORMAL/RESTRICTED. A restricted Record is visible only with
  `VIEW_RESTRICTED_RECORDS` (Administrators). For anyone else, every route treats it exactly as a nonexistent
  Record: pages, friendly-ID lookup, lists, links from Files, Sources and Sessions, Citations, grouping and
  history.
- **Restricted Files.** These keep the Phase 2 rule everywhere a Record shows Files. A non-Administrator sees no
  link, page, thumbnail or count for them.
- **Change History** gains a second read-time filter. Entries about a restricted Record, or about a Citation of
  one, are removed. A value or reason referring to a Record is withheld unless every Record it names is visible;
  references are recognised by `R` friendly IDs (same tokenization as File IDs) and by UUIDs. This filter is
  applied after the File filter, so a restricted Record and a nonexistent one are never distinguished.
- **No propagation.** Restriction does not propagate between Records and Files (D3-04).

### Consolidation (D3-03; F-006, F-007, BUILD-SPEC §33)
- **Starting it.** A researcher selects two or more ACTIVE, visible Files of the same restriction and designates
  one as preferred. A File derived from another candidate cannot take part, because a derivative is not an
  independent capture.
- **Preview.** Before anything changes, the preview lists each redundant File's Record links, Capture Session
  placement and derivatives.
- **Execution, in one transaction:**
  - Each Record link of a redundant File is kept unchanged, as provenance. Where the preferred File is not
    already linked to that Record, a new link is added that names the consolidation and the File it was carried
    from (never rewriting the original link).
  - Each redundant File moves to recoverable deletion.
  - The consolidation and its members are recorded.
- **Not created.** No derivation is ever created between members.
- **Permanent deletion (F-007)** uses the existing Administrator destruction, which leaves a tombstone. The
  identifiers stay retired.

### Bulk reclassification (D3-11, BI-007)
Selected visible Records (at most 500 per action) are reclassified in one transaction with Change History on
each Record. A failure rolls back all of them.

## Consequences
- Text kept as written now (dates, creator, Access Provider, jurisdiction, party names) is interpreted
  alongside, not replaced, when Phases 4–6 add structured objects (A-004).
- No Phase 3 operation reads or writes preservation originals, so Windows and Linux behaviour is identical. A
  test on each platform checks that originals stay byte-identical and in place.
