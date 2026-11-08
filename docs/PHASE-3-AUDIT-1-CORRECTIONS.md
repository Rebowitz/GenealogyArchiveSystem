# Phase 3 — response to the first independent Codex audit

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
