# Phase 4 WP1 Architecture C: Gate 2 correction response (C1–C8)

| | |
|---|---|
| Review | [`PHASE-4-WP1-ARCHITECTURE-C-INDEPENDENT-REVIEW.md`](PHASE-4-WP1-ARCHITECTURE-C-INDEPENDENT-REVIEW.md) (Codex, Gate 2, reviewed `55041be`; ACCEPT WITH SPECIFIED CORRECTIONS) |
| Baseline | `bac22aecdaa815f37b73c8f67cc9626750157539` |
| Revised contract | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), Draft 2. Revised sections are tagged **[G2-C1]**…**[G2-C8]**. |
| Owner decision recorded | **D4-32** (OD-7: global-first historical admission refusal), in [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md) |
| Status | Documentation only. The contract remains DRAFT — NOT ACCEPTED. No implementation, no proofs and no finding closed. Verification of these responses belongs to the next independent Codex gate. |

The review's finding text is not altered. This document records responses only.

## 1. Correction map

| Item | Review finding | Contract sections changed | Response |
|---|---|---|---|
| **C1** | **B1** (BLOCKING): token durability and EDT fencing | §5.1 (EDT, effect interval, logical cancellation); §5.4 rules **T-1 to T-7** and traces **T-a to T-h**; §5.6; §11 rows 2, 3, 10; Proof 2 (d) | Acquisition and reclaim are each committed in their own short transaction before any effect (T-1); an unknown acquisition outcome issues no effect until resolved (T-2). The interval covers all threads, pending asynchronous work and every EDT outcome. Every EDT checks the interval in the same transaction at the retained O position, or as owned O after Ct (G10), and a mismatch rolls back fully (T-3). Release only after quiescence, the storage outcome and known EDT outcomes; an unknown release permits no further effects (T-4). Reclaim is a CAS on the exact old interval with NOWAIT and an event. The proof that an old backend either committed first, is refused as still open (55P03, then terminate and confirm), or is rolled back as stale needs no connection registry (T-6). Logical cancellation never touches the token (T-7). Lock order preserved (§4.1, T-3 placement). |
| **C2** | **B2** (BLOCKING): fresh-install bootstrap circular | §6.4 M-1 classification; §6.6; §7.5 (B-1 to B-6, crash table, legacy-initialized transition); §9.2 SEED and ARCHIVE_INITIALIZED; §10.4; §11 row 15; Proof 1 (h) | M-1 classifies pristine, initialized, legacy-initialized or mismatch. Before `installation_state` exists, M-2 to M-5 are the fence. The control migration creates OFFLINE plus a SEED receipt in one transaction. Bootstrap initialization reuses the accepted `initialize` steps (table lock, refuse an existing row, mint once) plus an ARCHIVE_INITIALIZED receipt in the same transaction. It is permitted only in the pristine state of the current attempt. The configuration association is a separate, non-atomic B-5 with exact recovery. After an unknown COMMIT the tool confirms the backend is gone and reads; it never mints a second UUID. READY only after normal validation. Initialized archives keep strict equality; there is no general identity exemption. |
| **C3** | **M1** (MAJOR): receiving timeout and staging cleanup | §5.1 logical cancellation; T-7; trace T-h; §5.8 inventory rows (receiving, timeout, `cleanStaging`, init-volume, staging directory, probe, integrity re-verification, cache); §11 row 16; §13 OD-9; Proof 2 (e) | The accepted lease/progress abandonment CAS and event are kept unchanged as logical cancellation. Physical cleanup (`cleanStaging`) is mapped to the existing `file_operation` and requires T-1 or T-6, with "renewal/progress absent; cleanup pending" reported otherwise. Live receiver renewal and failed-STAGED-CAS protections are preserved. Draft 1's changed "receiving, stalled" behaviour is **withdrawn**. The effects with no operation row are treated individually, as the review's §8 table recommends. |
| **C4** | **M2** (MAJOR): historical equivalence, authority and precedence; OD-7 | §8.1 (D4-32), §8.2 (coordination domain versus exact physical object), §8.3 (attested-object Q, destruction-side U, superseded generation added), §8.4 (role-scoped checks; check 6 movement authority from current authorization including durable INTENDED Q), §8.5 (classification order: witness, conflicts, then disposition; inert never waives) | Repeated repairs at different generations pass without merging objects. Bound Q/U, destruction-side and attestation relations are preserved. Global-first refusal of READY, with scoped admission deferred and the `reconcileAll` reason stated. |
| **C5** | **M3** (MAJOR): receipt identity and atomic coupling | §9.1 receipt row (change_history claim corrected); §9.2 receipt data contract (receipt ID, attempt correlation with no foreign key to the current attempt, kinds, exact old/new fields per kind, uniqueness, one atomic CAS plus insert plus change, rollback, append-only, admin-only, lookup after an unknown COMMIT); §7.4; §7.6 | Success is never identified from the new binding alone. The full report is kept as a file behind its digest, with no secrets in history. No generic journal is introduced. OD-5 keeps only the representation choice open. |
| **C6** | **N1/N2** (MINOR): invariant scope and crash-state wording | §2.1 A-1; §3 INV-3, INV-6, INV-7; §6.6 actual-state recovery table; §12 failure-criteria rule | Immutable fields and append-only history are distinguished from authorized lifecycle updates. Admitted effect-capable producers are distinguished from the maintenance tool's own authority. Unexplained objects are reported, never adopted. Recovery covers pre-table, partial M-2 and M-3, OFFLINE (attempt reused), committed READY with partial X-3 (no replay), completed reopen (new attempt), and bootstrap (§7.5). There are no new controller states. |
| **C7** | **P1/P2/P3** (FEASIBILITY) | §5.3 P1 hypothesis; §5.5 per-effect storage outcome (Windows `flushDirectory` no-op stated; `force` limits for non-local storage stated; no durability subsystem claimed), P3 hypothesis including namespace effects; §6.3 P2 hypothesis conditions; §12 Proof 1 (h)–(m) and Proof 2 (d)–(i) | All three remain explicit, unproved hypotheses, each with a fail-closed outcome. The review's bounded proof variants are incorporated, not executed. |
| **C8** | Decisions and traceability | §4.3 B-9 (source lacks the current-run check, verified at `bac22ae`); §4.1 and §13 OD-1/OD-2 kept distinct; §10.1 terminology and capabilities; §13 OD-3 and OD-6 reclassified as not contract-acceptance blockers, OD-9 withdrawn as unnecessary, OD-7 approved (D4-32); §14 traceability | All 19 / 22 ordinary windows, the open findings and the implementation and proof obligations are carried forward. |

## 2. Remaining matters

**Owner decisions still open** (none resolved by this correction):
- OD-1 (D-LP-9 (a));
- OD-2 (D-LP-3);
- OD-3 (fence library);
- OD-4 (mismatch lifecycle);
- OD-5 (receipt representation);
- OD-6 (admin principal and grants);
- OD-8 (host-ID source).

OD-3 and OD-6 are reclassified as **not** contract-acceptance blockers: their safety requirements are fixed in the
contract, and the choices are due before proof execution and implementation.

**Unproved:** P1, P2 and P3; the §5.8 and EDT coverage inventories; the B-9 late-runner test; the privilege inventory;
the inherited fixtures; the full regression matrix.

**Matters for owner attention:**
1. **Wider token scope.** T-3 extends the token check to every EDT (Q/U settlement, claim release, generation,
   findings in state-changing windows, cleanup flags). This touches more transaction windows than Draft 1 described,
   all within the retained lock order. The implementation footprint is larger than a single state-transition guard,
   though the architecture is unchanged.
2. **Bootstrap reuses accepted code with an addition.** B-4 runs the accepted `ArchiveService.initialize` steps and
   adds a receipt insert to the same transaction. That is a future implementation change to accepted
   initialization, kept within its existing transaction and lock.
3. **Runtime signalling privilege.** T-2 and T-4 rely on the runtime role signalling backends of its **own** role.
   That right is part of the OD-6 privilege inventory.

**Not changed:**
- Java code, migrations, tests, deployment files and CI;
- accepted Phase 0–3 specifications;
- revision 3.9;
- the simplification assessment;
- the review.

WP1 remains unaccepted. WP1 implementation is unauthorized. WP2 remains on HOLD.
