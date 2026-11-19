# WP1 Architecture C — Draft 6 Independent Verification

**Date:** 18 November 2026\
**Repository / branch:** Rebowitz/GenealogyArchiveSystem / phase-4\
**Verified baseline:** b04c6e24c41a9578fd3623c8d3624bf9b701410f\
**Verdict:** **ACCEPT WITH SPECIFIED CORRECTIONS**

## 1. Disposition and scope

| Finding | Disposition |
|---|---|
| **D5-N1 — test chronology/determinism** | **PARTIAL:** the chronology is corrected and the three schedules are separately reachable; the new literal pg_locks row-lock assertions need the small observation correction in §3. |
| **D5-N2 — timeout wording** | **CLOSED architecturally/documentationally.** |
| **D4-V1 — renewal/multi-operation cycle** | The renewal design remains architecturally satisfactory. **Unqualified closure remains PARTIAL solely for the required test specification**, not for a remaining production-protocol contradiction. |

**New findings: 0 BLOCKING, 0 MAJOR, 1 MINOR (D6-N1).** No new protocol or structural contradiction was identified in the two corrections.

**Is Draft 6 ready for an explicit owner architectural-contract acceptance decision? No, as written.** One finite test-observation amendment remains. No redesign, new owner policy, experiment or completed implementation inventory is required to make it. After that amendment, the prior conclusion stands: the architectural design is suitable for owner contract-acceptance consideration independently of implementation readiness and the unexecuted feasibility proofs.

**Governance gate: CONTINUE**, restricted to this observation amendment and then owner contract-acceptance consideration. This report does not conduct a new general architectural review.

## 2. Verified baseline and evidence

Fetched origin/phase-4 and confirmed the exact required HEAD. The local phase-4 checkout was clean before advancing to that descendant without merge/rebase, and clean at verification start. Compared with the published Draft 5 report commit 2dd6250, only the boundary contract, the historical note in the Draft 5 response and the new Draft 6 response change. Source, SQL, tests, governing decisions/instructions and earlier independent reviews are unchanged.

Read the complete Draft 6 contract, using the already-read unchanged Draft 5 text plus the full current delta; the Draft 6 response; Draft 5 independent verification and response/history; relevant prior findings; engineering instructions and D4-31/D4-32. Checked the operative text rather than accepting the builder's completion claims. Direct comparison confirms the ownership rules and §§6–10 are unchanged. Unchanged accepted source was inspected only for the renewal/stale predicate: data/FileOperationRepository.java:289–330, under src/main/java/org/genealogyarchive/.

All contract references below are at b04c6e2. PostgreSQL primary documentation was consulted for observation semantics. **No code, SQL experiment, test, feasibility proof, migration or application was executed.** Documentation closure is not implementation or platform proof.

## 3. D5-N1: chronology corrected; one observation amendment remains

| Variant / requirement | Independent verification |
|---|---|
| **A — post-reclaim overlap** (§12.1 A-1…A-10, lines 1359–1368) | Barrier 1 explicitly precedes every O-row acquisition. A-3 requires former process exit and the exact surviving backend tuple still at that barrier; an ended backend invalidates the run. A-4 commits both reclaims before barrier 1 is released. A-5 adds the separate post-B-lock/pre-transaction-end barrier. A-6 establishes the replacement's A → B wait. The corrected single-row renewal never requests A. The success and timeout cases are separate runs, with barrier 2 respectively released or retained. The former impossible combination of successful B reclaim and an uninterrupted old B lock is gone. |
| **B — busy reclaim/refusal** (B-1…B-3, lines 1374–1376) | A distinct holder retains B while T-6 attempts NOWAIT; the asserted result is 55P03, whole-attempt rollback, unchanged interval, no reclaim event and no replacement effects. A later fresh attempt is separately recorded after the holder ends. As required by the referenced T-6 rule, E-1 must already be established; a live owner is refused before SQL and would not exercise the NOWAIT branch. The future fixture must instantiate that existing prerequisite. |
| **C — historical control** (C-1…C-3, lines 1382–1384) | Reproduction requires actual B-before-A acquisition and the opposing wait graph, not input-array order. Failure is explicitly “not reproduced”; neither control outcome validates the corrected path. This is the correct limited use of the superseded implementation. Its lock-state observation needs D6-N1 just as A does. |
| **Shared requirements** (lines 1339–1353) | Real PostgreSQL 16/18 and Windows/Linux clients; named server barriers; no sleep-driven ordering; bounded observation waits; named blockers; disabled application retries and enumerated controller attempts; explicit SQLSTATEs; zero 40P01 on corrected paths; distinct success/refusal/timeout; rollback/no-partial-effects and preserved slow-upload/failed-STAGED-CAS regressions are all explicit. |

The schedules are logically reachable with correctly placed, test-only server hooks and a harness that actually preserves the old backend. That harness has not been built or demonstrated here. A-3's exact pid/backend_start, continued barrier occupancy and invalid-run rule are sufficient specification requirements to prevent counting an already-ended backend as evidence; they do not claim that client death normally preserves sessions. The future proof must report inability to establish this state rather than silently substituting a live client or a vanished backend.

A BEFORE-statement hook can establish entry before O acquisition; the single-row UPDATE's post-row hook can establish the later B-held phase. The future trigger/function implementation must verify its placement and restrict hooks to the intended test backend/statement so token reclaims are not stopped by the renewal barrier. This is test-fixture validation, not additional production architecture.

### D6-N1 — MINOR: pg_locks absence/grants are not a complete tuple-lock witness

**Directly introduced assertions:** A-2 requires no A/B row lock “in pg_locks”; A-7 treats no A grant/wait there as confirmation that R never requested A; C-2 describes B's acquired row lock as observed there. A-5/A-6 also require positive row-state observations.

PostgreSQL 16 and 18 document that row-level locks normally do not appear in pg_locks because their information is stored on disk. A row waiter commonly appears as a wait on the holder's transaction ID. Thus absence of a tuple entry cannot establish absence of an A lock, and a required granted tuple entry is not a reliable way to recognize B's actual holder. A series of snapshots also cannot by itself prove that an acquisition never occurred between samples. This is a test-oracle defect, not a new renewal deadlock. [PostgreSQL 16 pg_locks](https://www.postgresql.org/docs/16/view-pg-locks.html), [PostgreSQL 18 pg_locks](https://www.postgresql.org/docs/18/view-pg-locks.html).

**Smallest amendment to §12.1:**

- Replace the literal presence/absence-of-tuple-entry assertions with verified hook placement and a recorded per-operation acquisition/statement timeline. Barrier 1's verified pre-row position, held by exact backend R, establishes the pre-acquisition phase; both successful T-6 reclaims independently corroborate absence of a conflicting old holder at that point.
- Establish B-held from the verified post-lock hook naming B plus the replacement's known B-only locking statement blocked by R. Establish replacement-A-held from its successful A acquisition in the same still-open transaction before that B request. Use pg_blocking_pids and the transaction-ID/advisory waits visible in pg_locks as corroboration, not an exhaustive tuple-lock registry.
- Establish that corrected R never requests A from the captured single-operation-B statement and audited hook path, with operation-ID acquisition instrumentation where needed. Do not infer “never” solely from absent pg_locks entries.
- Apply the same evidence rule to C: per-row acquisition instrumentation and actual opposing named-backend waits establish B-before-A and the graph. If that evidence cannot be obtained, retain “not reproduced”.

No new production table, lock registry, dependency or protocol is requested. No installed-platform test must pass before this wording can be corrected. The actual harness and proof remain future, separately authorized work. The assertion that rollback leaves no partial effect concerns the rolled-back transaction's effects; it must not accidentally reject an independently committed renewal allowed by A-8/A-10.

## 4. D5-N2 and bounded regression check

**D5-N2 CLOSED:** T-m line 611 now permits one receiver_alive_at update at the time its clock_timestamp expression is evaluated; eligibility remains governed by the stored progress/renewal timestamps and configured receiving timeout. It expressly disclaims a fixed extension from process death or prior expected expiry and confers neither token ownership nor EDT authority. This matches FileOperationRepository:294–295 and :308–321. No new timer, lease, expiry or takeover semantics are introduced.

The full delta confirms that single-operation outermost renewal, E-1/E-2, the ordered complete O prefix and rollback/rediscovery rule are unchanged. D4-N1, V2, V3 and V4 are not reopened. Migration/receipts, bootstrap, offline recovery, historical admission, evidence/provenance and restriction/non-disclosure sections remain unchanged. D4-31/D4-32 and their acceptance/authorization limits remain governing.

The Draft 5 response now has an accurate supersession note identifying both the unreachable combined schedule and the removed lease-period bound; prior independent finding text remains intact. The Draft 6 response accurately records its intended changes, subject to the pg_locks qualification above. It does not supply executed evidence.

P1–P3, full EDT/O-writer/implicit-lock inventories, hook/harness validation, renewal performance, B-9, privilege/deployment work, inherited fixtures and platform/regression proofs remain expressly unproved. The correction does not close any of those obligations or WP1-R1, F5-4 or GAS-P4-002…005.

## 5. Convergence and completion boundary

The two finite corrections preserve the approved safety requirements without new persistent machinery. The chronology now converges; the remaining issue is how the future harness proves its lock state. Correcting that oracle is materially simpler than adding a registry or redesigning ownership. No directly introduced structural nonconvergence warrants RECONSIDER or reopening settled design questions. Existing complexity/trend assessments remain applicable.

The only remaining finite documentation correction is D6-N1's observation amendment. After it, D5-N1 and the outstanding test-specification portion of D4-V1 can close architecturally; execution remains a later proof. The owner may then consider contract acceptance separately from implementation authorization, open OD choices and feasibility results. This verification makes no owner decision.

Only this new report is created and committed locally. The contract, responses, decisions, source, tests and previous reviews remain unchanged. No push, merge, rebase, tag, implementation, test or feasibility execution occurs.

**WP1 remains DRAFT — NOT ACCEPTED. Implementation remains unauthorized. WP2 remains HOLD.**
