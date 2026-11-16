# Phase 4 WP1: L1 locking-protocol proposal (WP1-R1, GAS-P4-002), revision 3.1

**Status: REVISED PROPOSAL for owner review and independent Codex assessment. Not implemented.**
- No production locking code, SQL, trigger or migration in this document has been written.
- The test designs in §10 are specifications; none of them is claimed to pass.
- Executed evidence is limited to the bounded, non-production checks in §11.
- **Revision 3.1** reconciles revision 3 (`569753f`) with the second Stage A review, finding by finding. The
  reconciliation matrix is in `PHASE-4-WP1-AUDIT-CORRECTIONS.md`. Changes from revision 3 are marked **[3.1]**.
- **This is not a readiness claim.** PO-1 is contradicted by source (§2.3), PO-2 is replaced by the key ledger with
  its own open obligations (§2.5), and the theorem in §5 is conditional on decisions D-LP-1…D-LP-9 and proof
  obligations PO-A…PO-D.

| | |
|---|---|
| Revision | 3: responds to the second Stage A review (`Phase-4-WP1-Stage-A-Second-Review.md`) and the Revision 3 direction (baseline `d2c96f2`) |
| Supersedes | Revision 2 at `d2c96f2`. Its invariant I-ADV, proof obligation PO-1, the claim that operation-owned tables are never locked explicitly, the batch-preflight guard contract and the catalog paragraph of lemma L2 are **withdrawn**. |
| Retained | The validated directions of SA-R1 (File before Record in `linkFile`), SA-R2 (stabilised derivative closure) and SA-R4 (creation-only grouping dependencies). No new evidence contradicts them. |
| Inputs and limits | The full Revision 3 direction and the complete second review (`Phase-4-WP1-Stage-A-Second-Review.md`) were received. Revision 3.1 accounts for every finding of the review individually (reconciliation matrix in the correction record). |

---

## 0. Response matrix (Revision 3 direction §1–§9)

| Direction item | What revision 3 does | Where |
|---|---|---|
| §1 Replace PO-1 and I-ADV | I-ADV and PO-1 withdrawn. Writer-by-writer resource-protection matrix for every preservation writer and every cross-row trigger, built from source and the live catalog. The false claims it exposes are listed (§2.3). | §2.3, §2.4 |
| §2 D-LP-8 as an active decision | Options A, B and C evaluated. **Recommendation: Option C**, content-row coordination (CR-1), with operation rows classified by writer kind (CR-2), content-less rows under their operation (CR-3) and location-key invariants (CR-4). Existing operation-before-content paths are analysed, not assumed away. No blocking advisory acquisition is added. Seven Phase 2 changes are listed for approval. | §3.2, §4.9, §7 |
| §3 PO-2 unique-key ledger | Every primary, unique, partial-unique and logical conflict key: contenders, holder, first conflicting wait, protection, classification, tests and assumptions. No constraint is weakened. | §2.5 |
| §4 Guard contract | Batches removed. Every blocking acquisition is checked immediately before its SQL; inserted rows keep their public keys; modes, duplicates and strongest-mode handling are explicit; re-lock protection of WP1-R3 is kept; the `commitShared` parent re-lock is replaced by an assertion; rollback resets state. The false "never explicitly locked" assertion is replaced. | §6 |
| §5 Catalog and reference locks | Source → Repository dependency, terminal reference waits from Record, Citation, worksheet overrides, session correction, history, vocabularies and users. A narrowly scoped terminal-reference rule (TR) is proved; no lock is claimed to be held that is acquired implicitly. | §4.8, §5 (L7) |
| §6 Closure boundaries | READ COMMITTED; a new transaction per attempt; exactly three attempts; the original `expectedVersion` and actor; missing and hidden-root outcomes without leakage; authorization and lifecycle checks before any retry-exhaustion message; full rollback; history and inheritance; deep, all-state and growing graphs. | §4.6, LP-11, LP-12 |
| §7 Worksheet entry points | `addFiles`, `addBatch` and `addByFriendlyIds` specified separately; authoritative File state read under FOR SHARE after lock waits; restriction and destruction races; deduplication and history order; one-row compaction updates; integer bounds; public keys kept in the guard. `saveMarks` duplicate edits and precedence preserved independently. | §4.3, §4.4 |
| §8 Test contracts | LP-1…LP-12 amended as directed; LP-10g and LP-13 added. The universal baseline-deadlock requirement is deleted; every test is classed BASE, COMPAT, DB or ACC, and ACC tests follow the acceptance rules. | §10 |
| §9 Deliverables | 1 this document; 2 §0.1; 3 §2.2–§2.4; 4 §2.5; 5 §3.3, §4.9; 6 §8; 7 §6; 8 §10; 9 §7; 10 §5 (proof obligations with counterexample schedules) and §12; 11 the commit SHA is given in the checkpoint report. Evidence classes: §0.2. | — |

### 0.1 SA-R1…SA-R6 response matrix (status at revision 3)

| Finding | Revision 2 response | Revision 3 status |
|---|---|---|
| SA-R1 Record → File | `linkFile` File FOR SHARE before Record; G → L → C → G reproduced (40P01) | **Retained** (§4.5). Worksheet-to-File edges removed by F before K (§4.2–§4.3). `add` now takes F FOR SHARE so the File state it reads is authoritative (§4.3). LP-9a observes the corrected blocking. |
| SA-R2 derivative closure | Discovery, ordered locks, re-discovery, bounded restart; complete SQL | **Retained**, with implementation boundaries: READ COMMITTED, a new transaction per attempt, exactly three attempts, original version and actor, no-leak outcomes, full rollback, all graph shapes (§4.6). |
| SA-R3 preservation inventory | Inventory; File before content (D-LP-6a, 6b); I-ADV | **Revised.** I-ADV withdrawn (§2.3). Writer and trigger protection matrix (§2.4), key ledger (§2.5), Option C (§3.2–§3.3, §4.9), Phase 2 changes P2-8a…f. D-LP-6a and 6b retained; the `commitShared` re-lock becomes an assertion. |
| SA-R4 grouping dependencies | Classification source only on the new-Record path | **Retained** (§4.2); LP-4b and LP-4c cover applied sources and replay. |
| SA-R5 guard before SQL | Batch preflight | **Replaced** by a per-statement, non-interleavable contract that keeps inserted keys and WP1-R3 re-lock rules (§6); LP-8b negative tests. |
| SA-R6 test designs | LP-1…LP-12 | **Amended** (§10): baseline and corrected schedules separated, test classes, corrected-path acceptance rules. |

### 0.2 Evidence classes used in this document

| Class | Meaning | Where |
|---|---|---|
| **Independently verified** | Confirmed by another party | Only LP-6 and the SA-R1 reproduction ran in CI on all four jobs (run `43831470275`); the reviewer has not yet assessed revision 3 |
| **Executed (builder)** | Run by the builder outside CI, on local PostgreSQL 16 | §11.3 predicate waits; §11.4 schema extraction |
| **Builder source analysis** | Read from source, schema and triggers at `d2c96f2`, not executed | §2.1–§2.5 |
| **Proposed design** | Not implemented | §3–§9 |
| **Test specification** | Not implemented | §10 |

## 1. Summary

- **Revision 2's preservation argument was wrong.** It rested on I-ADV: that every write to an operation-owned row is
  made under that operation's advisory lock. Source inspection shows four kinds of writer that hold no such lock:
  - Administrator retry and cancel requests;
  - receiver heartbeats and recovery abandonment of RECEIVING uploads;
  - IntegrityService and RecoveryService changing content state, whose triggers rewrite *other* operations' rows and
    the content's generation;
  - quarantine settlement, which releases every operation's claim at a location.

  Revision 2 also said operation-owned tables are never locked explicitly; `FileOperationRepository.note` and
  `UnresolvedObjectRepository.lockForUpdate` do lock them.
- **Revision 3 replaces I-ADV with a coordination protocol (Option C)** that classifies every writer by the
  resources it actually touches:

  | Rule | Content |
  |---|---|
  | **CR-1 content coordination** | Every transaction that writes a content-scoped preservation row of content X (X's state, any generation of X, an unresolved or quarantine row of X) holds X's `file_content` row **FOR UPDATE first**, at L1c, before any such write. Content-scoped rows are then never contended between two such transactions. |
  | **CR-2 operation rows** | A transaction that writes an operation row O either holds O's advisory lock, or is a *single-resource transaction* (it writes O and O's event rows and nothing else, and takes no other blocking lock). |
  | **CR-3 content-less rows** | Unresolved and quarantine rows with no content are written only under the advisory lock of their operation. |
  | **CR-4 location keys** | Location uniqueness keys are claimed only for a location the claimant created exclusively or already owns; trigger and settlement writes keyed by location match only rows of the writer's own operation or no committed row. |

- **Under the decisions of §8, §5 shows that no wait-for cycle exists** among the writers of §2. The proof depends on:
  - proof obligations PO-A…PO-D, each with a test (PO-E withdrawn in 3.1; see D-LP-9);
  - the reconciliation of §2.4 with the second review's counterexamples (R3-OPEN-1).
- **The order is unchanged from revision 2** except for one new position:

  | Level | Resources |
  |---|---|
  | L0 | restriction operation |
  | L1a | capture session (S) |
  | L1b | Files (F), ascending |
  | L1c | content (Ct), ascending |
  | **L1p** | **content-scoped preservation rows (P), only while holding their Ct (CR-1)** |
  | L1d | worksheet keys (K), ascending |
  | L1e | Archival Record (R) |
  | L1f | Source, then Repository (terminal references, rule TR) |
  | L2…L5 | Phase 4 |

  No transaction holds both a P row and a K or R lock, so L1p's position relative to L1d…L1f is immaterial; it is
  placed after Ct because it is coordinated by Ct.

## 2. Inventory (current behaviour at `d2c96f2`)

Notation:
- `X↑` = ascending UUID order. `U` FOR UPDATE, `Sh` FOR SHARE, `KS` FOR KEY SHARE, `NKU` the NO KEY UPDATE taken by
  an ordinary UPDATE.
- `ins` INSERT, `uq` uniqueness wait, `fk` implicit foreign-key KEY SHARE, `dfk` a DEFERRABLE INITIALLY DEFERRED
  foreign key (its KEY SHARE is taken at COMMIT), `trg` trigger.
- `adv(O)` = the operation's session advisory **try**-lock (held on a separate connection for the whole worker step);
  `xadv(O)` = the transaction-scoped advisory try-lock. `[tx]` marks a transaction boundary.

### 2.1 Phase 3 (records)

Unchanged from revision 2 §2.1. The writers are:
- G `GroupingService.applyChunk`;
- A `CaptureSessionService.add`;
- M `saveMarks`, X `removeFile`, O `correctOverrides`, SC session correct;
- L `RecordService.linkFile`; RL, RR and RC (other Record writes);
- CI `CitationService`;
- CA `CatalogService`.

Two additions from the catalog inspection:
- **RR Record correction** may change `archival_record.source_id`, which takes **fk KS on the new Source** after
  R U.
- **SC session correction** may change `repository_id` and `source_id` (fk KS on both, after S U, in
  foreign-key trigger order).

### 2.2 Cross-row trigger effects (live catalog at `d2c96f2`)

| Trigger (table, event) | Rows it writes or locks | Lock effect |
|---|---|---|
| `archival_file_restriction_cascade` (archival_file, NORMAL → RESTRICTED) | NORMAL children via `file_derivation`, recursively | NKU on descendants (SA-R2) |
| `file_derivation_inherit_restriction` (file_derivation ins) | parent `FOR SHARE`; child NKU if the parent is RESTRICTED | Sh on the parent, NKU on the new child |
| `file_content_missing_retires_ownership` (file_content → MISSING) | `file_operation` rows with `target_created` at the CURRENT generation's location | NKU on **other operations' rows**, by location |
| `file_content_vacates_current_generation` (file_content ↔ MISSING) | the content's CURRENT generation (`vacated`) | NKU on X's generation |
| `file_content_generation_retires_ownership` (generation → QUARANTINED, MISSING, DESTROYED) | `file_operation` rows with `target_created` at the generation's path | NKU on **other operations' rows**, by location |
| `file_content_generation_claims_location` (generation ins) | `claim_physical_location`: `INSERT … ON CONFLICT (volume_id, path) DO NOTHING` | uq on `physical_location_owner` |
| `file_operation_claims_location` (op ins, or update of `target_created`, `target_path`, `volume_id`) | deletes the op's own owner row; claims the new location | uq on `physical_location_owner` |
| `file_operation_single_owner`, `file_content_generation_single_owner`, `…_unresolved_has_record`, `unresolved_object_generation_keeps_record` | reads only (DEFERRED; at COMMIT) | none |
| `archival_file_restriction_inherited`, `archival_file_tombstone_final`, `storage_volume_preservation_guard`, `file_capture_context_applied_final`, identity and append-only triggers | reads or rejects | none |

**Deferred foreign keys:** `file_operation.file_id → archival_file` is DEFERRABLE INITIALLY DEFERRED. Its KS on the
File is taken at COMMIT, after every other statement of the transaction.

### 2.3 False statements in revision 2 (withdrawn)

| Revision 2 statement | Counter-evidence in source at `d2c96f2` |
|---|---|
| I-ADV: a `file_operation` row is updated only under that operation's advisory lock | `OperationAdminService.retry` transitions BLOCKED operations with no lock; `cancel` calls `note` (FOR UPDATE) with no lock; `IngestionService` heartbeat and staging, and `abandonStaleReceiving`, write RECEIVING rows with no lock; the content-MISSING and generation-retirement triggers update other operations' rows; `QuarantineMoves.settle` calls `releaseTargetsAt` for every operation at a location |
| Generation, unresolved and quarantine rows are written under advisory ownership or the content's U lock | `QuarantineMoves.settle` (generation, unresolved, quarantine), `quarantineSuperseded` (generation, unresolved), `UnresolvedObjects` release, attest and close (unresolved, generation), the DestructionProcessor completion transaction (all four, content last), and `markVacated` all write without the content lock |
| Operation-owned tables are never locked explicitly (old LP-10b) | `FileOperationRepository.note`: `SELECT … FROM gas.file_operation … FOR UPDATE`; `UnresolvedObjectRepository.lockForUpdate`: `… FROM gas.unresolved_object … FOR UPDATE` |
| Lemma L2: Record and Citation fk on S and C are covered because "C last" | The C locks are taken implicitly by foreign keys in Record, Citation, session and override writers, not held beforehand. They are covered by rule TR (§5, L7), not by the order. |
| IntegrityService transactions take a single content | Correct for the explicit lock, but their state change fires two cross-row triggers (generation, other operations). They also take fk KS on the content (finding insert) before NKU on it: an upgrade |

### 2.4 Writer-by-writer resource-protection matrix (preservation)

Columns:
- **Ownership:** the advisory lock held, if any.
- **Sequence:** blocking acquisitions in order, per transaction.
- **Shared rows and keys:** rows or keys another writer can also touch.
- **Triggers:** cross-row trigger effects.
- **Classification:** the class under revision 3 (CR-n), or the change needed.

| # | Writer, transaction | Ownership | Sequence (current) | Shared rows and keys | Triggers | Classification under revision 3 |
|---|---|---|---|---|---|---|
| P1 | `OperationProcessor.plan`, `planRepair`, `reject`, `resume`, `toWaiting`, `block`, `protectAndPromote`, `quarantinePartialAndReplan` (each a `transition` [tx]) | adv(O) | O NKU → event ins | O; event key (O, seq) | — | CR-2 (owner) |
| P2 | `createOwned` [tx] | adv(O) | (REPAIR) Ct **Sh** → O NKU (`markTargetCreated`) | Ct; O; location key | `file_operation_claims_location`: uq (v, target) | CR-2 owner; Ct Sh first is consistent with CR-1 (no P write); location under CR-4 (exclusive filesystem create precedes the claim) |
| P3 | `unverifiedOwnCopy` [tx] | adv(O) | O NKU (release) → U ins (uq OPEN (v, path)) → O NKU | O; U location key | claims trigger (delete own owner row) | REPAIR: **change P2-8f** (Ct U first when content exists); INGEST (no content): CR-3 |
| P4 | `commitShared` [tx] | adv(O) | Ct U → Ct NKU → O NKU → (insertFile) parent F U → F ins (private) → derivation ins | Ct; parent F | derivation inherit: parent Sh (held) | **D-LP-6b** (parent F before Ct); parent re-lock replaced by assertion (§6) |
| P5 | `commitNewContent` [tx] | adv(O) | O NKU → O NKU (release) → Ct ins (private) → G ins (private) → parent F U → F ins (private) → derivation | O; location key (own target); parent F | claims: delete own owner row, then claim the same path for the generation (uq) | CR-2 owner. Parent F follows only owned and private resources; the location key is the operation's own path (CR-4, PO-C). No change. |
| P6 | `commitRepair` [tx] | adv(O) | O NKU → **Ct U** → U ins (uq OPEN) → G(old) NKU → O NKU (release) → G(new) ins → Ct NKU → findings NKU → history | O; Ct; G(X); U(X) location key; findings of X | G(new) claims location (uq); G(old) → MISSING retires ownership (matches nothing: PO-B) | O first is safe: O's other writers are single-resource transactions (CR-2). Ct then P: CR-1. **No change.** |
| P7 | `quarantineSuperseded` inner [tx] | adv(O) | U ins (uq OPEN) → G NKU | U(X); G(X) | G → MISSING retires ownership | **Change P2-8b:** Ct(X) U first |
| P8 | `finish`, `deleteStaging`, `ioProblem` (`note`), `incrementAttempts` | adv(O) | O NKU or O U (`note`) → event | O | — | CR-2 owner |
| P9 | `OperationAdminService.retry` (`transition` [tx] or `note` [tx]) | **none** | O NKU (or O U) → event ins | O; event key | — | CR-2 single-resource. Its transition sets no foreign-key column. |
| P10 | `OperationAdminService.cancel` (`note` [tx]) | **none** | O U → event ins | O | — | CR-2 single-resource |
| P11 | `performCancel` → `applyCancel` final [tx] | adv(O) | (`claimBecomesUnresolved`: O NKU → U ins) or O NKU (release) → O NKU (transition) | O; U location key | claims trigger (own row) | REPAIR with unresolved: **change P2-8f** (Ct first); otherwise CR-2 owner and CR-3 |
| P12 | `applyCancel` → `QuarantineMoves.settle` | adv(O) | see Q2 | | | see Q2 |
| P13 | `IngestionService.receive`: op ins [tx] | none (the row is new) | O ins (uq (batch_id, item_key)) → fk parent F KS → event ins | batch/item key; parent F | claims trigger (no target yet: nothing) | private O; uq on batch/item is a first-wait (L6), ledger K3 |
| P14 | receiver `heartbeat`, `renewReceivers` (timer), `transition` RECEIVING → STAGED, `abandon` (RECEIVING → ABANDONED) | none | O NKU, predicate `state = 'RECEIVING'` → event | O | — | CR-2 single-resource, **state-specific [3.1]**: every statement's predicate is `state = 'RECEIVING'`; no advisory-owning writer touches a RECEIVING row (the worker starts at STAGED). `renewReceivers` updates several RECEIVING rows in one statement; it holds no other resource, so each wait is on a single-resource or receiving peer. |
| P14b **[3.1]** | `IngestionService.cleanup`, `RecoveryService.cleanStaging`, and the post-transaction `markStagingCleaned` calls in `applyCancel`, `destroy` and DestructionProcessor (one autocommit statement each) | none | O NKU (`staging_cleaned`) | O | — | CR-2 single-resource. They may wait for O's owner (for example a worker still finishing a final operation's step); they hold nothing while waiting (L6). LP-10i. |
| P15 | `RecoveryService.runOnce`: `abandonStaleReceiving` per op [tx] | none | O NKU → event | O | — | CR-2 single-resource |
| P16 | `RecoveryService.runOnce`: `presentButRecordedMissing` → `setState` (one statement) | none | Ct NKU → (trg) G(X, CURRENT) NKU → (trg) O-at-location NKU | Ct; G(X); other O | vacates; missing-retires-ownership | CR-1: Ct is the statement's first lock. The O update matches no committed row (PO-B). **No change.** |
| P17 **[3.1]** | `RepairService.receive`: op ins [tx with its event] | none (the row is new) | O ins (`batch_id` NULL: the batch/item key never conflicts) → fk KS on content-less references: `storage_volume`, `app_user`, and the selected open `integrity_finding` (`finding_id`) | finding row (KS) | — | Private O. The finding fk KS conflicts only with FOR UPDATE or key updates of the finding; findings are only ever updated non-key (`resolve`, NKU), so the KS never waits. `app_user` per D-LP-9. No change. |
| D1 | `FileService.destroyHolding` [tx] | xadv(repairs), try, bounded wait | F U → (replacement read) → Ct U → F NKU (fk replacement KS) → Ct NKU → repair O NKU → DESTROY O ins (dfk file at COMMIT: held) | F; replacement F; Ct; repair O | — | **D-LP-6a** (F↑ {file, replacement} before Ct). Repair O under xadv: CR-2 owner. |
| D2 | `DestructionProcessor.process` → `removeBytes` | adv(DESTROY op) + tryLockAll(every other writer of X) | see D3…D6 | | | |
| D3 | `stopUnfinishedRepairs` (`transition` [tx] per repair; `note`) | holds the repair's adv (tryLockAll) | repair O NKU → event; own O U → event | O | — | CR-2 owner |
| D4 | DP `markVacated` (one statement) | adv | G(X, CURRENT) NKU | G(X) | — (`UPDATE OF state` not fired) | **Change P2-8c:** Ct(X) U first (CR-1); or a single-resource statement (see §4.9) |
| D5 | DP `claimBecomesUnresolved` [tx] for a stopped repair's copy | holds that repair's adv | O NKU → U ins (uq OPEN) | O; U(X) location key | claims trigger | **Change P2-8c** (Ct(X) U first) |
| D5b **[3.1]** | DP `newRecord` (via `classify` and `refuse`): `recordIfAbsent` outside any transaction of the caller (autocommit) | adv + all writers | U ins (uq OPEN (v, path)) → fk KS Ct(X), O | U(X) location key | — | **Change P2-8c, extended:** run in a transaction with Ct(X) U first. Without it, the insert holds a public OPEN key while its fk waits for Ct(X). |
| D6 | DP completion [tx] | adv + all writers | U NKU (resolve) → own O NKU → G NKU (each → DESTROYED) → Q NKU → **Ct NKU last** → other O NKU (release) | U(X), G(X), Q(X), Ct(X), other O | G → DESTROYED retires ownership by location | **Change P2-8c:** Ct(X) U **first**. The other O are held by tryLockAll (CR-2 owner). |
| D7 | DP `incomplete` (`transition`, `note`) | adv | O | O | — | CR-2 owner |
| Q1 | `QuarantineMoves.move` → `intend` (one statement) | the caller's adv | Q ins (uq (v, quarantine_path)) → fk O KS (own) | Q location key | — | CR-3 or CR-1 via the caller; ledger K9 |
| Q2 | `QuarantineMoves.settle` [tx] | the caller's adv (`reconcileAll`: tryLock; worker; cancel; DP) | Q NKU → **every O at source NKU** (`releaseTargetsAt`) → U NKU (resolve) → G NKU (`retireGenerationAt`) | Q(X), O by location, U(X), G(X) | G retires ownership by location | **Change P2-8a:** Ct(X) U first when content is not null; `releaseTargetsAt` matches only the entry's own operation (PO-B) |
| U1 | `UnresolvedObjects.release` [tx] | adv(u.operation) | U U (`lockForUpdate`) → U NKU → G NKU (`retireGeneration`) → O U (`note`) → event | U(X), G(X), O | G → MISSING retires ownership | **Change P2-8d:** Ct(X) U first when content is not null |
| U2 **[3.1]** | `attestUnderLock` [tx]. Reached **with** adv(u.operation) for an open, unattested record, and **without** any advisory lock when the record is already attested or closed (`attest`, lines 121–122) | adv(u.operation), or none | U U → (open and unattested only) U NKU → O U (`note`) | U, O | — | **Change P2-8d** (Ct(X) U first, in both branches). The no-advisory branch writes nothing after its U lock (it returns or throws), so it is single-resource apart from the Ct lock P2-8d adds. |
| U3 | `quarantineAttested` | adv(first.operation) | `note` [tx]; `QuarantineMoves.move` (Q1, Q2) | | | as Q1 and Q2 |
| U4 | `closeIfVerifiedAbsent` [tx] | adv(u.operation), or held by DP | U U → U NKU → G NKU | U(X), G(X) | G retires ownership | **Change P2-8d** |
| I1 | `IntegrityService.checkContent` MISSING [tx] | none | finding ins (**fk Ct KS**) → Ct NKU (**upgrade**) → (trg) G NKU → (trg) O-at-location NKU | Ct, G(X), other O | vacates; missing-retires-ownership | **Change P2-8e:** Ct(X) U first (final strength) |
| I2 | `damaged` [tx] | none | finding ins (fk Ct KS) → Ct NKU → G NKU (`recordDamageEvidence`) | Ct, G(X) | — | **Change P2-8e** |
| I3 | `reappeared` [tx] | none | Ct NKU → (trg) G NKU → findings NKU | Ct, G(X), findings(X) | vacates | **Change P2-8e** |
| I4 | `reverify` [tx] | none | Ct NKU → findings NKU | Ct, findings | vacates when leaving MISSING | **Change P2-8e** |
| I5 | `record`, `insertFindingIfNew`, `observeExisting`, scan rows (one statement each) | none | finding ins (fk Ct KS); observation ins (uq (scan, finding)); scan NKU | ledger K12, K13 | — | first-wait or private (L3, L6) |
| V1 | `VolumePreservationService` initialise, set ingestion volume [tx] | none | storage_volume NKU, archive_instance NKU | volume rows | `storage_volume_preservation_guard` (reads) | outside L1 (no archival writer locks volumes) |

### 2.5 Unique and logical conflict-key ledger (PO-2 completed)

"First wait" is the first statement at which a contender can wait on the key. Classes:
- **private**: fresh UUID or the inserter's own key; no contender can exist.
- **serialised**: contenders hold a common lock before reaching the key, so the key adds no edge.
- **ordered**: the key is acquired in the global order (rule 1).
- **first-wait**: the key is the contender's first blocking acquisition (L6).
- **no-wait**: no constraint, so no database wait.

| Key | Table and constraint | Contenders | First wait | Protection | Class | Test and assumption |
|---|---|---|---|---|---|---|
| K1 | `file_capture_context` PK `(file_id)` | A (`add`) inserting; **X (`removeFile`) deleting [3.1]**: an uncommitted DELETE holds the key, and a concurrent insert of that `file_id` waits for it | the insert | inserts in ascending `file_id` after S and F; X holds S(own) and K U and takes nothing after the DELETE except history (terminal) | **ordered** (L1d); the key is a public resource, not a private-row exemption | LP-2, LP-3, LP-2c (remove versus add of one File); the key stays in the guard state after INSERTED (§6) |
| K2 | `file_capture_context_sequence` `(capture_session_id, sequence_number)` | A (insert, compaction) | the insert or compaction UPDATE | S held U | serialised (S) | LP-3 |
| K3 | `file_operation_batch_id_item_key_key` `(batch_id, item_key)` | IR receive (the same batch item uploaded twice) | the op insert, the transaction's first statement | none needed | first-wait | PO-A test: receive transaction holds nothing before the insert |
| K4 | `file_operation_event` PK `(operation_id, seq)`, seq = `max+1` | every event writer of O | the insert | every event writer has already updated or locked O in the same transaction (`transition`, `note`, `abandonStaleReceiving`) | serialised (O) | LO test: every call of `event` follows an O write in the same transaction |
| K5 | `file_content_created_by_operation_key` | commitNewContent only | — | the operation's own id | private | — |
| K6 | `archival_file_ingest_operation_id_key` | insertFile only | — | own id; adv(O) | private | — |
| K7 | `file_content_generation_current` (partial: `content_id WHERE state = 'CURRENT'`) | commitRepair (new CURRENT after old leaves), commitNewContent (new content) | the insert | Ct(X) U (commitRepair); new content private | serialised (Ct) | LP-10c |
| K8 **[3.1, split]** | `file_content_generation_location` `(volume_id, storage_path)` | generation inserts: `commitNewContent` (path built from its own new content UUID), `commitRepair` (path from content UUID and the planned generation) | the generation insert | new content: the path contains a fresh content UUID (private). Repair: two repairs of X can plan the same `maxGeneration + 1` (`planRepair`, no Ct lock; review §4.2). Only one can create the file (CREATE_NEW); the other blocks with COLLISION. A repair commit inserts the generation while holding Ct(X) U, and every other repair commit of X must take Ct(X) first. | serialised (Ct) for repairs; private for new content | LP-10h (two repairs of one content planning one generation: one COLLISION, one commit, no 23505, retry plans max+1) |
| K8b **[3.1]** | `physical_location_owner` PK `(volume_id, path)`, and the partial unique index of one owner row per operation | `claim_physical_location` from an operation's `markTargetCreated` (in `createOwned`), and from a generation insert | the claim (V0017 deliberately waits on an uncommitted claim) | **shared key.** Protection: (i) an operation claims only after its own CREATE_NEW at that path succeeded, so a second claimant needs the file to have disappeared in between; (ii) the claim is the **last** blocking statement of `createOwned`'s transaction, so its holder never waits afterwards (terminal holder); (iii) a generation claim happens in `commitRepair` under Ct(X) U, or in `commitNewContent` on its own fresh path, and `createOwned` for a repair of X takes Ct(X) Sh first, so it cannot hold a claim while `commitRepair` holds Ct(X). | shared; terminal holder or serialised (Ct) | LP-10f (concurrent claimants after an external removal: the loser waits for a terminal holder, then COLLISION or ownership error; no 40P01) |
| K9 | `quarantine_object` `(volume_id, quarantine_path)` | `QuarantineMoves.intend` | the insert (one statement) | quarantine paths are derived from the operation and source (one per entry) | first-wait (one statement) | ledger test: the path is unique per (operation, source) |
| K10 | `unresolved_object_open_location` (partial: `(volume_id, path) WHERE status = 'OPEN'`) | P3, P6, P7, P11, D5, DP `newRecord` | `recordIfAbsent` (ON CONFLICT DO NOTHING waits for an uncommitted OPEN insert) | content-scoped records: Ct(X) U held (CR-1, after P2-8a…f); content-less records: adv(O) (CR-3). Two records at one path for different contents cannot arise: a path belongs to one content's generations (K8) or to one operation's target (CR-4) | serialised | LP-10d; PO-D |
| K11 | `storage_volume_active_name`, `app_user_username_ci`, `archive_instance` keys | administration | the insert or update, first statement | — | first-wait; outside archival writers | — |
| K12 | `integrity_finding` "one OPEN finding per kind and content or location" (`insertFindingIfNew`) | IntegrityService | none: check-then-insert, no constraint | — | **no-wait** (existing Phase 2 behaviour: duplicates are possible, no wait) | not changed; reported here only |
| K13 | `integrity_finding_observation` PK `(scan_id, finding_id)` | one scan at a time per scan id | the insert | the scan row | private to the scan | — |
| K14 | `capture_session_group` PK `(capture_session_id, group_key)` | G | the insert | S U | serialised (S) | LP-4a |
| K15 | `record_file` PK `(record_id, file_id)` | G, L, RL | the insert | R U held by every inserter | serialised (R) | LP-9 |
| K16 | friendly numbers (`*_friendly_number_key`) | every creator | none: `nextval` never returns a value twice and holds no lock until commit | sequence | no-wait (non-transactional) | — |
| K17 | `change_history`, `historical_date`, `record_role`, `citation` PKs | their writers | — | fresh UUID | private | — |
| K18 | `grouping_run`, `grouping_run_group` PKs | G | — | fresh run id; S held | private | — |
| K19 **[3.1]** | `file_content_generation` PK `(content_id, generation)` | `commitRepair`, `commitNewContent` | the insert | repair: Ct(X) U; new content: private | serialised (Ct) or private | LP-10h |
| K20 **[3.1]** | grouping FKs: `grouping_run` → session and actor; `grouping_run_group` → run and Record; `capture_session_group` → session and Record | G | none: each parent is held (S, R) or private (new run, new Record) | — | covered | LP-4a |

**No constraint is weakened.** K12 is recorded as existing behaviour (a logical key without a database constraint);
it creates no wait and is outside this protocol.

## 3. Order and rules

### 3.1 Order

L0 → L1a S → L1b F↑ → L1c Ct↑ → L1p P (only under its Ct) → L1d K↑ → L1e R → L1f Source → Repository → L2…L5 (Phase 4).
L7 is implicit only.

### 3.2 Rules

1. **Monotonic.** Every blocking acquisition (explicit lock, ordered uniqueness insert, or an implicit lock that can
   wait) has a key (level, UUID) ≥ every key the transaction already holds. Levels may be skipped.
2. **Final strength first.** No upgrade at any level. A transaction that will update a row it has also referenced by
   a foreign key takes the update-strength lock first (this removes I1's KS → NKU upgrade).
3. **Parents first.** Before any statement that takes an implicit lock, the transaction holds that resource at the
   same or a stronger mode, or the resource is private (rule 5), or it is a terminal reference (rule TR). **No other
   exception** (the revision 1 exception stays withdrawn).
4. **Session membership in SQL.** Worksheet rows are locked only through a predicate on the transaction's own
   session.
5. **Private new rows.** A row inserted by the transaction may be referenced by it at any point; its public
   uniqueness keys stay keys the transaction holds (§6).
6. **CR-1, content coordination.** A transaction that writes, directly or through a trigger, any of X's state, X's
   generation rows, an unresolved or quarantine row whose `content_id` is X, or a finding of X, holds Ct(X) FOR
   UPDATE before the first such write. The lock is acquired at L1c, after any F locks. **[3.1]** A single statement
   whose first lock is the NKU of its own update of X (P16) satisfies CR-1: NKU conflicts with every other CR-1
   holder's FOR UPDATE or NKU. Only a statement that takes no other lock first may rely on this.
7. **CR-2, operation rows.** A transaction that writes operation row O, explicitly or by trigger, either holds O's
   advisory lock (session or transaction scope; always acquired by try, never waiting), or is a single-resource
   transaction: it writes O and O's event rows and nothing else, and its only blocking acquisition is O.
   - Trigger writes keyed by location match only rows of the writer's own operation, or no committed row (PO-B).
8. **CR-3, content-less rows.** An unresolved or quarantine row with a null `content_id` is written only under the
   advisory lock of its `operation_id`.
9. **CR-4, location keys.** `physical_location_owner` and the generation location key are claimed only for a
   location that the claiming transaction's own operation created by exclusive filesystem create, or released in
   the same transaction (PO-C).
10. **TR, terminal references [3.1, revised].** Source, Repository and vocabulary rows, and `app_user` (after
    D-LP-9), are terminal references:
    - **a.** every transaction other than a catalog correction takes on Source and Repository only implicit KEY
      SHARE (foreign keys). Every transaction takes on vocabulary rows and `app_user` only KEY SHARE (foreign keys)
      or NO KEY UPDATE (their own non-key updates). There is no explicit FOR UPDATE or FOR SHARE on `app_user` (this
      requires **D-LP-9**) or on vocabulary rows (true today);
    - **b.** catalog corrections take their single explicit FOR UPDATE as their first blocking acquisition. Their
      later waits are limited to a Source correction's KEY SHARE on its new Repository, and KEY SHARE on `app_user`
      and vocabulary rows;
    - **c.** no transaction updates a key column of a terminal reference (identity triggers).

    **Revision 3's TR (c), "the administrator check … takes no other lock afterwards", and (d) (PO-E) are
    withdrawn.** The administrator check's single statement locks several `app_user` rows and waits on later rows
    while holding earlier ones. A three-transaction cycle through it is reproduced in §11.5 (40P01).
11. **Presentation order is separate from lock order.**

### 3.3 Why Option C (D-LP-8)

| | **A: extend the total order to all shared preservation rows** | **B: smaller ordered core plus individually proven exceptions** | **C (recommended): content coordination plus classified exceptions** |
|---|---|---|---|
| What it requires | Pick a global position for O, G, U, Q and location keys, and reorder every writer to it. Operation-before-content paths (P5, P6) and content-before-operation paths (P2, D1, D6, the I and P16 triggers) disagree. One of them must change in every pairing, and trigger-driven location writes (which pick their rows by predicate at run time) must also be ordered. | Keep F, Ct, K, R and C ordered and prove each other writer acyclic one by one: terminal, first-wait or single-resource. | One coordinator (Ct FOR UPDATE) for every content-scoped row; the remaining shared rows (O, content-less U and Q, location keys) are covered by three classification rules, each checkable by a test. |
| Phase 2 changes | Most preservation writers, plus the triggers (they would have to lock in a defined order instead of by predicate). | Few now, but every new preservation writer needs a new proof; the I1/I2 and DP paths cannot be shown terminal (they hold Ct and then write G and O). | Six bounded changes P2-8a…f (P2-8c extended in 3.1 to DestructionProcessor `newRecord`), plus D-LP-6a and 6b already proposed,, all of the form "lock Ct FOR UPDATE first". No trigger changes. No advisory waits added. |
| Strength of the safety argument | Strong in principle, but the triggers' predicate-chosen rows make the order hard to enforce or test. | Weakest: a list of special cases. | Strong: content-scoped rows are serialised by one exclusive lock; the remaining cases reduce to proof obligations PO-A…PO-D with tests. |
| Behavioural change | Large | Small | Small: some preservation transactions wait for the content row where they now race; outcomes unchanged |

**The cost of C is serialisation of preservation writes per content.** That is acceptable: these are background or
Administrator operations on one content.

## 4. Revised writer sequences and SQL

### 4.1 Mapping of resources to SQL

As revision 2 §4.1, plus:

| Level | Lock statement |
|---|---|
| L1b F (KS) | `SELECT file_id FROM gas.archival_file WHERE file_id = :id FOR KEY SHARE` |
| L1c Ct (U, Sh) | `SELECT content_id FROM gas.file_content WHERE content_id = :id FOR UPDATE` (or `FOR SHARE`) (existing `FileContentRepository.lock`, `lockShared`) |
| L1p (explicit) | `SELECT state FROM gas.file_operation WHERE operation_id = :id FOR UPDATE` (existing `note`); `SELECT … FROM gas.unresolved_object WHERE unresolved_id = :id FOR UPDATE` (existing `lockForUpdate`) |
| L1f Source, then Repository | existing `lockSource`, `lockRepository` (catalog corrections only; rule TR) |

### 4.2 G: `GroupingService.applyChunk` (SA-R4, retained)

Unchanged from revision 2 §4.2:
1. S U, under the session's advisory try-lock.
2. Decide the path under S: `newRecord = (target == null)`.
3. **F↑ Sh** over chunk Files ∪ `continuesFrom` ∪ (new path only: the classification-source File).
4. **K↑ U** with the membership predicate over chunk rows ∪ (new path only: the classification-source row).
5. Accepted checks in the accepted order and precedence.
6. Writes unchanged. The new Record's fk KS on Source and classification are terminal references (TR); its fk on S
   is covered (S held).

### 4.3 A: `CaptureSessionService.add` and its three entry points (direction §7, D-LP-2)

**The common core `add(actor, session, ids)`**, in one transaction:
1. `AccessPolicy.require(EDIT_RECORDS)`; `ADD_LIMIT` check (unchanged; before the transaction).
2. **S U.** `base = maxSequence(S)`.
3. **Deduplicate** by first occurrence (`LinkedHashSet`), unchanged.
4. **Unlocked pre-read**, as today: missing or not visible → `hidden`; DESTROYED → `destroyed` (final state, so the
   unlocked read is authoritative); otherwise a *candidate*.
5. **F↑ Sh** over the candidates (KEY SHARE in revision 2 is replaced by SHARE, see below).
6. **Authoritative re-read under the lock**, in request order. The File's restriction and lifecycle cannot change
   until commit:
   - not visible now → `hidden`;
   - DESTROYED now → `destroyed`;
   - otherwise *eligible*, with eligible rank 1…n in request order.
7. **K↑ inserts** in ascending `file_id`:
   - **SQL:** `INSERT INTO gas.file_capture_context (file_id, capture_session_id, sequence_number, added_at, added_by) VALUES (:f, :s, :seq, :at, :by) ON CONFLICT (file_id) DO NOTHING`
   - **Provisional sequence:** `:seq = base + rank`.
   - **Outcome:** one row affected → *added*; zero → *taken*.
   - **Range check:** `base + n` is checked against `Integer.MAX_VALUE` before any insert; overflow → the accepted
     rule violation, with nothing inserted.
8. **Compaction**, for added rows in ascending final rank r = 1…m with `provisional ≠ base + r`:

   ```sql
   UPDATE gas.file_capture_context SET sequence_number = :target
    WHERE file_id = :f AND capture_session_id = :s AND sequence_number = :provisional
   ```

   - Each UPDATE **must affect exactly one row**; any other count is an internal error that rolls back the
     transaction.
   - The target is free at its turn (a skipped provisional slot, or one vacated by a lower-ranked row), so the
     immediate `(capture_session_id, sequence_number)` uniqueness holds at every statement.
   - `row_version`, `added_at` and `added_by` are not changed.
9. **History** for added rows in request order, unchanged.

**Linearization rule [3.1].** Each candidate File's outcome is decided by its state at the moment this transaction's
SHARE lock on it is granted:
- a restriction or destruction that committed before the grant is seen (the File counts as hidden or destroyed);
- one that starts after the grant waits for this transaction.

This includes restriction of a descendant through another root: that root's FC holds the File FOR UPDATE, so this
`add` waits, then reads RESTRICTED. The accepted per-item reads happened at varying times inside the transaction, so
an equivalent single point did not exist before. The new rule is strictly more precise and never counts a File
differently from some accepted interleaving (LP-3b).

**Why SHARE, not KEY SHARE (direction §7, "authoritative state after lock waits").** Under KEY SHARE a restriction
or destruction (NKU) could still commit between the re-read and this commit. Accepted Phase 3 behaviour (GAS-P3-002)
counts a File restricted before its in-transaction read as hidden. SHARE makes the step-6 read authoritative until
commit: a concurrent restriction or destruction waits for the add, or the add waits for it and then reads its
result. This matches `linkFile` (D-LP-5). It adds no wait-for cycle: F is ordered (L1b), and both a restriction (FC)
and a destruction (FD) start at F.

**Entry points** (counts unchanged):

| Entry point | Before the core | Result mapping |
|---|---|---|
| `addFiles(actor, s, ids)` | none | `added`, `taken`, `destroyed + hidden` (hidden equals nonexistent) |
| `addBatch(actor, s, batch)` | the batch is visible to the actor, else empty; Files filtered to those visible in an unlocked pre-read | `added`, `taken`, `destroyed`. `hidden` (a File restricted between the filter and step 6) is **not counted**, as GAS-P3-002 requires. |
| `addByFriendlyIds(actor, s, text)` | parse; deduplicate numbers; unknown numbers counted; known ones passed to `addFiles` | as `addFiles`, plus `unknown` in not-available |

**Guard state.** Each attempted insert key (`file_capture_context`, `file_id`) is recorded as INSERTED or TAKEN and
stays in the transaction's key set (§6).

### 4.4 M, X and O

| Writer | Revised sequence |
|---|---|
| M `saveMarks` | S U → K↑ U over the **distinct** file ids of the edits, with the membership predicate (`WHERE capture_session_id = :s AND file_id = :f FOR UPDATE`). Missing rows are recorded and **no error is raised at lock time**. Edits are then processed in request order, **duplicates included**, with the accepted per-edit precedence: `visibleFile` (not found) → row in this session (not found, from the lock result) → text and vocabulary validation → no-change skip → version Conflict → applied rule → update → history. A second edit of the same row sees the first edit's new version, exactly as today. |
| X `removeFile` | Unchanged: S U → K U through the predicate |
| O `correctOverrides` | Unchanged: K U → update (fk KS on Source and Repository: terminal references, TR) |

### 4.5 L: `RecordService.linkFile` (SA-R1, retained)

Unchanged from revision 2 §4.5:
1. **F Sh** before **R U**.
2. Revalidate the File under its lock in the accepted precedence.
3. Insert (fk KS covered by the held Sh).

### 4.6 FC: `FileService.correct` when restricting (SA-R2 retained; boundaries for direction §6)

Only NORMAL → RESTRICTED cascades. Every other `correct`, `delete` and `restore` keeps its single `F U`.

**Outside any transaction** (the method is not called inside another transaction; the implementation asserts
`!TransactionSynchronizationManager.isActualTransactionActive()` on entry):
1. `AccessPolicy.require(actor, EDIT_FILES)`. The `actor` and `expectedVersion` are captured once and used unchanged
   in every attempt.
2. Up to **exactly three attempts**. Each attempt is a **new** transaction (`ctx.tx().execute`, propagation
   REQUIRED, isolation READ COMMITTED, the database default, set explicitly on the template).

**Each attempt:**
1. **Root visibility, before any closure work:** `files().find(root)` filtered by `canSee(actor)`. If missing or
   hidden → `NotFoundException` (the accepted message), with no further statement. Nothing about descendants is
   read.
2. **Discovery** (statement 1, no lock): the full reachable set, any state, any depth.

   ```sql
   WITH RECURSIVE reachable(file_id) AS (
       SELECT CAST(:root AS uuid)
       UNION
       SELECT d.file_id
         FROM gas.file_derivation d
         JOIN reachable r ON d.parent_file_id = r.file_id
   )
   SELECT file_id FROM reachable ORDER BY file_id
   ```

3. **Acquisition** in ascending UUID order, the root in its place:
   `SELECT file_id FROM gas.archival_file WHERE file_id = :id FOR UPDATE`.
4. **Re-discovery** (a fresh statement, so a fresh READ COMMITTED snapshot): the same SQL. If its set differs from
   the locked set → `status.setRollbackOnly()` and return ABANDONED. **The whole attempt rolls back**: locks,
   history and any statement since step 1.
5. **Root checks under lock, in the accepted order:**
   1. visible (else not found, no retry);
   2. not DESTROYED (accepted rule message);
   3. `row_version == expectedVersion` (accepted Conflict, no retry);
   4. unrestrict rules (not applicable here).
6. **Restrict:**
   - `inheriting = unrestrictedDescendants(root)` (accepted SQL, over locked rows);
   - `updateAttributes`;
   - the V0014 cascade updates only NORMAL descendants, all held, so it does not wait;
   - history for the root, then `recordInheritedRestriction` for each inheriting descendant (accepted content and
     order);
   - post-check `unrestrictedDescendants(root)` is empty.
7. **After three ABANDONED attempts,** a final check in a new read-only transaction:
   - root not visible → `NotFoundException` (no leakage);
   - root DESTROYED → the accepted rule message;
   - version ≠ `expectedVersion` → the accepted Conflict;
   - otherwise `ConflictException("Derivative Files of " + root.friendlyId() + " were being added while it was being restricted; nothing was changed. Try again.")`.

   The message names only the root, which the actor can see. It reveals no count or identity of descendants.

**Implementation boundaries [3.1]:**
- The attempt loop is **outside** the transaction callback. An abandoned attempt returns normally from the callback
  after `setRollbackOnly()`; no exception is swallowed inside a rollback-only transaction, and no savepoint is used.
  Each attempt therefore starts with no locks held and an empty guard tracker.
- A reachable node that is missing at acquisition cannot occur (`archival_file` rows are never deleted). If it does,
  it is recorded as MISSING (never ACQUIRED) and the attempt fails with an internal error. It is **not** counted as
  graph growth.
- Steps 1 and 7 together make the hidden-root outcome identical to the accepted not-found outcome in every path; the
  exhaustion message is reachable only after the root passed visibility, lifecycle and version checks.
- **History semantics.** The restriction transaction records history only for the root and for the descendants it
  actually changed (NORMAL → RESTRICTED). A child published after the restriction commits gets its own accepted
  creation history from `insertFile`, with `restriction = RESTRICTED` inherited through the trigger. No entry for it
  is fabricated in the restriction transaction.

**Graph cases:**
- **Deep chains:** the recursive CTE has no depth bound; `UNION` removes duplicates (diamond-shaped graphs).
- **All-state graphs:** DESTROYED, DELETED and already RESTRICTED intermediates are discovered and locked.
  - The cascade does not descend below an already RESTRICTED node, but its descendants are already RESTRICTED by
    the inheritance invariant.
  - Locking them is harmless (more serialisation, no more edges).
- **Growth during the attempt:** detected at step 4 (LP-11).
- **Publication after stabilisation:** blocked by the held parent lock, then restricted by the inheritance trigger
  (LP-12).
- **Overlapping roots [3.1]:** two restrictions whose closures share nodes lock the shared nodes in UUID order; one
  waits for the other; the second then finds them RESTRICTED and changes only what remains NORMAL (LP-11b).
- **Publisher rollback [3.1]:** a publisher that rolls back while FC waits leaves no edge. FC's re-discovery equals
  its acquired set and it proceeds (LP-12b).

**Why the closure is stable** (revision 2 argument, retained):
- `file_derivation` is immutable and append-only.
- Every new edge's publisher locks the parent (FOR UPDATE in `insertFile`, FOR SHARE in the trigger).
- After step 3 every reachable node is held, so a new edge under a reachable node either committed before that
  node's lock was granted (seen by step 4) or waits for this transaction (and is then restricted by inheritance).

### 4.7 Phase 2 File-before-content writers (retained, with the guard correction)

| Writer | Revised sequence |
|---|---|
| FD `destroyHolding` (D-LP-6a) | Revision 2 §4.7, unchanged: F↑ {file U, replacement KS} → Ct U; accepted check order and messages. The DESTROY operation's deferred fk on the file is taken at COMMIT on a held row. |
| OS `commitShared` (D-LP-6b) | (adv) → **parent F U** → **Ct U** → state and generation re-check → op transition → `insertFile`. **`insertFile` takes no SQL lock on the parent** when called from `commitShared`: it calls `guard.assertHeld(L1_FILE, parent, UPDATE)` instead (§6). `commitNewContent` keeps its own parent lock (see P5). |

### 4.8 Catalog and reference writers (direction §5)

| Writer | Sequence | Terminal-reference waits |
|---|---|---|
| CA Source correct | Source U (first) → update (fk KS on the new Repository if it changed) → history (fk KS actor) | Repository KS: blocked only by a Repository correction (whose only explicit lock is that row) |
| CA Repository correct | Repository U (first) → update → history | actor KS only |
| CA create Source or Repository | private insert (fk KS on Repository, actor) | first-waits only |
| SC session correct | S U → update (fk KS Repository, Source, in foreign-key trigger order) | terminal; KS on both |
| O override correct | K U → update (fk KS Repository, Source) | terminal |
| RC, RR Record create and correct | (S or R held) → insert or update (fk KS Source, classification, session) | terminal; session covered (held, or first-wait for RC) |
| CI Citation | R U → insert or update (fk KS Source, R held) | terminal |
| Every writer | history insert (fk KS actor); vocabulary fk KS | terminal (L7) |
| Administrator check | `app_user` rows of enabled administrators `ORDER BY user_id FOR UPDATE` (first), then app_user updates | none after the first statement |

There is no typed sub-order among non-catalog references: KEY SHARE requests from non-catalog writers on Source and
Repository arrive in foreign-key trigger order, which differs by table. The safety argument is rule TR (§5, L7), not
an order.

### 4.9 Phase 2 preservation changes under Option C (P2-8a…f)

Each change inserts one statement, `SELECT content_id FROM gas.file_content WHERE content_id = :x FOR UPDATE`
(`FileContentRepository.lock`), at the **start** of the named transaction, when the content id is not null. Each
then re-reads the state it decides on under that lock (the re-reads already exist in each method, except where
noted).

| ID | Transaction | Notes |
|---|---|---|
| P2-8a | `QuarantineMoves.settle` final transaction (content not null) | Then the existing `setState(INTENDED → …)` compare-and-set decides; nothing else changes. |
| P2-8b | `OperationProcessor.quarantineSuperseded` inner transaction | The generation's state is re-checked by the existing compare-and-set (`QUARANTINE_PENDING` → …). |
| P2-8c | DestructionProcessor: completion transaction (replaces the implicit Ct NKU at its end); the `claimBecomesUnresolved` transaction for a stopped repair's copy; `markVacated` (wrapped in a transaction with the Ct lock) | The completion transaction's later `setState(DESTROY_PENDING → DESTROYED)` becomes an update of a held row. |
| P2-8d | `UnresolvedObjects.release`, `attestUnderLock`, `closeIfVerifiedAbsent` (content not null): Ct U **before** `lockForUpdate` | |
| P2-8e | IntegrityService: `checkContent` MISSING transaction, `damaged`, `reappeared`, `reverify`: Ct U first, **before** the finding insert | Removes the KS → NKU upgrade (rule 2). The existing compare-and-set state updates decide unchanged. |
| P2-8f | `OperationProcessor.unverifiedOwnCopy` and `applyCancel` when the operation has content (REPAIR): Ct U first | INGEST operations have no content yet: CR-3 under adv(O) |

**Not changed:**
- `commitRepair` (O own, then Ct U): O's only other writers are single-resource transactions (CR-2). See §5, L4.
- `createOwned` (Ct Sh first).
- `commitNewContent` (private content).
- `RecoveryService`'s single-statement state change (Ct is its first lock).
- Receivers and Administrator retry and cancel (single-resource).

**No advisory lock is added, and no existing advisory acquisition changes.** All remain try-locks, acquired before
any row lock (`destroyHolding`'s bounded retry loop included).

## 5. Wait-for argument (revision 3)

A transaction T **waits for** U when T requests a heavyweight lock, an implicit lock, or a uniqueness or tuple wait
that conflicts with something U holds. Advisory try-locks and sleeps never enter the wait queue, so they create no
edge.

Exceptions:
- Hikari pool waits and application-level retries are not database waits. They are bounded by timeouts and listed
  in §12.
- Session advisory locks held on a separate connection never block a statement.

**L1 (ordered acquisition).** If every blocking request carries a key ≥ every held key (rule 1) and no transaction
upgrades (rule 2), no cycle exists among those requests.
- A cycle would force all requested keys equal, held by all, requested again in a conflicting mode, which only an
  upgrade does.
- Uniqueness waits on ordered inserts (K1) are requests on their key.

**L2 (implicit edges).** Every implicit lock is one of:
- **(a)** on a resource already held at equal or stronger mode (rule 3);
- **(b)** on a private row (L3);
- **(c)** on a terminal reference (L7);
- **(d)** a trigger write covered by L4 or L5;
- **(e)** a deferred foreign key on a held or private row (`file_operation.file_id`: FD holds the File U; commit
  transitions set `file_id` to the transaction's own new File).

**L3 (private rows).** A row inserted by T is invisible until T commits. Others can wait on it only through a
uniqueness key.
- The ledger (§2.5) shows every such key is private, serialised by a lock T already holds, ordered (K1) or a
  first-wait.
- Holding a private row therefore adds no incoming edge except through those keys, which are covered by their
  class.

**L4 (operation rows, CR-2).** Two transactions that both hold O's advisory lock cannot exist (try-locks on one key
are exclusive across session and transaction scope). So a wait on O involves at most one owner, plus:
- **(i)** single-resource transactions. These hold nothing before O and take nothing after it except O's event key
  (K4, serialised by O). A single-resource transaction has no outgoing edge while it holds O, so it cannot be on a
  cycle (L6);
- **(ii)** trigger and settlement writes keyed by location (`missing_retires_ownership`, `retires_ownership`,
  `releaseTargetsAt`). By **PO-B** these match only:
  - no committed row, because the single-owner constraint (deferred, enforced at every commit) forbids a committed
    operation owning a path that any generation records. A predicate that does not match a row's committed version
    does not wait on it (verified, §11.3);
  - or the writer's own operation (settlement of an entry whose `operation_id` the caller holds).
- **(iii)** foreign-key KS on O from inserts. These reference the inserter's own operation (adv held) or a new
  private operation, and KS conflicts only with `note`'s FOR UPDATE by the owner or by a single-resource
  transaction.

Hence an edge on O runs only between the owner and a transaction that cannot be on a cycle. **The owner may
therefore hold O before Ct** (P5, P6) without creating a cycle.

**L5 (content-scoped rows, CR-1).** Every write of a content-scoped row of X happens while the writer holds Ct(X)
FOR UPDATE, after changes P2-8a…f. Ct(X) U is exclusive, so two writers of content-scoped rows of X never hold
locks on them at the same time, and the only waits on them are for Ct(X) itself, which is ordered (L1c).
- Explicit locks on P rows (`lockForUpdate`, and `note` when it is not the owner's) are taken under Ct(X), or under
  CR-2 or CR-3.
- The cascade (SA-R2) is covered as in revision 2: every descendant it updates is held by FC.

**L6 (first-only waits).** A transaction whose only blocking request is its first (it holds nothing when it waits)
has no incoming edge while waiting, so it cannot be on a cycle. This covers:
- K3, K9 and K11;
- I5 statements;
- the Administrator check's first statement;
- catalog corrections' first lock.

**L7 (terminal references, rule TR) [3.1, revised].**
- **`app_user` and vocabulary rows.** With D-LP-9 adopted, these rows are taken only in KEY SHARE and NO KEY UPDATE
  modes. KEY SHARE conflicts with neither. NO KEY UPDATE conflicts with NO KEY UPDATE: two updates of one user, or of
  one vocabulary row. Each such update is in a transaction whose other blocking acquisitions are at most:
  - the D-LP-9 guard, which is a first acquisition;
  - KEY SHARE on terminal rows.

  The holder of one such NO KEY UPDATE never waits on a non-terminal resource afterwards. These rows therefore add
  no edge to any cycle.
- **Source and Repository.**
  - **A wait on t held in KEY SHARE** comes only from a catalog correction's FOR UPDATE. That is the correction's
    first acquisition, so the wait is L6.
  - **A wait on t held in FOR UPDATE by a catalog correction W** comes from KEY SHARE requests. W's only further
    waits are:
    - the new Repository's KEY SHARE, blocked only by a Repository correction W', whose further waits are on
      `app_user` and vocabulary KEY SHARE, which never wait (above);
    - its own `app_user` and vocabulary KEY SHARE (never wait).

    The chain W → W' ends; it has no cycle, and it never reaches a non-terminal resource.
- **Without D-LP-9** the `app_user` part fails: the administrator check A holds earlier administrators' FOR UPDATE
  while waiting for a later administrator's KEY SHARE holder. §11.5 reproduces A → T1 → T2 → A.

**Theorem.** Assume:
- D-LP-1, D-LP-3, D-LP-5, D-LP-6a, D-LP-6b, D-LP-7, D-LP-8 (Option C, changes P2-8a…f, P2-8c as extended in 3.1)
  and **D-LP-9** are adopted;
- PO-A…PO-D hold (PO-E is withdrawn and replaced by D-LP-9).

Then every blocking request of every writer in §2 is ordered (L1), covered (L2), private (L3), an operation-row edge
of L4, a content-scoped edge of L5, a first-only wait (L6) or a terminal-reference wait (L7). No wait-for cycle
exists among the inventoried writers.

**Proof obligations** (each tested, §10). Each has the schedule that would refute it; the matching test runs
that schedule against the implemented protocol and must show the obligation holds.

| ID | Statement | Counterexample schedule (refutes it if it can occur) | Test |
|---|---|---|---|
| PO-A | Single-resource transactions (P9, P10, P14, P15, D3, D7, the I5 statements) take no blocking lock other than O and its event key | An Administrator retry or cancel, or a receiver, that holds O and then waits for Ct, a generation or a File held by the worker that is waiting for O | LP-10b (inventory) and LP-10g (retry against a worker in `commitRepair`) |
| PO-B | Location-keyed trigger and settlement writes match only the writer's own operation or no committed row | A committed operation row owning path p while a generation records p, and IntegrityService marking that content MISSING while the operation's worker holds the row and waits for Ct | LP-10e |
| PO-C | Location keys are claimed only after an exclusive create or an own release in the same transaction | Two transactions claiming one `(volume, path)`: T1 holds the claim and waits for T2's content; T2 waits on T1's claim | LP-10f, LP-10h |
| PO-D | Unresolved OPEN location inserts for content X happen under Ct(X); content-less ones under their operation's advisory lock | Two recorders of one path for X, one without Ct(X), each holding a row the other needs | LP-10d |
| ~~PO-E~~ | **Withdrawn [3.1].** It does not prevent the cycle: each participant references only one user (§11.5). Replaced by D-LP-9. | Executed: §11.5 | LP-14 |

**What is not proven:**
- **Declined decisions:** each leaves its scenario, which PostgreSQL detects as 40P01 (fails visibly).
- **The second review's counterexamples** are accounted for individually in the correction record's reconciliation matrix. The schedules above are the builder's; the reviewer's are cited there with their source references.
- **Writers added later:** must be inventoried before implementation.

## 6. `LockSequence` contract (direction §4)

| Operation | When | Checks before SQL | Records after SQL |
|---|---|---|---|
| `acquire(level, table, id, mode)` → `Ticket` | immediately before each explicit lock statement, one row each | against the **current** state (no stored batch): key (level, UUID) ≥ every held key, unless a re-lock (below); `mode` is the strongest the transaction will need for this row (caller-declared; mismatch later → `assertHeld` fails); the tracker has no other open ticket (one statement at a time, non-interleavable); the tracker is not FAILED | `Ticket.acquired()` → ACQUIRED(mode); `Ticket.missing()` → MISSING (no lock held); `Ticket.failed(sqlState)` → ATTEMPTED with SQLSTATE, and the tracker enters FAILED |
| `insertKey(level, table, key)` → `Ticket` | immediately before an INSERT whose uniqueness key may wait (K1) | as `acquire`, mode UPDATE | `inserted()` → INSERTED: the row is PRIVATE_NEW **and** the key stays in the held-key set at its level; `taken()` → TAKEN: no row held, but the key position is recorded as passed (later keys must be ≥ it) |
| `privateNew(table, id)` | after inserting a row **all of whose unique indexes are classified private in the ledger (§2.5)** **[3.1]** | the table is on the ledger's private list; otherwise the call is rejected and the caller must use `insertKey` for each shared key (for example `file_operation` batch/item, unresolved OPEN location, generation location) | PRIVATE_NEW; later locks or references to the row are exempt from order checks; **shared keys are never erased by it** |
| re-lock (`acquire` on a key already ACQUIRED) | as `acquire` | WP1-R3 rules kept: same level; same-or-weaker mode; no acquisition at a **higher** level or sub-level since (else rejected). A re-lock of a MISSING or TAKEN key is a normal acquisition, not a re-lock. | ACQUIRED unchanged |
| `assertHeld(level, table, id, mode)` | where code relies on a lock already held (the `commitShared` parent File, §4.7) | ACQUIRED at ≥ mode, else `IllegalStateException` | — |
| ownership context **[3.1]** | **derived from locks actually held, not set by hand:** `OperationLocks.tryLock`, `tryLockAll` and `tryLockForTransaction` register ownership with the guard when the lock is granted, and the returned `Held` (or transaction completion, for the transaction-scoped lock) deregisters it. A single-resource context is opened only by the repository methods that are single-resource by construction (`transition` and `note` outside owner context, `heartbeat`, `markStagingCleaned`). | — | used by L1p checks: an explicit O lock requires registered ownership of O or a single-resource context; a single-resource context rejects any other `acquire` (PO-A). Tests cross-check registered ownership against `pg_locks` (`locktype = 'advisory'`, granted) for the key. |
| reset | on commit **and** rollback (`TransactionSynchronization.afterCompletion`) | — | clears all state, including FAILED |

**Mode strength:** KEY SHARE < SHARE < NO KEY UPDATE < UPDATE.
- A request for a row the transaction will need at several strengths is made once, at the strongest. For example,
  `destroyHolding`'s replacement File: KS. **[3.1]** If the replacement id equals the file id, there is one plan entry
  (U), and the accepted rejection message is raised at its accepted position, after the not-found, version and
  lifecycle checks.
- **[3.1] Table and level consistency:** each repository method is bound to exactly one (level, table). `acquire`
  rejects a table that does not match the declared level.
- **Duplicate targets** in one call site are rejected; callers deduplicate first. `add` and `saveMarks` deduplicate
  by first occurrence.

**Levels mapped to repository operations:**

| Level | Repository operations |
|---|---|
| `L1_SESSION` | `CaptureSessionRepository.lock` |
| `L1_FILE` | `ArchivalFileRepository.lock`, `lockShared`, new `lockKeyShare` |
| `L1_CONTENT` | `FileContentRepository.lock`, `lockShared` |
| `L1_PRESERVATION` | `FileOperationRepository.note` (explicit O U), `UnresolvedObjectRepository.lockForUpdate` |
| `L1_WORKSHEET` | new `lockSessionRow(s, f)`, `lockRow(f)`, `addRow` (via `insertKey`) |
| `L1_RECORD` | `ArchivalRecordRepository.lock`, new `lockShared` |
| `L1_CATALOG_SOURCE`, `L1_CATALOG_REPOSITORY` | `CatalogRepository.lockSource`, `lockRepository` (TR: first acquisition only) |
| L7 | `app_user` and vocabulary rows: no explicit lock after D-LP-9. The guard rejects any explicit `app_user` lock. |

- **Implicit locks** (foreign keys, triggers, deferred keys) are **not** instrumented. They are covered by §5 L2–L5
  and L7, and tested with real blockers (LO-7, LP-10e).
- **Replaced assertion.** Revision 2's "operation-owned tables are never locked explicitly" is withdrawn. Explicit
  locks on `file_operation` and `unresolved_object` exist (above) and are checked by the context rules.

## 7. Behaviour that would change (each needs an explicit owner decision)

| ID | Accepted component | Change | Behaviour seen by users |
|---|---|---|---|
| D-LP-3 | Phase 2 `FileService.correct` (restricting) | Stabilised closure, three attempts (§4.6) | A new visible Conflict after three abandoned attempts |
| D-LP-6a | Phase 2 `destroyHolding` | F↑ {file, replacement} before content | Unchanged |
| D-LP-6b | Phase 2 `commitShared` | Parent F before content; parent re-lock replaced by assertion | Unchanged |
| D-LP-8 / P2-8a | Phase 2 `QuarantineMoves.settle` | Ct U first | Settlement waits for concurrent work on the same content |
| P2-8b | Phase 2 `quarantineSuperseded` | Ct U first | as above |
| P2-8c | Phase 2 `DestructionProcessor` (completion, stopped-repair copy, `markVacated`, **and `newRecord`/`refuse` [3.1]**) | Ct U first | as above |
| P2-8d | Phase 2 `UnresolvedObjects` release, attest, close | Ct U first | An Administrator decision waits briefly for concurrent work on the content |
| P2-8e | Phase 2 `IntegrityService` (MISSING, damaged, reappeared, reverify) | Ct U first, before the finding insert | A scan waits briefly for concurrent work on the content |
| P2-8f | Phase 2 `unverifiedOwnCopy`, `applyCancel` (REPAIR) | Ct U first | as above |
| D-LP-5 | Phase 3 `linkFile` | F Sh before R | A link waits for a concurrent restriction of the File |
| D-LP-7 | Phase 3 grouping, `add` (F Sh, ordered inserts, compaction), `saveMarks`, `removeFile` | §4.2–§4.4 | `add` waits for a concurrent restriction or destruction of a candidate File; counts unchanged |
| **D-LP-9 [3.1]** | Accepted user administration (`UserService.update`, `AppUserRepository.lockEnabledAdministrators`) | Replace the multi-row `FOR UPDATE` on enabled administrators with a transaction-scoped **blocking** advisory lock on one fixed key (`pg_advisory_xact_lock`) as the **first** statement of every transaction that changes a user's role or enabled flag. Then read the enabled administrators without row locks and update the target row (NO KEY UPDATE). | Unchanged outcomes: the at-least-one-enabled-Administrator rule is kept, because every role or enable change serialises on the key. Concurrent role changes queue on the key instead of on administrator rows. Researchers' and workers' history inserts no longer wait behind a role change. |

## 8. Decisions requested (updated)

| ID | Decision | Recommendation |
|---|---|---|
| D-LP-1 | The order and rules of §3 (adds L1p, CR-1…CR-4 and TR) | Approve |
| D-LP-2 | `add` with sorted inserts and compaction, F FOR SHARE (§4.3) | Approve |
| D-LP-3 | Closure with the boundaries of §4.6 | Approve |
| D-LP-4 | The `LockSequence` contract of §6; closes GAS-P4-002 | Approve |
| D-LP-5 | `linkFile` File before Record | Approve |
| D-LP-6a, 6b | File before content in `destroyHolding`, `commitShared` | Approve |
| D-LP-7 | Grouping, `add`, `saveMarks`, `removeFile` sequences | Approve |
| **D-LP-8** (now active) | **Option C** and Phase 2 changes P2-8a…f (P2-8c extended in 3.1) | Approve C. Decline A (too many changes, trigger ordering). Decline B (weakest argument). |
| **D-LP-9 [3.1]** | The administrator check: options **(a)** first-statement blocking advisory key (recommended); **(b)** `FOR UPDATE NOWAIT` with a visible "try again" conflict (no queueing, but user-visible failures under contention); **(c)** keep, and accept the reproduced 40P01 risk (requires three concurrent actors, one of them changing a role) | Approve (a). It is a first acquisition, so it is not a late advisory wait; no existing wait is inverted. |

## 9. Phase 4 writers and LO-1…LO-7

As revision 2 §9:
- W1–W5 and W11 take R Sh as their only L1 lock, then L2…L5 ascending;
- W10 takes no L1 lock;
- W12 takes R U.

Their references to Source, vocabularies and users are terminal (TR, with D-LP-9). LO-3 is extended to reject any explicit `app_user` lock.

## 10. Concurrency-test contracts (amended as directed; not implemented)

**Common rules for every test:**
- **Matrix:** PostgreSQL 16 and 18 × Linux and Windows.
- **Real transactions** on separate connections. Each session's `application_name` and backend pid are captured at
  session open (the `Lp6LockBehaviourIT` pattern), and the txid at its first write.
- **Controller-driven schedules:** the controller advances only after observing the named session waiting
  (`wait_event_type = 'Lock'`), with `pg_blocking_pids` naming the expected pid.
- **Outcome classes, distinguished:** 40P01, 55P03 (`lock_timeout = '20s'` per session), success, or an expected
  application exception.
- **Final-state assertions** on rows, versions, counts and history.
- **Seams** (`CaptureFaults`, `FileFaults`, `PreservationFaults`) are test-only no-ops in production.
- **Test classes** (direction §8), one per test:
  - **BASE**: baseline deadlock characterisation, on the accepted code; expected 40P01.
  - **COMPAT**: compatibility reference, against a serial run of the accepted behaviour.
  - **DB**: database behaviour characterisation; plain SQL; no project logic.
  - **ACC**: corrected-path acceptance, on the implemented protocol.
- **Acceptance rules for every ACC test:**
  - every participant's outcome is asserted (success, or the specific expected application exception);
  - every transaction is completed explicitly (commit or rollback) before assertions;
  - zero unexpected 40P01 and zero unexpected 55P03;
  - full final-state and history verification for every row touched.
- **Deleted:** the universal requirement that every test first reproduce a baseline deadlock. A baseline
  reproduction is required only where a baseline cycle exists: LP-1b, LP-2b, LP-5b, LP-9b and LP-10b-baseline. All
  other tests assert corrected behaviour only.

| ID | Class | Contract |
|---|---|---|
| **LP-1a** (baseline, Scenario A) | BASE | Sessions S1 {A}, S2 {B}, accepted code. T1 locks S1 and row A; T2 locks S2 and row B (the seam after the first row lock). Then T1 requests B and T2 requests A, interleaving enforced by the controller (T1 observed waiting on T2 before T2's request). Expected 40P01, one victim. |
| **LP-1b** (corrected, foreign rows) | ACC | Same data. `saveMarks(S1, [A, B])` and `saveMarks(S2, [B, A])` under the revised code: each answers "not found" for the foreign row at its accepted precedence position. `pg_blocking_pids` never shows T1 and T2 blocking each other on worksheet rows. Rows unchanged. A third case has an earlier validation error preceding the foreign row in request order: that error is reported (precedence). |
| **LP-2a** (baseline, Scenario B) | BASE | Accepted code; `add(S1, [F2, F1])` and `add(S2, [F1, F2])`, with the seam after each first insert; the controller observes the cross-wait; 40P01. |
| **LP-2b** (corrected) | ACC | Revised code; T1 paused after inserting F1 (lowest UUID); T2 started and **observed waiting on F1 with blocker = T1**; then: **(i)** T1 commits → T2 reports F1 and F2 taken (if T1 took both) or partial overlap (T1 {F1}, T2 {F1, F3}: T2 adds F3); **(ii)** variant: T1 **rolls back** → T2's insert succeeds and T2 adds F1. No 40P01; per-session sequences consecutive in request order. |
| **LP-3** (compaction) | ACC | Session rows 1…5. Request [Fc, Fa, Fd, Fb] (UUID order Fa < Fb < Fc < Fd), plus a hidden id, a destroyed id and a duplicate. **The competing claim is controlled:** T2 holds an uncommitted insert of Fa in S2; T1 (revised `add`) is observed waiting on Fa (blocker = T2); T2 commits. **Assert:** provisional 6…9; compaction UPDATEs each affect exactly 1 row (statement counts captured); final Fc 6, Fd 7, Fb 8; `row_version` 1; `added_at` and `added_by` unchanged; counts per entry point (`addFiles`: added 3, taken 1, not available 2; `addBatch` excludes hidden; `addByFriendlyIds` adds unknown); history rows in request order; no 23505. Bound case: `base` near `Integer.MAX_VALUE` → rule violation, nothing inserted. |
| **LP-4a** | COMPAT | Grouping chunk with plan order the reverse of UUID order, with continuations: same Records, links, holds, conflicts and history as a serial reference run; the recorded sequence is S, F↑, K↑, R. |
| **LP-4b** | ACC | Classification source applied by chunk 1; a forced restart; chunk 2 (extension) neither locks nor checks the source row or File; outcome equals the accepted code. |
| **LP-4c** (replay boundary) | ACC | Replay of a completed chunk (idempotent run): no new locks beyond S; no new history. Replay after the source row was changed by another session between chunks: the accepted Conflict precedence. |
| **LP-5a** | ACC | Grouping holds derivative D Sh (D < P) while FC restricts parent P: FC waits (blocker = G); after G commits, FC completes; D RESTRICTED with inheritance history. |
| **LP-5b** | ACC | The same with D > P, and with a **two-level** chain P → D1 → D2 in the chunk (D2 < P < D1): FC's ordered closure lock waits on the first held node; no 40P01. Baseline variant reproduces Scenario C (40P01) on the accepted code. |
| **LP-6** | DB | Executed harness (§11.1). |
| **LP-7** | ACC | Phase 4 writer racing grouping on one Record: the Phase 4 writer requests only R Sh at L1; no cycle. |
| **LP-8a** | ACC | Full integration suite with the guard: no violation. |
| **LP-8b** (negative guard tests) | ACC | Each must throw **before** SQL: descending acquisition; acquisition while a ticket is open; acquisition after FAILED without reset; upgrade (Sh → U) at any level; re-lock after a higher sub-level (WP1-R3); duplicate target in one call; insert key below a held key; L1p lock without owner or single-resource context; single-resource context with a second acquisition; `assertHeld` on a missing or weaker lock. Reset after rollback clears FAILED. |
| **LP-9a** (corrected SA-R1) | ACC | G holds D Sh; FC holds the closure of P (P, D in order) and is **observed waiting on D (blocker = G)**; L requests P Sh (revised `linkFile`) and is **observed waiting with blocker = FC** before taking R; G takes R and commits; FC completes; L completes. No 40P01. |
| **LP-9b** (baseline) | BASE | `Lp6LockBehaviourIT.saR1_…` (executed, §11.2). |
| **LP-10a** (Scenario D, corrected) | ACC | FD on File X (content Ct) and OS committing a derivative of X sharing Ct. **No symmetric barrier:** the controller pauses FD after its F lock only, starts OS, observes OS **waiting on X with blocker = FD**, then releases FD. Second schedule: OS paused after its parent lock; FD observed waiting on X (blocker = OS). No 40P01; final states as in serial order. |
| **LP-10a-baseline** | BASE | Accepted code, FD paused after F, OS paused after Ct, then both released: 40P01. |
| **LP-10b** (replaces the I-ADV test; PO-A) | ACC | Statement-level inventory under the guard's context markers. Every `file_operation` write is in owner context or single-resource context. Every single-resource transaction issues no other blocking statement. Every `note` FOR UPDATE outside owner context is single-resource. Runtime assertion in tests plus an ArchUnit rule on call sites. |
| **LP-10c** (K7) | ACC | Concurrent commitRepair and IntegrityService MISSING on content X: the second waits for Ct(X) (blocker = first); final generation states consistent; exactly one CURRENT. |
| **LP-10d** (K10, PO-D) | ACC | Concurrent `quarantineSuperseded` and DestructionProcessor `claimBecomesUnresolved` recording at one path of X: one waits for Ct(X); exactly one OPEN record; no 23505. |
| **LP-10e** (PO-B) | ACC | IntegrityService marks X MISSING while a worker holds an uncommitted `markTargetCreated` on an unrelated path: no wait (blocker set empty). A seeded committed-state check that no operation owns a path that a generation records. |
| **LP-10f** (PO-C) | ACC | Two operations planned to the same path: one exclusive create wins; the other is COLLISION; one `physical_location_owner` row. |
| **LP-11** (closure growth) | ACC | FC discovers {P, D}; the seam pauses before acquisition; a publisher **commits** D2 under D (an independent, committed change); FC re-discovers → attempt abandoned and **rolled back** (no history rows, no version change from attempt 1); attempt 2 locks {P, D, D2}; D2 RESTRICTED with history. Deep chain (depth 6) and diamond variants. All-state variant: a DESTROYED intermediate is locked and its descendants handled. |
| **LP-12** (concurrent publication) | ACC | FC holds the full closure; a publisher of D3 under D is **observed waiting (blocker = FC)**, with D3 still uncommitted; after FC commits, the publisher's transaction (independent of FC) commits, its trigger restricts D3, and D3's **creation history** records the inherited restriction **[3.1]**. **Exhaustion variant [3.1]:** before each of the three re-discoveries the controller makes a publisher **commit and acknowledge** one new edge; result: one visible Conflict; **the restriction's own writes are absent** (root version and history unchanged by FC); the publishers' independently committed Files and edges remain. Hidden root: not found. DESTROYED root: the accepted rule message. |
| **LP-11b [3.1]** | ACC | Overlapping roots: FC1 restricts P1 and FC2 restricts P2, sharing descendant D. One waits for the other (blocker observed); both complete; history records D's change once (by whichever changed it). |
| **LP-12b [3.1]** | ACC | Publisher rollback: a publisher waiting on FC's parent rolls back; FC completes in one attempt; no edge, no history for the rolled-back child. |

**Additional tests** (ACC):
- **LP-10g** (PO-A): an Administrator retry of operation O while O's worker is inside `commitRepair` (holding O,
  waiting for Ct(X) held by IntegrityService). The retry waits only for the worker (blocker = worker pid) and
  completes after it. No 40P01.
- **LP-13** (rule TR): a Source correction holding the Source and requesting its new Repository, while a Repository
  correction holds it and a grouping transaction holds a new Record's KEY SHARE on the Source. No 40P01. All three
  complete with final versions and history.

**Further amendments [3.1]** (from the second review §9):
- **LP-1b:** the corrected foreign-row test uses an **independent blocker**: T3 holds the foreign row B FOR UPDATE
  throughout. `saveMarks(S1, [A, B])` must return not found for B within a bound well below `lock_timeout` while T3
  still holds B. That shows it never waited on B. Sampling `pg_blocking_pids` alone is not relied on.
- **LP-2c (ACC):** `removeFile` of F from S2 (uncommitted DELETE) while `add(S1, [F])` runs. The add waits on F's
  key (blocker = remover). After the commit it adds F; after a rollback it reports F taken.
- **LP-3:** the controller commits the competing claim of Fa **before** T's insert attempt on Fa (acknowledged), and
  in a second run holds it uncommitted with T observed waiting. Every pre-existing row's sequence, version and
  attribution is asserted unchanged. Wording: each compaction target is "a gap or a just-vacated slot".
- **LP-3b (ACC):** restriction races around the SHARE grant: before the grant (counted hidden); after the grant (the
  restriction waits for the add); through another root's closure (the add waits for that FC).
- **LP-4d (COMPAT and ACC):** existing-Record extension; classification source in a later chunk; boundary fault and
  replay; assertion that no source lock is taken on extension paths; per-edit validation precedence.
- **LP-5:** all lifecycle and restriction states of the intermediate node; depths 2 and 3; both UUID orders per run
  set.
- **LP-7:** run now only as a **synthetic** R SHARE holder against grouping; it does not certify W1–W11, which are
  tested when implemented.
- **LP-8b additions:** an acquisition after a TAKEN key below it; `privateNew` on a table with shared keys; a
  mixed-mode plan (U plus KS) on one id; a lower-level same-row re-lock after a higher level (WP1-R3 still
  rejects); tracker state after rollback and retry.
- **LP-9a:** FC is observed waiting **in its explicit closure acquisition** (`SELECT … FOR UPDATE` on D), not in the
  trigger. The outcomes and history of G, FC and L are all asserted.
- **LP-9b (BASE) limits:** the existing harness asserts one 40P01 victim. Extend it to collect every future, roll
  back every survivor explicitly, and log txids. It is not reused as an ACC test.
- **LP-10b:** the static and runtime checks follow the registered-ownership rule of §6, including trigger-driven and
  delegated writes (the call graph covers `QuarantineMoves`, `OperationCopies` and `UnresolvedObjects` helpers),
  cross-checked against `pg_locks`.
- **LP-10h (ACC):** two repairs of one content plan one generation; one CREATE_NEW collision, one commit, no 23505;
  a retry plans `max + 1`.
- **LP-10i (ACC):** final staging cleanup (P14b) racing the worker that still owns the operation: the cleanup waits
  (blocker = worker) and completes.
- **LP-10j (ACC):** concurrent repairs of one content committing: the second waits on Ct(X) and then finds the state
  changed (accepted rollback outcome).
- **LP-10k (ACC):** destruction completion against settlement, attestation and IntegrityService on one content: all
  serialise on Ct(X); no 40P01; final generation, unresolved and quarantine states consistent.
- **LP-14 (BASE, then ACC after D-LP-9):** the §11.5 schedule using the application services (an `add` or grouping
  transaction writing history, then waiting; a role change by a third administrator). BASE expects 40P01; ACC
  expects the role change to wait on the D-LP-9 key and no 40P01.
- **Corrected-path rules, added:** disable any automatic deadlock retry in the code under test; bound the controller
  with its own timeout independent of `deadlock_timeout` and `lock_timeout`; collect every future.

**Database-behaviour characterisation to add to CI** (DB): §11.3 predicate waits and §11.5, on all four jobs.

## 11. Bounded verification executed (non-production)

### 11.1 LP-6

Unchanged from revision 2. Harness `Lp6LockBehaviourIT`, CI run `43831470275` at `9eb66ea`: a compatible KEY SHARE
(foreign-key or explicit) is not queued behind a waiting FOR UPDATE, on PostgreSQL 16.16 and 18.7, Linux and Windows.
It does not dismiss SA-R1.

### 11.2 SA-R1 cycle reproduction

Unchanged from revision 2: G → L → C → G gives 40P01, with blockers confirmed by pid, on all four CI jobs (run
`43831470275`).

### 11.3 Predicate waits (new; basis of PO-B)

| | |
|---|---|
| Where | Builder's local PostgreSQL 16, scratch database and throwaway table, plain psql. Not part of CI; no project code. |
| Schedule | A holds an uncommitted UPDATE of row 1 (`owned` false → true). B runs `UPDATE … WHERE path = 'p' AND owned` (the committed version does not match). C runs `UPDATE … WHERE path = 'p'` (the committed version matches). |
| Result | B: no wait (54 ms). C: waits, ended by `lock_timeout` (55P03, "while updating tuple (0,1)"). |
| Use | A location-keyed trigger UPDATE waits only on rows whose committed version matches its predicate. PO-B still needs LP-10e on all four CI jobs. |

### 11.4 Schema facts used

Taken from a scratch database migrated with V0001…V0025 (local PostgreSQL 16):
- every unique and partial-unique index (§2.5);
- every foreign key, including the deferred `file_operation.file_id`;
- every non-internal trigger and the bodies of the cross-row trigger functions (§2.2).

### 11.5 Administrator-check cycle (new [3.1]; refutes revision 3's TR (c), (d) and PO-E)

| | |
|---|---|
| Where | Builder's local PostgreSQL 16, scratch database migrated with V0001…V0025, real `app_user`, `change_history` and the exact `lockEnabledAdministrators` SQL. One throwaway table `res` stands in for any capture-session, worksheet or File row. Not part of CI; no project code. |
| Seed | two enabled administrators a1 < a2 |
| Schedule | T1 (txid 5982, pid 316): inserts history with actor a2 (fk KEY SHARE on a2). T2 (5983, pid 317): `res` FOR UPDATE. A (5984, pid 315): the administrator check; locks a1, then waits for a2 (blockers = {316}). T1: `res` FOR UPDATE, waits (blockers = {317}). T2: inserts history with actor a1, waits (blockers = {315}). |
| Result | T1: `ERROR 40P01 deadlock detected`, "Process 316 waits … blocked by process 317. Process 317 waits … blocked by process 315. Process 315 waits … blocked by process 316." T2 and A then proceeded. |
| Meaning | Any transaction that writes history and later takes a blocking lock can play T1 or T2. One example is accepted `add`, which writes history after each insert and then waits on the next insert. This is a defect of the accepted code, reachable with three concurrent actors, one of them changing a role. It is addressed by D-LP-9. |

## 12. Residual risks and open items

- **R3-OPEN-1 (closed by reconciliation [3.1]).** Every finding of the second review is accounted for individually
  in the correction record's reconciliation matrix. Closure means accounted for, not resolved. The findings that
  remain open or partial are listed there and below.
- **Open after reconciliation [3.1]:** PO-A…PO-D are unproven until their tests run on the implementation;
  D-LP-1…D-LP-9 await owner decision; the corrected-path tests are specifications only.
- **R3-OPEN-2 (closed).** The Revision 3 direction was first received truncated in §8. The complete text was
  received afterwards; its test classes, acceptance rules and deliverables are now addressed (§0.1, §0.2, §5, §10).
- **PO-A…PO-D** are code or data invariants verified by tests, not by database constraints.
- **Pool and retry waits:** these are application waits, not database waits, and are bounded by timeouts:
  - Hikari connection waits while a worker holds a session advisory lock on another connection;
  - `destroyHolding`'s 2 s bounded loop.

  They cannot form a database deadlock, but they can delay work until a timeout.
- **Writers added after this inventory** must be inventoried before implementation.
- **The FC bound (three attempts)** may make restriction fail visibly under sustained publication.
- **Declined decisions** leave their scenario's 40P01, detected and failing visibly.

## 13. Proposed wording

### 13.1 Clarifications §5.5.3 (replaces the L1 row and rules 1 and 3)

> | L1 | **L1a** capture session → **L1b** Files (ascending) → **L1c** content (ascending) → **L1p** content-scoped preservation rows (only while holding their content row FOR UPDATE) → **L1d** worksheet keys (ascending; locks and uniqueness inserts) → **L1e** Archival Record → **L1f** Source, then Repository (catalog corrections only; all other access is terminal KEY SHARE). Operation rows follow CR-2 (owner or single-resource transaction); content-less preservation rows follow CR-3; location keys follow CR-4. | per WP1-R1 revision 3 §3 | Phase 2, Phase 3; Phase 4 takes L1e only |
>
> 1. **Monotonic acquisition.** Every blocking request has a key (level, sub-level, UUID) ≥ every key held. Worksheet rows are locked only through a predicate on the transaction's own session.
>
> 3. **Parents before implicit locks.** Before any statement that takes an implicit lock, the transaction holds that resource at an equal or stronger mode, or the resource is private, or it is a terminal reference (rule TR). There is no other exception.

### 13.2 GAS-P4-002: proposed status text

> **Status:** superseded by WP1-R1 revision 3. The premise of this record holds within one session only; Scenarios A–D are cross-session or cross-subsystem cycles. The replacement protocol (L1a…L1f with L1p, CR-1…CR-4, TR) and the guard contract are proposed in PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL revision 3, decisions D-LP-1…D-LP-8. Not implemented.
