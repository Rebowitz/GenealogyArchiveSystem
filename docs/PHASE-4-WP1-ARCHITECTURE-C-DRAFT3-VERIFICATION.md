# WP1 Architecture C — Draft 3 Independent Verification

**Date:** 18 November 2026\
**Repository / branch:** Rebowitz/GenealogyArchiveSystem / phase-4\
**Verified baseline:** a9ba91d9fa6b23acf820bc1525ef5c50e7dce415\
**Verdict:** **ACCEPT WITH SPECIFIED CORRECTIONS**

## 1. Conclusion and scope

Draft 3 satisfactorily corrects **V2** and **V3**. **V1 is PARTIAL**: its SQL, ownership premise and signalling policy improve substantially, but a negative application_name search is promoted into proof of complete backend exclusion without an adequate attribution contract. **V4 is PARTIAL only for a small classification-wording correction**: the missing generation/owner witnesses are restored, but “exactly one listed class” conflicts with the table's cumulative checks.

The remaining substantive issue is a **MAJOR contract/proof gap within V1**, not a demonstrated implementation exploit or a reason to reject Architecture C. There is **one new MINOR issue**, the V4 exclusivity wording. No new independent BLOCKING finding is asserted. No implementation or feasibility claim is accepted by this report.

The smallest safe direction is to keep labels diagnostic, require independently established exclusion/attribution for takeover or signalling, and use the existing offline exclusion/drain when former backend authority cannot be established. A connection registry is **not** required by this review.

**Recommended gate: CONTINUE with the bounded corrections in §7**, then owner consideration of contract acceptance and separately authorized proofs. This is not implementation readiness, WP1 acceptance or authorization to execute the proofs.

## 2. Baseline and evidence

origin/phase-4 was fetched and exactly matched the required baseline. The local phase-4 working tree was clean before it was advanced to that descendant without merge/rebase, and clean at the start of review. The baseline changes only the boundary contract and two correction-response documents relative to ef5859e; Java, SQL, tests, decisions and deployment code are unchanged.

Read the complete Draft 3 contract; Gate 2 independent review; Gate 2 correction-verification report; Gate 2 correction-response document; V1–V4 correction-response document; Phase 4 decision register including D4-31 and D4-32; and governing AGENTS.md, CLAUDE.md and ENGINEERING-DESIGN-INSTRUCTIONS.md. Prior reviews were not edited. Builder responses were checked against operative text and source, not accepted as proof.

Material implementation evidence at the baseline:

- data/DataSources.java: application_name is currently only the connection-purpose label supplied when Hikari creates the pool.
- data/OperationLocks.java and FileOperationRepository.java: separate advisory sessions; transactional state/event CAS; notes, receiver renewal and stale receiving abandonment.
- preservation/OperationAdminService.java:161–293: cancellation finalization observes objects, can alter claims/U, commits CANCELLED and cleans staging.
- IngestionService.java and RecoveryService.java: independent receiver renewal, stale state CAS, late STAGED CAS refusal and ordinary staging cleanup.
- OperationProcessor.java:573–589 and 627–703: initial generation uses the actual planned generation; repair preserves prior creator and creates the new generation; COMMITTED precedes final cleanup.
- DestructionProcessor.java:36–37, 111–160 and 331–369: stoppable-repair states, operation coordination and U writer roles.
- physical-location/generation SQL constraints, accepted migration configuration and ADR-0005; the retained AU derivations in proposal revision 3.9.

The source does **not** show that a committed generation creator can simply be cancelled by destruction: STOPPABLE excludes COMMITTED. No finding is invented on that basis.

PostgreSQL documentation/source was consulted for labels, statistics snapshots and parameter mutability; no SQL was executed. No application tests, migration, feasibility experiment or implementation ran.

All contract line references below are to the verified baseline.

## 3. Individual V1–V4 dispositions

| Finding | Status | Independent result |
|---|---|---|
| **V1 — ownership, reclaim, backend exclusion** | **PARTIAL** | §4.1 explicitly replaces advisory-owned O; §5.4.1 classifies token-owned and terminal lockers; T-1/T-4/T-6 are short outermost transactions; NOWAIT is on SELECT, table waits have lock_timeout; 55P03 means busy, not attribution. Runtime no longer signals. However, T-6's negative label census and §10.4(b)'s label-only attribution do not yet justify their exclusion/termination conclusions. See §4. |
| **V2 — Flyway evidence and receipts** | **CLOSED** | E-5, §7.3, §7.4 and §9.2 distinguish native history/schema evidence, atomic SEED, atomic initialization, atomic singleton transitions and post-migration MIGRATED summaries. The committed-history/missing-receipt branch is explicit and does not replay migration or require per-step receipt plumbing. |
| **V3 — administrator cancellation** | **CLOSED** | §5.1's closed token-free list distinguishes request notes from finalization. §5.8 requires the entire performCancel interval, including no-move U/release branches and cleanup; retry/attest/release require EDT ownership unless specifically proved request-only. T-i tests a live lost-advisory owner. Accepted receiving abandonment/renewal/late-STAGED-CAS protections remain. |
| **V4 — historical witness completeness** | **PARTIAL (minor wording only)** | §8.3 restores initial INGEST generations at arbitrary valid g₀, later REPAIRs, superseded initial generations, both owner types, U writer roles and Q legacy reasons, including forward/reverse checks and retained AU derivations. The global “exactly one listed class” sentence must be scoped to alternative producer classes rather than all cumulative checks: §5. |

CLOSED is architectural/documentation closure only. P1/P2/P3 and the inventories/tests remain **FEASIBILITY-DEPENDENT or implementation obligations**. Existing WP1 implementation findings remain OPEN.

## 4. V1: useful labels do not themselves prove complete exclusion

### 4.1 What Draft 3 now gets right

T-2 knows its own attempted backend tuple, closes the connection and waits for confirmed absence; the runtime never guesses a blocker or sends a signal. T-6 uses FOR NO KEY UPDATE NOWAIT followed by CAS/event in one bounded transaction, while lock_timeout also covers table-lock waiting. Contention does not identify a former owner.

The replacement token-owned premise and sequential EDT rule are meaningful progress. The O-row inventory must still be verified completely, including trigger/FK acquisitions, multi-row renewal and destruction's multiple owned operations. “One owning thread” is conditional on excluding old backends, not a substitute for that exclusion.

Terminal non-owner notes must remain **outermost and free of earlier ordered locks**, as the inventory's “O only” class requires. A note being the last statement is not enough if its caller holds Ct and waits for another owner's O. Keep that restriction explicit when mapping actual call sites; do not silently extend the exception.

These are credible bounded rules to validate in the planned inventory and lock tests. No experiment has established them here.

### 4.2 Remaining MAJOR gap: the label census is not authoritative as written

**Contract:** §5.4 lines 309–317, T-6 lines 391–418, §5.4.1's no-former-backend premise, §10.4 lines 1088–1100.

The draft reasons:

1. the recorded JVM is gone;
2. no pg_stat_activity row carries its label;
3. therefore no backend of its former interval exists.

Step 3 follows only if labelling is **complete, exact, stable for every physical backend's relevant lifetime, unambiguous and observable**. “All connections are created through DataSources” is not that complete lifetime/attribution rule. The current factory supplies an initial client property; it is not a server-owned process identity binding.

PostgreSQL defines application_name for statistics/logging. Its source registers it as **PGC_USERSET** on both supported major versions. A session can change the label; text is length-limited. A UUID makes accidental collision unlikely but does not authenticate ownership or make the setting immutable. [PostgreSQL 16 source](https://raw.githubusercontent.com/postgres/postgres/REL_16_STABLE/src/backend/utils/misc/guc_tables.c), [PostgreSQL 18 source](https://raw.githubusercontent.com/postgres/postgres/REL_18_STABLE/src/backend/utils/misc/guc_tables.c), [label documentation](https://www.postgresql.org/docs/16/runtime-config-logging.html#GUC-APPLICATION-NAME).

pg_stat_clear_snapshot refreshes statistics visibility; it does not authenticate a label or prove the census includes every relevant former connection. Same-role visibility helps inspection, but it does not turn text into provenance. [Statistics documentation](https://www.postgresql.org/docs/16/monitoring-stats.html).

**Counterexample to the stated inference:** an old backend B remains in an EDT before its O check, holding an ordered resource such as Ct. Its application_name is missing, changed or not matched by the implemented label encoding. The JVM dies. T-6 can acquire O, find zero matching labels and authorize a replacement while B still holds/request locks. The claim “no backend can later lock anything” is then false. T-3 may still force a later stale transaction rollback, but the new deadlock/exclusion argument explicitly assumes no such backend survives; a predicate is not proof of that stronger premise.

This is a counterexample to the **contract's unconditional label claim**, not evidence that the current accepted application actually executes a label-changing SQL statement. Audited supported-program invariants can justify trust in a label, but those lifetime/coverage assumptions must be explicit and established before the census is used as authority. They cannot be replaced by a test that labels only the prototype's ordinary connection.

There is a second direction: a different live process's backend can carry the dead instance's string. §10.4(b) would call that “exact attribution” and permit termination. Thus “a spoofed or foreign label can only cause refusal” is not true of the whole procedure as written. Matching pid/backend_start identifies **that server backend**, not its client-process ownership.

### 4.3 Smallest bounded correction

1. State that application_name is a **diagnostic/candidate/refusal signal**, not by itself complete former-backend exclusion or exact authority to terminate.
2. Before automatic reclaim, require an independently justified exclusion/fencing condition covering **all** surviving EDT authority. If that condition cannot be established, refuse the subject. The existing administrative offline route—producer exclusion, admission closure and drain of every relevant backend—already supplies a conservative fallback.
3. Retain exact independently recorded attempted-backend tuples for unknown-COMMIT resolution where available. A positive label match alone must not authorize terminating another backend; unattributable sessions go to investigation/refusal or the authorized offline bulk-drain boundary.
4. If the builder wants to retain a label-based automatic path under the supported-writer trust model, specify and prove the **additional** complete lifetime, encoding, visibility, pool/reset/reconnect and no-bypass assumptions, plus treatment of every unknown session. Do not present those assumptions as guarantees supplied by PostgreSQL. A changed/missing/ambiguous label must not silently enable takeover.
5. Add negative proof variants: old EDT backend before its O check with a missing/changed/truncated label; duplicate/foreign label on a live backend; unavailable/incomplete census. Expected outcome is no unjustified reclaim and no unrelated termination.

This requests **no registry or new durable table**. It applies to uncertain former authority, not every normal handoff: clean release, positively established exclusion and unrelated safe work can continue. It may use deliberate administrative recovery already approved by C-04/C-07/C-09.

**Owner approval:** no new policy decision is required to obey those rules. Existing OD-6 still controls the administrative principal/grants before proofs/implementation. Any proposal to relax exclusion for availability would require explicit approval; none is recommended.

**Runtime signalling:** the code-path prohibition is appropriate. Do not confuse “do not grant pg_signal_backend” with removing PostgreSQL's default same-role signalling capability. The privilege inventory must distinguish supported-program behavior from an enforced function permission boundary; no extra runtime signalling capability is necessary.

## 5. V4: coverage restored; scope the “exactly one” statement

**New MINOR issue:** §8.3 line 857 requires every row to match exactly one listed class, but the table mixes alternative creator classes with cumulative integrity checks.

A superseded INGEST-created generation g₀ must satisfy the **initial-generation** creator witness and the **superseded-generation** state rules; it participates in the **generation-set** check as well. Similarly, an operation's target meaning and quarantine-pointer consistency are different checks, not competing producer identities. A literal match-count-equals-one implementation could reject the healthy repaired archive the draft expressly requires to pass.

**Correction:** require one applicable producer/creator classification **where the alternatives are mutually exclusive**, and require **all applicable** state, set, pointer, ledger and alias checks. Do not make aggregate checks count as additional competing row classes. This is a sentence/table-structure clarification, not a missing historical fact, new ledger or new safety mechanism.

Coverage itself is now materially complete for the previously missing classes. Source checks support:

- g₀ is op.targetGeneration in commitNewContent, not necessarily 1;
- creator identity survives supersession;
- OperationProcessor:650–660 supplies the supersession U role;
- DestructionProcessor:331–369 supplies CURRENT_COPY, DAMAGED_COPY, QUARANTINED_COPY and former-location roles;
- OPERATION/GENERATION owner forward and reverse checks remain required;
- legacy DAMAGED_GENERATION and recorded-absent Q treatment are explicit;
- positive witnesses do not authorize effects independently of current evidence/claims.

D4-32's global-first refusal and deferred scoped admission remain intact. R39-T is approved and not reopened. No missing target chronology is invented. Realistic inherited-state fixtures remain an implementation obligation.

**Owner approval:** none for this clarification.

## 6. C1–C8 regression check and retained uncertainties

| Requirement | Draft 3 assessment |
|---|---|
| C1 token durability/EDTs | Acquisition/reclaim/release durability, unknown outcomes and full EDT validation retained. Backend-exclusion authority remains PARTIAL under V1; the rest requires the planned inventory/tests. |
| C2 bootstrap | No regression identified: pristine administrative entry, OFFLINE seed, one Archive ID, separate config association and exact unknown-init recovery remain. |
| C3 receiving/cancellation | Corrected: token-free abandonment/request-only transitions do not authorize cleanup; full cancellation observations/accountability/cleanup require interval ownership. |
| C4 historical admission | Missing witnesses restored; exact-key/current-authority/global-first rules retained. Apply the minor cumulative-classification clarification. |
| C5 receipts/migration | Corrected: native Flyway history is authoritative; missing MIGRATED summary is reconciled rather than used to reject committed history or replay migration. |
| C6 invariant/crash scope | No regression identified: admitted producers versus administrative authority, mutable lifecycle versus immutable evidence, unexplained objects, actual OFFLINE/READY/reopening state are distinguished. |
| C7 platform/proof limits | P1 process identity, P2 local process-lifetime locking and P3 NAS completion remain explicitly unproved and fail closed. No proof was run. |
| C8 decisions/traceability | D-LP-3 and D-LP-9(a) remain distinct/unapproved; OD-3/6 are implementation/proof choices; OD-7 is D4-32; OD-9's changed UX remains withdrawn. No implementation finding is closed. |

Accepted Phase 0–3 identity, provenance, evidence-bound preservation, restriction/non-disclosure, optimistic concurrency and grouping/recovery safeguards remain requirements. The 19 P2-8 / 22 Phase 2 windows and F5-1…F5-3 are retained. New token instrumentation must not be described as already implemented or as reducing those obligations.

The full O-writer/EDT inventory, B-9 late-runner correction, least-privilege deployment, inherited fixtures and Windows/Linux × PostgreSQL 16/18 regressions remain future implementation acceptance work. A pending inventory is permissible; the false unconditional backend-exclusion inference is a contract correction, not merely an experiment waiting to pass.

## 7. Complexity, finite completion criteria and next owner gate

Draft 3 closes two substantive findings and restores the missing witness coverage. This is measurable progress, not merely a lower count. However, the new label has become an authority mechanism, not just diagnostic text, and needs the simpler fail-closed alternative in §4 before further expansion.

The contract has grown from Draft 2's 1,099 lines to Draft 3's 1,314, while table/state counts remain one/two tables and READY/OFFLINE. Line count alone is not complexity or safety. The meaningful change is another backend-attribution premise coupled to automatic reclaim. The engineering-governance trend assessment therefore remains necessary; the direction is converging in several areas, but the authority claim is not yet justified.

**Finite corrections before accepting these corrections:**

- [ ] Remove sole reliance on the negative label census and label-only administrative termination. Specify complete exclusion/attribution or safe refusal/offline fallback, and the essential negative proof cases in §4.
- [ ] Scope the witness table's “exactly one” to genuinely alternative producer/creator roles; run all applicable cumulative integrity checks.
- [ ] Keep O-only terminal exceptions free of pre-held ordered locks, and ensure the planned writer inventory validates the actual lock classes after these changes. No additional global mechanism is requested.

**Recommended governance gate: CONTINUE with these bounded corrections.** RECONSIDER is not warranted merely because an implementation choice or P1–P3 proof remains open. Neither is unconditional contract acceptance warranted while a diagnostic label supplies the asserted exclusion proof.

After correction, the next owner gate may consider accepting the architectural contract and separately authorizing focused proofs. This report grants neither approval. OD-1…OD-6 and OD-8 remain as recorded; D4-31/D4-32 are unchanged.

Only this independent report is created/committed locally. The contract, prior reports, response documents, decisions, code, SQL, tests and deployment files are unchanged by this review. No push, merge, rebase, tag, experiment or implementation is performed. Architectural finding dispositions do not close WP1-R1, F5-4 or GAS-P4-002…005.

**WP1 remains DRAFT — NOT ACCEPTED. Implementation remains unauthorized. WP2 remains HOLD.**
