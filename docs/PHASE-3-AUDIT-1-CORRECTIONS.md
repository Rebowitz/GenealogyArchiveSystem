# Phase 3 — responses to the independent Codex audits

This file keeps the complete history of both correction cycles: cycle 1 (first audit, `8b0f142`) below, then
cycle 2 (re-audit, `12cdd70`) at the end.

## Cycle 1 — response to the first independent Codex audit

| | |
|---|---|
| Audit | Phase 3 independent implementation, security, architecture and acceptance audit |
| Audited head | `8b0f142487fc1ce8beee8759e20efddb458b5ca4` (tested implementation `6d71856`, CI run 42519342916) |
| Verdict | **REVISIONS REQUIRED**: 4 BLOCKING, 4 MAJOR, 1 MINOR |
| Corrected implementation head | `3abd2f7` (all corrections and tests); verified at `8c5555c975cc9195581846f3cf94e0e3a5b1049f`, which adds only this report |
| CI evidence | CI run **42533095750** on `8c5555c`: all four jobs passed (details below). The run on `3abd2f7` itself (42533012227) was cancelled by the report push; the code is identical. |
| Status of every finding | **builder-corrected, pending independent verification**. Only Codex can close a finding, and only the owner can accept Phase 3. |

**Scope.** These are corrections to the nine findings and the tests that demonstrate them. Nothing else changed:
- no deferred requirement was implemented (F-006, F-007, BI-007);
- V0001–V0022 are unchanged;
- no approved document changed;
- preservation behaviour is unchanged.

The blocking findings were corrected first.

## Findings

### GAS-P3-001 — BLOCKING — worksheet exposes a restricted Record through a visible File
| | |
|---|---|
| Root cause | `CaptureSessionService` returned stored `CaptureRow`s, which carry `applied_record_id` and `applied_run_id`, filtered only by File visibility. `session.html` linked the Record unconditionally. |
| Files changed | `records/CaptureRow.java`, `data/CaptureSessionRepository.java`, `records/CaptureSessionService.java`, `templates/session.html` |
| Correction | `CaptureRow` gains a stored `applied` flag and a `forReader(recordVisible)` projection. Every row the service returns is projected for the actor: worksheet rows, the File's capture context, and the `correctOverrides` result. The applied Record is named only if the actor may see it, and the run is never named. The page shows "Grouped" instead of a hidden Record. Administrators are unchanged. |
| Regression tests | `Phase3AuditCorrectionsIT.gasP3_001_worksheetAndContextNeverNameAHiddenRecord` (Editor, Viewer and Administrator; Record restricted after grouping; worksheet, context and mutation result). `RecordsWebIT.gasP3_001_002_003_sessionPageDisclosesNothingHidden` (rendered HTML has neither the UUID nor the friendly ID, but does show "Grouped"; the Administrator page has the link). |
| Residual limitation | The Editor or Viewer sees that a visible row is grouped. That is the "applied/final indicator" the assignment asked to retain. |

### GAS-P3-002 — BLOCKING — batch-add result counts hidden Files
| | |
|---|---|
| Root cause | `addBatch` expanded the batch to every File in it, and `addFiles` counted the hidden ones as "not available". |
| Files changed | `records/CaptureSessionService.java` |
| Correction | A batch expands only to Files the actor can see now. Internally the add separates `hidden` from `destroyed`, and the batch result reports neither hidden members nor a File restricted between expansion and insert. Explicitly entered IDs keep the approved hidden-equals-nonexistent rule. |
| Regression tests | `Phase3AuditCorrectionsIT.gasP3_002_batchAddCountsOnlyVisibleFiles`: a hidden-only batch answers exactly like an empty one; a mixed batch counts only visible Files, on first add and on re-add; explicit hidden ID versus nonexistent ID. `RecordsWebIT…` covers the same over HTTP, with identical notices for a hidden-only and an empty batch. |
| Residual limitation | The batch picker still lists recent batches by date and uploader; it shows no counts. The audit did not raise this. |

### GAS-P3-003 — BLOCKING — historical run summaries ignore current restrictions
| | |
|---|---|
| Root cause | `GroupingService.runs` returned the stored counters, filtered only by the starter's historical `restricted_view`. |
| Files changed | `records/GroupingService.java`, `records/GroupingRunView.java` (new), `data/GroupingRunRepository.java` (`appliedFiles`), `templates/session.html` |
| Correction | Readers now get a `GroupingRunView`. A reader who can see restricted Files and Records gets the stored accounting. Anyone else gets: (a) only runs started without restricted view; (b) applied rows counted as that run's applied Files they can see now; (c) Records created or extended counted as those they can see now; (d) held, conflicted and ungrouped counts omitted, shown as "-"; and (e) no run at all when nothing visible remains. Stored `grouping_run` rows are never rewritten. |
| Regression tests | `Phase3AuditCorrectionsIT.gasP3_003_runSummariesFollowCurrentRestrictions`: a formerly public Editor run, then one File restricted, then everything restricted (Editor and Viewer see no run; the Administrator's stored accounting is unchanged and the stored row is byte-equal). `…gasP3_003_restrictedRecordIsNotCountedForOthers`. |
| Residual limitation | The immediate post-apply notice to the person who ran grouping still reports that run's own counts (held rows are counted over rows visible to them, as approved). |

### GAS-P3-004 — BLOCKING — chunks commit with a stale File snapshot
| | |
|---|---|
| Root cause | Chunk validation checked only worksheet `row_version`. A File's restriction or lifecycle lives on `archival_file`, and the link was made from the planning-time `ArchivalFile`. |
| Files changed | `records/GroupingService.java`, `data/ArchivalFileRepository.java` (`lockShared`), `data/GroupingRunRepository.java` (`held`) |
| Correction | Each chunk transaction locks in a fixed order: the session, then the worksheet rows (version check), then each File and each applied row the group continues from (`FOR SHARE`, so a restriction or lifecycle change waits for the commit), then the target Record (`FOR UPDATE`). It then re-reads each of them. A File or continuation source the starter can no longer see, or a target Record that is hidden or deleted, holds the group's remaining work. A File whose lifecycle changed is a conflict. Links are made with the freshly read File. Chunks already committed stay, and the group is never recorded as complete. Held rows are counted only where still visible to the starter. |
| Regression tests | `Phase3AuditCorrectionsIT`: `gasP3_004_fileRestrictedAfterPlanIsNotApplied` (AFTER_PLAN; group held, unrelated group applied, no link, Administrator later applies); `…fileDeletedAfterPlanIsAConflict` (AFTER_PLAN deletion); `…restrictionBetweenChunksHoldsTheRest` (1,500 rows; restriction after chunk 1; 1,000 links kept, group not complete, Administrator finishes in order without duplicates); `…hiddenContinuationSourceAfterPlanIsHeld`. The existing 2,345-row, 1,001-row, replay and safeguard-1 tests are unchanged. |
| Residual limitation | None known. |

### GAS-P3-005 — MAJOR — a disappearing cutoff becomes apply-all
| | |
|---|---|
| Root cause | The cutoff was validated before the lock and the plan. Planning stopped only if it met the cutoff, so a missing cutoff meant "all rows". |
| Files changed | `records/GroupingService.java`, `records/GroupingFaults.java` (BEFORE_PLAN seam), `records/CaptureSessionService.java` (`removeFile` lock order) |
| Correction | The cutoff's position is captured at validation. Planning (one snapshot, under the session's grouping lock) requires the cutoff to be present at that same position and visible. Otherwise the request is rejected like a nonexistent File. Planning now happens before the run row is written, so a rejected request applies nothing and records no run. `removeFile` now locks the session before the row, the same order as every other path. |
| Regression tests | `Phase3AuditCorrectionsIT.gasP3_005_cutoffRemovedBeforePlanningRejectsTheRequest` (deterministic removal at BEFORE_PLAN, rejected identically to a nonexistent File, nothing applied, no run) and `…cutoffMovedBeforePlanningRejectsTheRequest` (removed and re-added at the end, then R-005 with an intact cutoff). `GroupingIT.replayAfterInterruptionAtEveryBoundary…` now covers BEFORE_PLAN as well (no run recorded). |
| Residual limitation | Removing the cutoff after planning does not affect the run, which is already bounded by its snapshot. |

### GAS-P3-006 — MAJOR — Citations of a deleted Record stay editable
| | |
|---|---|
| Root cause | `CitationService.correct` never checked the cited Record. `create` checked it without a lock. |
| Files changed | `records/CitationService.java` |
| Correction | `create` and `correct` (which also handles delete and restore) lock the cited Record `FOR UPDATE`, as Record deletion does, and require it to be ACTIVE. Citations of a deleted Record stay readable. Source-only Citations are unaffected. |
| Regression tests | `Phase3AuditCorrectionsIT.gasP3_006_citationsOfADeletedRecordAreFrozen` (correct and delete refused; still readable and unchanged; Source-only unaffected; editable again after restore). `…citationCreationRacingRecordDeletion` (10 concurrent create/delete races: the Record always ends DELETED, and any created Citation predates the deletion). |
| Residual limitation | None known. |

### GAS-P3-007 — MAJOR — a link note can be lost without history
| | |
|---|---|
| Root cause | FILE_LINKED and FILE_UNLINKED history omitted the note. |
| Files changed | `records/RecordService.java` |
| Correction | `note` is recorded on FILE_LINKED (set) and on FILE_UNLINKED (before → none). The whole-event restricted-File redaction applies unchanged, because those events carry the `file` field. |
| Regression tests | `Phase3AuditCorrectionsIT.gasP3_007_linkNoteSurvivesUnlinkInHistory`: create → unlink with no correction; create → correct → unlink; then the File is restricted and the Editor and Viewer see none of it. |
| Residual limitation | Links removed before this correction have no recoverable note. No Phase 3 data exists outside test databases. |

### GAS-P3-008 — MAJOR — applied worksheet marks can be changed by direct SQL
| | |
|---|---|
| Root cause | V0021's `file_capture_context_applied_final` trigger protected only the applied Record, the applied run and deletion. |
| Files changed | `db/migration/V0023__applied_worksheet_marks_final.sql` (new) |
| Correction | A forward-only V0023 replaces the trigger function. Once a row is applied, it also rejects distinct changes to `start_new_record`, `record_group`, `page_designation` and `classification_code`. Capture-context overrides stay correctable. **Migration strategy:** V0021 is not edited, because every database that already applied it would fail Flyway checksum validation. `CREATE OR REPLACE FUNCTION` changes no row and keeps the V0021 trigger binding. |
| Regression tests | `Phase3SchemaIT.appliedWorksheetMarksAreFinalInTheDatabase`: a V0022 database with an applied row upgrades to V0023 (one migration, row unchanged). Each of the four marks, changed to a value or to NULL, is rejected. Override changes succeed. Unapplied rows stay editable. The upgrade test now expects V0021–V0023 (3 migrations). |
| Residual limitation | None known. |

### GAS-P3-009 — MINOR — smoke status check ignores the producer's exit status
| | |
|---|---|
| Root cause | `contains "$(java … status)" "Ingestion"` takes `contains`'s status, so a failing `status` that printed the text passed. |
| Files changed | `scripts/smoke-test.sh`, `scripts/smoke-checks.sh` (new, sourced), `scripts/ci/smoke-checks-test.sh` (new), `.github/workflows/ci.yml` (one step) |
| Correction | `require_output <fragment> <message> <command…>` runs the command once and requires exit 0 and the fragment. The race-free page checks (`contains`) and the EXIT-trap cleanup are kept. The helpers are shared, so the regression test exercises exactly what the smoke test uses. |
| Regression tests | `scripts/ci/smoke-checks-test.sh`, run in CI on every job. Cases: text with exit 0 passes; text with exit 7 fails; exit 0 without the text fails; 600 KB of output around the text passes (no pipeline race); `contains` present and absent. Run locally: all 6 cases pass. The old shape, reproduced locally (`contains "$(producer exiting 7)"`), passed, which demonstrates the defect. |
| Residual limitation | None known. |

## Focused results and full CI evidence
CI run **42533095750** on `8c5555c975cc9195581846f3cf94e0e3a5b1049f`. Each job's full log was inspected; the
whole-workflow indicator was not relied on.

| Job | Tests | Fail / Err / Skip | Phase3AuditCorrectionsIT | Phase3SchemaIT | GroupingIT | RecordsWebIT | Smoke-check helpers | Packaged smoke | Native service | Worker kill/restart |
|---|---|---|---|---|---|---|---|---|---|---|
| ubuntu-24.04 / PostgreSQL 16 (128891518638) | 692 | 0 / 0 / 0 | 13 passed | 9 passed | 20 passed | 3 passed | all 6 cases passed | passed | systemd passed | `WorkerProcessKillIT` passed |
| ubuntu-24.04 / PostgreSQL 18 (128891518300) | 692 | 0 / 0 / 0 | 13 passed | 9 passed | 20 passed | 3 passed | all 6 cases passed | passed | systemd passed | passed |
| windows-2025 / PostgreSQL 16 (128891518546) | 692 | 0 / 0 / 0 | 13 passed | 9 passed | 20 passed | 3 passed | all 6 cases passed | passed | Windows service passed | passed |
| windows-2025 / PostgreSQL 18 (128891518524) | 692 | 0 / 0 / 0 | 13 passed | 9 passed | 20 passed | 3 passed | all 6 cases passed | passed | Windows service passed | passed |

Tests per job: 677 before the corrections, 692 after (+13 in `Phase3AuditCorrectionsIT`, +1 in `Phase3SchemaIT`, +1 in
`RecordsWebIT`). All Phase 0–2 tests still run. So do the earlier Phase 3 tests: the 2,345-row and 1,001-row grouping,
interruption and replay, capture-context inheritance and isolation, Citation identity and original locator,
restriction traversal, and the V0020→Phase 3 upgrade.

**Focused checks run locally before CI:**
- V0023 applied to a local PostgreSQL 16 database holding an applied worksheet row: all four mark changes were
  rejected; override and version updates were accepted.
- `scripts/ci/smoke-checks-test.sh`: all 6 cases passed.
- The defective status-check shape, reproduced in isolation, passed although the producer exited 7.

The Java tests themselves run only in CI.

## Changes to earlier tests
- `GroupingIT.replayAfterInterruptionAtEveryBoundaryCreatesNoDuplicates` iterates over every fault point, now including BEFORE_PLAN. For BEFORE_PLAN it expects no run row, because nothing is recorded before planning. For every other point the assertions are unchanged.
- `Phase3SchemaIT`'s upgrade count rises from 2 to 3 for V0023.

No other assertion was changed or removed.

---

# Second independent audit (re-audit of `12cdd70`) — correction cycle 2

| | |
|---|---|
| Audited head | `12cdd703507f8ce211b7a3daad462c62c9cefbbb` |
| Verdict | **REVISIONS REQUIRED** |
| Independently closed by Codex | GAS-P3-001, 002, 005, 006, 007, 008, 009. They are not reopened. The only change touching them is that the GAS-P3-002 tests now use authorized batches (see GAS-P3-010). |
| Remaining | GAS-P3-003 (PARTIALLY CORRECTED), GAS-P3-004 (PARTIALLY CORRECTED), GAS-P3-010 (OPEN). All three are BLOCKING. |
| Corrected implementation SHA | `16f41b8` (corrections and tests), plus `b9bf869` (fix to one new test assertion; see below) |
| CI-tested SHA | `b9bf8690bdfd57257495de6f78605b021512b4a1`, CI run **42544922421** |
| Final documentation-only head | the commit adding this section, which adds documentation only (adds only this section and the PR hand-off) |
| Status | All three are **builder-corrected, pending independent verification** |

**Source document.** The full re-audit report (`phase-3-independent-reaudit-12cdd70.md`) was not among the files the
owner provided in this session. These corrections follow the owner's second correction assignment, which states
each finding, the required correction and the mandatory tests. Codex should check them against the full report.

### GAS-P3-003 — BLOCKING — run contributions attributed to the File and the Record
| | |
|---|---|
| Root cause | The cycle-1 projection counted an applied File whenever the File was visible. It counted Records separately and kept the run whenever either count was non-zero. So a run whose only Record became restricted, with its Files still NORMAL, was still listed with its applied count. |
| Corrected source files | `records/GroupingService.java` (`runs`), `data/GroupingRunRepository.java` (`contributions` replaces `appliedFiles`) |
| Implementation | A contribution is one row the run applied: (File, the Record it was applied to), read from the permanent worksheet marks. For a reader without both restricted-File and restricted-Record visibility, a contribution counts only if the reader may see both, now. Every reader-facing figure comes from that single set: applied rows; created and extended Records (distinct visible Records among the authorized contributions, split by whether this run created them); and whether the run is shown at all, since a run with no authorized contribution is omitted along with its timestamps and state. Held, conflicted and ungrouped counts stay omitted. Stored `grouping_run` rows and the Administrator view are unchanged. The per-File "Grouped" indicator is unchanged. |
| Regression tests | `Phase3Audit2CorrectionsIT`: `gasP3_003_runOfOnlyARestrictedRecordDisappears` (mandatory tests 1, 4 and 5: Editor and Viewer, Administrator accounting, stored row unchanged, "Grouped" kept); `gasP3_003_mixedRunCountsOnlyAuthorizedContributions` (tests 2 and 5: a hidden Record with NORMAL Files plus one hidden File in a visible Record; un-restricting restores the count at once); `gasP3_003_extendedRecordFollowsItsVisibility`. `RecordsWebIT.gasP3_003_010_sessionPageHidesRestrictedRunsAndOthersBatches` (test 3 over HTTP, for a fresh Viewer and Editor). |
| Residual limitation | None known. |

### GAS-P3-004 — BLOCKING — later-chunk classification source
| | |
|---|---|
| Root cause | Under the approved rule, a new Record takes the first classification in its group. It was computed from the whole cached group at planning time and written in chunk 1, before the supplying row, which can sit in a later chunk, had its worksheet version, File authorization or lifecycle checked. |
| Corrected source files | `records/GroupingService.java` (`applyGroup`, `applyChunk`) |
| Implementation | The plan now carries the classification-source row instead of the bare value; the selection rule is unchanged. In the transaction that creates the Record (only there, because classification is used only at creation), before anything is written, that row is locked in the usual order (worksheet rows before Files). Its classification is used only if the row is unchanged since planning (same version, not applied, same session) and its File, share-locked, is visible to the person applying and ACTIVE. A changed row or a lifecycle change is a conflict: the group is left whole for the next run, which plans again from current data. A hidden File holds the group. Either way nothing is written: no Record, no history, no "DEED". The Record's other creation input, the session's Source, comes from the session row already locked in that transaction. Later chunks only add links from their own rows, which are already validated. Committed chunks, durable group identity, the 1,000-row transaction bound and unlimited Record size are unchanged. |
| Regression tests | `Phase3Audit2CorrectionsIT`: `gasP3_004_hiddenLaterChunkClassificationSourceIsNotUsed`, the mandatory scenario: a 1,001-row group, rows 1–1,000 blank and row 1,001 DEED, row 1,001's File restricted at AFTER_PLAN, applied by an Editor. No Record or history anywhere has DEED, no Record is created, nothing is applied or completed, and 1,000 rows are held. An Administrator then applies it as DEED, with all 1,001 links in order. `gasP3_004_changedLaterChunkClassificationSourceIsAConflict`: the source is changed to DTRUST at AFTER_PLAN, giving 1,001 conflicted rows and no Record; the next run uses DTRUST. `gasP3_004_deletedClassificationSourceIsAConflict`. The `GroupingIT` 2,345-row and 1,001-row tests are unchanged. |
| Residual limitation | None known. |

### GAS-P3-010 — BLOCKING — upload-batch metadata authorization
| | |
|---|---|
| Root cause | `recentBatches` listed every user's batches (UUID, date, uploader, note) and `addBatch` accepted any batch. The accepted Phase 2 rule (`IngestionService.batch`: creator or MANAGE_PRESERVATION, otherwise "not found") was not applied. |
| Corrected source files | `records/CaptureSessionService.java` (`recentBatches`, `addBatch`, `maySeeBatch`), `data/CaptureSessionRepository.java` (`recentBatches(createdBy, limit)` filtered in SQL before the limit, `batchCreator`) |
| Implementation | The picker lists only the actor's own batches, or every batch for MANAGE_PRESERVATION (Administrators), filtered before the 50-row limit. `addBatch` applies the same rule: another user's batch, whether mixed, hidden-only or empty, expands to no Files, which is exactly what a nonexistent batch does. GAS-P3-002's visible-only expansion and counting are unchanged within an authorized batch. Ingestion permissions and the batch-detail rule are unchanged. Cross-user batch selection is not needed by an approved workflow: researchers add their own uploads, and Administrators add any. So no cross-user projection was added. |
| Regression tests | `Phase3Audit2CorrectionsIT.gasP3_010_batchPickerFollowsTheBatchRule` (mandatory tests 1, 2, 3 and 5: own batch listed even when another user has 58 newer ones; another user's mixed, hidden-only and empty batches are never listed; the Administrator lists all). `gasP3_010_otherUsersBatchesAnswerLikeNonexistentOnes` (tests 3 and 6: answers identical to a random UUID; the Administrator adds a mixed batch with counts (2, 0, 0); a hidden-only batch answers like an empty one even for its creator). `RecordsWebIT.gasP3_003_010_…` (test 4: the Editor's and Viewer's session HTML has no other-user batch UUID, uploader name or note, while the Administrator's has all three). |
| Effect on closed tests | `Phase3AuditCorrectionsIT.gasP3_002_…` and `RecordsWebIT.gasP3_001_002_003_…` had the Editor add batches the Administrator created, which the accepted rule now treats as nonexistent. They now use the Editor's own batches, with members restricted by an Administrator after upload. Their GAS-P3-002 assertions are unchanged: hidden-only equals empty, a mixed batch counts only visible Files, and explicit IDs follow hidden-equals-nonexistent. |
| Residual limitation | An Editor cannot add another user's batch as a unit. They can add that user's visible Files by ID or range, as before. |

### Focused and full CI evidence (cycle 2)
CI run **42544922421** on `b9bf8690bdfd57257495de6f78605b021512b4a1`. Each job's full log was inspected.

| Job | Tests | Fail / Err / Skip | Phase3Audit2CorrectionsIT | Phase3AuditCorrectionsIT | GroupingIT | Phase3SchemaIT | RecordsWebIT | Smoke-check helpers | Packaged smoke | Native service | WorkerProcessKillIT |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ubuntu-24.04 / PostgreSQL 16 (128927829542) | 701 | 0 / 0 / 0 | 8 | 13 | 20 | 9 | 4 | passed | passed | systemd passed | passed |
| ubuntu-24.04 / PostgreSQL 18 (128927829533) | 701 | 0 / 0 / 0 | 8 | 13 | 20 | 9 | 4 | passed | passed | systemd passed | passed |
| windows-2025 / PostgreSQL 16 (128927829340) | 701 | 0 / 0 / 0 | 8 | 13 | 20 | 9 | 4 | passed | passed | Windows service passed | passed |
| windows-2025 / PostgreSQL 18 (128927829532) | 701 | 0 / 0 / 0 | 8 | 13 | 20 | 9 | 4 | passed | passed | Windows service passed | passed |

Every test in every listed class passed. There are 701 tests per job: 692 after cycle 1, plus 8 in
`Phase3Audit2CorrectionsIT` and 1 in `RecordsWebIT`. All Phase 0–2 tests still run, as do the cycle-1 correction
tests and the V0020→V0023 upgrade tests.

**Earlier cycle-2 run.** Run 42544204664 on `16f41b8` failed one new assertion. The 3 other tests in its class,
and the full suite on both Linux jobs, passed. `gasP3_010_batchPickerFollowsTheBatchRule` expected the other
Editor's three oldest batches in a picker correctly limited to the 50 newest, and that Editor had 58. `b9bf869`
asserts instead that those 50 newest are all the other Editor's and never the first Editor's. No production code
changed.
