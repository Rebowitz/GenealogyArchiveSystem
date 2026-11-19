# Phase 4 WP1 Architecture C: Draft 3 verification response (V1, V4)

| | |
|---|---|
| Verification | [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT3-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT3-VERIFICATION.md) (Codex; verified `a9ba91d`; ACCEPT WITH SPECIFIED CORRECTIONS; V2, V3 CLOSED; V1, V4 PARTIAL) |
| Baseline | `63c86e413de0e2dcb7b179b4274d841947345f3a` |
| Revised contract | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), **Draft 4**. Changes are tagged **[G4-V1]** and **[G4-V4]**. |
| Status | Documentation only. DRAFT — NOT ACCEPTED. No implementation, experiments or finding closures. V2 and V3 are not reopened. No new owner decision is required. |

## 1. V1: backend attribution and authority

The preferred bounded design was adopted: **label-based reclaim authority is removed**. No registry, table, state or
controller is added.

| Requirement (assignment §2 and §3) | Contract section | How it is met |
|---|---|---|
| 1. `application_name` is diagnostic, candidate and refusal information only | §5.4 "Connection labels are diagnostic only" | Stated, including `PGC_USERSET` and the client-supplied value. The instance ID in the label is optional, for diagnosis. |
| 2. A negative census authorizes nothing | §5.4; T-6 (no census in the reclaim) | The reclaim SQL no longer queries `pg_stat_activity`. A diagnostic census may only refuse. |
| 3. Automatic reclaim needs independent exclusion or fencing over all surviving EDT authority | T-3 (row lock under the interval predicate is the **first** lock of every EDT); T-6 conditions **E-1** (process gone, P1; this host; not own JVM) and **E-2** (`NOWAIT` row lock on the exact old interval, then CAS), with the five-step argument | Commit authority for every EDT is the row lock under its interval predicate. A holder makes NOWAIT refuse. After the CAS, the old predicate can never match. A surviving backend that has not taken the row holds no lock of that window, because O is first, and rolls back. PostgreSQL enforces this fence, independently of labels and of process visibility. |
| 4. Otherwise refuse, with the offline fallback | T-6 outcomes; §5.7; trace T-e | Busy, ambiguous, another host or unreadable identity ⇒ refuse. Resolution is by PostgreSQL ending the backend, or by the offline drain (M-2 to M-5). |
| 5. Keep exact recorded attempted-backend tuples | T-2; §7.4; §10.4 (a) | Each process records its own `(pid, backend_start)` from the server at connect time, independently of labels |
| 6. A label match never authorizes termination | §10.4 "Never grounds for termination"; trace T-l | A label match, contention, same-role visibility or an unlabelled backend is never grounds for termination. Such sessions are investigated, or the offline drain is used. |
| 7. Runtime never signals; administrative termination needs authority and confirmed absence | §10.1 runtime row; §10.4 (a), (b) | The runtime code-path prohibition is distinguished from PostgreSQL's default same-role capability, as the review asked. Targeted termination only for an independently recorded tuple, revalidated, then absence confirmed. Bulk termination only in M-5. |
| 8. Keep clean release and ordinary operation | T-4 ("a clean release is the ordinary handoff"); T-6 | Clean release needs no reclaim and no maintenance. Ordinary crash recovery stays automatic when E-1 and E-2 hold (trace T-f). |
| 9. Short outermost transactions, NOWAIT, lock order, full EDT validation | T-1, T-4, T-6 (outermost; `SET LOCAL lock_timeout`; `SELECT … FOR NO KEY UPDATE NOWAIT`); T-3; §5.4.1 | Retained. The token-owned-O-first placement is the retained G1/G10 pattern ("after an owned O lock, ordered acquisitions proceed under the normal π check"). Draft 3's alternative placements are withdrawn. |
| 10. Statistics visibility, snapshots, label length and UUIDs are not guarantees | §5.4 label paragraph | Stated |
| §3 terminal lock order | §5.4.1 "The terminal exception is narrow" and the class table; §4.1 | Terminal O-only writers MUST be outermost and hold no earlier ordered lock. A final statement is not enough if the caller holds Ct. The future inventory must validate every O-row writer: triggers, foreign keys, renewal, destruction's multi-operation path, each note call site and the O-first reordering. Nothing is claimed executed. |

**Required negative proof cases** (assignment §5) are added as Proof 2 (n)–(q), §11 row 20 and traces T-f, T-k and
T-l, with expected refusal and no-termination outcomes:
- a former EDT backend before its O check whose label is missing, changed or truncated;
- a live unrelated backend carrying the former label;
- an unavailable or incomplete census;
- ambiguous exclusion.

They are future proofs and have not been executed.

## 2. V4: classification scope

| Requirement | Contract section | How it is met |
|---|---|---|
| Exactly one applicable producer classification, only where the alternatives are mutually exclusive | §8.3 "How rows are classified"; new **Kind** column (P / C) | P rows: generation creator (g₀ INGEST or later REPAIR), owner type, the AU-2 operation row, the U writer role, the Q role by reason |
| All applicable consistency checks, cumulatively; aggregate checks not counted as classes | Same | C rows: superseded-state rules, generation set, forward and reverse ledger, owner-kind consistency, settled destinations, pointer, aliases and claimants. The worked example is a superseded INGEST g₀. |
| Preserve g₀ at any number, REPAIR generations, superseded INGEST generations, both owner types, legacy Q and U, forward and reverse checks, D4-32 | §8.3 rows (unchanged content), fixtures, outcome mapping; §8.1 | Unchanged from Draft 3. No historical evidence created; R39-T not relaxed. |

## 3. Answers requested

- **Is automatic reclaim still available?** **Yes**, under independently established conditions only:
  - **E-1:** the recorded former process is gone on this host (P1), the token names this host, and the interval is
    not active in the reclaimer's JVM;
  - **E-2:** within one short, outermost reclaim transaction, the subject's row is lockable with `NOWAIT` under the
    exact old interval, followed by the CAS and the event.

  Otherwise it refuses, and falls back to waiting or to the offline drain. Labels are never an input.
- **Remaining feasibility and implementation obligations:**
  - P1 (process identity), P2 (fence lifetime), P3 (NAS completion);
  - Proof 2 (j) and (n)–(q);
  - the complete EDT and O-row writer inventory, including the reordering of retained windows to O first, triggers,
    foreign keys, renewal and destruction;
  - B-9 grouping current-run revalidation;
  - the privilege inventory (OD-6);
  - realistic inherited fixtures;
  - the Phase 0–3 and Phase 4 regression matrix on Windows and Linux × PostgreSQL 16 and 18.
- **New owner decision required:** none. OD-1…OD-6 and OD-8 remain as recorded.
- **Points for attention:**
  - **O-first reordering.** Making token-owned O the **first** lock of every EDT may reorder some retained windows
    that today take Ct before O. This placement is permitted by the retained G1/G10, but it is an implementation
    footprint change the inventory and LO tests must confirm.
  - **Liveness residual.** A surviving backend of a dead process, for example after a network partition, can delay
    reclaim or new owners until PostgreSQL ends it. That is safe, but recovery can be slower without the offline
    drain.

WP1 remains unaccepted. Implementation is unauthorized. WP2 remains on HOLD. The next gate is Codex verification of
Draft 4.
