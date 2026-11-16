# Phase 4 WP1: L1 locking-protocol proposal (WP1-R1, GAS-P4-002)

**Status: PROPOSAL for owner review and independent Codex assessment. Not implemented.** No production code,
migration or test in this document has been written. The test designs in §9 are specifications; none of them is
claimed to pass.

| | |
|---|---|
| Direction | Claude Code Direction Sheet: Phase 4 WP1 Audit Corrections, Workstream A |
| Audited head | `5600d782b5556179eac434fb785d9b8db059777c` |
| Inputs | The direction sheet's description of the audit's Scenarios A and B. The audit report `Phase-4-WP1-Independent-Audit.md` itself was not available to the builder. Clarifications §5.5.3; ADR-0011; ADR-0012 §8; issue GAS-P4-002 |
| Scope | Accepted Phase 3 L1 writers (and the Phase 2 File writers they meet), and how Phase 4 writers take their L1 resources |

---

## 1. Summary

The accepted Phase 3 code serialises work *within* one capture session by locking the session row first. Nothing
orders work *across* sessions. Two cross-session schedules, plus a third found while making this inventory, can
therefore deadlock.

- **Effect today.** PostgreSQL's deadlock detector aborts one of the transactions with an error. That is a visible
  failure, with no partial commit and no corrupted data. It is still a defect: correct requests can fail, and a
  grouping chunk can be aborted.
- **The proposal:**
  1. one explicit **L1 sub-order**: capture session, then worksheet keys, then Files, then Record, then
     Repository/Source;
  2. **membership predicates in the locking SQL**, so a session-scoped writer never locks another session's rows;
  3. **sorted acquisition** inside each sub-level, including the order of inserts that can wait on a uniqueness
     check;
  4. **presentation order kept separate from lock order**;
  5. replacing the opaque L1 of `LockSequence` with **checked sub-levels**, which closes GAS-P4-002.
- **Phase 4 is unaffected.** Phase 4 writers take exactly one L1 resource, the Archival Record, as their first
  lock. They never rely on a session mutex.
- **One residual risk needs an owner decision** (§8, D-LP-3): the Phase 2 restriction cascade on derivative Files.

## 2. Current behaviour and failure scenarios

### 2.1 The resources at L1

| Code | Resource | Explicit locks today | Implicit waits today |
|---|---|---|---|
| S | `capture_session` row | `CaptureSessionRepository.lock` (FOR UPDATE) | foreign-key KEY SHARE from `archival_record.capture_session_id` (insert) |
| K | worksheet key: the `file_capture_context` row of a File, or the *pending insert* of one (PK `file_id`) | `CaptureSessionRepository.lockRow` (FOR UPDATE), no membership predicate | the `ON CONFLICT (file_id)` uniqueness wait in `addRow` on an uncommitted insert or delete of the same `file_id` |
| F | `archival_file` row | `ArchivalFileRepository.lock` (FOR UPDATE), `lockShared` (FOR SHARE) | KEY SHARE from `file_capture_context.file_id` and `record_file.file_id` inserts; NO KEY UPDATE from the restriction cascade trigger (V0014) on derivatives; FOR SHARE from the `file_derivation` insert trigger on the parent |
| R | `archival_record` row | `ArchivalRecordRepository.lock` (FOR UPDATE) | KEY SHARE from `record_file`, `record_role` and `citation` inserts (row already held by the same writer) |
| C | `source` and `repository` rows | `CatalogRepository.lockSource` and `lockRepository` (FOR UPDATE) | KEY SHARE from Session, Record, Citation and override writes that reference them |

### 2.2 Scenario A: opposing explicit worksheet-row locks (existing Phase 3 defect)

`CaptureSessionService.saveMarks(S1, [A, B])` locks S1, then calls `lockRow(A)` and `lockRow(B)`. Each call locks
the row **before** checking that it belongs to S1. Meanwhile `saveMarks(S2, [B, A])` locks S2, then B, then A.

| T1 (session S1) | T2 (session S2) |
|---|---|
| locks S1 | locks S2 |
| locks row A (its own) | locks row B (its own) |
| waits for row B (held by T2) | waits for row A (held by T1) |

That is a cycle. A request naming a row of another session is invalid; it is rejected only after the lock wait.

### 2.3 Scenario B: opposing implicit uniqueness waits (existing Phase 3 defect)

`addFiles(S1, [F1, F2])` and `addFiles(S2, [F2, F1])`, where neither File is in any session.

| T1 (S1) | T2 (S2) |
|---|---|
| inserts row key F1 (uncommitted) | inserts row key F2 (uncommitted) |
| inserts F2: `ON CONFLICT` waits for T2 | inserts F1: `ON CONFLICT` waits for T1 |

That is a cycle on two keys that do not exist yet. Sorting the request list only in the application would not be
enough, because the wait is created inside PostgreSQL by the uniqueness check. The *insert order* is what must be
ordered (§4.3).

### 2.4 Scenario C: grouping against the restriction cascade (existing Phase 2/3 interaction, found here)

- Grouping (`GroupingService.applyChunk`) takes `lockShared` on each chunk File in *plan* order (GAS-P3-004).
- Restricting a File (`FileService.correct`, Phase 2) locks the parent P `FOR UPDATE`. The V0014 trigger then
  updates every NORMAL derivative D, which takes NO KEY UPDATE on D. That conflicts with FOR SHARE.

If one session holds both D and P:

| Grouping | Restriction of P |
|---|---|
| holds D (FOR SHARE) | holds P (FOR UPDATE) |
| waits for P (FOR SHARE) | cascade waits for D |

That is a cycle. It can happen only when a derivative and its parent are in the same chunk. Derivative Files can
be added to sessions.

### 2.5 Paths reviewed and found without a cycle

| Path | Locks (in order) | Why no cycle |
|---|---|---|
| `CaptureSessionService.removeFile` | S (the row's session), then K FOR UPDATE, then DELETE | Holds one S and one K; the row is filtered after locking, but it belongs to that S by an immutable `capture_session_id`. Waits on nothing after K. |
| `CaptureSessionService.correctOverrides` | K FOR UPDATE (no S), then KEY SHARE on C | One K only; C holders are sinks (§5). |
| `CaptureSessionService.correct` | S, then KEY SHARE on C | |
| `RecordService` correct, lifecycle, roles, link page and order changes | R FOR UPDATE, then KEY SHARE on C | |
| `RecordService.linkFile` | R FOR UPDATE, then KEY SHARE on F (`record_file` insert) | KEY SHARE waits only on F FOR UPDATE holders, which are sinks with respect to R, S and K (§5). |
| `CitationService` create and correct | R FOR UPDATE, then KEY SHARE on C | |
| `CatalogService` correct Source or Repository | C FOR UPDATE only | |
| `RecordService.create` | KEY SHARE on S (when a session is named) and C | No wait on R. |
| `FileService` correct, delete, restore and destroy (Phase 2) | F FOR UPDATE, then `file_content`, then cascade | Waits only on F (the cascade; Scenario C) and Phase 2 internals, never on S, K or R. |
| `OperationProcessor.insertFile` (Phase 2) | parent F FOR UPDATE, then insert of the derivative, then the `file_derivation` trigger FOR SHARE on the same parent | |
| Phase 2 operation, content and integrity paths | Phase 2 internal (`file_operation`, `file_content`, advisory locks) | Never wait on S, K or R. |

The friendly-ID allocator uses sequences (`nextval`), which take no row lock. History inserts reference `app_user`,
an L7 row locked only implicitly.

## 3. Recommended protocol

### 3.1 One L1 sub-order

**S < K < F < R < C.** Within K and F, rows are taken in ascending `file_id` order: PostgreSQL's unsigned UUID
order, as `LockSequence.compareUnsigned`. A transaction takes at most one S and at most one R today, and the
protocol keeps it that way. The Phase 4 levels L2…L5 follow C. L0 (the restriction operation) precedes S.

### 3.2 Rules (additions to clarifications §5.5.3)

1. **Membership in the locking SQL.** Every session-scoped writer locks worksheet rows with a predicate on its own
   session. A request naming a row of another session finds no row; it is rejected (not found) without any lock on
   that row.
2. **One sorted statement per sub-level.** K and F rows are locked with a single
   `… WHERE … = ANY(:ids) ORDER BY file_id FOR …`, at their final strength.
3. **Ordered inserts.** Inserts that can wait on a uniqueness check (new worksheet keys) are issued in ascending
   `file_id`.
4. **Presentation order is separate from lock order.** What the researcher sees is unchanged:
   - worksheet sequence numbers and the order of history entries follow the request;
   - grouping applies rows in plan order.
5. **Implicit KEY SHARE on F is a documented exception.** KEY SHARE taken by a foreign-key insert (`addRow`,
   `record_file`) may follow a K or R lock. It is allowed because the only rows it can wait for are held by
   F FOR UPDATE writers, and those never wait on S, K or R (§5, lemma 2).

## 4. Affected production methods and SQL

### 4.1 `CaptureSessionRepository`

- **New** `lockSessionRows(UUID sessionId, Collection<UUID> fileIds)`:

  ```sql
  SELECT <ROW_COLUMNS> FROM gas.file_capture_context
   WHERE capture_session_id = :s AND file_id = ANY(:ids)
   ORDER BY file_id FOR UPDATE
  ```

  It is announced to `LockSequence` as K, one acquisition per returned row, in that order. PostgreSQL re-checks the
  predicate after any wait (EvalPlanQual), so a row moved or deleted meanwhile is simply not returned.
- **`lockRow(fileId)`** stays for `correctOverrides`, which is a single-row, session-less writer.
- **`addRow`** is unchanged as SQL. Its callers issue it in ascending `file_id` (§4.3).
- **New** `renumberAdded(UUID sessionId, List<UUID> requestOrder, int base)`: sets `sequence_number = base + rank`
  for the rows this transaction inserted, updated in ascending target order (§4.3).

### 4.2 `CaptureSessionService`

| Method | Change |
|---|---|
| `saveMarks` | After the S lock, `lockSessionRows(session, edits' file ids)`. Any requested id missing from the result is answered "not found", as today, before any update. Edits are then applied in request order. |
| `removeFile` | Unchanged except that the row is locked through `lockSessionRows(sessionOfRow, [fileId])`. |
| `add` (`addFiles`, `addBatch`, `addByFriendlyIds`) | See §4.3. |

### 4.3 `add`: ordered inserts with the researcher's order preserved

1. Lock S.
2. Compute `eligible`: the visible, not-destroyed Files in **request order**. These are reads, not locks, exactly as
   today.
3. Insert each eligible key in **ascending `file_id`** with a *provisional* sequence:
   `base + position-in-eligible-request-order`. `ON CONFLICT DO NOTHING` marks a File as taken, as today.
4. If any File was taken, renumber the inserted rows to `base + rank`, in request order. Each target value is free
   by the time its row moves (it was a gap, or it was vacated by an earlier-ranked row), so the immediate
   `(capture_session_id, sequence_number)` uniqueness is never violated. These updates touch only rows this
   transaction inserted, so they cannot wait.
5. Write history in request order.

The result is identical to today's: consecutive sequence numbers, in request order, counting only added Files.

### 4.4 `GroupingService.applyChunk`

| Step | Today | Proposed |
|---|---|---|
| Session | `lock(S)` | unchanged |
| Rows | `lockRow` per planned row in plan order, then a membership check; the classification-source row may be re-locked after the Files | **One** `lockSessionRows(S, chunk rows ∪ classification-source row)` in `file_id` order. Then the same version, applied-state and membership checks as today, in plan order. |
| Files | `lockShared` per File in plan order, then per `continuesFrom`, then the classification-source File after the Record decision | **One** `SELECT … FROM gas.archival_file WHERE file_id = ANY(:ids) ORDER BY file_id FOR SHARE` over chunk Files ∪ `continuesFrom` ∪ the classification-source File. Then the same visibility and lifecycle checks as today. |
| Record | `lock(target)` (existing group) or insert | unchanged |

Hold and conflict outcomes are unchanged, because every check still runs after its lock. The classification-source
row and File are locked earlier than today but under identical conditions. That is the same lock set, taken in
order.

### 4.5 Phase 2 `FileService` (only if D-LP-3 option (a) is chosen)

- **Restriction:** before `updateAttributes`, lock P and its whole NORMAL-descendant closure with a recursive query,
  `… ORDER BY file_id FOR UPDATE`, in one statement. The V0014 cascade then finds every row already held.
- No other Phase 2 path changes.

### 4.6 `LockSequence`

The opaque `L1_PHASE3` is replaced by `L1_SESSION`, `L1_WORKSHEET`, `L1_FILE`, `L1_RECORD` and `L1_CATALOG`, with
the full rules (level order, UUID order within K and F, no upgrade). This closes GAS-P4-002. The documented implicit
exception (rule 5) is not announced, because it is not an explicit lock.

## 5. Wait-for argument

Assign each lock request the key (sub-level, UUID). A transaction **waits** only when it requests a lock held in a
conflicting mode by another transaction.

- **Lemma 1 (ordered requests).** Suppose every explicit request and every ordered insert of a transaction has a
  key greater than or equal to every key it already holds. Then if T waits for U, every key U holds that T wants is
  greater than or equal to every key T holds. A cycle T₁ → … → T₁ would then need a strictly increasing chain of
  maxima that returns to its start. So no cycle exists among such requests. This is the standard ordered-locking
  argument; it applies to uniqueness waits on ordered inserts because those waits are on keys in the same order.
- **Lemma 2 (sinks).** F FOR UPDATE holders (`FileService`, `OperationProcessor`) and C FOR UPDATE holders
  (`CatalogService`) never wait on S, K or R. With D-LP-3 (a), F FOR UPDATE holders also take F in ascending order.
  A transaction whose only out-of-order waits are on rows held by sinks cannot close a cycle, because a sink's own
  waits never lead back to S, K or R.
- **Coverage.** Under the proposal every L1 writer satisfies Lemma 1. The sole exception, the implicit KEY SHARE on
  F after K or R, is covered by Lemma 2.
  - Phase 4 writers satisfy Lemma 1 at L1, because the Record is their only and first L1 lock.
  - The confirmation takes no L1 lock (clarifications §5.5.3).
  - Higher levels follow the accepted L2…L5 rules.

Therefore no deadlock cycle exists among the enumerated writers, provided D-LP-3 (a) is adopted. Under D-LP-3 (b),
exactly Scenario C remains, and it is detected and aborted by PostgreSQL.

## 6. Compatibility with accepted Phase 3 behaviour

| Behaviour | Effect |
|---|---|
| Worksheet order, sequence numbers and counts (added, taken, hidden) | Identical (§4.3) |
| Grouping semantics: plan order, chunking, hold or conflict decisions, idempotency (R-004, R-005, GAS-P3-004) | Identical; only the moment and order of locks change, never the conditions checked after them |
| Cross-session request naming a foreign row | Still "not found"; now without waiting on that row |
| Error messages, history content and history order | Unchanged |
| Schema | None |
| Phase 3 tests | Expected to pass unchanged; any that fail would reveal behaviour this proposal claims to preserve, and would be reported, not rewritten |

## 7. Implications for W1–W11 and LO-1…LO-7

- **W1–W5 and W11.** These take `archival_record` FOR SHARE (`L1_RECORD`) as their first lock and no other L1
  lock. They never need S, K or F. They never depend on a session mutex: a session-scoped Phase 3 writer may hold
  S, K and F, but it waits on R only through Lemma 1, and never on L2+ resources.
- **W10 (confirmation).** No L1 lock; unchanged.
- **W12.** Becomes the explicit sub-ordered sequences of §4. Role withdraw and restore remain R FOR UPDATE only.
- **Test changes:**
  - **LO-1** gains exact expected sequences per W12 path.
  - **LO-2** applies its full checks to the L1 sub-levels.
  - **LO-3** also requires `FOR KEY SHARE` and the new `ANY(...)` lock statements to be announced.
  - **LO-4** adds the cross-session pairs of §9.
  - **LO-5** is unchanged.
  - **LO-7** documents the rule-5 implicit KEY SHARE as the only unannounced lock it accepts.

## 8. Alternatives considered and owner decisions requested

| Alternative | Why not recommended |
|---|---|
| Sort the request list only | Does not order uniqueness waits or locks taken through triggers, and does not stop locking a foreign session's rows (direction sheet) |
| A global advisory "worksheet" lock | Serialises all sessions; it is a mutex by another name, adds a new infrastructure dependency and gives up concurrency the specification does not require us to give up |
| Per-File advisory locks before inserts | Works for Scenario B only, and duplicates what ordered inserts already achieve |
| SERIALIZABLE isolation with retry | Changes failure behaviour across the accepted Phase 3 paths, and retries are not a lock-order proof |
| Rely on PostgreSQL deadlock detection | Fails visibly but still fails correct requests; it is the current state |

**Decisions requested:**
- **D-LP-1.** Approve the L1 sub-order and rules of §3 and the method changes of §4.1–§4.4. Recommended: approve.
- **D-LP-2.** Approve sorted insertion with request-order renumbering in `add` (§4.3) as the way to keep worksheet
  order. Recommended: approve.
- **D-LP-3.** Scenario C (§2.4):
  - **(a)** change Phase 2 `FileService` restriction to lock the parent and its NORMAL descendants in ascending order
    before the cascade (§4.5). **Recommended.**
  - **(b)** accept PostgreSQL detection for this narrow case and record it as a known limitation.
- **D-LP-4.** Approve replacing the opaque L1 in `LockSequence` with checked sub-levels (§4.6), closing GAS-P4-002.
  Recommended: approve.

## 9. Concurrency regression-test design (not implemented)

All tests run on Windows and Linux with PostgreSQL 16 and 18. Deadlock retry is disabled, `deadlock_timeout` is
lowered, and `lock_timeout` is set so a hang fails the test.
- **Before/after evidence.** Each scenario test is first committed against the current code, where it must fail by
  `40P01` or a lock timeout, and then against the fix.
- **Interleaving seam.** To force an interleaving, a test-only seam pauses at a named point. This follows the
  existing `GroupingFaults` and `ScriptedFaults` pattern, with a new `CaptureFaults` point after each lock or
  insert.

| ID | Design | Expected after the fix |
|---|---|---|
| LP-1 (Scenario A) | Sessions S1 {A}, S2 {B}. T1 `saveMarks(S1, [A, B])` and T2 `saveMarks(S2, [B, A])`, paused after their first row lock | Both answer "not found" for the foreign row; no wait; `pg_locks` shows no lock on the foreign row; zero `40P01` over 50 iterations |
| LP-2 (Scenario B) | Unassigned F1 and F2 (F1 < F2). T1 `addFiles(S1, [F2, F1])` and T2 `addFiles(S2, [F1, F2])`, paused after the first insert | Both insert F1 first, so whichever inserts F1 first adds both Files and the other reports both taken; never a deadlock. Sequence numbers in each session follow its request order. |
| LP-3 (order kept) | `addFiles(S, [F3, F1, F2])` where F2 is already taken | Rows F3 = n+1 and F1 = n+2; consecutive; history in request order |
| LP-4 (grouping order) | A chunk whose plan order is the reverse of `file_id` order, with continuations and a classification source in a later chunk | Same Records, links, holds and conflicts as the Phase 3 reference runs; the recorded sequence is S, K ascending, F ascending, R |
| LP-5 (Scenario C) | A chunk holding derivative D and parent P, racing the restriction of P | (a): no deadlock in 50 iterations; the outcome is that of one of the two serial orders (D ends restricted; the chunk's visibility checks are those of Phase 3). (b): the test asserts the documented detection outcome. |
| LP-6 (linkFile triangle) | Grouping holds F SHARE and waits for R, held by `linkFile`, which requests F KEY SHARE while `FileService` waits for F FOR UPDATE | `linkFile` proceeds (KEY SHARE does not queue behind the waiting FOR UPDATE); no deadlock. This test verifies a PostgreSQL behaviour Lemma 2 relies on. |
| LP-7 (guard) | Full `LockSequence` checks on the L1 sub-levels across the whole integration suite | No violation in any Phase 3 or Phase 4 test |
| LP-8 (Phase 4 independence) | A Phase 4 writer (from WP4) taking Record SHARE, racing grouping on the same Record and session | No wait cycle; the Phase 4 writer never requests S, K or F |

## 10. Existing defects versus future Phase 4 risks

| Item | Kind | Present in accepted Phase 3 or 2? | Effect if left |
|---|---|---|---|
| Scenario A | Existing Phase 3 defect | Yes | A deadlock error on an invalid cross-session request |
| Scenario B | Existing Phase 3 defect | Yes | A deadlock error when two sessions claim the same Files at once |
| Scenario C | Existing Phase 2/3 interaction | Yes | A deadlock error in a narrow derivative-and-parent case |
| A Phase 4 writer assuming a session mutex | Future Phase 4 risk | No (no Phase 4 writer exists yet) | Avoided by §7: Phase 4 takes only R at L1 |
| Opaque L1 in the guard (GAS-P4-002) | Test-coverage gap | Introduced in WP1 | Phase 3 L1 order is unchecked; closed by D-LP-4 |

## 11. Proposed wording

### 11.1 Clarifications §5.5.3: replacement of the L1 table row and rule 1

> | L1 | **Phase 3 resources, in this sub-order:** L1a capture session → L1b worksheet keys (`file_capture_context` rows and pending inserts, by `file_id`) → L1c Files (`archival_file`, by `file_id`) → L1d Archival Record → L1e Repository and Source. | As in WP1-R1 (PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL §3) | Phase 3 paths; Phase 4 writers take L1d only |
>
> 1. **Monotonic acquisition.** A transaction acquires blocking row locks in non-decreasing (level, sub-level, UUID) order, including inserts that may wait on a uniqueness check. Session-scoped writers lock worksheet rows only through a predicate on their own session. The single exception is the implicit KEY SHARE that a foreign-key insert takes on an L1c File after an L1b or L1d lock; it can wait only on L1c FOR UPDATE holders, which never wait on L1a, L1b or L1d.

### 11.2 GAS-P4-002 issue record: proposed status text

> **Status:** superseded by WP1-R1. The L1 internal order is no longer left unchecked: it is defined by the L1a…L1e sub-order of PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL §3, enforced by `LockSequence` sub-levels (D-LP-4). The original premise of this record, that Phase 3 L1 safety comes from locking the capture session first, holds only within one session; the independent audit's Scenarios A and B show cross-session cycles. It is corrected here.

## 12. Residual risks

- **Scenario C under D-LP-3 (b).** Detected and aborted; it fails visibly.
- **LP-6** relies on PostgreSQL not queuing a compatible KEY SHARE request behind a waiting FOR UPDATE request.
  This is believed correct for heap row locks, but it must be proven by test on PostgreSQL 16 and 18 before
  acceptance. If it fails, `linkFile` would lock the File FOR KEY SHARE explicitly before R. That changes R → F to
  F → R in `linkFile`, which would need a further decision.
- **Advisory and Phase 2-internal locks** (operation processing, integrity) are outside this inventory. They do not
  touch S, K or R. A future writer that combines them with L1 must be added to the inventory.
- **L7** has no explicit locks and no guard level (WP1-R3). A level must be added before any writer locks an L7 row
  explicitly.
