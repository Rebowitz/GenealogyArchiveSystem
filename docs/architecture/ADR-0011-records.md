# ADR-0011: Repository, Source, Capture Session and Archival Record model (Phase 3)

## Status
Approved by owner 2026-11-08 (WP0), with two mandatory safeguards below. Phase 3 coding is authorized; merge, formal acceptance and tagging require separate owner approval. See [`PHASE-3-DECISIONS.md`](../PHASE-3-DECISIONS.md).

## Context
Governing requirements:
- BUILD-SPEC §8, §17, §19, §22, §25, §38–§40.
- ACCEPTANCE-SPEC R-001…R-006, BI-001, BI-002, the Phase 3 parts of O-003…O-006, A-001…A-004, A-008, A-009,
  CT-003 and CT-004.

For courthouse work the chain is *Repository → Source/Record Book → Archival Record → File representations*. A
File may represent parts of several Records, and a Record may use several Files. Phase 3 comes before the
Person, Date and Place models (Phase 4), the evidence model (Phase 5) and Organization/Structure (Phase 6).

Not in Phase 3: File consolidation (F-006, F-007, P2-G), bulk reclassification (BI-007) and CSV grouping import.
See §9.

## 1. Objects and identity
- **Identity.**
  - Repository (`RP`), Source (`S`), Capture Session (`CS`), Archival Record (`R`) and Citation (`CT`) each get
    a UUIDv7 key and a friendly number from their own `NO CYCLE` sequence (prefix check: D3-07 in the decisions
    document).
  - Rows are never deleted. Identity columns are immutable by trigger, as in V0011.
- **Concurrency and history.** Correctable attributes carry `row_version` (SEC-008). Every change writes Change
  History in the same transaction.
- **Incompleteness is valid (A-003).**
  - A Record needs only its identity: classification, Source, dates, summary and Files are all optional.
  - Repositories, Sources and Sessions need only a researcher-facing name or title. That is a label, not a
    historical fact.
  - A Source's Repository is optional (O-003).
- **Kept distinct (O-004…O-006, D3-06).** A Source holds the historical creator, Access Provider and
  jurisdiction as written, in separate text fields. None of them is ever stored in the Repository.
- **Dates (D3-05).** The record date and filing date are source expressions as written, in separate fields. The
  capture date is a calendar date and belongs to the capture, not the record.
- **Text (D3-10).** A Record has a *verbatim transcription* and a *researcher-written abstract*. These are two
  fields with separate labels and separate history; neither is OCR or a Source Statement.
- **Vocabularies (D3-08).**
  - `record_classification` and `record_role_type` each hold a code (immutable), a label and a retired flag.
  - Codes are never deleted or reused.
  - A retired code stays valid where it is already used, but is not offered for new use.
- **Classification (R-006, CT-003, CT-004, D3-09).** Classification is correctable. Changing it never changes
  identity, provenance, Citations, Files or preservation originals.

## 2. Files and Records (R-001…R-003)
- `record_file` links a Record and a File with:
  - an image sequence, which is the display order within the Record;
  - a court/source page designation, as text (`227`, `227A`, `227-228`);
  - a note.
- The page is never derived from the sequence. Several Files may share a page, and one File may be linked to
  several Records.
- Links can be added, re-paged, reordered or removed, with history.
- A link to a File that is later deleted or destroyed remains, and shows that File's state to readers who can
  see the File.

## 3. Capture Sessions and the override tri-state (BI-001; plan §10.2)
- **Session context.** A session holds Repository, Source, capture date, researcher, equipment/method and notes.
- **Membership.**
  - `file_capture_context` places a File in at most one session, at a sequence position.
  - Files are added explicitly, from an upload batch or by friendly-ID list or range. Ingestion is unchanged.
- **Tri-state per field.** Each of the five overridable fields has a `mode` and a value:

  | Mode | Effective value | Meaning |
  |---|---|---|
  | `INHERIT` | the session's current value | the default |
  | `VALUE` | the File's own value | must be non-blank |
  | `BLANK` | none | not known for this File, even though the session has a value |

  A database CHECK enforces that a value is present exactly when the mode is `VALUE`.
- **Read-time resolution.**
  - The effective context is computed when it is read and is never stored on the File.
  - Correcting the session changes the effective value of every `INHERIT` row. It never changes `VALUE` or
    `BLANK` rows.
- **History.**
  - A session correction is recorded once, in the session's history, with its before and after values.
  - It is **not** written into each inheriting File's history, so File history is never rewritten
    retroactively.
  - A File's override change is recorded in that File's history as two fields per changed item,
    `capture_<field>_mode` and `capture_<field>`. Because the mode is its own field, "inherits", "explicitly
    not known" and a value can never be confused.
  - The File page shows each effective value with its origin: "from CS0003", "this File" or "not known for this
    File".
- **Tests.**
  - Override isolation: changing one File's override leaves the session and its other Files unchanged.
  - Correcting an inherited value changes `INHERIT` rows only. A `BLANK` row stays blank when the session later
    gains a value.
  - History assertions: no File history entry results from a session correction.

## 4. Courthouse grouping: idempotency and restart (R-004, R-005; plan §10.4)
- **Worksheet.**
  - Each row stores its marks: Start New Record, `record_group`, page designation and classification.
  - Marks are saved per page, in a small transaction, so a half-finished worksheet is ordinary saved state
    (R-005).
  - The marks of an applied row are final.
- **Stable ordering.** Rows are ordered by `(sequence_number, file_id)`. Sequence numbers are unique per
  session (deferrable UNIQUE).
- **Group rules.** These are applied over rows the applying person can see (§8), skipping Files that are
  not ACTIVE, **only where restricted or omitted rows cannot change group boundaries**. A hidden Start New Record marker, explicit group assignment or other omitted boundary must never cause separate Records to be silently merged. Ambiguous groups are flagged and remain unapplied pending authorized resolution:
  1. A row with `record_group` joins that group. The explicit group wins over Start New Record.
  2. Otherwise, a Start New Record row starts a new group.
  3. Otherwise, a blank row continues the preceding row's group. If the preceding row is already applied, it
     continues that row's Record, provided the Record is visible and ACTIVE.
  4. Blank rows with nothing before them stay ungrouped. They are valid archived material.
- **Durable identities.**
  - Each run is a `grouping_run` row (RUNNING → COMPLETED or INTERRUPTED), with planned, applied, ungrouped and
    conflicted row counts.
  - Each group has a stable key:
    - `g:<record_group>` for an explicit group, compared after trimming and lower-casing;
    - `s:<file id>` for a group begun by a Start New Record row;
    - `r:<record id>` for continuing an existing Record.
  - `capture_session_group` permanently maps a session's explicit key to the Record it created.
- **Bounded transaction processing.** Groups of up to 1,000 rows may commit in one transaction. Larger logical groups use a stable group-to-Record identity and durable progress checkpoints across multiple bounded transactions, with atomic row/link progress in each transaction. A crash or retry must never create duplicate Records or links. Each transaction:
  - creates or extends the Record;
  - inserts its links in order, with pages;
  - marks the rows applied, setting `applied_record_id` and `applied_run_id` (final, enforced by trigger);
  - writes a `grouping_run_group` completion row and the run's counters.

  For groups of up to 1,000 rows, application is atomic per group. For larger groups, committed chunks remain valid and identifiable as partial progress; an interrupted chunk rolls back atomically. The group must not be reported COMPLETED until all intended visible rows are applied. Never treat partial progress as a completed group.
- **Replay.**
  - Every run plans again from the durable row state. Applied rows are final, so a retry never creates a second
    Record or link for them.
  - Links are keyed `(record_id, file_id)` and inserted with `ON CONFLICT DO NOTHING`.
  - A rerun of an interrupted session only applies what remains. Partial work stays valid.
- **Exclusion.**
  - Each run holds a per-session PostgreSQL advisory lock. A second concurrent run is refused with "grouping is
    already running".
  - At the start of a run, while holding the lock, any `RUNNING` run of that session is closed as
    `INTERRUPTED`. That covers a process that stopped; its completed groups stay as recorded.
- **Conflicting edits.**
  - Each row's `row_version` is captured when the run is planned. Marking a row applied requires that same
    version.
  - If any row of a group was edited after planning, the whole group is skipped and counted as conflicted. It
    is left unapplied for the next run, so a group is never split.
  - Editing an applied row's marks is refused.
- **Cross-batch groups.**
  - Explicit keys are session-scoped and permanent. A row added later, from another upload batch, with the same
    `record_group` joins the same Record in a later run.
  - Keys are never shared between sessions. Adding a File to a Record from another session is done by linking
    it manually.
  - Start and continuation groups never span runs. The only exception is a blank row that extends an applied
    row's Record (rule 3).
- **Bounds.** At most 1,000 rows are processed in one transaction; this is not a maximum group or Record size. Larger groups must be processed in bounded, resumable chunks with stable Record identity, ordering and crash-safe progress.
- **New Records.**
  - A Record created by grouping takes the session's Source and its group's first classification.
  - It is NORMAL, because restriction never propagates (D3-04). Its creation history names the session.
- **Tests.** Fault injection, reusing the Phase 2 mechanism, will interrupt:
  - before a group's transaction;
  - inside it, before commit;
  - after commit, before the next group;
  - after the last group, before the run is finished;
  - after a committed chunk of a group larger than 1,000 rows;
  - with a restricted Start New Record or explicit-group boundary hidden from the applying user (must not silently merge Records).

  Each replay must have no duplicate Records or links, correct counts and an `INTERRUPTED` → new-run
  sequence.

## 5. Roles (D3-01)
- `record_role` holds:
  - a role code;
  - the party as written, which is optional because the name may be illegible;
  - a note;
  - a display order.
- A Role creates no Person, Person Relationship or Event Role. Phase 4 adds an optional link.

## 6. Citations: identity and provenance (plan §10.1)
- **Identity.** A Citation has a UUID and a `CT` number. Its **targets** (`source_id`, `record_id`, at least
  one) are immutable. Re-pointing a Citation means creating a new one and deleting the old one; both keep their
  identities and history.
- **Three forms, nothing fabricated:**

  | Form | What is stored | What is shown |
  |---|---|---|
  | Source-only | the Source | the locator says where in the Source (volume, page, entry) |
  | Record-only | no Source | the Record's current Source is read live and labelled "via Record R…"; it is never copied into the Citation |
  | Source + Record | both, as entered | if the Record's Source later differs, both are shown and nothing is reconciled automatically |

- **Locator.**
  - `original_locator` is captured on creation and is immutable.
  - `locator` is the current, correctable value.
  - The citation text and note are correctable.
  - Every correction is in Change History, with actor and time.
  - Creation attribution (`created_by`, `created_at`) is immutable.
- **Reclassification.** Reclassifying the cited Record changes nothing about the Citation. A Citation refers to
  the Record's identity, never to its classification.
- **Lifecycle.** A Citation is ACTIVE or DELETED (recoverable), never removed. It cannot be created against a
  deleted Record.
- **No style formatting.** Phase 5 Source Statements will cite Citations.

## 7. Record lifecycle (D3-12; plan §10.5)
- **States.** ACTIVE or DELETED. `deleted_at` and `deleted_by` are set exactly when DELETED (CHECK). There is no
  purge, merge, cascade or physical deletion, and R numbers are never reused.
- **Delete** is a correction with history and a reason. It keeps every link, Role, Citation, grouping
  association and history entry.
- **Restore** returns the Record to ACTIVE, with everything intact.
- **Visibility of a deleted Record.** This follows the Phase 2 rule for deleted Files:
  - It is still reachable by its ID by anyone who could see it, marked *deleted*.
  - It is excluded from default lists. An "include deleted" list is available to editors.
  - It is shown in a File's "Used in Records", marked deleted.
- **What a deleted Record cannot do.**
  - Nothing about it changes except restore: no field, link, Role or Citation edits.
  - Grouping never extends it: its rows' continuation is broken.
  - No new Citation may be created against it.
- **No leaks through the deleted state.** Restriction checks (§8) apply to deleted Records and to links to
  deleted or destroyed Files exactly as to active ones. A destroyed File's tombstone appears through a Record
  only to readers allowed to see that File (`FileService.canSee`).

## 8. Restriction traversal (D3-04, P2-H; plan §10.3)
- **One policy.** A restricted Record is visible only with `VIEW_RESTRICTED_RECORDS` (Administrators).
  Restricted Files keep the Phase 2 rule (`FileService.canSee`). For everyone else, a hidden object and a
  nonexistent one are indistinguishable on every path: same "not found" answer and status.

| Path | Rule for a reader who may not see the object |
|---|---|
| Record page, ID lookup, guessed UUID or `R` number | not found |
| Record lists, Source and Session pages, File "Used in Records" | omitted |
| A NORMAL Record's Files | restricted Files omitted: no link, page designation, note, thumbnail or count. Positions are numbered among the visible Files, never by stored sequence |
| Citations | Citations of a restricted Record are treated as nonexistent |
| Capture Session worksheet and counts | restricted rows omitted; counts cover visible rows; positions renumbered |
| Grouping | runs use only visible rows **where omitted rows cannot affect archival grouping semantics**; ambiguous boundaries block or flag the affected group. Restricted rows stay unapplied for non-Administrators. Runs started by an Administrator are shown only to Administrators. A grouping-run group whose Record is restricted is omitted |
| Saving marks or overrides for a hidden File | not found, exactly as for a nonexistent File |
| Change History | entries about restricted Records and their Citations are removed. A value is withheld unless every `R`/`F` ID or UUID it names is visible (the File filter's tokenization, extended to `R`). Stored sequence numbers are not written to history |
| Friendly-number gaps | valid (BUILD-SPEC §8). They are no more informative than the gaps from Phase 2 Files |

- **No automatic propagation.** Restriction never propagates between Records and Files.
- **Tests.**
  - Every row above, for an Editor and a Viewer, plus the Administrator counterpart.
  - Mixed-visibility collections: a NORMAL Record with restricted, deleted and destroyed Files; a session
    interleaving restricted rows; a Citation set over both kinds of Record.
  - Comparison of the not-found responses for hidden and nonexistent IDs.

## 9. Scope control (plan §10.6)
- No File consolidation (F-006, F-007, P2-G).
- No bulk or generalized reclassification (BI-007).
- No CSV import, Person links, structured dates, Source Statements, managed-filename changes, or restriction
  management beyond D3-04.
- The Phase 3 completion report will list F-006, F-007 and BI-007 as outstanding and will not claim them.

## 10. Compatibility and gates (plan §10.7)
- **Migrations.** New migrations V0021–V0022 only; V0001–V0020 are untouched.
- **Existing tests.** All Phase 0–2 tests still run. Phase 1 and 2 schema tests are pinned to their versions.
- **Upgrade test.** A V0020 database containing Files, a quarantine object and a destruction tombstone
  upgrades with that data byte-for-byte unchanged.
- **Gates:**
  - R-001…R-006 and BI-001/BI-002;
  - the Phase 3 parts of O-003…O-006;
  - authorization and negative tests;
  - the full Windows/Linux × PostgreSQL 16/18 CI matrix.
- **Platform independence.** No Phase 3 operation reads or writes preservation originals. Each platform job
  checks that originals stay byte-identical and in place across the Phase 3 tests.

## Consequences
- Text kept as written now (dates, creator, Access Provider, jurisdiction, party names) is interpreted
  alongside, never replaced, when Phases 4–6 add structured objects (A-004).
- Behaviour that depends on the reader (grouping scope, renumbered positions) is the price of not leaking
  restricted Files. It is documented, and Administrators see the complete picture.
