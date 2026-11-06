# Phase 2 audit corrections

Corrections for the independent Codex Phase 2 audit of implementation
`438649f2d0a88cf861675c5cb8234f46ca607eaa` (reviewed head `587a79ea949447442b811cb01a1ea4051cb8198b`):
9 MAJOR and 2 MINOR findings. **Phase 2 is not accepted**; the corrected commit goes back for an
independent audit. PR #5 stays a draft and unmerged.

Governing rules kept: V0001–V0013 unchanged (all schema changes are in the new V0014); no test or
security check weakened; no approved requirement changed. Two existing tests asserted behaviour the audit
found defective and were replaced (listed below). The exact corrected SHA, CI run and test counts are in
the PR description.

## Correction matrix

| Finding | Correction | Regression tests |
|---|---|---|
| **GAS-P2-003** (MAJOR) restricted metadata in Change History | `HistoryVisibility` hook applied by `HistoryService` to global and per-object reads; `RestrictedFileHistory` (preservation) removes entries about Files restricted now (so earlier entries are covered too) and withholds fields/reasons naming a restricted File's UUID, friendly ID, or content it uses; the global query excludes restricted Files in SQL so the page stays full. Upload status/batch lists withhold a File reference restricted since. | `Phase2AuditCorrectionsIT.gasP2_003_*` (restricted at ingestion; restricted later; references from visible history: replacement, reason, shared content; upload status); `FileWebIT.gasP2_003_theHistoryPageDoesNotShowRestrictedFiles` |
| **GAS-P2-004** (MAJOR) derivatives of a restricted parent | V0014 triggers make inheritance a database invariant: restricting a File restricts derivatives at every depth; a derivation recorded under a restricted parent (row-lock ordered) is restricted; a derivative of a restricted File cannot be lowered. The worker re-checks ancestors under the parent's row lock when committing a derivative; `FileService.correct` records each inherited restriction with its cause and uses an ancestor-aware lowering rule. V0014 also makes existing data consistent. | `gasP2_004_restrictingAParentRestrictsExistingDerivativesAtEveryLevel`, `…ADerivativeReceivedBeforeItsParentWasRestrictedIsCommittedRestricted` (pending upload), `…RemovingAnInheritedRestrictionStaysDeliberate` (application and database refuse; lowering stays per File) |
| **GAS-P2-005** (MAJOR) sharing without protection | Before sharing, the worker verifies the stored original against its authoritative size/SHA-256, compares bytes with the upload, and re-applies lost read-only protection (recording a `NOT_PROTECTED` finding) or blocks with `PROTECTION`; the commit re-checks that the verified generation is still current. | `gasP2_005_sharingAnUnprotectedOriginalRestoresProtectionBeforeReportingSuccess`, `…IfProtectionCannotBeRestoredTheUploadBlocksAndIsNotArchived`; existing `IngestionIT.f004_*` |
| **GAS-P2-006** (MAJOR) destruction left quarantined bytes | Destruction removes every known copy: each generation's storage path (any state), quarantined generations' copies, and quarantine-ledger entries of the content (interrupted moves settled first); `BYTES_REMOVED` only after re-checking every location, otherwise `CLEANUP_INCOMPLETE` naming what remains. Destroyed generations keep their quarantine path (V0014 constraint). | `gasP2_006_destroyingAfterARepairRemovesTheQuarantinedGenerationToo`, `…DestructionFinishesAndRemovesAQuarantineMoveThatWasInterrupted`, `…DestructionRemovesQuarantinedPartialCopiesOfTheContent`; existing `DestructionIT.tx007_*` |
| **GAS-P2-007** (MAJOR) lost storage subdirectory | `ManagedVolume.locate`: on a volume whose marker verified, a missing `originals`/shard directory means "not present" (links and non-directories still refused; a missing root stays "volume unavailable"). Used by scans, downloads (which now record the content as MISSING), thumbnails, re-verification, quarantine moves and destruction. | `gasP2_007_aLostShardDirectoryIsMissingContentAndCanBeRepaired`, `…ALostOriginalsDirectoryIsMissingContentAndCanBeRepaired` |
| **GAS-P2-008** (MAJOR) partial-copy move/commit window | Quarantine ledger `quarantine_object` (V0014): intent recorded and committed before every move (partial, unverified, cancelled copies and damaged generations), confirmed after; `QuarantineMoves.settle` finishes an entry from what is on disk and is idempotent; workers settle their operation's entries before deciding anything; recovery settles the rest under the operation lock. | `gasP2_008_aStopAfterThePartialCopyMovedButBeforeItWasRecordedIsReconciled` (with repeated recovery), `…AStopAfterTheMoveWasRecordedButBeforeTheOperationWasReplannedIsReconciled`, `…ADatabaseFailureAfterTheMoveIsRetriedWithoutLosingTheRecord`; `WorkerProcessKillIT` |
| **GAS-P2-009** (MAJOR) cancellation moved a foreign object | `file_operation.target_created` (V0014) is set immediately after the operation's exclusive create succeeds and cleared on replanning. Only an owned target is measured, adopted, quarantined or cancelled; any other object is a `COLLISION` across retries and volume waits. Cancellation leaves it untouched and says so; scans report it. | `gasP2_009_cancellingAWaitingOperationNeverMovesAnObjectItDidNotCreate` (the audit's exact scenario), `…ACollisionStaysUntouchedAcrossRetriesAndCancellation`; existing `RecoveryIT.tx005_*` |
| **GAS-P2-001** (MAJOR) live receiver abandoned | Receiver lease `receiver_alive_at` (V0014) renewed on a timer by the receiving process independently of bytes; recovery abandons only when neither lease nor progress was renewed within `files.receivingTimeout`. The live-request timeout (server read timeout) ends a silent client's upload in the receiver itself, with its own message. | `gasP2_001_aLiveReceiverBlockedLongerThanTheTimeoutKeepsItsUpload`, `…ADeadReceiverIsAbandonedAndCleanedOnceItsLeaseLapses`; `FileWebIT.gasP2_001_aClientThatStopsSendingIsEndedByItsLiveReceiverNotByRecovery`; existing `RecoveryIT.requirement1_*` |
| **GAS-P2-002** (MAJOR) no real process termination | `WorkerProcessKillIT` launches a separate production-wired worker JVM (`KillableWorkerMain`, test sources only), waits until it is blocked mid-copy with a genuine partial copy on disk, terminates it with `Process#destroyForcibly` (SIGKILL / TerminateProcess), launches a fresh worker JVM, and verifies one File with its permanent identity and provenance, authoritative size/SHA-256, protected bytes, the partial copy quarantined and accounted in the ledger, staging cleaned, and no change after further recovery passes. Runs in all four CI jobs. | `WorkerProcessKillIT.gasP2_002_aWorkerProcessKilledMidCopyIsRecoveredByAFreshWorkerProcess` |
| **GAS-P2-010** (MINOR) empty required volumes | Scans check every active required volume plus the ingestion destination, whether or not they hold content. | `gasP2_010_anEmptyRequiredVolumeThatIsNotTheIngestionDestinationIsScanned` |
| **GAS-P2-011** (MINOR) repeated scans | `integrity_finding_observation` (V0014) links each scan to every finding it observed; unresolved findings of missing/damaged content are observed by later scans even when a quick scan cannot re-detect them; the scan summary distinguishes new and still-unresolved problems; the scan page marks findings first recorded earlier. | `gasP2_011_aRepeatedScanReportsDamageThatIsStillUnresolved`; updated `IntegrityRepairIT.iv005_*` |

## Tests replaced because they asserted the audited behaviour

- `RecoveryIT.requirement1_anUploadThatStallsPastTheTimeoutIsAbandonedAndCannotLaterBeArchived` expected
  a live receiver to be abandoned for lack of byte progress, which the audit identified as the GAS-P2-001
  defect. It is replaced by the live-receiver, dead-receiver and live-request-timeout tests above.
- `IntegrityRepairIT.iv005_…`'s final assertion expected a repeated scan to report nothing for problems
  still present (GAS-P2-011). It now expects the same findings to be reported again, without duplicate rows.
- `Phase2SchemaIT` counts V0014 in the Phase 2 upgrade and lists its two new tables.

## Specification reconciliation

- **ADR-0009 receiving timeout:** the ADR described abandonment by byte-progress time. As the audit noted,
  that implementation choice did not satisfy authorization requirement 1, which governs; ADR-0009 now
  describes the receiver lease. No owner requirement changed.
- No other conflict with an approved requirement was found; no owner decision is needed for these
  corrections.

## Remaining limitations (unchanged scope decisions)

Linux separation per unit with one account (B1); residual directory-swap race (P2-E); NAS fallback
documented only (B4); JDK-only thumbnails; no resumable uploads; no scheduled scans; ingestion volume not
yet in the configuration export (Phase 8). Change History redaction covers structured references (File
UUIDs, friendly IDs, content IDs); free-text reasons that describe a restricted File in other words cannot
be recognised and are shown.
