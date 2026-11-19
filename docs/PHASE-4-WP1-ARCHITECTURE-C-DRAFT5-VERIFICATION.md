# WP1 Architecture C — Draft 5 Independent Verification

**Date:** 18 November 2026\
**Repository / branch:** Rebowitz/GenealogyArchiveSystem / phase-4\
**Verified baseline:** 519ce689fbbb3d830628dc0ed4f3b6c92b6c6072\
**Verdict:** **ACCEPT WITH SPECIFIED CORRECTIONS**

## 1. Decision

**D4-V1: PARTIAL — the architectural renewal correction is satisfactory; the specified deterministic test still needs a finite correction. D4-N1: CLOSED architecturally.** V2, V3 and V4 remain architecturally CLOSED.

**Is Draft 5 ready for an owner architectural-contract acceptance decision? No, as written.** Correct the test schedule and the small timeout-bound claim in §4 first. No new architectural mechanism, feasibility execution or completed implementation inventory is needed to make those two documentation corrections. The O-prefix/renewal design itself is sufficiently coherent for owner contract-acceptance consideration once those corrections are recorded. This is a narrow specification qualification, not a finding that Architecture C needs redesign.

**New findings: 0 BLOCKING, 0 MAJOR, 2 MINOR.** No new structural lock-order contradiction was identified. **Governance gate: CONTINUE**, limited to the finite corrections below, then owner contract-acceptance consideration and separately authorized proofs. Implementation readiness is not established.

## 2. Baseline and method

Fetched origin/phase-4 and verified exact HEAD 519ce689fbbb3d830628dc0ed4f3b6c92b6c6072. The phase-4 tree was clean before advancing to that descendant without merge/rebase, and clean at review start. Relative to 7b289c8, the baseline changes only the boundary contract, the Draft 4 correction-response history note and the new Draft 5 correction response. Java, SQL, tests, decisions, governance and previous independent reports are unchanged.

Read the complete Draft 5 contract and response; the complete Draft 4 independent verification; relevant original Gate 2, correction-verification and Draft 3 reviews/responses; D4-31/D4-32; and AGENTS.md, CLAUDE.md and ENGINEERING-DESIGN-INSTRUCTIONS.md. Earlier source inspection remains applicable to the unchanged accepted code; renewal, receiver timing, transaction boundaries, publication/settlement and other-writer paths were checked directly again. Builder assertions were compared with operative rules and source, not counted as independent proof.

Contract line references below are at the verified baseline. Java references are under src/main/java/org/genealogyarchive/, SQL under src/main/resources/db/migration/. PostgreSQL documentation was consulted; **no application, SQL experiment, test, migration, feasibility proof or CI run was executed**. CLOSED means documentation/architecture closure only; implementation findings remain open.

## 3. Verification of D4-V1 and D4-N1

| Finding | Result and precise evidence |
|---|---|
| **D4-V1 renewal rule** | **Architecturally satisfactory.** §5.1 line 246 and §5.4.1 line 535 require one operation per outermost independently committed/rolled-back transaction, prohibit an enclosing batch and prohibit taking a second O while holding the first. The coordinator may iterate. The accepted ANY(:ids) UPDATE is explicitly replaced; input sorting is not offered as a substitute. |
| **D4-V1 retained receiver semantics** | §5.4.1 retains WHERE state = RECEIVING and only updates receiver_alive_at. §5.8 lines 673–675 keeps independent renewal, stale/progress abandonment CAS and failed-STAGED-CAS cleanup protections. Source FileOperationRepository:289–296 still implements the old batch; :307–330 uses greatest(last_progress_at, coalesce(receiver_alive_at, last_progress_at)) and rechecks the stale/state predicate inside its per-row CAS/event transaction. IngestionService:59–80 renews independently of byte arrivals; :285–293 refuses late staging after abandonment. These remain implementation obligations, not pass results. |
| **D4-V1 cycle exclusion** | §5.4.1 lines 537–552 and T-m remove the B-held/A-requested edge of the old renewal transaction. A surviving corrected statement may hold B, but cannot request A before ending its transaction. A replacement prefix A → B can wait for that terminal holder without closing the Draft 4 cycle. No prompt backend disappearance is needed. RepairService:37–55 creates RECEIVING repairs on persisted X through the shared receiver; DestructionProcessor:111–130 includes non-final writers, confirming the original overlap is real and has not merely been excluded by assertion. |
| **D4-V1 test design** | §12.1 includes the right actors, real PostgreSQL, server barriers, named blocker observations, disabled retries, explicit 40P01/55P03 outcomes and slow-upload/late-STAGED regressions. Its lock/reclaim chronology is inconsistent: **D5-N1** below. Thus the overall D4-V1 disposition remains **PARTIAL for test specification only**, not for the single-row renewal architecture. |
| **D4-N1 trace/order** | **CLOSED.** T-a line 598 now says O → Ct → Q. T-3 lines 394–405 explicitly allows an already-held lower-UUID prefix, then requires no later business acquisition or mutation and rollback on mismatch. |
| **D4-N1 PostgreSQL semantics** | Theorem A lines 461–494 distinguishes a qualifying interval predicate from merely physically locking a row; states READ COMMITTED rechecking; treats stronger-isolation serialization failure as rollback; recognizes compatible FK KEY SHARE; and scopes NOWAIT refusal to conflicting locks. The declared supported O classes use UPDATE/NO KEY UPDATE/FOR UPDATE, not KEY SHARE as commit authority. The full PostgreSQL conflict matrix still applies to any newly inventoried lock mode. |
| **D4-N1 proof scope** | Lines 458–508 explicitly separate (A) conditional subject-specific stale-commit fencing, (B) the global argument conditional on the inventory, and (C) backend disappearance, which is not claimed. Stale prefix/compatible/stale physical locks can remain until session end. lock_timeout bounds individual lock waits, not the whole transaction. |

The subject theorem is sound under its stated transaction premises: an old EDT holding qualifying O authority blocks reclaim; if reclaim wins, the old predicate cannot later confer authority to commit dependent mutations. Reclaim need not prove all old sessions ended. That does not prove all current accepted windows already conform, global deadlock freedom of unimplemented code, process identity or NAS completion. PostgreSQL documents the relevant compatibility and predicate behavior. [Row locking](https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS), [READ COMMITTED](https://www.postgresql.org/docs/16/transaction-iso.html#XACT-READ-COMMITTED), [SELECT locking clauses](https://www.postgresql.org/docs/16/sql-select.html#SQL-FOR-UPDATE-SHARE).

## 4. New MINOR findings and smallest corrections

### D5-N1 — MINOR: §12.1 cannot establish its stated overlap as a single schedule

**Evidence:** contract lines 1337–1339; T-6 lines 424–443; source FileOperationRepository:293–296 (renewal UPDATE).

Step 3 reclaims both A and B, but allows B's reclaim to fail with 55P03 while the renewal holds B and then succeed after the barrier releases. Step 4 subsequently requires the old renewal still to hold B while the replacement starts its fully owned prefix. A successful B reclaim cannot coexist with that same uninterrupted conflicting B lock. If the old renewal ends to permit reclaim, its lock is gone. If the initial barrier is before the row lock, the required second stop after B is acquired has not been specified. Merely having begun the statement does not distinguish these states. The BEFORE/AFTER location of a statement-trigger seam matters. A timed pg_sleep is not a deterministic state barrier by itself. [PostgreSQL trigger timing](https://www.postgresql.org/docs/16/sql-createtrigger.html).

**Replace the combined chronology with separate controlled variants:**

1. **Actual post-reclaim overlap:** hold the old renewal backend at a named server-side barrier **before any O row is acquired**. Establish the former process's exit while confirming this exact backend remains alive. Reclaim A and B and observe both commits. Release the first barrier; stop the same surviving statement at a second barrier **after B is locked and before its transaction ends**. Start the replacement prefix; observe replacement holding A and blocked by the renewal on B. The renewal never requests A. Release the second barrier for the success variant; hold it through the replacement's bounded lock wait for the timeout variant. A client-death harness must actually preserve the server statement; a backend that has already ended does not exercise this case.
2. **Busy reclaim/refusal:** in a separate variant, hold renewal's conflicting B lock before T-6. Assert 55P03, full reclaim rollback, unchanged old interval and no replacement effect. A separately recorded fresh attempt after the holder ends is allowed, but it is not the post-reclaim-overlap variant. Keep application retries disabled; controller-directed attempts must be explicitly enumerated.
3. **Characterisation control:** the old multi-row variant must establish B-before-A and the intended wait graph if described as reproducing the old counterexample. An uncontrolled ANY scan with an outcome merely logged is not deterministic evidence of that graph. If it does not reproduce the graph, label it not reproduced; do not count that as validation of the corrected test.

Keep named-backend blocker assertions (pg_locks/pg_blocking_pids), recorded SQLSTATEs, zero 40P01 on corrected paths, distinct refusal/timeout/success, rollback/no partial effects, and the unchanged slow-upload/failed-STAGED-CAS regressions. Barriers must establish lock state; waits only bound observations. This changes a future test specification, not the production protocol. No experiment is requested or executed here.

### D5-N2 — MINOR: T-m overstates the elapsed-time bound of a late renewal

**Evidence:** T-m line 610 says a stale renewal delays the accepted timeout by at most one lease period. Source IngestionService:59–61 defines a renewal cadence separately from files.receivingTimeout; FileOperationRepository:294 sets receiver_alive_at using clock_timestamp(), while :308–310 decides staleness from stored progress/renewal timestamps.

A surviving statement can be delayed before evaluating that timestamp. There is no supplied bound on that delay from process death or the previously expected expiry. One late timestamp update is not a bound of one renewal cadence, or a fixed elapsed interval from the writer's death. PostgreSQL's clock_timestamp is the actual evaluation time. [Current-time semantics](https://www.postgresql.org/docs/16/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT).

**Correction:** retain only the defensible claim: the stale single-row statement may update receiver_alive_at once; the unchanged stale predicate then decides eligibility from the stored timestamps. Do not promise a fixed timeout extension from death or prior expiry. This is liveness wording, not a takeover authority gap: renewal still changes no token or EDT authority, and no new expiry mechanism or owner policy is needed.

## 5. O prefix and retained windows

T-3 lines 360–410 and §5.4.2 lines 554–586 now expressly establish precedence: discover all affected operations before locking, secure their intervals outside the business transaction, acquire the UUID-ordered O prefix, then preserve applicable F/Ct ordering, CR-1 shared-mutation coordination, Wh2 SHARE lifetime, private publication and G1's ordered projection. **A late-discovered operation requires full rollback and rediscovery, never an appended O lock after Ct.** A failed prefix predicate likewise acquires no later business locks or mutations. This is a consistent architectural route; no mandatory retained rule was found to require a conflicting shared mutation before the O prefix.

| Source path / mapping checked | Independent assessment and remaining implementation obligation |
|---|---|
| **U release/attest/absence:** UnresolvedObjects:80–102, 149–168, 327–335 | Existing U-first mutations/late notes must become O → Ct → U/G with authority re-read. The already-attested/closed fast path stays read-only/terminal. A note inside this window is not an outermost terminal exception. |
| **New content / repair:** OperationProcessor:573–624, 627–687 | O-first shape is compatible. New-content Ct/G are private under E4, so their insertion before parent F is not a shared Ct/F inversion. Preserve own-key transfer, parent restriction inheritance, actual planned generation, deferred new-File FK and history. Repair keeps Ct serialization/observation/high-water rules. |
| **Shared publication / planning / createOwned:** OperationProcessor:471–492; retained Wh1/Wh2 | Prepending O does not replace F-before-shared-Ct, fresh reservation/OPEN reads or Ct SHARE through exclusive create/claim. Current Ct-first source is expressly outside the theorem until reordered. |
| **Q / cross-operation settlement:** QuarantineMoves:88–95, 238–275; OperationProcessor:730–735 | The Q operation's interval is required, including when another repair invokes settlement. Include every qualifying claimant in the prefix, including final stale claims; revalidate under Ct and rediscover after rollback if the set differs. A caller's own token is insufficient for another Q operation. |
| **Destruction:** DestructionProcessor:111–130, 443–485; FileService.destroyHolding:270–335 | Existing other-writer/advisory selection is not interval authority. All participating existing O rows precede F/Ct. The new DESTROY is private and gets its token in the inserting transaction. Preserve new-writer revalidation, evidence, tombstone/version/history atomicity. Do not reuse the old final-claimant-terminal premise without checking new token-owned paths. |
| **Administration:** OperationAdminService:100–143, 161–293 | Request-only notes remain distinct from final cancellation and non-request retry. Every finalization observation, U/claim branch, move and cleanup stays inside its interval; O prefix before each EDT. Existing retry/source behavior is not already proof of compliance. |
| **Notes/events/implicit waits:** FileOperationRepository:219–269, 391–399; V0009…V0020 | O must serialize MAX(seq)+1 before event allocation, atomically with its transition. Final lock strength, FK KEY SHARE, location-owner uniqueness, OPEN-U uniqueness and trigger effects remain real inventory obligations. V0015 foreign-O retirement updates rely on F1/V0017 permanent generation ownership, not Java's guard. Private-path/Ct proofs remain necessary. |
| **No-operation integrity/recovery:** IntegrityService:434–479; RecoveryService:51–55 | No fictitious O is introduced. Ct/F5-3 and explicit filesystem compatibility/exclusion with repair/destruction remain unproved §5.8 obligations. The contract forbids uncovered effects; it does not claim the inventory has been completed. |

The accepted Transactions helper is a normal Spring TransactionTemplate. Draft 5 now explicitly requires enforcing outermost ownership transactions despite that default. The renewal rule independently prohibits batch enclosure. The implementation must verify these boundaries at call sites, not simply wrap a loop in a transaction or trust a method name. The table is a design mapping, not a completed source-conformance proof. Existing accepted SQL and code remain unchanged.

## 6. Prior corrections, safeguards and convergence

| Check | Result |
|---|---|
| **C1 token durability/coverage** | Committed acquire before effects, exact interval CAS, unknown outcome resolution, quiescent release, subject fence and offline fallback retained. Renewal mechanism corrected; test specification needs D5-N1. Full EDT inventory unproved. |
| **C2 bootstrap** | No regression: pristine OFFLINE/SEED, one permanent Archive ID with atomic initialization/history/receipt, separate configuration association, backend-absence and exact unknown-COMMIT recovery. |
| **C3 receiving/cancellation / V3** | Architecturally CLOSED: independent leases and logical stale abandonment retained; physical cleanup and all cancellation finalization branches require intervals. No timeout-based token theft or changed OD-9 UX. |
| **C4 historical admission / V4** | Architecturally CLOSED: producer alternatives versus cumulative checks; initial/later/superseded generations and both owner types; Q/U roles and current authority; witness → conflict → disposition. R39-T bounded, D4-32 global-first refusal intact; scoped admission remains deferred. |
| **C5 receipts / V2** | Architecturally CLOSED: native Flyway history/checksums/schema remain authoritative; MIGRATED is a later informational/reconciled summary; SEED, initialization and singleton transitions have their own atomic targets. No migration replay on a missing summary. |
| **C6 invariant/crash scope** | No regression in immutable evidence versus lifecycle updates, admitted producers versus administrative tool, unexplained objects, actual OFFLINE/READY/reopening state recovery. |
| **C7 platform/proof limits** | P1 process identity, P2 lifetime fence and P3 NAS data/namespace completion remain explicitly UNPROVED/FEASIBILITY-DEPENDENT and fail closed. No platform pass evidence is claimed. |
| **C8 decisions/traceability** | D4-31/D4-32 preserved; D-LP-3 and D-LP-9(a) remain distinct; OD-1…6/8 retain their recorded gates. B-9 current-run correction, privilege inventory, realistic inherited fixtures and all accepted regressions remain open obligations. |

Phase 0–3 identity, evidence/provenance/history, immutable originals, restriction/non-disclosure, optimistic concurrency, grouping and receiving protections remain required. All 19 P2-8 / 22 Phase 2 windows and F5-1…F5-3 remain carried forward. Neither this review nor the builder's documentation establishes their implementation acceptance.

**Necessity and simplicity:** the token addresses lost-advisory/live-effect ownership; Ct/claim coordination addresses cross-operation physical accountability; the offline fence/freeze/drain addresses installation/legacy authority. Those layers serve different demonstrated failure domains. Single-row renewal is materially simpler than restoring a registry or adding another ownership mechanism. The new corrections add no table, durable state, controller, epoch or generic journal. The increased transaction count is a bounded implementation/performance obligation; it must preserve renewal timeliness under the supported workload without batching locks again.

**Trend:** the Draft 4 two-review complexity assessment remains applicable. Draft 5 removes the demonstrated renewal cycle at the rule level, corrects proof scope and makes complete-set/restart precedence explicit. The residual findings are test reachability and liveness wording, not new structural contradictions. This is demonstrated convergence; the three-review nonconvergence trigger is not established. No architecture reconsideration or additional generic machinery is justified.

**Finite completion before owner acceptance consideration:** (1) replace §12.1's combined chronology with the separate reachable variants in D5-N1; (2) remove T-m's elapsed-time bound per D5-N2. No further architecture-selection decision is needed. Once recorded, owner consideration can proceed independently of implementation readiness. Proofs, all-writer/implicit-lock inventories, outstanding OD choices and regression evidence remain separately gated.

Only this new report is created and committed locally. The contract, responses, previous findings, decisions, source, tests and migrations remain unchanged. No push, merge, rebase, tag, experiment, proof or implementation occurs. WP1-R1, F5-4 and GAS-P4-002…005 remain OPEN.

**WP1 remains DRAFT — NOT ACCEPTED. Implementation remains unauthorized. WP2 remains HOLD.**
