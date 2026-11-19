# WP1 Architecture C — Gate 2 Correction Verification

**Date:** 18 November 2026\
**Repository / branch:** Rebowitz/GenealogyArchiveSystem / phase-4\
**Verified starting HEAD:** 095ba3f6bb6c69ece62085fc69e1677970970792\
**Verdict:** **ACCEPT WITH SPECIFIED CORRECTIONS**

## 1. Result and baseline

Draft 2 substantially addresses the original review. Architecture C's core approach remains credible; no new controller, journal, epoch or distributed mechanism is justified. Four bounded issues below still prevent accepting all corrections as complete.

The fetched origin/phase-4 exactly matched the required HEAD. The local phase-4 checkout was clean, advanced to that descendant without merge/rebase, and verified clean again before creating this report. Commit 095ba3f changes only the decision register, boundary contract and correction-response document; it contains no implementation.

Read in full: the independent review at bac22aecdaa815f37b73c8f67cc9626750157539; Draft 2; PHASE-4-WP1-ARCHITECTURE-C-GATE2-CORRECTIONS.md; D4-32 and governing D4-31; AGENTS.md, CLAUDE.md and ENGINEERING-DESIGN-INSTRUCTIONS.md. Relevant accepted Java/SQL and revision 3.9's retained ownership/lock argument were checked directly. The response matrix was treated as a claim to verify.

**No experiments, implementation tests or migrations were run.** CLOSED below means an architectural/documentation finding was corrected, not that implementation has passed or an existing implementation issue is closed.

All Draft 2 line references below are at the verified HEAD.

## 2. C1–C8 verification

| Checklist | Result | Evidence and limit |
|---|---|---|
| **C1** durable ownership and EDTs | **PARTIAL** | §5.4 T-1/T-2 explicitly commit acquisition before effects; T-3 covers all effect-dependent transactions; T-4 waits for quiescence/known outcomes; T-6 addresses old backends. The retained advisory-ownership premise and exact nonblocking reclaim/signalling path still need V1. |
| **C2** bootstrap | **CLOSED** | §6.4 M-1/M-6 and §7.5 B-1…B-6 remove the missing-table/Archive-ID circle. OFFLINE seed and initialization receipts are atomic; configuration association is explicitly separate; unknown init outcome cannot mint another ID. V2 concerns later migration-receipt recovery, not this corrected initialization sequence. |
| **C3** receiving and cleanup | **PARTIAL** | T-h and §5.8 preserve stale-predicate abandonment, live renewal and failed-STAGED-CAS behavior; cleanup uses the existing operation token. The broader administrator-cancel exception is unsafe as worded: V3. |
| **C4** historical equivalence/refusal | **PARTIAL** | §§8.1–8.5 separate content coordination from physical identity, preserve Q/attestation authority and put witness/conflict checks before inert disposition. D4-32 correctly defers scoped admission. The now-exhaustive classification needs complete baseline witness coverage: V4. |
| **C5** receipts | **PARTIAL** | §9.2 adds receipt UUID, kind-specific data, uniqueness, atomic CAS/insert and admin-only append-only evidence; the change_history atomicity claim is corrected. Generic receipt recovery is still inconsistent with native Flyway checkpoints: V2. |
| **C6** invariant/crash wording | **CLOSED** | INV-3/6/7 now distinguish unexplained objects, lifecycle changes and known administrative authority. §6.6 covers pre-table entry, OFFLINE attempt reuse, committed READY and partial reopen without replaying the READY decision. |
| **C7** platform hypotheses/proofs | **CLOSED as a specification correction** | P1/P2/P3 remain explicitly unproved; Windows directory-flush and non-local force limits are stated; proofs include old backends, identity precision, receiving, rename/writeback and startup/exit variants. Their safety claims remain FEASIBILITY-DEPENDENT. |
| **C8** decisions/traceability | **PARTIAL** | OD-3/6 are no longer automatic contract blockers; OD-9's changed UX is withdrawn; B-9 correctly records the missing current-run check; D-LP-3 and D-LP-9(a) are distinct. V1 must finish the runtime signalling/ownership contract. |

## 3. Every original finding

| Original finding | Classification | Supporting evidence |
|---|---|---|
| **B1** token durability/transaction coverage | **PARTIAL** | T-1…T-6 and T-a…T-g now supply the missing committed ownership and old-backend cases. V1 prevents treating the retained G10 proof as unchanged. |
| **B2** fresh-install circularity | **CLOSED** | §7.5 permits only pristine OFFLINE initialization; uses the accepted initialization lock/one UUID and same-transaction history plus receipt; B-5/config recovery is explicit. |
| **M1** receiving timeout/staging ownership | **PARTIAL** | §5.8 and T-h implement the narrower receiving correction. V3 is a distinct overextension of the logical-cancellation exception to administrator finalization. |
| **M2** historical identity/authority/precedence | **PARTIAL** | Exact-key identity, durable INTENDED authority and precedence are corrected; D4-32 resolves refusal scope. V4 concerns missing witness classes, not reopening R39-T. |
| **M3** receipt identity/atomic replay | **PARTIAL** | §9.2 supplies the requested data/atomicity contract, but §7.4 cannot require a separate receipt to decide every committed Flyway migration: V2. |
| **N1** invariant scope | **CLOSED** | Revised INV-3/6/7 and proof pre-admission clarification. §6.3's phrase “no compatible producer … alive” should be read under INV-7 as no admitted effect-capable holder, not absence of a waiting startup PID. |
| **N2** crash-prefix/suffix wording | **CLOSED** | §6.6 differentiates actual READY/OFFLINE/external states and attempt reuse. It no longer assumes NOLOGIN/OFFLINE at every crash point. |
| **P1** process identity | **FEASIBILITY-DEPENDENT** | §5.3 explicitly tests reliable absence, access/query errors, PID reuse and timestamp round-trip under real accounts. No execution evidence supplied. |
| **P2** process-lifetime fence | **FEASIBILITY-DEPENDENT** | §6.3 specifies retained unique channel/lock, no premature close, shared-mode checks and protected local object; FileChannel/JNA selection remains gated. |
| **P3** NAS/kernel completion | **FEASIBILITY-DEPENDENT** | §5.5 recognizes delayed bytes and namespace operations, force limits and refusal on an unproved topology. Proof 2 remains required. |

## 4. Newly introduced or remaining correction defects

### V1 — MAJOR: reconcile lost-advisory ownership, executable NOWAIT and signalling

**Contract:** §4.1 lines 108–109; §5.4 T-2/T-3/T-6, especially 297–316 and 339–356; §10.1 runtime capabilities.

The durable token now provides the intended same-operation exclusion, but the retained **G10 owned-O premise still says the writer holds its advisory lock**. Trace T-d explicitly lets W1 continue after losing that lock. Calling a late O acquisition “owned O” does not establish the old premise. Likewise, converting a formerly standalone finding into a transaction followed by an O check changes its wait set; the old terminal/autocommit classification is no longer applicable unchanged.

This is a proof/contract gap, not a claim that a new 40P01 was executed. The smallest correction is to state the token-owner substitute explicitly and inventory its competing writers: live-token owners, logical single-row cancellation/renewal, acquire/release/reclaim transactions and other content windows. Show that the new O acquisition follows the retained order or a specifically justified terminal exception. All token transactions must remain short and outermost. Do not silently retain a guard class whose premise no longer holds.

The reclaim notation also needs an executable form. PostgreSQL UPDATE has no NOWAIT clause; use, for example, **SELECT … FOR NO KEY UPDATE NOWAIT on the exact old interval, then CAS UPDATE/event in the same short transaction**, or another explicitly supported nonblocking design. A generic UPDATE described as “with NOWAIT” is not a SQL implementation contract. Confirm required table-lock behavior separately; do not equate every 55P03 with a former owner's row lock. [PostgreSQL SELECT](https://www.postgresql.org/docs/16/sql-select.html), [UPDATE](https://www.postgresql.org/docs/16/sql-update.html).

Finally, T-2 knows its **own attempted backend**; T-6's NOWAIT failure does not identify which backend belongs to the dead process. DataSources currently labels connections by purpose, not by process/interval. A concurrent legitimate same-role single-row writer can also hold O. It is not safe to infer “the blocker is the former owner” and terminate it merely from contention.

**Least-authority correction:** ordinary recovery may refuse/wait and leave ambiguous blocker diagnosis to the administrative path. If automatic termination is retained, require reliable attribution to the exact attempted backend/transaction and revalidation; never kill an arbitrary same-role backend. Same-role termination is a PostgreSQL permission boundary, not a per-process ownership boundary. No broad pg_signal_backend grant is needed or justified just to resolve one's own attempt. Signal success alone is not confirmed exit. [PostgreSQL signalling](https://www.postgresql.org/docs/16/functions-admin.html).

**Owner:** OD-6 remains a before-proof/implementation capability choice. Existing C-06/C-07 already require safe attribution and least authority. No new registry/table is requested.

### V2 — MAJOR: distinguish Flyway checkpoint evidence from control receipts

**Contract:** §7.1 E-5; §7.3; §7.4 lines 592–601; §9.2; §15 administrative transitions.

Consider a migration whose SQL and Flyway history row commit, followed by connection loss/crash **before** the separate MIGRATED receipt transaction. History is advanced, but no matching receipt exists. §7.3 permits validated-prefix continuation, whereas §7.4 requires a matching receipt to decide a committed migration and otherwise refuses. Neither §7.1 nor the accepted SchemaMigrator inserts a maintenance receipt in every migration transaction.

§9.2's universal “CAS singleton + receipt + singleton change” also does not literally describe a Flyway step, which changes schema/history, or ARCHIVE_INITIALIZED, which inserts the archive identity.

**Correction:** make the evidence/atomic target kind-specific:

- Native Flyway steps: after backend absence, decide from committed history/checksums/schema under exclusion; an aggregate/checkpoint MIGRATED receipt can then be recorded/reconciled without pretending it was atomic with an earlier migration.
- SEED: atomic with the control migration, as already specified.
- ARCHIVE_INITIALIZED: atomic with archive insertion/change history, as already specified.
- ENTER_OFFLINE/REBIND/READY: atomic singleton CAS plus exact receipt.

Show the “history committed, MIGRATED receipt absent” recovery branch. Do not add receipt plumbing to every migration merely to preserve the generic wording. A successful migration is not proved solely by a receipt either.

**Source:** accepted SchemaMigrator, ADR-0005, MigrationFailureIT; original review S10 and §11. **Owner:** none; this preserves approved checkpoint semantics and C-11.

### V3 — BLOCKING: logical cancellation is defined too broadly

**Contract:** §5.1 lines 228–230, T-7 and §5.8 line 441 (“A cancel is logical … unless it moves bytes”).

The receiving-timeout correction is sound. The added **administrator-cancel** exemption is not bounded merely by whether a move occurs.

Actual OperationAdminService.applyCancel:
- classifies an unverified object at lines 213–223;
- can call OperationCopies.claimBecomesUnresolved or releaseTarget at lines 269–274;
- commits CANCELLED at 275;
- deletes staging at 284–287.

The unverified/absent branches can move **no archival bytes**, yet mutate Q/U/claim accountability based on filesystem observations. They are EDTs under the draft's own definition. After advisory-session loss, a second actor must not use the broad “logical cancel” label to release/change accountability while the live original token owner may still write. A cancellation request/queue entry is different from effect-dependent finalization.

**Correction:** limit the token-free exception to the accepted stale RECEIVING abandonment CAS and similarly proven request-only/owner-independent transitions. Explicitly require interval ownership/exclusion and T-3 for every final-cancellation branch involving observations, Q/U, claims or cleanup, even when no bytes are moved. Preserve timeout cancellation and failed-STAGED-CAS behavior. No new state/table or changed user-facing timeout policy is needed.

**Requirements:** D4-31 C-01/C-02/C-07/C-09; retained P2-8 cancellation windows. **Owner:** none.

### V4 — MAJOR: exhaustive admission needs baseline generation/owner witnesses

**Contract:** §8.3 witness table and §8.5 lines 755–765.

The new rule classifies **every Q, U, owner and generation row** through the witness table. That table has no physical_location_owner witness and describes only a superseded generation created by REPAIR. A normal initial/current generation created by INGEST is not that class. Literal “no witness → UNPROVED → refuse READY” therefore rejects a healthy ordinary archive. An initial generation can also be superseded after repair; its creator remains INGEST.

Accepted OperationProcessor.commitNewContent creates the initial generation from the ingest's actual target generation, which need not be 1. physical_location_owner represents both OPERATION and GENERATION owners. Revision 3.9's retained AU-1/AU-3 already supply relevant structured derivations.

**Correction:** make the table explicitly exhaustive with those retained role-specific derivations, or explicitly reference the full retained classification for classes omitted from the summary. Include initial INGEST generations (including g₀ > 1), later REPAIR generations and both owner types, with forward/reverse ledger consistency. Preserve the corrected exact-key and known-relation rules. Do not infer past target assignment, merge all X paths, exempt UNPROVED, or manufacture a new historical ledger.

**Requirements:** C-01/C-02/C-10; D4-32. **Owner:** none; global-first remains approved.

## 5. Preserved protections, complexity and next gate

The document continues to retain the global order, content coordination, physical ownership, all 19 P2-8 / 22 Phase 2 windows, generation reservation, F5-1…F5-3, restriction/non-disclosure, optimistic concurrency, grouping safeguards and operation-specific recovery. B-9 now accurately identifies the missing current-run check. None is implemented by Draft 2.

The receiving correction preserves the accepted distinction: the timeout measures stale **progress and independent receiver renewal**, not OS death. Logical ABANDONED does not permit another writer or cleanup. Configuration limits/defaults and the late receiver's failed STAGED CAS remain unchanged. V3 is about the overbroad administrator exception, not a rejection of that corrected timeout policy.

Bootstrap now preserves one permanent Archive ID and handles configuration as a separate store. Receipts now have useful unique identities and exact old/new data; V2 concerns their application to different atomic targets. Runtime administration remains separated; automatic same-role signalling is not essential to C and should not expand authority merely for availability.

**Trend assessment:** this is the second C review with new interactions exposed by detailed corrections, so the required complexity/trend assessment is recorded here. Draft 2 adds explicit rules/traces and broader EDT instrumentation, but not a third table, generic controller or epoch framework. Several findings are genuinely corrected; the remaining issues are narrow scope/atomicity/ownership inconsistencies. Continue only with the finite V1–V4 corrections. No evidence presently requires revisiting the approved architecture. Further substantial machinery must be justified against these simpler alternatives.

**Recommended gate: CONTINUE with specified corrections**, then separately authorized feasibility proofs. P1–P3, complete EDT/privilege inventories, realistic inherited fixtures, installed-platform tests and the full accepted regression matrix remain required. A planned test is not proof of safety.

D4-32 resolves OD-7 only. OD-1/2/3/4/5/6/8 remain as recorded. No new owner policy choice is required for V1–V4; the existing least-authority and archival rules control.

Only this verification report is authorized to change and be committed locally. The boundary contract, decisions, original review and correction response remain unchanged. No push, merge, rebase, tag, implementation, experiment or closure of implementation findings is authorized or performed.

**WP1 remains unaccepted. WP1 implementation remains unauthorized. WP2 remains HOLD.**
