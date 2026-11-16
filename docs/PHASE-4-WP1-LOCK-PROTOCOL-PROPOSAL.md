# Phase 4 WP1: L1 locking-protocol proposal (WP1-R1, GAS-P4-002), revision 3.4

**Status: REVISED PROPOSAL for owner review and independent Codex assessment. Not implemented.**
- No production locking code, SQL, trigger or migration in this document has been written.
- The test designs in §10 are specifications; none of them is claimed to pass.
- Executed evidence is limited to the bounded, non-production checks in §11.
- **Revision 3.4** corrects the four blockers named in the Revision 3.4 direction (fifth independent assessment,
  `Phase-4-WP1-Fifth-Architectural-Assessment.md`, which **was not received by the builder**; the direction's relay
  of its findings is followed):
  - **F5-1:** serialised generation reservation (Wh1a, Wh1b);
  - **F5-2:** preservation-row guard classes (G1, G10);
  - **F5-3:** generation-bound download evidence (We7);
  - **F5-4:** fail-closed admission and activation (§5.5).

  Changes are marked **[3.4]**.
- **Revision 3.3** responded to the fourth independent architectural assessment
  (`Phase-4-WP1-Fourth-Architectural-Assessment.md`, reviewed head `b4c4aca`).
  - **F4-1:** INV-U is withdrawn; path reoccupation is prevented by new change group P2-8g (§5.4).
  - **F4-2:** guard key classes and the order projection are corrected (§6).
  - **F4-3:** the download MISSING window is added (§4.9).

  The response matrix is in the correction record. Changes are marked **[3.3]**.
- **Revision 3.2** responded to the third independent architectural assessment
  (`Phase-4-WP1-Third-Architectural-Assessment.md`, reviewed head `2227b10`) and the Revision 3.2 direction. Its
  finding-by-finding response matrix is in the correction record. Changes are marked **[3.2]**.
- **Revision 3.1** reconciled revision 3 (`569753f`) with the second Stage A review, finding by finding. The
  reconciliation matrix is in `PHASE-4-WP1-AUDIT-CORRECTIONS.md`. Changes from revision 3 are marked **[3.1]**.
- **This is not a readiness claim.** PO-1 is contradicted by source (§2.3), PO-2 is replaced by the key ledger with
  its own open obligations (§2.5), and the theorem in §5 is conditional on decisions D-LP-1…D-LP-9, lemmas A1–A4,
  PO-B…PO-D, the invariant INV-P (scoped to admitted data, §12) and P2-8g (INV-U withdrawn in 3.3).
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
  | **CR-1 content coordination** | **[3.3, scoped as §3.2]** Every multi-statement window writing a content-scoped preservation row of persisted X holds Ct(X) FOR UPDATE first; named exceptions E1–E5 (§3.2, §5). |
  | **CR-2 operation rows** | O is written by its advisory owner, by a lemma A1/A2 window entered outside any transaction, or by a location-keyed write under PO-B (§3.2). |
  | **CR-3 content-less rows** | Rows whose content is not persisted are written under their operation's advisory, except E3 (intent) and E5 (fast path) (§3.2). |
  | **CR-4 location keys** | Claims follow PO-C (§5.2); **[3.3]** no non-final operation claims a path while an OPEN unresolved record exists there (P2-8g, §5.4). |

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
| D4 | DP `markVacated` (one statement) | adv | G(X, CURRENT) NKU | G(X) | — (`UPDATE OF state` not fired) | **Change P2-8c (Wc5):** Ct(X) U first in a new transaction. **[3.3]** The 3.1 "single-resource statement" alternative is withdrawn. |
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
| K13 | `integrity_finding_observation` PK `(scan_id, finding_id)` | **[3.3]** any observation statement for the same scan and finding (no scan-row lock is taken; "one scan at a time" is not relied on) | the insert (`ON CONFLICT DO NOTHING`) | none; the holder is a terminal autocommit statement (lemma A3) | shared; terminal holder | LP-10m |
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
3. **Parents first [3.3].** Before any statement that takes an implicit lock, one of these holds:
   - the transaction holds that resource at the same or a stronger mode;
   - the resource is private (rule 5);
   - it is a terminal reference (rule TR);
   - the statement is admitted under a named classification of §5 (E1–E5; lemmas A0–A4; PO-C's delegated keys
     S1–S3; PO-B's location-keyed writes).

   No unnamed exception exists; the revision 1 exception stays withdrawn.
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

**Count [3.4, operative].** The seven decision groups (P2-8a…g) change **19 transaction windows**:
- the 15 of revision 3.2;
- **We7** (download MISSING);
- **Wh1a** (`planRepair` reservation);
- **Wh1b** (`quarantinePartialAndReplan` reservation; a distinct window that shares Wh1a's allocation design);
- **Wh2** (`createOwned` OPEN check).

**Phase 2 total: 22 changed windows**: 19 under P2-8a…g, plus 3 under D-LP-3, D-LP-6a and D-LP-6b. The startup
admission audit (§5.5) is a read-only gate, not a changed window.

*Historical, not operative:* **Count [3.3].** The **seven** decision groups (P2-8a…g) change **18 transaction windows**:
- the 15 listed below;
- **We7** (download MISSING, F4-3);
- **Wh1** and **Wh2** (P2-8g, F4-1).

**Revision 3.2's count was:** the six decision groups (P2-8a…f) change **15 transaction windows** (marked **CHANGED**):
- Wa2;
- Wb1;
- Wc1, Wc4, Wc5, Wc6;
- Wd1, Wd2, Wd4;
- We1–We4;
- Wf1, Wf2.

**Eleven** windows in those groups stay unchanged under named exceptions or as wrappers (marked **UNCHANGED**):
Wa1, Wa3, Wc2, Wc3, Wd1′, Wd2′, Wd3, Wd4′, We5, We6, Wf3. Two further rows (Wc7, Wd5) only delegate to Wa1 and Wa2.

Separately, D-LP-3, D-LP-6a and D-LP-6b change **3** further Phase 2 windows: FC restriction, `destroyHolding` and
`commitShared`. *Historical:* **Phase 2 total [3.3]: 21 changed windows.** That is 18 under the seven P2-8 subdecisions, plus 3
under D-LP-3, D-LP-6a and D-LP-6b: ten Phase 2 subdecision items, not ten top-level D-LP IDs. Wc7 and Wd5 are
delegates, not windows.

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
| We7 **[3.4, F5-3]** `FileService.download` on verified absence | **CHANGED** | a reader's request. **Authorization snapshot (unchanged):** `get` checks `VIEW_ARCHIVE` and File visibility, then lifecycle (DESTROYED, or DELETED without `EDIT_FILES`, gives not found), then content state (not ACTIVE gives the accepted state message), all as unlocked reads at request start. No File lock is taken (none was before, and none may follow Ct); a Content lock does not stabilise File permissions or roles, and none is claimed. The **observation** O = (content_id, state = ACTIVE, current generation number, volume_id, current storage_path) is taken from the same unlocked read, then the filesystem probe runs **outside any transaction**. | on verified absence, a new outermost transaction: **Ct(X) U** → re-read the locked identity L → if **L = O** (all five fields equal): finding insert (MISSING, with O's generation and path) → Ct NKU (ACTIVE → MISSING compare-and-set). If **L ≠ O**: **insert nothing, change nothing.** | when L = O: `vacates_current_generation` vacates **the generation in O**, which is still CURRENT; `missing_retires_ownership` (generation path, F1) | identity comparison under Ct (state alone is **not** sufficient) | commit. **L = O:** then throw the accepted "This File's stored original is missing. The problem has been recorded." **L ≠ O and L.state ≠ ACTIVE:** throw the accepted state message for L.state (the text of the request-start check). **L ≠ O and L.state = ACTIVE** (a different generation, path or volume): throw the accepted "This File's stored original cannot be read right now. Nothing was changed; try again later." No re-probe inside the transaction; a retry re-observes. `row_version` is not compared (a `markVerified` update must not force a retry). |
| We5 additions **[3.3]** | UNCHANGED (E2) | `download` SIZE_MISMATCH branch (autocommit `insertFindingIfNew` outside any transaction); `OperationProcessor.tryShare` NOT_PROTECTED (autocommit, outside any transaction; the worker holds its own operation's advisory; the finding's `operation_id` fk references that own operation) | as We5 | fk KS on content, volume, own operation | — | each statement commits |

#### P2-8f: repair cancellation, unverified copies, ingest, private publication

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wf1 `unverifiedOwnCopy` | **CHANGED** when `file_operation.content_id` names a persisted row; else CR-3 | adv(own) | **Ct(X) U** (or MISSING) → O NKU (release own claim) → U insert (OPEN) → O NKU | release trigger deletes own owner row; fk KS Ct, O | operation state re-read | commit |
| Wf2 `applyCancel` final transaction | **CHANGED** (same rule) | adv(own) (`performCancel`); its pending settlements are Wa2 | **Ct(X) U** (or MISSING) → (`claimBecomesUnresolved`: O NKU → U insert) or O NKU (release) → O NKU (transition) | as Wf1 | compare-and-set on state | commit |
| Wf3 `commitNewContent` | UNCHANGED (**exception E4**, private publication) | adv(own) | O NKU → O NKU (release own claim) → Ct insert (private) → G insert (claims GENERATION at its own path) → parent F U → F insert (private) → derivation | **at COMMIT:** deferred fk `file_operation.file_id` → own new File (private); `single_owner` read | — | commit |

#### P2-8g [3.4]: generation reservation and location reoccupation (Alternative A; §5.4)

**Allocator call-site inventory** (source analysis at `e5bf8c0`; the only writers of `file_operation.target_generation`):

| Site | Kind | Today | Under 3.4 |
|---|---|---|---|
| `OperationProcessor.plan` (line 121) | INGEST | `target_generation = 1` for a fresh planned content id (`GlobalIds`) | **unchanged**: private content, private path (E4 context); no Ct (the content is not persisted) |
| `OperationProcessor.planRepair` (lines 135–139) | REPAIR | unlocked `max(maxGeneration(X), own previous) + 1`, then `transition` STAGED → PROMOTING | **Wh1a** |
| `OperationProcessor.quarantinePartialAndReplan` (lines 371–378) | REPAIR and INGEST | unlocked `max(own + 1, maxGeneration(X) + 1)`, then `transition` PROMOTING → PROMOTING | **Wh1b** (REPAIR, or INGEST whose content is persisted). INGEST with unpersisted content: unchanged (own + 1, private path). |
| any other | — | none: no transition clears or rewrites `target_generation` | — |

**Reservation transaction** (Wh1a and Wh1b share it):

| Step | Statement | Notes |
|---|---|---|
| 0 | entry | worker thread holds adv(own operation) (registered, not LOST); **no outer transaction** (G9-style assertion); a new outermost transaction, READ COMMITTED |
| 1 | `SELECT … FROM gas.file_content WHERE content_id = :x FOR UPDATE` | **Ct(X) U**; the first lock. If no row: Wh1a rejects (the content must exist for a repair); Wh1b proceeds as private INGEST. |
| 2 | re-read content: state (Wh1a: MISSING or DAMAGED; Wh1b: not DESTROY_PENDING or DESTROYED), size, SHA-256 against the operation's expected values | a mismatch → the accepted `reject` or `block` outcome, inside this transaction; nothing reserved |
| 3 | `SELECT GREATEST((SELECT COALESCE(MAX(generation), 0) FROM gas.file_content_generation WHERE content_id = :x), (SELECT COALESCE(MAX(target_generation), 0) FROM gas.file_operation WHERE content_id = :x), :own_previous) AS high_water` | a **subsequent statement**, so a fresh snapshot after the lock is granted. All operation states count: planned, failed, cancelled, stopped, rejected and completed. `content_id` is set for every repair and for an ingest once it shares or creates X. |
| 4 | `next = high_water + 1`, checked: if `high_water ≥ 2147483647` (`integer` maximum), the operation is **BLOCKED (STATE_UNKNOWN)** with "generation numbering is exhausted for this content; nothing was created", recorded in this transaction | visible; before any filesystem or ownership change |
| 5 | `transition(op, expected state → PROMOTING, …, {target_generation: next, target_path: originalPath(X, next), volume_id, target_created: false})` | **O(own) NKU** plus the event insert (K4). The claim trigger fires on `target_path` and `volume_id` changes: OLD `target_created` is false (planning, or after the partial copy was quarantined and released), so it deletes nothing; NEW is false, so it claims nothing. No fk column changes except `volume_id` (fk KS on the volume: terminal). |
| 6 | compare-and-set result | **false** (the state moved, for example a concurrent Administrator cancel): **roll back the whole transaction**; no reservation exists; the worker returns as today, and the next step re-reads the operation |
| 7 | commit | **this commit is the reservation.** Only then does `createOwned` (Wh2) run the filesystem create and the claim, in its own transaction. |

**Durability and meaning:**
- A committed `target_generation` is never cleared (inventory above), so it survives cancellation, retargeting, cleanup
  and every final state. It is the durable high-water evidence.
- A number computed in memory, or in a transaction that rolled back or crashed before commit, **reserves nothing**
  and may be chosen again. That is safe: no filesystem object or claim was made from it.
- Same-operation replanning (Wh1b) includes `own_previous`, so it always advances past the operation's own earlier
  target.

**Why Ct(X) → O(own) is safe:**
- O(own) is written only by its owner (this worker, under its advisory) and by lemma A1 holders, which are terminal.
  So the planner can wait on O only for a holder that never waits for Ct(X).
- Other transactions holding their own O and waiting for Ct(X) (for example another repair's `commitRepair`) wait for
  the planner. The planner never requests their O.

**Serialisation:** two planners of X serialise on Ct(X) U, so the second's step 3 sees the first's committed
reservation. **Two ordinary planners always receive distinct committed numbers.**

| Window | Status | Entry | Sequence | Implicit effects | Revalidation | Exit |
|---|---|---|---|---|---|---|
| Wh1a `planRepair` | **CHANGED** | as step 0 | steps 1–7 | step 5 | steps 2 and 6 | commit (the reservation), or full rollback |
| Wh1b `quarantinePartialAndReplan` (persisted content) | **CHANGED** (newly counted in 3.4) | as step 0; the partial copy's quarantine settlement (Wa2) has already committed in its own transaction | steps 1–7 with expected state PROMOTING | step 5 | steps 2 and 6 | as Wh1a |
| Wh2 `createOwned` (REPAIR) **[3.4, tightened]** | **CHANGED** | worker; adv(own); outermost transaction | (1) **Ct(X) FOR SHARE**; (2) a **separate, fresh** statement: `SELECT 1 FROM gas.unresolved_object WHERE volume_id = :v AND path = :target AND status = 'OPEN'`; (3) revalidate that the operation's persisted `content_id`, `volume_id`, `target_path` and `target_generation` equal those planned and that the content state is not DESTROY_PENDING or DESTROYED; (4) only if no OPEN record and identity equal: CREATE_NEW, then `markTargetCreated` (claim); (5) **Ct(X) SHARE is held through the claim and commit** | the claim trigger (PO-C S1) | steps 2 and 3 under Ct(X) Sh | **OPEN present or identity changed:** no CREATE_NEW, no claim. The transaction **commits** a `block(op, COLLISION, "the planned target … has an open unresolved record; nothing was created. An Administrator retry plans a new target.")` (an operation transition plus its event), so the refusal is recorded and not rolled back. Retry: Wh1b-style replan (a new reserved number). |

**Collision and retry semantics:**
- **New plans:** after Wh1a, distinct planners never share a number, so a COLLISION from plan sharing disappears for
  new repairs. CREATE_NEW collisions remain possible only from unexpected filesystem objects (accepted behaviour).
- **Legacy pre-planned repairs** (planned before Wh1a, possibly sharing a number): Wh2 refuses them while an OPEN
  record exists. Otherwise CREATE_NEW and the claim behave as today. Their admission is governed by §5.5.

**Not a cure for existing owners:** Wh2 governs **new** claims only. Operations already owning a path when the
protocol is enabled (for example a PROMOTED repair at an OPEN path) are classified by the admission audit (§5.5).

**Owner decision (D-LP-8, pending):** durable cross-operation generation skipping (numbers of failed or cancelled
plans are never reused) is a **proposed** behaviour change, not approved.

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
| **A2: RECEIVING-set update** | `renewReceivers(ids)`: `UPDATE … WHERE state = 'RECEIVING' AND operation_id = ANY(:ids)` | Locks several rows in one statement, in scan order. | **Not single-resource.** A cycle needs another transaction holding a row in `ids` while waiting for a row the statement holds. The other writers of RECEIVING rows are A1 holders (terminal) or another `renewReceivers` call. Two calls never share a row: (i) `ids` is the in-memory `receiving` set of one JVM, and an operation is added only by the request that inserted it (P13) and removed at its transition or abandonment; (ii) the renewal is one scheduled timer per JVM with fixed delay, so calls in one JVM do not overlap; (iii) an operation is never registered in two JVMs. So the sets are disjoint. | **[3.3] Scope:** per **`IngestionService` instance** (a single-thread scheduled executor with fixed delay; `stage` adds and removes only its own operation). The SQL itself (`ANY` over an array) has **no guaranteed row order**, so the lemma holds only while: no caller other than the instance's timer calls `renewReceivers` (to be enforced by an ArchUnit rule); and no operation is staged twice (assertion in `stage`). Overlapping calls are **not supported**; supporting them would need ordered per-row acquisition. **Conditional** on that registration and call protocol; awaiting LP-10l. |
| **A0 [3.3]: owner `note` holders** | `note` (O FOR UPDATE + event) by the owner: P8 worker notes; DestructionProcessor notes (lines 146, 608, 659, 830); `UnresolvedObjects` release (line 97, last statement) and `attestUnderLock` (line 164, followed only by `enqueue`); `OperationAdminService` cancellation notes under the operation's own lock; `quarantineSuperseded` notes | After its `note`, a window takes no blocking acquisition except O's event key (K4), private inserts (a background job with a fresh PK and no other unique key) and commit. | So an owner `note` holder is terminal **after** the note. Its earlier locks are covered by its own window's classification (§4.9). A waiter on O behind it (an intent fk, a finding fk, an A1 call) waits for a holder that will not wait for it. | source-supported; awaiting LP-10b inventory |
| **A3: standalone finding and observation statements [3.3, corrected]** (exception E2, We5) | autocommit SELECT, finding insert, observation insert, outside any transaction (including `download` SIZE_MISMATCH and `tryShare` NOT_PROTECTED) | Each statement is its own transaction. | **Finding insert:** a new private row (fresh PK; no shared unique key). Its fk KS checks run in foreign-key trigger order on content, scan, volume and operation; **it may hold an earlier KS while waiting on a later one.** **KS conflicts only with FOR UPDATE and key updates; ordinary NKU is compatible**, so the 3.2 statement "waits for a Ct FOR UPDATE **or NKU** holder" is corrected to **FOR UPDATE only**. The FOR UPDATE holders are: Ct(X) U holders (§4.9 windows); O FOR UPDATE holders (A0 owners and A1 `note`, both terminal after the note); no FOR UPDATE on scan or volume rows. A KS this statement already holds blocks only a later FOR UPDATE requester: a §4.9 window's Ct lock, or a `note`. **[3.4, corrected]** That Ct requester may already hold resources: an F lock (ordered below Ct; `destroyHolding`, `commitShared`), its own O (owner-only, waited on only by terminal holders: `commitRepair`, `commitNewContent`), or nothing. None of these is a resource our standalone statement will request, so the KS it holds cannot close a cycle. A `note` holder waiting for our KS on O holds only O and its own earlier window resources, and we never request those. So no cycle closes through the held KS. **Observation insert:** key (scan, finding); the contender is another autocommit observation statement (terminal); fk KS on scan and finding never waits (those rows are only NKU-updated). **State-changing callers** of the same helpers (We1, We2, We7) run inside their window and join its tracker; they are **not** admitted as E2. | source-supported; awaiting LP-10m |
| **A4: terminal contentless and fast paths [3.3, corrected]** | `attest` fast path (E5, Wd3); intent (E3, Wa1) | One explicit lock or one insert, with no later wait except on terminal holders. | **Wd3:** U FOR UPDATE, then return or throw; terminal. The classification is safe because attestation and resolution are irreversible, so a record seen as attested or closed cannot return to the writing branch. **Wa1:** the destination key embeds the operation id, and every application contender holds that operation's advisory. Its fk KS on O waits only for an O FOR UPDATE holder: an **A0 owner `note`** or an **A1 non-owner `note`**, both terminal after the note. KS on the volume never waits. **Wa1 does not establish the safety of Wa2** (§5.4). | source-supported; awaiting LP-10n |

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

### 5.1 PO-B: location-keyed operation updates [3.3, revised]

| | |
|---|---|
| **Statement** | `missing_retires_ownership`, `generation retires_ownership` and settlement's `releaseTargetsAt` wait only on a committed qualifying operation row that is (i) owned by an operation whose advisory the writer holds, (ii) a **final** operation's stale claim, whose row is locked only by terminal holders (A0, A1), or (iii) locked by an A1 holder. Otherwise they match no committed row. |
| **Fact F1** (source-supported) | GENERATION owner rows are never deleted (only `owner_kind = 'OPERATION'` rows are deleted, V0017), so no committed operation can own a path any generation records. |
| **Generation-keyed writes** | F1. **Source-supported**, unchanged from 3.2. |
| **Settlement, generation source** | F1. **Source-supported.** |
| **Settlement, own-target source** | The entry's own operation; the settler holds its advisory. **Source-supported.** |
| **Settlement, attested source** (s = u.path) **[3.4]** | INV-U stays withdrawn. A committed claimant at s is one of: **(a)** the entry's own operation (advisory held); **(b)** a **final** operation's stale claim (destruction refusal), whose row has only terminal writers (A0, A1); **(c)** an **active** claimant that made its claim **after enablement**, impossible because Wh2 refuses new claims while the OPEN record exists, and the record stays OPEN until Wa2 commits; **(d)** an **active or resumable claimant that already existed at enablement**, which the admission audit classifies UNSAFE and isolates (§5.5), so no settlement of that content runs. **Generation reservations (Wh1a, Wh1b)** make (c) unreachable through planning as well. **Trigger-driven ownership changes** (claims, releases, retirements) are covered by F1, PO-C and the window inventory. **Deferred COMMIT checks** are reads. **Class: conditional on P2-8g and the §5.5 admission policy** (proposed design); awaiting LP-15 and the admission tests. |
| **Deferred work at COMMIT** | unchanged: no fk column changes; deferred triggers are reads. |
| **Tests** | LP-10e (i)–(iii) as 3.2. **(iv) is replaced** by LP-15 (the F4-1 schedule) and by runtime assertions on **destruction refusal after inventory** and on **claim creation while an OPEN record exists** (§10). |

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

### 5.4 F4-1: unresolved presence and path reoccupation [3.3]

**Evidence class:** source analysis (the fourth assessment's schedule, rechecked by the builder against `b4c4aca`);
the recommendation is a proposed design. No new PostgreSQL execution.

**INV-U withdrawn.** Revision 3.2's INV-U ("an OPEN unresolved record at p implies no committed OPERATION owner at
p") is **contradicted** by permitted paths. It is withdrawn, and **not** replaced by an equivalent assertion. Two
independent counterexamples:

| Case | Path | What it shows |
|---|---|---|
| **A. Destruction refusal keeps a claim** | DestructionProcessor classifies a stopped repair's target as its copy, then finds changed or unidentifiable bytes in `deleteAuthorized` → `refuse` → `newRecord` records OPEN unresolved presence. `newRecord` does not release the operation's claim. Destruction stays incomplete, so the completion transaction (which releases collected claims) does not run. | **Persistent coexistence** of an OPEN record and a claim by a **final** (CANCELLED) operation. Its later writers are terminal (A0, A1). This refutes INV-U. On its own it is not an active-worker cycle. |
| **B. A later repair reclaims an emptied path** | `claimBecomesUnresolved` releases a claim and records OPEN presence: non-ownership **at that commit only**. `planRepair` takes `max(maxGeneration, own previous) + 1`, which ignores other repairs' failed target generations. `createOwned` checks only content state before CREATE_NEW and the claim. | An active repair W of the same content can claim the same unrecorded generation path p after p is emptied. |

**The deadlock (assessment §3), source-derived.** X needs repair; p = originalPath(X, g), with g unrecorded; the OPEN
record at p is attested.
1. Settler S holds adv(A), records the intent and moves p → q; it pauses at AFTER_QUARANTINE_MOVE, before Wa2.
2. Repair W of X plans g; `createOwned` takes Ct(X) Sh; CREATE_NEW at the empty p succeeds; the claim commits; W
   reaches PROMOTED.
3. S starts Wa2 and takes **Ct(X) U** (P2-8a).
4. W starts `commitRepair` and takes **O(W) NKU**, then requests Ct(X) and waits for S.
5. S's `releaseTargetsAt(p)` matches W's committed claim, requests O(W) and waits for W.

**S → W → S.** It needs no cross-content path, no INV-P failure and no malformed data. In the **accepted** code
(no P2-8a) there is no deadlock, but step 5 **releases W's legitimate new claim** on its own copy: a semantic
defect recorded as GAS-P4-004 (source-derived, not executed).

**Alternatives** (comparison criteria from the assessment §3 and the truncated direction §2.2):

| Criterion | **A: prevent reoccupation (recommended)** | **B: tolerate reoccupation; non-blocking settlement** |
|---|---|---|
| Serialising resource | Ct(X): `createOwned` already takes Ct(X) Sh; every OPEN-record creator, converter and resolver of X takes Ct(X) U after P2-8a…f (Wa2, Wb1, Wc1, Wc4, Wd1, Wd4, Wf1, Wf2, `commitRepair`) | none new: settlement probes foreign claimant rows `FOR UPDATE NOWAIT` (or SKIP LOCKED) before `releaseTargetsAt` |
| Mechanism | **Wh1:** planning never reuses any repair's planned generation (monotonic per content). **Wh2:** `createOwned` refuses a target with an OPEN record, checked under Ct(X) Sh. Two independent layers; not a one-time check at record creation. | On a busy foreign row, roll back the whole Wa2 and retry later (reconciliation). Release only claims that **predate** the intent, which needs the claimant set recorded at intent time: a new column or table (schema change). |
| Filesystem move / database boundary | The record stays OPEN from intent until Wa2 commits (Wa2 resolves it in the same transaction as the release). So from the move until settlement no repair can claim s; after Wa2 commits, s is free and empty, and reuse is harmless. | Must revalidate source, destination, record, content identity and owner after the move; a new owner's claim must survive. |
| Outcome for a repair facing an OPEN path | Only reachable through legacy or unplanned data, since Wh1 already avoids it. Visible BLOCKED (COLLISION) with a message; a retry replans a new generation. | The repair proceeds; settlement may retry repeatedly while the repair holds its row (bounded by the repair's short transaction). |
| Destruction refusal (case A) | The stale claim of the final operation stays until settlement releases it; final rows have only terminal writers (A0, A1). No new behaviour. | Same, plus claimant-set bookkeeping. |
| Crash recovery and retries | Unchanged: intent before move; Wa2 atomic; `reconcileAll` resumes. `createOwned` creates nothing when refused, so no partial state. | New retry state; risk of starvation under repeated repair activity; the partial-rollback rules must be specified. |
| Change size | Two windows; no schema change; no trigger change. | A schema change (claimant set) plus a retry protocol. |
| Accepted behaviour | Generation numbers can skip planned-but-failed numbers. Replanning by the same operation already skips (`previous + 1`). Byte evidence, ownership and history unchanged. | Settlement completion can be delayed; release semantics change. |
| Safety argument | PO-B settlement case becomes: claimant = own operation (advisory held) or a final operation (terminal writers). No foreign active owner is possible, so no S → W edge exists. | Relies on NOWAIT plus restart and on correct claimant-set bookkeeping. |

**Recommendation: Alternative A** (P2-8g, windows Wh1 and Wh2). It keeps `releaseTargetsAt`'s accepted meaning:
the source becomes historical for the stale claims that described the moved object. It also ensures no newer
operation's copy can be at the source while settlement is pending. So settlement neither releases nor adopts a new
operation's copy.

**Legacy data.** Repair operations planned before Wh1 may already share a generation number with a failed repair's
target. Wh2's OPEN check under Ct(X) still prevents a claim while the record is open. Where no OPEN record exists,
reuse of an empty unrecorded path is harmless. The data audit in §12 lists such cases.

### 5.5 F5-4: fail-closed admission and activation [3.4]

**Evidence class:** proposed design.
- **No schema change.**
- **No rewriting of paths or evidence.**
- **The admission state is recomputed at each startup** and is not stored.

**Audit inventory** (read-only SQL over relational identity, not path prefixes alone). Each item names its source
rows:

| # | Check | Unsafe when |
|---|---|---|
| AU-1 | generations: `storage_path` against `originalPath(content_id, generation)`; volume | mismatch (legacy identity) |
| AU-2 | operations: `target_path` against the constructor for (kind, `content_id`, `target_generation`); `target_created`; state | mismatch on a non-final operation |
| AU-3 | `physical_location_owner`: every row resolves to an existing GENERATION (content, generation) or OPERATION (`operation_id`) whose recorded path equals the owner path | a dangling or mismatched owner |
| AU-4 | OPEN `unresolved_object` against OPERATION owners at the same (volume, path) | an owner that is **non-final** (active or resumable): **UNSAFE-ACTIVE**. Owner final (CANCELLED, FAILED, COMPLETED, …): **stale, admitted** (terminal writers only). |
| AU-5 | quarantine entries: INTENDED entries whose source (volume, path) is claimed by an operation other than the entry's own | **UNSAFE-AMBIGUOUS** |
| AU-6 | quarantine destinations against the `quarantinePath(operation, suffix)` constructor | mismatch |
| AU-7 | planned generations: repair operations of one content sharing a `target_generation`, where either is non-final | **UNSAFE-ACTIVE** if both could still claim |
| AU-8 | case-fold and host-normalisation aliases: per volume, distinct relational identities (different generation, operation or unresolved row) whose paths are equal under `lower()` and the platform's NFC normalisation | alias across **independent** identities. Several references to one object (a generation and its own unresolved record, an operation and its own quarantine entry) are **normal** and not flagged. |

**Classification and scope.** Each unsafe finding names a content X; contentless ones name an operation.
- **Admitted:** clean data and final stale claims (AU-4 final).
- **Isolated (fail-closed):** every content with an UNSAFE-ACTIVE, UNSAFE-AMBIGUOUS or legacy-identity finding.

**Alternative chosen: A, fail-closed admission and controlled isolation.** It is smaller than B (identity-aware
settlement), which needs claim-incarnation evidence and therefore a schema change.

| Isolated content X: disabled | Still allowed |
|---|---|
| worker steps of any operation on X (planning, creation, commits, replanning) | reads and downloads of ACTIVE content (no state change) |
| `reconcileAll` and other settlement of X's quarantine entries | standalone E2 findings |
| unresolved release, attest and close for X | Administrator **cancellation** of an isolated operation (`performCancel`): with every other writer of X disabled, its windows have no concurrent X writer, so no cycle is possible |
| destruction of X | the Administrator **"re-run admission audit"** action |
| IntegrityService state changes on X (We1–We4: the finding is recorded through the E2 path; the content state is not changed) | |
| download MISSING recording for X (We7: the accepted "cannot be read right now; nothing was changed" outcome) | |

**Visible outcome:**
- At startup, the log and the Administrator operations and integrity views list each isolated content, with its
  audit category and the rows involved.
- Each isolated operation shows "held by lock-protocol admission audit (category AU-n)" (a computed flag, not a
  stored state).
- Disabled actions return a visible "This content is held for review by the lock-protocol admission audit; nothing
  was changed."

**Administrative resolution workflow:**
1. The Administrator reviews the listed rows.
2. They cancel or allow to finish the isolated operations (cancellation as above).
3. They use the existing unresolved and quarantine tools, once re-admitted, or record a decision.
4. They re-run the audit.

Contents whose findings are cleared are admitted; there is no automatic rewrite.

**Activation boundary:**
1. The release that contains the corrected protocol runs the audit at startup, **before** any preservation writer
   starts (the worker, recovery, reconciliation, integrity scanner and receivers are not started until the audit
   completes).
2. The old release is not running; it is the same process being upgraded.
3. From the first enabled moment, every writer is a new-protocol writer (Wh1a, Wh1b, Wh2 and Ct coordination), so
   **no old writer can act between audit and enablement**.
4. A restart re-runs the audit.
5. A database restored from backup is audited at its first start.

**Default:** fail-closed for affected unsafe data; everything else is admitted.

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
| G1 **[3.4]** | `acquire(level, table, id, mode)` → Ticket | no open ticket; not FAILED; context allows it (G9); (level, table) matches the method binding. **Ordered resources only** (S, F, Ct, worksheet K rows and the ordered worksheet key, R, catalog Source then Repository): projection π = (level, sub-level, uuid). Let M = the max π over all held or passed ordered resources at any level. Reject if π(new).(level, sub-level) < M.(level, sub-level); at an equal sub-level reject if uuid < M.uuid (equal → G5 re-lock). **L1p rows are not ordered by π** (no cross-table UUID comparison) and are admitted by G10 instead. **Late-global prohibition:** after a content-coordinated U lock or a terminal single-resource lock is held, **any** new ordered acquisition is rejected. After an owned O lock, ordered acquisitions proceed under the normal π check (owner-held O is waited on only by terminal holders, lemma L4). Then: not an upgrade. | `acquired()` → rowLocks (with class); `missing()`; `failed()` |
| G2 **[3.3]** | `insertEffects(table, Effects)` → Ticket, one per INSERT statement. **Effects** declares, per unique index the statement touches: an **ordered** key (projected by π, checked as G1); a **coordinated** key with its coordinator; a **delegated** key (G7); or **private**. | Ordered keys: G1's check. Coordinated keys: **no order check**; the coordinator must be held (S U for the worksheet sequence; Ct(X) U, or the content being private, for generation keys; Ct(X) U or adv(operation) for unresolved OPEN; adv(operation) for the quarantine destination; empty tracker for batch/item, a first wait). **No cross-index ordering by index name.** PostgreSQL's internal index order is not observed and not claimed. | `inserted()`: ordered keys INSERTED (passed), coordinated keys recorded as effects, row private; `taken(index)`: that ordered key TAKEN (passed); `failed`: FAILED |
| G3 **[3.3]** | `deleteEffects(table, Effects)` | as G2 (ordered keys checked; coordinated keys asserted) | ordered DELETED positions are held until transaction end |
| G4 | `privateNew(table, id)` | the table is on the ledger's private list **and** the INSERT touches no instrumented or delegated shared index; else rejected | privateRows |
| G5 | re-lock: `acquire` of an ACQUIRED key | **WP1-R3 rules kept:** same level; same-or-weaker mode; no acquisition at a higher level or sub-level since; a MISSING or TAKEN key is a normal acquisition | none |
| G6 | `assertHeld(level, table, id, mode)` | ACQUIRED at ≥ mode, else `IllegalStateException`; no SQL | — |
| G7 | `delegatedKey(trigger, index, keyTuple, proofId)` | for keys taken inside triggers (`claim_physical_location`, owner delete): **not an ordering check**. Records that the statement may wait on that key, under the named proof (PO-C S1, S2, S3); rejects if the proof's precondition is false in tracker terms (S2: Ct(X) held U; S3: own operation; S1: last blocking statement). **[3.3]** For S1 the ticket marks the window **CLOSED**: every later statement through the guarded data source is rejected until commit, including uninstrumented SQL. G7 records proof context; it does not prove path privacy or reoccupation safety (§5.2, §5.4). | delegated positions (for test reporting) |
| G8 | ownership: `OperationLocks.tryLock`, `tryLockAll` and `tryLockForTransaction` register `adv[key]` when granted; `Held.close()` (after `pg_advisory_unlock` returns true) or the transaction end (for xact locks) deregisters it | — | the registry **survives tracker resets**; a new tracker attempt sees live ownership. Ownership is bound to the **thread** that acquired it. **[3.3]** `Held.handOff(thread)` is allowed only when the giving thread has **no active tracker and no statement in flight**; it does not authorise sharing a tracker or connection across threads. **Connection loss** on the advisory connection, or an unlock returning false, marks the ownership **LOST**. Protected work that relies on it (G10) is then rejected until the operation is re-acquired. |
| G9 **[3.3]** | `enter(lemma)` for A1, A2, A3 and A4 windows; `standalone()` for E1 and E2 statements, **only at the named entry points** (the scan loop and download branches outside transactions) | **rejects unless** the tracker is empty **and** `!isActualTransactionActive()`, checked **before** the new outermost transaction opens. Helpers called inside a state-changing window (We1, We2, We7) do **not** call `standalone()`; they join the normal tracker. | context set with the lemma's **allowed effects**: A1 = {O row, O event key}; A2 = {the receiving rows of the call}; A3 = {finding insert, observation key}; A4-fast = {U row FOR UPDATE}; A4-intent = {quarantine destination key (coordinated by adv)}. Anything else is rejected. |
| G10 **[3.4]** | L1p admission (one contract with G1) | **Content-coordinated U** (`lockForUpdate`, `recordIfAbsent` effects): precondition Ct(X) ACQUIRED at UPDATE for X = the row's re-read persisted `content_id`. Modes: FOR UPDATE, NKU. Later permitted: other content-scoped rows of X, own O, private inserts, terminal KS; **no new ordered acquisition** (G1). **Owned O** (`note`, `transition`, owner updates): precondition `adv[O]` registered to this thread and not LOST (G8). Modes: FOR UPDATE, NKU. Later permitted: everything the window's §4.9 row lists, subject to G1. **Terminal single-resource O or U** (A1, A4-fast): precondition SINGLE_RESOURCE context (G9) with that declared row. Modes: as the lemma states. Later permitted: event key, then commit only. **Contentless U** (CR-3): precondition `adv[u.operation]` registered. **Missing coordinator** (no Ct, or Ct not at UPDATE) or **ownership LOST:** reject before SQL; the window's transaction rolls back and the worker step ends with the accepted retry-later handling. **Implicit trigger and fk effects** of each admitted statement are those listed for its window in §4.9; the guard records the declared effects and does not observe them. | records (class, coordinator or owner, row, mode) |
| G11 **[3.3]** | D-LP-9 key | **at entry:** no active outer transaction (asserted before `tx.execute`); **inside** the new transaction the advisory SQL is the first statement and the tracker is empty | registered as transaction-scoped; released at commit or rollback |
| G12 | completion | `afterCompletion` (commit **and** rollback), including rollback after an exception before a ticket closed: clears rowLocks, keyPositions, missing, privateRows, openTicket, failed, context. **Never** touches the session-ownership registry; transaction-scoped advisory registrations end here. **[3.3]** Unknown completion (connection lost during commit) resets the tracker and marks any session ownership on that thread for revalidation (G8). | — |

### 6.3 Key classes and the order projection [3.3, F4-2]

| Class | Keys | Rule |
|---|---|---|
| **Ordered** | row locks of §4.1; worksheet `file_capture_context(file_id)` (insert and delete) | π(resource) = (level, sub-level, uuid). The worksheet key projects to **(L1d, file_id)**, the same point as the worksheet row lock on that File, so a row lock and a key on one File compare consistently. G1 checks them against all held or passed ordered resources at every level. |
| **Coordinated** | worksheet `(capture_session_id, sequence_number)` (coordinator S U; sequence values follow **request order** and are not ordered); generation PK, CURRENT and location (Ct(X) U, or the content private); unresolved OPEN location (Ct(X) U, or adv(operation) if contentless); quarantine destination (adv(operation)); `file_operation` batch/item (empty tracker: first wait) | Precondition asserted; no order check |
| **Delegated** | `physical_location_owner` PK and its operation-owner index (inside `claim_physical_location` and the release trigger) | G7 with a named proof (PO-C S1, S2, S3) |
| **Private** | fresh-UUID primary keys; job rows; history rows | G4: per insert, only when the statement touches no ordered, coordinated or delegated key |

**Witnesses** (both must be accepted; LP-8b):
- two worksheet inserts F1 < F2 in one `add`: π = (L1d, F1), then (L1d, F2), ascending. Their sequence keys (for
  example 9, then 6) are coordinated by S and not ordered;
- LP-3's provisional slots 6, 8, 9 and compaction updates 8 → 7, 9 → 8.

**Rejection witness:** holding R (L1e) and then requesting any F (L1b) is rejected by G1, whatever the UUIDs.

**Savepoints and native SQL:**
- The guarded `DataSource` proxy rejects `Connection.setSavepoint` and statements beginning `SAVEPOINT`, in tests.
- `OperationLocks`' native JDBC advisory connection is an audited exception: advisory try-locks and unlocks only.
- Any other native JDBC path must be listed in LP-10b's inventory.

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
| P2-8e | Phase 2 IntegrityService **and `FileService.download` [3.3]** | We1–We4, **We7** (download MISSING: Ct U first, state re-read; the finding and state still commit before the accepted error is thrown); standalone findings (including download SIZE_MISMATCH and `tryShare` NOT_PROTECTED), observations and `markVerified` unchanged (E1, E2) | 5 | A scan or a download that finds the original missing waits briefly for concurrent work on the content |
| **P2-8g [3.4]** | Phase 2 repair planning and target creation | **Wh1a** (`planRepair`), **Wh1b** (`quarantinePartialAndReplan`): serialised reservation under Ct(X) U, high water over recorded and planned generations of all states, checked overflow; **Wh2**: OPEN check in a fresh statement under Ct(X) Sh, identity revalidation, recorded COLLISION refusal | 3 | Generation numbers skip failed or cancelled plans (**pending owner decision**). New planners never share a number. A legacy pre-planned repair facing an OPEN record is BLOCKED (COLLISION) with a message; a retry replans. |
| P2-8f | Phase 2 repair cancellation and unverified copies | Wf1, Wf2 (persisted content only); ingest under CR-3; publication E4 | 2 | as above |
| D-LP-5 | Phase 3 `linkFile` | F Sh before R | 1 | A link waits for a concurrent restriction of the File |
| D-LP-7 | Phase 3 grouping, `add`, `saveMarks`, `removeFile` | §4.2–§4.4 | 4 | `add` waits for a concurrent restriction or destruction of a candidate File; counts unchanged |
| Admission (§5.5) **[3.4]** | Activation of the corrected protocol | startup audit AU-1…AU-8; fail-closed isolation of unsafe contents; Administrator cancel and re-audit workflow | 0 (a gate) | Isolated contents' preservation work pauses visibly until reviewed |
| D-LP-9 | Option (a) as specified in §4.10 | 4th: architecturally acceptable as a separately bounded item | Retain the recommendation. **No production implementation is authorised by the 3.4 direction.** |

**Totals:**
- Phase 2 **[3.4]**: 22 windows: 19 under the seven P2-8 subdecisions, plus 3 under D-LP-3, 6a and 6b. *(Historical 3.3: 21; 3.2: 18.)*
- Phase 3: 5 windows under D-LP-5 and D-LP-7.
- User administration: 1 window.

## 8. Decisions requested (revision 3.2)

| ID | Decision | Third assessment | Builder recommendation |
|---|---|---|---|
| D-LP-1 | Order and rules of §3 (L1p, CR-1…CR-4 with E1–E5, TR with D-LP-9) | acceptable only with amendments | Approve the 3.2 text (CR-1…CR-3 rescoped; PO-A split) |
| D-LP-2 | Ordered insertion and compaction, F FOR SHARE | architecturally acceptable | Approve |
| D-LP-3 | Closure; **explicit owner approval required for the new visible Conflict after exactly three abandoned restriction attempts** | architecturally acceptable | Approve |
| D-LP-4 | Guard contract §6 (G1–G12, key classes §6.3), **[3.4] with preservation-row classes in G1 and G10** | 5th: blocker F5-2 | Approve the 3.4 contract. Does **not** close GAS-P4-002 by specification. |
| D-LP-5 | File before Record | architecturally acceptable | Approve |
| D-LP-6a | Destruction ordering | acceptable only with amendments | Approve with the same-id rule and the PO-B narrowing |
| D-LP-6b | Shared publication ordering | architecturally acceptable | Approve |
| D-LP-7 | Phase 3 sequences | architecturally acceptable | Approve |
| D-LP-8 | Modified Option C, P2-8a…g (**19 windows**), including **[3.4]**: serialised generation reservation (Wh1a, Wh1b); **durable cross-operation generation skipping**; Wh2 COLLISION and retry behaviour; the We7 generation-bound download correction; **fail-closed admission (§5.5)** | 5th: blockers F5-1, F5-3, F5-4 | Approve the 3.4 design. Each listed behaviour change needs explicit owner approval. |
| D-LP-9 | Option (a) as specified in §4.10 | **4th: architecturally acceptable as a separately bounded implementation item**; NOWAIT not equivalent | Approve (a). Option (b) NOWAIT is **withdrawn** as an equivalent (counterexample §11.7). (c) keep = explicit risk acceptance only. |

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
  - ~~**Runtime check:** `pg_locks` after each A1 statement shows only the O row lock and the transaction lock.~~
    **Historical, not operative [3.4]:** withdrawn in 3.3. Use blocker pids, real conflicting probes and the exact
    SQL, trigger and fk inventory.
  - **Ownership:** registered ownership is cross-checked against granted advisory locks.
- **LP-10h [3.4, aligned]:** two ordinary new planners of one content run concurrently: they serialise on Ct(X) (blocker observed) and receive **distinct committed** generation numbers. A same-generation CREATE_NEW collision is kept only as a **legacy-fixture** or DB characterisation.
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

**[3.3] Amendments from the fourth assessment §9:**
- **LP-8c [3.4, F5-2] (service paths):**
  - Ct → U → own O with U's UUID greater than O's, **accepted**, and the reverse order, accepted;
  - owned attestation and unresolved release;
  - the terminal O and U fast paths;
  - a new lower-level ordered resource after a higher one, **rejected**;
  - an unsupported late ordered lock after a coordinated U, **rejected**;
  - a missing coordinator, **rejected**;
  - session-advisory ownership LOST, or advisory-connection failure, **rejected**;
  - worksheet multi-index and descending provisional-sequence acceptance retained.

  The accepted service order is not changed to satisfy UUID comparisons.
- **LP-8b additions:**
  - **accepted:** several worksheet inserts in one `add`, each touching the ordered `file_id` key and the
    coordinated sequence key; descending request-rank sequence values; LP-3's 6, 8, 9 compaction;
  - **rejected:** a lower-level new resource after a higher-level one (R then F);
  - the A4 fast-path U lock admitted under its lemma; a helper joining a normal state window (no `standalone()`);
  - LOST ownership after advisory-connection loss; `handOff` refused while a tracker is active;
  - a window CLOSED after an S1 claim rejects uninstrumented SQL;
  - savepoint rejection through the `DataSource` proxy.
- **LP-9c:** the fixture must ensure G holds no lock on P through any other dependency.
- **LP-10b observability (corrected):**
  - The 3.2 assertion "`pg_locks` shows only the O row lock" is withdrawn: granted tuple locks are mostly not shown
    in `pg_locks`.
  - Use instead: the SQL, trigger and fk inventory; the guard's declared effects; and **controller probes**. A probe
    is a conflicting request with `NOWAIT` from a controller connection: 55P03 means the row is held, success means
    it is not.
  - Identify blockers by `pg_blocking_pids`.
- **LP-10d [3.4, corrected]:** revision 3.3's fixture is withdrawn. It assumed `commitRepair` and Wb1 act on the same
  initial generation, but one needs it CURRENT and the other QUARANTINE_PENDING. Two separate valid fixtures replace
  it:
  - **(i) Ct serialisation:** a repair of X in `commitRepair` (generation g_old CURRENT; X MISSING or DAMAGED) races
    a We1 MISSING recording of X. The second is observed waiting **on Ct(X)** (blocker = first). Final states are
    consistent, with exactly one CURRENT generation.
  - **(ii) OPEN-key contention:** two legitimate recorders of one OPEN path for X, both under Ct(X): Wc4 (a stopped
    repair's copy) and Wf2 (that repair's cancellation path). The fixture states initial generations, ownership and
    states explicitly. The second returns the existing record after the first commits.
  - **(iii)** A DB-class characterisation of the raw OPEN-key wait.

- **LP-10e:** (ii) uses the own-target case only. **(iv) is replaced** by runtime assertions at destruction refusal
  after inventory, and at any claim while an OPEN record exists, plus LP-15.
- **LP-10f:** the uncommitted claim is held by a **server-side seam**: a test-only trigger, installed only in the test
  database, that blocks on a controller advisory key before the claiming statement completes. A Java seam after an
  autocommit statement returns is too late. Variants: commit, rollback, ownership error, and the operation-owner
  index as a DB-constraint test.
- **LP-10l:** the real `IngestionService` registration and timer:
  - the timer never overlaps itself (an executor test);
  - a second `stage` of one id is rejected by assertion;
  - an ArchUnit rule allows only the instance timer to call `renewReceivers`.

  Two literal disjoint arrays alone are not a test of the invariant.
- **LP-10m:** two autocommit observation inserts with the **server-side seam** before the first commits, so the
  second waits on the key. The production E2 statements are not wrapped into a larger transaction. Synthetic SQL
  characterisation is labelled DB.
- **LP-13 (iii):** the controller's raw Repository blocker is distinguished from a real Repository-correction service
  call; both variants are asserted.
- **LP-14b:** the full four-job gate of the fourth assessment §8 (items 1–8), including first-changer rollback in the
  last-administrator race, display-only, no-change and self-disable precedence cases, entry rejection from an outer
  transaction, key release on commit and rollback, and no user FOR UPDATE in the service path.
- **LP-15 [3.4]:** three classes, kept apart.
  - **(a) Semantic baseline (COMPAT characterisation, accepted code):** a settler pauses at AFTER_QUARANTINE_MOVE;
    repair W reclaims p and commits its claim; settlement releases W's claim (GAS-P4-004). Assert the ownership
    outcome. No deadlock is expected.
  - **(b) Raw-SQL deadlock characterisation (DB):** the 3.2 Wa2 order (Ct(X) U, then the keyed release) against
    `commitRepair` (O(W), then Ct(X)): 40P01.
  - **(c) Corrected acceptance (ACC):**
    - Wh1a with W planning while an OPEN record exists at p: W reserves a new number.
    - Wh2 in both orders: OPEN creator first (W refused, COLLISION recorded and committed), and Wh2 first (the OPEN
      creator waits on Ct(X), then records). The settlement completes; W's copy is never released or adopted.
  - **Reservation cases (ACC):**
    - two simultaneous planners from one baseline get distinct committed reservations;
    - one planner rolls back, and the next reuses nothing committed;
    - a crash between selection and persistence leaves nothing reserved;
    - cancelled and final historical plans count toward the high water;
    - partial-copy replanning (Wh1b) competes with an initial plan (Wh1a);
    - same-operation replanning advances past its own previous plan;
    - a compare-and-set failure means full rollback, then a retry reads afresh;
    - at `high_water = 2147483647` the operation is BLOCKED with the exhaustion message, and nothing is created or
      claimed.

    Each case asserts **committed** rows, not in-memory values.

- **LP-16 [3.4]:** download evidence.
  - **(i)** Ordinary confirmed MISSING: the finding and state are committed, then the accepted message.
  - **(ii)** ACTIVE(g1) → MISSING(g1) → repair commits ACTIVE(g2), all **before** the delayed download takes Ct. A
    server-side seam holds the download between probe and transaction. Result: nothing inserted, g2 not vacated,
    and the accepted "cannot be read right now; nothing was changed" message.
  - **(iii)** Changed volume, path or current generation only.
  - **(iv)** Already MISSING at lock time: the accepted state message.
  - **(v)** Destruction started (DESTROY_PENDING), and restoration or reverification (MISSING → ACTIVE, same
    generation): identity equal only when all five fields match.
  - **(vi)** Finding attribution and history exact.
  - **(vii)** The precedence: not found → state message → unavailable volume → unreadable → missing.
  - **(viii)** Standalone SIZE_MISMATCH and `tryShare` NOT_PROTECTED (E2).
  - **(ix)** Authorization snapshot: a File restricted after the request-start check behaves as accepted (no new
    File lock).

- **[3.3] Changes to the assumptions:**
  - **INV-U is withdrawn (contradicted, §5.4)** and is not restated under another name. P2-8g is a mechanism with
    its own window inventory, not an assumption.
  - **INV-P is scoped:** the theorem covers the **admitted data set**: rows produced by the audited constructors
    (`ManagedVolume.originalPath` and `quarantinePath`, ingest UUIDs from `GlobalIds`, repair generation planning,
    partial-copy replanning) and preserved known provenance. It is **not** a database constraint.
- **[3.4] Data audit:** superseded by the fail-closed admission and activation design of §5.5 (AU-1…AU-8). A read-only
  report alone is not sufficient.

- **[3.3] Receiver scope:** lemma A2 holds per `IngestionService` instance, with no direct callers and no duplicate
  staging (§5).
- **[3.2] Unresolved assumptions** (each with a test, none proved by listing; INV-U superseded as above):
  - ~~**INV-U**~~ **withdrawn [3.3]** (§5.4); replaced by the P2-8g mechanism, not by an assumption;
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

> **Status:** OPEN. **[3.3]** The original deadlock-freedom premise is superseded (it holds within one session only; Scenarios A–D are cross-session or cross-subsystem cycles, and GAS-P4-003 adds a user-administration cycle). The replacement protocol (L1a…L1f with L1p, CR-1…CR-4 with exceptions E1–E5, TR with D-LP-9) and the guard contract are proposed in PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL revision 3.3, decisions D-LP-1…D-LP-9 (D-LP-8 including P2-8g). Not implemented; not closed by specification.
