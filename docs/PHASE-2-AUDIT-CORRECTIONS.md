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

## Corrections IV01 – IV03 (audited revision `d2a2349920ed352a258920d5aca46b36f83cf92e`)

Two MAJOR findings (IV01, IV02) and one MINOR (IV03). Earlier corrections are kept; no schema change.

| Finding | Correction | Regression tests |
|---|---|---|
| **GAS-P2-IV01** (MAJOR) destruction deleted a foreign object at a historical path | Destruction's inventory is now only locations a record says hold bytes now: `CURRENT`/`QUARANTINE_PENDING` generation storage paths, `QUARANTINED` generation and ledger quarantine paths, and owned targets. Storage paths of `QUARANTINED`/`MISSING`/`DESTROYED` generations, ledger sources, `REMOVED` quarantine paths and unowned targets are historical: never deleted; an object found there is reported as left untouched. `QuarantineMoves.settle` retires the source in the confirming transaction for every record (all operations' ownership via `releaseTargetsAt`; the superseded generation via `retireGenerationAt`), closing the window between the ledger confirmation and the separate generation update. Generation/ledger/content retirement moved into the completion transaction. | `Phase2AuditCorrectionsIT.gasP2_IV01_aForeignObjectAtAQuarantinedGenerationsVacatedPathSurvivesDestruction` (the audit's case), `…DestructionBeforeTheDamagedGenerationWasMovedRemovesItWhereItIs` (before the move), `…AMoveInterruptedBeforeItWasRecordedFailsVisiblyIfSomethingAppearedAtItsSource` (after the move, before its record: `CLEANUP_INCOMPLETE`, nothing touched, completes once resolved), `…AMoveSettledAfterAnInterruptionRetiresTheSourceSoAForeignObjectThereSurvives` (after the record), `…CleanupInterruptedAfterEachRemovalNeverTouchesHistoricalLocations` |
| **GAS-P2-IV02** (MAJOR) embedded-UUID deny-list was existence-dependent | The deny-list is removed. `FileIdentifiers.referencesIn` reads references from the text alone (UUID windows read as the JDK reads them; canonical-shaped windows take precedence over windows overlapping them) and `RestrictedFileHistory` shows a value only if every reference is visible. Whole and embedded identifiers follow the same policy; restricted and nonexistent are never distinguished. | `gasP2_IV02_restrictedAndNonexistentUuidsLookTheSameInEveryContext` (canonical, upper-case, Arabic-Indic, full-width × 8 contexts incl. glued and hyphen-joined; Viewer, Editor, Administrator; per-object and global), `…FilenamesAndIndirectReferencesFollowTheSamePolicy` (filenames, content IDs); `FileIdentifiersTest.gasP2_IV02_*` (overlap rule, text-only reading, seeded generator: any text containing a resolvable spelling is governed by the allow-list, delimited spellings read exactly) |
| **GAS-P2-IV03** (MINOR) repair could write after destruction intent | `FileService.destroy` takes the locks of the content's unfinished repairs before its transaction (bounded 2 s wait, then a visible refusal), re-checks under the content row lock that no unheld repair has started, and cancels unfinished repairs in the intent transaction. `OperationProcessor.createOwned` creates the copy and records ownership in one transaction holding the content row `FOR SHARE` after re-reading its state. | `gasP2_IV03_destructionIntentCannotCommitWhileARepairThatPassedItsCheckIsWriting` (the reported interleaving, before creation and during copying), `…ARepairCreatesNothingOnceDestructionIntentHasCommitted`, `…AConcurrentDestructionRequestIsRefusedNotBlockedWhileARepairCopies` (two threads), `…DestructionIntentStopsARepairThatIsNotRunningSoItNeverWrites` |

Tests changed because they asserted superseded behaviour (none weakened): `gasP2_R01_removalWaitsWhileTheRepairIsBusyElsewhere`
now uses a committed repair, since unfinished repairs are stopped by the intent (IV03); `FileIdentifiersTest`'s
FV03 completeness test (every resolvable substring is a reference, for the removed deny-list) is replaced by
the IV02 allow-list property, and the glued-hex example now expects the UUID the JDK reads.

Consequences, by design: hyphen-joined text whose groups form an extra UUID window with no canonical
window to anchor it (rare) is withheld from non-Administrators unless that UUID is visible. A destruction
request made while a repair of the same content is writing is refused with a message to retry.

## Final outstanding corrections (audited revision `9b27418d6b744016a65a332d86d33f53c0083c3e`)

| Finding | Correction | Regression tests |
|---|---|---|
| **IV01** (MAJOR) stale ownership after a missing-content repair | Repair of MISSING content retired the old generation (`CURRENT` → `MISSING`) outside the quarantine ledger, so the ingest operation still "owned" the old path and destruction deleted whatever was there. **V0015** makes the invariant a database rule for every retirement path: retiring a generation (QUARANTINED, MISSING, DESTROYED) or recording content as MISSING ends all operations' ownership of that location in the same transaction; an operation can never take ownership of a retired generation's location; existing contradictory rows are corrected. Destruction removes a current location recorded missing (open MISSING finding) only if the object there is verifiably the content (authoritative size and SHA-256). | `Phase2AuditCorrectionsIT.gasP2_IV01_anUnrelatedObjectAtAMissingGenerationsOldPathSurvivesDestruction` (the exact reproduction), `…AtTheLocationOfContentRecordedMissingIsNotRemoved` (repair stopped before commit), `…TheVerifiedOriginalBackAtAMissingLocationIsRemoved`, `…ARepairStoppedAfterItsCommitLeavesAnUnrelatedObjectAtTheOldPathUntouched`, `…TheDatabaseRefusesOwnershipOfARetiredLocation`; every step checks the ownership invariant independently and that `BYTES_REMOVED` never coexists with a known copy; repeated recovery |
| **Pool exhaustion** (MINOR) one pooled connection per repair lock | Destruction intent takes repair locks as transaction-scoped advisory locks on its own transaction connection (same key as the workers' session locks; bounded 2 s wait, then a visible refusal; lock order operation-then-rows unchanged). Removal holds all other writers' locks together on one connection (`OperationLocks.tryLockAll`). The IV03 guarantees are unchanged: a repair still creates and owns its copy in one transaction holding the content row `FOR SHARE`. | `RepairLockPoolIT` (pool of 3 connections, 6 idle repairs): destruction proceeds without waiting for a connection and leaves none in use; no stopped repair writes; with one repair writing among the idle ones, destruction is refused in bounded time while other work still gets connections, and succeeds once the repair finishes; any number of locks held on one connection. Existing IV03 tests unchanged. |

Schema: new **V0015** (triggers and a one-time consistency update). V0001–V0014 unchanged.

## Ownership consistency corrections (audited revision `0a563d4875cc2a03eafc71415b655792fd5959d9`)

**Structural correction (explained before implementation).** The ownership representation was not
sufficient: a location could be claimed in three places that drifted apart (an operation's
`target_created`, a generation row, and content state MISSING). V0016 gives each location exactly one owner
and puts the physical evidence on that owner:

1. *One owner at a time.* An operation owns only an uncommitted copy; ownership passes to the generation in the
   commit transaction (`commitNewContent`, `commitRepair`). A deferred constraint trigger on both tables
   rejects, at commit, any state in which an operation claims a location a generation records - for every
   INSERT/UPDATE, whatever code path or SQL issues it. Existing rows are corrected by the migration.
2. *Generations carry missing evidence.* `file_content_generation.vacated` is set by trigger in the same
   statement that records content MISSING (and the scan/download writes the finding in the same transaction),
   cleared only on MISSING -> ACTIVE (after verification). MISSING -> DAMAGED is rejected by the database;
   a vacated generation cannot be QUARANTINE_PENDING (check constraint).
3. *Evidence for moves.* Quarantine intents record the source object's size and last-modified time; settling
   moves or confirms only that object, otherwise raises `UnexplainedObjectException` (nothing moved, operation
   reports it).

| Finding | Root cause traced | Correction | Regression tests |
|---|---|---|---|
| **Repair adopted a foreign object** (MAJOR) | content MISSING -> object appears -> scan marks `MISSING -> DAMAGED` -> repair sets the old generation `QUARANTINE_PENDING` -> the foreign object is moved to quarantine and later destroyed | scans keep MISSING content MISSING and report the object as `UNEXPECTED_OBJECT` unless it verifies (then ACTIVE); repair retires a vacated generation as MISSING; moves require the recorded object | `v0016_aForeignObjectAppearingAtAMissingLocationBeforeRepairIsNeverAdopted` (QUICK and FULL scans, repair, destruction), `v0016_anObjectSwappedInDuringAnInterruptedQuarantineMoveIsNotMovedOrAdopted` (crash after the intent; recovery, worker and destruction refuse; finishes once removed), `v0016_anInterruptedQuarantineMoveOfTheRecordedObjectIsStillFinished` |
| **Ownership retirement not atomic** (MAJOR) | the MISSING finding and the content state were separate transactions; a stale ingest claim then overrode destruction's verification | missing recording is one transaction (scan and download); vacated set by the same statement; ownership ends at commit; recovery and destruction reconcile an open MISSING finding on ACTIVE content before anything else | `v0016_recordingMissingContentIsAtomic` (crash between finding and state), `v0016_recoveryReconcilesAnInterruptedMissingRecordingBeforeAnythingIsDeleted`, `v0016_destructionReconcilesAnInterruptedMissingRecordingItself`, `v0016_ownershipPassesToTheGenerationWhenTheCopyIsCommitted` |
| **V0015 enforcement incomplete** (MINOR) | V0015 checked only `UPDATE OF target_created` against retired generations | V0016 replaces it with the single-owner constraint on INSERT and UPDATE (of `target_created`, `target_path`, `volume_id`; generation `storage_path`, `volume_id`) checked at commit | `v0016_theDatabaseRefusesEveryStatementThatWouldOwnARecordedLocation` (direct SQL: UPDATE claim, UPDATE path onto a generation, INSERT claiming operation, INSERT generation at a claimed path, claim released before commit allowed, MISSING -> DAMAGED refused, vacated QUARANTINE_PENDING refused, vacated set/cleared by content state) |

Every new test also checks the ownership invariants against the whole database (`assertOwnershipInvariants`).
Test changed (stronger rule, not weakened): `gasP2_IV01_theDatabaseRefusesOwnershipOfARetiredLocation` now
expects the single-owner refusal. Schema: new **V0016**; V0001–V0015 unchanged; `Phase2SchemaIT` expects 8
Phase 2 migrations from the Phase 1 baseline.

## Structural audit corrections (audited revision `c422e8cb16486f3d12f98b147437873d9afec2d0`)

Three MAJOR and one MINOR finding; the V0016 redesign is kept and extended in **V0017**.

| Finding | Correction | Regression tests |
|---|---|---|
| **1. Missing detection for DAMAGED content** (MAJOR) | Scans record MISSING from ACTIVE *or* DAMAGED (one transaction with the finding; vacates the location). Recovery reconciles ACTIVE or DAMAGED content with an open MISSING finding. A repair quarantines a damaged copy only if it still matches the damage evidence (size and SHA-256 recorded by the scan; size-mismatch findings now record the hash too); otherwise the generation is retired and the object reported (`UNEXPECTED_OBJECT`). | `v0017_damagedContentThatGoesMissingIsVacatedAndAForeignObjectThereIsNeverAdopted`, `…ACrashWhileRecordingDamagedContentAsMissing…`, `…RecoveryReconcilesDamagedContentRecordedMissing…`, `…ADamagedCopyReplacedWithoutAScanIsNotQuarantinedByTheRepair`; ACTIVE -> MISSING tests from V0016 kept |
| **2. Quarantine recovery identity** (MAJOR) | Intents record SHA-256 (V0017 column); settle moves/confirms only matching bytes, verifies the destination after the rename and moves a mismatch back; intents without hash evidence are unverified and block with a visible note. | `v0017_anObjectWithTheSameSizeAndTimestampButDifferentBytesIsNotMoved`, `…ALegacyIntentWithoutContentEvidenceIsNeverFinishedSilently`, `…AnObjectSwappedInBetweenTheCheckAndTheRenameIsPutBackUntouched`; earlier valid-recovery tests kept |
| **3. Full-scan restoration and protection** (MAJOR) | MISSING -> ACTIVE only when the bytes verify *and* the object is already read-only; verified-but-writable stays MISSING with a `NOT_PROTECTED` finding (downloads, sharing and repair treat it as missing; destruction removes it only because its hash is the authoritative one). Worker re-verification hashes before protecting and confirms protection took effect. | `v0017_restoredBytesThatAreWritableAreNotRestoredUntilProtected`, `…ReverificationProtectsOnlyVerifiedBytesAndNeverTouchesAnotherObject` (both use `SafeFiles` platform checks, run on Windows and Linux) |
| **4. Concurrent ownership enforcement** (MINOR) | `physical_location_owner` with primary key (volume, path), filled by triggers on INSERT/UPDATE of operations and INSERT of generations; `INSERT … ON CONFLICT DO NOTHING` waits for a concurrent claimant and then refuses. | `v0017_concurrentConflictingClaimsCannotBothCommit` (two connections, both orders, READ COMMITTED and REPEATABLE READ, rollback lets the second succeed, bounded by `statement_timeout`), `…OwnershipTransfersAtomicallyFromOperationToGeneration` |

Broader change: destruction now requires a vouched-for SHA-256 for every generation or quarantine object it
deletes (authoritative, recorded damage, or recorded at quarantine); unexplained objects are left and reported.

Uncertainties resolved by choice (reported for owner review): verified-but-writable originals are kept
MISSING with a finding rather than given a new content state; quarantined objects moved before V0017 are
vouched for by their settled ledger record (no hash was recorded); destruction leaves an undetected damaged
copy of ACTIVE content (hash neither authoritative nor recorded) in place and reports it.

Residual time-of-check/time-of-use window: there is no portable way to rename "the file just hashed". A
replacement between the hash and the rename is detected by re-hashing the destination and moved back; a
replacement during that re-hash/move-back window, or by a local writer racing directory components, remains
the accepted P2-E residual risk (native `openat`-style code would be needed).

## V0017 audit corrections (audited revision `006081c0eb4630de1a17443f1f7e9c53f65d1d3e`)

Three MAJOR, one MINOR; V0016/V0017 kept; new **V0018**.

| Finding | Reproduced cause | Correction | Regression tests |
|---|---|---|---|
| **1. Legacy settled quarantine records** (MAJOR) | destruction treated a QUARANTINED ledger record without a hash as sufficient evidence | quarantined objects are deleted only if they match a hash recorded before now (move hash, or the damage evidence for a damaged generation); otherwise the cleanup stays `CLEANUP_INCOMPLETE` naming the path and reason | `v0018_aLegacySettledQuarantineRecordNeverAuthorisesDeletingAReplacement`, `v0018_aQuarantinedCopyReplacedAfterItsMoveIsNotDeleted` |
| **2. False BYTES_REMOVED** (MAJOR) | a claimed copy that failed verification was only "reported" and destruction completed | a claimed copy that cannot be shown to be the content's is *unresolved*: left untouched, destruction stays `CLEANUP_INCOMPLETE` (content `DESTROY_PENDING`, generation unchanged) on every retry until an Administrator resolves it; vacated locations remain merely reported | `v0018_aCorruptedCurrentCopyKeepsDestructionIncompleteUntilResolved`, `v0018_anInterruptedCleanupWithAnUnresolvedCopyNeverBecomesBytesRemoved` |
| **3. Trusted damage-hash evidence** (MAJOR) | repairs/destruction used the latest damage finding, and quarantine intents a fresh hash of whatever was at the path | the generation stores the size/SHA-256 seen when the damage was first detected (`damaged_size`/`damaged_sha256`, set in the transaction ACTIVE -> DAMAGED, never replaced - database trigger); later scans report a changed object instead; repair planning, quarantine intents (trusted evidence recorded, mismatch blocks before anything is recorded), recovery and destruction verify against it | `v0018_damageEvidenceIsKeptWhenAReplacementAppearsAndALaterScanRunsAndTheRepairDoesNotAdoptIt`, `v0018_quarantineIntentsCarryTheTrustedDamageEvidenceNotAFreshHash`; earlier v0017 replacement/swap tests kept |
| **4. Legacy migration conflicts** (MINOR) | V0017's backfill used `ON CONFLICT DO NOTHING` | V0018 audits every operation and generation claim; any claim without its owner row stops the upgrade with a diagnostic (each operation/generation, location and current owner, plus the remedy); nothing is changed or chosen; re-running after resolution succeeds; V0018 also backfills damage evidence from the earliest damage finding | `Phase2SchemaIT.conflictingLegacyClaimsStopTheUpgradeWithADiagnosticAndNothingIsChosen` (repeated), `…anExistingPhase2InstallationUpgradesAndItsClaimsAreRecorded`, `…acceptedPhase1DatabaseUpgradesCleanlyToPhase2` (V0008 -> V0018) |

Cross-cutting: ADR-0009 now has the deletion/quarantine authorization table (claiming record, evidence, when
recorded, what is allowed). No path, current-object hash or historical ledger status is accepted as evidence.

Remaining evidence limitations: the first damage observation is trusted as "the damaged copy" (an object
replaced before any scan saw the damage cannot be told apart from damage); an operation's uncommitted copy
is identified by its exclusive-create claim, not by a hash; pre-V0017 quarantined copies without a hash
need Administrator resolution before their content can be fully destroyed.

## V0018 audit corrections (audited revision `70efeef37bff567fc76bbb6813b704750d2c8fa7`)

Three MAJOR findings. The three causes were reproduced from the source before any change:

1. **Stale operation claims.** `DestructionProcessor.ownedTargets` deleted whatever was at a claimed target
   (`target_created`) with no check of the bytes. In `promote`, the "partial copy" branch and the
   unverified-copy branch, and in cancellation, the object at the claimed target was hashed *at move time*
   and that fresh hash became the ledger evidence that destruction later deleted against. A replacement at
   a claimed location was therefore moved and later deleted.
2. **First damage observation.** `IntegrityService.damaged` copied the hash observed by the first scan that
   saw a mismatch into `damaged_sha256`, and repair (`notTheRecordedDamagedCopy`, the quarantine intent),
   recovery (settle against that intent) and destruction (`vouched`, `classifyQuarantined`) treated it as
   proof. An object swapped in before that scan became "the damaged copy".
3. **Retirement of occupied locations.** `commitRepair` retired a vacated generation as `MISSING` without
   looking, and `retireUnexplained` set an occupied, unverifiable location to `MISSING`. Both locations
   became historical, so destruction recorded `BYTES_REMOVED` while the object was still there.

### Correction design (documented before coding)

One rule replaces the three special cases. **Bytes are moved or deleted only on evidence the archive
established itself before the object could have been replaced. An object that cannot be shown that way is
left exactly where it is, and its presence is recorded durably as unresolved.** The evidence is:

| Object | Ownership record | Trusted identity evidence | Physical disposition |
|---|---|---|---|
| Current generation copy | generation (CURRENT) | authoritative size + SHA-256 | kept; destroyed only when it matches |
| Operation's uncommitted copy | operation claim (`target_created`) | equals the received bytes (receiver's SHA-256), or is a byte prefix of the staged upload after the staged upload re-verifies against that hash; or the size + hash measured by the worker while it wrote it | verified: moved to quarantine / deleted. Otherwise: unresolved, untouched |
| Damaged copy of a superseded generation | generation | **none** - an observation of a mismatch identifies nothing. Only an Administrator's attestation of the recorded observation (or verified absence) resolves it | unresolved, untouched, unless attested (then quarantined against that hash) |
| Object at a vacated location | none (bytes left) | authoritative hash only | verified: destroyed with the content. Otherwise unresolved, untouched |
| Quarantined object | ledger entry (QUARANTINED) | the move's hash, *only* if the entry records that it was trusted when recorded (`evidence_basis`) | trusted and matching: destroyed. Otherwise unresolved, untouched |

What this changes:

* **Operation copies** (finding 1): one check, used by promotion, cancellation, recovery (which drives the
  same code) and destruction. Staged uploads of operations that still own a copy are kept until the copy is
  resolved, because they are the evidence. A replacement is never moved, adopted or deleted: the claim
  becomes an unresolved record and the operation blocks (promotion) or completes its cancellation with the
  object left in place.
* **Damage observations** (finding 2): `damaged_size`/`damaged_sha256` stay, but only as a diagnostic
  observation (the database comment says so; the column cannot be rewritten in a historical migration). No
  code path uses them, or any other observed hash, to authorize a move or deletion. A repair no longer
  quarantines the superseded damaged copy automatically: if nothing is at the old location the generation
  is `MISSING`; if anything is there it is `UNRESOLVED`, untouched, until an Administrator attests that the
  recorded observation is the archive's damaged copy (it is then quarantined against that hash, which must
  still match) or that it is not (it is released and left in place), or it is verifiably gone.
* **Unresolved presence** (finding 3): a durable `unresolved_object` record per occupied location. Repair
  retirement, cancellation, promotion and destruction create it; destruction never completes while one is
  open for the content; scans, recovery and destruction re-check it and keep it open while anything is there;
  it closes only on verified absence or an Administrator's resolution (who, when, why).
* **Specification note.** ADR-0009 P2-I said repair "keeps the damaged copy in quarantine", and Phase 2
  decision 4 that repair "retains accountable references to damaged generations until quarantine is
  confirmed". With an untrusted observation the archive cannot confirm which object is the damaged copy;
  the audit itself allows "remain recoverable or explicitly unresolved". The damaged copy is therefore kept
  accountable as `UNRESOLVED` in place (still recoverable), and quarantine happens on an Administrator's
  attestation. This is a narrowing of P2-I recorded here for owner review; the governing documents are not
  rewritten.

### Why a schema migration (V0019) is necessary

* Unresolved presence must survive restarts and repeated recovery, and must be visible to destruction and
  scans independent of any operation; no existing table can hold it without overloading `MISSING` (which
  means "bytes absent") or a finding (which is not tied to the disposition of a location). New table
  `unresolved_object`, at most one open record per location.
* A superseded generation whose location is occupied but unverifiable needs a state that is neither
  `MISSING` (absent) nor `QUARANTINE_PENDING` (which a vacated generation may not be, V0016): new generation
  state `UNRESOLVED`, which the database allows only while an open record exists.
* Ledger hashes recorded before this correction were fresh hashes of whatever was at a claimed path, so the
  ledger must say whether its hash was trusted when recorded: `quarantine_object.evidence_basis`
  (`OPERATION_COPY_VERIFIED`, `ADMINISTRATOR_ATTESTED`, or `UNTRUSTED`; existing rows are `UNTRUSTED`),
  immutable like the other evidence columns.

V0001–V0018 are unchanged; the V0016 single-owner and vacated rules, the V0017 claims table and move
evidence, and the V0018 evidence-once trigger and claims audit all remain in force.

### Implemented

| Finding | Correction | Regression tests (`Phase2AuditCorrectionsIT` unless named) |
|---|---|---|
| **1. Stale operation claims** (MAJOR) | `OperationCopies` identifies an operation's copy (complete: receiver's size + SHA-256; partial: byte prefix of the staged upload after it re-verifies). Promotion, cancellation, recovery (which drives both) and destruction all use it; quarantine intents carry the verified measurement with basis `OPERATION_COPY_VERIFIED`. An unverified object: claim released, `unresolved_object` (OPERATION_COPY) recorded, operation BLOCKED (promotion) or CANCELLED with the object left (cancellation), destruction `CLEANUP_INCOMPLETE`. Staged uploads of operations that still own a copy are kept (destruction intent, stopped repairs, recovery, final clean-up) and removed once the copy is. | `v0019_aReplacementAtAnOperationsClaimedTargetIsNeitherMovedNorAdoptedAndStaysUnresolved` (interrupted creation, restarts, scan, retry), `v0019_withoutItsStagedUploadAClaimedPartialCopyCannotBeIdentifiedAndIsLeftUntouched`, `v0019_aGenuinePartialCopyIsIdentifiedByItsStagedUploadAndMovedToQuarantine`, `v0019_aCancellationInterruptedAfterItsIntentNeverMovesAReplacement` (crash after the intent; job retry and recovery refuse), `v0019_destructionNeverDeletesAReplacementOfAStoppedRepairsCopy` (interrupted cleanup), `RecoveryIT.cancellingNeverMovesAReplacementOfItsUncommittedCopy`, `RecoveryIT.cancellingAWaitingOperationQuarantinesItsVerifiedUncommittedCopyInTheWorker` |
| **2. First damage observation** (MAJOR) | `damaged_size`/`damaged_sha256` are an observation (column comments, javadoc); no path authorises a move or deletion from them, nor from any fresh hash. Repair inspects the superseded location before its commit: empty -> MISSING; occupied -> UNRESOLVED + record (DAMAGED_COPY or OBJECT_AT_VACATED_LOCATION), untouched. Legacy QUARANTINE_PENDING generations are retired the same way (never moved). Quarantine of a damaged copy happens only after an Administrator attests the recorded object (`UnresolvedObjects.attest`, worker job `file.quarantine-attested`, intent basis `ADMINISTRATOR_ATTESTED`, verified before and after the rename). Destruction trusts only the authoritative hash, trusted ledger hashes and attestations. | `v0019_anObjectReplacedBeforeTheFirstDamageScanIsNeverMovedOrDeleted` (repair, restarts, scans, destruction, release), `v0019_anAttestedObjectThatChangedAfterwardsIsNeitherMovedNorDeleted`, `v0019_everyMoveRecordsTheBasisOfItsEvidence`, `v0018_damageEvidenceIsKept…` (now: UNRESOLVED, observation not adopted), `v0018_quarantineIntentsCarryTheAttestedEvidenceNotAFreshHash`, `IntegrityRepairIT.iv003…` (damaged copy kept untouched, then quarantined on attestation) |
| **3. Retirement of occupied locations** (MAJOR) | No occupied location is retired as MISSING: UNRESOLVED generation + durable record (database: UNRESOLVED requires an open record; records never deleted; resolutions final). Destruction never records BYTES_REMOVED while any open record of the content or its operations remains, and records new ones for unidentified objects at current locations (current generation, vacated current location, quarantine path). Scans count open records as unresolved (never "No problems found"); a record closes only on verified absence (a scan or destruction finds nothing there) or an Administrator's resolution - restarting or repeating recovery never closes one. An Administrator can release (left in place) or attest. | `v0019_anUnresolvedObjectAtAFormerGenerationLocationSurvivesRepeatedRecoveryUntilItIsGone`, `v0019_onlyANamedAdministratorResolvesAnUnresolvedObjectAndAResolutionIsFinal`, `v0016_aForeignObjectAppearingAtAMissingLocationBeforeRepairIsNeverAdopted`, `v0017_damagedContentThatGoesMissing…`, `gasP2_IV01_anUnrelatedObjectAtTheLocationOfContentRecordedMissingIsNotRemoved`, `Phase2SchemaIT.anExistingV0018InstallationUpgradesAndEarlierMoveEvidenceIsUntrusted` |

**Unified audit (finding 4).** Every path that moves, quarantines or deletes bytes, with its ownership record,
identity evidence and disposition:

| Path | Ownership record | Trusted identity evidence | Disposition when the evidence does not hold |
|---|---|---|---|
| Ingest/repair promotion: copy found at own target | operation claim | receiver's hash (complete) or prefix of re-verified staging | unresolved, operation BLOCKED |
| Promotion: copy just written did not verify | operation claim | measurement taken while writing | (only this object is moved) |
| Partial-copy quarantine and its recovery (`settle`) | ledger intent | intent hash with basis `OPERATION_COPY_VERIFIED` | not moved; fails visibly |
| Commit (ownership transfer) | operation -> generation, one transaction | full re-hash equals the authoritative hash | not committed (BLOCKED) |
| Integrity scans (missing, damaged, reappeared) | generation | authoritative hash only (restores ACTIVE); observations are diagnostic | findings only; nothing moved |
| Repair supersession | generation | none needed: nothing is moved | MISSING if empty, UNRESOLVED if occupied |
| Attested quarantine and its recovery | `unresolved_object` + ledger intent | Administrator's attestation of the observed hash (`ADMINISTRATOR_ATTESTED`) | not moved; record stays open |
| Cancellation | operation claim | as promotion | object left, unresolved, operation CANCELLED |
| Destruction | generation / ledger / claim / record | authoritative hash; trusted ledger hash; `OperationCopies`; attestation | left, unresolved, `CLEANUP_INCOMPLETE` |
| Re-verification | generation | authoritative hash before protecting | nothing changed |

Path occupancy, a claim, a ledger status, a hash observed by a scan and a hash taken at move time are never
accepted as evidence; the V0016 single-owner/vacated rules, the V0017 claims table and move verification and
the V0018 evidence-once trigger and claims audit are unchanged.

**Tests changed because they asserted the audited behaviour (not weakened):**
* Repairs of damaged content no longer quarantine the damaged copy by themselves. Tests that exercised that
  move (`gasP2_006_*`, `gasP2_IV01_*` with a damaged generation, `v0016_anObjectSwapped…`,
  `v0016_anInterruptedQuarantineMove…`, `v0017_anObjectWithTheSameSize…`, `v0017_anObjectSwappedInBetween…`,
  `IntegrityRepairIT.iv003…`/`requirement4…`) now run the same crash points through the attested move, which
  uses the same intent/settle machinery; their assertions on what is moved, refused and accounted are kept.
* Tests whose subject was not the damaged copy (`gasP2_R01_*`, `gasP2_FV01_*`, three `gasP2_IV03_*`,
  `RepairLockPoolIT`) use content whose original was lost instead of damaged, because a damaged copy now
  (correctly) keeps destruction incomplete until resolved.
* Tests that expected `BYTES_REMOVED` with an unidentified object at a *current* location (vacated or not) or
  a superseded generation retired as MISSING while occupied now expect `CLEANUP_INCOMPLETE`/`UNRESOLVED`
  until an Administrator releases the object (then completion, the object untouched).
* `RecoveryIT.cancellingABlockedOperationQuarantinesItsUncommittedCopyInTheWorker` overwrote the copy and
  expected it to be moved - finding 1 itself. It is split into a genuine-copy test (moved) and a
  replaced-copy test (left, unresolved).
* `v0017_aLegacyIntentWithoutContentEvidence…` builds its legacy state (QUARANTINE_PENDING generation) from
  a V0019 repair, because current code never creates that state.

**Remaining evidence and filesystem limitations.**
* A replacement that is byte-identical to the archive's bytes (complete copy) or a true prefix of the staged
  upload is indistinguishable from the operation's own copy; moving it loses nothing.
* The P2-E residual race is unchanged: an object swapped in during the re-hash/move-back window after a rename,
  or a directory-component swap by a local writer, needs native `openat`-style code.
* The repair inspects the superseded location just before its commit; an object that appears between that
  look and the commit is not recorded as unresolved but is reported by scans as an unexpected object at a
  historical location (never touched).
* Legacy (pre-V0019) quarantine moves are `UNTRUSTED`: their quarantined objects keep destruction incomplete
  until an Administrator attests, releases or removes them; a legacy in-flight intent with its object still at
  the source fails visibly until the object is resolved.
* Verified absence closes a record automatically (a scan or destruction finds nothing there); an object
  removed outside the application therefore needs no further action.
* Damaged content can no longer be destroyed, nor its damaged copy quarantined, without an Administrator's
  decision about the damaged object - by design, but it is new operational work.

## V0019 audit corrections (audited revision `a6b45252777cb8d3384e98c5cf486629ac10749c`)

Three MAJOR findings and one MINOR. Reproduced from the source before any change:

1. **Attestation executed after release.** `release` changed only the record (no lock, no check of a started
   move). `quarantineAttested` re-read the record only once it held the operation lock, but an intent recorded
   before a crash was finished by `RecoveryService.reconcileAll` -> `QuarantineMoves.settle` with no check of
   the authorization; and a release committed while the worker was between its check and the rename did not
   stop the rename. The moved object then carried an `ADMINISTRATOR_ATTESTED` ledger hash, which destruction
   trusted for deletion.
2. **Repair inspection-to-commit race.** `inspectSuperseded` looked at the old location before the commit
   transaction; an object appearing in between was retired as MISSING, and destruction treated every
   historical location as "report only", recording BYTES_REMOVED.
3. **Inaccessible paths read as absent.** `Files.exists` returns false when the path cannot be examined
   (permission, unreadable parent, unmounted volume), and `ManagedVolume.locate` treated an unreadable parent
   directory as missing. Scans recorded MISSING, `resolveAbsent` closed unresolved records, `settle` recorded
   SOURCE_ABSENT, repair retired generations as MISSING and destruction counted the location as removed.
4. **Attestation not atomic (MINOR).** The attestation, its operation event and the job were three separate
   commits; a failure between them left an authorization without work, or a note without either.

### Correction design (documented before coding)

* **Authorization lifecycle** (no new tables; the states are already durable):
  `OPEN` -> `ATTESTED` (attested_by/at, operation event and job committed in **one transaction**) ->
  `EXECUTING` (the move intent, at the deterministic quarantine path `att-<record>` of the record's operation)
  -> `RESOLVED QUARANTINED` (in the settle transaction) - or `RESOLVED NOT_ARCHIVAL` (release) from `OPEN` or
  `ATTESTED` only. Worker, destruction and release all hold the record's operation lock (PostgreSQL advisory
  lock, cross-process) while they act, and the worker re-reads the authorization under that lock immediately
  before recording its intent. Release is refused while the lock is held or once a move was recorded (it then
  names where the object is or is going); a released record can never gain an intent, so no worker, retry or
  recovery can move it. Settling an attested move re-validates the authorization (record still open and
  attested at that source) before any rename; destruction trusts an attested ledger hash only together with
  the record that was resolved QUARANTINED by that move. Duplicate attestations are idempotent (the existing
  attestation is returned; no second job).
* **Three-valued presence.** One probe (`Presence`) answers PRESENT, VERIFIED_ABSENT or UNKNOWN by reading
  attributes component by component without following links: only "no such file", on a volume whose marker is
  still there, is absence. Permission errors, unreadable directories, links in the chain, a vanished marker or
  any I/O error are UNKNOWN, which every caller treats as "cannot decide": no MISSING, no record closed, no
  SOURCE_ABSENT, no BYTES_REMOVED; scans report UNREADABLE (paths only, to Administrators) and recovery
  retries. The probe goes through the fault seam so tests inject access failures identically on Windows and
  Linux.
* **Former locations stay accountable.** The repair's look before the commit is no longer evidence after it:
  destruction re-inspects *every* location the content was ever at (historical generation paths, operation
  targets, ledger sources and quarantine paths) and records any unidentified object there as unresolved
  (CLEANUP_INCOMPLETE until it is gone, released or verified), instead of reporting it and completing. Scans
  keep reporting such objects as unexpected. Nothing unrelated is deleted or moved.

### Migration

No migration. The lifecycle uses the V0019 columns (attestation, status, resolution) plus the ledger intent at
a deterministic path; synchronization uses the existing operation advisory locks; presence and former-location
accounting are application rules over the existing `unresolved_object` table. V0001-V0019 are unchanged.

## Remaining limitations (unchanged scope decisions)

Linux separation per unit with one account (B1); residual directory-swap race (P2-E); NAS fallback
documented only (B4); JDK-only thumbnails; no resumable uploads; no scheduled scans; ingestion volume not
yet in the configuration export (Phase 8). Change History redaction covers structured references (File
UUIDs, friendly IDs, content IDs); free-text reasons that describe a restricted File in other words cannot
be recognised and are shown.
