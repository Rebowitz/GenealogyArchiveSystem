# Phase 4 WP1: L1 locking-protocol proposal (WP1-R1, GAS-P4-002), revision 3.7

**Status: REVISED PROPOSAL for owner review and independent Codex assessment. Not implemented.**
- No production locking code, SQL, trigger or migration in this document has been written.
- The test designs in §10 are specifications; none of them is claimed to pass.
- Executed evidence is limited to the bounded, non-production checks in §11.
- **Revision 3.7** responds to the eighth independent architectural assessment
  (`Phase-4-WP1-Eighth-Architectural-Assessment.md`, reviewed head `7a97e70`) and the Revision 3.7 direction (§5.8):
  - an installation-specific lease;
  - a continuous legacy freeze (an owner decision);
  - the bootstrap state machine;
  - per-transaction identity handover;
  - the controller-loss latch and drain;
  - journal reconciliation;
  - audit corrections.

  It is designed **conditionally** on Q-36-1 (one application host), which is **not approved**. F5-1, F5-2 and F5-3
  are unchanged. Changes are marked **[3.7]**.
- **Revision 3.6** responds to the seventh independent architectural assessment
  (`Phase-4-WP1-Seventh-Architectural-Assessment.md`, reviewed head `9993220`) and the Revision 3.6 direction. F5-4
  is answered by:
  - an **early admission fence**: F1, an OS instance lease held by every compatible process for its life; F2, a
    one-time legacy transition (OS verification, then the role default, then same-role session termination); F3,
    connection identity checked on every physical connection;
  - **one maintenance authority** (MA) for migration, activation and resolution;
  - a **durable action journal**;
  - a **corrected admission audit** (§5.7).

  §5.6 is retained where §5.7 does not replace it. F5-1, F5-2, F5-3 and Wh2 are unchanged. A-6 is deferred. Changes
  are marked **[3.6]**.
- **Revision 3.5** responds to the sixth independent architectural assessment
  (`Phase-4-WP1-Sixth-Architectural-Assessment.md`, reviewed head `fe197ed`) and the Revision 3.5 direction:
  - **F5-4** is answered by **globally quiesced, fail-closed preservation activation** (§5.6), with a durable
    activation record and a database write gate (a **schema requirement**, owner decision D-LP-8 (v)). Revision
    3.4's process-local isolation (§5.5) is withdrawn as a safety authority and kept only as history.
  - AU-1…AU-8 are rewritten on the real states (§5.6.5). The writer inventory is per entry point (§5.6.4). The
    offline resolution procedure is §5.6.7, the control units §5.6.8 and the admission tests LP-17 (§10).
  - §11 and the §12 heading, lost in 3.4, are restored; §6.3 is aligned with G1/G10.
  - F5-1, F5-2, F5-3 and Wh2 are retained unchanged. R34-OPEN-1 was reconciled by the sixth assessment for
    reviewer access only; the builder never received the fifth report.

  Changes are marked **[3.5]**.
- **Revision 3.4** corrects the four blockers named in the Revision 3.4 direction (fifth independent assessment,
  `Phase-4-WP1-Fifth-Architectural-Assessment.md`, which **was not received by the builder**; the direction's relay
  of its findings is followed):
  - **F5-1:** serialised generation reservation (Wh1a, Wh1b);
  - **F5-2:** preservation-row guard classes (G1, G10);
  - **F5-3:** generation-bound download evidence (We7);
  - **F5-4:** fail-closed admission and activation (§5.5; **withdrawn in 3.5**, replaced by §5.6).

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
  - **builder-executed:** local PostgreSQL 16 (§11.3, §11.5–§11.8);
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
| §2 D-LP-8 as an active decision | Options A, B and C evaluated. **Recommendation: Option C**, content-row coordination (CR-1), with operation rows classified by writer kind (CR-2), content-less rows under their operation (CR-3) and location-key invariants (CR-4). Existing operation-before-content paths are analysed, not assumed away. No blocking advisory acquisition is added by Option C (D-LP-9 adds one first-acquisition key, §4.10). *Historical [3.2]:* six decision groups changing 15 transaction windows; **operative count [3.4, unchanged in 3.5]: 19 P2-8 windows, 22 Phase 2 business windows (§4.9)**. | §3.2, §4.9, §7 |
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
| **Executed (builder)** | Run by the builder outside CI, on local PostgreSQL 16 | §11.3 predicate waits; §11.4 schema extraction; §11.5–§11.7 (historical, restored in 3.5); **§11.8 activation-gate primitives [3.5]** |
| **Builder source analysis** | Read from source, schema and triggers at `d2c96f2`, not executed | §2.1–§2.5 |
| **Proposed design** | Not implemented | §3–§9 |
| **Test specification** | Not implemented | §10 |

### 0.3 Revision 3.5 response (Revision 3.5 direction §1–§14) [3.5]

| Direction item | Revision 3.5 | Where |
|---|---|---|
| §1 Preserve F5-1, F5-2, F5-3, Wh2 and earlier directions | Unchanged except editorial alignment. The REPAIR missing-content case is now explicitly not private (INGEST-only), as the sixth assessment §4 requires. | §4.9, §6.3 |
| §2 Prefer Option 1; feasibility | **Option 1 selected.** Feasibility per participant from source. Accepted code alone cannot exclude an old web process. Enforceable with a durable activation record and a two-layer database write gate (**schema requirement**, owner decision). No Option 2. | §5.6, §5.6.1, §5.6.2 |
| §3 Quiescence and activation protocol | A controlled maintenance command. Database-verified absence of sessions, then a NOWAIT barrier, the audit, an epoch publication and startup eligibility. Mixed-version exclusion at three points. | §5.6.3 |
| §3.2 Restart and failure | 15 cases | §5.6.6 |
| §4 Writer inventory | E-1…E-27, per entry point | §5.6.4 |
| §5 Cancellation and resolution | Cancellation is no exception. Offline `preservation-resolve` with existing authorities A-1…A-4; A-5 (hold) and A-6 (legacy acceptance) are owner decisions. | §5.6.7 |
| §6 AU-1…AU-8 | Rewritten on the real states, outcomes and ownership facts. The SHARED_CONTENT COMMITTED case is ADMITTED-CLEANUP and completes through `finish`. | §5.6.5 |
| §7 Conflicting-identity closure | A component over all identities and AU-8 keys. Any unsafe component refuses activation as a whole. | §5.6.5 |
| §8 Control units | CU-0…CU-11, with SQL and lock order. Business-window counts (19 and 22) unchanged and kept separate. | §5.6.8 |
| §9 Admission tests | LP-17 AA-1…AA-16 | §10 |
| §10 LP-10d(ii) | Replaced by (ii-a) service-level advisory skip and retry, and (ii-b) helper/DB OPEN-key fixture | §10 |
| §11 Documentation | §11 restored verbatim from `e5bf8c0`; the §12 heading restored; §6.3 aligned; superseded tables marked; F5 matrix reconciled; R34-OPEN-1 recorded as reconciled for reviewer access; GAS-P4-004 unchanged in severity and status | §6.3, §11, §12; correction record |
| §12 Decisions | D-LP-1…D-LP-9 updated | §8 |
| §13, §14 | Documentation and bounded non-production checks only (§11.8). No production code, migration, merge or tag. | — |

### 0.4 Revision 3.6 response (Revision 3.6 direction §1–§14) [3.6]

| Direction item | Revision 3.6 | Where |
|---|---|---|
| §1 Preserve F5-1, F5-2, F5-3, Wh2 and accepted corrections | Retained; no new contradiction found | — |
| §2 R7-1 early admission fence | F1 instance lease, F2 legacy transition (OS verification, role default, then same-role termination), F3 connection identity; alternatives 1–6 evaluated; schedule showing the R7-1 cycle cannot recur | §5.7.1–§5.7.4, §5.7.6 |
| §3 R7-2 logical-step fencing | Step admission; the epoch cannot change while any compatible process lives (F1); connection loss, reconnect, crash and shutdown defined; coverage-policy corrections to E-1…E-27 | §5.7.5, §5.7.6 |
| §4 R7-3 one maintenance authority | MA = F1 exclusive + controller advisory (7340, 2) + durable OPEN run; binding of action transactions; lifetimes; failure; diagram | §5.7.7 |
| §5 R7-4 durable resumable resolution | Action journal (4 new tables), per-window rows committed with their windows, resume from business state, foreign-owner revalidation just before keyed release, byte classes corrected | §5.7.8 |
| §6 R7-A1…A4; AU-1…AU-8 | Rebuilt on writer behaviour (initial generation g₀ by creator; historical vacated; UNRESOLVED; trusted partial-prefix evidence; destruction U roles; bound Q-destination U) | §5.7.9 |
| §7 A-5 | Restricted to INGEST and REPAIR in PROMOTING or PROMOTED; CAS; attribution; target, high water and ownership unchanged; still an owner decision | §5.7.8, §8 |
| §8 A-6 | **Deferred**; no Unicode rule proposed | §5.7.8, §8 |
| §9 Disabled download | E-11 read-mode exception with authorisation precedence | §5.7.10 |
| §10 Control architecture and PostgreSQL claims | CU-0…CU-21; trigger installation contract; table count 47 verified by catalog, not assumed; sequence, DDL, locking-`SELECT` and filesystem limits; narrowed claims | §5.7.4, §5.7.11, §5.7.12 |
| §11 Tests | LP-17 rebuilt (AA-1…AA-24, including every mandatory scenario); LP-10d (ii-b) split into a coordinator test and a raw-key characterisation | §10 |
| §12 Reconciliation and decisions | Seventh-assessment matrix in the corrections record; §8 grouped A–E; business windows 19 / 22 unchanged; control units counted separately | §8; correction record |
| §13, §14 | Documentation and bounded builder checks only (§11.9) | — |

### 0.5 Revision 3.7 response (Revision 3.7 direction §1–§13) [3.7]

| Direction item | Revision 3.7 | Where |
|---|---|---|
| §1 Preserve F5-1…F5-3 and Phase 2/3 behaviour | Retained; no new contradiction found | — |
| §2 R8-1 lease identity | Installation ID; a fixed per-platform lease root with one object per installation; database and Archive ID binding; duplicate-identity refusal; lifetime, replacement, link and alias rules; launcher and service-account contract; Q-36-1 kept as an owner decision | §5.8.1 |
| §3 R8-2 continuous legacy exclusion | L1…L12 with the owner-approved freeze (D-LP-8 (xi)); automated aids (masking, the required configuration key, two OS verifications by PID and start instant) and fail-closed refusal; corrected COMMIT → terminate → verify → publish order; counterexample under a successful transition | §5.8.2 |
| §4 R8-3(a) bootstrap | States S0…S9; order B1…B10 (+ B11 restore); crash and unknown outcomes per boundary; no ordinary admission in DRAIN_PENDING; Flyway and `SchemaMigrator` reconciled | §5.8.3 |
| §5 R8-3(b) identity handover | No session-level declaration on maintenance connections; per-transaction `maintenance_begin` from the committed singleton; a closure sequence without GA001 | §5.8.4 |
| §6 R8-4 controller loss | F1 is the ultimate authority; point checks are not tokens; latch; bounded continuation; drain by `(pid, backend_start)`; resume reconciliation; single active phase; bound maintenance admission; narrowed waits | §5.8.5 |
| §7 R8-5 journal and replay | R36-5 withdrawn; stable keys; attempt, observation and reconciliation records; the 9-step unknown-COMMIT algorithm; filesystem matrix; A-1…A-5 reconciliation; A-3 atomic final transaction kept; A-6 inoperative | §5.8.6 |
| §8 R8-6 audit | Targetless pre-plan and BLOCKED branches; recorded-absent intents; transition-specific quarantine pointer (source-checked); constructor scope limited to the eight identity columns | §5.8.7 |
| §9 Other corrections | Diagnostic startup kept; effect-free commands; exact disabled exceptions; 48 gated relations; statement-trigger semantics; schema invariants; restore and rewind limits; inoperative markings | §5.8.8, §5.8.9 |
| §10 Tests | LP-17 3.7 amendments; LP-10d (ii-b1) reachability resolved as helper-level | §10 |
| §11 Decisions and assumptions | §8 regrouped: Q-36-1 and D-LP-8 (xi) explicit and unapproved; (v-a/b/d) pending; (vi) conditional; (vii) deferred; (x) optional; D-LP-9(a) separately eligible; smallest alternatives | §8, §5.8.10, §5.8.12 |
| §12, §13 | Documentation only; no new execution (§11.10) | — |

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
| Phase 2 changes | Most preservation writers, plus the triggers (they would have to lock in a defined order instead of by predicate). | Few now, but every new preservation writer needs a new proof; the I1/I2 and DP paths cannot be shown terminal (they hold Ct and then write G and O). | *Historical [3.2]:* six decision groups P2-8a…f changing 15 transaction windows, plus D-LP-6a and 6b (**operative [3.4/3.5]: 19 P2-8 windows; 22 Phase 2**, §4.9); all of the form "lock Ct FOR UPDATE first". No trigger changes. No advisory waits added by Option C. |
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
  P2-8f). The guard records MISSING and the window proceeds under CR-3 (the operation's advisory lock).
- **[3.5] INGEST only** (sixth assessment §4). The MISSING outcome is accepted as private **only when the operation's
  kind is INGEST**. For REPAIR or DESTROY a missing content row is malformed: the window refuses with no
  filesystem change, the step fails visibly (BLOCKED STATE_UNKNOWN, recorded), and AU-2 refuses activation for such
  rows. This replaces 3.4's "no branch on an operation-kind label".
- A not-yet-committed content row is invisible, so the statement never waits for it.

**Count [3.4, operative].** The seven decision groups (P2-8a…g) change **19 transaction windows**:
- the 15 of revision 3.2;
- **We7** (download MISSING);
- **Wh1a** (`planRepair` reservation);
- **Wh1b** (`quarantinePartialAndReplan` reservation; a distinct window that shares Wh1a's allocation design);
- **Wh2** (`createOwned` OPEN check).

**Phase 2 total: 22 changed windows**: 19 under P2-8a…g, plus 3 under D-LP-3, D-LP-6a and D-LP-6b. **[3.5]**
Admission and activation are **control units**, inventoried separately (§5.6.8, CU-0…CU-11; **[3.6] replaced by
§5.7.11, CU-0…CU-21**); they are not part of these 22 business windows.

*Historical, not operative:* **Count [3.3].** The **seven** decision groups (P2-8a…g) change **18 transaction windows**:
- the 15 listed below;
- **We7** (download MISSING, F4-3);
- **Wh1** and **Wh2** (P2-8g, F4-1).

*Historical, not operative:* **Revision 3.2's count was:** the six decision groups (P2-8a…f) change **15 transaction windows** (marked **CHANGED**):
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
| 1 | `SELECT … FROM gas.file_content WHERE content_id = :x FOR UPDATE` | **Ct(X) U**; the first lock. If no row: Wh1a rejects (the content must exist for a repair); Wh1b proceeds as private **only if the operation is an INGEST [3.5]**; a REPAIR without persisted content is refused (BLOCKED STATE_UNKNOWN, nothing created). |
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
  record exists. Otherwise CREATE_NEW and the claim behave as today. **[3.5]** Their admission is governed by §5.6
  (AU-7: a live duplicate refuses activation as a whole).

**Not a cure for existing owners:** Wh2 governs **new** claims only. Operations already owning a path when the
protocol is enabled (for example a PROMOTED repair at an OPEN path) are classified by the admission audit
(§5.6.5, AU-4 (b): UNSAFE-ACTIVE), which refuses activation as a whole [3.5].

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
| **Settlement, attested source** (s = u.path) **[3.4]** | INV-U stays withdrawn. A committed claimant at s is one of: **(a)** the entry's own operation (advisory held); **(b)** a **final** operation's stale claim (destruction refusal), whose row has only terminal writers (A0, A1); **(c)** an **active** claimant that made its claim **after enablement**, impossible because Wh2 refuses new claims while the OPEN record exists, and the record stays OPEN until Wa2 commits; **(d)** an **active or resumable claimant that already existed at enablement**: **[3.5]** the audit classifies it UNSAFE-ACTIVE (AU-4 (b), AU-5) and **activation is refused as a whole**, so no settlement runs anywhere (§5.6.9). **Generation reservations (Wh1a, Wh1b)** make (c) unreachable through planning as well. **Trigger-driven ownership changes** (claims, releases, retirements) are covered by F1, PO-C and the window inventory. **Deferred COMMIT checks** are reads. **Class: conditional on P2-8g and the §5.6 activation policy [3.5]** (proposed design); awaiting LP-15 and LP-17. |
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
reuse of an empty unrecorded path is harmless. **[3.5]** AU-7 classifies such cases (§5.6.5): a duplicate that
can still create or holds its target refuses activation; terminal duplicates are history.

### 5.5 F5-4: fail-closed admission and activation [3.4] — **historical, superseded by §5.6 in 3.5**

> **Historical, not operative.** The sixth assessment found this design insufficient (F5-4 OPEN):
> - Its activation premise ("the same process being upgraded") is false for separate web, worker and CLI
>   processes.
> - Its isolation table omitted the direct RecoveryService loop and other entry points.
> - Its allowed cancellation runs the very settlement it disabled.
> - Its isolation did not follow every conflicting identity.
> - Its AU-2 rejected a valid SHARED_CONTENT COMMITTED ingest.
>
> Revision 3.5 withdraws it in favour of §5.6. The text below is kept unchanged for traceability only.


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

### 5.6 F5-4: globally quiesced, fail-closed preservation activation [3.5, replaces §5.5; **partly superseded by §5.7 in 3.6**]

**Evidence class:** proposed design, with source analysis at `fe197ed` and bounded builder-local PostgreSQL 16
checks (§11.8). Nothing here is implemented.

**Selected architecture: the sixth assessment's Option 1.** Preservation is enabled globally or not at all.
- Activation is a **controlled maintenance operation**, run by an operator from the CLI while every service is
  stopped. It is never an automatic startup action.
- The audit runs once, under a database barrier that no writer can pass.
- If any unsafe component exists, nothing is enabled; resolution is offline and reviewed (§5.6.7), then the audit
  is re-run.
- Revision 3.4's process-local isolation (§5.5) is **withdrawn as a safety authority**. There is no partial
  isolation, isolated-content set or held-for-review flag.

**Schema consequence (stated, not assumed away).** Option 1 is enforceable on the actual deployment only with a
durable activation record and a database write gate (§5.6.2). That is a schema change in the authorised
implementation (a future migration, not written). It is an explicit part of owner decision D-LP-8 (§8). The
reason is the feasibility finding below; it is not a live partial-isolation system (Option 2).

#### 5.6.1 Feasibility on the actual processes (direction §2.1)

Source at `fe197ed`. "Mutating" means the process can write preservation rows or archival-volume bytes.

| Participant | How it runs (source) | Mutating? | What accepted code does against a newer schema | Excludable by |
|---|---|---|---|---|
| Web (`serve`) | `ProcessLauncher.startWeb` → `WebApplication`; one context per process; any number of web processes is possible | **Yes**: uploads, repair uploads, retry, cancel request, unresolved release/attest, reverify and scan requests, destroy, download MISSING recording, volume administration | **Keeps serving.** Only `HomeController` checks `SystemStatus.ready()`. No interceptor or filter gates requests on schema state. `IngestionService.readiness` checks the ingestion volume and staging, **not** the schema. The readiness health group reports OUT_OF_SERVICE but does not stop handlers. | **Not by accepted code.** Needs the write gate (§5.6.2). |
| Worker (`worker`), one or many | `startWorker` → `WorkerProcess`; each has its own worker id, presence row and `JobRunner` | **Yes**: every preservation job kind; recovery | **Refuses to start** (`WorkerProcess.start` requires `ready()`; `SchemaMigrator.status` reports AHEAD for unknown migrations because `ignoreMigrationPatterns` is empty and `outOfOrder` is false). A worker that is **already running** stops recovery and job claims at its next heartbeat check (default 60 s). A job already running continues to its end. | Startup: accepted code. Running: drain evidence (§5.6.3) plus the write gate. |
| Multiple workers | `JobRunner` claims by registered kind with `FOR UPDATE SKIP LOCKED` and lease tokens; another worker reclaims an expired lease | via jobs | A lease or presence row says nothing about whether the process is alive | Session evidence and the write gate, not presence |
| Receivers and ingestion | inside the web process: `IngestionService.receive` streams to staging; the instance timer `renewReceivers` renews leases | Yes (staging files, RECEIVING rows) | as web | as web |
| Recovery | `RecoveryService.runOnce`, called only by `WorkerProcess.recover()` when the heartbeat said ready | Yes (P15, P16, `reconcileAll`, every `needingAttention` operation, staging cleanup) | as worker | as worker |
| Administrative preservation actions | web requests; worker jobs (`JOB_CANCEL`, `JOB_QUARANTINE_ATTESTED`, `JOB_REVERIFY`) | Yes | as web and worker | as web and worker |
| CLI | `Cli` commands, each its own short-lived process and pool (application name `genealogy-archive-<command>`) | `migrate` (DDL), `init-archive`, `create-admin`, `register-volume`, `init-volume`, `set-ingestion-volume`; the rest read only | The volume commands and `create-admin` check `ready()`; `init-archive` checks the schema is current | accepted checks plus the write gate |
| Deployment | Separate web and worker processes per instance (Windows service or Linux unit per installation); one configured database role (`DatabaseSettings`); Hikari keeps `minimumIdle = 1` per pool, so a running process normally holds at least one visible session | — | — | operator stop, verified by the database (§5.6.3) |

**Read-only versus mutating.** No process type is read-only by construction: every web and worker process can
mutate. The design therefore makes **old builds read-only at the database** (§5.6.2, layer A). An old web process
may still serve reads; it can no longer write or lock rows.

**Conclusion.**
- With accepted code alone, Option 1 is **not enforceable**: an old web process keeps serving preservation writes
  against a newer schema, and a stopped-service checklist cannot be verified from inside the database.
- With the durable activation record, the two-layer write gate and the database-verified quiescence of §5.6.3,
  Option 1 **is enforceable and testable** (§10 LP-17).
- No alternative is substituted. Option 2 (live partial isolation) is not proposed.

#### 5.6.2 Durable activation authority and write gate (schema requirement; proposed, not written)

**Tables** (new; every row insert-only except the singleton; nothing rewrites existing preservation data):

| Table | Content |
|---|---|
| `gas.preservation_activation` (singleton) | `state` ∈ {NOT_ACTIVATED, ACTIVE, REFUSED}; `protocol_version` int; `epoch` bigint; `decided_at`; `decided_by` (operator label and the Administrator account named on the command); `run_id` uuid; `audit_digest` (SHA-256 of the audit report) |
| `gas.preservation_activation_event` | one row per publication: run id, epoch, state, protocol, time, attribution, per-check counts |
| `gas.preservation_audit_finding` | one row per unsafe finding of a REFUSED run: epoch, check (AU-n), component id, the referenced rows (table, key), detail |
| `gas.preservation_resolution` | one row per offline resolution decision (§5.6.7): component, action, rows, before/after values, evidence, attribution, reason |

The migration that adds them sets the singleton to (NOT_ACTIVATED, protocol 1, epoch 1). Preservation protocol 1
is the protocol of this proposal (P2-8a…g, D-LP-3, 6a, 6b, 9). A later migration that changes the preservation
protocol resets the singleton to (NOT_ACTIVATED, new protocol, epoch + 1).

**Layer A: old builds are read-only.** The same migration runs
`ALTER ROLE CURRENT_USER IN DATABASE <current database> SET default_transaction_read_only = on` (an ordinary role
may set its own defaults).
- Accepted builds never override it: no `setReadOnly`, `BEGIN READ WRITE` or session characteristic exists in
  `src/main/java` (source search).
- Every write **and every locking read** (`SELECT … FOR UPDATE/SHARE`) of an old build fails at statement start with
  SQLSTATE 25006, **before** any row lock (§11.8 (c)). Plain reads still work.
- Session advisory try-locks still work, but they never wait. A held one makes a new-protocol `tryLock` report
  "busy", which is the accepted retry outcome.
- New builds open every connection with the startup options
  `-c default_transaction_read_only=off -c gas.preservation_protocol=<P> -c gas.activation_epoch=<E>`.

**Layer B: protocol and epoch gate.** A `BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE … FOR EACH STATEMENT` trigger
on **every** application table in `gas` (the 40 tables at V0025, §11.4 and §11.8 (f), plus the four new ones;
`flyway_schema_history` excluded):
- It compares `current_setting('gas.preservation_protocol', true)` and `current_setting('gas.activation_epoch',
  true)` with the singleton.
- It raises SQLSTATE `GA001` ("write refused: this process's build or activation epoch does not match the archive's
  activation") on any difference.
- It reads the singleton with a plain `SELECT`, so it never waits on the activation barrier. **[3.7: narrowed: compatible with EXCLUSIVE B0 only; it can wait behind ACCESS EXCLUSIVE DDL (§5.8.5)]**

Layer B refuses writes by:
- a new-build process started under an earlier epoch (one that stayed undetected during activation);
- a build of another protocol;
- a session that forgot the options.

Locking reads of such a process are not refused by layer B. They come from new-protocol code (same order rules), or
from a different-protocol build, which a protocol migration must exclude as layer A excludes accepted builds
(§12, R35-4).

**Neither layer is a security boundary.** Anyone holding the database password can set the options. The layers
prevent **accidental** mixed versions and stale processes, which is the threat the sixth assessment names.

**Startup eligibility (each new web, worker and CLI process).**
1. The process opens **one** bootstrap connection, with read_only off and the protocol option, and nothing held.
2. Its first statement: `SELECT state, protocol_version, epoch FROM gas.preservation_activation FOR SHARE`. This
   waits while an activation holds the barrier, so it reads the published result, never a half-finished one.
3. It closes the bootstrap connection and builds its pool with `gas.activation_epoch = <epoch read>`.
4. **Preservation is enabled in the process iff** `state = ACTIVE` and `protocol_version` equals the build's
   protocol. The decision is an immutable in-memory **cache** of the durable record (`PreservationAdmission`).
   Otherwise every preservation entry point refuses visibly (§5.6.4), and the status page and `status` command show
   the state and its reason.
5. The cache cannot silently go stale. The durable record changes only through activation, which requires global
   quiescence (§5.6.3) and increments the epoch. Any process that survived it undetected has every later write
   refused by layer B and fails visibly until it is restarted.

#### 5.6.3 Activation protocol (direction §3, steps 1–10) **[step-4 authority, bootstrap `FOR SHARE` wait and "never waits" wording inoperative 3.6/3.7; see §5.7, §5.8]**

Command: `activate-preservation --administrator <username> --reason <text>` (new CLI command; the
`preservation-status` command reports state, epoch, protocol and the last report).

| Step | Action | Authority and evidence | On failure |
|---|---|---|---|
| 1 Identify participants | Every web process, every worker process and every CLI process of the instance (§5.6.1). Operating-system services and units per the installation guide. | §5.6.1 table | — |
| 2 Stop new work | The operator stops all web and worker services and runs no other CLI command. | operating-system service state (operator) | — |
| 3 Drain | **Drain by termination, not completion.** Every preservation step is a durable state machine with crash recovery (Phase 2): stopped work is left in its committed state. RUNNING job leases expire; RECEIVING uploads are abandoned later by recovery; INTENDED moves are settled later. The audit classifies all of this durable state (§5.6.5). | Phase 2 recovery design | — |
| 4 Verify absence | Inside the activation transaction: (a) the activation-table barrier; (b) **no other client session** on the database (`pg_stat_activity`; a row whose `backend_type` is hidden is counted as a client); (c) no `worker_presence` heartbeat newer than twice the heartbeat interval (supplementary, never sufficient alone); (d) every gated table locked `EXCLUSIVE NOWAIT`; (e) `pg_stat_clear_snapshot()` and check (b) again (the statistics view is cached per transaction, §11.8 (d)); (f) layer A's role setting and layer B's trigger present on every table. | the database: sessions and locks | any check fails → ROLLBACK; nothing published; the report names the sessions, presence rows or busy table |
| 5 Audit | AU-1…AU-8 (§5.6.5) as reads in the same transaction, after the locks. With EXCLUSIVE held, no other transaction can write or row-lock any audited table, so the READ COMMITTED statements see one stable state. | the locks | an exception → ROLLBACK; nothing published |
| 6 Unsafe found | Publish **REFUSED**: findings rows plus the event row. Preservation stays disabled everywhere. Resolution is offline (§5.6.7). | — | — |
| 7 Publication boundary | `UPDATE` the singleton to ACTIVE (or REFUSED) with `epoch = epoch + 1`, `run_id`, digest and attribution; insert the event row; COMMIT. The commit is the boundary. | the epoch | COMMIT fails → nothing published; unknown → step 10 |
| 8 Start participants | The operator starts the services. Each bootstraps (§5.6.2) and enables preservation only for ACTIVE plus a matching protocol. | startup eligibility | a mismatch → disabled, visibly |
| 9 Late joiners | An accepted build is read-only at the database (layer A); an accepted worker also refuses to start (AHEAD). A new build from an earlier epoch has its writes refused (layer B). | layers A and B | refused with 25006 or GA001, visibly |
| 10 Failure or unknown outcome | Every failure before COMMIT leaves the previous state (NOT_ACTIVATED or REFUSED: disabled) or ACTIVE at the old epoch. A lost connection at COMMIT is resolved by `preservation-status`: the event row with this `run_id` either exists (published) or not (not published). Never assume either way. | durable rows | fail-closed |

**Activation transaction (SQL sketch; READ COMMITTED; the session options carry the current epoch E):**

```sql
BEGIN;
LOCK TABLE gas.preservation_activation IN EXCLUSIVE MODE NOWAIT;          -- barrier B0: never waits
SELECT state, protocol_version, epoch FROM gas.preservation_activation;   -- must equal E read at command start
SELECT pid, usename, application_name, backend_type, xact_start            -- 4(b): must return no row
  FROM pg_stat_activity
 WHERE datname = current_database() AND pid <> pg_backend_pid()
   AND (backend_type = 'client backend' OR backend_type IS NULL);
SELECT worker_id FROM gas.worker_presence WHERE last_seen_at > now() - :twiceHeartbeat;   -- 4(c): must be empty
LOCK TABLE gas.<t1>, gas.<t2>, … IN EXCLUSIVE MODE NOWAIT;                 -- 4(d): every gated table, fixed order
SELECT pg_stat_clear_snapshot();  -- then 4(b) again: must return no row
-- 4(f): pg_db_role_setting holds default_transaction_read_only=on for (current_user, current_database());
--       pg_trigger holds the gate trigger on every gated table
-- 5: AU-1 … AU-8 reads (§5.6.5)
UPDATE gas.preservation_activation
   SET state = :result, protocol_version = :P, epoch = epoch + 1, run_id = :run,
       decided_at = now(), decided_by = :who, audit_digest = :digest
 WHERE epoch = :E;                                                         -- exactly one row
SET LOCAL gas.activation_epoch = :E1;                                      -- E + 1, for the inserts below
INSERT INTO gas.preservation_activation_event (…) VALUES (…);
INSERT INTO gas.preservation_audit_finding (…) …;                          -- REFUSED only
COMMIT;
```

The command uses **exactly one connection** (no pool), so it never counts a session of its own in 4(b), and it
reads E on that connection before `BEGIN`. A superuser or other-role session (for example a DBA's `psql`) is counted
in 4(b), and its writes are refused by layer B because it lacks the options. Layer A applies only to the configured
role.

**Lock-order position.** Activation is its own top level, **L-ACT, before L0**.
- It runs on a dedicated session holding nothing else.
- Every acquisition is NOWAIT, so activation **never waits** and cannot be part of a wait-for cycle.
- Bootstrap reads wait on it while holding nothing.
- The layer B trigger reads without conflicting.
- A writer that already holds any gated-table lock makes step 4(d) fail; activation is then refused, not delayed.
  This meets the sixth assessment's requirement that an activation lock precede every resource its barrier could
  wait on.
- **Autovacuum** can hold a conflicting table lock. NOWAIT then refuses the run, and the operator retries. This is
  a visible, bounded inconvenience, not a safety issue.

**Mixed-version prevention (direction §3.1).** The sixth assessment's counterexample, old worker B and new process
N, is excluded at three independent points:
1. **B cannot start** against the migrated schema (accepted AHEAD check).
2. **A running B is detected:** its pooled session fails step 4(b), so N's activation is never published.
3. **An undetected B** (no session at the check, for example while its pool reconnects) is read-only (layer A).
   - Its `createOwned` fails at its first statement (`Ct FOR SHARE` is a locking read, 25006), before CREATE_NEW.
   - Its `commitRepair` fails at its first `transition` UPDATE.
   - So B neither creates bytes through `createOwned`, nor claims, nor holds an O lock while waiting for Ct.
   - The Ct/O cycle needs B to hold O(B) and wait on Ct(X), which a 25006 refusal prevents.

Audit success is **not** used as evidence of exclusion; points 1–3 are.

#### 5.6.4 Complete preservation-writer inventory (direction §4)

Every entry point calls one admission check, `PreservationAdmission.require()`, **before its first effect**
(database write, staging write or volume access). Delegated helpers **never** check again: they run only inside an
admitted entry and therefore inherit its decision. Process types: **Web**, **Wk** (worker), **CLI**. Window and lock
references are the §2.4 rows (P, D, Q, U, I, V) and the §4.9 windows. Behaviour **while disabled** means state is not
ACTIVE or the protocol does not match.

| # | Entry point (caller) | Process | Identities | Transactions and locks (ref.) | Filesystem effects | While disabled | Restart / recovery |
|---|---|---|---|---|---|---|---|
| E-1 | `IngestionService.startBatch` (`FileController` POST batches) | Web | batch | batch insert | none | refused: "Preservation is not activated on this archive (state, reason). Nothing was changed." | — |
| E-2 | `IngestionService.receive` (upload item) | Web | O (new), planned Y | P13 insert; P14 heartbeat and `transition`; enqueue | staging write | refused before the insert; no staging file | RECEIVING rows from a stopped receiver are abandoned by recovery (P15) after activation |
| E-3 | `IngestionService` instance timer `renewReceivers` | Web | own RECEIVING set | P14 (A2) | none | the set is empty (E-2 refused) | — |
| E-4 | `RepairService.receive` (repair upload) | Web | O (new), X | P17; P14 | staging write | refused | as E-2 |
| E-5 | `OperationAdminService.retry` | Web | O | P9 (A1); enqueue | none | refused | — |
| E-6 | `OperationAdminService.cancel` (request) | Web | O | P10 (A1); enqueue `JOB_CANCEL` | none | refused | queued cancel jobs stay queued |
| E-7 | `UnresolvedObjects.release` | Web | U, X or contentless, G, O | adv(u.op); filesystem examination; Wd1 (U1) | read (examination) | refused | — |
| E-8 | `UnresolvedObjects.attest` (fast path and Wd2) | Web | U, X, O | U2 / Wd2; enqueue | read | refused (both paths) | — |
| E-9 | `IntegrityService.requestScan`, `requestReverify` | Web | scan, X | scan insert; enqueue | none | refused | — |
| E-10 | `FileService.destroy` | Web | File, replacement, X, repairs, new DESTROY O | D1 (D-LP-6a); enqueue | none | refused | — |
| E-11 | `FileService.download` | Web | File, X, G | read; We7 (MISSING); E2 (SIZE_MISMATCH) | read | **bytes served only when the observation verifies; otherwise the accepted "cannot be read right now. Nothing was changed; try again later." message, with no finding or state change** (behaviour change, D-LP-8) | — |
| E-12 | `VolumePreservationService.initializeVolume`, `setIngestionVolume`; CLI `init-volume`, `set-ingestion-volume`, `register-volume` | Web, CLI | volume, archive | V1 | volume layout directories | **permitted** (no content, operation, unresolved, quarantine or owner row; outside L1) | — |
| E-13 | `OperationsController` job retry (`/operations/jobs/{id}/retry`) | Web | job | job row | none | refused for preservation job kinds; permitted otherwise | — |
| E-14 | `JobRunner` handler registration (`PreservationJobs`) | Wk | per job | — | — | the preservation kinds (INGEST, REPAIR, DESTROY, INTEGRITY_SCAN, CANCEL, QUARANTINE_ATTESTED, REVERIFY) are **not registered**, so they are never claimed and stay queued. THUMBNAIL (cache files only; no database write) stays registered. | expired leases are reclaimed after activation |
| E-15 | `OperationProcessor.process` (`JOB_INGEST`, `JOB_REPAIR`, recovery) | Wk | O; X or private Y; G; U; Q; owner rows | plan; Wh1a; tryShare (E2 NOT_PROTECTED; D-LP-6b commitShared); own INTENDED settlement (Q2, Wa2); Wh2 `createOwned`; copy; Wh1b with Wa1/Wa2; Wf1 unverified copy; commitNewContent (P5); commitRepair (P6); finish (P8 plus Wb1 for REPAIR); resume and cleanup; block, toWaiting, ioProblem (P1, P8) | staging read and delete; CREATE_NEW; rename to quarantine | not reachable (E-14, E-21) | resumes from the committed state |
| E-16 | `DestructionProcessor.process` (`JOB_DESTROY`, recovery of CLEANUP_* and WAITING DESTROY) | Wk | DESTROY O, X, every writer of X, U, G, Q | D2…D7 (Wc1, Wc4, Wc5, Wc6) | deletes of authorised objects | not reachable | as E-15 |
| E-17 | `IntegrityService.run` (`JOB_INTEGRITY_SCAN`) | Wk | scan, X, G, findings, U | We1–We4; E1 (We6); E2 (I5); `observeUnresolved` | read | not reachable | rescans after activation |
| E-18 | `IntegrityService.reverify` (`JOB_REVERIFY`) | Wk | X, findings | I4 (We4) | read | not reachable | — |
| E-19 | `OperationAdminService.performCancel` → `applyCancel` (`JOB_CANCEL`) | Wk | O, X or private, Q, U, owner rows (by location) | adv(O) try; pending INTENDED settlement (Q2/Wa2); OperationCopies; `move` (Wa1, rename, Wa2); Wf2; final transition (P11) | rename; read | not reachable | queued |
| E-20 | `UnresolvedObjects.quarantineAttested` (`JOB_QUARANTINE_ATTESTED`) | Wk | U, X, O, Q | adv(u.op); U3 (Wa1, rename, Wa2) | rename | not reachable | queued |
| E-21 | `WorkerProcess.recover` → `RecoveryService.runOnce` | Wk | all | P15 (`abandonStaleReceiving`); **P16, the direct `presentButRecordedMissing → setState` loop** (E1: Ct NKU, then triggers on G and other O); `QuarantineMoves.reconcileAll` (Q2 under a tryLock of each entry's operation); the `needingAttention` loop → E-15 or E-16; `cleanStaging` (P14b) | staging delete; renames (settlement) | **`recover()` is not called at all** (the admission check is at the head of `recover()`, before `runOnce`, so P16 and `reconcileAll` cannot run) | runs on the first heartbeat after an ACTIVE start |
| E-22 | indirect state changes from E2 findings | Wk | X | an open MISSING finding later converted by P16 | — | no E2 finding is written while disabled (E-11; E-17 unreachable); P16 is unreachable | after activation, P16 runs under the new protocol (E1) |
| E-23 | CLI `migrate` | CLI | schema | DDL; installs layers A and B | none | runs in the maintenance procedure only (services stopped) | — |
| E-24 | CLI `init-archive`, `create-admin` | CLI | archive, user | existing | none | permitted (not preservation; new-build options) | — |
| E-25 | CLI `activate-preservation`, `preservation-status`, `preservation-resolve` | CLI | all | §5.6.3, §5.6.7 | read (resolution may move to quarantine, §5.6.7) | these are the maintenance entries | — |
| E-26 | `FileService.correct`, `delete`, `restore`; capture sessions, records, catalog, users (Phases 0–3) | Web | File, Phase 3 rows | D-LP-3, D-LP-5, D-LP-7, D-LP-9 windows | none | **permitted**. They write no Ct, O, U, Q, G or owner row, and they follow the new order rules (they are new-build code). | — |
| E-27 | Helpers: `QuarantineMoves.move`, `settle`, `reconcileAll`; `OperationCopies.check`; `Presence`; repository `setState`, `transition`, `note`, `recordIfAbsent`, `insertFindingIfNew`; `ctx.enqueue` | any | — | the caller's windows | the caller's | never entered except from an admitted entry; an ArchUnit rule restricts their callers to E-1…E-25 (LP-17 AA-7) | — |

"An Administrator invoked it" is never an admission reason (direction §4). The only maintenance-time exception is
the offline resolution tool (§5.6.7), which runs under the same quiescence as activation.

**An admitted operation cannot continue across a boundary change.** The boundary changes only when activation
publishes, which requires quiescence. A process that survived it undetected cannot commit (layer B), so an entry
check made earlier can never authorise a write after the boundary.

#### 5.6.5 Audit AU-1…AU-8, corrected (direction §6)

**Operation-state facts (source: `OperationState`).**
- **Final** = {COMPLETED, ABANDONED, CANCELLED, REJECTED}. COMMITTED is **not** final, and there is **no FAILED
  state**.
- Content states: ACTIVE, DAMAGED, MISSING, DESTROY_PENDING, DESTROYED.
- Generation states: CURRENT, QUARANTINE_PENDING, QUARANTINED, MISSING, DESTROYED.

**Ownership facts.**
- An OPERATION owner row exists exactly while `target_created` is true (the claims trigger, in **any** operation
  state).
- GENERATION owner rows are inserted with the generation and never deleted (F1).

**Outcome classes.**

| Class | Meaning | Effect |
|---|---|---|
| ADMITTED | consistent | — |
| ADMITTED-STALE | a **final** operation still has `target_created`, with no conflicting identity | terminal writers only (A0, A1); admitted |
| ADMITTED-CLEANUP | COMMITTED, or a DESTROY in CLEANUP_PENDING / CLEANUP_INCOMPLETE, whose remaining work is cleanup of its own records | admitted; completes after activation |
| UNSAFE-ACTIVE | a non-final operation can still create, claim or release at a location another identity also uses | refuses activation |
| UNSAFE-AMBIGUOUS | which identity an object, claim or pending move belongs to cannot be decided from the records | refuses activation |
| UNSAFE-LEGACY | identity or provenance does not match the constructors and relations below | refuses activation ~~unless a reviewed acceptance record exists (§5.6.7, action A-6)~~ **[inoperative 3.7: A-6 deferred; no waiver exists]** |
| UNSAFE-UNCLASSIFIED | **any combination not listed in the tables below** | refuses activation (fail-closed default) |

Every check reads the locked snapshot. Paths are compared in Java with `PortableRelativePath` semantics (AU-8); no
SQL `lower()` is used. **No check repairs or rewrites anything.**

**AU-1 Generation identity.** For each generation G of content X:
- `storage_path = originalPath(X, G.generation)`;
- `G.volume_id = X.volume_id` (repairs are planned on the content's volume, `planRepair`);
- at most one CURRENT per content (unique index); exactly one when X is ACTIVE, DAMAGED, MISSING or DESTROY_PENDING;
  none when X is DESTROYED;
- `vacated` is true only on the CURRENT generation of MISSING content (V0016);
- `quarantine_path` is non-null only for QUARANTINED or DESTROYED (V0014);
- `G.operation_id` is the creator: generation 1 → `X.created_by_operation` (an INGEST with outcome NEW_CONTENT and
  `target_generation` 1); generation ≥ 2 → a REPAIR of X with that `target_generation` in COMMITTED or COMPLETED.

A mismatch is UNSAFE-LEGACY. Historical paths are never rewritten.

**AU-2 Operation target meaning** (the shared-content false positive is corrected). Classified by (kind, state,
outcome, `target_created`, ownership, live or retained). For REPAIR and DESTROY, `content_id` must name a
**persisted** content in every state. Only INGEST may name an unpersisted, private Y; there is no private fallback
for REPAIR (sixth assessment §4).

| Kind | State | Outcome | Target meaning | Required | Class |
|---|---|---|---|---|---|
| INGEST | RECEIVING, STAGED | — | none yet | `target_path` null, or (STAGED after a COLLISION retry) the earlier private plan with `target_created` false | ADMITTED |
| INGEST | PROMOTING, PROMOTED, BLOCKED, WAITING_FOR_VOLUME | — | **live private plan** | `content_id` = Y has **no** `file_content` row; `target_path = originalPath(Y, target_generation)`; `target_created` ⇒ an own OPERATION owner row | ADMITTED (AU-7, AU-8 still apply) |
| INGEST | COMMITTED | NEW_CONTENT | retained history (the claim moved to generation 1) | X persisted with `created_by_operation` = this operation; generation 1 path = `target_path`; `target_created` false | **ADMITTED-CLEANUP** |
| INGEST | **COMMITTED** | **SHARED_CONTENT** | **retained history of an abandoned private plan** | `content_id` = X persisted and ACTIVE or later; `target_path` parses as `originalPath(Y, target_generation)` with Y ≠ X, Y **not** persisted and referenced by no other row; `target_created` **false** (`tryShare` runs only at `attempts = 0`; `incrementAttempts` commits before `createOwned`, so no exclusive create has happened) | **ADMITTED-CLEANUP** (the sixth assessment's case) |
| INGEST | COMMITTED | SHARED_CONTENT | as above but `target_created` true | not produced by accepted code | UNSAFE-LEGACY |
| REPAIR | STAGED | — | none, or an earlier plan to be replaced by Wh1a | X persisted; `target_created` false | ADMITTED |
| REPAIR | PROMOTING, BLOCKED, WAITING_FOR_VOLUME | — | **live plan** | X persisted; `target_path = originalPath(X, target_generation)`; volume = X's; `target_generation` is **not** a recorded generation of X; `target_created` ⇒ own owner row | ADMITTED unless AU-4, AU-5 or AU-7 find a conflict (then UNSAFE-ACTIVE) |
| REPAIR | PROMOTED | — | **owned copy** | as above with `target_created` true and an own owner row | as above |
| REPAIR | COMMITTED | REPAIRED | retained history (the claim moved to the new generation) | the generation `target_generation` of X exists with this operation as creator and `storage_path = target_path`; `target_created` false | **ADMITTED-CLEANUP** (finish runs Wb1, then COMPLETED) |
| DESTROY | any non-final | — | no target | X persisted; `target_path` null | ADMITTED or ADMITTED-CLEANUP (CLEANUP_PENDING, CLEANUP_INCOMPLETE) |
| any | final | any | retained history | `target_created` false; or true → **ADMITTED-STALE** if the owner row is its own and no other identity aliases the path (AU-8) | ADMITTED / ADMITTED-STALE |
| any | any other combination | | | | **UNSAFE-UNCLASSIFIED** |

**AU-3 Physical-owner ledger, both directions.**
- **(a) Rows to identities.**
  - Every OPERATION owner row names an operation with `target_created`, the same `volume_id` and
    `target_path = path`.
  - Every GENERATION owner row names an existing generation with the same `volume_id` and `storage_path = path`.
- **(b) Identities to rows.**
  - Every operation with `target_created` and a non-null `target_path` has exactly one OPERATION owner row for
    itself at (volume, target_path).
  - Every generation has its GENERATION owner row.
  - V0017's backfill used `ON CONFLICT DO NOTHING`, so a legacy `target_created` operation whose path a generation
    owned can lack its row. That fails (b).
- **(c)** Kind consistency: an OPERATION row never sits at a path that a generation of any content records (F1).

Every failure is UNSAFE-LEGACY or UNSAFE-AMBIGUOUS. **Nothing is inserted or deleted.**

**AU-4 OPEN unresolved records.** For each U with `status = 'OPEN'`:
- **(a) Identity, whether or not an owner exists.**
  - `content_id` null ⇒ `generation` null (V0019 check). If `generation` is set, that generation of `content_id`
    exists on `U.volume_id`.
  - For a generation-bound record (kinds DAMAGED_COPY, OBJECT_AT_VACATED_LOCATION, CURRENT_COPY and
    QUARANTINED_COPY), U.path equals one of that generation's recorded paths (`storage_path` or `quarantine_path`).
    The exact kind-to-column mapping is taken from each kind's writers when the audit is implemented, and fixed by
    one LP-17 fixture per kind. It is not asserted here beyond source reading.
  - For OPERATION_COPY, `operation_id` names an operation whose `target_path = U.path` on that volume, with
    `content_id` equal to the operation's (or null for a private ingest).
  - `operation_id` exists. Attestation fields are paired (V0019); an attested record has `observed_sha256`.
  - Any failure is UNSAFE-LEGACY.
- **(b) Claims at (U.volume, U.path).**
  - A GENERATION owner row of U's own (content, generation): the same object (ADMITTED).
  - A GENERATION owner row of another generation or content: **UNSAFE-AMBIGUOUS**.
  - An OPERATION owner row of an operation in a **final** state: **ADMITTED-STALE**. Only terminal writers touch
    it; settlement releasing it is PO-B case (b).
  - An OPERATION owner row of a **non-final** operation, including COMMITTED, CLEANUP_*, BLOCKED and
    WAITING_FOR_VOLUME, whether U's own operation or a foreign one: **UNSAFE-ACTIVE**. Accepted code releases the
    claim in the transaction that records the copy unresolved (`claimBecomesUnresolved`), so this state means an
    interleaving the protocol cannot order.
- **(c)** A non-final REPAIR of U's content whose **live plan** (AU-2) targets U.path without a claim:
  **UNSAFE-ACTIVE** (Wh2 would refuse it, but the operation could still be resumed by an old build).

**AU-5 INTENDED quarantine entries.** For each Q with `state = 'INTENDED'`, determine its **source role** from
`reason` and relations. Exactly one role must hold:

| Role | Reason | Source relation | Evidence binding | Destination suffix |
|---|---|---|---|---|
| own copy | PARTIAL_COPY, UNVERIFIED_COPY, CANCELLED_COPY | `source_path = O.target_path`, O = Q.operation_id, same volume | OPERATION_COPY_VERIFIED with `source_size`/`source_sha256` equal to O's `expected_*` (the receiver's hash), or UNTRUSTED for PARTIAL_COPY (a prefix) | `partial-<n>`, `unverified-copy-<n>`, `cancelled` |
| attested object | ATTESTED_OBJECT | an attested U (OPEN) with `U.path = source_path`, `U.operation_id = Q.operation_id` | ADMINISTRATOR_ATTESTED with `source_sha256 = U.observed_sha256` and equal size | `att-<U id without hyphens>` |
| superseded generation (legacy) | DAMAGED_GENERATION | a QUARANTINE_PENDING generation of `Q.content_id` with `storage_path = source_path` | as recorded (UNTRUSTED allowed: settlement does not trust it) | any `[a-z0-9-]{1,40}` (legacy) |

- Q.content_id (no foreign key) equals O's `content_id` for own copies. It may be the planned private Y of an ingest.
- **Claimants at the source:**
  - the entry's own operation (own copy): ADMITTED;
  - a final operation: ADMITTED-STALE;
  - **any other non-final operation:** UNSAFE-ACTIVE (the GAS-P4-004 shape);
  - a GENERATION owner other than the bound generation: UNSAFE-AMBIGUOUS.
- Two INTENDED entries with the same (volume, source alias key) and different operations are **UNSAFE-AMBIGUOUS**.
- A missing or mismatched evidence binding is UNSAFE-LEGACY.

**AU-6 Quarantine destinations.** For each Q in INTENDED or QUARANTINED:
- `quarantine_path = quarantinePath(Q.operation_id, suffix)`, with the suffix derived from the role above (the
  `<n>` bounded by O's attempts);
- the destination is used by no other role: it is no owner row's path, no generation's `storage_path`, no
  operation's `target_path` and no OPEN U's path, under AU-8 keys;
- for QUARANTINED, every generation whose `quarantine_path` equals it is the bound generation, and every operation
  whose `quarantine_path` equals it is Q's operation;
- the ATTESTED_OBJECT linkage: an INTENDED entry's U is OPEN and attested; a QUARANTINED entry's U is RESOLVED with
  resolution QUARANTINED (settlement resolves it in the same transaction, Wa2).

A matching name alone is never sufficient. Failures are UNSAFE-LEGACY or UNSAFE-AMBIGUOUS.

**AU-7 Planned reservations.** For each persisted X, consider every REPAIR of X that **can still create or holds**
its target:
- **can create:** PROMOTING; WAITING_FOR_VOLUME with a target; BLOCKED with a target and a reason other than
  COLLISION or STAGING_INVALID (`retry` returns such an operation to PROMOTING; COLLISION returns to STAGED and
  re-plans);
- **holds:** `target_created`, in any state.

STAGED re-plans through Wh1a and cannot create its old target. COMMITTED, CLEANUP and final states without
`target_created` are history.

Two such operations with equal `target_generation`, or one whose `target_generation` is a recorded generation of X
it did not create, are **UNSAFE-ACTIVE**. Terminal duplicates are history; Wh1a's high water already dominates
them. Wh1a and Wh1b are unchanged.

**AU-8 Path aliases.**
- **Path columns:** `file_content_generation.storage_path` and `quarantine_path`; `file_operation.target_path` and
  `quarantine_path`; `quarantine_object.source_path` and `quarantine_path`; `unresolved_object.path`;
  `physical_location_owner.path`.
- **Key:** (volume_id, `PortableRelativePath.caseFoldKey()`), which is Java `toLowerCase(Locale.ROOT)` of the stored
  string.
  - Values must parse with `PortableRelativePath.of` (NFC is required and rejected, never rewritten).
  - A value that does not parse is UNSAFE-LEGACY.
  - Constructor paths are ASCII. A non-ASCII value in any of these columns is **UNSAFE-LEGACY**, because NTFS's
    per-volume upcase table is not reproduced.
- Two references with one key are **the same object** only by one of these relations:
  - generation ↔ its own owner row, its own U (AU-4 (a)), its own superseded Q, and its creating operation's
    retained target;
  - operation ↔ its own owner row, its own OPERATION_COPY U and its own-copy Q source;
  - attested U ↔ its ATTESTED_OBJECT Q source;
  - Q destination ↔ the bound generation's or operation's `quarantine_path`.
- Any other pair with one key, including two raw-equal strings and **case-only variants**, is **UNSAFE-AMBIGUOUS**.
- Retained-history targets (AU-2) take part, so an alias between a live identity and history is also refused.

**Conflicting-identity closure (direction §7).**
- Each unsafe finding seeds a **component**: the connected component of a graph over contents, operations,
  generations, U, Q and owner rows. The edges are the row references (`content_id`, `operation_id`, generation) and
  equal AU-8 keys.
- The component crosses contents (an operation of Y and a generation of X at one key), contentless U and Q
  destinations.
- Under global fail-closed activation, **any** unsafe component refuses enablement **as a whole**. Components are
  computed only to report and resolve them (§5.6.7); they never authorise partial operation.

**Legitimate states that must pass (each a fixture in LP-17):**
- a SHARED_CONTENT COMMITTED ingest stopped at AFTER_COMMIT_BEFORE_CLEANUP;
- NEW_CONTENT and REPAIRED COMMITTED operations;
- a final operation's stale claim;
- a PROMOTED repair owning its own fresh target;
- an INTENDED own-copy move interrupted after the rename;
- an attested OPEN record with an INTENDED attested move;
- a private ingest plan;
- a DESTROY in CLEANUP_INCOMPLETE;
- RECEIVING uploads of a stopped receiver.

**SHARED_CONTENT cleanup after activation (direction §6, AU-2).** The operation is ADMITTED-CLEANUP. After ACTIVE,
the first worker recovery pass (E-21) reaches it in `needingAttention` and runs `OperationProcessor.finish`:
1. the fault point;
2. `deleteStaging` (staging delete; then the P14b staging flag);
3. the `transition` COMMITTED → COMPLETED (P8, owner advisory; no Ct; `target_created` false, so no claim
   trigger);
4. the thumbnail job.

The obsolete Y target stays in the row as history. No cancellation is offered or needed (`requireCancellable`
rejects COMMITTED, which is correct). It is never isolated.

#### 5.6.6 Restart and failure cases (direction §3.2)

| Case | Outcome |
|---|---|
| Web-only restart | It bootstraps and reads the unchanged (state, epoch). ACTIVE: enabled. Workers continue. The epoch is unchanged because activation did not run. |
| One worker restarts while another runs | The same. Job leases and `SKIP LOCKED` are unchanged. |
| Process startup during active preservation work | normal: same epoch, same protocol |
| Activation attempted while any process runs | refused at step 4(b) or 4(d); nothing published; the report lists the sessions and tables |
| Process startup during an activation | its bootstrap `FOR SHARE` waits on the barrier, then reads the published state, or the old one if activation rolled back |
| Interrupted audit (crash, exception, lost connection before COMMIT) | rolled back; previous state; the operator re-runs |
| Crash before publication | as above |
| Crash after publication (before services start) | the state is the published one; services read it at start |
| Unknown completion | `preservation-status` finds or does not find the event with the `run_id`; until then, services stay stopped |
| Database restart during activation | rolled back; as interrupted |
| Restored backup | The backup carries its own singleton. Its audit property holds by construction: after its activation, only new-protocol, current-epoch writers could commit (layers A and B). A backup taken before activation restores NOT_ACTIVATED or REFUSED (disabled). A pre-migration backup restores an old schema: the new build reports BEHIND, `migrate` installs the gate, and activation is required. Byte-to-row divergence after a restore is the existing integrity and recovery concern (scans, unresolved records), unchanged here. The restore procedure must restore with read-only off; layer A is re-verified at activation and at every startup (4(f)). |
| Version-mixed rollout | Accepted builds: read-only (A); the worker cannot start. Builds of another protocol: disabled at startup (protocol mismatch), writes refused (B). Builds of the same protocol: the same order rules; allowed. |
| New process with a stale epoch (missed restart) | every write refused (GA001); it must be restarted |
| Re-admission after reviewed correction | After §5.6.7 actions, a new activation run: steps 1–10 again. ACTIVE only if the **whole** database passes. |
| Re-audit while ACTIVE (operator choice) | the same procedure; the epoch increments; services must restart |

#### 5.6.7 Offline resolution of unsafe state (direction §5)

**Policy.**
- Generic cancellation is **not** an isolation exception. While the state is not ACTIVE, `performCancel` and every
  other preservation entry is disabled (E-6, E-19).
- With ACTIVE, the audit has shown that no unsafe component exists. Cancellation is then ordinary Phase 2 behaviour
  under the new protocol (Wf1, Wf2, Wa2).

**Procedure** (`preservation-resolve`; maintenance only; no service running):
1. **Exclusion.**
   - The command performs step 4's checks (a), (b), (c) and (f) at start.
   - It holds the session advisory `pg_try_advisory_lock(7340, 2)` for its whole run (a busy key refuses a second
     resolver).
   - A process started meanwhile reads REFUSED and is disabled, so no preservation writer can compete.
2. **Component report.** It prints the full component (§5.6.5 closure): every row with state and identity, the
   audit findings, and a **read-only** filesystem observation of every component path (present or absent; size;
   SHA-256; file key; timestamps). Nothing is changed.
3. **Decision.**
   - The Administrator named on the command chooses one action per component row from the list below, with a reason.
   - Each action re-checks its precondition **inside its own transaction** (the state may have been changed by an
     earlier action).
   - Actions are **existing** Phase 2 authorities, executed by the new-protocol code in this single process.
4. **Record.** Every action inserts a `preservation_resolution` row (component, action, rows, before and after
   values, filesystem observation, attribution, reason). Where it does so today, the existing action also writes
   its operation events and history.
5. **Re-audit.** `activate-preservation`. Resolution never publishes ACTIVE itself.

**Permitted actions (no new cancellation authority):**

| # | Action | Existing authority | Precondition re-checked in the action's transaction |
|---|---|---|---|
| A-1 | Attest an OPEN unresolved record | `UnresolvedObjects.attest` | the accepted attestation rules; the observed hash equals the recorded one |
| A-2 | Release an OPEN unresolved record | `UnresolvedObjects.release` | accepted rules; **no non-final foreign claimant at U.path** (AU-4 (b)) |
| A-3 | Cancel a BLOCKED or WAITING_FOR_VOLUME ingest or repair | `performCancel` / `applyCancel` (accepted state rule) | every source its settlement or move would release (`releaseTargetsAt`) has no claimant other than the operation itself and final stale claims (AU-5); otherwise refused, and nothing changes |
| A-4 | Move an attested object to quarantine | `quarantineAttested` | as A-3 for the attested source |
| A-5 | **Administrative hold:** PROMOTING, PROMOTED or WAITING_FOR_VOLUME → BLOCKED (STATE_UNKNOWN), recorded with attribution, which makes A-3 or retry applicable | the worker's existing `block` semantics, **not** an existing Administrator action | **New authority; requires explicit owner approval** (D-LP-8 admission (c)). Without approval, a component whose resolution needs it stays refused, and preservation stays disabled. |
| A-6 **[inoperative 3.7; deferred]** | Reviewed legacy acceptance: record that a specific UNSAFE-LEGACY row is accepted as is | none (a decision record only; no data change) | allowed only for a path **outside** the constructor namespaces (`originals/…`, `quarantine/<operation id>-…`), so it can never alias a future constructor path, and only if AU-8 finds no alias for it. The next audit treats the row as ADMITTED-REVIEWED. **Owner approval required** (D-LP-8 admission (d)). |

The procedure meets the direction's seven requirements:
- **Bytes and evidence are preserved:** no action deletes or overwrites; moves follow intent-before-move with
  attested or verified evidence; the observation is recorded.
- **Competing writers are excluded:** by step 1.
- **No ambiguous foreign release:** the A-2, A-3 and A-4 preconditions.
- **Decisions and attribution are recorded:** step 4.
- **No new cancellation authority:** A-5 and A-6 are presented as decisions, not assumed.
- **Re-audit before resuming:** step 5.
- **Operations that cannot be cancelled** under accepted rules (COMMITTED, CLEANUP_*, PROMOTING, PROMOTED) are never
  forced. They are either admitted classes (COMMITTED, CLEANUP) or need A-5.

#### 5.6.8 Admission control-unit inventory (direction §8)

Separate from the business windows: **19** P2-8 and **22** Phase 2 changed business windows are unchanged and do
not include these units.

| Unit | Entry | Transaction and SQL effects | Lock order | Failure |
|---|---|---|---|---|
| CU-0 Gate installation | `migrate` (services stopped) | DDL: the four tables; the gate function and statement triggers on every table; the singleton (NOT_ACTIVATED, 1, 1); `ALTER ROLE CURRENT_USER IN DATABASE … SET default_transaction_read_only = on` | Flyway's own DDL locks; maintenance only | the migration rolls back; the schema is unchanged |
| CU-1 Quiescence initiation | operator | none (service stop) | — | — |
| CU-2 Drain verification | `activate-preservation` / `preservation-resolve` | reads `pg_stat_activity`, `worker_presence`, `pg_db_role_setting`, `pg_trigger`; `pg_stat_clear_snapshot()` | after B0 | rollback; report |
| CU-3 Barrier | `activate-preservation` | `LOCK … preservation_activation IN EXCLUSIVE MODE NOWAIT` (B0); then every gated table `EXCLUSIVE NOWAIT`, in fixed name order | **L-ACT**, before L0; never waits | 55P03 → rollback; report the busy table |
| CU-4 Audit | the same transaction | reads only (AU-1…AU-8) | under CU-3 | rollback |
| CU-5 Publication | the same transaction | `UPDATE` singleton (state, epoch + 1, run, attribution); `SET LOCAL` epoch; `INSERT` event; `INSERT` findings (REFUSED) | the singleton is held EXCLUSIVE (B0) | rollback = nothing published; unknown → CU-8 |
| CU-6 Startup eligibility | every process start | bootstrap `SELECT … FOR SHARE` on the singleton; pool options | the first statement, nothing held; waits only on B0 | wait bounded by the activation run; a timeout keeps preservation disabled, visibly |
| CU-7 Write gate | every write statement | the trigger: a plain read of the singleton; raise GA001 on a mismatch | never waits | the statement fails; its transaction rolls back |
| CU-8 Unknown-outcome resolution | `preservation-status` | reads the singleton and events by `run_id` | — | — |
| CU-9 Re-admission | `activate-preservation` from REFUSED (or ACTIVE) | CU-2…CU-5 | as CU-3 | as CU-3…CU-5 |
| CU-10 Offline resolution | `preservation-resolve` | session advisory (7340, 2) try; per action, the **existing** business window of that action (Wd1, Wd2, P11 with Wa1/Wa2/Wf2, U3) plus a private `INSERT preservation_resolution` before COMMIT | the advisory is taken first with nothing held; each action keeps its own window order; the inserted row has a fresh UUID primary key (private, G4) | the action's transaction rolls back; earlier recorded actions stand; the run reports and stops |
| CU-11 Protocol change | a future `migrate` | `UPDATE` singleton → (NOT_ACTIVATED, new protocol, epoch + 1) | maintenance | rollback |

#### 5.6.9 What this does to PO-B and the theorem

- PO-B attested-source case (d), an active or resumable claimant that existed at enablement, can no longer
  coexist with an enabled protocol: the audit refuses activation for it (AU-4 (b), AU-5).
- Cases (a)–(c) are unchanged.
- The §5 theorem is stated **only for databases with state ACTIVE** at the build's protocol. Every row in such a
  database either passed the audit or was written later by an admitted new-protocol writer (layers A and B).
- No theorem is claimed for NOT_ACTIVATED or REFUSED databases: preservation does not run there.

#### 5.6.10 Assumptions specific to admission

| ID | Assumption | Why it holds or how it is tested |
|---|---|---|
| R35-1 | Every running process of the instance has at least one database session | Hikari `minimumIdle = 1` (`DataSources`). Where false (pool reconnecting), layers A and B still exclude it. LP-17 AA-3. |
| R35-2 | The instance uses one database role | `DatabaseSettings` holds one user; layer A is per (role, database). Another role would need its own setting; 4(f) checks the configured role. |
| R35-3 | Application transactions are READ COMMITTED | stated in §4.6 and the guard; layer B evaluates per statement. A REPEATABLE READ transaction that took its snapshot before an activation could pass B with a stale read; the implementation asserts the isolation level (LP-17 AA-16). |
| R35-4 | A future protocol change provides its own old-build exclusion | layer A excludes accepted builds only. Layer B refuses writes, not locking reads, of another protocol's build. |
| R35-5 | Restores are performed with services stopped | the restore procedure; startup and activation re-verify layer A |
| R35-6 | Filesystem effects of a process killed mid-step are crash-equivalent | Phase 2 recovery design: intent-before-move, CREATE_NEW collision, unresolved records |

### 5.7 Revision 3.6: early admission fence, maintenance authority, action journal and corrected audit [3.6]

**Evidence class:** proposed design; source analysis at `9993220`; bounded builder-local checks in §11.9. Nothing here
is implemented, and none of the LP-17 tests below is claimed to pass.

**What 3.6 replaces in §5.6** (the replaced text stays in §5.6 for traceability; where they differ, §5.7 governs):

| §5.6 element | 3.6 status |
|---|---|
| Layer A as the old-build exclusion; "every old process is read-only" | **Narrowed** (§5.7.4). Layer A covers only *fresh* sessions of builds that do not override it. Existing sessions are terminated (§5.7.3), and compatible builds are fenced before their first statement (§5.7.2). |
| Step 4(b)/(c) session and presence checks as the quiescence authority | **Demoted to refusal evidence.** The authority is the instance lease (§5.7.2), plus OS verification for the one-time legacy transition (§5.7.3). |
| The bootstrap `FOR SHARE` wait on B0 | **Withdrawn.** No compatible process can start during maintenance (§5.7.2), so nothing waits on B0 and resolver connections cannot self-block. |
| A cached ACTIVE decision as the authority of a running process | **Replaced** by a decision that cannot become stale while the process lives (the epoch changes only under the maintenance authority, which needs every compatible process dead), and is revalidated on every new physical connection (§5.7.5). |
| `preservation-resolve` with advisory (7340, 2) only; the `preservation_resolution` table | **Replaced** by one maintenance authority (§5.7.7) and a durable action journal (§5.7.8) |
| AU-1…AU-8 of §5.6.5 | **Replaced** by §5.7.9 |
| A-1…A-6 | A-1…A-4 re-bounded; A-5 restricted (still a decision); **A-6 deferred** (§5.7.8) |
| E-11 "require at entry" | **Replaced** by a read-mode exception (§5.7.10) |
| CU-0…CU-11 | **Replaced** by CU-0…CU-21 (§5.7.11) |
| The "never waits", "all old sessions read-only" and "every mutation refused" wording | **Narrowed** to proved scopes (§5.7.4, §5.7.11) |

Retained from §5.6 without change:
- the global fail-closed choice (Option 1);
- the singleton, event and finding tables;
- the core of the activation transaction (B0 NOWAIT; fixed-order EXCLUSIVE NOWAIT; audit under the locks; atomic
  publication with epoch + 1, `run_id` and event; COMMIT as the boundary);
- the per-entry inventory E-1…E-27, with the coverage-policy corrections of §5.7.5;
- the disabled-state behaviour of all entries except E-11.

#### 5.7.1 Selected admission-fence architecture and alternatives (R7-1, R7-2)

**Selected (the smallest design found sound for the actual deployment):**
- **F1, instance lease.** An operating-system file lock on one lock file per Archive Instance host. Every
  compatible process (web, worker, every CLI command) holds it **shared for its whole life**, acquired **before** it
  opens any database connection or touches any volume. The maintenance authority holds it **exclusive** (try-lock
  only). The OS releases a process's lock only when that process has ended, which makes it the **proof of
  termination** for every compatible process.
- **F2, one-time legacy transition.** Accepted builds (Phases 0–3 and Phase 4 before this protocol) do not know F1.
  The first maintenance run that installs the fence:
  1. verifies with the OS that no legacy process is running (service manager plus process scan);
  2. installs layer A;
  3. **terminates every other session of the application role and waits for each to end**, so that no session
     predating layer A survives;
  4. leaves layer A to make every later fresh legacy session read-only before its first row lock.
- **F3, connection identity.** Every physical connection of a compatible process declares (protocol, epoch,
  database identity) and is **validated at creation** by a database function. A failed validation makes the
  process fence itself and exit **before any business statement** on that connection.
- **F4, layer B.** Retained only as defence in depth for DML that carries a wrong declaration.
- **F5, one maintenance authority (MA).** Shared by migration, activation, re-admission, resolution, restore
  verification and protocol change: F1 exclusive, then a controller connection holding a session advisory lock,
  then a durable OPEN run row (§5.7.7).

**Why this closes the two gaps.**
- An epoch can change only inside MA, and MA needs F1 exclusive, which exists only when **no** compatible process is
  alive on the host. So a compatible process can never observe an epoch change during its life: its start-time
  decision is valid until it exits.
- Legacy processes are verified gone once at the transition. Afterwards they are excluded at the database: they
  cannot take locks (layer A on fresh sessions), and an old worker cannot start at all (AHEAD).
- Neither guarantee depends on counting sessions or heartbeats.

**Alternatives evaluated (direction §2).**

| # | Alternative | Assessment | Decision |
|---|---|---|---|
| 1 | **Separate login role per protocol generation**; `ALTER ROLE <old> NOLOGIN`; the new build uses a new role | The strongest database-side denial for legacy builds: independent of `pg_hba` method and of client settings. It needs `CREATEROLE` (or a superuser) at each protocol transition, a second credential in installation configuration (ADR-0004: secrets come only from an environment variable or a protected file), and re-pointing of ownership or privileges. | **Rejected as default; offered as optional hardening (D-LP-8 (x)).** F2's terminate-then-read-only sequence gives the required pre-lock exclusion for the accidental scope without new privileges. If the owner wants legacy denial that also resists deliberate `READ WRITE` overrides, this is the mechanism. |
| 2 | **Session termination plus credential revocation or connection denial** during maintenance | Termination is part of F2 and CU-12 (same-role `pg_terminate_backend(pid, timeout)` needs no extra privilege, §11.9 (b)). Revoking `CONNECT` or `ALTER DATABASE … CONNECTION LIMIT 0` needs the database owner, and also shuts out the maintenance command's own later connections and any recovery after a crashed run. Password rotation depends on the `pg_hba` method, which a non-superuser cannot verify. | **Termination adopted; revocation and connection limits rejected** |
| 3 | **Database shared/exclusive admission lock** (a session advisory lock held shared by each process and taken exclusive by maintenance) | Fences only participants that take it, so it cannot fence legacy builds. Loss of the holding connection releases it **while the process may still be alive** and mid-filesystem-step, so it is not proof of termination (seventh assessment §5, Case C). | **Rejected as the lifetime fence.** An advisory lock is used only to bind maintenance action connections to a live controller (§5.7.7). |
| 4 | **Controlled deployment transition** combining the above | This is the selected design: F1 for compatible processes, F2 (OS verification, termination, layer A) once for legacy ones, F3 per connection, MA for every maintenance command. | **Selected** |
| 5 | Session enumeration plus presence heartbeats (3.5) | Observation, not a fence (assessment §5) | Kept as refusal evidence only |
| 6 | DML triggers alone (layer B) | No trigger event for locking `SELECT`, sequences, DDL or filesystem effects | Defence in depth only (§5.7.4) |

#### 5.7.2 F1: the instance lease

| Property | Specification |
|---|---|
| Object **[inoperative 3.7: replaced by the installation-specific lease, §5.8.1]** | One lock file per Archive Instance host: Linux `/var/lib/genealogy-archive/run/instance.lock` (group `genealogy-archive`, mode 0660); Windows `%ProgramData%\GenealogyArchive\run\instance.lock` (ACL: read for `NT SERVICE\GenealogyArchiveWeb` and `…Worker`; read/write for SYSTEM and Administrators). Created by the install scripts (a deployment change; D-LP-8 (v)). Its contents are irrelevant. |
| Holder | Every compatible process: `serve`, `worker` and every CLI command. One JVM opens one channel. |
| Mode | Services and ordinary CLI commands: `FileChannel.lock(0, Long.MAX_VALUE, shared = true)` on a read channel. Maintenance: `tryLock(…, shared = false)` on a read/write channel. Maintenance **never blocks** on F1. |
| Acquisition order | **The first action of `main`**, before the configuration is used to open a pool, before Flyway, before any volume access. Rule F1-0 (an ArchUnit and startup test): no `DataSource` or `ManagedVolume` can be created before the lease is held. |
| Waiting | A service that finds the lease held exclusively waits (blocking shared lock), logging "maintenance run in progress" every minute. It never times out into running without the lease. |
| Lifetime | Until the process exits. It is never released early, not even when the database is unreachable. |
| Loss | Only by process termination. A JVM cannot lose its own lock while it lives: there is no unlock call, and the channel is never closed (Linux `fcntl` locks are released if **any** descriptor of the file is closed, so the file is opened exactly once per JVM). |
| Release | By the OS at exit, including SIGKILL, a crash or `TerminateProcess`. Windows may release a lock some time after process end; maintenance's try-lock then fails and retries, which is never early. |
| Proof obligation | F1 proves the termination of **compatible processes on this host** only. A process on another host, or one started from a build without F1, is outside it (§5.7.3, R36-1). |
| Scope assumption | One host per Archive Instance: the installation configuration is per host (ADR-0004), and services run under the host's service manager (ADR-0002, ADR-0010). A second host using the same database is outside the v1 deployment and is **not fenced**. This needs the owner's confirmation (spec question Q-36-1). MA also refuses when an application-role session from another client address is present (CU-12). |

**Startup sequence of a compatible process (CU-14):**
1. Take the F1 shared lease.
2. Load the configuration.
3. Open one bootstrap connection and read the singleton (state, protocol, epoch, cluster `system_identifier`,
   database OID), checking that **no OPEN maintenance run** exists (§5.7.7).
4. If an OPEN run exists or the database identity differs from the published one (§5.7.12), refuse to start, with
   the reason.
5. Build the pool with the connection identity of F3.
6. Enable preservation iff the state is ACTIVE and the protocol matches; otherwise run in disabled mode.

Because F1 is held, the values read in step 3 cannot change while the process lives (§5.7.5).

#### 5.7.3 F2: one-time legacy transition (accepted builds → protocol 1)

Run as maintenance command `transition` (CU-13), inside MA (§5.7.7). Order matters:

| Step | Action | Evidence / authority |
|---|---|---|
| T1 | The operator stops the services (systemd units `genealogy-archive-web/worker`; WinSW services `GenealogyArchive-Web/Worker`). | — |
| T2 | **OS verification of legacy termination.** (a) The service manager reports both units or services stopped (`systemctl is-active`; SCM `QueryServiceStatus`). (b) Process scan with `ProcessHandle.allProcesses()`, run with administrative OS rights. Linux: refuse if any process's command line names the installation's application jar or launcher. Windows: arguments of other processes are generally unavailable to Java, so refuse if any WinSW wrapper of the instance is running, **and** list every running Java runtime with user and start time; each listed PID must be stopped or explicitly confirmed unrelated (`--confirm-unrelated <pid>`), and the confirmation is recorded in the journal with OS user and time. | OS. A missing database backend or an expired heartbeat is **not** used as evidence. |
| T3 | Acquire MA (F1 exclusive; controller connection; advisory). | §5.7.7 |
| T4 | Migration (CU-0): tables, gate function and triggers (ENABLE ALWAYS), connection-identity function, and `ALTER ROLE <configured role> IN DATABASE <db> SET default_transaction_read_only = on`, **all in one Flyway transaction**, so the role default rolls back with the DDL. `lock_timeout` is set; DDL can wait on a stray session, and the timeout turns that wait into a visible rollback. | the role sets its own default (allowed for an ordinary role, §11.8 (c)) |
| T5 | **Terminate every other backend of the application role** in this database except the run's own: `pg_terminate_backend(pid, 5000)` until none remain (`pg_stat_clear_snapshot()` before each re-read). Sessions of other roles are **not** terminated; they refuse the run with a list, and a DBA closes them. Superuser sessions likewise. Application-role sessions from a non-local client address refuse the run (Q-36-1). | same-role termination needs no extra privilege (§11.9 (b)) |
| T6 | Activation audit and publication (CU-4…CU-6). | §5.7.9 |
| T7 | Release MA (CU-11); the operator starts the new services. | — |

**Why T4 precedes T5.** A legacy session that existed before T4 is read-write and stays read-write (§11.9 (a)). T5
ends it. Any reconnect after T4 is a fresh session, which layer A makes read-only. So when T7 admits the first
compatible business writer (metadata or preservation, ACTIVE or REFUSED), **no legacy session able to take a row
lock exists**. This covers the pre-ACTIVE metadata state the direction names.

#### 5.7.4 Layers A and B: narrowed claims and installation contract

**Layer A** (`default_transaction_read_only = on` for the configured role in this database):
- **Proved scope.** A *fresh* session whose client does not override the default can take no row lock (`SELECT … FOR
  UPDATE/SHARE` refused with 25006 before execution) and perform no DML. Plain reads and advisory try-locks still
  work.
- **Client premise** (to be verified with the actual driver and pool on PostgreSQL 16 and 18, Linux and Windows,
  LP-17 AA-3):
  - Accepted builds never issue `SET … READ WRITE` or `setReadOnly`.
  - `JdbcUrls` rejects the `options` URL parameter.
  - Hikari issues `setReadOnly` only when its configured read-only flag differs from the connection's.
  - pgjdbc's default read-only mode sends no session-level SQL.
- **Not covered:**
  - existing sessions (terminated by T5 or CU-12 instead);
  - clients that override the default (compatible builds do so deliberately; a deliberate legacy override is out of
    scope);
  - filesystem effects.
- Installed for the **configured application role** (named in the installation configuration), not
  `CURRENT_USER` of a maintenance role. Verified at CU-12 and at each startup from `pg_db_role_setting`.

**Layer B** (gate trigger), narrowed:
- `BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE … FOR EACH STATEMENT`, **`ENABLE ALWAYS`**, so it also fires
  under `session_replication_role = replica`.
- Function `VOLATILE`, `SECURITY INVOKER`, with `SET search_path = pg_catalog, gas`.
- It reads the singleton at **each execution**, so prepared statements are re-checked; a prepare-time authorisation
  is never reused.
- **Fail-closed:** a missing, empty, invalid or mismatched setting raises GA001, and so does a missing singleton.

It covers:
- statement-level INSERT, UPDATE, DELETE and TRUNCATE on every gated table, zero-row statements included;
- `INSERT … ON CONFLICT` (both INSERT and UPDATE statement triggers fire);
- `COPY FROM`;
- `MERGE`;
- DML performed by other triggers (each target table's own gate fires).

These are to be verified by installation tests.

It does **not** cover:
- locking `SELECT`;
- `nextval`/`setval`. Policy: friendly-id sequence gaps are acceptable (sequences are `NO CYCLE`, and accepted
  rollbacks already leave gaps). A refused writer may consume values; no ownership state depends on sequences.
- DDL, which only `migrate` performs, under MA;
- filesystem effects.

**Coverage verification.** At CU-12 and at each startup, a catalog check confirms that every `relkind IN ('r', 'p')`
table in schema `gas` except `flyway_schema_history` has the enabled gate trigger. "40 + 7 = 47 tables" (V0025 plus
the seven control tables of this revision) is the current inventory, **not** a permanent theorem. A migration that
adds a table, a partition or a direct write target must add the trigger; the check refuses otherwise.

**Settings scope.**
- `SET LOCAL` reverts at transaction end. The activation and transition connections are maintenance connections:
  they are closed after the run and never returned to an ordinary pool.
- Compatible pools set the declaration as connection startup options (a `DataSource` property, not the URL), so a
  reset or reconnect re-declares from the process's start-time values. F3 then validates them.
- Application transactions are READ COMMITTED (asserted, R35-3).

#### 5.7.5 Logical-step fencing, connection loss and the corrected coverage policy (R7-2)

**Authority of a logical step:**
- A logical step is one durable unit of preservation work, such as receive, plan and reserve, create and copy, an
  intended move with its rename and settlement, commit, finish, a destruction pass, a scan or a recovery pass.
- Its authority is `PreservationAdmission.begin(stepKind, identities)`, called **before the step's first business
  lock or filesystem effect**. It succeeds only if (a) the process holds F1, which is always true after startup;
  (b) the process's start-time decision is ACTIVE at the build's protocol (or the step is in the read-mode set,
  §5.7.10); and (c) the process is not draining or fenced.
- The step holds its authority until it reaches a safe boundary: its last durable window has committed or rolled
  back, and no filesystem primitive is in flight. Helpers (E-27) run only inside an open step and never call
  `begin` themselves.

**Why a start-time decision is a valid lifetime authority here:**
- The decision can be invalidated only by a new epoch.
- A new epoch is published only inside MA, and MA requires F1 exclusive, which requires every compatible process on
  the host to be dead.
- Therefore no step of a living process can straddle an epoch change. This is the property the stale-worker
  counterexample lacked.

**Connection identity (F3):**
- Every new physical connection, from the pool's first connection to every reconnect, runs
  `SELECT gas.admit_session(:protocol, :epoch, :dboid, :sysid)` as Hikari `connectionInitSql`, with the literals
  fixed at process start.
- The function raises GA002 if any value differs from the singleton, the singleton is missing, or an OPEN
  maintenance run exists.
- A failed initialisation never yields a usable connection. The process's pool-failure listener then **fences**
  the process (no new step begins) and exits with code 3; the exit is visible to the service manager.

GA002 cannot occur while F1 is respected (the singleton cannot change), so it signals a violated assumption: another
host, a restored database underneath a running process, or a manual edit. It is treated as fatal.

**Connection loss during a filesystem step:**
- The process keeps F1, so no maintenance can start.
- The filesystem primitive in progress (copy, rename, delete, staging write) completes or fails on its own. The
  step's next database window fails with a connection error, and the step ends at that window, leaving the last
  committed durable state. These are exactly the accepted Phase 2 crash-and-retry states.
- When the database returns, new connections pass F3 (nothing changed), and recovery resumes the step from its
  durable state.

**Shutdown:**
- On stop (SIGTERM, a service stop), the process stops accepting new steps and lets running steps reach a safe
  boundary within the service manager's stop timeout. Receivers abort their requests: the staging bytes are left
  for recovery, as today.
- A step that misses the deadline is cut off when the process is killed. A killed process performs no further
  effects, and its partial state is crash-equivalent (R35-6), now justified by F1: MA can begin only after the OS
  has released the process's lock.

**Coverage-policy corrections to E-1…E-27** (assessment §7):
- **E-2, E-4 (receive) and E-3 (renewal timer):** the whole receive, including staging writes and receiver cleanup,
  is one step. The timer exists only in a process that holds F1, and the epoch cannot change during its life.
- **E-12 (volume administration) and E-24 (initialisation, users):** these are new-build writers. They hold F1 and
  pass F3, so they are fenced exactly like preservation entries. "Permitted while disabled" means not gated on
  ACTIVE; it does not mean unfenced or without effect. They mutate metadata and volume layout.
- **E-14 (job runner):** job claim, renew, succeed and fail, and worker presence, are gated DML of a fenced process.
  Handler registration is decided once at start and cannot change during the process's life.
- **E-15…E-21:** each loop iteration is a step. RecoveryService's P16 loop and `reconcileAll` call `begin` per
  entry, before the operation advisory and before any rename.
- **E-23 and E-25:** run only inside MA (§5.7.7). They never use ordinary admission.
- **E-26 (Phases 0–3 metadata):** the writers are compatible, and F2/T5 removed every legacy session able to lock.
  The seventh assessment's R7-1 cycle needs such a session (§5.7.6).
- **E-27 (helpers):** callers are restricted (ArchUnit) to E-1…E-25 and the maintenance wrappers of §5.7.8, by exact
  method name.

#### 5.7.6 Counterexample resolutions

**R7-1, connected old administrator versus new worksheet add and remove** (assessment §4). Schedule under 3.6:

```
time  legacy web L (old admin A)        maintenance (MA)                      new web N (T1 add, T2 remove)
 t0   connected, read-write session sA   —                                    not installed yet
 t1   operator stops services (T1); L stops, or is found by T2 (OS scan) -> run refused until stopped
 t2                                      F1 exclusive; controller; advisory
 t3                                      T4: role default on (one Flyway tx)
 t4                                      T5: pg_terminate_backend(sA) -> gone   (sA could only exist if L survived T2)
 t5   any reconnect of L is fresh:        T6 publish; T7 release MA            N takes F1 shared; F3 ok
      default read-only
 t6   A: SELECT … FOR UPDATE on app_user  ->  25006 before any row lock        T1, T2 run under D-LP-7 / D-LP-9(a)
```

The cycle A→T1→T2→A needs A to hold `app_user(a1)` FOR UPDATE and wait on T1's KEY SHARE. Under 3.6 there are two
cases:
- A's session existed before T4: T5 ended it before any compatible writer was admitted.
- A's session is fresh: its locking read is refused (25006) before it acquires a row lock.

Either way the A edge cannot exist, and T1/T2 are compatible participants ordered by D-LP-7 and D-LP-9(a).
**Residual:** a deliberately overridden legacy session (`SET default_transaction_read_only = off`) is outside the
accidental scope. Alternative 1 (D-LP-8 (x)) closes it.

**Case B, an old process doing filesystem work with no database session.** T2 is OS evidence that no legacy process
is running. A legacy process that T2 cannot see is a T2 failure (another host, or Windows without confirmation),
which Q-36-1 and the recorded confirmation cover. F1 covers this case for compatible processes.

**Case C, a stale new worker resuming an INTENDED move after the epoch changes** (assessment §5):

```
time  worker N (compatible, holds F1 shared)                  maintenance
 t0   cached ACTIVE, epoch E; in RecoveryService.runOnce
 t1   pool sessions drop (DB restart)                          operator runs activate-preservation
 t2                                                            F1 tryLock exclusive -> REFUSED (N alive)
                                                               run refused: "instance lease held by a running process"
 t3   reconnects; F3: singleton still (P, E) -> ok
 t4   reconcileAll -> begin(step) -> advisory -> rename -> Wa2 commits under epoch E (legitimately)
```

Step 3 of the assessment's schedule ("maintenance … publishes E+1") cannot occur while N lives. If N is killed
instead, F1 is released only after N's threads have ended, so no rename can follow the publication. The INTENDED
entry is a durable crash state, settled by recovery after activation.

#### 5.7.7 One maintenance authority (R7-3)

**Identifier:** the triple **MA = (F1 exclusive, controller session advisory `(7340, 2)`, durable OPEN row in
`gas.maintenance_run`)**. Key (7340, 2) is reserved for it. (7340, 1) remains D-LP-9(a)'s key.

**Commands that acquire it:**
- `transition`;
- `migrate`;
- `activate-preservation` (activation, re-admission and re-audit);
- `preservation-resolve`;
- `maintenance resume`, `maintenance abort`;
- `verify-restore`.

Read-only `preservation-status` and `status` take F1 shared only.

**Acquisition order** ~~never waiting~~ **[3.7: waits are bounded, not absent, §5.8.5; step 2's session declaration is replaced by `maintenance_begin`, §5.8.4]**:
1. F1 `tryLock` exclusive.
2. Open the controller connection (application role; startup options declare the singleton's current identity;
   `application_name = genealogy-archive-maint-<run>`).
3. `pg_try_advisory_lock(7340, 2)`.
4. CU-12 checks: stray sessions terminated or refusing, layer A and B coverage.
5. `INSERT gas.maintenance_run (…, state 'OPEN', controller_pid, controller_backend_start)`, unless an OPEN run
   exists (a partial unique index allows at most one). An existing OPEN run can be continued only by
   `maintenance resume` or `maintenance abort` (CU-20).

Any failure releases what was acquired, in reverse order. ~~Nothing waits~~ **[inoperative 3.7]**; the run is refused with a reason.

**Lifetimes:**

| Element | Lifetime |
|---|---|
| F1 exclusive | the maintenance process's life |
| Advisory (7340, 2) | the controller **session**, which stays open and idle between transactions for the whole run |
| OPEN row | durable, from step 5 until CU-11 closes it (CLOSED or ABORTED). **It survives a crash**, which keeps services out (startup step 3; F3 refuses connections while a run is OPEN). |
| Action connections | opened by the maintenance process after step 5, at most two (the action connection and Flyway's), each registered in `gas.maintenance_connection (run_id, pid, backend_start)` on open |

**Binding of action transactions.** The first statement of **every** maintenance transaction other than the
controller's is:

```sql
SELECT gas.require_maintenance(:run);  -- raises GA003 unless: an OPEN row with run_id = :run exists, AND
                                       -- pg_locks shows advisory (classid 7340, objid 2, objsubid 2) granted to the
                                       -- row's controller_pid, AND that pid's backend_start equals the recorded one
```

So an action can begin only while its controller holds the authority. The check is a plain read of
`maintenance_run` and of `pg_locks` (§11.9 (c)); it takes no lock on the singleton or on any business table.

**No self-blocking.** The controller holds only a session advisory and no table lock between transactions. The
activation transaction's EXCLUSIVE table locks (CU-4) are taken on the action connection and released at its COMMIT.
No maintenance connection takes the startup `FOR SHARE` read (withdrawn), and the CU-4/CU-12 session checks exclude
exactly the registered `(pid, backend_start)` pairs of the run.

**Mutual exclusion of activation, resolution and migration:** all three run only as steps of an MA run, and at most
one OPEN run exists. Two concurrent maintenance commands on one host cannot both obtain F1 exclusive; on two hosts,
both cannot obtain the advisory or insert a second OPEN row.

**Failure of the controller connection:**
- The advisory is released, so every later action transaction fails GA003 at its first statement, before any
  business lock.
- A filesystem primitive already in progress completes (the process still holds F1, so no compatible process can
  start and act concurrently). The action's next window then fails, and the journal shows the last committed window
  (§5.7.8).
- The maintenance process exits non-zero. The OPEN row remains, so services refuse to start until
  `maintenance resume` or `maintenance abort`.

**Leaving pending operations safe:**
- Every action window is an accepted durable business window, so between windows the business state is an accepted
  crash-recovery state (for example Q INTENDED after the rename).
- `maintenance resume` re-acquires MA (a new controller pid is recorded on the same OPEN row, transferred under the
  new advisory) and continues each unfinished action from the journal plus the business state (§5.7.8).
- `maintenance abort` records every unfinished action as `ABANDONED_AT_SAFE_BOUNDARY`. It changes no business row:
  the durable crash state is left to ordinary recovery once preservation is ACTIVE, or to a later run. It then closes
  the run ABORTED.

**Transaction and connection diagram (activation re-admission after resolution, one run):**

```
process M (holds F1 exclusive for its whole life)
  controller C ──BEGIN/COMMIT─▶ (autocommit) pg_try_advisory_lock(7340,2) ─▶ [session holds advisory .........................]
                 └─ tx C1: INSERT maintenance_run(OPEN, controller_pid=C) COMMIT
  action A1 ── tx A1.0: require_maintenance; INSERT maintenance_connection COMMIT
             ── action journal windows (§5.7.8), each: BEGIN; require_maintenance; <business window>; INSERT window row; COMMIT
             ── tx A1.k: activation (CU-4): BEGIN; require_maintenance; B0 NOWAIT; tables EXCLUSIVE NOWAIT; audit; publish; COMMIT
  controller C ── tx C2: UPDATE maintenance_run SET state='CLOSED' COMMIT; pg_advisory_unlock(7340,2); close
  M exits ─▶ OS releases F1
```

#### 5.7.8 Durable, resumable offline resolution: the action journal (R7-4)

**Existing tables do not suffice.**
- `file_operation_event` is per operation.
- `quarantine_object` and `unresolved_object` record business evidence.
- None records a maintenance action that spans several operations, together with its authority and per-window
  outcome.

New control schema (all **append-only** except the state columns noted; listed in D-LP-8 (v-b)):

| Table | Columns (main) | Notes |
|---|---|---|
| `gas.maintenance_run` | `run_id` uuid PK; `kind`; `state` {OPEN, CLOSED, ABORTED}; `opened_at`, `closed_at`; `host`, `os_user`; `operator_principal`; `controller_pid`, `controller_backend_start`; `epoch_at_open`, `protocol` | partial unique index: one OPEN; state changes OPEN→CLOSED or OPEN→ABORTED only (trigger) |
| `gas.maintenance_connection` | `run_id`, `pid`, `backend_start`, `role` (controller, action, flyway) | append-only |
| `gas.maintenance_action` | `action_id` uuid PK; `run_id`; `seq`; `kind` (A-1…A-5); scope: `operation_id`, `content_id`, `unresolved_id`, `quarantine_id`, `volume_id`, `path`, finding refs; `authorized_principal`, `named_administrator`; `reason`; `precondition` (jsonb snapshot of the revalidated rows and their versions); `state` {INTENDED, IN_PROGRESS, COMPLETED, NOT_APPLIED, FAILED, ABANDONED_AT_SAFE_BOUNDARY} | state changes forward only (trigger); `INTENDED` is committed **before the first effect** |
| `gas.maintenance_action_window` | `action_id`, `window_no`, `window_kind` {PRECHECK, INTENT, FILESYSTEM_OBSERVED, SETTLEMENT, FINAL_TRANSITION, STAGING_CLEANUP, ATTEST, RELEASE, HOLD}; `outcome` {COMMITTED, NOT_APPLIED, FAILED}; links: `quarantine_id`, `unresolved_id`, `event_id`, `generation`; `observation` (path, size, SHA-256, file key, timestamps, where measured); `detail`; `at` | append-only; inserted **in the same transaction** as the business window it records (a `beforeCommit` hook of the maintenance wrapper), so a window and its record commit or roll back together |

The 3.5 table `preservation_resolution` is withdrawn. **New control tables in total: 7**: the three kept from
3.5 (`preservation_activation`, `preservation_activation_event`, `preservation_audit_finding`) plus the four above.
The gate inventory therefore becomes 40 + 7 = **47** tables.

**Operator identity.**
- `operator_principal` is the authenticated OS identity of the maintenance process (Linux: the effective user;
  Windows: the process token's user). That is the accepted CLI authority (ADR-0010: the CLI is run by an
  administrator or the service account).
- `--administrator <username>` is **recorded as named, not as authenticated**. It must name an existing enabled
  Administrator account, so a typo cannot be recorded. The record says "named by operator", and no claim is made
  that the account authenticated.

**Action protocol (each action):**

```
J0  tx: require_maintenance; INSERT maintenance_action(state=INTENDED, precondition=snapshot) COMMIT     -- before any effect
J1  tx: require_maintenance; PRECHECK: re-read and re-validate every precondition row (versions, states,
        claimant set at every location the action may release); mismatch -> UPDATE action NOT_APPLIED; COMMIT; stop
    ... then the action's accepted windows, unchanged in their own order and boundaries, each with its journal row ...
Jn  tx: require_maintenance; UPDATE maintenance_action SET state = COMPLETED | FAILED (with the failing window) COMMIT
```

**A-3, cancellation of a BLOCKED or WAITING_FOR_VOLUME ingest or repair** (accepted `requireCancellable`: not
DESTROY), with the accepted windows kept separate:

| Window | Business effect (accepted code) | Journal | Crash after this window leaves |
|---|---|---|---|
| J0, J1 | none | INTENDED; PRECHECK | nothing changed; resume repeats J1 |
| W-pend | settle the operation's own pending INTENDED entry (Wa2), if any | SETTLEMENT | Q settled; resume continues |
| W-check | `OperationCopies.check` (read; filesystem observation) | FILESYSTEM_OBSERVED | nothing changed |
| W-intent | Wa1: `intend` (Q INTENDED, trusted evidence: the verified observed copy, or `expected_*` when absent, exactly as accepted) | INTENT (q id) | Q INTENDED, bytes at the source |
| FS | rename source → quarantine destination (no database) | — | bytes at either place, Q INTENDED: an accepted settle-later state |
| W-settle | Wa2 under Ct(X) U. **Immediately before `releaseTargetsAt`, revalidate the exact claimant set at the source** (owner rows joined to `file_operation`): only the cancelled operation itself and final operations are allowed. Otherwise the window rolls back. | SETTLEMENT (or FAILED: foreign claimant) | Q QUARANTINED or SOURCE_ABSENT; the claim released |
| W-unverified | for an UNVERIFIED copy instead of a move: Wf2 (`claimBecomesUnresolved`) | SETTLEMENT (u id) | U OPEN |
| W-final | P11: CANCELLED transition (CAS on the expected state) | FINAL_TRANSITION | operation CANCELLED |
| FS-staging | staging delete (accepted cleanup; disposable staging, see below) | — | staging present or gone |
| W-staging | `markStagingCleaned` | STAGING_CLEANUP | done |
| Jn | — | COMPLETED | — |

**Resume rule [inoperative 3.7: replaced by §5.8.6]**. For each IN_PROGRESS action, the next window is derived from **business state, not from the
journal alone**:
- an INTENDED Q of this action means settle;
- a QUARANTINED Q with the operation not final means W-final;
- a CANCELLED operation with staging not cleaned means FS-staging.

Every business window is a CAS on its expected state, so repeating a window after an unknown COMMIT is a
`NOT_APPLIED` no-op and is journalled as such (idempotent). The journal row of a window commits with it, so there is
never a committed window without its row. An unknown outcome is resolved by reading the business state after the
connection is gone (CU-21).

**A-4, an attested move:**
- J0, J1, then `quarantineAttested`'s accepted windows: object-identity re-observation against the full recorded
  identity (size, SHA-256, created, modified, file key), Wa1 with ADMINISTRATOR_ATTESTED evidence, the rename, Wa2
  with the same claimant revalidation, and the resolution of U in Wa2.
- A V0020 "decision not confirmed" or an identity mismatch is `NOT_APPLIED` with the accepted note.

**A-1 (attest) and A-2 (release):**
- These are single accepted windows (Wd2 and Wd1), each with its journal row, under the accepted identity-evidence
  rules (V0020: an attestation binds to the observed object).
- A-2's precondition is **exactly AU-4 (b)'s admitted claimant set**: no claimant at all, the record's own
  generation row, or final operations only. It refuses **any** non-final claimant, own or foreign (the 3.5 "foreign
  only" wording was inconsistent with AU-4 and is withdrawn).

**Byte classes (correcting 3.5's "no action deletes bytes"):**

| Class | Where | May a resolution action remove it? |
|---|---|---|
| Protected originals | generation storage paths | **Never.** No resolution action moves or deletes them. Superseded-generation quarantine is ordinary recovery, not a resolution action. |
| Quarantine evidence | Q destinations | **Never** deleted |
| Operation copies | operation targets | Moved to quarantine (A-3, A-4) or left and recorded as unresolved (Wf2); never deleted |
| Attested objects | U paths | Moved to quarantine (A-4); never deleted |
| **Disposable staging** | upload and repair staging | **Deleted by accepted cleanup** after A-3 has made the operation final, under the accepted rule that staging is kept while it is the identity evidence for an existing copy (`stopUnfinishedRepairs`; cancellation). A cancelled ingest's staging is the only copy of those uploaded bytes; discarding it is accepted cancellation semantics, stated here explicitly. |

**A-5, administrative hold (D-LP-8 (vi); remains an explicit owner decision):**
- **Kinds:** INGEST and REPAIR only. **Never DESTROY**, even in WAITING_FOR_VOLUME: destruction is not cancellable
  (`requireCancellable`), and holding it would not make cancellation valid.
- **States:** PROMOTING and PROMOTED only. These are the states from which the accepted worker itself blocks
  (`promote` and `verifyAndCommit` call `block`). WAITING_FOR_VOLUME and BLOCKED are already cancellable and need no
  hold. RECEIVING and STAGED have no target and are left to ordinary recovery.
- **CAS:**
  ```sql
  UPDATE file_operation SET state = 'BLOCKED', blocked_reason = 'STATE_UNKNOWN', message = :m
   WHERE operation_id = :o AND state = :expected
     AND target_path IS NOT DISTINCT FROM :tp AND target_generation IS NOT DISTINCT FROM :tg
     AND target_created = :tc AND attempts = :a
  ```
  via the accepted `transition` (P1 window, owner advisory first), with the event `administrative hold by
  maintenance run R, action A (reason)`.
- **Unchanged:** target path, target generation (so Wh1a's all-state high water is unchanged), `target_created` (so
  the owner row is unchanged: ownership is not altered), and the expected size and hash.
- **Afterwards:**
  - A-3 may cancel the operation in the same run.
  - Or, after ACTIVE, an ordinary `retry` resumes it to PROMOTING with its target (the accepted retry of
    STATE_UNKNOWN).
- **The hold waives nothing.** The held operation still "holds" (AU-7) and still claims (AU-4 (b)), so the audit
  refuses activation until the conflict is actually resolved.

**A-6, reviewed legacy acceptance: deferred** (D-LP-8 (vii) not proposed for the initial scope).
- Components refused only for UNSAFE-LEGACY reasons stay refused.
- A separate future owner-reviewed remedy must prove:
  - host-equivalent disjointness from every future constructor output (case-insensitive and Unicode-aware on both
    hosts);
  - an immutable exact scope;
  - valid source, evidence and claimant relations.

  It must never override active ownership, owner-ledger inconsistency, evidence ambiguity or an unproved host alias.
- No Unicode-specific admission rule is proposed: §5.7.9 refuses non-constructor values in identity columns
  (UNSAFE-LEGACY) whatever their characters, and does not redefine `PortableRelativePath`.

#### 5.7.9 AU-1…AU-8 corrected (R7-A1…R7-A4)

**Source facts.**
- **Generation states:** CURRENT, QUARANTINE_PENDING, QUARANTINED, MISSING, DESTROYED and **UNRESOLVED** (V0019: a
  superseded generation whose location holds an unidentified object; it requires an OPEN U for (content,
  generation), enforced by a deferred constraint trigger).
- **Operation finality:** final = COMPLETED, ABANDONED, CANCELLED, REJECTED.
- **Path constructors:** `ManagedVolume.originalPath(content, generation)` and
  `ManagedVolume.quarantinePath(operation, suffix)`. Every identity path column is filled from these, or copied from
  another identity column.
- **Admission outcomes** are those of §5.6.5. UNSAFE-UNCLASSIFIED remains the default for anything unlisted, but
  **every state below that accepted code can produce is listed as passing**, so no ordinary recovery state needs a
  waiver.

**AU-1 Generation identity**
- **Invariant:**
  - `storage_path = originalPath(content, generation)`, and `volume_id = content.volume_id`.
  - The **initial generation g₀(X)** is the generation whose `operation_id = X.created_by_operation`. That operation
    is an INGEST of X with outcome NEW_CONTENT, `target_generation = g₀`, in COMMITTED or COMPLETED.
  - **g₀ may be any number ≥ 1** (R7-A1). `quarantinePartialAndReplan` raises an ingest's target before any Content
    row exists, and `commitNewContent` → `FileContentRepository.insert` stores `op.targetGeneration`.
  - Every other generation g ≠ g₀ was created by a REPAIR R of X with `R.target_generation = g`, in COMMITTED or
    COMPLETED, and g > g₀. Numbers may skip (Wh1a, partial replans).
  - For content ≠ DESTROYED, exactly one CURRENT. For DESTROYED content, every generation is DESTROYED
    (destruction completion).
  - An UNRESOLVED generation has an OPEN U with that (content, generation) **at its storage path** on its volume.
  - **`vacated` is evidence, not identity (R7-A2):** the audit checks only the database constraint
    `NOT (vacated AND QUARANTINE_PENDING)`. V0016's trigger changes `vacated` only on CURRENT, so superseded
    generations (MISSING, UNRESOLVED, DESTROYED, QUARANTINED) keep historical `vacated = true`, which is valid.
- **Constructors and writers:**
  - `commitNewContent` (`insert`; g₀ = op target);
  - `commitRepair` (`insertGeneration`; old CURRENT → MISSING or UNRESOLVED);
  - `quarantineSuperseded` (QUARANTINE_PENDING → UNRESOLVED or MISSING; QUARANTINED via settlement);
  - destruction completion (→ DESTROYED).
- **Passing:** an initial generation 3 after two partial replans; skipped numbers; a historical vacated MISSING or
  UNRESOLVED generation after a successful repair; QUARANTINED with `quarantine_path`; a DESTROYED content with all
  generations DESTROYED.
- **Failing:**
  - **UNSAFE-LEGACY:** a non-constructor storage path; a creator that is not the content's ingest and not a
    committed repair of that number; a generation on another volume; two CURRENT (prevented by an index);
  - **UNSAFE-AMBIGUOUS:** an UNRESOLVED generation whose OPEN U is at another path.
- **Joins:** generation ⋈ content (content_id); generation ⋈ file_operation (operation_id); generation ⋈ U (content,
  generation, OPEN).
- **Legacy assumption:** `operation_id` is NOT NULL on every generation (schema). Generations written before V0016
  may carry `vacated` from the V0016 backfill; that is admitted as evidence.
- **Fixtures:** AA-11b (g₀ = 3), AA-11c (historical vacated), plus one fixture per failing case.

**AU-2 Operation target meaning:** §5.6.5's table, with these corrections:
- INGEST COMMITTED or COMPLETED with NEW_CONTENT: the generation `target_generation` of X **is g₀(X)** (any
  number), with `storage_path = target_path`, created by this operation (R7-A1).
- **[inoperative 3.7: replaced by the transition-specific pointer rule, §5.8.7]** An INGEST with a partial history has `quarantine_path` = the destination of its latest PARTIAL_COPY Q (set
  together with the replan in `quarantinePartialAndReplan`).
- SHARED_CONTENT is unchanged (ADMITTED-CLEANUP, `target_created` false), checked **relationally**. The Y in
  `target_path` must parse as `originalPath(Y, t)` and Y must have no `file_content` row; Y must be referenced by no
  other row's identity column (content_id, generation, path). Event text is never searched.
- **Fixtures:** AA-11 and AA-11b.

**AU-3 Physical-owner ledger, both directions:** retained from §5.6.5.
- It includes GENERATION rows of **every** generation state: F1 lemma, rows are never deleted.
- A ledger row never implies that bytes are present. The audit is database-only and asserts nothing about the
  filesystem.
- **Fixture:** AA-14 (each direction).

**AU-4 OPEN unresolved records: role by writer (R7-A4).** Each OPEN U must match **exactly one** writer role:

| Role | Writer (source) | U.operation | Kind | Path, generation |
|---|---|---|---|---|
| W1 own copy | `OperationCopies.claimBecomesUnresolved`, called by P3 `unverifiedOwnCopy`, Wf2 cancellation and destruction's UNVERIFIED branch | the copy's operation O | OPERATION_COPY | `O.target_path` (current); generation null; content = O.content if persisted, else null |
| W2 supersession | `commitRepair` (old CURRENT); `quarantineSuperseded` (QUARANTINE_PENDING) | the repair R of X | DAMAGED_COPY or OBJECT_AT_VACATED_LOCATION (by the generation's `vacated` at that moment) | storage path of generation g of X; generation = g (UNRESOLVED while OPEN) |
| W3 destruction | `DestructionProcessor.newRecord` (refusal and inventory paths) | **the DESTROY D of X**, not the copy's creator | OPERATION_COPY | a writer w's `target_path` (w a writer of X; D itself has no target); generation null |
| | | D | CURRENT_COPY or OBJECT_AT_VACATED_LOCATION | storage path of X's CURRENT; generation = g |
| | | D | DAMAGED_COPY | storage path of a QUARANTINE_PENDING or UNRESOLVED generation; generation = g |
| | | D | **QUARANTINED_COPY** | a QUARANTINED generation's `quarantine_path`, or a QUARANTINED Q's destination for X; **generation null** |
| | | D | OBJECT_AT_VACATED_LOCATION | a former location of X: a Q source, a REMOVED Q destination, the storage path of a MISSING, DESTROYED or QUARANTINED generation, or a writer's target; generation null |

- **(a) Identity:** the role's relation holds with the joined rows, on the same volume. The attestation fields are
  consistent (V0019/V0020). If attested and confirmed, the full recorded identity is present (size, SHA-256,
  created, modified, file key).
- **(b) Claimants at (volume, path):**
  - **Admitted:** none; the GENERATION row of the same object (roles W2 and W3 at a generation storage path); an
    OPERATION row of a **final** operation (for example the CANCELLED repair stopped by destruction, whose claim D
    releases at completion: ADMITTED-STALE).
  - **UNSAFE-ACTIVE:** an OPERATION row of a **non-final** operation, own or foreign.
  - **UNSAFE-AMBIGUOUS:** a GENERATION row of another object.
- **(c)** A live plan of a non-final REPAIR targeting U.path without a claim: UNSAFE-ACTIVE (retained).
- **Passing fixtures:**
  - AA-10b: a destruction-created OPERATION_COPY U naming D, with the stopped repair's final stale claim;
  - AA-10c: a destruction-created QUARANTINED_COPY U with generation null at a QUARANTINED Q destination;
  - W2 records from a successful repair.
- **Failing fixtures:** a U matching no role; a role relation broken (for example a W1 U whose O targets another
  path); a non-final claimant.

**AU-5 INTENDED quarantine entries: binding by reason (R7-A3)** **[3.7: extended by the recorded-absent rules, §5.8.7]**

| Reason | Source relation | Evidence binding (as recorded; never recomputed, never downgraded, never compared with the full expected hash unless the writer used it) |
|---|---|---|
| PARTIAL_COPY | source = `originalPath(O.content, t)` for a target t of O (current or earlier; the replan transition follows the settlement); destination suffix `partial-<attempts at the time>`; after settlement, `O.quarantine_path` = this destination | OPERATION_COPY_VERIFIED with the **trusted partial-prefix** size and SHA-256 (`Evidence.operationCopy(partial)`). Check `source_size ≤ O.expected_size`. The hash is trusted as recorded: prefix identity was verified before the intent. |
| UNVERIFIED_COPY | source = O's target; suffix `unverified-copy-<n>` | OPERATION_COPY_VERIFIED: the copy measured while written (`operationCopy(copied)`); any size or hash |
| CANCELLED_COPY | source = O's target; suffix `cancelled` | OPERATION_COPY_VERIFIED: the verified observed copy, **or** O's `expected_*` (accepted ABSENT branch) |
| ATTESTED_OBJECT | an OPEN, attested, decision-confirmed U at the source, with `U.operation_id = Q.operation_id`; suffix `att-<U id without hyphens>` | ADMINISTRATOR_ATTESTED; `source_size`/`source_sha256` = U's observed values; U carries the full recorded identity |
| DAMAGED_GENERATION (legacy) | source = storage path of a QUARANTINE_PENDING generation of `Q.content` | as recorded; UNTRUSTED allowed |
| any, UNTRUSTED | rows recorded before V0019 (the column default) | admitted when the role relation holds; settlement handles UNTRUSTED evidence under its accepted rule (`QuarantineMoves` refuses to treat it as identifying) |

- **Claimants at the source:** as §5.6.5 (own, final stale, foreign non-final UNSAFE-ACTIVE, another generation
  UNSAFE-AMBIGUOUS).
- **Fixtures:** AA-14b (a trusted partial-prefix PARTIAL_COPY with size below expected passes); a mismatched basis
  for the reason fails.

**AU-6 Quarantine destinations: same-object relations (R7-A4)**
- **Constructor:** the destination is `quarantinePath(Q.operation, suffix)`, with the suffix allowed for the reason
  (AU-5).
- **Admitted references to a destination d** (all denote the same object):
  - its Q;
  - `O.quarantine_path` of Q's operation;
  - the bound generation's `quarantine_path` (superseded-generation quarantine);
  - a **destruction U of kind QUARANTINED_COPY, generation null**, at d, for the same content (W3);
  - for REMOVED destinations, a destruction OBJECT_AT_VACATED_LOCATION U.
- Any other reference at d's key (an owner row, a generation storage path, an operation target, or a U of another
  role or content) is UNSAFE-AMBIGUOUS.
- **Fixtures:** AA-10c (pass); a foreign U at d (fail).

**AU-7 Reservation versus ability**
- **Reservation:** every recorded `target_generation`, in every state, is part of Wh1a's high water. There is no
  audit predicate; the overflow rule is unchanged.
- **Ability**, per operation of X:
  - **can create:** PROMOTING; WAITING_FOR_VOLUME with a target; BLOCKED with a target and a reason other than
    COLLISION or STAGING_INVALID;
  - **holds:** `target_created` in any state, **PROMOTED included**.
- **Own-created exception:** an operation whose `target_generation` equals the generation it created (NEW_CONTENT
  or REPAIRED; COMMITTED, COMPLETED or later) is history, not a conflict.
- **UNSAFE-ACTIVE:** two able-or-holding operations of X with an equal `target_generation`; or an able-or-holding
  operation whose `target_generation` is a recorded generation of X that it did not create.
- **Fixtures:** a PROMOTED repair holding its fresh target (pass); a PROMOTED repair with a duplicate tg (fail).

**AU-8 Aliases: closure over real relations**
- **Same-object relation ≡:** union-find over every path reference (eight columns, §5.6.5), joined by:
  1. generation storage ≡ its GENERATION owner row;
  2. operation target ≡ its OPERATION owner row (`target_created`);
  3. a NEW_CONTENT or REPAIRED operation's retained target ≡ the storage path of the generation it created;
  4. AU-4 roles: U ≡ the referenced target, generation storage, Q destination or former location;
  5. AU-5 roles: Q source ≡ the referenced target, U path or generation storage;
  6. AU-6: Q destination ≡ `O.quarantine_path` ≡ the bound generation's `quarantine_path` ≡ the W3
     QUARANTINED_COPY U.
- **Key:** (volume, `PortableRelativePath.caseFoldKey`).
- **UNSAFE-AMBIGUOUS:** any key with **more than one ≡ class**, whether raw-equal or a case-only variant.
- **UNSAFE-LEGACY:** a value that is not a constructor output for the identity it names (AU-1, AU-2, AU-6). This
  subsumes 3.5's non-ASCII rule, which is withdrawn: Unicode is not singled out, and `PortableRelativePath` is not
  redefined.
- **Fixtures:** AA-9 (independent aliases fail; bound references pass).

**Non-waivable categories** (no future waiver may admit them): UNSAFE-ACTIVE; any AU-3 owner-ledger inconsistency;
UNSAFE-AMBIGUOUS from AU-4, AU-5, AU-6 or AU-8; an unproved host alias.

#### 5.7.10 E-11: the download read-mode exception

`FileService.download` keeps its accepted order:
1. visibility and authorisation (not found or forbidden first: `NotFoundException` for DESTROYED, or for DELETED
   without `EDIT_FILES`);
2. lifecycle;
3. content state (a non-ACTIVE content is not served, with the accepted message);
4. volume availability.

Only then does it ask `PreservationAdmission.evidenceMode()`, which returns WRITE or READ_ONLY. There is **no**
require-ACTIVE call at method entry, so activation state is never disclosed ahead of not-found or permission.

| Observation (accepted checks: safe access, stored size; no new full rehash) | WRITE (ACTIVE) | READ_ONLY (disabled) |
|---|---|---|
| verified absent | We7 (generation-bound identity; MISSING finding and state, or nothing changed on a mismatch); "…is missing. The problem has been recorded." | **no write**; "This File's stored original cannot be read right now. Nothing was changed; try again later." |
| size mismatch | E2 SIZE_MISMATCH finding; "…not being served. The problem has been recorded." | **no write**; "This File's stored original does not match its recorded size and is not being served. Nothing was recorded." |
| inaccessible or inconclusive | accepted "cannot be read right now. Nothing was changed" | the same |
| verified | served | served |

The READ_ONLY branch is itself a step in the read-mode set (§5.7.5): it holds F1, writes nothing and moves nothing.
We7's point-in-time residual is unchanged.

#### 5.7.11 Control units CU-0…CU-21 (replacing §5.6.8)

These are separate from the **19 P2-8 and 22 Phase 2 business windows**, which are unchanged: the maintenance
wrappers run accepted windows and add a private journal insert.

| Unit | Entry | Effects | Order / waits | Failure |
|---|---|---|---|---|
| CU-0 Gate installation | `migrate` under MA (T4) | one Flyway transaction: DDL for the 7 control tables (§5.7.8); gate function and `ENABLE ALWAYS` triggers; `admit_session`, `require_maintenance`; the role default for the configured role | DDL **can wait** on stray sessions; `lock_timeout` bounds it | rollback as one unit, the role default included |
| CU-1 Service stop | operator | — | — | — |
| CU-2 Legacy OS verification | `transition` T2 | read OS state | — | refuse; list processes |
| CU-3 Stray-session termination | T5 / CU-12 | `pg_terminate_backend(pid, 5000)` for application-role backends except the run's | waits up to the timeout per pid | refuse when a pid survives, or another role's or a remote session exists |
| CU-4 Activation barrier and audit | action connection | B0 `EXCLUSIVE NOWAIT`; gated tables `EXCLUSIVE NOWAIT` in name order; AU reads | these acquisitions **never wait** (NOWAIT). The transaction's other statements can wait only on maintenance-held locks of the same run, of which there are none. | 55P03 → rollback; every acquired lock released |
| CU-5 Publication | same transaction | `UPDATE` singleton (exactly one row; a trigger forbids delete or insert of another row); `SET LOCAL` epoch; `INSERT` event and findings | under B0 | rollback |
| CU-6 Re-admission and re-audit | `activate-preservation` | CU-4 + CU-5 | — | as CU-4 |
| CU-7 Write gate (layer B) | every DML statement | plain read of the singleton | compatible with EXCLUSIVE B0. It **can** wait behind an ACCESS EXCLUSIVE (only migration DDL takes one, under MA, when no compatible process runs). | GA001 |
| CU-8 Connection identity (F3) | every new physical connection | `admit_session` read | the same | GA002 → fence and exit |
| CU-9 Instance lease (F1) | every process start; MA | OS lock | services wait (blocking shared); MA never waits | — |
| CU-10 MA acquisition | maintenance commands | F1 X; controller; `pg_try_advisory_lock(7340, 2)`; INSERT run | never waits | refuse; release in reverse order |
| CU-11 MA release | maintenance commands | UPDATE run CLOSED or ABORTED; unlock; exit | — | the run stays OPEN, so services stay out |
| CU-12 MA checks | inside CU-10 | sessions (`pg_stat_clear_snapshot` refresh; hidden `backend_type` counted); client addresses; layer A row for the configured role; trigger coverage | read | refuse |
| CU-13 Legacy transition | `transition` | T1…T7 | — | per step |
| CU-14 Startup eligibility | each process | F1 shared; bootstrap read (no OPEN run; database identity); decision | — | refuse start |
| CU-15 Step admission | each logical step | in-process `begin` / safe boundary | — | step refused; nothing done |
| CU-16 Action binding | every maintenance transaction | `require_maintenance` | plain reads | GA003 before any business lock |
| CU-17 Action journal: intent | J0 | INSERT action (INTENDED) | private row | rollback |
| CU-18 Action journal: window | each wrapped business window | private INSERT window row, in the window's transaction | private (fresh primary key; FK to the action row, which only this run updates) | rolls back with the window |
| CU-19 Action journal: completion | Jn | UPDATE action state | the action row only | rollback; resume re-derives |
| CU-20 Resume and abort | `maintenance resume` or `maintenance abort` | rebind the OPEN run to the new controller; continue or mark ABANDONED_AT_SAFE_BOUNDARY | as CU-10 | refuse |
| CU-21 Unknown-outcome resolution | `preservation-status` / resume | if the recorded pid and `backend_start` of the transaction's connection is still present: `pg_terminate_backend(pid, timeout)` and confirm it is gone; **only then** read the event (by `run_id`) or the business state | — | undecided until the backend is gone; services stay out because the run is OPEN |

**Narrowed claims:**
- "Activation never waits" now reads: **CU-4's lock acquisitions are NOWAIT.** Migration DDL (CU-0), termination
  (CU-3) and service startup (CU-9) can wait, each bounded or visible.
- "Every mutation is refused" now reads: **every DML statement on a gated table by a connection with a wrong or
  missing declaration is refused.** Locking reads, sequences, DDL and filesystem effects are fenced by F1, F2 and
  layer A, not by layer B.
- "All old sessions become read-only" now reads: **fresh non-overriding sessions after T4.** Existing ones are
  terminated by T5.

#### 5.7.12 Restore, reactivation and protocol change

- **Database identity.** The singleton records the `system_identifier` (`pg_control_system()`, readable by the
  application role, §11.9 (d)) and the database OID at publication. CU-14 and `admit_session` refuse when they
  differ.
- A logical restore (a new database or cluster) therefore cannot start services until `verify-restore` (an MA run)
  has:
  1. re-verified layer A for the configured role (role settings may not be restored with a database dump);
  2. checked trigger coverage;
  3. recorded the new identity;
  4. re-audited and published a new epoch.
- A **physical** restore (the same identifier and OID, a byte-identical point in time, role settings included) is
  self-consistent. Its ACTIVE row is valid for its data, but CU-14 still checks layer A.
- **Pre-gate backups** restore an accepted schema: the new build reports BEHIND, and `transition` is required.
- **REFUSED or NOT_ACTIVATED** dumps restore disabled.
- **An interrupted restore** leaves a database that fails CU-14 (identity, coverage or a missing singleton), so
  services refuse to start.
- **Protocol change (CU-11 of 3.5, now part of the `migrate` run under MA):**
  - The new build's maintenance connection declares the singleton's **current** (protocol, epoch), which it read
    under MA.
  - The migration updates the singleton to (NOT_ACTIVATED, P + 1, E + 1) and `SET LOCAL`s the new values for its
    remaining statements.
  - Old-protocol compatible builds started later hold F1 and fail F3 (GA002) at their **first connection**, before
    any business lock. Future generations are therefore fenced before the lock by F3, not by layer A (this resolves
    R35-4).
  - This is not a blanket ungated connection: the declaration is accepted only under an OPEN run (`admit_session`
    checks the run row for maintenance connections).

#### 5.7.13 Open assumptions and proof obligations

| ID | Statement | Status |
|---|---|---|
| R36-1 | One host per Archive Instance; F1's lock file is on a local filesystem | **owner confirmation (Q-36-1)**; MA refuses remote application-role sessions |
| R36-2 | OS file locks are released only after the holding process's threads have ended (Linux `fcntl`; Windows `LockFileEx`) | Linux primitive observed (§11.9 (e)); Windows **unverified**: LP-17 AA-17 on Windows CI |
| R36-3 | Client premise of layer A (no READ WRITE override by accepted builds) | source-derived; AA-3 with the real driver and pool versions (Spring Boot 4.1.1 BOM; versions to be recorded) |
| R36-4 | Windows legacy verification relies on SCM state plus operator confirmation of listed Java processes | stated limitation; confirmations journalled |
| R36-5 **[inoperative 3.7]** | Every business window used by an action is a CAS on its expected state, making repetition after unknown outcome NOT_APPLIED | per-window proof at implementation (CU-18 tests) |
| R36-6 **[3.7: scoped as R37-6]** | No accepted writer fills an identity path column except from the constructors or by copying another identity column | source inventory §5.7.9; LP-10b-style ArchUnit rule at implementation |
| R35-3 | READ COMMITTED application transactions | retained |
| R35-6 | Killed-process effects are crash-equivalent | retained; now justified by F1 |
| R35-1, R35-2, R35-4 | 3.5 assumptions | **superseded** by F1, F3 and §5.7.12 |

### 5.8 Revision 3.7: lease identity, continuous legacy exclusion, bootstrap, identity handover, controller loss, reconciliation and audit corrections [3.7]

**Evidence class:** proposed design; source analysis at `7a97e70`. There is no new local execution in 3.7 (§11.10).
Nothing is implemented, and no LP-17 case is claimed to pass.

**Conditional scope.** Everything in §5.7 and §5.8 is designed **for one designated application host per Archive
Instance** (Q-36-1). That boundary is an **owner decision that has not been taken**; this document does not record it
as approved. If the owner rejects it, F1 no longer proves the termination of every writer, and the smallest
alternative is stated in §5.8.10.

**Precedence.** §5.8 governs over §5.7, which governs over §5.6. Superseded statements are marked
**[inoperative 3.7]** in place (§5.8.9).

#### 5.8.1 R8-1: installation identity and the lease-object contract

**Identities.**

| Identity | What it is | Provisioned | Never |
|---|---|---|---|
| **Installation ID** | A UUID naming one installation of the application on one host: its configuration, its lease object and its database binding. Installation metadata only. | By the install scripts (or `provision-installation`, an effect-free command that writes only the configuration file) **before** any database or storage effect, as the required configuration key `installation.id` | the Archive ID; derived from a hostname or path; a File identity |
| **Approved host ID** | The host the installation runs on: Linux `/etc/machine-id`, Windows `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid` | read at startup | a security credential (a cloned VM shares it: limitation, R37-3) |
| **Archive ID** | `archive_instance.archive_id` (ADR-0006, immutable) | by `init-archive`, as today | changed by any lease or host operation |

**Lock-object mapping.**
- **Lease root**, fixed per platform and not configurable:
  - Linux: `/var/lib/genealogy-archive-leases` (root:root, 0755);
  - Windows: `%SystemDrive%\ProgramData\GenealogyArchive-Leases`, where `%ProgramData%` comes from
    `SHGetKnownFolderPath`, not from the configurable `-DataDir`. The ACL grants Full control to SYSTEM and
    Administrators, and RX to the instance's service SIDs. Inheritance is removed.
- **Lease object:** `<lease root>/<installation id>.lease`. Exactly one per installation ID, and different IDs give
  different objects. Two independent installations on one host therefore have independent leases, and maintenance of
  one never waits for the other. That removes 3.6's single host-wide path.
- **File contents:** fixed text naming the installation ID, used only for diagnostics.
- **Ownership and mode:** Linux root:`<service group>` 0640; Windows the same ACL as its directory. The lease root
  must be on a **local** filesystem: Linux `statfs` type ext4, xfs or btrfs; Windows a fixed NTFS volume. NAS, network
  and removable filesystems are refused, and archival storage is never used.

**Binding to the database** (columns of the singleton, §5.8.3; set by the transition, never by ordinary processes):
- `installation_id`, `approved_host_id`, `lease_object` (canonical path text);
- `cluster_system_identifier` and `database_oid`;
- `archive_id`: null until `init-archive`, which sets it in the same transaction as the `archive_instance` insert.

Every compatible process's ordinary connection initialisation (`admit_session`, F3) checks its configured
installation ID, its host ID and its canonical lease path against these columns. A process whose configuration is a
**copy** with another installation ID, another lease path, or another host fails **before any business statement**
(GA002). This is how two lease identities for one database are detected and refused. (A copy that keeps the **same**
installation ID maps to the **same** lease object, so the lease itself excludes it.)

**Acquisition and lifetime.** `LeaseHolder.acquire()` is the first action of `GenealogyArchive.main` for every
effect-producing command.
1. Parse only the configuration file (effect-free) to get `installation.id`.
2. Validate the path: every component from the lease root down must be a real directory (no symbolic link, no
   Windows junction or reparse point), checked with `NOFOLLOW_LINKS` attributes, `isOther`, and `toRealPath` equal to
   the constructed path (case-insensitively on Windows). It must have the expected owner and must not be group- or
   world-writable on Linux.
3. Open **one** channel.
   - **Linux:** `READ` (shared) or `READ, WRITE` (maintenance), with `NOFOLLOW_LINKS`.
   - **Windows:** additionally `ExtendedOpenOption.NOSHARE_DELETE` (and `NOSHARE_WRITE` for the shared holders), so the
     object cannot be renamed, deleted or replaced while any holder has it open.
4. Lock:
   - services and ordinary CLI: `lock(0, MAX, true)`;
   - maintenance: `tryLock(0, MAX, false)`.

   A shared request that returns `isShared() == false` is an **unsupported platform**: refuse and exit, never
   serialise silently.
5. Record the identity: Linux `(dev, ino)` of the opened path; Windows the volume serial and file index of the object
   opened (implementation via `GetFileInformationByHandle` through the platform package, a "filesystem/path handling"
   exception allowed by CLAUDE.md).

**Lifetime rules** (application invariants, enforced by code and tests; not OS guarantees):
- The channel and `FileLock` are held in one static final holder, reachable for the JVM's life. There is no
  try-with-resources, no shutdown hook and no cleaner that closes it.
- No other code opens the lease path (ArchUnit). On Linux this is essential, because closing **any** descriptor of
  the inode releases the process's `fcntl` locks.
- Pool shutdown, pool replacement, worker cleanup, receiver cleanup and timer shutdown never touch it. The lease ends
  only with the JVM.
- **Interruption:** a waiting shared `lock()` runs on a dedicated thread. The main thread waits on it with a
  supervisor that logs "maintenance in progress" every minute. Interruption, `ClosedChannelException`,
  `OverlappingFileLockException` or any I/O error means exit, with no effect performed.
- **Unexpected loss:** at every logical-step `begin` and every minute, `FileLock.isValid()` and the recorded object
  identity are re-checked. The path must still resolve to the same `(dev, ino)` or file index. On any failure the
  process latches **FENCED**: no new step; steps in flight run only to the safe boundary defined in §5.7.5. It then
  exits with code 4. This detects replacement after the fact; non-replacement is enforced by the next rule.
- **Install, upgrade and uninstall contract:**
  - The scripts create the lease root and the lease object **once, during provisioning, while no holder can exist**,
    and never afterwards.
  - Upgrade replaces the jar only, never the lease object.
  - Uninstall leaves the lease root in place.
  - A missing lease object at startup is a **refusal**, not "create a new one". Only offline `provision-installation`
    (no holder, root/Administrator) may create it.
  - This excludes the Linux replacement counterexample of the eighth assessment §5 (holder on inode I, maintenance
    locking a new inode J): J can come into being only through an offline provisioning step that refuses while I's
    lease root is in use. Its check is `fuser`-style: maintenance `tryLock` on I succeeds only if there is no holder.
- **Children and helpers:** the application starts no child processes (`ProcessBuilder` and `Runtime.exec` are
  absent from `src/main`, verified by source search). An ArchUnit rule keeps it so. Any future child that could
  mutate storage must take the same shared lease first.
- **Launchers:**
  - Linux systemd runs `java -jar … serve|worker` directly, so the unit's main PID is the JVM, the holder.
  - Windows WinSW runs `java.exe` as its child. The child JVM holds the lease, so the death of the WinSW parent says
    nothing; only the lease does.
  - Direct CLI use goes through the same `main`. Test scripts must use the same entry or label themselves as
    non-acceptance harnesses.

**Service-account contract.**
- **Linux:** services run as `genealogy-archive` (read on the lease object; shared lock on a read channel).
- **Linux maintenance commands** (`transition`, `migrate`, `activate-preservation`, `preservation-resolve`,
  `maintenance resume / abort`, `verify-restore`, `rebind-host`) run as **root**. They need write access to the lease
  object and OS process visibility, and they read the database secret from the protected file as today.
- **Windows:** web and worker run as `NT SERVICE\GenealogyArchiveWeb` and `…Worker` (RX on the lease root and R on
  the object); maintenance runs as an elevated Administrator.
- This **changes** the accepted installer instruction "migrate as the service account" (`deploy/linux/install.sh`
  step 3). That is a documented deployment change in D-LP-8 (v-a).
- `web.service` has `ProtectSystem=strict`; the lease root needs no write access for services, but its read
  visibility under that setting is acceptance test AA-17.

#### 5.8.2 R8-2: continuous legacy-build exclusion

**Two separate layers.**
1. The **operating-system process fence**, which the database cannot provide. It keeps legacy (accepted, pre-F1)
   processes from running at all during the transition.
2. The **database backstops**: the role default (fresh legacy sessions are read-only) and the layer B DML gate. They
   limit what a legacy *session* can do. They do **not** stop a legacy *process*, or its filesystem work, after its
   backend is terminated.

**Owner-approved prerequisite (new decision D-LP-8 (xi)): continuous legacy change freeze.** From step L1 to step
L12 below, no legacy build may start on the approved host. That covers services, service recovery, scheduled tasks,
manual runs, scripts, copied launchers and copied jars. This is an **operational guarantee given by the operator**,
not an automated one. The tooling enforces what it can (marked *auto*) and refuses when it cannot verify; it does not
claim more.

| Step | Action | Kind |
|---|---|---|
| L1 | Freeze begins. Linux: `systemctl disable --now` **and `systemctl mask`** both units (mask defeats `Restart=on-failure`, dependencies and manual `start`). Windows: `sc config <svc> start= disabled` and `sc failure <svc> reset= 0 actions= ""` for both services. Disable every scheduled task or cron entry that runs the jar (operator inventory, recorded). | operator; *auto*-verified by L5 (unit and service state, start type, failure actions) |
| L2 | Services stopped | operator |
| L3 | Install the new build: **replace the jar in place** (the units and WinSW XML then name new code); provision the installation ID, lease root and lease object; **write the required key `installation.id` into `installation.yml`**. Every accepted build rejects unknown configuration keys (`InstallationConfigLoader`: "Unknown keys are errors"), so any legacy launch that reads **this** configuration file exits at configuration load, **before any database or storage effect**. | *auto* partial fence: it does not stop a legacy launch with a copied configuration |
| L4 | `transition` takes F1 exclusive on the new lease object | *auto* |
| L5 | **OS verification 1.** Enumerate every process (`ProcessHandle.allProcesses`, with administrative rights). Record `(pid, start instant, user, command)` for each Java runtime process. **Refuse** if: (a) any process's command or arguments name the installation's jar, any copy of a `genealogy-archive*.jar`, or `org.genealogyarchive`; (b) any Java process's arguments are unavailable (usual on Windows) and the operator has not **confirmed it unrelated by `(pid, start instant)`**; (c) any process information is inaccessible; (d) the L1 unit or service state is not masked/disabled, or recovery is still enabled. | *auto*, fail-closed on uncertainty; confirmations journalled |
| L6 | Bootstrap controller and advisory (§5.8.3 B2) | *auto* |
| L7 | Gate-installation migration (§5.8.3 B3–B5), to **known COMMIT** | *auto* |
| L8 | **After known COMMIT**, on a read-write maintenance connection: terminate every other application-role backend except the run's own (`pg_terminate_backend(pid, 5000)`), and confirm each is gone. **This is not rolled back** by anything. | *auto* |
| L9 | Coverage verification (§5.8.3 B7) | *auto* |
| L10 | **OS verification 2, immediately before admission:** repeat L5. Any Java process **not** present at L5 with the same `(pid, start instant)` makes the run refuse (PID reuse is detected through the start instant). Repeat the session check of L8. | *auto*, fail-closed |
| L11 | Durable `TRANSITION_COMPLETE` publication (§5.8.3 B8) | *auto* |
| L12 | Normal MA run: admission audit and ACTIVE or REFUSED (B9–B10), then close. **Only then** does the operator unmask and enable the units or services and end the freeze. | operator |

**What remains operator responsibility (stated, not hidden):**
- a legacy launch from a **copied configuration and copied jar**, started between L5 and L10 and finished before L10;
- any launch on another host (Q-36-1).

**Interrupted installation:**
- Before L7 commits, the database is still accepted V0025. The freeze continues, and the new build's units either
  wait on F1 or refuse (`TRANSITION_PENDING`, §5.8.3).
- After L7, the state machine resumes at the recorded state.
- The freeze may end only at L12. Ending it early means L10 or the next `transition resume` refuses whenever it finds
  a legacy process.

**The administrator/worksheet counterexample after a successful transition.** The eighth assessment's §6 schedule
needs an old read-write session A holding `app_user(a1)` FOR UPDATE.
- **Before L8:** A's pre-default backend may exist, but **no** compatible business writer exists. Ordinary
  `admit_session` refuses every connection until `TRANSITION_COMPLETE` (B8), so T1 and T2 cannot run.
- **L8** ends A's backend after the role default is known committed.
- **L5, L10 and the freeze** keep the legacy *process* from running, and with it any not-yet-connected A.
- **After L11:** any accepted-build session that does appear is fresh, so it inherits the read-only default and its
  locking `SELECT` fails with 25006 before a row lock, under premise R36-3.

The A edge therefore cannot form, and T1/T2 order under D-LP-7 and D-LP-9(a). This is a **conditional** result: it
depends on the freeze, on Q-36-1 and on R36-3. A legacy process already executing filesystem work is **not** stopped
by L8; only the freeze and the L5/L10 verification address it.

#### 5.8.3 R8-3(a): bootstrap state machine

**Durable transition state** (column `transition_state` of `gas.preservation_activation`, created by V0026) and
observed schema state:

| State | How recognised | Ordinary `admit_session` | Permitted next |
|---|---|---|---|
| **S0 FRESH** | no `gas` schema, or an empty history | n/a (no gate) | B3 (all migrations V0001…V0026) |
| **S1 ACCEPTED_V0025** | history top = V0025; no singleton | n/a (no gate); legacy builds could run, hence the freeze | B3 (V0026) |
| **S2 INSTALL_UNKNOWN** | migration outcome not known (lost connection at COMMIT) | refuses (S3) or n/a (S1) | B4: drain the Flyway backends, then read the history for V0026 success → S3 or S1 |
| **S3 DRAIN_PENDING** | singleton exists with `transition_state = 'DRAIN_PENDING'` | **refuses** (GA002: "transition not complete") | B6 → B7 → B8 |
| **S4 TRANSITION_COMPLETE / NOT_ACTIVATED** | `transition_state = 'COMPLETE'`, state NOT_ACTIVATED | admits, with preservation disabled (pre-ACTIVE metadata allowed) | MA run → B9 |
| **S5 ACTIVE** / **S6 REFUSED** | state | admits | MA runs |
| **S7 MAINTENANCE_OPEN** | an OPEN `maintenance_run` (any S3…S6) | **refuses** | `maintenance resume` or `maintenance abort` |
| **S8 PHYSICAL_RESTORE** | identity columns equal; OS evidence of a restore (operator) | admits per state (limitation, §5.8.8) | `verify-restore` recommended |
| **S9 LOGICAL_RESTORE** | `cluster_system_identifier` or `database_oid` ≠ current | **refuses** | `verify-restore` (bound restore bootstrap B11) |

**Bootstrap order** (direction §4; numbered B1…B10 to match items 1–12):

| B | Action | Connection | Crash or unknown outcome here |
|---|---|---|---|
| B1 | F1 exclusive (L4) + legacy exclusion (L1–L5) | — | nothing durable; restart |
| B2 | **Bootstrap controller:** a dedicated non-pooled connection from `MaintenanceConnectionFactory` (`application_name = genealogy-archive-maint-<run>`, `default_transaction_read_only = off`, **no** epoch declaration). `pg_try_advisory_lock(7340, 2)`. **Pre-gate inspection:** schema present? history top? singleton present? OPEN run? Only catalog and history reads, which work in every state. The run id is generated in memory. Registered connections are tracked **in process** (the tables may not exist yet). | controller | nothing durable; the advisory goes with the session |
| B3 | **Flyway** `migrate` through `SchemaMigrator` on a **maintenance DataSource**: non-pooled, every connection created by the same factory and registered in process, with `lock_timeout` and `statement_timeout`. Accepted behaviour is kept: one transaction per versioned migration including its history row; no `group`; `outOfOrder` false; validation on migrate; refusal when AHEAD, FAILED or INVALID. **V0026** runs in **one** transaction, in this order: (i) create the 8 control tables (§5.8.8); (ii) seed the singleton (`NOT_ACTIVATED`, protocol 1, epoch 1, `transition_state = 'DRAIN_PENDING'`, binding columns from Flyway placeholders `${installationId}`, `${hostId}`, `${leaseObject}`, `${runId}`, and `cluster_system_identifier`/`database_oid` read in SQL); (iii) create the functions (`gate`, `admit_session`, `admit_maintenance`, `maintenance_begin`, `require_maintenance`); (iv) insert the **TRANSITION run row** (OPEN, controller pid and `backend_start` from placeholders that the controller read about itself in B2); (v) create the gate triggers `ENABLE ALWAYS` on every table; (vi) `ALTER ROLE ${applicationRole} IN DATABASE <current_database()> SET default_transaction_read_only = on` (dynamic SQL; the configured role is the connecting role, so it is "for self"); (vii) the history row (Flyway). Every statement after (v) passes the gate because V0026's **first** statement is `SET LOCAL gas.preservation_protocol = '1'; SET LOCAL gas.activation_epoch = '1'` and its rows match. | Flyway connections | **before COMMIT:** the whole of V0026 rolls back (role default included) → S1. **Unknown:** S2 → B4. |
| B4 | **Known-COMMIT determination:** close the maintenance DataSource; confirm from the controller that every registered Flyway backend `(pid, backend_start)` is gone (terminate with timeout if not); then read the history: V0026 success → S3, else S1. Only the controller connection, which survived, is used. | controller | repeatable |
| B5 | **Run adoption:** the controller now declares identity per transaction through `maintenance_begin(run)` (§5.8.4). It persists the in-process connection registrations into `maintenance_connection`. | controller | the OPEN run row survives (S7 ∧ S3). Resume re-adopts it. |
| B6 | L8: terminate legacy backends (not rolled back) | controller | repeatable; terminations stay done |
| B7 | **Coverage verification** (§5.8.8): catalog check of every relation, trigger event, timing, level, function OID and enabled-ALWAYS state; the role default row for the configured role and database; function definitions (by OID and a stored digest) | controller | read only |
| B8 | L10, then `UPDATE preservation_activation SET transition_state = 'COMPLETE'` in a `maintenance_begin` transaction | controller | before COMMIT → still S3 (repeat B6–B8); unknown → read after confirming the backend is gone (the controller itself: if it is lost, resume uses a new controller and the drain rule §5.8.5) |
| B9 | **Normal MA phase** within the same run: the action connection runs the activation barrier, audit and publication (§5.7, CU-4/CU-5) | action | §5.8.4 / §5.8.5 |
| B10 | Close the run (§5.8.4) and exit (F1 released) | controller | the run stays OPEN → S7 → resume |

**No ordinary writer between gate installation and drain.** From V0026's COMMIT until B8 commits, every ordinary
connection fails `admit_session` (S3). Compatible services cannot even take F1 while `transition` holds it
exclusively. The pre-seeded NOT_ACTIVATED row therefore does **not** admit pre-ACTIVE metadata; only `COMPLETE` does.

**Fresh installation (S0).** The same machine runs: B3 applies V0001…V0026, and B6 has no backends to end (L5 still
runs).

**Permitted bootstrap connections, and why this is not an ungated runtime connection:**
1. Only `MaintenanceConnectionFactory` makes them, only while its process holds F1 exclusive and its controller holds
   advisory (7340, 2).
2. **Before V0026 commits**, no gate exists. Their writes are DDL and seed under F1, the freeze and L5.
3. **After V0026 commits**, they are gated like every connection. Their declaration comes only from
   `maintenance_begin` / `admit_maintenance` for the OPEN run (§5.8.4, §5.8.5).

There is no flag that disables layer B.

**Logical restore (S9).** `verify-restore` is bootstrap path B11.
1. F1 exclusive and the advisory.
2. A controller whose `admit_maintenance` is allowed in S9 **only** for a run of kind `RESTORE_REBIND`, inserted
   under advisory self-check, the bootstrap exception: the gate admits an INSERT into `maintenance_run` when no run is
   OPEN and the inserting session itself holds (7340, 2), verified on `pg_backend_pid()`.
3. Record the old identity in the run.
4. Re-install the role default (the dump may not carry it) and verify coverage.
5. Require the operator statement that the database and storage were restored **as a coherent set**, and record it.
6. Rebind `cluster_system_identifier`/`database_oid` (and the host ID if the host changed: `rebind-host`).
7. Run the admission audit; publish epoch + 1; close.

#### 5.8.4 R8-3(b): epoch and protocol identity handover

**Rule.** Maintenance connections carry **no session-level epoch or protocol declaration**. Every maintenance
transaction (controller, action, Flyway after the gate exists) begins with:

```sql
SELECT gas.maintenance_begin(:run_id);
-- 1. requires an OPEN maintenance_run row :run_id;
-- 2. requires this session to be registered for that run (pid and backend_start in maintenance_connection), and the
--    run's controller (recorded pid and backend_start) to hold advisory (locktype 'advisory', database = this
--    database's OID, classid 7340, objid 2, objsubid 2 = two-int4 key space, mode 'ExclusiveLock', granted);
-- 3. reads the COMMITTED singleton (a READ COMMITTED statement snapshot) and executes
--    set_config('gas.preservation_protocol', P, true), set_config('gas.activation_epoch', E, true)
--    (transaction-local);
-- 4. raises GA003 on any failure, before any business lock is taken.
```

Every later gated write in that transaction therefore uses the identity of the **committed** singleton. A publication
updates the singleton and, in the same transaction, re-runs `set_config(…, true)` with the new values for its
remaining inserts. When it commits, the local settings vanish. The **next** transaction on **any** maintenance
connection reads the new committed values in its own `maintenance_begin`. **No connection can carry a stale
identity into a later transaction**, because none carries one at session level. A maintenance write outside a
`maintenance_begin` transaction has no declaration and fails GA001 (fail-closed).

**Successful sequence without GA001:**

```
controller C (idle, session advisory)        action A
                                             BEGIN; SELECT maintenance_begin(R) -> P1,E (local)
                                             LOCK … NOWAIT; audit; UPDATE singleton SET epoch=E+1 …  (gate: E = E ✓)
                                             SELECT set_config('gas.activation_epoch', E+1, true)
                                             INSERT event, findings  (gate: E+1 = E+1 ✓); COMMIT (known)
BEGIN; SELECT maintenance_begin(R) -> P1,E+1 (local)
UPDATE maintenance_run SET state='CLOSED' (gate: E+1 = E+1 ✓); COMMIT
pg_advisory_unlock(7340,2); close; JVM exits -> F1 released
```

| Case | Handling |
|---|---|
| New activation epoch | as above |
| New protocol version (a future protocol migration) | the migration's first statement is `SELECT gas.maintenance_begin('${runId}')` (Flyway placeholder, explicit in the SQL, no callback dependency); it updates the singleton to (P + 1, E + 1, NOT_ACTIVATED) and `set_config`s the new values; later transactions read them |
| Action connection after COMMIT | next `maintenance_begin` |
| Controller | next `maintenance_begin` |
| Registered additional connections | the same rule; registration lives in `maintenance_connection` |
| Flyway connections | before V0026: no gate; V0026 sets its own local identity (B3); later migrations call `maintenance_begin` first |
| Run closure | a `maintenance_begin` transaction, as shown |
| Unknown publication COMMIT | identity choice needs no outcome: the next `maintenance_begin` reads whatever committed. The **outcome** (published or not) is determined by CU-21 / §5.8.5 before the run proceeds. |
| Crash between publication and close | no refresh exists to be lost. Resume (§5.8.5) re-adopts the run, and its `maintenance_begin` reads the committed epoch. |
| Resumed maintenance | the same; the new controller is registered and rebound first |

**Ordinary processes are unchanged:** a compiled protocol plus the epoch read at start under F1, declared as startup
options, validated by `admit_session` on every physical connection. Their epoch cannot change while they hold F1
shared.

#### 5.8.5 R8-4: maintenance authority, controller loss and backend drain

**Authority statement.** On the approved host, **F1 exclusive is the ultimate run-lifetime process-exclusion
authority.** While the maintenance JVM lives, no compatible process, maintenance or ordinary, can start on the host.
The controller advisory **serialises database-side controllers** and records which backend is the controller. The
OPEN row is **recovery information** that keeps services out after a crash. `require_maintenance` and
`maintenance_begin` are **point checks**, not fencing tokens: a controller may be lost one instant after a check
succeeds. Under single-host F1 that is acceptable, by the bounded-continuation rule below. **No remote maintenance
controller is admitted:** `admit_maintenance` requires the host ID of the binding. Cross-host maintenance would need
a different fencing architecture (§5.8.10).

**Single active phase.**
- The maintenance JVM runs **one phase at a time** on one thread, where a phase is one business window, one
  filesystem primitive, one Flyway migration, one barrier/audit transaction or one journal transaction.
- At most one action connection has an open transaction. The controller opens transactions only while no action
  transaction is open, and it never holds a table or row lock across a phase: between its own short transactions it
  holds only its session advisory.
- Therefore no controller statement can wait on the action connection, and no action statement can wait on the
  controller. The idle controller cannot self-block.

**Loss detection and latching.**
- The controller is probed (`SELECT pg_backend_pid(), now()`) before every phase and every 10 s while idle. Each
  `maintenance_begin` also re-checks the advisory.
- Any failure (connection error, advisory not shown, pid or `backend_start` changed) sets the process-wide
  **MAINTENANCE_FENCED** latch, which is never reset in that JVM.
- After the latch: **no new phase** starts, including any unstarted filesystem primitive or any next window of an
  action.
- **Bounded continuation:** the one phase already admitted may finish.
  - A database transaction that has passed `maintenance_begin` may commit or roll back. Its outcome is **unknown**
    until determined below.
  - A filesystem primitive already called (one `move`, `copy` or `delete`) completes or fails.
- **Why the continuation cannot overlap a replacement:** a replacement maintenance process needs F1 exclusive, which
  this JVM holds until it exits. It exits only after the drain below. A replacement on another host is outside the
  design (Q-36-1).

**Drain before exit or replacement.**
1. Wait for the admitted phase to end. It is bounded by the action connection's `statement_timeout` and the
   primitive's own completion.
2. Close the action and Flyway connections.
3. From a **fresh** connection (or the controller, if alive), for each registered `(pid, backend_start)` of this run:
   if still present in `pg_stat_activity` for this database, `pg_terminate_backend(pid, 10000)`, then confirm it is
   absent (with `pg_stat_clear_snapshot()`).
4. If the database is unreachable or a backend survives, exit with "drain unconfirmed". The OPEN row remains, and
   resume must complete the drain first.
5. Only then exit, which releases F1.

A killed maintenance JVM (SIGKILL) skips this. Its server-side transactions may stay alive until PostgreSQL notices
the closed socket. Resume handles that.

**Resume (`maintenance resume`, CU-20):**
1. F1 exclusive.
2. A new controller with `pg_try_advisory_lock(7340, 2)`. If refused, a live old controller backend exists:
   terminate it by its recorded `(pid, backend_start)` and retry. This works only when that pid and start match the
   row, so an unrelated session is never terminated.
3. **Old-run reconciliation:** every `maintenance_connection` row of the run whose `(pid, backend_start)` is still in
   `pg_stat_activity` is terminated with timeout and confirmed absent. Every other application-role backend except
   the new controller is treated as stray (L8 rule).
4. Rebind:
   `UPDATE maintenance_run SET controller_pid, controller_backend_start, controller_generation = controller_generation + 1`
   in the bootstrap-exception transaction. The gate admits it because the inserting session itself holds (7340, 2) and
   the row is OPEN.
5. Determine outcomes (§5.8.6), only after step 3.

**Bound maintenance admission versus ordinary admission:**
- `admit_session` (ordinary) refuses while any run is OPEN or `transition_state ≠ COMPLETE`.
- `admit_maintenance(run)` (maintenance factory only, as connection-initialisation SQL) requires all of: the OPEN run
  `run`; this host's ID equal to the binding; and the run's controller pid/`backend_start` holding (7340, 2) in this
  database, or the session itself holding it for the controller connection. It then registers the session in
  `maintenance_connection`.
- The ordinary factory never sets the run setting, so it cannot obtain maintenance admission.

**Waits (narrowed, replacing "nothing waits"):**
- These never wait: F1 `tryLock`, `pg_try_advisory_lock`, and CU-4's `LOCK … NOWAIT` statements.
- These can wait, each bounded:
  - connection establishment (`connectTimeout`);
  - `pg_terminate_backend(pid, timeout)` (its timeout);
  - catalog reads and run-row writes (`lock_timeout` 5 s, `statement_timeout` 60 s on maintenance connections);
  - Flyway DDL (`lock_timeout`);
  - startup waiting for F1 shared (supervised, with no effects before acquisition).
- No maintenance statement is claimed never to wait.

#### 5.8.6 R8-5: journal and replay correctness

**Withdrawn:** R36-5 ("every business window is a CAS; repetition is NOT_APPLIED") is **inoperative**. Accepted source
contradicts it:
- `markStagingCleaned` updates `updated_at` unconditionally;
- `note` appends an event each time;
- `UnresolvedObjects.release` throws on a closed record;
- `QuarantineMoves.move` finds or inserts (`ON CONFLICT DO NOTHING`);
- `settle` examines and renames **before** its state CAS.

**Stable identities and records** (journal schema, final; §5.8.8):

| Record | Key | Meaning |
|---|---|---|
| Logical action | `maintenance_action.action_id`; unique `(run_id, seq)` | one authorised decision with an immutable scope, authorisation and precondition snapshot |
| **Original committed window** | `maintenance_action_window` unique `(action_id, window_no)`; `window_no` is fixed by the action kind's plan (A-3: 1 pending settlement, 2 intent, 3 settlement, 4 final, 5 staging mark; A-1: 1; A-2: 1; A-4: 1 intent, 2 settlement; A-5: 1) | inserted **in the same transaction** as the business window; exists iff that window committed |
| **Attempt** | `maintenance_action_event` (kind `ATTEMPT_STARTED`, `ATTEMPT_ROLLED_BACK`, `ATTEMPT_UNKNOWN`) with `attempt_no` | written in a **separate** transaction (before, and after a known rollback). A failure description therefore survives a rolled-back business transaction. |
| **Observation** | `maintenance_action_event` (kind `OBSERVATION`) | what a probe saw (path, presence, size, SHA-256, file key, times, inconclusive reason) and when |
| **Filesystem reconciliation** | `maintenance_action_event` (kind `FS_RECONCILED`) | the reconciliation decision with its evidence (it never claims that PostgreSQL committed a rename) |
| **Follow-up action** | a new `maintenance_action` with `follows_action_id` | any new authorised decision after a failure; never a silent continuation |

**Unknown-COMMIT algorithm** (any window of any action):
1. Confirm that the original backend can no longer commit: drain by `(pid, backend_start)` (§5.8.5).
2. Read the window key `(action_id, window_no)`.
3. Read the authoritative business state of the window's rows.
4. **Row present:** the window committed. Reuse its recorded outcome, and **do not** repeat its mutation, event or
   timestamp. Record `ATTEMPT_UNKNOWN` resolved as committed.
5. **Row absent:** under the atomic journal contract the business transaction did not commit. Verify this against
   the business state with the window's **expected post-state predicate** (table below).
6. **Inconsistent** (row absent but post-state present, or row present but post-state absent and not explained by a
   later window): **stop**, record an `OBSERVATION` of the inconsistency, and refuse to continue that action. The run
   stays OPEN for review; it is never silently treated as a no-op.
7. Reconcile external filesystem effects **separately** (matrix below).
8. Record truthful observations, including "inconclusive".
9. Persist the failure or rollback description in a separate `maintenance_begin` transaction.

**Resume set.** INTENDED actions (after J0, possibly before any window) and IN_PROGRESS actions are resumed. FAILED
actions are terminal for that action: a new follow-up action is needed. `maintenance abort` records
`ABANDONED_AT_SAFE_BOUNDARY`, with the remaining durable work described (for example "Q INTENDED at
volume:source→dest; ordinary recovery settles after ACTIVE").

**Filesystem reconciliation of a quarantine move** (accepted `settle` rules; a path's existence is never taken as
identity):

| Source | Destination | Recorded evidence | Outcome |
|---|---|---|---|
| present | absent | trusted, matching (attested: full V0020 identity and binding) | may move (the accepted `settle` rename, then re-verify what arrived) |
| present | absent | absent-at-intent, no evidence, untrusted, or not matching | **not moved, not adopted** (accepted refusal); the action FAILS: "source not identified" |
| absent | present | trusted, and the destination object matches (`arrived`) | settle QUARANTINED ("an earlier attempt had already moved it") |
| absent | present | no evidence or no match | **refuse** (accepted); FAILED with an observation |
| absent | absent | any | settle SOURCE_ABSENT |
| present | present | any | **refuse** (accepted `FileAlreadyExistsException`) |
| inaccessible or inconclusive at either end | | | no outcome; the attempt is recorded as inconclusive; retry later |

**A-3 cancellation** (`requireCancellable`: kind ≠ DESTROY; BLOCKED or WAITING_FOR_VOLUME). The accepted atomic
transaction is kept: `claimBecomesUnresolved` **or** `releaseTarget`, **and** the CANCELLED transition, in one
transaction (`applyCancel`, `OperationAdminService` lines 269–280). It is window 4, and its journal row is inserted
in **that** transaction.

| Window | Accepted effect | Claimant revalidation (inside the window's transaction, after Ct(X) U where it applies, immediately before the keyed release) | Post-state predicate |
|---|---|---|---|
| 1 pending settlement | `settle` of each own INTENDED entry | before `releaseTargetsAt(source)`: the claimants at the source are ⊆ {this operation} ∪ final operations; otherwise roll back → FAILED | Q not INTENDED |
| 2 intent | `move` → `intend` (find or insert). **Recorded-absent** intent if nothing is there: null size, time and hash, `ABSENT_AT_INTENT`; trusted evidence is stored only for a present, matching object. | — (no release) | Q exists for (volume, `cancelled` destination) |
| 3 settlement | `settle` (filesystem first, then the state CAS + `releaseTargetsAt` + U resolution + generation retirement) | as window 1 | Q QUARANTINED or SOURCE_ABSENT |
| 4 final (atomic) | unverified copy: `claimBecomesUnresolved` (U OPEN + own claim released) **or** release-only `releaseTarget` **or** nothing; **plus** the CANCELLED transition with `quarantine_path` set when Q is QUARANTINED | before `claimBecomesUnresolved` or `releaseTarget`: the claimants at the target are ⊆ {this operation} ∪ final operations, and **no OPEN U** at the target belongs to another role | operation CANCELLED (+ U OPEN, or claim ended) |
| FS staging | `StagingArea.delete` (returns true when already absent) | — | staging absent |
| 5 staging mark | `markStagingCleaned`, **only if** `staging_cleaned = false` (the predicate is read in the window; never re-run once the window row exists) | — | `staging_cleaned = true` |

**The ordinary owned target is the passing case.** The cancelling operation's own `target_created` claim is
**allowed**: it is what A-3 resolves. The disqualifying conflicts are: a **non-final foreign** claimant; an **OPEN
U** at the target of another writer role (AU-4); a changed operation identity (kind, state, target, target
generation, attempts against the J0 snapshot); and DESTROY.

| State found on resume | Next |
|---|---|
| action INTENDED, no window row | revalidate eligibility (J1) → continue, or NOT_APPLIED |
| own Q INTENDED | filesystem reconciliation, then window 3 |
| Q QUARANTINED, operation still eligible | window 4 with `quarantine_path` = Q destination |
| Q SOURCE_ABSENT, operation still eligible | window 4 (release-only path as accepted) |
| unverified-copy decision recorded in J1 | window 4 atomic (U + release + CANCELLED) |
| CANCELLED **with** window 4 row | completed; continue at FS staging |
| CANCELLED **without** window 4 row | inconsistent (cancelled by another path?): stop; observation; not counted as this action's success |
| CANCELLED, staging not cleaned | FS staging, then window 5 |
| staging already absent, flag false | window 5 only |
| ineligible, DESTROY, or changed identity | NOT_APPLIED (J1), never forced |

`performCancel`'s `false` return ("not applied") is never treated as proof that a cancellation did not happen; the
window row and the business state decide.

**A-1 attestation** (accepted `attest`: V0020 identity evidence, named Administrator; it enqueues
`JOB_QUARANTINE_ATTESTED` as accepted, and the job stays queued while preservation is disabled):
- **Completion predicate:** U attested, its `attested_by` = the action's named Administrator, its decision evidence
  CONFIRMED, its recorded identity equal to the J0-observed identity, **and** window 1's row.
- **Already attested without this action's row:** NOT_APPLIED, with an observation "attested earlier by …, at …".
  There is no second attestation and no second job.

**A-2 release:**
- **Completion predicate:** U RESOLVED with resolution RELEASED **and** window 1's row.
- **Closed otherwise** (another resolution, or by another actor): NOT_APPLIED, with an observation. The accepted
  method is never invoked on a closed record (it would throw).
- **Precondition, immediately before the decision, inside window 1:** no non-final claimant, own or foreign; only
  the exact generation-own relation may pass.

**A-4 attested move** (accepted `quarantineAttested` eligibility **unchanged**):
- U open and attested; kind is not CURRENT_COPY and not QUARANTINED_COPY; content not DESTROY_PENDING or DESTROYED;
  full V0020 binding.
- **Windows:** intent and settlement, through the filesystem matrix.
- **Completion predicate:** Q QUARANTINED with U RESOLVED QUARANTINED, or Q SOURCE_ABSENT with U RESOLVED ABSENT, and
  window 2's row.
- Attestation alone never broadens eligibility.
- **Byte classes (terminology corrected):**
  - **Authoritative originals:** the objects at the storage path of a generation in **CURRENT** state, and of
    QUARANTINE_PENDING generations, whose move belongs to accepted superseded-generation quarantine. **No resolution
    action moves or deletes them.**
  - **An attested unidentified object** at the storage path of a historical **UNRESOLVED** generation is **not** an
    authoritative original. A-4 may move it, as accepted (`retireGeneration` UNRESOLVED → QUARANTINED).

**A-5 administrative hold (D-LP-8 (vi), conditional), one window:**

```sql
UPDATE gas.file_operation
   SET state = 'BLOCKED', blocked_reason = 'STATE_UNKNOWN', message = :m, updated_at = clock_timestamp()
 WHERE operation_id = :o
   AND kind IN ('INGEST', 'REPAIR') AND state IN ('PROMOTING', 'PROMOTED') AND state = :expectedState
   AND target_path IS NOT DISTINCT FROM :tp AND target_generation IS NOT DISTINCT FROM :tg
   AND target_created = :tc AND attempts = :a
   AND expected_size IS NOT DISTINCT FROM :es AND expected_sha256 IS NOT DISTINCT FROM :eh
```

- It runs inside the accepted `transition` window (owner advisory first), with its event and window 1's row.
- **Completion predicate:** BLOCKED STATE_UNKNOWN with window 1's row. Zero rows means NOT_APPLIED.
- Target, generation, high water, `target_created`, owner row and expected evidence are unchanged. The hold is not a
  waiver.

**A-6** is deferred and **inoperative**: no table, command or code path exists for it.

#### 5.8.7 R8-6: audit corrections (deltas to §5.7.9)

**AU-2: targetless and pre-plan states.** Source: `OperationProcessor.plan` checks staging **before** any content id
or target is assigned (lines 105–122). Ingest planning assigns `content_id` Y, target generation 1 and the target in
one transition; `planRepair` (Wh1a) assigns a REPAIR's target.

| Kind | State | Blocked reason | Required (no target created) | Class |
|---|---|---|---|---|
| INGEST | RECEIVING, STAGED | — | `content_id`, `target_path`, `target_generation` **null**; `target_created` false; **no** owner row; no Q or U naming it | ADMITTED |
| INGEST | BLOCKED | STAGING_INVALID (from `plan`) | as above | ADMITTED (cancellable; not retryable, as accepted) |
| INGEST | STAGED or BLOCKED STAGING_INVALID **after a COLLISION retry** | — / STAGING_INVALID | the earlier private plan retained: Y unpersisted, `target_path = originalPath(Y, t)`, `target_created` **false**, no owner row | ADMITTED (history; AU-7 not able) |
| REPAIR | RECEIVING, STAGED, BLOCKED STAGING_INVALID (from `plan`) | — / STAGING_INVALID | `content_id` X **persisted**; `target_path` null **or** an earlier plan with `target_created` false; no owner row | ADMITTED (**never private**) |
| INGEST or REPAIR | BLOCKED | STAGING_INVALID (from PROMOTING: staging missing or changed), PROTECTION, VERIFY_MISMATCH, UNSAFE_PATH, STATE_UNKNOWN, COLLISION | a target as §5.6.5; `target_created` true ⇒ own owner row; false ⇒ no owner row | per AU-4/AU-7 |

A BLOCKED operation with `target_created = true` and no own owner row, or with an owner row and `target_created =
false`, is AU-3 UNSAFE-LEGACY. No target is ever manufactured.

**AU-2: the operation's quarantine pointer** (`file_operation.quarantine_path`). Its writers (source):
- the replan transition in `quarantinePartialAndReplan`, which sets the PARTIAL_COPY destination together with the
  new target;
- the final CANCELLED transition in `applyCancel`, which sets the CANCELLED_COPY destination when the move
  QUARANTINED.

The rule is transition-specific, and all Q history is preserved and unchanged:
- The replan transition sets the pointer to `moved.quarantinePath()` **whether the partial settled QUARANTINED or
  SOURCE_ABSENT** (source, lines 349–381).
- Only replans change an INGEST's or REPAIR's target, and each moves it to a new generation path.
- After a crash between the partial's settlement and its replan, `promote` settles the entry and **writes the same
  target again, without a replan**. That partial is then never followed by a pointer update.

Validation:
- Let `QP*` be the operation's settled PARTIAL_COPY entries whose `source_path` ≠ the current `target_path`; their
  replan committed. Let `QC` be its CANCELLED_COPY entry.
- **CANCELLED with QC QUARANTINED** whose source = the current target: the pointer = QC's destination (set by the
  final transition).
- **Otherwise:** the pointer = the destination of the **newest** entry of `QP*` (by attempt suffix), or **null** if
  `QP*` is empty.
- PARTIAL entries whose source = the current target (the crash-then-rewrite case, possibly more than one over time)
  are valid history and never the pointer's subject.
- Any other value, or a pointer naming a Q of another operation, is UNSAFE-LEGACY.

**AU-5: recorded-absent intent** (source: `QuarantineMoves.move` / `QuarantineRepository.intend`). A move whose
source is absent at intent stores `source_size`, `source_modified_micros` and `source_sha256` all **null**, with
`detail = ABSENT_AT_INTENT` (`recordedAbsent()`), and keeps the evidence basis as recorded. This applies to **every
reason** that `move` serves: PARTIAL_COPY, UNVERIFIED_COPY, CANCELLED_COPY and ATTESTED_OBJECT.

| Recorded evidence | AU-5 admission | Settlement meaning (accepted, unchanged) |
|---|---|---|
| **recorded absent** (three nulls + `ABSENT_AT_INTENT`) | ADMITTED if the role's **source relation** holds. The size and hash bindings of §5.7.9 are **not applied**: there are no values. The three columns must be null together. | both ends absent → SOURCE_ABSENT. An object that **later** appears at the source is not authorised by the earlier absence: it stays unmoved and unadopted. A destination-only object is not confirmed (no evidence). |
| trusted, present | the §5.7.9 per-reason bindings (trusted partial prefix; measured copy; observed copy **or** expected values; attested identity) | as accepted |
| no evidence (before V0017) or UNTRUSTED (before V0019) | ADMITTED if the source relation holds | the move is refused while an object is present; an Administrator must verify (accepted messages) |

- A **present object lacking identifying evidence** therefore stays a visible refusal. It never becomes a trusted
  move.
- **SOURCE_ABSENT** settled entries are admitted with any of the three evidence kinds. Their destinations never
  received bytes, so AU-6 expects no object reference there beyond the Q itself and the operation pointer rule above.
- The A-3 window 2 wording "expected_* when absent" is corrected: those values are the **argument** to `move`, but
  what is persisted for an absent source is three nulls.

**AU-8: constructor-provenance scope.**
- The constructor claim (R36-6, now **R37-6**) covers **only the eight AU-8 identity columns**: generation
  `storage_path` and `quarantine_path`, operation `target_path` and `quarantine_path`, Q `source_path` and
  `quarantine_path`, U `path`, and owner `path`. It covers them only for their audited application writers
  (`ManagedVolume.originalPath`, `quarantinePath`, or a relational copy of another identity column).
- It does **not** apply to `integrity_finding.path` or other observation fields. `IntegrityService` deliberately
  records enumerated unexpected paths, link names, the directory label `originals` and staging leftovers. These are
  **observations**: they never become ownership identities, and a non-constructor or Unicode observation path never
  refuses activation.
- `PortableRelativePath`'s accepted NFC Unicode behaviour is unchanged.
- A non-constructor value in an ownership identity column stays UNSAFE-LEGACY whatever its characters. Ambiguous
  ownership identities stay fail-closed (non-waivable).
- Repository methods accept arbitrary strings, so the provenance claim is an audited-writer property checked by the
  audit, not a database constraint.

**AU-4: "exactly one role"** means one **writer and provenance classification** per U, not one physical join row: a
same-object closure may join several references.

**Per-check summary after 3.7** (invariant, source writers, passing, failing, joins, fixtures; full text in §5.7.9
plus the deltas above):

| Check | Passing additions in 3.7 | Failing kept | New fixtures |
|---|---|---|---|
| AU-1 | — | as §5.7.9 | — |
| AU-2 | targetless pre-plan and STAGING_INVALID states (INGEST, REPAIR); retained earlier plans; transition-specific quarantine pointer, including the crash before replan | a manufactured or claimed target where none was created; a pointer from no assigning transition | AA-11d (targetless BLOCKED INGEST), AA-11e (targetless BLOCKED REPAIR), AA-11f (partial history then cancellation pointer), AA-11g (crash after partial settlement before replan) |
| AU-3 | — | owner/`target_created` mismatch | — |
| AU-4 | writer-classification wording | non-final claimant; another object's generation row | — |
| AU-5 | recorded-absent intents for every reason; pre-V0017 and pre-V0019 evidence; SOURCE_ABSENT | role relation broken; partial nulls | AA-14c (recorded-absent INTENDED per reason), AA-14d (SOURCE_ABSENT), AA-14e (present object without evidence → admitted; settlement refuses) |
| AU-6 | SOURCE_ABSENT destinations | foreign references at a destination | — |
| AU-7 | targetless and retained-plan states are not "able" | duplicate able or holding target generation | — |
| AU-8 | observation paths out of scope; Unicode NFC observation paths | ambiguous identity aliases; non-constructor identity values | AA-9b (Unicode unexpected-object finding does not refuse) |

#### 5.8.8 Other specification corrections

**Startup and offline commands:**

| Process or command | Accepted behaviour | 3.7 behaviour | Owner approval? |
|---|---|---|---|
| `help`, `version`, `check-config` | effect-free | effect-free: **no lease, no database** | no |
| `status` | read-only database inspection | **no lease**; a *diagnostic* connection: read-only (`default_transaction_read_only = on`, set explicitly), no declaration, plain `SELECT`s only, allowed in every state (layer B gates only DML) | no |
| `serve` with PostgreSQL unavailable | starts; status page shows "not ready" (`initializationFailTimeout(-1)`) | **kept as diagnostic mode.** It takes F1 shared, then serves only the status page until a bootstrap read succeeds. Then, once, CU-14 eligibility. If that is refused (S3, S7, S9, binding mismatch), it **stays** in diagnostic mode with the reason (no business routes) instead of exiting. | no (accepted behaviour kept) |
| GA002 at a **later** reconnect (identity changed under a live process) | n/a | process latches FENCED and exits | yes: part of D-LP-8 (v-a) |
| `worker` | refuses to start unless ready | unchanged (exit); additionally refuses unless eligible | no |
| `init-archive`, `create-admin`, `register-volume`, `init-volume`, `set-ingestion-volume` | writers | F1 shared + F3; require S4…S6 (`TRANSITION_COMPLETE`). They are **not** pre-gate commands. | no |
| `provision-installation` | new | effect-free except writing the configuration key and (offline, admin) the lease object | yes (v-a) |

**Disabled-preservation exceptions** (the precise list replacing "E-1…E-21 refused"). While not ACTIVE, the
preservation operations marked *refused* in §5.6.4 are refused. **Permitted**, each holding F1 shared and passing F3:
- E-11's READ_ONLY download branch (§5.7.10);
- E-12 volume administration;
- E-14 job bookkeeping (claim, renew, succeed or fail of non-preservation kinds) and worker presence;
- THUMBNAIL generation (cache files only);
- E-24 and E-26 metadata.

**Control schema: 8 tables, 48 gated relations.**
- The 3.6 tables: `preservation_activation`, `preservation_activation_event`, `preservation_audit_finding`,
  `maintenance_run`, `maintenance_connection`, `maintenance_action`, `maintenance_action_window`.
- **New:** `maintenance_action_event` (attempts, observations, reconciliations).
- The binding columns live in the singleton, not in a new table.
- 40 + 8 = **48**, a **source count to be verified** by the catalog check below.

**Invariants:**
- **Singleton:**
  - exactly one row (`singleton boolean PRIMARY KEY CHECK (singleton)`, with an INSERT trigger refusing a second row);
  - DELETE refused by a row trigger; TRUNCATE refused by a statement trigger;
  - `protocol_version` and `epoch` strictly increasing (row trigger: NEW > OLD on change; `epoch < 2^62`);
  - `transition_state` only DRAIN_PENDING → COMPLETE;
  - identity and binding columns changed only by B8, B11 and `rebind-host` runs.
- **Event and finding rows:** INSERT only (row trigger refuses UPDATE and DELETE; statement trigger refuses
  TRUNCATE). FK to `(run_id)`; `epoch` equals the epoch published in the same transaction.
- **Run:**
  - at most one OPEN (partial unique index);
  - state changes OPEN → CLOSED or OPEN → ABORTED only;
  - `controller_*` columns change only through a rebind (`controller_generation + 1`);
  - the immutable columns are `run_id`, `kind`, `opened_at`, `host`, `operator_principal`.
- **Connection rows:** INSERT only; `(run_id, pid, backend_start)` unique.
- **Action rows:**
  - `(run_id, seq)` unique;
  - immutable: scope, authorisation, `named_administrator`, `reason`, precondition snapshot;
  - mutable: only `state`, forward (INTENDED → IN_PROGRESS → COMPLETED | NOT_APPLIED | FAILED |
    ABANDONED_AT_SAFE_BOUNDARY), and `updated_at`.
- **Window rows:** INSERT only, `(action_id, window_no)` unique.
- **Event rows (`maintenance_action_event`):** INSERT only.

**Trigger coverage verification (CU-12, CU-14, B7).** For every relation of `relkind IN ('r', 'p')` in schema `gas`
except `flyway_schema_history`, exactly one trigger must have:
- the exact `tgfoid` of `gas.gate`;
- `tgtype`: BEFORE, STATEMENT, INSERT | UPDATE | DELETE | TRUNCATE;
- `tgenabled = 'A'` (ALWAYS).

The function's definition digest must also match. A partition is checked **individually**: a direct write to a
partition fires that partition's statement triggers, not the parent's. Any future write target (a new table, a
partition, or a foreign or write-through relation) fails the check until it is covered.

**Statement-level semantics** (scope stated, to be confirmed by installation tests):
- BEFORE STATEMENT triggers fire for zero-row DML and for TRUNCATE.
- `INSERT … ON CONFLICT DO UPDATE` fires the INSERT and the UPDATE statement triggers.
- `MERGE` fires the statement triggers of its actions.
- `COPY FROM` fires INSERT's.
- DML performed by triggers fires the target's own gate.
- A DML statement takes its **relation-level** lock **before** its BEFORE STATEMENT trigger. "Before business row
  work" therefore does **not** mean "before any database lock". Early exclusion is F1, F2 and F3.

**Restore and same-cluster rewind:**
- System identifier and database OID detect a logical restore into a new database or cluster (S9). They do **not**
  detect a physical restore, a PITR or a rewind in the same cluster, which keeps both values and may keep an ACTIVE
  epoch.
- Every restore procedure therefore requires, operationally: all application processes stopped on the approved
  host (F1 exclusive held by `verify-restore`); no writer on any former host (Q-36-1; an unresponsive former host is
  **not** shown stopped by absent sessions, and the operator must establish it, for example by powering it off or
  isolating its storage and network); the database and storage restored **as a coherent set**, recorded by the
  operator; and `verify-restore`, which re-installs and verifies the role default and coverage and re-audits.
- After a physical restore the operator **must** run `verify-restore`. The tooling cannot detect that one happened,
  which is a stated limitation.

#### 5.8.9 Superseded language made inoperative

The places below are marked **[inoperative 3.7]** in place, and the operative replacements are in §5.8:
- §5.6.5 ADMITTED-REVIEWED / "unless a reviewed acceptance record exists";
- §5.6.7 A-6;
- §5.6.2 / §5.6.3 "never waits", "all old builds read-only", the bootstrap `FOR SHARE` wait and the step-4 authority;
- §5.7.2's fixed host-wide lease path;
- §5.7.7 "Nothing waits" and "never waiting", and the session-level epoch declaration of maintenance connections;
- §5.7.8 R36-5 and the resume rule;
- §5.7.9's AU-2 partial-pointer rule and AU-5's evidence table without recorded absence;
- R36-6's unscoped wording.

#### 5.8.10 If an assumption is not approved: the smallest alternative

| Not approved | Consequence | Smallest alternative |
|---|---|---|
| **Q-36-1** (one application host) | F1 cannot prove the termination of writers on another host. A database advisory cannot replace process or host death (eighth assessment §4). | **Single-writer-host by deployment, plus database login denial for other hosts:** a per-host login role (D-LP-8 (x)), with `pg_hba` restricting the role to the approved host's address. A host change then requires the old host's role `NOLOGIN` plus OS-verified shutdown. Live multi-host writers would need a fencing-token protocol carried into every filesystem step, which is **out of v1 scope** (ADR-0002 forbids distributed locking). |
| **D-LP-8 (xi)** (legacy change freeze) | L5/L10 are point observations; a legacy launch with a copied configuration between them is not excluded | Per-generation login roles (D-LP-8 (x)), with the accepted role set `NOLOGIN` **before** L5, so a legacy launch cannot connect at all. Its filesystem work without a database is then limited to effects possible without database reads (receiver staging needs a database insert first; workers need the database for every job), which reduces but does not eliminate the residual. |

#### 5.8.11 Control units added in 3.7

Business windows are unchanged: **19 P2-8 and 22 Phase 2**. CU-0…CU-21 are retained (with CU-10, CU-12, CU-14,
CU-20 and CU-21 amended as above), and nine are added, for **31 control units, CU-0…CU-30**:

| Unit | Entry | Effects |
|---|---|---|
| CU-22 Installation provisioning | `provision-installation` / install scripts | configuration key; lease root and object (offline) |
| CU-23 Lease-object verification | `LeaseHolder` at start, at step `begin`, every minute | path, type, owner, identity; FENCED on failure |
| CU-24 Bootstrap controller | `transition`, `verify-restore` (B2) | pre-gate inspection; advisory; in-process registration |
| CU-25 Gate-installation migration | B3 (V0026) | the one Flyway transaction |
| CU-26 Known-COMMIT determination | B4 | Flyway backend drain; history read |
| CU-27 Transition-complete publication | B8 | `transition_state = COMPLETE` |
| CU-28 Identity handover | `maintenance_begin` | per-transaction committed identity |
| CU-29 Controller-loss latch and drain | §5.8.5 | latch; bounded continuation; drain by `(pid, backend_start)` |
| CU-30 Journal reconciliation | §5.8.6 | unknown-COMMIT algorithm; filesystem matrix; attempt and observation rows |

Journal wrappers add statements and transactions (window rows, attempts, observations) and guard effects. These are
control scope, declared to the guard (D-LP-4), and are not hidden in the unchanged business count.

#### 5.8.12 Assumptions and proof obligations after 3.7

| ID | Statement | Status |
|---|---|---|
| Q-36-1 | One designated application host per installation; PostgreSQL may be remote under an approved application-host policy (sessions accepted only for the binding's host ID; `pg_hba` restriction recommended); no overlapping failover; a host move or restore requires verified old-writer exclusion; the lease is local and independent of NAS | **owner decision, not approved** |
| D-LP-8 (xi) | Continuous legacy change freeze, L1…L12 | **owner decision, not approved** |
| R36-2 | Windows lock release only after the holder's end; `NOSHARE_DELETE` prevents replacement | unexecuted; AA-17 |
| R36-3 | Accepted builds do not override read-only | unexecuted with real versions; AA-3b |
| R37-1 | V0026's single-transaction contents (including `ALTER ROLE … IN DATABASE` and the run insert) commit or roll back as one unit under Flyway | unexecuted; AA-19 |
| R37-2 | Flyway runs one transaction per versioned migration and uses only connections created by the provided DataSource | accepted configuration; confirm against the pinned version at implementation |
| R37-3 | Host ID (machine-id or MachineGuid) distinguishes hosts; cloned VMs must be re-provisioned | limitation; documented |
| R37-4 | PostgreSQL ends a killed JVM's backends when it detects the closed socket; resume terminates them by `(pid, backend_start)` | drain rule |
| R37-5 | Each window's post-state predicate distinguishes committed from not committed (§5.8.6) | per-window proof at implementation |
| R37-6 | Constructor provenance of the eight identity columns' audited writers | source inventory; scoped |
| R37-7 | The pointer rule above matches the only two writers of `file_operation.quarantine_path` (replan; final cancel) | source-derived at `7a97e70`; ArchUnit or inventory check at implementation |

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

### 6.3 Key classes and the order projection [3.3, F4-2; aligned with G1/G10 in 3.5]

| Class | Keys | Rule |
|---|---|---|
| **Ordered** **[3.5, aligned]** | exactly G1's ordered list: S, F, Ct, worksheet K rows, R, catalog Source then Repository; worksheet `file_capture_context(file_id)` (insert and delete). **Not** the L1p preservation rows (O, U, G, Q, owner rows; 3.3's "row locks of §4.1" wording included them and is withdrawn) | π(resource) = (level, sub-level, uuid). The worksheet key projects to **(L1d, file_id)**, the same point as the worksheet row lock on that File, so a row lock and a key on one File compare consistently. G1 checks them against all held or passed ordered resources at every level. |
| **L1p admitted (G10)** **[3.5]** | content-coordinated U (Ct(X) U held); owned O (live advisory, not LOST); terminal single-resource O or U (A1, A4-fast); contentless U (adv(u.operation)); content-scoped G and Q rows of X under Ct(X) U | No π and no cross-table UUID comparison; G10's precondition; the late-global prohibition of G1 after a coordinated U or terminal lock |
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
| ~~Admission (§5.5) [3.4]~~ | *historical: process-local isolation, withdrawn in 3.5* | | | |
| **D-LP-8 (v) admission [3.5; revised in 3.6 as (v-a)…(v-d), §5.7]** | Activation of the corrected protocol | Maintenance command `activate-preservation` under database-verified quiescence; durable activation record; write gate (layers A and B); global refusal while any unsafe component exists; offline `preservation-resolve` (§5.6) | 0 business windows; control units CU-0…CU-11 (§5.6.8) | Until activation succeeds, **all** preservation work (uploads, repairs, cancellation, unresolved decisions, scans, destruction, recovery) is refused visibly; reads and Phases 0–3 work continue. A download whose original cannot be verified returns the accepted "cannot be read right now" message and records nothing. |
| D-LP-9 | Phase 1 `UserService.update` | `pg_advisory_xact_lock(7340, 1)` as the first statement (§4.10) | 1 | Concurrent role changes serialise; outcomes unchanged |

**Totals:**
- Phase 2 **[3.4]**: 22 windows: 19 under the seven P2-8 subdecisions, plus 3 under D-LP-3, 6a and 6b. *(Historical 3.3: 21; 3.2: 18.)*
- Phase 3: 5 windows under D-LP-5 and D-LP-7.
- User administration: 1 window.

## 8. Decisions requested (revision 3.7)

**None of these is approved by this document. Nothing is implemented.**

**(Q) Deployment boundary (owner decision; not approved):**

| ID | Decision | Eighth assessment | Builder recommendation |
|---|---|---|---|
| **Q-36-1** | (1) Every web, worker, receiver and timer, recovery, CLI, maintenance, restore and upgrade process of an installation runs on **one designated application host** (the binding's host ID). (2) No second active application host, detached helper or overlapping failover. (3) PostgreSQL **may be remote**, under an approved application-host policy: maintenance and ordinary admission accept only the binding's host ID, and `pg_hba` restriction of the application role to that host's address is recommended. (4) The lease is local and independent of NAS or mirrored storage. (5) A host move, failover or restore requires verified shutdown of the old host's writers (an unresponsive old host is not shown stopped by absent sessions) and `rebind-host` / `verify-restore`. | owner decision required | Recommend approval. Without it, see §5.8.10. |
| **D-LP-8 (xi)** | **Continuous legacy change freeze** (L1…L12, §5.8.2) as a mandatory operational prerequisite, with the stated automated aids and fail-closed refusals | required as an explicit owner-approved prerequisite | Recommend approval. Without it, see §5.8.10. |

**(A) Architecturally supported; owner approval still needed:** D-LP-2, D-LP-4 (with control and journal effects
declared to the guard), D-LP-5, D-LP-6a, D-LP-6b, D-LP-7 (compatible participants), D-LP-8 (iv). Recommendation:
approve each.

**(B) New owner behaviour approvals:**
- **D-LP-3:** the Conflict after exactly three abandoned attempts.
- **D-LP-8 (i):** durable generation skipping.
- **D-LP-8 (ii):** exhaustion → BLOCKED STATE_UNKNOWN.
- **D-LP-8 (iii):** Wh2 COLLISION and retry.
- **D-LP-8 (v-c):** while not ACTIVE, the preservation operations marked refused in the inventory are refused; the
  permitted exceptions are listed in §5.8.8 (the E-11 read branch, E-12, E-14 job bookkeeping and presence,
  THUMBNAIL, E-24, E-26); diagnostic startup is kept (§5.8.8).

Recommendation: approve each.

**(C) Schema and control: pending architectural closure (not recommended for authorisation before the ninth
assessment):**

| ID | Content | Status |
|---|---|---|
| D-LP-1 | business order plus L-ACT, F1 and MA as the actual early fence and authority | pending (v-a/v-b) |
| D-LP-8 (v-a) | F1 installation-specific lease (§5.8.1), F2 with the continuous freeze (§5.8.2), F3 with host and installation binding, diagnostic mode, the GA002 runtime exit, root/Administrator maintenance (replacing "migrate as the service account") | pending; depends on Q-36-1 and (xi) |
| D-LP-8 (v-b) | 8 control tables / 48 gated relations; V0026 contents; functions `gate`, `admit_session`, `admit_maintenance`, `maintenance_begin`, `require_maintenance`; bootstrap state machine; identity handover; controller-loss latch and drain; journal reconciliation; commands (`provision-installation`, `transition`, `migrate`, `activate-preservation`, `preservation-status`, `preservation-resolve`, `maintenance resume / abort`, `verify-restore`, `rebind-host`); control units CU-0…CU-30 | pending |
| D-LP-8 (v-d) | AU-1…AU-8 (§5.7.9 + §5.8.7) | pending |

**(D) Separately bounded:** **D-LP-9 (a)** is eligible for separate bounded authorisation, which it does not have
automatically.

**(E) Conditional, deferred or optional:**
- **D-LP-8 (vi) A-5 hold:** conditional owner choice, with kind and state in the update predicate (§5.8.6).
- **D-LP-8 (vii) A-6:** deferred and inoperative.
- **D-LP-8 (viii) Unicode restriction:** not proposed.
- **D-LP-8 (x) per-generation login roles:** optional. It is required only if Q-36-1 or (xi) is not approved
  (§5.8.10), and it would need its own role, ownership and credential contract.

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
  - ~~**(ii) OPEN-key contention:** Wc4 and Wf2 of one stopped repair as simultaneous recorders.~~ **Withdrawn in
    3.5:** both need the same operation advisory (sixth assessment §11 item 4). Replaced by (ii-a) and (ii-b) in the
    [3.5] amendments below.
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


**[3.5] Amendments from the sixth assessment §11** (future acceptance gates; none needs to pass for revision 3.5):
- **LP-10h and LP-15 reservation, no symmetric barrier.**
  1. A obtains Ct(X) UPDATE, and a seam holds A **after** its grant.
  2. B is observed waiting on Ct(X), with blocker A.
  3. A is released.
  - If A commits, B reserves strictly above A's committed number.
  - If A rolls back, B may take A's uncommitted number and must never take a committed one.
- **LP-15 Wh2 claimant-first:**
  - The creator must be a legitimate Content-coordinated writer (Wc4 or Wd2 under Ct(X) U, with the operation
    advisory held as the real entry holds it). The fixture must not seed OPEN presence beside a foreign active owner.
  - The real conversion and stop protocol (`stopUnfinishedRepairs`, then `claimBecomesUnresolved`) produces the OPEN
    record.
- **LP-15 inherited overlap through real activation.**
  - Seed a valid active OPEN and claim overlap.
  - Run `activate-preservation`: it publishes REFUSED.
  - Neither the web nor the worker process can run the settlement-and-repair pair (both disabled).
  - A local flag assertion is not accepted as evidence.
- **Probe meaning (all DB-class probes):**
  - NOWAIT success means no conflict with the requested mode at that instant, not "no lock of any mode".
  - A 55P03 probe is rolled back before its connection is reused.
  - Row existence and blocker PIDs are asserted.
  - Granted tuple locks have limited visibility in `pg_locks`.
- **Reservation restart boundary:**
  - a crash after a partial copy's Wa2 committed but before Wh1b's reservation;
  - unknown commit of the reservation;
  - a cancellation requested while the worker holds the advisory (the job reports busy and retries);
  - exhaustion followed by an Administrator retry.

  Assert that no bytes are ever created from an uncommitted computed number. An old committed plan is resumed
  (BLOCKED or WAITING_FOR_VOLUME with a target) or re-planned (STAGED) exactly as AU-7 classifies it.
- **LP-16 (x) same-generation restoration:**
  - D observes ACTIVE(g1, p1) and absence.
  - Another transaction records MISSING(g1), then reverification restores ACTIVE(g1, p1) before D's Ct grant.
  - The identity matches, so D commits its finding and state.

  The assertion is that this is **point-in-time evidence** (the observation was true when made, and the restored
  state is MISSING again with a finding), not a claim that bytes were absent at COMMIT. The residual is documented
  (§12).
- **Operation-state fixtures:** only real enum states. No FAILED fixture, and COMMITTED is never treated as final.
- **Full outcomes:**
  - Real constraints and triggers stay enabled.
  - Collect every statement, COMMIT and rollback result for every participant and controller cleanup.
  - Assert the exact ownership, evidence, bytes and history.
  - Assert zero unexpected 40P01 or 55P03.
  - Automatic deadlock retry is disabled, and every controller has an independent deadline.

- **LP-10d (ii) [3.5, replaced].** The 3.4 fixture is withdrawn: Wc4 (DestructionProcessor, under the stopped
  repair's advisory through `tryLockAll`) and Wf2 (cancellation of the same repair, under the same advisory) cannot
  both pass advisory ownership. Two tests replace it:
  - **(ii-a) Service level, reachable (ACC).**
    1. A repair R of X is stopped with an unverified copy.
    2. A DESTROY of X runs `DestructionProcessor.process`, which holds R's advisory through `tryLockAll`. A seam holds
       it inside Wc4, after its advisory grant and before its Ct(X) request.
    3. A `JOB_CANCEL` for R runs `performCancel`. Its `tryLock("file-operation", R)` **fails**, and it throws the
       accepted "busy; the cancellation will be retried" exception. The job is retried per `JobRunner`, and **no**
       Ct request was made by the cancel.
    4. After the destruction releases the advisory, the retried cancel observes the state the destruction left
       (R stopped or CANCELLED, as the accepted rules give) and applies or records "not applied" accordingly.
    5. Assert the exact rows, events, OPEN records (one per path) and bytes.

    The reverse order (cancel first, holding R's advisory) makes the destruction's `tryLockAll` fail and retry
    (accepted DP behaviour); assert likewise.
  - ~~**(ii-b)**~~ **withdrawn in 3.6** (self-contradictory; see the [3.6] (ii-b1)/(ii-b2) below). *Historical text:* **(ii-b) Helper or database level (DB).** Two transactions both hold Ct(X) in turn (as the real windows do).
    Each runs the real `UnresolvedObjectRepository.recordIfAbsent` SQL for one (volume, path). The second **waits on
    the OPEN unique key** while the first is uncommitted (server-side seam), then returns the **existing** record
    after the first commits. This tests the OPEN-key and returned-existing semantics without a manufactured service
    state, and it is labelled DB.
  - (i) and (iii) of 3.4 are retained.

- **LP-17 [3.5] Admission and activation acceptance group** (AA-1…AA-16; ACC unless marked). **Superseded by the [3.6] LP-17 group below; kept for traceability.**
  - **Participants:** real separate processes (`serve`, `worker`, CLI), each launched through `ProcessLauncher` in
    its own JVM with its own pool.
  - **Database:** PostgreSQL 16 and 18, with real migrations, triggers and the layer A role setting.
  - **Platforms:** Linux and Windows CI jobs.
  - **Every case asserts:**
    - the exact singleton, event and finding rows;
    - process identity (`application_name`, worker id);
    - ownership rows, OPEN records and quarantine rows;
    - bytes on the volume (hash per path) and history;
    - every transaction outcome, including SQLSTATE 25006, GA001 or 55P03 where expected.

| # | Case | Expected outcome |
|---|---|---|
| AA-1 | Separate web and worker processes started after ACTIVE | both bootstrap the same (state, epoch); an upload, its ingest job and a download complete; the web performs no worker step |
| AA-2 | Two competing workers | both enabled; `SKIP LOCKED` claims are disjoint; a lease reclaimed after one worker is killed is processed once; preservation outcomes identical to one worker |
| AA-3 | Old writer alive during attempted activation: (a) an accepted-build worker or web holding a pooled session; (b) an accepted-build process with **no** session at the check, connecting afterwards; (c) a new build with the previous epoch | (a) activation refused at 4(b), nothing published; (b) its locking reads and writes fail with 25006 **before** any row lock (`pg_locks` shows no tuple lock), and the sixth assessment's B/N schedule cannot form; (c) its writes fail with GA001 |
| AA-4 | Failed and interrupted audit: an exception in AU-n; the activation process killed during AU-n; a NOWAIT conflict at 4(d) | rollback; singleton unchanged; no event row; services that start read the old state |
| AA-5 | Crash before and after publication: the activation process killed before COMMIT; the database restarted after COMMIT returns | before: unchanged; after: ACTIVE at epoch + 1 with the event row; services start enabled |
| AA-6 | Startup while writers are active: (a) a web process starts during an activation's audit; (b) activation attempted while a worker runs a job | (a) its bootstrap waits (blocker = activation pid) and then reads the published state; (b) refused at 4(b) or 4(d) |
| AA-7 | Direct RecoveryService updates | an open MISSING finding on ACTIVE content (P16's input) while the state is NOT_ACTIVATED: no `setState`, no `reconcileAll`, no `needingAttention` step runs (row versions unchanged after several recovery intervals); after ACTIVE, P16 runs under E1. Plus the ArchUnit rule that E-27 helpers are reached only from E-1…E-25. |
| AA-8 | Cancellation traversing pending settlement | a BLOCKED repair with an INTENDED own-copy entry: while not ACTIVE, the `cancel` request is refused and a queued `JOB_CANCEL` is not claimed; after ACTIVE, `performCancel` settles the entry (Wa2) and cancels; no foreign claim is released |
| AA-9 | Cross-content and contentless shared path: an operation of Y and a generation of X at one AU-8 key (including a case-only variant); a contentless OPEN U whose path equals a Q destination | REFUSED; the finding names the whole component (rows of X, Y, U and Q); no row changed |
| AA-10 | Legitimate final stale claims: a CANCELLED operation with `target_created` and its owner row; an OPEN U at that path | ACTIVE (ADMITTED-STALE); a later settlement releases the stale claim as PO-B (b) |
| AA-11 | Valid SHARED_CONTENT COMMITTED cleanup after crash: an ingest stopped by the AFTER_COMMIT_BEFORE_CLEANUP fault point | ACTIVE (ADMITTED-CLEANUP); the first recovery pass completes it (COMPLETED, staging deleted, the Y target retained as history); no cancellation offered |
| AA-12 | Restart of only one process: restart the web while a worker processes a repair; restart one of two workers mid-job | no epoch change; the restarted process is enabled; the job lease is reclaimed and processed once |
| AA-13 | Restored backup and version mismatch: restore a dump taken at ACTIVE (with services stopped; read-only off for the restore); restore a pre-activation dump; start a build of another protocol | the first: ACTIVE, consistent; the second: disabled until activation; the third: disabled at startup, writes GA001 |
| AA-14 | Unsafe admission refusal: each UNSAFE class (AU-1…AU-8 one by one) | REFUSED with exactly that finding; every preservation entry (E-1…E-21) refused or unreachable with its message; E-12, E-24 and E-26 work |
| AA-15 | Reviewed resolution and re-admission: `preservation-resolve` with A-2 (release) and A-3 (cancel) on a resolvable component; a second component that needs A-5 without owner approval | the first is resolved, with resolution rows and events; re-audit stays REFUSED while the second remains; after the second is resolved, ACTIVE |
| AA-16 | Unknown completion and fail-closed behaviour: the connection is cut at COMMIT (proxy); also a REPEATABLE READ transaction attempted by application code | `preservation-status` decides from `run_id`; services are not started until then; the isolation assertion rejects the RR transaction |

  **DB-class companions:**
  - (a) Layer A refuses `SELECT … FOR UPDATE` without a tuple lock.
  - (b) The bootstrap `FOR SHARE` waits on B0.
  - (c) The layer B trigger never waits on B0.
  - (d) `pg_stat_activity` is cached until `pg_stat_clear_snapshot()`.

- **[3.6] LP-10d (ii-b) corrected** (seventh assessment §11). (ii-a) is retained unchanged, including the accepted
  "not applied" outcome when the repair is already CANCELLED. The 3.5 (ii-b) was self-contradictory: both
  participants need Ct(X) UPDATE, so the second cannot reach the OPEN key while the first is uncommitted. It is
  replaced by two separately labelled tests:
  - **(ii-b1) Coordinator test (ACC, DB-assisted).** Two real recorder windows of one (volume, path) for X.
    1. The first holds Ct(X) U and has inserted the OPEN record; a server seam holds it before COMMIT.
    2. The second is observed **waiting at Ct(X)**: `pg_stat_activity.wait_event_type = 'Lock'`, and
       `pg_blocking_pids` contains the first.
    3. After the first commits, the second passes Ct and its `recordIfAbsent` **returns the committed OPEN record**.
    4. Assert one OPEN row, both transaction outcomes, and zero unexpected 40P01 or 55P03.
  - **(ii-b2) Raw unique-key characterisation (DB, explicitly without the application coordinator).** Two raw
    transactions run the accepted `INSERT … ON CONFLICT` for the OPEN partial unique key. They **deliberately omit
    Ct**; that scope is labelled in the test name.
    1. The second waits on the first's uncommitted key (server seam).
    2. After COMMIT it takes the conflict path.

    This characterises PostgreSQL only. It is **not** evidence about production coordination, and production
    coordination is not weakened to make it reachable.

- **LP-17 [3.6] Admission, fence and maintenance acceptance group.** This replaces the 3.5 table. These are future
  implementation acceptance tests; none is claimed to pass.
  - **Common rules:** real separate processes (`serve`, `worker`, CLI) through `ProcessLauncher`; real migrations,
    triggers, role default and lock file; PostgreSQL 16 and 18; Linux and Windows.
  - **Every case asserts:**
    - every statement, COMMIT and rollback outcome;
    - expected 25006, GA001, GA002, GA003 or 55P03, distinguished from **unexpected** 40P01, 55P03 or lock
      timeouts (zero allowed);
    - automatic retries disabled, with independent controller deadlines;
    - singleton, run, action, window, event and finding rows;
    - ownership, OPEN U, Q and bytes per path (hash);
    - process identity (`application_name`, PID, F1 holder);
    - cleanup of every participant.
  - **DB probes** never infer the absence of row locks from `pg_locks` (granted tuple locks are not reliably shown
    there). They use conflicting-mode NOWAIT probes, exact row existence and blocker PIDs, with each probe rolled back
    before its connection is reused.

| # | Case | Expected |
|---|---|---|
| AA-1 | Separate web and worker after ACTIVE | Both hold F1 shared and pass F3 on **every** physical connection; an upload, its ingest and a download complete |
| AA-2 | Two workers | Disjoint `SKIP LOCKED` claims. A job lease reclaimed after one worker is killed **mid-step** ends in the accepted idempotent outcome; no "exactly once" claim is made for its effects. |
| AA-3a | **R7-1 cycle, connected old administrator.** A legacy-build web holds a read-write session sA from before T4; the new worksheet add T1 and remove T2 are scripted as in the seventh assessment §4 | `transition` terminates sA at T5 (it is shown read-write right up to T5). After T7, the legacy process's reconnect is fresh and its enabled-administrator `SELECT … FOR UPDATE` fails with 25006 before any row lock (a probe confirms a1 is lockable). T1 and T2 complete under D-LP-7 and D-LP-9(a); zero 40P01. A control run **without** T5 is documented as reproducing the cycle risk (characterisation, not acceptance). |
| AA-3b | Old process disconnected during activation | A legacy process with no session at CU-12 and alive at the OS: `transition` T2 refuses (Linux command-line match; Windows WinSW or listed Java PID). With the process stopped, its later reconnect is read-only (25006 for locking reads and DML), with real driver reconnect. |
| AA-3c | Stale new worker resumes an INTENDED move after an epoch change (assessment §5, Case C) | The worker pauses in `runOnce` before reconciliation; its sessions are dropped (database restart). `activate-preservation` **refuses at F1** while the worker lives. After the worker is killed, the activation proceeds, and no rename happens after publication (bytes and Q unchanged between the kill and the publication). |
| AA-3d | Filesystem step in progress while every database session disappears | The worker is paused inside the rename or copy of a step; the database is restarted. The primitive completes, the next window fails with a connection error, and the durable state is an accepted crash state. Maintenance stays refused (F1) until the worker exits; after restart, recovery completes the step. |
| AA-4 | Failed audit, NOWAIT conflict, killed activation | Rollback; every acquired lock released (probes); singleton unchanged; the run stays OPEN, so services refuse to start until resume or abort |
| AA-5 | Crash before and after the publication COMMIT | Before: unchanged. After: ACTIVE at E + 1. The outcome is authoritative only after CU-21 confirms the original backend is gone. |
| AA-6 | A service started during maintenance | It blocks on F1 shared (logged); after CU-11 it passes F3 and reads the published state. It never reads during the run. |
| AA-7 | Direct recovery paths | A process with an earlier epoch's ACTIVE cannot exist while maintenance publishes (F1). Fixture: F3 refuses a connection from a process started with stale literals (GA002 → exit before any statement); P16 and `reconcileAll` are not reached. |
| AA-8 | Cancellation through pending settlement, offline | A-3 with an INTENDED own entry: windows W-pend … W-staging journalled; a foreign non-final claimant at the source makes W-settle roll back (FAILED: foreign claimant); an own active claim makes J1 refuse (NOT_APPLIED). Disabled job registration persists across reconnects. |
| AA-9 | Conflict components | Independent aliases (a case-only variant across contents; a foreign U at a Q destination) are refused **and** relationally bound same-object references pass |
| AA-10a | Final stale claim | as 3.5 |
| AA-10b | **Destruction-created OPERATION_COPY U naming the DESTROY operation**, with the stopped repair's final claim | ACTIVE (W3 role; ADMITTED-STALE) |
| AA-10c | **Destruction QUARANTINED_COPY U (generation null) at a QUARANTINED Q destination** | ACTIVE (AU-6 bound) |
| AA-11 | SHARED_CONTENT cleanup | as 3.5 |
| AA-11b | **NEW_CONTENT with initial generation > 1** (two partial replans before commit) | ACTIVE; AU-1 g₀ = 3 |
| AA-11c | **Historical vacated generation** after a successful repair of MISSING content | ACTIVE; `vacated = true` retained on the superseded generation |
| AA-12 | One-process restart; lost sessions without a JVM restart | Same epoch, no maintenance needed. After a database restart the pool reconnects through F3. Maintenance attempted meanwhile is refused at F1. |
| AA-13 | Restore | (a) A logical dump of an ACTIVE database restored into a new database **without the role default**: services refuse (identity mismatch and missing layer A); `verify-restore` reinstalls, re-audits and publishes a new epoch. (b) A physical restore: layer A is checked, and the database is ACTIVE. (c) A pre-gate dump: BEHIND, so `transition` is required. (d) An interrupted restore refuses. (e) A build of another protocol fails F3 at its first connection (GA002) with no business statement executed. |
| AA-14 | Every unsafe class | Each AU failing fixture of §5.7.9 refused with exactly its finding, **and** every legitimate fixture of §5.7.9 passes in the same database (not as an operator exception) |
| AA-14b | **Verified partial-prefix PARTIAL_COPY evidence** (trusted, size below expected) | admitted (INTENDED and QUARANTINED) |
| AA-15 | Resolution and re-admission | A-1, A-2 and A-3 journalled; a component needing A-5 without approval stays REFUSED; with A-5 approved, a PROMOTED repair is held (CAS checked against a concurrent change, which yields NOT_APPLIED) and then cancelled. A DESTROY is never eligible. Non-waivable components (UNSAFE-ACTIVE, host alias) stay refused. |
| AA-18 | **Resolver versus activation** | A second maintenance command while a resolution run is OPEN: refused (F1, or the advisory and OPEN row from another host); activation inside the same run succeeds after the actions |
| AA-19 | **Resolver versus migration** | `migrate` while a run is OPEN: refused; migration inside a run follows CU-0 |
| AA-20 | **Maintenance authority lost between windows** | The controller backend is terminated after W-intent: the next window fails GA003 before any business lock; the process exits; the run stays OPEN; services refuse to start; `maintenance resume` re-derives W-settle from Q INTENDED and completes |
| AA-21 | **Crash after intent, after rename, after settlement, and before the final journal** | Each crash point is followed by `resume`, giving the same final business state and a truthful journal (repeated windows NOT_APPLIED; no committed window without its row) |
| AA-16 | Unknown COMMIT and settings | A connection cut at COMMIT: undecided until CU-21 confirms the backend is gone. `SET LOCAL` reverts after COMMIT; a missing, empty or invalid declaration gives GA001 or GA002; a prepared DML statement is re-checked after an epoch change (a new connection is needed, so GA002); rollback restores no eligibility. |
| AA-17 | **F1 on Windows and Linux** | Shared plus shared succeed; exclusive is refused while any shared holder lives; after a kill, exclusive succeeds only once the holder has ended (Windows release delay tolerated as refusal); the lock file ACL works for both service accounts |
| AA-22 | **Download precedence while disabled** | A DESTROYED File gives not-found and a DELETED File without permission gives not-found, both before any activation-related message. A non-ACTIVE content gives the accepted message. Verified absence while disabled gives "cannot be read right now. Nothing was changed", with no finding and no state change. A size mismatch while disabled gives "Nothing was recorded". ACTIVE behaviour is unchanged (We7). |
| AA-23 | Sequence policy | A refused writer consumes `friendly_id` values; the gaps are accepted and no row is written |
| AA-24 | Trigger coverage check | A table without the gate trigger (or with it disabled) makes CU-12 and CU-14 refuse |

- **LP-17 [3.7] amendments** (eighth assessment §12). These amend the 3.6 group: where a row below names a case, it
  replaces or extends that case. These are still **future implementation acceptance tests**; none is claimed to pass.
  - **Determinism:** explicit Java and process barriers and server-side seams, never sleeps. Every probe releases its
    own locks before participants proceed.
  - **Unexpected outcomes:** zero unexpected 40P01 or 55P03. An unexpected timeout is a failure, not evidence of
    deadlock prevention.

| # | 3.7 specification |
|---|---|
| AA-1 | as 3.6, also recording the resolved pgjdbc, Hikari and Flyway versions, and every physical connection's declaration and `admit_session` result |
| AA-2 | as 3.6; a worker killed mid-step; assert every effect (bytes, Q, U, owner rows, events), not only disjoint job ids |
| AA-3a | **Split.** (i) *Transition acceptance:* a live legacy administrator process makes L5 refuse; after a verified stop, the transition completes. (ii) *Database companion (labelled):* an **orphaned pre-default backend** with no live process (a raw psql session as the application role, read-write, opened before V0026) is terminated at L8 and confirmed gone; the worksheet T1/T2 run only after B8. (iii) *Characterisation (labelled, not acceptance):* the original A→T1→T2→A cycle reproduced with a surviving read-write session and B8 bypassed in a raw-database fixture. Its purpose is to show what L8 prevents; T2 is never weakened. |
| AA-3b | (i) A legacy restart attempted **after L5 and before L7 commits** through the unit or service (masked or disabled: refused by the service manager), through the shared `installation.yml` (exits at configuration load: unknown key), and through a copied jar and configuration (detected at L10 → refuse). (ii) Service-recovery and scheduled-task launch cases. (iii) A fresh post-L7 accepted-build reconnect with the **real old driver and pool**: locking reads and DML fail with 25006. |
| AA-3c | as 3.6, also with an **admitted INTENDED move** in progress: source and destination bytes asserted; no rename after publication |
| AA-3d | **No inevitable failure is asserted.** After the database restart, either the pool has reconnected (same epoch; the step continues) or the next window fails with a connection error at a durable boundary. Assert safety in both cases: epoch unchanged while the holder lives; maintenance refused at F1; accepted recovery |
| AA-4 | (i) A **committed REFUSED** publication (findings, epoch + 1) is distinguished from (ii) an audit exception, (iii) a NOWAIT conflict and (iv) a killed activation, each of which rolls back with every earlier lock released (probes). The run stays OPEN, giving S7. Bootstrap crash states B1…B10 are covered by AA-19. |
| AA-5 | Acknowledged **and** unknown publication; the controller **closes the run at the new epoch** through `maintenance_begin` with no GA001 (§5.8.4 sequence) |
| AA-6 | Startup during maintenance waits on F1 shared on a **supervised** thread (logged every minute); **no effect** (configuration aside) before acquisition |
| AA-7 | as 3.6; the stale-literal F3 refusal is labelled a negative fixture, not proof that a compliant worker straddles maintenance |
| AA-8 | (i) **An ordinary owned target is cancelled successfully** (normal A-3: own `target_created` claim allowed). (ii) Negatives only for an **OPEN U at the target of another writer role** and a **non-final foreign claimant**. (iii) Every release path revalidated: pending settlement, settlement, release-only final and the unverified-copy final. (iv) The unverified-copy final is **one transaction** (U + release + CANCELLED + window row). |
| AA-9 | as 3.6, plus non-constructor **observation** paths (integrity findings, including Unicode NFC names) that must **not** refuse (AA-9b) |
| AA-10a/b/c | as 3.6, seeded through the real accepted writers (`DestructionProcessor.newRecord`, `claimBecomesUnresolved`) where possible |
| AA-11…AA-11c | as 3.6, plus **AA-11d** targetless BLOCKED STAGING_INVALID INGEST produced by the real `plan`; **AA-11e** targetless BLOCKED STAGING_INVALID REPAIR; **AA-11f** partial history, then cancellation (pointer = CANCELLED_COPY destination); **AA-11g** crash after partial settlement before replan (pointer older or null; same target rewritten) |
| AA-12 | as 3.6, plus: the lease channel survives pool shutdown and replacement, receiver and timer shutdown, and routine cleanup (`isValid` and object identity unchanged) |
| AA-13 | (a) Fresh bootstrap (S0); (b) pre-gate dump (S1 → transition); (c) logical dump without the role default (S9 → `verify-restore` reinstalls and verifies it); (d) physical restore: the operational exclusion procedure, with **no** claim that the identity values detect it; (e) another protocol's build: F3 refuses at first connection; (f) database and storage coherence statement recorded |
| AA-14…AA-14b | as 3.6 (present-source prefix), plus **AA-14c** a recorded-absent INTENDED for each of PARTIAL_COPY, UNVERIFIED_COPY, CANCELLED_COPY and ATTESTED_OBJECT; **AA-14d** SOURCE_ABSENT settled; **AA-14e** a present object without evidence (admitted; settlement refuses visibly); every null-target branch of the AU-2 table |
| AA-15 | Hold: kind and state enforced **in the UPDATE predicate** (DESTROY and RECEIVING give zero rows → NOT_APPLIED); ledger, high water and evidence unchanged; no waiver. A concurrent ordinary writer is unreachable under MA, so stale preconditions are tested through a **precheck-to-CAS seam** or a labelled raw database mutation. |
| AA-16 | **Split:** (i) a database characterisation (labelled): an existing prepared session after a protocol change shows execution-time gate evaluation (GA001); (ii) acceptance: F3 refusal of new connections; (iii) `set_config(…, true)` reversion at COMMIT and ROLLBACK on controller and action connections; (iv) missing, empty or invalid declarations; (v) closure at the new epoch |
| AA-17 | Lease contract on Linux and Windows: actual systemd units and WinSW virtual accounts with a **custom DataDir** (the lease root stays fixed); protected-directory ownership and mode; an interrupted shared wait; an unsupported shared mode; a channel closed by a second open (must be impossible: ArchUnit); **replacement** (Linux new inode; Windows `NOSHARE_DELETE` refuses the rename or delete); symbolic link, junction, reparse point and alias refusal; WinSW parent versus `java.exe` child termination; **two independent installations** on one host (independent maintenance); a duplicate installation ID copy (same lease) versus a different ID against one database (GA002) |
| AA-18 | Ordered phases inside one run (resolution, then activation), and exclusion of a second maintenance command (F1). **No cross-host claim.** |
| AA-19 | **Bootstrap:** S0 and S1 through B1…B10, with a crash or unknown outcome at each B boundary (expected state per §5.8.3); Flyway connections registered in process before V0026 and persisted at B5; V0026 rollback including the role default; no ordinary admission in S3 |
| AA-20 | Controller loss **after `maintenance_begin` and before business SQL/COMMIT**, and between windows: the latch is set; the admitted phase finishes with its outcome determined; **no new phase**; full backend drain by `(pid, backend_start)` before exit; resume drains a surviving old backend of a SIGKILLed JVM before rebinding |
| AA-21 | **Journal-aware reconciliation replaces "repeated windows NOT_APPLIED".** Crashes after J0; after each window; after release, attest and hold; after SOURCE_ABSENT; within the atomic unverified-copy final; after the staging delete and before the mark; with unknown COMMIT of a **successful** window. The original window row's outcome, event and timestamp are reused and **not** repeated. Inconsistent business and journal states stop the run with an observation. |
| AA-22 | as 3.6, also keeping valid disabled downloads served |
| AA-23 | sequence gaps characterised; no application row or owner change |
| AA-24 | Coverage by **trigger function OID, `tgtype` bits and `tgenabled = 'A'`**, per relation, including a disabled trigger, a wrong function, a missing TRUNCATE event, and a direct partition target fixture |

- **LP-10d (ii-b) [3.7].** The 3.6 split is retained.
  - **(ii-b1)**: source inspection at `7a97e70` found **no pair of service-level recorders** of one OPEN key that is
    not already serialised by a shared operation advisory or excluded by the protocol:
    - destruction holds every writer's advisory (`tryLockAll`);
    - `claimBecomesUnresolved` records only its own operation's target;
    - `commitRepair` and `quarantineSuperseded` record only their own content's superseded generation paths, under the
      repair's advisory.

    (ii-b1) therefore drives the two real repository recorder windows (`recordIfAbsent` under the real Ct(X) U
    coordinator) directly. It observes the second waiting at Ct and then returning the committed OPEN record, and it
    is **labelled helper-level**. No service race is claimed.
  - **(ii-b2)** remains a raw database characterisation without the coordinator.
  - **(ii-a)** keeps the accepted "not applied" result when cancellation or destruction has already changed
    eligibility.

## 11. Bounded verification executed (non-production) [restored in 3.5]

**[3.5]** Revision 3.4 (`fe197ed`) dropped this section and the §12 heading by an editing error, which left the
§11.1–§11.7 references dangling (sixth assessment, F4-12). §11.1–§11.7 are restored **verbatim** from revision 3.3
(`e5bf8c0`, file `docs/PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`, lines 1185–1244). They are **historical builder-local
or CI evidence**, re-executed by nobody for this revision; they are not new independent execution. Only §11.8 is new
in 3.5.

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

### 11.8 Activation-gate primitives (new [3.5])

**Where and how.**
- **Class:** builder-executed.
- **Environment:** local PostgreSQL 16.15; scratch database `r3inv`.
- **Objects:** throwaway schemas, tables and roles only (`r35*`, `gas_other`), dropped afterwards. No project code
  and no CI.
- **Limits:** these are **primitive** checks of PostgreSQL behaviour that §5.6 relies on. They are not tests of the
  design, which LP-17 specifies.

| # | Schedule | Result | Used by |
|---|---|---|---|
| (a) | Session 1 holds a row `FOR UPDATE`; session 2 runs `LOCK TABLE … IN EXCLUSIVE MODE NOWAIT` | `ERROR: could not obtain lock on relation` (55P03), immediately | 4(d): a live writer refuses activation without waiting |
| (b) | Session 1 holds `EXCLUSIVE` on a table; session 2 (with `statement_timeout` 1 s) runs a plain `SELECT`, a `SELECT … FOR UPDATE` and an `UPDATE` | the plain read returns at once; the locking read and the update both time out (they wait) | barrier semantics: reads pass, locks and writes wait |
| (c) | The role-in-database default `default_transaction_read_only = on` (set by the non-superuser role itself) | Without options: a plain read and `pg_try_advisory_lock` succeed; `SELECT … FOR UPDATE` and `UPDATE` fail with "cannot execute … in a read-only transaction". With `PGOPTIONS=-c default_transaction_read_only=off`, the update succeeds. | layer A |
| (d) | In one transaction: count other sessions; another session connects; count again; `pg_stat_clear_snapshot()`; count again | 0, 0 (cached), 1 | 4(e) |
| (e) | A statement trigger comparing the session settings `gas.preservation_protocol` and `gas.activation_epoch` with a singleton row | No settings: refused. Matching settings: allowed. Wrong protocol: refused. An earlier `SELECT … FOR UPDATE` without settings **succeeds** (the trigger alone does not stop locking reads, hence layer A). | layer B, and the reason both layers are needed |
| (f) | Full sequence: activation transaction (singleton `EXCLUSIVE NOWAIT`, session check, table `EXCLUSIVE`, audit read, `UPDATE` epoch 1 → 2, `SET LOCAL`, COMMIT), with a concurrent bootstrap `SELECT … FOR SHARE` started 1 s in | The bootstrap waited 2.04 s and returned `ACTIVE`, epoch 2. Afterwards a writer with epoch 1 was refused, and a writer with epoch 2 succeeded. The application schema has 40 base tables at V0025. | CU-3, CU-5, CU-6, CU-7 |
| (g) | Session 1 holds `EXCLUSIVE` on the singleton; session 2, with matching settings, updates another gated table through the trigger (`lock_timeout` 2 s) | the write succeeded in 0.04 s: the trigger's plain read does not wait on the barrier | CU-7 never waits |
| (h) | `pg_stat_activity` as the application role, for a session of another role | the row is visible with `application_name`; `backend_type` and `query` are hidden (`<insufficient privilege>`). Background processes show `datname` null. | 4(b) counts a hidden `backend_type` as a client |

**Not established here:**
- the absence of tuple locks while an **aborted** old-build transaction stays open (LP-17 DB companion (a));
- autovacuum interaction with 4(d);
- PostgreSQL 18;
- Windows.

### 11.9 Fence primitives (new [3.6])

**Where and how.**
- **Class:** builder-executed.
- **Environment:** local PostgreSQL 16 (scratch database `r3inv`, application-like non-superuser role `gas_test`)
  and OpenJDK 21.0.12 on Linux; throwaway objects only (`r36_t`; a lock file in the scratchpad); nothing kept.
- **Scope:** primitive observations only. No project code, no CI, no PostgreSQL 18, no Windows. These checks do
  **not** test the design; LP-17 specifies that.

| # | Schedule | Result | Used by |
|---|---|---|---|
| (a) | Session S opened **before** `ALTER ROLE … IN DATABASE … SET default_transaction_read_only = on` | S reports `off` and locks a row `FOR UPDATE` afterwards, so an existing session is not converted (confirms the seventh assessment §3) | T5 required |
| (b) | The same non-superuser role calls `pg_terminate_backend(S, 5000)` | returns `t`; S receives "terminating connection due to administrator command". A fresh session then gets "cannot execute SELECT FOR UPDATE in a read-only transaction". | T5 / CU-3 with no extra privilege; layer A on fresh sessions |
| (c) | `pg_try_advisory_lock(7340, 2)` held by another session, viewed in `pg_locks` | locktype `advisory`, classid 7340, objid 2, objsubid 2, mode `ExclusiveLock`, granted | CU-16 binding check |
| (d) | `pg_control_system()` and `pg_database.oid` as the application role | readable (a `system_identifier` value; the database OID); `pg_db_role_setting` readable | §5.7.12 identity; CU-12 / CU-14 layer A check |
| (e) | Java `FileChannel` locks on one file: two JVMs hold `lock(shared)`; a third tries `tryLock(exclusive)`; then SIGKILL of each holder | exclusive REFUSED with two holders, REFUSED with one, ACQUIRED after both were killed | F1 (Linux only) |

**Not established here:**
- Windows lock semantics and release delay;
- the real Hikari and pgjdbc client premise (R36-3);
- `pg_terminate_backend` on PostgreSQL 18;
- autovacuum interaction;
- every LP-17 case.

### 11.10 Revision 3.7 verification [3.7]

**No new local execution was performed for revision 3.7.** The revision rests on source inspection at `7a97e70`:
- `OperationProcessor` `plan`, `quarantinePartialAndReplan` and `promote`;
- `QuarantineMoves` `move` and `settle`;
- `QuarantineRepository.intend`;
- `OperationAdminService.applyCancel`;
- `UnresolvedObjects` `release`, `attest`, `quarantineAttested` and `retireGeneration`;
- `SchemaMigrator`;
- `InstallationConfigLoader` (rejection of unknown keys);
- the deployment scripts and unit files;
- `BlockedReason`;
- the writers of `file_operation.quarantine_path`;
- the absence of `ProcessBuilder` and `Runtime.exec` in `src/main`.

The §11.9 primitives (3.6) are not re-executed and remain Linux PostgreSQL 16 / OpenJDK 21 observations only.

## 12. Residual risks and open items

- **[3.3] Changes to the assumptions:**
  - **INV-U is withdrawn (contradicted, §5.4)** and is not restated under another name. P2-8g is a mechanism with
    its own window inventory, not an assumption.
  - **INV-P is scoped:** the theorem covers the **admitted data set**: rows produced by the audited constructors
    (`ManagedVolume.originalPath` and `quarantinePath`, ingest UUIDs from `GlobalIds`, repair generation planning,
    partial-copy replanning) and preserved known provenance. It is **not** a database constraint.
- **[3.4] Data audit:** superseded by the fail-closed admission design of §5.5 (AU-1…AU-8). A read-only report
  alone is not sufficient. **[3.5]** §5.5 is itself historical. The operative design is §5.6: global fail-closed
  activation, the corrected AU-1…AU-8 (§5.6.5) and assumptions R35-1…R35-6 (§5.6.10).
- **[3.7]** The operative admission design is §5.7 as amended by §5.8. It is conditional on **Q-36-1** and
  **D-LP-8 (xi)**, neither of which is approved. Assumptions and proof obligations are listed in §5.8.12, and the
  alternatives if those decisions are not approved in §5.8.10.
- **[3.6]** The operative admission design is §5.7, with assumptions and proof obligations R36-1…R36-6
  (§5.7.13); R35-1, R35-2 and R35-4 are superseded. Open spec question **Q-36-1**: confirm that an Archive Instance's
  processes run on one host (F1's scope). A-6 is deferred (D-LP-8 (vii)).
- **[3.5] Residuals carried forward from the sixth assessment:**
  - **We7 point-in-time evidence:** the identity check does not prove the bytes are still absent at COMMIT; a
    same-generation restoration can recreate the observed identity (LP-16 (x)). Ct does not control external
    filesystem writers, and this is not claimed.
  - **Wh1b restart boundary:** recovery distinguishes an already committed reservation (resumed) from an uncommitted
    computed number (never used) (LP-15 restart cases).
  - **REPAIR missing content** is never private (§4.9, INGEST only).
  - **GAS-P4-004** remains MAJOR, OPEN, source-derived and unimplemented; its protection for inherited active
    owners now rests on §5.6's global refusal.
  - **Old-build locking reads** are excluded only by layer A, and same-protocol builds are not distinguished
    (R35-4). Neither layer is a security boundary.

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
  6. an independent implementation audit;
  7. **[3.5]** LP-17 (AA-1…AA-16) with real separate processes on all four configurations; **[3.6]** replaced by
     the 3.6 LP-17 (AA-1…AA-24), on Linux and Windows.

  WP1-R1, GAS-P4-002, GAS-P4-003, GAS-P4-004 and GAS-P4-005 stay open until then.
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

> **Status:** OPEN. **[3.3]** The original deadlock-freedom premise is superseded (it holds within one session only; Scenarios A–D are cross-session or cross-subsystem cycles, and GAS-P4-003 adds a user-administration cycle). The replacement protocol (L1a…L1f with L1p, CR-1…CR-4 with exceptions E1–E5, TR with D-LP-9) and the guard contract are proposed in PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL revision 3.5, decisions D-LP-1…D-LP-9 (D-LP-8 including P2-8g and global fail-closed activation, §5.6). Not implemented; not closed by specification.
