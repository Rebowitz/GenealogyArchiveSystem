# WP1 Architecture C — Draft 7 Independent Verification

**Date:** 18 November 2026\
**Repository / branch:** Rebowitz/GenealogyArchiveSystem / phase-4\
**Verified baseline:** 5cfb0b4df6cf244950d6dca52715ffd5c30504b5\
**Verdict:** **ACCEPT CORRECTIONS**

## 1. Decision

**D6-N1: CLOSED architecturally/documentationally.** The remaining test-specification qualification of **D4-V1 is now architecturally CLOSED**; the associated D5-N1 documentation qualification is resolved. No implementation finding is closed.

**Architecture C Draft 7 is ready for an explicit owner architectural-contract acceptance decision, independently of implementation readiness and feasibility proofs.** No further finite correction is identified within this narrowly authorized scope. **New findings: 0 BLOCKING, 0 MAJOR, 0 MINOR.**

**Governance gate: CONTINUE to the owner contract-acceptance decision**, followed only by separately authorized work. This report makes no acceptance decision and grants no implementation or proof authorization.

## 2. Baseline and review boundary

Fetched origin/phase-4 and verified the exact required HEAD. The local phase-4 working tree was clean before advancing to that descendant without merge/rebase, and clean at review start. Relative to the published Draft 6 review commit 68c91b3, only the boundary contract, the Draft 6 response's supersession note and the new Draft 7 response change. Accepted source, SQL, tests, decisions, governance and previous independent reviews are unchanged.

Read the complete Draft 7 contract through the previously read unchanged Draft 6 text and the full current delta; the Draft 7 response; Draft 6 verification; relevant prior correction history; governing engineering instructions and D4-31/D4-32. Checked operative requirements independently of builder claims. Source was not re-audited beyond the authorized observation issue. PostgreSQL 16/18 documentation supplies the necessary platform evidence about pg_locks visibility. No test, SQL experiment, application, migration or feasibility proof was executed.

All contract line references below are at the verified baseline. Findings are limited to D6-N1 and directly introduced regressions.

## 3. D6-N1 closure evidence

The new **Observation evidence rule**, §12.1 lines 1346–1360, directly resolves the previous defect:

- It expressly rejects pg_locks as an exhaustive tuple-lock registry, both presence/absence assertions and “never acquired” inferences from finite snapshots.
- Positive evidence is verified hook placement on exact pid/backend_start, captured statements/parameters, per-operation acquisition timelines and known single-row outcomes.
- pg_blocking_pids and transaction-ID/advisory waits are corroboration, not substitutes for row-specific evidence.
- Hooks are restricted to their intended backend and statement; renewal barriers must not suspend reclaim or replacement statements. The harness must validate their placement.

These requirements match PostgreSQL's documented limitation: held row locks normally are not represented in pg_locks; row contention commonly appears as a transaction-ID wait. [PostgreSQL 16 pg_locks](https://www.postgresql.org/docs/16/view-pg-locks.html), [PostgreSQL 18 pg_locks](https://www.postgresql.org/docs/18/view-pg-locks.html).

| Variant | Independent verification |
|---|---|
| **A — post-reclaim overlap** (A-2…A-10, lines 1376–1384) | Barrier 1's verified pre-row placement establishes the pre-acquisition phase. Exact backend survival at that barrier after client-process exit is required; a terminated backend invalidates the run. Both reclaims commit before release of barrier 1. Barrier 2 records B at a verified post-lock/pre-end point. Replacement-A-held follows the successful A-only locking SELECT in its still-open transaction; the subsequent known B-only request is blocked by R. R's captured B-only statement, audited hook path and acquisition instrumentation support the no-A claim. None depends on absent tuple entries. Release versus retention of barrier 2 gives separate reachable success and 55P03 timeout schedules. |
| **B — busy reclaim/refusal** (B-1…B-3, lines 1390–1392) | E-1/process exit is now explicit before the NOWAIT attempt. The named holder's post-B-lock hook and operation ID establish the conflicting holder, corroborated by 55P03. Whole-attempt rollback, unchanged interval, no event and no replacement effect remain required. A later fresh attempt is separately recorded after the holder ends; it is not combined with A. |
| **C — historical control** (C-1…C-3, lines 1398–1400) | Deterministic per-operation instrumentation must establish B-before-A. Known acquisitions and opposing named-backend waits establish the intended graph, with blocker/transaction-ID evidence or the resulting 40P01 as corroboration. Uncontrolled ANY-array scan order cannot prove it. “Not reproduced” remains mandatory when the graph is not established, and neither control outcome validates the corrected path. |

The schedules are coherent specifications, conditional on building and validating the test-only hooks and client-death harness. The contract explicitly requires actual backend survival and verified hook locations; it does not claim those have been demonstrated. The harness must retain the recorded statement/acquisition evidence when a backend or transaction ends. A missing observation cannot count as a passing proof. Those are execution obligations already required by §12.1, not remaining architectural corrections.

## 4. Rollback, preservation and direct regression check

The common rollback rule, lines 1364–1367, now concerns only effects of the rolled-back transaction and expressly allows an independently committed renewal. A-9 applies this distinction specifically to the replacement's timeout rollback. This resolves the previous risk of a false test failure without relaxing atomicity.

The common rules retain real PostgreSQL 16/18 and Windows/Linux coverage; named state barriers rather than sleep-driven ordering; disabled application retries and enumerated controller attempts; explicit SQLSTATE capture; zero 40P01 on corrected paths; distinct success/refusal/timeout results; and slow-upload/failed-STAGED-CAS regressions. Variant C's historical characterisation remains separate from corrected-path acceptance.

The complete delta and unchanged-section comparison confirm no operative change to single-operation outermost renewal, E-1/E-2, the complete UUID-ordered O prefix, rollback/rediscovery, retained File/content coordination or D4-32 global-first historical refusal. D4-N1, D5-N2, V2, V3 and V4 remain architecturally closed. The identity, evidence/provenance, restriction/non-disclosure, migration/receipt and offline-recovery protections are unchanged. No unrelated settled design question is reopened.

The Draft 6 response's new note accurately supersedes the pg_locks tuple-observation wording, identifies the replacement evidence and preserves the earlier response as history. Independent review text is unchanged. The Draft 7 response maps to the operative correction; its claim of readiness is supported at the documentation gate only.

## 5. Convergence and remaining gates

This bounded correction satisfies the observation requirement without adding a production table, state, registry, controller or ownership mechanism. Verified hooks and statement outcomes are a simpler and appropriate test oracle. The correction preserves approved requirements, adds no disproportionate recovery coupling and completes the finite documentation qualification. No directly introduced structural contradiction or nonconvergence trigger warrants another architectural revision cycle.

Owner contract acceptance can now be considered. Implementation readiness and safety execution evidence remain separate: P1–P3, Proofs 1/2 and §12.1 execution; full EDT/O-writer/implicit-lock inventories and window reordering; outermost-transaction enforcement; renewal performance; B-9; integrity/recovery compatibility; privilege/deployment work; realistic inherited fixtures; open OD-1…6/8 choices; and the accepted platform/regression matrix. WP1-R1, F5-4 and GAS-P4-002…005 remain OPEN.

Only this new independent report is created and committed locally. The contract, correction responses, decisions, source, tests and prior reviews are unchanged. No push, merge, rebase, tag, implementation, test or feasibility execution occurs.

**WP1 remains DRAFT — NOT ACCEPTED. Implementation remains unauthorized. WP2 remains HOLD.**
