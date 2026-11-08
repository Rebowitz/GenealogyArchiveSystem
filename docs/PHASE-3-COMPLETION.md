# Phase 3 completion report — Repository, Source and Archival Records

Prepared by the implementation agent for independent review. **Phase 3 is not accepted.** Formal acceptance
requires an independent Codex audit, correction of confirmed findings and owner approval. The Phase 3 PR is a
draft and has not been merged; no Phase 3 tag exists.

## Baseline and head
| | |
|---|---|
| Starting baseline | `main` at `9bb2952` (Phase 2 merge, PR #5); Phase 2 accepted at `464d2d0` |
| Governing documents | [`PHASE-3-PLAN.md`](PHASE-3-PLAN.md) (owner-final `e9dad0d`), [`PHASE-3-DECISIONS.md`](PHASE-3-DECISIONS.md) and [ADR-0011](architecture/ADR-0011-records.md) (owner-approved `432af5b`, including the two mandatory safeguards) |
| Branch / PR | `phase-3` / draft PR (not merged) |
| Implementation head | see the PR description (the verified head and its CI run IDs) |
| Draft reuse | `phase-3-wip` (written before the §11 gate) was checked against the approved design and reused only where it conformed; see §8 |

## 1. What was implemented
- **Catalogue (O-003…O-006, D3-06).** Repositories (`RP`), Sources (`S`) with an optional Repository, and the
  historical creator, Access Provider and jurisdiction as written in their own Source fields.
- **Archival Records (`R`; R-001…R-003, R-006, A-003, D3-05, D3-10).**
  - Every field beyond identity is optional.
  - Classification is correctable and never touches Files.
  - Record date and filing date are kept as written, in separate fields.
  - A researcher-entered **verbatim transcription** and a separate **researcher-written abstract**, each with its
    own label and history.
- **File links.** Many-to-many File links with page designation independent of image order. Image order moves
  past visible neighbours only.
- **Record Roles (D3-01).** A role code, the party as written (optional) and a note. No Persons or relationships
  are created.
- **Vocabularies (D3-08).** Classifications `DEED`, `DTRUST` and the nine spec roles. Administrators add,
  relabel and retire terms; codes are never deleted or reused.
- **Citations (`CT`; plan §10.1).**
  - Source-only, Record-only and Source + Record forms. Targets are immutable.
  - An immutable `original_locator` sits beside the correctable locator.
  - A Record-only Citation stores no Source; the Record's Source is shown "via the Record".
  - Lifecycle is recoverable.
- **Capture Sessions (`CS`; BI-001, UI-004/005, plan §10.2).**
  - Session context is inherited by its Files.
  - Each overridable field has three states: *inherit*, *own value* or *not known for this File*. A value is
    stored exactly in VALUE mode (CHECK).
  - Inheritance is resolved when read. A session correction is recorded only in the session's history.
  - An override is recorded in the File's history as mode plus value.
  - Files are added from an upload batch or by ID lists and ranges.
- **Courthouse grouping (R-004, R-005, D3-02, plan §10.4, owner safeguards 1 and 2).**
  - Each run has durable identity (`grouping_run`) and per-chunk progress (`grouping_run_group`), with a stable
    group-to-Record identity per session (`capture_session_group`, `g:` and `s:` keys).
  - Planning covers *all* rows, so a hidden boundary row is respected. A group that depends on rows the applier
    cannot see is held for an Administrator, never silently merged.
  - Rows are applied in chunks of at most 1,000 rows. That is a per-transaction bound, not a Record size limit.
  - Replay is safe: applied rows are final (trigger) and links are keyed.
  - A row edited after planning makes its group a conflict, and the group is never split.
  - A per-session advisory lock allows one run at a time. A stale RUNNING run is closed as INTERRUPTED.
  - An optional *apply through File* bound lets a researcher stop part-way (R-005).
- **Record lifecycle (D3-12, plan §10.5).** ACTIVE or DELETED. A deleted Record:
  - stays reachable by its ID, marked deleted;
  - is left out of default lists and grouping;
  - can only be restored, with everything kept.
- **Restriction (D3-04, plan §10.3).**
  - Restricted Records are Administrator-only on every path.
  - A NORMAL Record never discloses its restricted Files: no link, page, note, count, thumbnail or position gap.
  - Stored sequence numbers are never displayed or written to history.
  - History drops whole any Record or Capture Session entry about a hidden File.
  - Administrator grouping runs are shown only to Administrators.
- **Web pages.**
  - Repositories, Sources, Capture Sessions (worksheet, add Files, apply, runs) and Records (links, roles,
    citations, lifecycle).
  - Citations and Vocabularies (Administrator only).
  - The File page gains "Used in Records" and the tri-state capture context.
- **Reference Acceptance Archive (§64).** A synthetic courthouse deed book (`src/test/resources/reference-archive/`)
  built through the services by `ReferenceCourthouseIT`.

## 2. Schema / migration changes (V0001–V0020 untouched)
- **V0021:**
  - `record_classification` and `record_role_type`, with seeds;
  - `repository`, `source`, `capture_session`;
  - `file_capture_context`, with tri-state override modes, worksheet marks, and applied record/run (final by
    trigger);
  - friendly series `RP`, `S`, `CS`.
- **V0022:**
  - `archival_record`, `record_file`, `record_role`;
  - `grouping_run` (final once finished) and `grouping_run_group` (chunk rows, ≤1,000 rows each);
  - `capture_session_group` (namespaced keys);
  - `citation` (immutable targets and `original_locator`);
  - friendly series `R`, `CT`.
- **Conventions.** All tables are permanent (no delete or truncate) except correctable associations
  (`record_file`, `record_role`, unapplied `file_capture_context` rows). Identity columns are immutable by trigger.
- **D3-07 prefix check:** compatible, as recorded in the decisions document and pinned by `FriendlyPrefixTest`.

## 3. File / storage behaviour
None. No Phase 3 operation reads, writes, renames or moves a preservation original. Tests on both platforms assert
that originals stay byte-identical, keep their managed filename and stay at the same path through grouping and
reclassification (`GroupingIT`, `RecordsIT.r006…`, `ReferenceCourthouseIT`).

## 4. Acceptance tests implemented and run
| Requirement | Tests |
|---|---|
| R-001, R-002, R-003 | `RecordsIT.r001_r002_r003_filesAndPages`, `pagesAndImageOrderAreIndependent`, `ReferenceCourthouseIT` |
| R-004 | `GroupingIT.r004_startNewRecordAndExplicitGroups`, `explicitGroupsSpanBatchesAndRuns`, `ReferenceCourthouseIT` |
| R-005 | `GroupingIT.r005_stopAfter83Of200`, `r005_leadingBlankRowsStayUngrouped`, `ReferenceCourthouseIT` |
| R-006, CT-003, CT-004, A-001, A-008 | `RecordsIT.r006_classificationCorrection` (R01842, checked on disk), `ReferenceCourthouseIT` |
| A-002, D3-12 | `RecordsIT.deletionIsRecoverableAndPreservesEverything`, `Phase3SchemaIT` |
| A-003, UI-003 | `RecordsIT.anIncompleteRecordIsValid`, `Phase3SchemaIT` |
| A-009, D3-01 | `RecordsIT.rolesArePartiesAsWritten` |
| O-003…O-006 (Phase 3 parts) | `RecordsIT.o003_to_o006_…`, `ReferenceCourthouseIT` |
| BI-001, BI-002, UI-004/005 | `CaptureContextIT` |
| EV-003 / D3-05 | `RecordsIT.datesAsWrittenAndSeparateTranscriptionAndAbstract`, `ReferenceCourthouseIT` |
| Plan §10.1 citations | `RecordsIT.citationIdentityAndProvenance`, `r006…`, `Phase3SchemaIT.citationTargetsAndOriginalLocatorAreImmutable` |
| Plan §10.2 tri-state | `CaptureContextIT`, `Phase3SchemaIT.overrideModesAreTriState` |
| Plan §10.3 restriction traversal | `RecordRestrictionIT` (hidden vs nonexistent compared on every path; mixed-visibility Records, sessions and Citation sets), `RecordsWebIT.restrictedRecordsAndFilesStayInvisibleOverHttp` |
| Plan §10.4 replay, safeguards 1 and 2, PF-002 (partly) | `GroupingIT` (interruption at every fault point, stale runs, lock, conflicts, hidden Start New Record / explicit group / mixed group / hidden applied row, 2,345-row group interrupted after a chunk and retried, 1,001-row group) |
| Plan §10.5 lifecycle | `RecordsIT.deletionIsRecoverableAndPreservesEverything`, `GroupingIT.aDeletedRecordIsNotExtended` |
| SEC-001…004, 007, 008 | `RecordsIT.viewersCannotChangeRecords`, `vocabularyTermsAreRetiredNeverDeleted`, `RecordRestrictionIT`, `RecordsWebIT`, `AccessPolicyTest` |
| D3-07 | `FriendlyPrefixTest` |
| Schema and upgrade (plan §10.7) | `Phase3SchemaIT` (V0020 database with an active File, a destroyed File's tombstone and a quarantine object upgrades with that data unchanged), `Phase2SchemaIT` pinned to V0020 |
| Browser | `RecordsWebIT` (courthouse workflow end to end; every page for each role) |

## 5. Test results
The CI run IDs for the verified head are in the PR description. Every job of the Windows/Linux × PostgreSQL 16/18
matrix runs the full suite (unit + PostgreSQL integration, including all Phase 0–2 tests), the packaged smoke test
and the native service-manager test. No test is skipped on any platform.

## 6. Known limitations
- **Reader-dependent grouping (safeguard 1, by design).** A non-Administrator's run leaves held groups unapplied
  until an Administrator applies the session. The editor sees that rows were "held for an Administrator", which
  tells them something in that group needs Administrator attention. That is the visible flag the owner required,
  and it is the only signal: counts cover visible rows only.
- **Applied grouping is final.** Rows applied by grouping cannot be re-marked or removed from their session. Links
  can still be corrected on the Record, but the worksheet keeps the record of what was applied.
- **Group-key namespace.** Explicit `record_group` keys are scoped to one session. Joining Files from two sessions
  into one Record is done by linking on the Record.
- **No structured interpretation yet.** Dates, parties, creator, Access Provider and jurisdiction are text as
  written. Phases 4–6 add structured objects alongside them (A-004).
- **Catalogue objects carry no restriction** (owner-approved choice 7). Restricting them is Phase 9 policy.

## 7. Unresolved specification questions
None blocking. The seven owner-approved choices and both safeguards are implemented as approved.

## 8. Deviations from the approved plan
- **`phase-3-wip` reuse.** The draft was written before the §11 gate. It was reused only after checking it against
  the approved design:
  - consolidation and bulk reclassification code was removed (D3-03, D3-11);
  - the transcription field was split in two (D3-10);
  - overrides were made tri-state (10.2);
  - grouping was redesigned for both safeguards;
  - citation targets and the original locator were made immutable (10.1).
- **R-005 "apply through File".** The plan does not name it. Without it, applying a half-marked worksheet would
  sweep the unmarked trailing rows into the last Record (R-004's continuation rule), which defeats R-005. It is
  optional, and the default applies everything.
- **Scope control (plan §10.6).** Not implemented, and not claimed by this report: F-006/F-007 (consolidation,
  P2-G) and BI-007 (bulk reclassification) remain outstanding requirements.
