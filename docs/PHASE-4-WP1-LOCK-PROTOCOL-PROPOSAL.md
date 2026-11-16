# Phase 4 WP1: L1 locking-protocol proposal (WP1-R1, GAS-P4-002), revision 3.2

**Status: REVISED PROPOSAL for owner review and independent Codex assessment. Not implemented.**
- No production locking code, SQL, trigger or migration in this document has been written.
- The test designs in §10 are specifications; none of them is claimed to pass.
- Executed evidence is limited to the bounded, non-production checks in §11.
- **Revision 3.2** responds to the third independent architectural assessment
  (`Phase-4-WP1-Third-Architectural-Assessment.md`, reviewed head `2227b10`) and the Revision 3.2 direction. Its
  finding-by-finding response matrix is in the correction record. Changes are marked **[3.2]**.
- **Revision 3.1** reconciled revision 3 (`569753f`) with the second Stage A review, finding by finding. The
  reconciliation matrix is in `PHASE-4-WP1-AUDIT-CORRECTIONS.md`. Changes from revision 3 are marked **[3.1]**.
- **This is not a readiness claim.** PO-1 is contradicted by source (§2.3), PO-2 is replaced by the key ledger with
  its own open obligations (§2.5), and the theorem in §5 is conditional on decisions D-LP-1…D-LP-9, lemmas A1–A4,
  PO-B…PO-D and the invariants INV-U and INV-P.
- **Evidence classes:**
  - **independently verified:** CI run `43831470275`, checked by the second review;
  - **builder-executed:** local PostgreSQL 16 (§11.3, §11.5–§11.7);
  - **source analysis** (§2, §4.9, §5);
  - **proposed design** (§3–§6, §4.10);
  - **future test** (§10).

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
| §2 D-LP-8 as an active decision | Options A, B and C evaluated. **Recommendation: Option C**, content-row coordination (CR-1), with operation rows classified by writer kind (CR-2), content-less rows under their operation (CR-3) and location-key invariants (CR-4). Existing operation-before-content paths are analysed, not assumed away. No blocking advisory acquisition is added by Option C (D-LP-9 adds one first-acquisition key, §4.10). **[3.2]** Six decision groups changing 15 transaction windows (§4.9). | §3.2, §4.9, §7 |
| §3 PO-2 unique-key ledger | Every primary, unique, partial-unique and logical conflict key: contenders, holder, first conflicting wait, protection, classification, tests and assumptions. No constraint is weakened. | §2.5 |
| §4 Guard contract | Batches removed. Every blocking acquisition is checked immediately before its SQL; inserted rows keep their public keys; modes, duplicates and strongest-mode handling are explicit; re-lock protection of WP1-R3 is kept; the `commitShared` parent re-lock is replaced by an assertion; rollback resets state. The false "never explicitly locked" assertion is replaced. | §6 |
| §5 Catalog and reference locks | Source → Repository dependency, terminal reference waits from Record, Citation, worksheet overrides, session correction, history, vocabularies and users. A narrowly scoped terminal-reference rule (TR) is proved; no lock is claimed to be held that is acquired implicitly. | §4.8, §5 (L7) |
| §6 Closure boundaries | READ COMMITTED; a new transaction per attempt; exactly three attempts; the original `expectedVersion` and actor; missing and hidden-root outcomes without leakage; authorization and lifecycle checks before any retry-exhaustion message; full rollback; history and inheritance; deep, all-state and growing graphs. | §4.6, LP-11, LP-12 |
| §7 Worksheet entry points | `addFiles`, `addBatch` and `addByFriendlyIds` specified separately; authoritative File state read under FOR SHARE after lock waits; restriction and destruction races; deduplication and history order; one-row compaction updates; integer bounds; public keys kept in the guard. `saveMarks` duplicate edits and precedence preserved independently. | §4.3, §4.4 |
| §8 Test contracts | **[3.2]** LP-9a precondition and LP-9c; LP-13 rewritten; LP-14a/b; LP-10e, f, g, b, l, m, n; LP-8b additions; BASE list corrected. Earlier: LP-1…LP-12 amended as directed; LP-10g and LP-13 added. The universal baseline-deadlock requirement is deleted; every test is classed BASE, COMPAT, DB or ACC, and ACC tests follow the acceptance rules. | §10 |
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

**[3.2]** The operative per-transaction-window inventory of every changed or excepted writer (entry, order,
implicit effects, revalidation, exit) is §4.9. The rows below keep the writer-level view.

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
| Q1 | `QuarantineMoves.move` → `intend` (one statement) | the caller's adv | Q ins (uq (v, quarantine_path)) → fk O KS (own) | Q location key | — | **[3.2] Exception E3 / lemma A4** (§4.9 Wa1): one autocommit statement; destination key embeds the operation id; callers hold that operation's advisory; fk KS on O and volume only (no fk on `content_id`) |
| Q2 | `QuarantineMoves.settle` [tx] | the caller's adv (`reconcileAll`: tryLock; worker; cancel; DP) | Q NKU → **every O at source NKU** (`releaseTargetsAt`) → U NKU (resolve) → G NKU (`retireGenerationAt`) | Q(X), O by location, U(X), G(X) | G retires ownership by location | **Change P2-8a:** Ct(X) U first when content is not null; `releaseTargetsAt` matches only the entry's own operation (PO-B) |
| U1 | `UnresolvedObjects.release` [tx] | adv(u.operation) | U U (`lockForUpdate`) → U NKU → G NKU (`retireGeneration`) → O U (`note`) → event | U(X), G(X), O | G → MISSING retires ownership | **Change P2-8d:** Ct(X) U first when content is not null |
| U2 **[3.1]** | `attestUnderLock` [tx]. Reached **with** adv(u.operation) for an open, unattested record, and **without** any advisory lock when the record is already attested or closed (`attest`, lines 121–122) | adv(u.operation), or none | U U → (open and unattested only) U NKU → O U (`note`) | U, O | — | **[3.2]** Split: owned branch Wd2/Wd2′ (Ct(X) U first when persisted; CR-3 when contentless); **fast path Wd3 = exception E5**, terminal, not an ownership path |
| U3 | `quarantineAttested` | adv(first.operation) | `note` [tx]; `QuarantineMoves.move` (Q1, Q2) | | | as Q1 and Q2 |
| U4 | `closeIfVerifiedAbsent` [tx] | adv(u.operation), or held by DP | U U → U NKU → G NKU | U(X), G(X) | G retires ownership | **Change P2-8d** |
| I1 | `IntegrityService.checkContent` MISSING [tx] | none | finding ins (**fk Ct KS**) → Ct NKU (**upgrade**) → (trg) G NKU → (trg) O-at-location NKU | Ct, G(X), other O | vacates; missing-retires-ownership | **Change P2-8e:** Ct(X) U first (final strength) |
| I2 | `damaged` [tx] | none | finding ins (fk Ct KS) → Ct NKU → G NKU (`recordDamageEvidence`) | Ct, G(X) | — | **Change P2-8e** |
| I3 | `reappeared` [tx] | none | Ct NKU → (trg) G NKU → findings NKU | Ct, G(X), findings(X) | vacates | **Change P2-8e** |
| I4 | `reverify` [tx] | none | Ct NKU → findings NKU | Ct, findings | vacates when leaving MISSING | **Change P2-8e** |
| I5 | `record`, `insertFindingIfNew`, `observeExisting`, scan rows (one statement each) | none | finding ins (fk Ct KS); observation ins (uq (scan, finding)); scan NKU | ledger K12, K13 | — | **[3.2] Exception E2 / lemma A3** (§4.9 We5): standalone autocommit statements; content-scoped, **not** operation-only; finding insert fk KS on Ct, scan, volume, operation; observation key (scan, finding) |
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
6. **CR-1, content coordination [3.2, scoped].** Every **multi-statement transaction window** that writes a
   content-scoped preservation row of **persisted** content X holds Ct(X) FOR UPDATE before its first such write.
   - **The rows:** X's state, X's generation rows, unresolved rows with `content_id` X, the quarantine settlement of
     an entry with `content_id` X, and findings and observations of X inside a state-changing window.
   - **Where:** the lock is acquired at L1c, after any F locks.
   - **The windows:** §4.9 lists every window.
   - **Exceptions, each proved in §5:**
     - **E1:** a single autocommit statement whose first lock is its own NKU update of X (RecoveryService `setState`;
       standalone `markVerified`);
     - **E2:** standalone autocommit finding and observation statements (lemma A3);
     - **E3:** the quarantine intent insert (lemma A4);
     - **E4:** private new-content publication (`commitNewContent`, PO-C S3);
     - **E5:** the `attest` fast path, one row lock with no write (lemma A4).
7. **CR-2, operation rows [3.2].** Operation row O is written only by:
   - **(a)** its owner (adv(O), acquired by try; or DestructionProcessor's `tryLockAll`);
   - **(b)** a lemma A1 or A2 window, entered with an empty tracker and outside any transaction (guard G9);
   - **(c)** a location-keyed write under PO-B.

   O's serialised event key (K4), fk KS on O by its own inserts, and terminal KS on the actor's user row are part of
   these windows, not separate acquisitions.
8. **CR-3, content-less rows [3.2].** Unresolved and quarantine rows whose content is not persisted (`content_id`
   null, or for quarantine a planned ingest content id, since that column has no fk) are written only under
   adv(operation_id). Two exceptions:
   - the intent insert (E3), whose callers hold that advisory anyway;
   - the `attest` fast path (E5), which writes nothing and is **not** an ownership path.
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
| Phase 2 changes | Most preservation writers, plus the triggers (they would have to lock in a defined order instead of by predicate). | Few now, but every new preservation writer needs a new proof; the I1/I2 and DP paths cannot be shown terminal (they hold Ct and then write G and O). | **[3.2]** Six decision groups P2-8a…f changing **15 transaction windows** (§4.9), plus D-LP-6a and 6b; all of the form "lock Ct FOR UPDATE first". No trigger changes. No advisory waits added by Option C. |
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
| Administrator check | **[3.2]** Accepted code: `app_user` rows of enabled administrators `ORDER BY user_id FOR UPDATE`, inside the transaction; it waits on later rows while holding earlier ones (GAS-P4-003). Revision 3's "none after the first statement" is withdrawn. Proposed: D-LP-9(a), §4.10. | n/a after D-LP-9 |

There is no typed sub-order among non-catalog references: KEY SHARE requests from non-catalog writers on Source and
Repository arrive in foreign-key trigger order, which differs by table. The safety argument is rule TR (§5, L7), not
an order.

### 4.9 Modified Option C: transaction-window inventory (P2-8a…f) [3.2, replaces the 3.1 summary table]

**Evidence class:** builder source analysis at `2227b10` (proposed design for the "after" column). Every window is
listed with:
- **entry:** how the transaction begins, and what ownership the caller already holds;
- **sequence:** the resources acquired, in order;
- **implicit effects:** foreign keys, triggers and deferred work at COMMIT;
- **revalidation:** what is re-read under the locks;
- **exit:** how the window ends.

**The content lock statement** is the existing `FileContentRepository.lock`:
`SELECT … FROM gas.file_content WHERE content_id = :x FOR UPDATE`.
- Its target is the **persisted content identity** the window acts on, read from the row being changed:
  `quarantine_object.content_id`, `unresolved_object.content_id`, `file_operation.content_id` or the generation's
  `content_id`.
- If the statement returns no row, the content is not persisted, which can only be a planned INGEST content (see
  P2-8f). The guard records MISSING and the window proceeds under CR-3 (the operation's advisory lock). There is no
  branch on an operation-kind label.
- A not-yet-committed content row is invisible, so the statement never waits for it.

**Count.** The six decision groups (P2-8a…f) change **15 transaction windows** (marked **CHANGED**):
- Wa2;
- Wb1;
- Wc1, Wc4, Wc5, Wc6;
- Wd1, Wd2, Wd4;
- We1–We4;
- Wf1, Wf2.

**Eleven** windows in those groups stay unchanged under named exceptions or as wrappers (marked **UNCHANGED**):
Wa1, Wa3, Wc2, Wc3, Wd1′, Wd2′, Wd3, Wd4′, We5, We6, Wf3. Two further rows (Wc7, Wd5) only delegate to Wa1 and Wa2.

Separately, D-LP-3, D-LP-6a and D-LP-6b change **3** further Phase 2 windows: FC restriction, `destroyHolding` and
`commitShared`. **Phase 2 total: 18 changed windows under 9 decision items.**

#### P2-8a: quarantine intent, settlement, reconciliation, location-keyed release

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wa1 `QuarantineMoves.move` → `QuarantineRepository.intend` | **UNCHANGED (exception E3)** | after filesystem measurement outside any transaction; caller holds adv(entry operation) (worker: own; `performCancel`: own; `quarantineAttested`: adv(u.operation)); **one autocommit statement** | Q insert, key (volume_id, quarantine_path) `ON CONFLICT DO NOTHING` | fk KS on `file_operation` (the entry's operation) and `storage_volume`; **no fk on `content_id`**; no insert trigger | `find` after the statement (a separate read) | statement commit; durable intent committed **before** the rename |
| Wa2 `QuarantineMoves.settle` final transaction | **CHANGED** | a new transaction after the filesystem move or check; caller holds adv(entry operation) (worker, `applyCancel`, `reconcileAll` try-lock, attested), or DestructionProcessor's all-writers set, or is `quarantineSuperseded` (generation sources) | **Ct(entry.content_id) U** (MISSING if not persisted; then CR-3) → Q NKU (compare-and-set INTENDED → outcome) → O NKU for **every** operation with `target_created` at the source (`releaseTargetsAt`) → U NKU (`openAt`, `resolve`) → G NKU (`retireGenerationAt`) | `file_operation_claims_location` deletes each released operation's OPERATION owner row; `retires_ownership` on G matches no committed row (PO-B, F1); deferred `single_owner` and `generation_keeps_record` are reads; no fk column changes, so no new fk check | the Q compare-and-set decides; zero rows means it was already settled, and nothing else runs | commit |
| Wa3 `reconcileAll` | UNCHANGED (wrapper) | per entry: try adv(entry operation), skip if busy | → Wa2 | — | — | — |

#### P2-8b: superseded-generation retirement

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wb1 `quarantineSuperseded` inner transaction | **CHANGED** | worker holds adv(own repair) | **Ct(X) U** → re-read the generation under Ct: state must still be QUARANTINE_PENDING (else return) → U insert (`recordIfAbsent`, key OPEN (volume, path)) → G NKU (compare-and-set) | fk KS on Ct (held), O (own); G → MISSING fires `retires_ownership` (no committed match, F1) | generation state re-read under Ct; compare-and-set | commit; the entries' settlement then runs as Wa2 |

#### P2-8c: DestructionProcessor (all windows)

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wc1 `newRecord` (from `classify`, `reconcile`, `refuse`, `deleteAuthorized`) | **CHANGED** | holds adv(DESTROY operation) and all writers (`tryLockAll`); today an autocommit statement | new transaction: **Ct(X) U** → `openAt` re-read → U insert (key OPEN (volume, path)) | fk KS Ct (held), O (own) | re-reads the OPEN record under Ct; returns the existing record if present | commit |
| Wc2 `refuse` / `incomplete` notes | UNCHANGED (CR-2 owner) | adv held | O U (`note`) → event | event key (K4) | — | commit |
| Wc3 `stopUnfinishedRepairs` per-repair `transition` | UNCHANGED (CR-2 owner via `tryLockAll`) | holds that repair's advisory | O NKU → event | — | compare-and-set on state | commit |
| Wc4 `claimBecomesUnresolved` for a stopped repair's copy | **CHANGED** | holds that repair's advisory | **Ct(X) U** → O NKU (release own claim) → U insert | trigger deletes the OPERATION owner row; fk KS Ct (held), O | re-read the operation state under Ct | commit |
| Wc5 `markVacated` | **CHANGED** | adv held; today one autocommit statement | new transaction: **Ct(X) U** → G NKU (`vacated`) | `UPDATE OF state` triggers not fired | generation re-read | commit |
| Wc6 completion transaction | **CHANGED** | adv + all writers | **Ct(X) U first** → U NKU (resolve) → own O NKU → G NKU (each → DESTROYED) → Q NKU → Ct NKU (DESTROY_PENDING → DESTROYED, now an update of a held row) → other O NKU (release; owners held by `tryLockAll`) | `retires_ownership` (no committed match, F1); the release trigger deletes owner rows of operations whose advisory DestructionProcessor holds | content compare-and-set | commit |
| Wc7 DestructionProcessor settlements | → Wa2 | | | | | |

#### P2-8d: Administrator decisions on unresolved objects

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wd1 `release` (content persisted) | **CHANGED** | adv(u.operation) | **Ct(X) U** → U U (`lockForUpdate`) → U NKU → G NKU (`retireGeneration`) → O U (`note`, owner) → event | `retires_ownership` (F1); fk KS app_user (actor, terminal, D-LP-9) | U re-read under its lock: still OPEN, same identity | commit |
| Wd1′ `release` (contentless) | UNCHANGED (CR-3) | adv(u.operation) | U U → U NKU → O U → event | as above | as above | commit |
| Wd2 `attest` owned branch (open, unattested; content persisted) | **CHANGED** | adv(u.operation), then a filesystem examine outside any transaction | **Ct(X) U** → U U → U NKU → O U (`note`) → event | fk KS app_user | `lockForUpdate` re-read: open, unattested, identity | commit |
| Wd2′ owned branch, contentless | UNCHANGED (CR-3) | adv(u.operation) | U U → U NKU → O U | | | |
| Wd3 `attest` **fast path** (already attested or closed) | UNCHANGED (**exception E5**: terminal, not an ownership path) | **no advisory lock**; outermost transaction | U U (`lockForUpdate`) only, then return (attested) or throw (closed); **no write** | none | — | commit or rollback |
| Wd4 `closeIfVerifiedAbsent` (content persisted) | **CHANGED** | adv(u.operation), or called by DestructionProcessor holding it | **Ct(X) U** → U U → U NKU → G NKU | `retires_ownership` (F1) | open re-read | commit |
| Wd4′ contentless | UNCHANGED (CR-3) | adv held | U U → U NKU | | | |
| Wd5 `quarantineAttested` | delegated | adv(first.operation) | `note` (owner), then Wa1 and Wa2 | | | |

#### P2-8e: content-scoped findings, observations and verification state

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| We1 `checkContent` MISSING transaction | **CHANGED** | no advisory | **Ct(X) U** → finding SELECT (logical OPEN key, no constraint) → finding insert (fk KS Ct held, scan, volume, operation) → observation insert (key (scan, finding)) → Ct NKU (state) | `vacates_current_generation` (G of X, under Ct); `missing_retires_ownership` (no committed match, F1) | content state compare-and-set | commit |
| We2 `damaged` | **CHANGED** | no advisory | **Ct(X) U** → record (as We1) → Ct NKU → G NKU (damage evidence) | — | compare-and-set | commit |
| We3 `reappeared` | **CHANGED** | no advisory | **Ct(X) U** → Ct NKU (MISSING → ACTIVE) → findings NKU (resolve) | `vacates_current_generation` | compare-and-set | commit |
| We4 `reverify` final transaction | **CHANGED** | no advisory | **Ct(X) U** → Ct NKU → `markVerified` → findings NKU | as We3 | compare-and-set | commit |
| We5 standalone `record` / `observe` / `insertFindingIfNew` / `observeExisting` (NOT_PROTECTED, LINK_OR_REPARSE, UNREADABLE, UNEXPECTED_OBJECT, `observeUnresolved`, the volume-level findings, `reverify`'s NOT_PROTECTED) | UNCHANGED (**exception E2**) | **outside any transaction**: each statement autocommits | (1) SELECT; (2) finding insert; (3) observation insert, `ON CONFLICT (scan_id, finding_id) DO NOTHING` | (2) fk KS on content, scan, volume, operation; (3) fk KS on scan and finding | — | each statement commits |
| We6 standalone `markVerified` | UNCHANGED (**exception E1**) | outside any transaction | Ct NKU (`last_verified_at`) | no state trigger | — | statement commit |

#### P2-8f: repair cancellation, unverified copies, ingest, private publication

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wf1 `unverifiedOwnCopy` | **CHANGED** when `file_operation.content_id` names a persisted row; else CR-3 | adv(own) | **Ct(X) U** (or MISSING) → O NKU (release own claim) → U insert (OPEN) → O NKU | release trigger deletes own owner row; fk KS Ct, O | operation state re-read | commit |
| Wf2 `applyCancel` final transaction | **CHANGED** (same rule) | adv(own) (`performCancel`); its pending settlements are Wa2 | **Ct(X) U** (or MISSING) → (`claimBecomesUnresolved`: O NKU → U insert) or O NKU (release) → O NKU (transition) | as Wf1 | compare-and-set on state | commit |
| Wf3 `commitNewContent` | UNCHANGED (**exception E4**, private publication) | adv(own) | O NKU → O NKU (release own claim) → Ct insert (private) → G insert (claims GENERATION at its own path) → parent F U → F insert (private) → derivation | **at COMMIT:** deferred fk `file_operation.file_id` → own new File (private); `single_owner` read | — | commit |

### 4.10 D-LP-9(a): administrator coordination [3.2, specified]

**Evidence class:** proposed design. The key-space check and the counterexample are builder-executed (§11.6, §11.7).

| Item | Specification |
|---|---|
| Key | `pg_advisory_xact_lock(7340, 1)`, the **two-int4** advisory form. Existing advisory keys are all single-`bigint` (`hashtextextended(text, 7340)` in `OperationLocks`, `FileOperationRepository.tryLockForTransaction`). PostgreSQL keeps the two forms in separate key spaces: a two-int4 lock was granted while the bigint with identical bits was held (§11.6). The key is documented in `OperationLocks` as reserved: class 7340, object 1 = "user administration". |
| Mode | transaction-scoped, **blocking** (it waits); the only blocking advisory acquisition in the archive |
| Position | the **first statement** inside the transaction of `UserService.update`, before `findById` and before any other database statement |
| Entry contract | `UserService.update` asserts `!TransactionSynchronizationManager.isActualTransactionActive()` on entry, so it is always the outermost transaction and holds no earlier resource. The guard additionally rejects the acquisition if anything is recorded. |
| Isolation | READ COMMITTED (set explicitly on the template) |
| Fresh read | after the key is granted, a **subsequent statement** reads the enabled administrators (plain SELECT, no row lock); then the target row; then the version check |
| Target update | the existing `AppUserRepository.update` (NO KEY UPDATE; changes display name, role, enabled, version; no key column) |
| Precedence kept | permission → display-name and role validation (before the transaction, unchanged) → not found → version Conflict → last-administrator rule → self-disable rule → no-change return → update Conflict → history. **Unchanged.** |
| `UserService.create` (and CLI `create-admin`, which delegates to it) | **Insert-only exception, documented.** `create` inserts a new user with `enabled = true` and any role. It **only adds** to the enabled-administrator set, so it can never violate the last-administrator rule. Its waits are: the `app_user_username_ci` key (first wait; holder is another `create`, terminal); fk KS on the actor's user row (never waits after D-LP-9, since no transaction holds FOR UPDATE on users); history (private). The CLI actor has no user row (actor kind command-line), so no KS. It takes no advisory key. Bootstrapping an empty database is unchanged. |
| `resetPassword`, `changeOwnPassword` | unchanged: target NKU, then history (KS on the actor). They can wait on the key holder's target NKU, or the key holder can wait on theirs. Both orders are acyclic: neither holds anything the other requests afterwards (§11.7 run 2). |
| Last-administrator invariant | every demotion or disable serialises on the key; each one reads after the previous one committed (fresh statement), checks, and updates atomically. Concurrent `create` cannot remove an administrator. |
| Withdrawn | 3.1's option (b) "FOR UPDATE NOWAIT" as an equivalent alternative: it leaves the two-transaction cycle A → P → A (reset password of a non-administrator target while the role change locks administrators), **reproduced locally** (§11.7 run 1). Option (c) "keep" remains only as an explicit risk decision. |

## 5. Wait-for argument (revision 3.2)

**Evidence class:** builder source analysis. Each obligation's classification is stated: source-supported,
conditional, or awaiting execution. No future test is counted as evidence.

A transaction T **waits for** U when T requests a heavyweight lock, an implicit lock, or a uniqueness or tuple wait
that conflicts with something U holds.
- **Advisory locks:** every advisory acquisition is a **try** (it never enters the wait queue) **except** the D-LP-9
  key. That one is a first acquisition by contract (L6).
- **Not database waits:** sleeps, Hikari pool waits and application retries, bounded by timeouts (§12).

**L1 (ordered acquisition)**, **L2 (implicit edges)** and **L3 (private rows)**: as revision 3.1. L2(c) now reads
"terminal reference under rule TR with D-LP-9".

**L4 (operation rows).** O is written only by:
- **(a) its owner,** holding adv(O) (session try-lock on the advisory connection, the transaction-scoped try-lock,
  or DestructionProcessor's `tryLockAll`). Two owners cannot coexist.
- **(b) a lemma A1 or A2 transaction** (below).
- **(c) a location-keyed write under PO-B.**

A wait on O therefore involves at most one owner; every other holder is terminal (A1, A2) or never waits on O (PO-B).
So the owner may hold O before Ct (P5, P6).

**PO-A replaced by four lemmas.**

| Lemma | Writers | Statement | Proof | Class |
|---|---|---|---|---|
| **A1: single-operation-row, non-owner** | Administrator `retry` (`transition` with `message` only, or `note`), `cancel` (`note`), `abandonStaleReceiving`, `IngestionService.abandon` and `cleanup`, `RecoveryService.cleanStaging`, post-transaction `markStagingCleaned` calls, receiver `heartbeat` and RECEIVING → STAGED `transition` | The outermost transaction (no outer transaction joined) writes O, and then only O's event row. | **Holder:** after O it waits only on the event key (K4: `max(seq) + 1` under O's own lock, so no other event writer of O is active), on fk KS on O (held), and on the actor's user row (terminal, D-LP-9). **No trigger:** the columns written (`state`, `message`, `received_bytes`, `receiver_alive_at`, `staging_cleaned`) are not `target_created`, `target_path` or `volume_id`, so `file_operation_claims_location` and `single_owner` do not fire. **No fk column changes,** so no immediate or deferred fk check. So the holder is terminal; a terminal holder is never on a cycle. **Entry:** these methods are called outside any transaction today (source); the guard enforces it (§6 rule G9). | source-supported; awaiting LP-10b, LP-10g, LP-10i |
| **A2: RECEIVING-set update** | `renewReceivers(ids)`: `UPDATE … WHERE state = 'RECEIVING' AND operation_id = ANY(:ids)` | Locks several rows in one statement, in scan order. | **Not single-resource.** A cycle needs another transaction holding a row in `ids` while waiting for a row the statement holds. The other writers of RECEIVING rows are A1 holders (terminal) or another `renewReceivers` call. Two calls never share a row: (i) `ids` is the in-memory `receiving` set of one JVM, and an operation is added only by the request that inserted it (P13) and removed at its transition or abandonment; (ii) the renewal is one scheduled timer per JVM with fixed delay, so calls in one JVM do not overlap; (iii) an operation is never registered in two JVMs. So the sets are disjoint. | **conditional** on (i)–(iii) (source-supported for one JVM; multi-JVM by registration); awaiting LP-10l |
| **A3: standalone finding and observation statements** (exception E2, We5) | autocommit SELECT, finding insert, observation insert | Each statement is its own transaction. | **Finding insert:** a new private row with a fresh PK. Its fk KS on content waits only for a Ct FOR UPDATE or NKU holder, while it holds nothing another transaction requests (the new row is invisible; no shared unique key). So the wait is first-only (L6). Its KS on scan, volume and operation never waits (those rows are only NKU-updated, or locked by A1 holders, which are terminal). **Observation insert:** key (scan, finding) `ON CONFLICT DO NOTHING`. A contender for the same key would be another autocommit observation statement (terminal holder). Its fk KS on scan and finding never waits. **No scan-uniqueness invariant is needed** for deadlock freedom; duplicate findings remain existing behaviour (K12). **Entry:** outside any transaction (guard G9). | source-supported; awaiting LP-10m |
| **A4: terminal contentless and fast paths** | `attest` fast path (E5, Wd3); intent (E3, Wa1) | One explicit lock or one insert, with no later wait except terminal ones. | **Wd3:** U FOR UPDATE, then return or throw; holds U only; terminal. **Wa1:** the (volume, quarantine_path) key embeds the operation id, and its only contenders hold the same operation's advisory (same caller); the statement's later waits are fk KS on O (blocked only by an A1 `note` holder, terminal) and on the volume (never). | source-supported; awaiting LP-10n |

**Rule (guard G9):** a lemma A1, A2, A3 or A4 window may be **entered only with an empty tracker and no active
transaction**. Joining one of these methods into a transaction that already holds shared resources is rejected
before SQL.

**L5 (content-scoped rows, CR-1 as amended).** After P2-8a…f, every **multi-statement** write window of a
content-scoped row of persisted X holds Ct(X) U first (§4.9). The remaining writers are exceptions E1–E5:
- **E1:** single statement with first-lock NKU on X.
- **E2:** standalone findings and observations (A3).
- **E3:** intent (A4).
- **E4:** private publication.
- **E5:** terminal fast path (A4).

Each exception holds at most terminal resources while waiting, or waits only first (L6). Two non-exception writers of
X never hold content-scoped locks of X at the same time, so their only waits are on Ct(X), which is ordered (L1c).

**L6 (first-only waits).** Covers:
- K3, K9 and K11;
- E2 statements;
- the D-LP-9 key;
- catalog corrections' first lock.

**[3.2]** Revision 3.1's "the Administrator check's first statement" is withdrawn; the check is replaced by D-LP-9.

**L7 (terminal references).** As revision 3.1. Without D-LP-9, §11.5 (three transactions) and §11.7 (two
transactions, NOWAIT) reproduce cycles.

### 5.1 PO-B: location-keyed operation updates

| | |
|---|---|
| **Statement** | `missing_retires_ownership`, `generation retires_ownership` and settlement's `releaseTargetsAt` wait only on a committed qualifying operation row that is (i) owned by an operation whose advisory the writer holds, or (ii) locked by an A1 holder. Otherwise they match no committed row. |
| **Fact F1** (source-supported: V0017 lines 55–62, `claim_physical_location`) | A generation insert claims `physical_location_owner (volume, path)` as GENERATION. **No code deletes GENERATION owner rows:** the only delete is `owner_kind = 'OPERATION'` in `file_operation_claims_location`. Any claim at an owned path raises `check_violation`, and `single_owner` rejects an operation owning a path any generation records, whatever the generation's state. So **no committed operation can ever own a path that any generation records** (CURRENT, vacated, MISSING, QUARANTINED, DESTROYED or historical). |
| **Generation-keyed writes** (both triggers) | Their path is a generation's `storage_path`, so by F1 there is no committed qualifying row. A blocked UPDATE evaluates the latest **committed** version; a non-matching committed version is skipped without waiting (§11.3; PostgreSQL READ COMMITTED documentation). **Handoff:** `commitRepair` releases its own claim at p and claims GENERATION at p in one transaction. Until it commits, no retirement of that new generation can run: it is invisible, and every generation write of X is under Ct(X), which `commitRepair` holds. **Class: source-supported.** |
| **Settlement, generation source** (`quarantineSuperseded` entries) | F1. **Source-supported.** |
| **Settlement, own-target source** (UNVERIFIED_COPY, PARTIAL_COPY, cancel) | The only possible owner at s is the entry's operation (one OPERATION owner per path, by PK). Every settler holds that operation's advisory (worker, `applyCancel`, `reconcileAll` try-lock, DestructionProcessor `tryLockAll`). A concurrent writer of that row is an A1 holder (terminal), whose new version is re-checked after it commits. **Handoffs** (retarget, release) happen only under the same advisory, so not concurrently. **Source-supported.** |
| **Settlement, attested source** (ATTESTED_OBJECT, s = u.path) | Needs **INV-U:** an OPEN unresolved record at (volume, p) implies no committed OPERATION owner at p. The creators are: `claimBecomesUnresolved` (releases the claim in the same transaction); `commitRepair`, `quarantineSuperseded` (generation paths, F1); DestructionProcessor `newRecord` (the content's accounted paths; the unfinished writers at those paths are stopped and their claims converted under `tryLockAll` before recording). **Class: conditional on INV-U** (not enforced by the database). |
| **Deferred work at COMMIT** | `releaseTargetsAt` and the triggers change no fk column, so no deferred fk check is queued. The deferred `single_owner` and `unresolved_has_record` triggers are reads. |
| **Counterexample shape** | I (holding Ct(X)) reaches a qualifying O owned by W; W holds O and requests Ct(X): I → W → I. By F1 it cannot arise for generation paths; by ownership it cannot arise for own-target sources; for attested sources only if INV-U fails. |
| **Tests (future)** | LP-10e(i): generation-path trigger against an uncommitted, unrelated operation update: no wait. (ii) The real V0015 and V0017 triggers with a committed **matching** row locked by an A1 holder: the trigger waits for the A1 holder (blocker observed), then re-checks. (iii) Ownership handoff during settlement: commit and rollback. (iv) INV-U: every creator path, checked statically and with a runtime assertion. **Controller and fixture locks are excluded** from the blocker sets by pid. |

### 5.2 PO-C: generation and physical-location uniqueness

| | |
|---|---|
| **Keys** | `physical_location_owner` PK (volume, path); its partial unique index (`operation_id`) WHERE OPERATION; `file_content_generation` PK (content, generation); the one-CURRENT partial index (content) WHERE CURRENT; generation location (volume, storage_path). |
| **Claim INSERT** (`claim_physical_location`, `ON CONFLICT DO NOTHING`) | waits on an uncommitted conflicting owner row, then either returns (same owner) or raises `check_violation`. |
| **Release DELETE** (operation update of target fields) | deletes the OPERATION owner row. A claim of the same key by another transaction waits for the deleter to commit or roll back. |
| **S1: `createOwned`** (repair: Ct(X) Sh first; ingest: none) | O NKU (`markTargetCreated`) → claim INSERT is the **last** blocking statement; COMMIT queues only the read-only `single_owner`. **Terminal holder.** A contender at the same path (possible only after an external removal let a second CREATE_NEW succeed) waits for a terminal holder, then gets `check_violation` or proceeds after a rollback. **CREATE_NEW is not used as the proof**; it explains why the case is rare. |
| **S2: `commitRepair`** (content-first) | O_own NKU → **Ct(X) U** → … → O NKU (release own claim: **DELETE of own key**) → G insert (generation PK and CURRENT index: Ct(X)-serialised; claims GENERATION at the **same** key it just deleted: own transfer, no wait) → Ct NKU → findings NKU (never contended; no FOR UPDATE on findings) → history (KS terminal) → COMMIT (reads). **A contender for p_new** is: another repair of X (blocked earlier at its own Ct(X) Sh or U); an ingest (its paths contain its own fresh content UUID, so never p_new = originalPath(X, g)); or a generation of other content (paths contain that content's UUID). So the held key has no contender that also holds something S2 needs. |
| **S3: `commitNewContent`** (private publication) | O_own NKU → release own claim (DELETE own key at originalPath(c, 1)) → Ct insert (private) → G insert (claims GENERATION at the same key: own transfer) → **parent F U (may wait)** → F insert → derivation → COMMIT: deferred fk `file_operation.file_id` KS on the **own new File** (private), plus `single_owner` read. **While S3 waits for the parent File it holds key originalPath(c, 1).** c is the operation's planned content id, a fresh UUID assigned once (`planIngest`), so the only claimants of that path are the same operation's own windows, serialised by its advisory. **No contender.** |
| **Class** | conditional, source-supported given the path invariant **INV-P** (content paths embed their own content UUID; ingest content ids are fresh per operation). Awaiting LP-10f and LP-10h. |
| **Counterexample shape** | T1 holds a shared claim and waits for T2; T2 waits for T1's claim. Excluded: S1 holders are terminal; S2 and S3 keys have no foreign contender under INV-P. |

### 5.3 PO-D: unresolved OPEN uniqueness

| | |
|---|---|
| **Key** | `unresolved_object` partial unique (volume, path) WHERE OPEN; `recordIfAbsent` uses `ON CONFLICT DO NOTHING` (it can wait). |
| **Existing-content callers** (all now hold Ct(X) U first) | `commitRepair` (S2), Wb1, Wc1 (**`newRecord`**), Wc4, Wf1, Wf2 |
| **Contentless callers** | ingest unverified copy and cancel (CR-3: adv(operation)) |
| **Cross-content exclusion** | An OPEN record at p for X and one for Y ≠ X would need p to be both X-scoped and Y-scoped. **X-scoped paths:** X's generation paths (owned by X forever, F1); X's repairs' targets originalPath(X, g); X's accounted paths in DestructionProcessor. **Contentless ingest paths:** originalPath(c_fresh, 1). Under INV-P these sets are disjoint. So two recorders of one p are both X-scoped (both hold Ct(X)) or both the same operation's (same advisory): serialised, and **no uniqueness wait occurs between them**. |
| **Deferred work** | `unresolved_generation_has_record` fires on `UPDATE OF status` only and reads; no deferred fk. |
| **Class** | conditional on P2-8a…f and INV-P; source-supported. Awaiting LP-10d. |

## 6. `LockSequence` contract (revision 3.2, implementable)

**Evidence class:** proposed design. The guard enforces the **instrumented** acquisition order only. It does **not**
observe PostgreSQL's internal index, foreign-key or trigger acquisitions and cannot establish PO-B, PO-C, PO-D,
index privacy or deferred-fk safety. Those rest on §5 and their tests.

### 6.1 State

```text
Tracker (one per thread, per outermost transaction attempt)
  rowLocks:  Map<ResourceKey, Held{mode, state ∈ {ACQUIRED}}>
  keyPositions: SortedSet<KeyPosition>  // uniqueness positions: INSERTED, TAKEN, DELETED
  missing:   Set<ResourceKey>            // MISSING outcomes (no lock held)
  privateRows: Set<RowId>                // rows inserted by this transaction
  openTicket: Ticket?                    // at most one
  failed:    SqlState?                   // FAILED until reset
  context:   NORMAL | SINGLE_RESOURCE(lemma) | STANDALONE
Ownership registry (process-wide, not per transaction)
  adv: Map<AdvisoryKey, OwnerRecord{thread, connectionId, since}>
ResourceKey  = (level, table, uuid);       comparator: level, then sub-level, then unsigned UUID (PostgreSQL uuid order)
KeyPosition  = (level, index, keyTuple);   comparator: level, then index name, then the key tuple in the index's order
```

### 6.2 Operations

| # | Operation | Checks before SQL | Records after |
|---|---|---|---|
| G1 | `acquire(level, table, id, mode)` → Ticket | no open ticket; not FAILED; context allows it (G9); (level, table) matches the method binding; (level, table, id) ≥ every rowLock **and** every keyPosition at the same or a lower level; not an upgrade (a held weaker mode → reject); a re-lock only per G5 | `acquired()` → rowLocks; `missing()` → missing; `failed(state)` → FAILED |
| G2 | `insertKeys(level, table, List<KeyPosition>)` → Ticket, for **every instrumented shared unique index the INSERT touches** | as G1 for each position (same comparator); the list is sorted by index name and must be ≥ all held | `inserted()` → each position INSERTED and the row private; `taken(index)` → that position TAKEN (a passed position: later acquisitions must be ≥ it), others recorded as attempted |
| G3 | `deleteKeys(level, table, List<KeyPosition>)` | as G2 | DELETED positions stay held until transaction end (a concurrent insert of that key waits for us) |
| G4 | `privateNew(table, id)` | the table is on the ledger's private list **and** the INSERT touches no instrumented or delegated shared index; else rejected | privateRows |
| G5 | re-lock: `acquire` of an ACQUIRED key | **WP1-R3 rules kept:** same level; same-or-weaker mode; no acquisition at a higher level or sub-level since; a MISSING or TAKEN key is a normal acquisition | none |
| G6 | `assertHeld(level, table, id, mode)` | ACQUIRED at ≥ mode, else `IllegalStateException`; no SQL | — |
| G7 | `delegatedKey(trigger, index, keyTuple, proofId)` | for keys taken inside triggers (`claim_physical_location`, owner delete): **not an ordering check**. Records that the statement may wait on that key, under the named proof (PO-C S1, S2, S3); rejects if the proof's precondition is false in tracker terms (S2: Ct(X) held U; S3: own operation; S1: last blocking statement, so the ticket closes the window for further `acquire`) | delegated positions (for test reporting) |
| G8 | ownership: `OperationLocks.tryLock`, `tryLockAll` and `tryLockForTransaction` register `adv[key]` when granted; `Held.close()` (after `pg_advisory_unlock` returns true) or the transaction end (for xact locks) deregisters it | — | the registry **survives tracker resets**; a new tracker attempt sees live ownership. Ownership is bound to the **thread** that acquired it; using it from another thread requires an explicit `Held.handOff(thread)`, which is the only way to transfer it. |
| G9 | `enter(lemma)` for A1, A2, A3 and A4 windows; `standalone()` for E1 and E2 statements | **rejects unless** the tracker is empty **and** `!isActualTransactionActive()` | context set; in SINGLE_RESOURCE, any further `acquire` other than the declared O row is rejected |
| G10 | L1p checks | an explicit lock on `file_operation` requires `adv[O]` owned by this thread, or SINGLE_RESOURCE(A1) for O; an explicit `unresolved_object` lock requires Ct(X) ACQUIRED (X persisted), or `adv[u.operation]` (contentless), or SINGLE_RESOURCE(A4 fast path) | — |
| G11 | D-LP-9 key | allowed only as the first acquisition of an empty tracker, with no active outer transaction | — |
| G12 | completion | `afterCompletion` (commit **and** rollback), including rollback after an exception before a ticket closed: clears rowLocks, keyPositions, missing, privateRows, openTicket, failed, context. **Never** touches the ownership registry. | — |

**Instrumented shared keys** (G2, G3):
- `file_capture_context(file_id)`;
- `file_capture_context(capture_session_id, sequence_number)` (positions only; S serialises);
- `unresolved_object` OPEN (volume, path);
- `quarantine_object` (volume, quarantine_path);
- `file_operation` (batch_id, item_key);
- `file_content_generation` (content, generation), CURRENT, and (volume, storage_path).

**Delegated (G7):** the `physical_location_owner` keys.

**Statement exceptions and nesting:**
- A SQL exception while a ticket is open records ATTEMPTED with its SQLSTATE and sets FAILED. No further acquisition
  is allowed until G12 runs.
- **Savepoints are not used** by any windowed method. A savepoint request inside a tracked transaction is rejected.
- A nested call with propagation REQUIRED joins the outer tracker, which is why G9 rejects lemma windows when
  anything is held.

**Levels mapped to repository operations:** as revision 3.1, plus:
- `L1_PRESERVATION`: G2 for `UnresolvedObjectRepository.recordIfAbsent` and `QuarantineRepository.intend`;
  `FileOperationRepository.insert` (batch/item) at L6 first-wait;
- `L1_CONTENT`: G2 for `FileContentRepository.addGeneration`;
- L7: none (the guard rejects explicit `app_user` locks; G11 for the D-LP-9 key).

## 7. Behaviour that would change (each needs an explicit owner decision) [3.2]

Phase 0, 1, 2 and 3 contracts to preserve in every change below:
- permissions, validation and error precedence, optimistic versions, no-change returns;
- atomic attributed history, request order, counts, hidden-information handling;
- restriction inheritance, durable intent-before-move, evidence and attestation, crash recovery.

**"Outcomes unchanged" is not permission to alter any of these.**

| ID | Accepted component | Change | Windows | Behaviour seen by users |
|---|---|---|---|---|
| D-LP-3 | Phase 2 `FileService.correct` (restricting) | Stabilised closure, three attempts (§4.6) | 1 | **New visible Conflict** after three abandoned attempts |
| D-LP-6a | Phase 2 `destroyHolding` | F↑ {file U, replacement KS} before content; same id → one U entry, accepted error position | 1 | Unchanged |
| D-LP-6b | Phase 2 `commitShared` | Parent F before content; parent re-lock replaced by `assertHeld` | 1 | Unchanged |
| D-LP-8 / P2-8a | Phase 2 quarantine | Wa2 Ct U first (Wa1 intent and Wa3 unchanged) | 1 | Settlement waits for concurrent work on the same content |
| P2-8b | Phase 2 superseded-generation retirement | Wb1 Ct U first, generation re-read | 1 | as above |
| P2-8c | Phase 2 DestructionProcessor | Wc1 (`newRecord`, via classify, reconcile, refuse, deleteAuthorized), Wc4, Wc5, Wc6 | 4 | as above |
| P2-8d | Phase 2 unresolved-object decisions | Wd1, Wd2, Wd4 (persisted content); fast path and contentless branches unchanged | 3 | An Administrator decision waits briefly for concurrent work on the content |
| P2-8e | Phase 2 IntegrityService | We1–We4; standalone findings, observations and `markVerified` unchanged (E1, E2) | 4 | A scan waits briefly for concurrent work on the content |
| P2-8f | Phase 2 repair cancellation and unverified copies | Wf1, Wf2 (persisted content only); ingest under CR-3; publication E4 | 2 | as above |
| D-LP-5 | Phase 3 `linkFile` | F Sh before R | 1 | A link waits for a concurrent restriction of the File |
| D-LP-7 | Phase 3 grouping, `add`, `saveMarks`, `removeFile` | §4.2–§4.4 | 4 | `add` waits for a concurrent restriction or destruction of a candidate File; counts unchanged |
| D-LP-9 | Accepted user administration (`UserService.update`) | §4.10: first-statement advisory key (7340, 1); plain fresh administrator read; `create` and CLI `create-admin` insert-only exception | 1 | Concurrent role or enable changes queue on the key; other user writes and all history writers no longer wait behind a role change |

**Totals:**
- Phase 2: 18 windows under 9 decision items.
- Phase 3: 5 windows under D-LP-5 and D-LP-7.
- User administration: 1 window.

## 8. Decisions requested (revision 3.2)

| ID | Decision | Third assessment | Builder recommendation |
|---|---|---|---|
| D-LP-1 | Order and rules of §3 (L1p, CR-1…CR-4 with E1–E5, TR with D-LP-9) | acceptable only with amendments | Approve the 3.2 text (CR-1…CR-3 rescoped; PO-A split) |
| D-LP-2 | Ordered insertion and compaction, F FOR SHARE | architecturally acceptable | Approve |
| D-LP-3 | Closure; **the three-attempt visible Conflict is a new outcome** | architecturally acceptable; owner approves the new Conflict | Approve |
| D-LP-4 | Guard contract §6 (G1–G12) | acceptable only with amendments | Approve the 3.2 contract. It does **not** close GAS-P4-002 by specification. |
| D-LP-5 | File before Record | architecturally acceptable | Approve |
| D-LP-6a | Destruction ordering | acceptable only with amendments | Approve with the same-id rule and the PO-B narrowing |
| D-LP-6b | Shared publication ordering | architecturally acceptable | Approve |
| D-LP-7 | Phase 3 sequences | architecturally acceptable | Approve |
| D-LP-8 | Modified Option C: P2-8a…f, 15 windows (§4.9) | acceptable only with amendments | Approve modified C; decline A and B |
| D-LP-9 | Option (a) as specified in §4.10 | acceptable only with amendments; NOWAIT not equivalent | Approve (a). Option (b) NOWAIT is **withdrawn** as an equivalent (counterexample §11.7). (c) keep = explicit risk acceptance only. |

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
  - full final-state and history verification for every row touched;
  - **[3.2]** automatic deadlock retries disabled in the code under test;
  - a controller deadline independent of `deadlock_timeout` and `lock_timeout`;
  - backend pids captured before any blocked work starts;
  - barriers only at seams every named participant can actually reach;
  - fixture and controller pids excluded from blocker assertions;
  - commit-time outcomes collected (deferred checks run at COMMIT).
- **Deleted:** the universal requirement that every test first reproduce a baseline deadlock. A baseline
  reproduction is required only where a baseline cycle exists. **[3.2, corrected list]:** BASE tests are LP-1a,
  LP-2a, LP-5b's baseline variant, LP-9b, LP-10a-baseline and LP-14a. LP-1b and LP-2b are ACC. All other tests assert
  corrected behaviour only (ACC), compatibility (COMPAT) or database behaviour (DB).

| ID | Class | Contract |
|---|---|---|
| **LP-1a** (baseline, Scenario A) | BASE | Sessions S1 {A}, S2 {B}, accepted code. T1 locks S1 and row A; T2 locks S2 and row B (the seam after the first row lock). Then T1 requests B and T2 requests A, interleaving enforced by the controller (T1 observed waiting on T2 before T2's request). Expected 40P01, one victim. |
| **LP-1b** (corrected, foreign rows) | ACC | Same data. `saveMarks(S1, [A, B])` and `saveMarks(S2, [B, A])` under the revised code: each answers "not found" for the foreign row at its accepted precedence position. `pg_blocking_pids` never shows T1 and T2 blocking each other on worksheet rows. Rows unchanged. A third case has an earlier validation error preceding the foreign row in request order: that error is reported (precedence). |
| **LP-2a** (baseline, Scenario B) | BASE | Accepted code; `add(S1, [F2, F1])` and `add(S2, [F1, F2])`, with the seam after each first insert; the controller observes the cross-wait; 40P01. |
| **LP-2b** (corrected) | ACC | Revised code; T1 paused after inserting F1 (lowest UUID); T2 started and **observed waiting on F1 with blocker = T1**; then: **(i)** T1 commits → T2 reports F1 and F2 taken (if T1 took both) or partial overlap (T1 {F1}, T2 {F1, F3}: T2 adds F3); **(ii)** variant: T1 **rolls back** → T2's insert succeeds and T2 adds F1. No 40P01; per-session sequences consecutive in request order. |
| **LP-3** (compaction) | ACC | Session rows 1…5. Request [Fc, Fa, Fd, Fb] (UUID order Fa < Fb < Fc < Fd), plus a hidden id, a destroyed id and a duplicate. **The competing claim is controlled:** T2 holds an uncommitted insert of Fa in S2; T1 (revised `add`) is observed waiting on Fa (blocker = T2); T2 commits. **Assert:** successful provisional slots 6, 8, 9 (Fa's rank-2 slot 7 is never inserted) **[3.2]**; compaction UPDATEs each affect exactly 1 row (statement counts captured); final Fc 6, Fd 7, Fb 8; `row_version` 1; `added_at` and `added_by` unchanged; counts per entry point (`addFiles`: added 3, taken 1, not available 2; `addBatch` excludes hidden; `addByFriendlyIds` adds unknown); history rows in request order; no 23505. Bound case: `base` near `Integer.MAX_VALUE` → rule violation, nothing inserted. |
| **LP-4a** | COMPAT | Grouping chunk with plan order the reverse of UUID order, with continuations: same Records, links, holds, conflicts and history as a serial reference run; the recorded sequence is S, F↑, K↑, R. |
| **LP-4b** | ACC | Classification source applied by chunk 1; a forced restart; chunk 2 (extension) neither locks nor checks the source row or File; outcome equals the accepted code. |
| **LP-4c** (replay boundary) | ACC | Replay of a completed chunk (idempotent run): no new locks beyond S; no new history. Replay after the source row was changed by another session between chunks: the accepted Conflict precedence. |
| **LP-5a** | ACC | Grouping holds derivative D Sh (D < P) while FC restricts parent P: FC waits (blocker = G); after G commits, FC completes; D RESTRICTED with inheritance history. |
| **LP-5b** | ACC | The same with D > P, and with a **two-level** chain P → D1 → D2 in the chunk (D2 < P < D1): FC's ordered closure lock waits on the first held node; no 40P01. Baseline variant reproduces Scenario C (40P01) on the accepted code. |
| **LP-6** | DB | Executed harness (§11.1). |
| **LP-7** | ACC | Phase 4 writer racing grouping on one Record: the Phase 4 writer requests only R Sh at L1; no cycle. |
| **LP-8a** | ACC | Full integration suite with the guard: no violation. |
| **LP-8b** (negative guard tests) | ACC | Each must throw **before** SQL: descending acquisition; acquisition while a ticket is open; acquisition after FAILED without reset; upgrade (Sh → U) at any level; re-lock after a higher sub-level (WP1-R3); duplicate target in one call; insert key below a held key; L1p lock without owner or single-resource context; single-resource context with a second acquisition; `assertHeld` on a missing or weaker lock. Reset after rollback clears FAILED. |
| **LP-9a** (corrected SA-R1) | ACC | **Precondition [3.2]: P < D** (UUID), so FC holds P before it waits on D. G holds D Sh; FC holds the closure of P (P, D in order) and is **observed waiting on D (blocker = G)**; L requests P Sh (revised `linkFile`) and is **observed waiting with blocker = FC** before taking R; G takes R and commits; FC completes; L completes. No 40P01. |
| **LP-9b** (baseline) | BASE | `Lp6LockBehaviourIT.saR1_…` (executed, §11.2). |
| **LP-10a** (Scenario D, corrected) | ACC | FD on File X (content Ct) and OS committing a derivative of X sharing Ct. **No symmetric barrier:** the controller pauses FD after its F lock only, starts OS, observes OS **waiting on X with blocker = FD**, then releases FD. Second schedule: OS paused after its parent lock; FD observed waiting on X (blocker = OS). No 40P01; final states as in serial order. |
| **LP-10a-baseline** | BASE | Accepted code, FD paused after F, OS paused after Ct, then both released: 40P01. |
| **LP-10b** (replaces the I-ADV test; PO-A) | ACC | Statement-level inventory under the guard's context markers. Every `file_operation` write is in owner context or single-resource context. Every single-resource transaction issues no other blocking statement. Every `note` FOR UPDATE outside owner context is single-resource. Runtime assertion in tests plus an ArchUnit rule on call sites. |
| **LP-10c** (K7) | ACC | Concurrent commitRepair and IntegrityService MISSING on content X: the second waits for Ct(X) (blocker = first); final generation states consistent; exactly one CURRENT. |
| **LP-10d** (K10, PO-D) | ACC | Concurrent `quarantineSuperseded` and DestructionProcessor `claimBecomesUnresolved` recording at one path of X: one waits for Ct(X); exactly one OPEN record; no 23505. |
| **LP-9c [3.2]** | ACC | Reverse order **D < P**: FC waits at D (blocker = G) while holding nothing of P. L requests P Sh and is granted, takes R U, links and commits. G then takes R and commits; FC completes. Assert completion order L, G, FC; all outcomes and history; no 40P01. |
| **LP-10e** (PO-B) [3.2] | ACC | Real V0015 and V0017 triggers and real settlement SQL. **(i)** Generation-path trigger with an uncommitted, unrelated operation update: no wait. **(ii)** A committed **matching** operation row (own-target settlement) locked by an A1 holder (Administrator `note`): settlement waits (blocker = A1 pid), then re-checks the new version; both commit. **(iii)** Ownership handoff (release, then claim) under the owner's advisory during settlement: commit and rollback variants. **(iv)** INV-U: every unresolved-record creator path, statically and with a runtime assertion. Controller and fixture pids excluded. |
| **LP-10f** (PO-C) [3.2] | ACC | **SQL claim contention, not filesystem collision.** T1 (`createOwned`) holds an **uncommitted** claim at p (seam after the claim). The file is removed externally, and T2's CREATE_NEW succeeds. T2's claim is **observed waiting on T1's key** (blocker = T1). Variants: (i) T1 commits → T2 gets the ownership error (check_violation) and blocks visibly; (ii) T1 rolls back → T2 claims. Also the operation-owner partial key: one owner row per operation. Plus the handoff release DELETE versus claim INSERT. |
| **LP-11** (closure growth) | ACC | FC discovers {P, D}; the seam pauses before acquisition; a publisher **commits** D2 under D (an independent, committed change); FC re-discovers → attempt abandoned and **rolled back** (no history rows, no version change from attempt 1); attempt 2 locks {P, D, D2}; D2 RESTRICTED with history. Deep chain (depth 6) and diamond variants. All-state variant: a DESTROYED intermediate is locked and its descendants handled. |
| **LP-12** (concurrent publication) | ACC | FC holds the full closure; a publisher of D3 under D is **observed waiting (blocker = FC)**, with D3 still uncommitted; after FC commits, the publisher's transaction (independent of FC) commits, its trigger restricts D3, and D3's **creation history** records the inherited restriction **[3.1]**. **Exhaustion variant [3.1]:** before each of the three re-discoveries the controller makes a publisher **commit and acknowledge** one new edge; result: one visible Conflict; **the restriction's own writes are absent** (root version and history unchanged by FC); the publishers' independently committed Files and edges remain. Hidden root: not found. DESTROYED root: the accepted rule message. |
| **LP-11b [3.1]** | ACC | Overlapping roots: FC1 restricts P1 and FC2 restricts P2, sharing descendant D. One waits for the other (blocker observed); both complete; history records D's change once (by whichever changed it). |
| **LP-12b [3.1]** | ACC | Publisher rollback: a publisher waiting on FC's parent rolls back; FC completes in one attempt; no edge, no history for the rolled-back child. |

**Additional tests** (ACC):
- **LP-10g** (lemma A1) [3.2]:
  - the **controller** holds Ct(X) FOR UPDATE (standing in for IntegrityService);
  - O's worker enters `commitRepair`, holds O, and is observed waiting on Ct(X) (blocker = controller);
  - an Administrator retry of O is observed waiting on O (blocker = worker);
  - the controller releases Ct(X).

  The worker completes, then the retry runs its actual branch and event. All three participants complete; no
  symmetric barrier on blocked participants; no 40P01.
- **LP-13** (rule TR) [3.2, no incompatible holdings]:
  - **(i)** A Source correction holds Source U; then grouping requests KS on that Source and is observed waiting
    (blocker = correction); the correction commits; grouping completes.
  - **(ii)** Reverse: grouping holds KS first; the Source correction is observed waiting; grouping commits; the
    correction completes.
  - **(iii)** Terminal chain:
    - the controller holds Repository U;
    - a Source correction (changing to that Repository) holds Source U and is observed waiting on the Repository;
    - grouping is observed waiting on the Source;
    - the controller releases the Repository.

    All outcomes and history collected; no 40P01.
- **LP-10l** (lemma A2): two `renewReceivers` with disjoint sets running concurrently with receiver `heartbeat`, an
  Administrator `cancel` note and `abandonStaleReceiving` on members of the set: no 40P01. Plus a check that an
  operation is in at most one receiving set.
- **LP-10m** (lemma A3): a standalone finding insert waiting on a controller-held Ct(X): blocker observed; no other
  participant waits on it. Two observation inserts of one key: one waits for the other's autocommit.
- **LP-10n** (lemma A4): the `attest` fast path holding U while a Ct(X)-holding release waits on it; the fast path
  returns; the release completes. The intent insert against an A1 `note` holder on its operation.

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
- **LP-8b additions:**
  - an acquisition after a TAKEN key below it;
  - `privateNew` on a table with shared keys;
  - a mixed-mode plan (U plus KS) on one id;
  - a lower-level same-row re-lock after a higher level (WP1-R3 still rejects);
  - tracker state after rollback and retry;
  - **[3.2]** **ownership survives tracker reset:** a session advisory held across two transactions is still
    registered in the second;
  - **ownership is released only by real unlock;**
  - a cross-thread use without `handOff` is rejected;
  - **invalid single-resource entry:** G9 rejects entering an A1 window while holding F, Ct or K, or inside an outer
    transaction;
  - a savepoint request is rejected;
  - a SQL exception before ticket completion sets FAILED;
  - G7 rejects a delegated key whose precondition is false;
  - G11 rejects the D-LP-9 key when anything is held.
- **LP-9a:** FC is observed waiting **in its explicit closure acquisition** (`SELECT … FOR UPDATE` on D), not in the
  trigger. The outcomes and history of G, FC and L are all asserted.
- **LP-9b (BASE) limits:** the existing harness asserts one 40P01 victim. Extend it to collect every future, roll
  back every survivor explicitly, and log txids. It is not reused as an ACC test.
- **LP-10b [3.2]:** audits **SQL, triggers and foreign keys**, not only Java flags.
  - **Inventory:** for every statement reachable from an A1–A4 window, list the columns written (proving no
    target-field trigger fires) and the fk columns changed (proving no fk check is queued).
  - **Runtime check:** `pg_locks` after each A1 statement shows only the O row lock and the transaction lock.
  - **Ownership:** registered ownership is cross-checked against granted advisory locks.
- **LP-10h (ACC):** two repairs of one content plan one generation; one CREATE_NEW collision, one commit, no 23505;
  a retry plans `max + 1`. **[3.2]** This tests planning collision and generation retry only; the shared SQL key
  cases are LP-10f.
- **LP-10i (ACC):** final staging cleanup (P14b) racing the worker that still owns the operation: the cleanup waits
  (blocker = worker) and completes.
- **LP-10j (ACC):** concurrent repairs of one content committing: the second waits on Ct(X) and then finds the state
  changed (accepted rollback outcome).
- **LP-10k (ACC):** destruction completion against settlement, attestation and IntegrityService on one content: all
  serialise on Ct(X); no 40P01; final generation, unresolved and quarantine states consistent.
- **LP-14a (BASE) [3.2]:** the §11.5 three-actor schedule through the application services (an `add` or grouping
  transaction writing history and then waiting; a second history writer; a role change). Expected 40P01. Plus the
  §11.7 two-transaction NOWAIT schedule as a DB characterisation.
- **LP-14b (ACC, after D-LP-9) [3.2]:** all four CI configurations.
  - **(i)** The same three actors: the old cycle is gone; all three complete.
  - **(ii)** Key queueing: role change A1 holds the key; role change A2 is **observed waiting on the advisory lock**
    (`locktype = 'advisory'`, blocker = A1); both complete in order.
  - **(iii)** Last-administrator race: two concurrent demotions of the last two administrators; exactly one
    succeeds and the other gets the accepted rule message.
  - **(iv)** `create` and CLI `create-admin` concurrent with a demotion: no wait on the key; the invariant holds.
  - **(v)** Password reset of the role change's target, in both orders: no 40P01; versions and Conflicts as
    accepted.
  - **(vi)** History rows and attribution exact.
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

### 11.6 Advisory key spaces (new [3.2]; basis of D-LP-9's key)

| | |
|---|---|
| Class | builder-executed; local PostgreSQL 16; scratch database; no project code |
| Schedule | session 1 holds `pg_advisory_lock((7340::bigint << 32) \| 1)` (a bigint with the same bits as the pair (7340, 1)); session 2 calls `pg_try_advisory_lock(7340, 1)` |
| Result | session 2: `true`; `pg_locks` shows the two-int4 lock with `objsubid = 2`, separate from the bigint lock |
| Use | the two-int4 key (7340, 1) cannot collide with any existing single-bigint `hashtextextended` key. Not yet in CI. |

### 11.7 D-LP-9 schedules (new [3.2])

| | Run 1: NOWAIT variant | Run 2: D-LP-9(a) variant |
|---|---|---|
| Class | builder-executed; local PostgreSQL 16; real `app_user`, `change_history`; no project code | same |
| Seed | administrators a1 < a2; non-administrator target n (EDITOR) | same |
| P (password reset of n) | `UPDATE app_user … WHERE user_id = n` (holds n NKU), txid 5990 | same, txid 5992 |
| A (role change) | `SELECT … WHERE role = 'ADMINISTRATOR' AND enabled ORDER BY user_id FOR UPDATE NOWAIT` (**granted**, 2 rows) | `SELECT pg_advisory_xact_lock(7340, 1)` (granted), then a plain administrator read (2) |
| A updates n | waits on P | waits on P (blocker = 411, P's pid) |
| P writes history as a1 | waits on A | **no wait** (a1 is not row-locked) |
| Outcome | **`40P01 deadlock detected`**: "Process 381 waits … blocked by process 380. Process 380 waits … blocked by process 381." | P commits; A's update proceeds; **no deadlock** |
| Use | refutes NOWAIT as an equivalent fix (third assessment §7) | consistent with §4.10; service-level LP-14b still required on all four jobs |

## 12. Residual risks and open items

- **[3.2] Unresolved assumptions** (each with a test, none proved by listing):
  - **INV-U:** an OPEN unresolved record at p implies no committed OPERATION owner at p (PO-B, attested
    settlement);
  - **INV-P:** content paths embed their content UUID, and ingest content ids are fresh per operation (PO-C, PO-D);
  - **A2:** receiving sets are disjoint (one timer per JVM; one registration per operation);
  - **A1, A3 and A4 entry contracts:** outside any transaction (guard G9).
- **[3.2] Implementation acceptance gates** (after an implementation is authorized; none is met by this document):
  1. every ACC test of §10 passes on PostgreSQL 16 and 18 × Linux and Windows, with all participants' COMMIT
     outcomes;
  2. LP-10b's SQL, trigger and fk audit is complete;
  3. the guard (G1–G12) runs enabled in the whole suite;
  4. LP-14b passes at service level;
  5. the Phase 0–3 regression suite is unchanged and green;
  6. an independent implementation audit.

  WP1-R1, GAS-P4-002 and GAS-P4-003 stay open until then.
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

> **Status:** OPEN. The original deadlock-freedom premise is superseded (it holds within one session only; Scenarios A–D are cross-session or cross-subsystem cycles, and GAS-P4-003 adds a user-administration cycle). The replacement protocol (L1a…L1f with L1p, CR-1…CR-4 with exceptions E1–E5, TR with D-LP-9) and the guard contract are proposed in PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL revision 3.2, decisions D-LP-1…D-LP-9. Not implemented; not closed by specification.
