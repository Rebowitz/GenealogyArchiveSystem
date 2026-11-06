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

## Re-audit corrections (audited revision `83031267c8c37394462c4cf8346cc5a1d8108cde`)

The independent re-audit found GAS-P2-001 … GAS-P2-011 resolved and two remaining MAJOR findings. Only
those were corrected; the nine accepted corrections and their tests are unchanged. No schema change (no
new migration).

| Finding | Correction | Regression tests |
|---|---|---|
| **GAS-P2-R01** (MAJOR) destruction left an uncommitted repair copy | `DestructionProcessor` first locks every unfinished operation on the content and every owner of an unsettled quarantine move of it (busy → `CLEANUP_INCOMPLETE`, "waits for operation …", retried). It then stops each unfinished repair (`STAGED`, `PROMOTING`, `PROMOTED`, `WAITING_FOR_VOLUME`, `BLOCKED` → `CANCELLED`, reason recorded on both operations, staging cleaned) so none can write, commit or move bytes later. Known locations now include each copy an operation on the content created itself (`target_created`) and did not since move through the quarantine ledger; an object it did not create is never included. Stopping is committed before removal; a new fault point `DURING_PHYSICAL_DELETE` (after each removal) exercises interruption mid-cleanup. `BYTES_REMOVED` still only after every known location is re-checked. | `Phase2AuditCorrectionsIT.gasP2_R01_aRepairCopyVerifiedButNotCommittedIsRemovedByDestructionAndRecovery` (crash after verify, before commit; destruction via recovery; repeated recovery, destruction and repair passes change nothing); `…CleanupInterruptedBeforeAndDuringRemovalIsFinishedByRepeatedRecovery` (crash after the repair is stopped and before removal; crash after the first of two copies is removed — never `COMPLETED`/`BYTES_REMOVED` while a copy remains; repeated recovery finishes); `…RemovalWaitsWhileTheRepairIsBusyElsewhere`; `…AnObjectTheRepairNeverCreatedIsLeftUntouched` |
| **GAS-P2-R02** (MAJOR) history authorization missed accepted File spellings | New `FileIdentifiers` is the single definition of File identifier spellings: the friendly-ID lookup (`FileService.getByFriendlyId`) now uses it, and `RestrictedFileHistory` uses the same rules to find references in history values and reasons — friendly IDs in any case and with any leading zeros (`F00001`, `f00001`, `F1`, `f1`, `F01`), UUIDs in every spelling `UUID.fromString` (the web layer's conversion) accepts (upper case, abbreviated groups), alone or embedded in text. The previous separate patterns (`F\d{5,}`, canonical lower-case UUID only) are removed. | `FileIdentifiersTest` (accepted and rejected spellings agree with the lookup; embedded references; ordinary text such as `F-1990`, `Fairfax` is not a reference); `Phase2AuditCorrectionsIT.gasP2_R02_historyOfOtherObjectsDoesNotLeakARestrictedFileByAnyResolvableSpelling` (each spelling is first shown to resolve, then withheld from per-object and global history for Viewer and Editor after restriction, still shown to Administrators; ordinary text stays visible) |

Known consequence (by design, R02): because `F1` is a valid File identifier, a reason containing the
standalone token `F1` (or `f1`) is withheld from non-Administrators when File F00001 is restricted, even if
the writer meant something else. Only tokens that resolve to a currently restricted File are withheld.

## Final verification corrections (audited revision `c58e6cd92602ba0a3a5f76657dc5230d8522a6a6`)

Codex reported two MAJOR findings (FV01, FV02) and one MINOR (FV03). The thirteen earlier corrections and
their tests are kept; no schema change (no new migration).

| Finding | Correction | Regression tests |
|---|---|---|
| **GAS-P2-FV01** (MAJOR) a recreated copy at a vacated target was ignored by destruction | Replaced R01's "moved away" special case with one accounting rule: a copy is accounted by a generation, the quarantine ledger, or its owning operation (`target_created`), and ownership ends only in the transaction that records where the copy went. `QuarantineMoves.settle` confirms a move and releases the mover's ownership of the vacated location together (`FileOperationRepository.releaseTarget`); the worker re-reads the operation after settling, so an object appearing at a vacated location is a `COLLISION`, never adopted; destruction counts every owned target and releases ownership in its completion transaction; a repair never writes a copy of content in `DESTROY_PENDING`/`DESTROYED`. | `Phase2AuditCorrectionsIT.gasP2_FV01_aCopyRecreatedWhereARecordedMoveVacatedIsRemovedByDestruction` (the audit's sequence), `…AfterAnUnconfirmedMoveWasSettled…`, `…AnUnconfirmedMoveSettledByDestructionLeavesNothingAndNoRepairWritesAgain`, `…AnUnknownObjectAtAVacatedTargetIsNeitherAdoptedNorRemoved`, `…CleanupInterruptedAfterEachRemovalNeverReportsCompletionEarly` (stop after the 1st, 2nd and 3rd removal). Each step checks the invariants independently: every object under `originals/`/`quarantine/` is accounted for, and `BYTES_REMOVED` never coexists with a remaining copy. |
| **GAS-P2-FV02** (MAJOR) redaction revealed whether a guessed identifier was a restricted File | `RestrictedFileHistory` now decides by what the reader may see, not by what is hidden: a value or reason is withheld when an identifier written in it as a whole (friendly ID, delimited UUID) names no unrestricted File, content of only unrestricted Files, or other object with history - so a restricted and a nonexistent identifier give identical results - and also when any embedded spelling is a restricted File's (or its content's) UUID. | `gasP2_FV02_aGuessedIdentifierLooksTheSameWhetherItsFileIsRestrictedOrDoesNotExist` (restricted vs nonexistent vs unrestricted; canonical, short, embedded friendly IDs; canonical, upper-case, Arabic-Indic UUIDs; several identifiers in one text; a user-chosen filename; per-object and global; Viewer and Editor; Administrator sees all), `…TextWithoutIdentifiersAndReferencesToVisibleObjectsAreNotSuppressed` |
| **GAS-P2-FV03** (MINOR) Unicode-digit UUIDs bypassed reference detection | Compatibility assessed: the JDK (and so Spring's conversion) has always resolved UUIDs written with any Unicode decimal digit or full-width letter, so URLs using them keep working. `FileIdentifiers.uuid` is now the web layer's String-to-UUID conversion (`IdentifierConversion`, same results as Spring's default) and the parsed-header path, and reference detection finds every substring that conversion resolves (any script, glued or over-long groups). Friendly IDs stay ASCII, as the lookup always required. | `FileIdentifiersTest.gasP2_FV03_*` (Arabic-Indic, Extended Arabic-Indic, Devanagari, Malayalam, full-width digits and letters, mixed scripts, Cyrillic/Greek look-alikes, Unicode hyphen, over-long groups, and a seeded generator checking every resolvable substring of 400 mixed texts); `IdentifierConversionTest` (agrees with Spring's default conversion); `FileWebIT.gasP2_FV03_aUnicodeDigitSpellingInTheUrlResolvesAsBeforeAndStillHidesRestrictedFiles`; `Phase2AuditCorrectionsIT.gasP2_FV03_unicodeDigitSpellingsOfARestrictedFileAreWithheld` |

Consequences, by design: (FV02) text naming a File identifier that resolves to no visible File - including a
nonexistent one such as a filename `F1.jpg` before any F00001 exists, or an unrelated UUID typed into a
reason - is withheld from non-Administrators. (FV02) The embedded-spelling rule can only be triggered by
someone who already knows a restricted File's UUID (UUIDs cannot be guessed); such a person already sees
"not found" on that File's page, so history reveals nothing more. Content shared by a restricted and an
unrestricted File stays withheld (GAS-P2-003 behaviour, unchanged).

## Remaining limitations (unchanged scope decisions)

Linux separation per unit with one account (B1); residual directory-swap race (P2-E); NAS fallback
documented only (B4); JDK-only thumbnails; no resumable uploads; no scheduled scans; ingestion volume not
yet in the configuration export (Phase 8). Change History redaction covers structured references (File
UUIDs, friendly IDs, content IDs); free-text reasons that describe a restricted File in other words cannot
be recognised and are shown.
