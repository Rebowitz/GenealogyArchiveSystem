# Phase 4 WP1: L1 locking-protocol proposal (WP1-R1, GAS-P4-002), revision 2

**Status: REVISED PROPOSAL for owner review and independent Codex assessment. Not implemented.**
- No production locking code, SQL or migration in this document has been written.
- The test designs in §10 are specifications; none of them is claimed to pass.
- The only executed evidence is the bounded, non-production harness in §11.

| | |
|---|---|
| Revision | 2: responds to the Stage A review (`Phase-4-WP1-Stage-A-Lock-Protocol-Review.md`), findings SA-R1…SA-R6, and to the Stage A revision direction |
| Supersedes | Revision 1 at `9c36d5e`. Its §5 "sink" lemma and its global deadlock-freedom conclusion are **withdrawn**. |
| Inputs | The Stage A direction sheet (the review report itself was not available to the builder); clarifications §5.5.3; ADR-0011; ADR-0012 §8; source as of `9c36d5e` |

---

## 0. Response matrix (SA-R1…SA-R6)

| Finding | What the revision does | Where |
|---|---|---|
| **SA-R1** Record → File dependency | The general "implicit KEY SHARE after Record" exception is **removed**. `RecordService.linkFile` takes the File **FOR SHARE before** the Record and revalidates under both locks. The G → L → C → G cycle is reconstructed, reproduced on PostgreSQL 16 (40P01), and shown to be impossible under the revised order. Worksheet-to-File implicit edges are removed by locking Files before worksheet keys. | §3, §4.2, §4.3, §5, §11.2 |
| **SA-R2** Derivative closure | Modified option (a): discover the full reachable set (any state), lock it in ascending UUID order with no eager root lock, recompute in a fresh statement, and restart a bounded number of times, then fail visibly. The expected version is never changed silently. A proof shows no publisher can commit a new reachable edge after stabilisation. Complete SQL is given. | §4.6, §5 (L5) |
| **SA-R3** Preservation inventory | Complete inventory of `file_content`, `commitShared`, `insertFile`, `destroyHolding`, replacement-File foreign keys, derivative publication, advisory and repair-exclusion locks, private new rows and worker transactions. The content-before-parent / parent-before-content cycle is resolved by ordering File before content. The required Phase 2 changes are listed separately for owner decision. | §2, §4.7, §7 |
| **SA-R4** Grouping dependencies | The classification-source row and File are locked **only on the new-Record path**, which is decided under the session lock before any row or File lock. Extensions and later chunks take no creation-only dependency. Check order and precedence are unchanged. A test covers a classification source applied by an earlier chunk. | §4.4, LP-4b |
| **SA-R5** Guard before SQL | New `LockSequence` contract: batch preflight before SQL; separate recording of acquired, missing, attempted and private-new resources; preflight of uniqueness-key inserts; final strength enforced. Implicit locks are covered by the proof, not by instrumentation. Sub-levels are mapped to repository operations. | §6 |
| **SA-R6** Test designs | LP-1…LP-8 rewritten with controller-driven choreography, actual blockers and backend pids, final-state assertions, and 40P01 kept distinct from 55P03. Added: LP-9 (granted-lock cycle), LP-10 (File/content cycle), LP-11 and LP-12 (closure growth, concurrent publication). | §10 |

---

## 1. Summary

- **The accepted protocol has three existing cross-session or cross-subsystem cycles**, plus a fourth found in this
  revision:
  - A: opposing worksheet-row locks;
  - B: opposing worksheet-key uniqueness waits;
  - C: grouping against the Phase 2 restriction cascade, including the three-transaction form G → L → C → G;
  - D: content-before-parent in `commitShared` against parent-before-content in `destroyHolding`.

  Each is detected by PostgreSQL and aborts one transaction with 40P01, so it fails visibly with no partial commit.
  They are defects nonetheless.
- **The revised protocol is a single total order over resources:**

  | Level | Resources |
  |---|---|
  | L0 | restriction operation |
  | L1a | capture session (S) |
  | L1b | Files (F) |
  | L1c | content (Ct) |
  | L1d | worksheet keys (K) |
  | L1e | Archival Record (R) |
  | L1f | Repository and Source (C) |
  | L2…L5 | Phase 4 |

  - Ascending UUID within F, Ct and K.
  - Membership predicates on worksheet rows.
  - Ordered uniqueness inserts.
- **Two kinds of resource are outside the order**, each with its own lemma:
  - *private new rows*, created by the transaction that holds them (lemma L3);
  - *operation-owned preservation rows*, written only under the operation's advisory ownership (lemma L4, which
    rests on invariant I-ADV, proof obligation PO-1).
- **With the decisions of §8 adopted, §5 proves that no wait-for cycle exists among the enumerated writers.** The
  proof depends on two Phase 3 changes (D-LP-5, D-LP-7) and three Phase 2 changes (D-LP-3, D-LP-6a, D-LP-6b). Each
  needs its own owner decision; none is assumed.

## 2. Complete affected-writer lock inventory (current behaviour)

Notation:
- `X↑` = rows of X in ascending UUID order. `X(plan)` = rows of X in an unordered or plan order.
- `U` FOR UPDATE, `Sh` FOR SHARE, `KS` FOR KEY SHARE, `NKU` the NO KEY UPDATE taken by an ordinary UPDATE.
- `ins` INSERT; `uq` uniqueness wait; `fk` implicit foreign-key KEY SHARE; `trg` trigger.

### 2.1 Phase 3 (records)

| Writer | Current acquisition sequence | Backward or unordered edges today |
|---|---|---|
| G `GroupingService.applyChunk` | advisory try (session) → S U → K(plan) U → F(plan) Sh → [existing: R U] or [new: K classification-source U (re-lock), F classification-source Sh, R ins (fk S, C, L7)] → `record_file` ins (fk F, held) → K NKU (`markApplied`) → R NKU (`touch`) | K and F in plan order; K after F on the new-Record path |
| A `CaptureSessionService.add` (`addFiles`, `addBatch`, `addByFriendlyIds`) | S U → for each id in request order: K ins (uq on `file_id`) → fk F KS | uq and fk interleaved in request order (Scenario B) |
| M `saveMarks` | S U → K(request) U, membership checked after lock | foreign rows locked (Scenario A) |
| X `removeFile` | S U (row's session) → K U → K delete | — |
| O `correctOverrides` | K U → K NKU (fk C) | — |
| SC `CaptureSessionService.correct` | S U → S NKU (fk C) | — |
| L `RecordService.linkFile` | R U → `record_file` ins (fk **F KS**) → R NKU | **R → F** (SA-R1) |
| RL other `RecordService` link edits (correct, move, unlink) | R U → `record_file` NKU or delete → R NKU | — |
| RR `RecordService` correct, lifecycle, roles | R U → R NKU (fk C, L7); `record_role` ins, NKU or del (fk R held) | — |
| RC `RecordService.create` | R ins (fk S KS, C KS, L7) | — |
| CI `CitationService` create, correct | R U → `citation` ins or NKU (fk C KS, R held) | — |
| CA `CatalogService` correct Source or Repository | C U → C NKU | — |

### 2.2 Phase 2 (preservation)

| Writer | Current acquisition sequence | Backward or unordered edges today |
|---|---|---|
| FC `FileService.correct` (restricting) | F_root U → F_root NKU → **trg cascade: F_descendants NKU (recursive, plan order)** | descendants after root in any UUID order (SA-R2; Scenario C) |
| FL `FileService.correct` (other), `delete`, `restore` | F U → F NKU (trg `restriction_inherited` reads parents, no lock) | — |
| FD `FileService.destroyHolding` | advisory **try** (each repair, bounded 2 s busy-wait) → F U → [replacement read, unlocked] → **Ct U** → F NKU (**fk replacement F KS**) → Ct NKU → repair op rows NKU → DESTROY op ins (fk F held, Ct held) | replacement F after F in any UUID order; F → Ct |
| OS `OperationProcessor.commitShared` | (worker holds the op's session advisory) → **Ct U** → op NKU → **insertFile: parent F U** → new F ins (private; fk Ct held, op own) → `file_derivation` ins (fk new F, parent F held; trg parent Sh held, new F NKU private) | **Ct → parent F** (Scenario D, against FD's F → Ct) |
| ON `OperationProcessor.commitNewContent` | (advisory) → op NKU → location release (own) → Ct ins (private) → generation ins (uq location: own path) → insertFile: parent F U → new F ins (private) → derivation as above | op → F (see lemma L4); private Ct before F (lemma L3) |
| OR `OperationProcessor.commitRepair` | (advisory) → op NKU → Ct U → generation NKU, ins → unresolved ins (uq location) → findings NKU → history | — (no F) |
| OC `OperationProcessor.createOwned` (repair copy creation) | (advisory) → Ct Sh → op NKU | — |
| IR `IngestionService` receive | op ins (fk parent F KS when deriving; batch) | single F, nothing held before it |
| IS `IntegrityService` per-content transactions | Ct NKU (state, `last_verified_at`) → finding ins (fk Ct held) | single Ct |
| DP `DestructionProcessor` | (advisory) → generation NKU → Ct NKU → op NKU | generation → Ct (lemma L4) |
| UQ `UnresolvedObjects`, `QuarantineMoves`, `OperationAdminService`, `RecoveryService` | advisory try → unresolved, quarantine, generation or op rows NKU; `RecoveryService` content updates run one statement per transaction | — (no F, S, K or R) |
| AI `ArchiveService` initialisation | `LOCK TABLE archive_instance` | initialisation only; no archival writer touches `archive_instance` |

**Resources that create no wait-for edge, checked in source:**

| Resource | Why no edge |
|---|---|
| Friendly-ID allocation (`FriendlyIdSeriesRepository.allocate`: read of `friendly_id_series`, then `nextval`) | `nextval` is non-transactional and holds no lock until commit; `friendly_id_series` is never updated by archival writers |
| `change_history` inserts (`ChangeRecorder`) | fresh UUID primary key; private row (L3) |
| `record_file` (record, file) uniqueness | every inserter holds the Record FOR UPDATE first, so two inserters of the same key never overlap |
| `capture_session_group` and worksheet `sequence_number` uniqueness | written only under the session FOR UPDATE |
| `file_capture_context.file_id` uniqueness | **does** create waits (Scenario B); ordered by rule 1 (L1d) |

Every advisory lock used by archival writers is a **try** lock (`pg_try_advisory_lock`,
`pg_try_advisory_xact_lock`, `tryLockAll`). It never enters PostgreSQL's wait queue, so it adds no wait-for edge.
`destroyHolding`'s retry loop is bounded (2 s) and then fails visibly.

### 2.3 Phase 4 (planned)

W1–W5 and W11 take R Sh as their first and only L1 lock. W10 (the confirmation) takes no L1 lock. W12 Role
withdraw and restore take R U. See §9.

## 3. The revised order and rules

**Order:** L0 → L1a S → L1b F↑ → L1c Ct↑ → L1d K↑ → L1e R → L1f C → L2 … L5 (Phase 4). L7 is implicit only.

**Rules:**
1. **Monotonic.** Every *blocking* acquisition (explicit lock, ordered uniqueness insert, or implicit lock that can
   wait) has a key (level, UUID) ≥ every key the transaction already holds. Levels may be skipped.
2. **Lock final strength first.** No upgrade at any level.
3. **Parents first.** Before any statement that takes an implicit lock (foreign key, trigger) on a resource, the
   transaction already holds that resource at the same or a stronger mode, or the resource is private (rule 5).
   **There is no other exception.** The revision 1 "implicit KEY SHARE after Record" exception is withdrawn.
4. **Session membership in SQL.** Session-scoped writers lock worksheet rows only through a predicate on their own
   session.
5. **Private new rows.** A row inserted by a transaction may be locked or referenced by that transaction at any
   point (lemma L3).
6. **Operation-owned rows.** `file_operation`, `file_content_generation`, `physical_location_owner`,
   `unresolved_object`, `quarantine_object` and the integrity rows are written only under invariant I-ADV, and stay
   outside the order (lemma L4).
7. **Presentation order is separate from lock order.** Worksheet sequence numbers and history follow the request,
   and grouping applies rows in plan order.

## 4. Revised writer sequences and SQL

### 4.1 Mapping of resources to SQL (all explicit locks are single-row, issued in ascending UUID order)

| Level | Lock statement (exact) |
|---|---|
| L1a S | `SELECT <SESSION_COLUMNS> FROM gas.capture_session WHERE capture_session_id = :id FOR UPDATE` (unchanged) |
| L1b F (Sh, U or KS) | `SELECT file_id FROM gas.archival_file WHERE file_id = :id FOR SHARE` / `FOR UPDATE` / `FOR KEY SHARE` |
| L1c Ct | `SELECT content_id FROM gas.file_content WHERE content_id = :id FOR UPDATE` (or `FOR SHARE`) (unchanged) |
| L1d K (lock) | `SELECT <ROW_COLUMNS> FROM gas.file_capture_context WHERE capture_session_id = :s AND file_id = :f FOR UPDATE` |
| L1d K (insert) | `INSERT INTO gas.file_capture_context (file_id, capture_session_id, sequence_number, added_at, added_by) VALUES (:f, :s, :seq, :at, :by) ON CONFLICT (file_id) DO NOTHING` (unchanged SQL; issued in ascending `file_id`) |
| L1e R | `SELECT … FROM gas.archival_record r … WHERE r.record_id = :id FOR UPDATE OF r` (unchanged), and for Phase 4 the same statement with `FOR SHARE OF r` |
| L1f C | `SELECT … FROM gas.source WHERE source_id = :id FOR UPDATE` and the same for `repository` (unchanged) |

Single-row statements in a Java-sorted loop are used instead of one `… ORDER BY … FOR …` statement. That way every
acquisition is preflighted by the guard (§6), and the order does not depend on plan shape.

### 4.2 G: `GroupingService.applyChunk` (SA-R4)

1. **S U** (unchanged), under the session's advisory try-lock as today.
2. **Decide the path** under S: `target = g.targetRecordId`, or else `capture_session_group` for the key. The group
   row is written only under S, so it is stable. `newRecord = (target == null)`.
3. **F↑ Sh** over chunk Files ∪ `g.continuesFrom` ∪ (newRecord and a classification source ≠ null ?
   {classificationSource.file} : ∅).
4. **K↑ U** with the membership predicate, over chunk rows ∪ (newRecord and a classification source ≠ null ?
   {classificationSource.row} : ∅). A requested row that is not returned is recorded as missing. The accepted
   checks then report Conflict exactly where `lockRow` returned empty or a foreign row before.
5. **Validation, in the accepted order and precedence, unchanged:**
   1. rows: version, applied, membership → Conflict;
   2. chunk Files: visibility → Hold, lifecycle → Conflict;
   3. `continuesFrom` visibility → Hold;
   4. existing path: R U, then visibility and lifecycle → Hold;
   5. new path: classification-source row (unchanged and unapplied → else Conflict), then its File (visibility →
      Hold, lifecycle → Conflict).
6. **Writes unchanged:** R insert (new path), links, `markApplied`, touch, run records, faults.

Extensions and later chunks have `target ≠ null` (set by the first chunk, or found in `capture_session_group`), so
they take no classification-source lock and perform no check of it, exactly as the accepted code. Every check still
runs after its lock, and locks only add serialisation. Plan order, chunking, holds, conflicts, history and
idempotency are therefore unchanged.

### 4.3 A: `CaptureSessionService.add` (D-LP-2 with the safeguards of direction §9)

1. **S U.**
2. **Deduplicate** by stable first occurrence (`LinkedHashSet`, as today).
3. **Classify each id in request order, unlocked as today:** hidden or nonexistent → `hidden`; destroyed →
   `destroyed`; otherwise *eligible*, with eligible request rank 1…n.
4. **F↑ KS** over the eligible Files. This matches the foreign-key lock the insert would take, so no visibility or
   restriction semantics change. KEY SHARE does not block restriction updates, which is the accepted behaviour.
5. **K↑ inserts:** for each eligible File in ascending `file_id`, one insert attempt with provisional
   `sequence_number = base + rank`, where `base = max(sequence) of S` under S. An update count of 1 means *added*,
   0 means *taken*. The uniqueness wait is ordered by `file_id`, and the foreign-key KS is already held.
6. **Compaction**, if any eligible File was taken. For the added Files in ascending final rank r = 1…m, if
   `provisional ≠ base + r`:

   ```sql
   UPDATE gas.file_capture_context SET sequence_number = :target
    WHERE file_id = :f AND capture_session_id = :s AND sequence_number = :provisional
   ```

   - Each target is free when its turn comes: it was a skipped provisional position, or it was vacated by an
     earlier-ranked row. So the immediate `(capture_session_id, sequence_number)` uniqueness holds at every
     statement, and `sequence_number ≥ 1` and the integer range are respected.
   - `row_version`, `added_at` and `added_by` are not changed. The rows are this transaction's own inserts
     (lemma L3).
7. **History** for added Files in request order, with content unchanged.
8. **Counts unchanged:** added, taken, hidden + destroyed.

### 4.4 M, X and O

| Writer | Revised sequence |
|---|---|
| M `saveMarks` | S U → K↑ U with the membership predicate. Any requested id not returned → "not found", before any update. Edits are then applied in request order, with the accepted checks. |
| X `removeFile` | Unchanged: S U → K U (now through the predicate) |
| O `correctOverrides` | Unchanged: K U → C (foreign key) |

### 4.5 L: `RecordService.linkFile` (SA-R1)

1. **F Sh** on the File. **Justification of the mode:** linking must not race a restriction or lifecycle change of
   the File, because the accepted code authorises on visibility and refuses DESTROYED Files. KEY SHARE does not
   block those updates (they take NKU); SHARE does. UPDATE is unnecessary.
2. **R U** (`lockVisible`): record visibility, ACTIVE state and version, as today. Its exceptions keep their
   precedence over File errors.
3. **Revalidate the File under its lock:** visible to the actor (else not found) and not DESTROYED (else the accepted
   rule message).
4. **Insert the link.** Its foreign-key KS on F is already covered by the held Sh. Then touch R and write history,
   unchanged.

The other link edits (correct, move, unlink) take no F lock and need none: their statements take no lock on
`archival_file`.

### 4.6 FC: `FileService.correct` when restricting (SA-R2; Phase 2 change D-LP-3)

Only the NORMAL → RESTRICTED change cascades. Every other `correct`, and `delete` and `restore`, keeps the single
`F U`.

**Attempt** (one transaction; at most 3 attempts):

1. **Discovery** (statement 1, no lock): the reachable set from the root, whatever its nodes' state.

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

2. **Acquisition:** for each id in ascending UUID order (the root takes its place in that order; it is not locked
   first):

   ```sql
   SELECT file_id FROM gas.archival_file WHERE file_id = :id FOR UPDATE
   ```

3. **Re-discovery** (statement 3, fresh READ COMMITTED snapshot): the same SQL as step 1. If its result differs from
   the locked set, the attempt is abandoned: **rollback**, then the next attempt in a new transaction.
4. **Root checks under lock, unchanged:** visibility, not DESTROYED, `row_version = expectedVersion` (the
   researcher's version, never refreshed), unrestrict rules. A mismatch → the accepted Conflict, with no retry.
5. **`inheriting`** = the accepted `unrestrictedDescendants(root)` query, now over locked rows. Then
   `updateAttributes`. The V0014 trigger cascades over rows already held, so it does not wait. History for the
   root and each inheriting derivative is unchanged. The post-check `unrestrictedDescendants(root)` is empty.
6. **After 3 abandoned attempts:** `ConflictException` with the message "Derivative Files of F00012 were being added
   while it was restricted; nothing was changed. Try again." It fails visibly, and nothing is committed.

**Why the closure is stable after step 3** (no publisher can commit a new reachable edge):
- `file_derivation` rows are immutable and never deleted (V0011 triggers), so the reachable set can only grow, and
  it grows only by a new edge whose parent is reachable.
- Every insert of a `file_derivation` row fires `file_derivation_inherit_restriction`, which takes `FOR SHARE` on the
  parent. The only publisher (`OperationProcessor.insertFile`) also takes the parent `FOR UPDATE` before inserting.
- After step 2 every reachable node is held FOR UPDATE. So an edge whose parent is reachable is either:
  - **committed before** that parent's lock was granted, and then visible to step 3 (a fresh statement), which
    restarts the attempt; or
  - **uncommitted** while the parent is held, and then its publisher blocks on the parent lock until this transaction
    commits. Its trigger then reads the parent as RESTRICTED and restricts the new child, which preserves the
    accepted inheritance (GAS-P2-004).
- So when step 3 matches step 2, the locked set equals the reachable set at commit time for every committed edge.
  Uncommitted edges are handled by inheritance.

### 4.7 Phase 2 writers: File before content (SA-R3; Phase 2 changes D-LP-6a and D-LP-6b)

| Writer | Revised sequence |
|---|---|
| FD `destroyHolding` (D-LP-6a) | advisory try (repairs) → **F↑ over {file, replacement}**: the file U, the replacement KS. A replacement id equal to the file id is not locked twice. → Ct U → updates as today. **Check order and messages unchanged:** file not found → version Conflict → lifecycle rule → replacement rule (equal, missing or DESTROYED, now read under its KS lock) → content and repair checks. A missing replacement row is recorded as MISSING and reported by the replacement rule in its accepted position. |
| OS `commitShared` (D-LP-6b) | (advisory) → **parent F U** (when the operation derives) → Ct U → state and generation re-check (unchanged) → `usedByRestrictedFile` (unchanged; reads) → op transition → `insertFile` with the parent already held (its `lock(parent)` becomes a same-strength re-lock) |
| ON `commitNewContent` | Unchanged. The parent F U follows only operation-owned and private resources (L3, L4). |
| OR, OC, IS, DP, UQ | Unchanged |

**Scenario D before and after.** Today FD holds the file and waits for the content, while OS holds the content and
waits for the parent, the same file. After the change both take F before Ct, so the cycle is impossible (lemma L1).

## 5. Wait-for argument (revision 2)

A transaction T **waits for** U when T requests a heavyweight lock, an implicit lock, or a uniqueness or tuple wait
that conflicts with something U holds. Advisory **try** locks and sleeps never enter the wait queue, so they create
no edge.

**L1 (ordered acquisition).** Suppose every blocking request of every transaction carries a key ≥ every key the
transaction holds (§3 rule 1). Let T₁ → T₂ → … → Tₙ → T₁ be a cycle in which Tᵢ requests a key kᵢ held by Tᵢ₊₁.
- Then kᵢ ≥ max(held by Tᵢ) ≥ k₍ᵢ₋₁₎, because Tᵢ holds k₍ᵢ₋₁₎.
- Around the cycle, k₁ ≤ k₂ ≤ … ≤ kₙ ≤ k₁, so all keys are equal: everyone waits for the same key k, held by
  everyone.
- But a transaction holding k does not wait for k in a conflicting mode from another holder unless it is upgrading,
  and rule 2 forbids upgrades.

So no cycle. Uniqueness waits on ordered inserts are requests on key (L1d, `file_id`), held by the inserting or
deleting transaction, and are covered.

**L2 (implicit edges).** Every implicit lock taken by a foreign key or trigger is on a resource already held at an
equal or stronger mode (rule 3), or on a private row (L3). It therefore never waits. Specifically:

| Implicit lock | Covered by |
|---|---|
| `addRow` fk F | F KS held |
| `record_file` fk F | F Sh held |
| `archival_file` fk Ct | Ct U held, or Ct private |
| `archival_file.replacement_file_id` fk | replacement KS held |
| `file_derivation` fk and trigger on the parent | parent U held |
| restriction cascade NKU on descendants | closure U held, stabilised (L5) |
| Record and Citation fk on S and C | S held (G), or C last and S first (RC; see L6) |
| Session fk on C | S then C |
| Override fk on C | K then C |
| History, vocabulary and app-user fk | L7: implicit KS only; their only conflicting modes are FOR UPDATE and key updates, which no archival writer takes, and the one explicit L7 lock (the enabled-administrator check) runs in transactions that take no L0–L6 lock afterwards |

**L3 (private rows).** A row inserted by T is invisible to other transactions until T commits. Others can wait on it
only through a uniqueness check on its keys. The relevant keys are fresh UUIDs (`file_id`, `content_id`,
`operation_id`, `historical_date_id`), own-operation keys (`created_by_operation`, `ingest_operation_id`) and
own-path keys (`physical_location_owner` and generation location for a path owned by T's operation; PO-2). So no
other transaction ever waits on a private row, and holding one adds no incoming edge, whatever its position in the
order.

**L4 (operation-owned rows).** Invariant **I-ADV**:
- a `file_operation` row is updated only by a transaction whose process holds that operation's advisory lock;
- `file_content_generation`, `physical_location_owner`, `unresolved_object` and `quarantine_object` rows tied to an
  operation or content are updated only by such a transaction, or one holding the content's U lock;
- **exception:** RECEIVING-state operation rows, which are written by the receiver and by `abandonStaleReceiving`.
  No writer holding F, Ct, S, K or R updates a RECEIVING row; FD transitions only `STOPPED_BY_DESTRUCTION` states,
  which exclude RECEIVING.

Under I-ADV two transactions never contend for the same operation-owned row, so these rows add no edge.
- I-ADV is not enforced by the database. It is a code invariant, and **proof obligation PO-1** verifies it by an
  inventory test (§10, LP-10b).
- If PO-1 failed, these rows would have to join the order after Ct (decision D-LP-8).

**L5 (closure).** The cascade's NKU on descendants never waits, because every descendant is held FOR UPDATE by FC
after stabilisation (§4.6). A descendant added concurrently waits for FC (its publisher blocks), and not the
reverse, so the cascade adds no edge. Its locks are acquired ascending (rule 1).

**L6 (first-only waits).** A transaction whose only blocking request is its first lock (it holds nothing when
waiting) cannot be in a cycle. This covers RC's fk on S (a new Record holds only private rows), IR's fk on the
parent, single-content IS transactions, and RecoveryService's one-statement transactions.

**Theorem.** With D-LP-1, D-LP-3, D-LP-5, D-LP-6a, D-LP-6b and D-LP-7 adopted and PO-1 and PO-2 verified, every
writer of §2 and §9 makes only ordered blocking requests (L1), implicit locks that never wait (L2), private-row
locks (L3), operation-owned-row writes without contention (L4), cascade locks under a stabilised closure (L5) or
first-only waits (L6). No wait-for cycle exists.

**What is not proven:**
- **If a decision is declined,** its scenario remains. Each is detected by PostgreSQL as 40P01, which fails visibly:
  D-LP-3 leaves Scenario C, D-LP-5 leaves SA-R1, D-LP-6a and 6b leave D, and D-LP-7 leaves A and B.
- **Writers added after this inventory** (Phase 4 WP2 and later) must be added to §2 and §9 before
  implementation.

## 6. `LockSequence` contract (SA-R5)

| Operation | Called | Checks and records |
|---|---|---|
| `preflight(level, table, ids, mode)` → `Batch` | before the first lock statement of a batch (one or more rows) | **Validates the whole batch before any SQL:** ids strictly ascending (unsigned); first id ≥ every held key; no id held at a weaker mode (no upgrade); level consistent with the table. Throws before execution. Records nothing as acquired. |
| `Batch.acquired(id)` | after a lock statement returns the row | records ACQUIRED(level, table, id, mode) |
| `Batch.missing(id)` | after a lock statement returns no row (missing, foreign session, deleted) | records MISSING: no lock held |
| `Batch.attempted(id, sqlState)` | when a lock statement fails (lock timeout 55P03, deadlock 40P01, other) | records ATTEMPTED with its SQLSTATE; the transaction is then rolled back by the caller |
| `preflightInsert(level, table, keys)` → `Batch` | before ordered uniqueness inserts (K) | the same validation as `preflight`, mode UPDATE (an insert or its uniqueness wait conflicts like an exclusive lock) |
| `Batch.inserted(id)` / `Batch.taken(id)` | after each insert | INSERTED (the row is held and private) or TAKEN (no lock held; any wait ended) |
| `privateNew(table, id)` | after inserting a row with a fresh key outside a batch | PRIVATE_NEW: later explicit locks or re-locks of it are exempt from order checks (L3) |

- Implicit locks (foreign keys, triggers) are **not** instrumented. They are covered by §5 L2, and LO-7
  (`pg_locks` with actual blockers) tests them.
- **Sub-level mapping:**

  | Level | Tables |
  |---|---|
  | `L1_SESSION` | `capture_session` |
  | `L1_FILE` | `archival_file` |
  | `L1_CONTENT` | `file_content` |
  | `L1_WORKSHEET` | `file_capture_context` |
  | `L1_RECORD` | `archival_record` |
  | `L1_CATALOG` | `source`, `repository` |

  Operation-owned tables are not announced (L4), and PO-1 checks that they are never locked explicitly.
- **Repository mapping:** `CaptureSessionRepository.lock` → `L1_SESSION`; the new `lockSessionRow(s, f)` →
  `L1_WORKSHEET`; `lockRow(f)` → `L1_WORKSHEET`; `addRow` → `preflightInsert` and `L1_WORKSHEET`;
  `ArchivalFileRepository.lock`, `lockShared` and the new `lockKeyShare` → `L1_FILE`;
  `FileContentRepository.lock` and `lockShared` → `L1_CONTENT`; `ArchivalRecordRepository.lock` and the new
  `lockShared` → `L1_RECORD`; `CatalogRepository.lockSource` and `lockRepository` → `L1_CATALOG`.
- The guard stays enabled for every test run, as today.

## 7. Phase 2 and Phase 3 behaviour that would change (each needs an explicit owner decision)

| ID | Accepted component | Change | Behaviour seen by users |
|---|---|---|---|
| D-LP-3 | Phase 2 `FileService.correct` (restricting) | Stabilised closure locked in ascending order, bounded restart (§4.6) | Unchanged, except a new visible conflict after 3 restarts during concurrent derivative publication |
| D-LP-6a | Phase 2 `FileService.destroyHolding` | Lock {file, replacement} ascending before content; replacement check under lock | Unchanged messages and outcomes |
| D-LP-6b | Phase 2 `OperationProcessor.commitShared` | Lock the parent File before the content | Unchanged |
| D-LP-5 | Phase 3 `RecordService.linkFile` | File FOR SHARE before the Record; revalidate under both locks | Unchanged messages; a link now waits for a concurrent restriction of the File instead of racing it |
| D-LP-7 | Phase 3 `GroupingService`, `CaptureSessionService` | §4.2–§4.4 | Unchanged |

## 8. Decisions requested (updated)

| ID | Decision | Recommendation |
|---|---|---|
| D-LP-1 | The order and rules of §3, including withdrawal of the revision 1 exception | Approve |
| D-LP-2 | `add` with sorted uniqueness inserts and compaction, under the safeguards of §4.3 | Approve |
| D-LP-3 | Modified option (a), §4.6 (Phase 2 change) | Approve |
| D-LP-4 | The `LockSequence` contract of §6 and the L1 sub-levels; closes GAS-P4-002 | Approve |
| D-LP-5 (new) | `linkFile` File-before-Record, §4.5 (Phase 3 change) | Approve |
| D-LP-6a, 6b (new) | File-before-content in `destroyHolding` and `commitShared`, §4.7 (Phase 2 changes) | Approve |
| D-LP-7 (new) | Grouping, `add`, `saveMarks` and `removeFile` sequences, §4.2–§4.4 (Phase 3 changes) | Approve |
| D-LP-8 (conditional) | Only if PO-1 fails: bring operation-owned rows into the order after Ct | Decide only if needed |

## 9. Phase 4 writers and LO-1…LO-7

- **W1–W5 and W11:**
  - R Sh is their first L1 lock and their only one.
  - They then take L2…L5, ascending.
  - Their implicit locks are foreign keys to R (held), to `record_role` (R-protected; NKU and KS compatible) and to
    L7.
- **W10:** no L1 lock.
- **W12:** R U.
- **No Phase 4 writer depends on a session mutex.**
- **Test changes:**
  - **LO-1** records exact sequences per writer, including MISSING, TAKEN and PRIVATE_NEW entries.
  - **LO-2** uses the §6 contract.
  - **LO-3** also checks `FOR KEY SHARE` statements and that operation-owned tables are never locked explicitly
    (PO-1).
  - **LO-4** adds LP-1…LP-12.
  - **LO-5** is unchanged.
  - **LO-7** verifies L2 with actual blockers.

## 10. Revised concurrency-test designs (not implemented)

**Common rules for every test:**
- **Matrix:** PostgreSQL 16 and 18, on Linux and Windows.
- **Real transactions** on separate pooled connections, each with `application_name`, backend pid and `txid`
  recorded at start.
- **A test controller** drives the schedule: it opens transactions, runs statements in threads, and advances only
  when a named session is observed waiting. Waiting is defined as `wait_event_type = 'Lock'`, with
  `pg_blocking_pids` naming the expected pid.
- **Blocking is asserted** from actual blockers and backend identities, never from the absence of rows in
  `pg_locks`.
- **Timeouts:** each session sets `lock_timeout = '20s'`, and `deadlock_timeout` is the server default.
- **Outcome classes, distinguished:** SQLSTATE 40P01 (deadlock), 55P03 (lock timeout), success, or an expected
  application exception.
- **Final-state assertions** on rows, versions and history.
- **Each scenario test runs twice.** First against the current code: expected 40P01, recorded as characterisation
  evidence. Then against the implemented protocol: expected no 40P01 and the specified final state.
- **Seams:** where an interleaving needs a pause inside a service, a test-only seam pauses at a named point, in the
  style of `GroupingFaults` (`CaptureFaults`, `FileFaults`). Seams are no-ops in production.

| ID | Choreography | Assertions after the protocol is implemented |
|---|---|---|
| LP-1 (A) | Sessions S1 {A}, S2 {B}. T1 `saveMarks(S1, [A, B])` and T2 `saveMarks(S2, [B, A])`. The controller waits until both hold their session, then releases both. | Each answers not found for the foreign row. `pg_blocking_pids` never shows T1 blocked by T2 or the reverse. No 40P01. Rows unchanged. |
| LP-2 (B) | Unassigned F1 < F2. T1 `add(S1, [F2, F1])` paused at the seam *after its first insert attempt*; T2 `add(S2, [F1, F2])` started; the controller observes T2 **waiting with blocker = T1's pid** on F1, then releases T1. Repeated with T2 first. | No 40P01. The first transaction to reach F1 adds both, and the other reports both taken. Each session's rows are consecutive, in its request order. No row is in both sessions. |
| LP-3 (compaction) | Session S with rows 1…5. F-ids chosen so ascending UUID order ≠ request order: request [Fc, Fa, Fd, Fb] (eligible ranks 1…4); a concurrent claim takes Fa before T inserts it. Variant with a hidden and a destroyed id interleaved, and a duplicate id. | Provisional positions 6, 7, 8, 9; Fa taken; compaction moves Fd 8 → 7 and then Fb 9 → 8, each into a slot just vacated. No unique violation. Final Fc 6, Fd 7, Fb 8. `row_version = 1`, `added_at` and `added_by` unchanged. Counts: added 3, taken 1, hidden + destroyed 2. History in request order. |
| LP-4a (grouping order) | A chunk whose plan order is the reverse of UUID order, with continuations | Same Records, links, holds, conflicts and history as a serial reference run. The recorded sequence is S, then F↑, then K↑, then R. |
| LP-4b (SA-R4) | A group whose classification source is in chunk 1 and applied there; a forced restart before chunk 2; then chunk 2 (extension) | Chunk 2 neither locks nor checks the source row or File. Its outcome equals the accepted code's. The recorded sequence contains no classification-source entries. |
| LP-5 (C) | A chunk holding derivative D and parent P (D < P and D > P variants); the controller holds G after its F locks; FC restricts P | No 40P01. Either FC waits for G (blocker = G) and then cascades, or G waits for FC. Final: D RESTRICTED, with inheritance history. |
| LP-6 | See §11 (executed as a harness) | — |
| LP-7 (Phase 4) | A WP4 writer racing grouping on the same Record | The Phase 4 writer requests only R Sh at L1; no cycle |
| LP-8 (guard) | Whole integration suite with the §6 guard | No violation |
| LP-9 (SA-R1) | G holds D Sh; FC holds P U and its cascade waits for D; L requests P Sh (new order) | L **waits before taking R** (blocker = FC). G takes R. No cycle: G commits, then FC, then L. No 40P01. Final state as in serial order. |
| LP-10 (D) | FD on File X (content Ct) racing OS committing a derivative of X sharing Ct, with the seam after each first lock | No 40P01. FD and OS serialise on X. Final: FD outcome and OS outcome as in serial order. |
| LP-10b (PO-1) | Inventory: every SQL statement writing operation-owned tables is reached only from methods running under the operation's advisory lock (or the receiver path for RECEIVING), checked by an `ArchitectureTest` call-graph rule and a runtime assertion in tests | Holds, or PO-1 fails, which triggers D-LP-8 |
| LP-11 (closure growth) | FC discovers {P, D}; the seam pauses before its acquisition; a publisher commits D2 under D; FC acquires and re-discovers | The attempt restarts. The second attempt locks {P, D, D2}. D2 ends RESTRICTED, with history. The expected version is unchanged. |
| LP-12 (concurrent publication) | FC holds the full closure; a publisher of D3 under D (D3 uncommitted, its parent lock requested) | The publisher waits (blocker = FC). After FC commits, the trigger restricts D3. No 40P01. With growth forced 3 times, a visible Conflict and nothing changed. |

## 11. Bounded verification executed (non-production)

### 11.1 LP-6 (direction §8)

The harness is `src/test/java/org/genealogyarchive/data/Lp6LockBehaviourIT.java`. It uses plain SQL sessions
against the existing schema; no production code is involved.

| Schedule | PostgreSQL 16 (builder's local server) | CI (PostgreSQL 16 and 18, Linux and Windows) |
|---|---|---|
| G holds F FOR SHARE; C requests F FOR UPDATE (waits, blocker = G); L inserts `record_file` (fk KEY SHARE on F) | L granted immediately; C's blockers = {G}; C granted after G commits | Reported in the checkpoint for `dc274cd` |
| Same with L issuing `SELECT … FOR KEY SHARE` | L granted immediately | As above |

**Conclusion:** a compatible KEY SHARE does not queue behind a waiting FOR UPDATE on these versions. **This does not
dismiss SA-R1:** the SA-R1 cycle uses *granted* locks.

### 11.2 SA-R1 cycle reproduction (current protocol)

| Step | Session |
|---|---|
| 1 | G: derivative FOR SHARE (granted) |
| 2 | C: parent FOR UPDATE (granted); the restriction UPDATE's cascade waits for G (blocker = G) |
| 3 | L: Record FOR UPDATE (granted); `record_file` insert waits for C (blocker = C) |
| 4 | G: Record FOR UPDATE |

- **Local result (PostgreSQL 16):** `ERROR 40P01 deadlock detected`, with the detail "Process G waits for … blocked
  by L; L … blocked by C; C … blocked by G".
- The same schedule is `Lp6LockBehaviourIT.saR1_theGrantedLockCycleOfTheAcceptedProtocolDeadlocks`, run on every CI
  job.

## 12. Residual risks

- **PO-1 (I-ADV) and PO-2 (own-path uniqueness)** are code invariants verified by tests, not by database
  constraints. A future preservation writer that violates them invalidates L4 or L3.
- **Writers added after this inventory** must be inventoried before implementation.
- **The FC restart bound** (3) can make restriction fail visibly under sustained derivative publication. That is
  acceptable and fails visibly.
- **Any declined decision** leaves its scenario's 40P01, detected and failing visibly.

## 13. Proposed wording

### 13.1 Clarifications §5.5.3 (replaces the L1 row and rules 1 and 3)

> | L1 | **L1a** capture session → **L1b** Files (ascending) → **L1c** content (ascending) → **L1d** worksheet keys (ascending; locks and uniqueness inserts) → **L1e** Archival Record → **L1f** Repository and Source. Operation-owned preservation rows are outside the order under invariant I-ADV; rows a transaction inserted itself are private. | per WP1-R1 revision 2 §4 | Phase 2, Phase 3; Phase 4 takes L1e only |
>
> 1. **Monotonic acquisition.** Every blocking request (explicit lock, ordered uniqueness insert, implicit lock that can wait) has a key (level, sub-level, UUID) ≥ every key held. Session-scoped writers lock worksheet rows only through a predicate on their own session.
>
> 3. **Parents before implicit locks.** Before any statement that takes an implicit lock, the transaction holds that resource at an equal or stronger mode, or the resource is private. There is no other exception.

### 13.2 GAS-P4-002: proposed status text

> **Status:** superseded by WP1-R1 revision 2. The premise of this record (L1 safety from session-first locking) holds within one session only; the audit's Scenarios A and B, and Scenarios C and D found later, are cross-session or cross-subsystem cycles. The replacement order (L1a…L1f) and the guard contract are proposed in PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL revision 2, decisions D-LP-1…D-LP-8. Not implemented.
