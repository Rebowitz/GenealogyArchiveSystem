# Phase 4 WP1 Architecture C: Draft 4 verification response (D4-V1, D4-N1, §8 criteria)

| | |
|---|---|
| Verification | [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-VERIFICATION.md) (Codex; verified `f2a06fb`; ACCEPT WITH SPECIFIED CORRECTIONS; V1 PARTIAL, V4 CLOSED) |
| Baseline | `7b289c84649135b4a9419f03e368feb242b5a235` |
| Revised contract | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), **Draft 5**. Changes are tagged **[G5-D4V1]**, **[G5-D4N1]**, **[G5-S8]** and **[G5]**. |
| Status | Documentation only. DRAFT — NOT ACCEPTED. No implementation, experiments or finding closures. V2, V3 and V4 are not reopened. No new owner decision is required. |

> **Draft 6 note (2026-11-18).** The Draft 5 verification
> ([`PHASE-4-WP1-ARCHITECTURE-C-DRAFT5-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT5-VERIFICATION.md)) found
> that the single §12.1 schedule summarised below could not be reached as written (D5-N1). It also found that T-m's
> "at most one lease period" claim overstated the timing bound (D5-N2). Draft 6 replaces §12.1 with three separate
> variants (A overlap, B refusal, C characterisation) and removes the bound. See
> [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT6-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT6-CORRECTIONS.md).

## 1. D4-V1 (MAJOR): renewal and the multi-operation cycle

The preferred bounded correction is adopted: **receiver renewal processes one operation per independent, outermost,
committed transaction**.

| Requirement (assignment §2) | Contract section | How it is met |
|---|---|---|
| One operation row per renewal transaction; independent commit or rollback; no enclosing batch; no second row while holding the first; the coordinator may iterate | §5.4.1 class table, "Receiver renewal" row; §5.1 token-free table | Per-operation `UPDATE … WHERE state = 'RECEIVING' AND operation_id = :id`, each its own outermost transaction. The accepted multi-row `ANY(:ids)` statement is replaced. |
| Keep the RECEIVING eligibility, stale and progress predicates, and independent lease renewal | Same; §5.8 receiving rows unchanged | Same WHERE predicate and lease meaning. The stale abandonment CAS is unchanged. |
| No reliance on sorted input to an unordered multi-row UPDATE | Renewal row | Stated |
| Updated class table, renewal exception, no-cycle reasoning, failure and recovery text | §5.4.1 (table; no-deadlock argument points 1–4, which explicitly exclude the Draft 4 counterexample); trace **T-m**; §11 row 21 | The surviving renewal statement holds at most one row and requests no other, so it cannot close a cycle |
| No registry, table, controller or ownership state | §15 "Draft 5 net change" | None added |

**Surviving-backend regression** (assignment §3): **§12.1**, a future deterministic test on real PostgreSQL. It
covers all nine required elements:
1. two RECEIVING REPAIRs on one content;
2. a renewal backend surviving its killed client, held at a server-side barrier;
3. both intervals reclaimed under E-1 and E-2;
4. an overlapping replacement prefix A, then B, then Ct(X);
5. barriers and blocker observation through `pg_locks` and `pg_blocking_pids`;
6. retries disabled;
7. zero 40P01;
8. refusal, timeout and success distinguished;
9. the slow-upload and failed-STAGED-CAS regressions preserved.

The Draft 4 multi-row behaviour is kept only as a labelled characterisation control. Nothing has been run.

## 2. D4-N1 (MINOR): traces and proof wording

| Requirement (assignment §4) | Contract section | How it is met |
|---|---|---|
| T-a shows O → Ct → Q | Trace T-a | Corrected |
| NOWAIT refusal attributed to conflicting locks only; KEY SHARE compatibility represented | T-6 theorem (A), step 2 | Conflicting = `FOR NO KEY UPDATE`, `FOR UPDATE`, or an UPDATE of O. FK `FOR KEY SHARE` is compatible: it neither blocks the reclaim nor confers authority. |
| A stale interval cannot obtain qualifying authority or commit an EDT; no claim that a stale backend can never physically lock O | Theorem (A), steps 3–4; §5.4.1 premise; traces T-f and T-m; Proof 2 (n) | "Cannot obtain qualifying authority". A physically held stale lock is acknowledged and harmless. |
| A multi-operation EDT may already hold an ordered lower-UUID prefix; after a failed predicate, no later business lock or mutation, then roll back | T-3 "Failed predicate"; theorem (A) (iii); §5.4.1 no-deadlock point 3 | Stated |
| Conditional theorem kept separate from no-deadlock and from backend disappearance | T-6: (A) theorem, (B) global argument in §5.4.1, (C) disappearance not claimed; residual paragraph | Three separate statements |
| Locks are not strengthened to make wording true | — | No lock strength is changed |

The verification's other precision points are also reflected:
- READ COMMITTED predicate recheck; a serialization failure is never acquired authority (theorem A, step 3);
- `lock_timeout` is per lock wait (residual paragraph);
- fresh re-reads, never cached objects, after a reclaim (theorem A (i)).

## 3. §8 completion criteria and assignment §5: O-prefix precedence and complete operation sets

| Requirement | Contract section | How it is met |
|---|---|---|
| The token-owned O prefix precedes the retained F/Ct ordering; all applicable retained ordering kept afterwards; Ct before shared content-scoped mutations | T-3 "Precedence over the retained window wording"; §4.1 | O prefix, then F → Ct (D-LP-6b), CR-1, Wh2 Ct SHARE span, E4 and G1 π, all unchanged after the prefix |
| Identify the complete operation set first, including location-keyed release, destruction and other writers, cross-operation Q settlement, and trigger or constraint row mutations | T-3 "Complete operation set first"; §5.4.2 rows (QuarantineMoves, destruction, implicit keys) | Required as an obligation for the implementation inventory |
| An operation discovered after Ct or a later lock means roll back and rediscover; never append O out of order | T-3 "Late discovery"; §11 row 22 | Stated |
| Preserve Phase 2 windows, authorization, provenance, restriction and atomicity; no claim of implementation | §5.4.2 (all rows are obligations; "Case A" for old Ct-first shapes); §4.2 unchanged | Stated |
| Correct the understated "nothing added" claim | §15 "Draft 4 net change" amendment | Corrected |

## 4. Residual architectural contradictions

None known in the operative text after this pass. Two related items remain proof or implementation dependencies, not
contradictions:
- **The no-deadlock argument (B) is conditional on the §5.4.1 class table being complete.** That is the §5.4.2
  inventory, which does not exist yet.
- **Theorem (A) excludes old Ct-first shapes (Case A).** They are excluded operationally during the transition
  (M-2 to M-5). After that they must be removed by the reordering.

## 5. Remaining implementation and feasibility obligations (unchanged in kind)

- **Feasibility:** P1, P2 and P3; Proof 1; Proof 2 (a)–(q); §12.1.
- **Inventory:** the complete EDT and O-writer inventory, with implicit FK, uniqueness and trigger effects, event
  `seq` allocation, every note call site, destruction's private DESTROY insertion and the window reorderings
  (§5.4.2).
- **Enforcement:** outermost T-1, T-4 and T-6 enforced despite the ordinary Spring `TransactionTemplate`.
- **Other:** B-9 grouping current-run revalidation; §5.8 integrity and recovery compatibility; the privilege
  inventory (OD-6); realistic inherited fixtures; the Phase 0–3 and Phase 4 regressions on Windows and Linux ×
  PostgreSQL 16 and 18.
- **Decisions:** OD-1…OD-6 and OD-8 remain at their recorded gates.

WP1 remains unaccepted. Implementation is unauthorized. WP2 remains on HOLD. The next gate is Codex verification of
Draft 5.
