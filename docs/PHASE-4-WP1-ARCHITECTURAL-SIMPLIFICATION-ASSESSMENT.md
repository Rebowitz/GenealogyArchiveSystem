# Phase 4 WP1 Architectural Simplification Assessment

**Repository:** Rebowitz/GenealogyArchiveSystem\
**Branch inspected:** phase-4\
**Starting and assessed commit:** 0b8f32b61947f38c1190c2d1acb6d4e9e8540d35\
**Date:** 17 November 2026\
**Scope:** assessment only; no implementation authorization\
**Recommendation:** **C — adopt a hybrid architecture**\
**Recommended gate:** **SIMPLIFY**, following the current RECONSIDER comparison; not READY FOR IMPLEMENTATION REVIEW\
**Status preserved:** WP1 unaccepted; WP2 HOLD

## 1. Executive conclusion

The current comprehensive proposal contains **unnecessary architectural complexity relative to the approved requirements**. This conclusion is not that its individual safety mechanisms are all unnecessary. Ordinary concurrency corrections, physical-copy accounting, evidence-bound actions, fail-closed admission of known unsafe inherited states, and exclusion of live filesystem writers are necessary. The unnecessary part is making a general resumable maintenance controller, protocol epochs, per-connection registrations, generic actions/windows/publication sequences and exceptional binding recovery into a single framework through which all those obligations must pass.

Recommend **C: retain the demonstrated ordinary-operation corrections and a small, explicit runtime/maintenance fence; replace the general maintenance framework with controlled offline maintenance and disaster recovery**. Architecture B below supplies the replacement boundary. The hybrid retains the useful parts of A rather than returning to the accepted code unchanged.

The specifications require safe recovery, not universal unattended continuation. BUILD-SPEC §56 expressly permits restoring a pre-upgrade checkpoint instead of elaborate reverse-migration machinery. ADR-0005 already provides explicit Flyway migration, per-migration transactions/history/checksums, startup schema mismatch refusal and post-migration validation. Full backup/restore is principally Phase 8. None of those facts excuses an unsafe Phase 4 upgrade; they do allow an exclusive, fail-closed administrative procedure instead of nine control tables.

**The simplified design can cover the same approved requirements, conditional on explicit hosting, maintenance-authority and recovery-policy decisions and proof of the narrow fencing contract described below. It is not yet demonstrated by implementation or tests.** A naïve alternative consisting only of PostgreSQL advisory locks and “stop the services” does **not** satisfy the requirements: a database session can disappear while its JVM still writes bytes, a service stop timeout is not process exit, and old clients may restart.

This report therefore recommends an architectural direction, not implementation authorization. The smallest next step is a bounded replacement contract and two feasibility proofs, not another comprehensive revision 3.10. If the owner requires unattended multi-host maintenance/takeover or autonomous arbitrary preservation repair, the proposed simplification would not satisfy those additional requirements; they must be stated and approved rather than inferred.

The cumulative review sequence is not converging sufficiently. The ninth, tenth and eleventh assessments each found new structural interactions in maintenance admission, compatibility, binding or historical classification. Specific findings closed, but recovery states and exceptions continued to grow. The new governing engineering instructions correctly require the comparison undertaken here.

## 2. Repository verification and evidence

### 2.1 Checkout and authorized scope

A fresh local checkout was created for this assessment because this chat had no attached repository worktree. Before analysis it was verified:

- branch: phase-4, tracking origin/phase-4;
- HEAD: 0b8f32b61947f38c1190c2d1acb6d4e9e8540d35, exactly the required starting commit;
- initial git status: clean;
- remote phase-4: the same commit at initial verification.

The difference from the eleventh-review head ed7670c2ae2879a52b38bcde80b0c5571808dfce to this starting head is limited to AGENTS.md, CLAUDE.md and the new engineering-governance document. Production source and proposal revision 3.9 are unchanged across those heads.

Only this assessment document is authorized to change. No code, tests, migrations, proposal, accepted specification or owner decision is changed by this assessment. No implementation tests were run; none of the proposed alternatives has executable acceptance evidence.

### 2.2 Sources inspected

Repository paths below refer to the assessed starting commit unless another commit is named. They are evidence references, not instructions to modify those files.

**Mandatory governance and requirements**

- [AGENTS.md](../AGENTS.md), [CLAUDE.md](../CLAUDE.md), [ENGINEERING-DESIGN-INSTRUCTIONS.md](ENGINEERING-DESIGN-INSTRUCTIONS.md), owner-approved 2026-11-17.
- [ACCEPTANCE-SPEC.md](ACCEPTANCE-SPEC.md): SEC-004/007/008, TX-001…008, IV-001…007, BK-001…008, OP-001…005, UP-001…011 and the acceptance/phase boundaries.
- [BUILD-SPEC.md](BUILD-SPEC.md): §§2–8, 26–38, 40, 43–50, 55–56, 60, 62–70.
- [PHASE-4-PLAN.md](PHASE-4-PLAN.md), [PHASE-4-DECISIONS.md](PHASE-4-DECISIONS.md), [PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md](PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md), particularly D4-16/D4-27, PR4-R3, W1–W14, LO-1…LO-7, §8 migration rules and work-package gates.
- [PHASE-2-DECISIONS.md](PHASE-2-DECISIONS.md), [PHASE-3-DECISIONS.md](PHASE-3-DECISIONS.md), accepted Phase 2 operations notes and Phase 3 plan/ADR-0011.
- ADR-0002 cross-platform, ADR-0004 configuration, ADR-0005 migrations, ADR-0006 Archive identity, ADR-0008 jobs/history, ADR-0009 preservation, ADR-0010 service identities and ADR-0012 Phase 4 architecture.

**Proposal and review evidence**

- [PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md), revision 3.9, including operative §5.10 rather than superseded intermediate text.
- [PHASE-4-WP1-AUDIT-CORRECTIONS.md](PHASE-4-WP1-AUDIT-CORRECTIONS.md) and [issues.md](issues.md).
- Stage A through eleventh independent assessments available in this chat's saved assessment artifacts. The tenth assessed 48eeb8bd574572bf63bfb3c2be260faaf210abd3; the eleventh assessed ed7670c2ae2879a52b38bcde80b0c5571808dfce. These reports are not tracked repository documents at the starting commit. Their absence from the repository tree is a distribution limitation, not a missing review in this assessment.
- The ninth-through-eleventh closure matrices and counterexamples were used to assess convergence. Builder-local historical verification and LP-6 characterization are not treated as future implementation proof.

**Actual implementation and tests**

- src/main/java/org/genealogyarchive/migration/SchemaMigrator.java and PostMigrationValidator; service/SystemStatusService; boot/ProcessLauncher and CoreConfiguration; cli/Cli.
- data/LockSequence, OperationLocks, FileOperationRepository, FileContentRepository, QuarantineRepository and UnresolvedObjectRepository.
- preservation/OperationProcessor, OperationAdminService, OperationCopies, QuarantineMoves, UnresolvedObjects, DestructionProcessor, RecoveryService, IntegrityService, FileService and RepairService.
- records/GroupingService, CaptureSessionService, RecordService and their repositories.
- worker/WorkerProcess, persistent job mechanism, configuration and storage/path abstractions; deployment scripts and native service tests.
- MigrationFailureIT, MigrationIT, Phase1/2/3/4SchemaIT, LockSequenceTest/IT, Lp6LockBehaviourIT, RecoveryIT, IntegrityRepairIT, RepairLockPoolIT, GroupingIT, RecordRestrictionIT, WorkerProcessKillIT, WorkerJobsIT and relevant Phase 2 audit regressions.

Some source/test names first probed were absent (SchemaMigratorIT and ProcessLauncherIT); the actual files are MigrationIT/MigrationFailureIT and ProcessLauncherTest. There is no claim that absent test files were executed.

### 2.3 Verified baseline facts

There are **25 migration SQL files and 40 CREATE TABLE application definitions** in the current migration chain. This includes the already present WP1 vocabulary/Historical Date work; “40 accepted application tables” in the proposal is a shorthand for this current baseline inventory, not a new independent acceptance decision on all WP1 changes. Flyway's own history table is excluded.

Actual ordinary protections already include PostgreSQL transactions, row versions, immutable identity triggers, physical_location_owner uniqueness, persistent file-operation and quarantine records, evidence-bound unresolved decisions, advisory operation/content coordination and persistent grouping/job progress.

SchemaMigrator explicitly disables ignoring unknown future migrations, performs versioned validation, and never migrates from normal web/worker startup. WorkerProcess checks readiness at startup and periodically. Its ten-second join timeout is not evidence that a blocked worker has exited. OperationLocks uses a **separate session connection**; its disappearance is not proof that the owning process and storage I/O ended.

Windows has distinct virtual service identities; Linux uses one UID with systemd sandbox separation. The documented workgroup-NAS account fallback is not CI-tested. NAS primary storage is supported in principle; that does not make a local native lease a cross-host fence.

## 3. Actual requirements-to-mechanism matrix

Legend: **R** approved requirement; **I** accepted implementation invariant; **P** current proposal choice; **O** operational assumption; **D** unresolved owner decision. A P is not promoted into an R by being detailed or repeatedly reviewed.

| Requirement / class and source | Relevant failure | Existing protection | Additional protection actually needed | Verification |
|---|---|---|---|---|
| R: preserve permanent identities and Archive ID; BUILD §§7–8, BK-005/007 | Restore/retry invents another identity | Archive row, IDs, retired identities, immutable triggers | Exact archive/config identity checks and deliberate offline installation rebinding; no new Archive ID on restore | Clean-room new-hardware/cross-OS restore; lost init COMMIT |
| R: optimistic concurrency; SEC-008, BUILD §44 | Stale edit overwrites current data | row_version, transactional services | Correct lock order where multi-row serialization is needed, with visible conflict | Two-session stale-save and race tests |
| R/I: all approved Phase 4 writers follow a global order; PR4-R3, LO-1…007 | Three-party File/Record cycle, implicit FK/unique waits | LockSequence registers explicit L1 but does not validate its suborder | Correct ordinary File/content/Record order, actual ordered acquisitions, implicit/unique-key inventory | Deterministic PostgreSQL 16/18 races; explicit 40P01 assertions |
| R/I: grouping meaning and non-disclosure; D3 safeguards | Hidden boundary merges records; reordered history | Persistent grouping runs, session locking, chunk progress and visibility rules | Sorted uniqueness acquisition without changing request order; preserve classification and creation semantics | Existing GroupingIT plus insertion/compaction and hidden-row fixtures |
| R/I: restrictions and authorization; SEC-004, P2-H, D4-16/27 | Concurrent derivative/mention publication escapes restriction | File inheritance triggers; service visibility; planned deferred Phase 4 invariant | Stabilized affected set, fresh revalidation, ordered locks; atomic approved restriction confirmation | Both race orderings, traversal/count/history non-disclosure |
| R: crash-safe ingestion; TX-002/003/005 | Bytes copied, final row missing | file_operation, staging, hashes, target claim and quarantine intent | Retain accepted recovery; fix identified ownership/generation/ordering gaps | Fault-point recovery and real killed worker |
| R/I: accountable bytes and evidence; P2-I, V0020, IV/TX | Wrong or replaced object moved/deleted | Q/U ledgers, owner keys, exact evidence and administrator attestation | Scoped current-claim revalidation; no foreign release, no historical inference | Replacement, alias, repeated repair and destruction races |
| R: storage failure fails visibly; TX-004/008 | NAS outage mistaken for absence | WAITING_FOR_VOLUME, marker checks, evidence-sensitive presence | Preserve distinction; prohibit takeover of possibly live effect execution | NAS interruption, blocked I/O and reconnection tests |
| R: known recoverable migration; UP-004/005, BUILD §56 | Partial upgrade or old executable | Explicit Flyway, transactional step/history/checksum, AHEAD/BEHIND refusal | Exclusive offline boundary, persisted fail-closed status, startup/build checks and old-launch exclusion | Crash after each committed step; old executable and live worker negatives |
| R: restoration with portable identity; BK/UP | Host/path/database identity changes | Portable volume paths, Archive ID/markers, accepted restore requirements | Coherent checkpoint; deliberate isolated restore/rebind; validation before reopening | Same/new host, logical/physical, Windows/Linux cases |
| R: jobs survive restart; OP-001/004 | Lease expiry mistaken for stopped effect | Durable job state and operation IDs; periodic recovery | Separate job eligibility from proven effect-owner exclusion | Crash, delayed old owner, session-loss takeover tests |
| R/I: cross-platform supported accounts/topologies; UP-003/008/009 | POSIX-only assumption; web writes originals | Native service wrappers, ACL/sandbox and Java containment | Correct portable exclusion and owner/admin permissions; NAS behavior labelled accurately | Installed-service matrix, custom DataDir and NAS characterization |
| R: proportionate design; BUILD §§3/67/68; engineering instructions | Recovery complexity creates more failure modes | Mature PostgreSQL/Flyway/platform services | Justify each new state/dependency; choose bounded administrative recovery when sufficient | Requirements tracing and independent boundary audit |
| I/O: trusted application writers and practical filesystem threat model; P2-E, V0020 | Manual row or hostile replacement resembles legitimate history | Owner-approved practical containment/evidence limits | Explicit bounded inherited-writer policy; refuse UNPROVED/conflicting states | Real writer-produced histories; demonstrate indistinguishable manual shape |
| P: nine control tables, 49 mutation gates, P/E per connection, controller generations, generic action/window/publication framework | Stale controller or effect overlaps maintenance | These do not exist in current production code | Requirement is exclusion plus recoverability, **not these representations** | Compare A with B's global offline authority |
| D/O: one designated application host, all producers inventoried | Second host continues after local fence | No accepted universal single-host/process proof | Owner chooses hosting contract; otherwise verify/exclude every authorized producer host | Same-host multi-process and explicit second-host negatives |
| D: autonomous generic maintenance recovery | Lost connection/action response | Accepted operation-specific recovery; migration checkpoint restore allowed | Owner must say whether unattended continuation is actually required | If required, validate a durable generic controller; otherwise use offline procedure |
| D: elevated/DBA maintenance authority | Cannot block logins/drain unsafe sessions | Current configured application role has limited signaling authority | Deliberate offline admin path or a proven alternative; do not silently grant runtime superuser | Least-privilege installed-role tests |

The approved Phase 4 global order and LO tests are architectural obligations; the literal previously accepted L1 chain has a demonstrated defect. Revising that chain requires explicit architectural approval, not a return to the defective sequence. Conversely, neither a universal “zero database deadlocks under every arbitrary SQL writer” theorem nor online schema changes are approved requirements. Supported writers must be ordered and tested; unsupported direct SQL is not silently made safe.

## 4. Architecture A — corrected comprehensive protocol

### 4.1 Strongest reasons to retain A

A is not arbitrary bureaucracy. It attempts a strong, uniform boundary: every compliant database writer declares the current protocol/epoch, maintenance has durable authority, stale backend work is rejected, exceptional actions have stable identities, and inherited data is audited before the theorem is relied on. This offers machine-checkable evidence, consistent diagnostics and less reliance on a careful offline administrator.

Its generic journal could be justified if many independent maintenance tools needed unattended restart across arbitrary filesystem effects, or if a requirement demanded automatic handover while unrelated application processes remained running. Per-table enforcement also catches missed application admission paths better than startup checks alone.

The prior reviews identified genuine bugs that ordinary row ordering alone cannot solve: path reoccupation and foreign claim release, generation reuse, copied/wrong installation access, unknown COMMIT and controller loss. A retains these discoveries in one explicit model.

### 4.2 Cost of making A complete

A currently proposes nine control tables, 49 covered application/control tables, CU-0…CU-35 (**36 units**) and **19 P2-8 / 22 Phase 2 business-window changes**, plus native lease integration. These are verifiable proposal counts, not implemented coverage. The 22 windows are not all attributable to maintenance complexity: many are genuine ordinary preservation corrections that a simpler architecture still needs.

To make A defensible after the eleventh assessment, at minimum:

| Outstanding input | Necessary A correction | Additional state/coupling/testing |
|---|---|---|
| Repeated repair of persisted X | Scope private-UUID exclusion to unpersisted INGEST; compare persisted generations/keys | Writer-role-specific equivalence; repeated R1/R2 partial-repair positives |
| Cross-operation subject identity | Preserve bound Q/U/destruction known-object relations | Classifier must distinguish related representations from independent owners |
| Durable intent authority | Separate inactive historical classification from active intent/evidence authority | All settlement helpers and audit theorem must use the same contract |
| R39-T | Owner accepts bounded writer origin or chooses truly independent offline resolution | Policy-specific positives/false-refusals; no retrospective proof |
| Unregistered orphan sessions | Authorized orphan-drain path distinct from accidental I2 | More authority branches and crash/registration states |
| Restore at migration checkpoint | Carry typed manifest compatibility through restore or finish migration before conversion | Cross-product of run kind, schema checkpoint, P/E and binding |
| Post-binding crash | Resume existing run using new binding; do not supersede/rebind again | Old/new binding × event × controller generation × run state |
| Lease repair rename/create crash | Durable correlation before binding commit; safe adoption | Filesystem receipt/reconciliation and independent old-holder exclusion |
| Allocated publication with no effect | Define resume/terminal resolution, including stale epoch | Allocation/attempt/result/abandon states and close predicates |
| JNA loading/OS waiting | Correct fallback flags, ABI and same-handle lock lifetime | Installed native matrix, Windows sharing-open and Linux OFD semantics |
| Binding event atomicity | Exact event/update ordering and privileges | Deferred or function-ordered checks; seed/init/rebind exceptions |

Not every row requires another table; many can use the current nine. That does not remove the combined state space. The continuing problem is interaction between mechanisms, not merely schema count.

### 4.3 Convergence judgment

The ninth review found binding, bootstrap, controller and replay contradictions. The tenth found that the upgraded executable could not enter the protocol it needed to migrate, and that repair/restore exceptions were not executable. The eleventh found an intermediate-checkpoint restore incompatibility and a historical classifier rejecting valid repeated repairs, alongside incomplete crash paths.

These are structural contradictions, not cosmetic omissions. Additions closed individual findings while generating new interactions. The owner-approved escalation thresholds have been exceeded. A could theoretically be completed, but another incremental repair cycle is not the preferred engineering investment without a requirement demonstrating why its generic machinery is necessary.

F5-1 through F5-3 remain architecturally resolved. Their discoveries and protections are retained in B/C; simplification is not permission to disregard them.

## 5. Architecture B — explicit ordinary/maintenance/recovery separation

This is a proposed replacement contract, not implemented code or an owner-approved design. It deliberately trades autonomous maintenance continuation for bounded administrative recovery. That trade is compatible with the cited approved requirements.

### 5.1 Ordinary operations: retain narrow domain mechanisms

1. Preserve accepted transactions, row versions, identity/restriction constraints, owner and Q/U evidence ledgers, job/grouping progress and service authorization.
2. Retain the demonstrated WP1 lock corrections: File before Record, final strength first, actual ordered acquisition, correct worksheet insertion/compaction, derivative closure stabilization, File/content ordering and content-scoped preservation serialization. Keep implicit FK/trigger/unique-key analysis. A guard records attempted versus acquired resources correctly but does not pretend to observe implicit SQL locks.
3. Retain F5-1…F5-3, Wh1a/Wh1b monotonic reservation and Wh2 unresolved-location exclusion, together with scoped current-claim release. Do not remove generation high-water protection simply because a global maintenance framework is removed.
4. Reuse accepted file-operation recovery for ingestion, repair, quarantine, cancellation and destruction. Do not build a second generic action journal for those same ordinary effects.
5. Permit multiple web/worker/CLI processes under the chosen hosting contract. Neither one JVM nor one worker is assumed.

**A required limit on advisory-lock takeover:** an advisory session disappearing is not proof its old process stopped I/O. The replacement contract must prevent another worker from resuming the same outstanding effect while that remains possible. The smallest candidate is a durable effect-owner token on the existing file_operation row (process-instance UUID, host identity and a verifiable process-start identity), acquired by CAS before an effect interval and cleared only after that interval has ceased. It is not a heartbeat lease; elapsed time never permits stealing it.

A replacement may reclaim an uncleared token only after actual old-process termination/exclusion is proved by the approved host supervisor, or through the offline procedure. A normal killed-worker restart can use supervisor-confirmed exit and preserve accepted automatic recovery. A partition with an unverifiable live old worker remains visibly blocked. Different operations continue normally where existing content/claim constraints allow them.

This is a **proposed narrow mechanism requiring proof**, not an assertion that existing columns already provide it or that every effect belongs to an INGEST row. Map receiving, destruction, reconciliation and administrator effects to a durable existing operation/subject owner; any effect with no such owner must be covered explicitly or refused. Content-shared/conflicting operations still need the existing content and claimant rules. Do not implement a broad “owner token” abstraction without demonstrating coverage.

An alternative is a proven platform-held operation/content fence with correct process lifetime. If it is simpler in the actual implementation, choose it after the feasibility comparison. Do not silently rely on connection liveness, job expiry or new generation reservation alone to fence an already-open old writer.

### 5.2 Small installation boundary

Target **one new operational singleton table**, provisionally installation_state, rather than nine control tables. Its narrow purpose:

- installation/approved host and expected Archive ID binding;
- an ordinary compatible schema/build contract;
- READY versus OFFLINE, with reason and a unique maintenance-attempt identity;
- reference/digest of the completed offline admission report and last administrative transition.

An existing operational identity table could be extended instead, but the estimate below budgets one new table to avoid disguising complexity as columns. It is not historical/domain identity and does not change Archive ID. Retain transition attribution using existing appropriate history plus a durable administrative receipt; old/new identities must be in that receipt and atomically bound to the singleton transition. If existing history cannot safely hold the exact typed transition, budget a second small append-only event table and update the comparison. Do not claim that evidence can simply disappear because A is removed.

The runtime role reads this state and performs permitted business DML, but cannot alter the state, schema or admission contract. The offline administrator/migrator has separately controlled authority. This role separation is an owner decision and deployment change; it is not already supplied by the current configured role.

All new-build entry points fail closed before effects on schema/build/installation/Archive-ID mismatch or OFFLINE. Reconnect checks repeat the ordinary contract. Readiness/status retains the accepted unavailable-database distinction; an actual identity mismatch latches/refuses effects. This is a small contract, not P/E declarations on every pooled transaction.

### 5.3 Physical runtime/maintenance fence

Retain a **process-lifetime shared/exclusive local fence** where it supplies a real proof that stopped effect threads cannot resume after maintenance:

- all effect-producing compliant processes hold shared ownership for their effect-capable lifetime;
- maintenance takes exclusive nonblocking ownership only after the continuous old-build/service/CLI freeze;
- locked-object identity comes from the actual handle, and the protected object is not replaced during normal operation;
- blocked native I/O means the process still holds its fence; a kill request is not exit.

This is one useful part of A worth retaining. A pure PostgreSQL maintenance advisory lock cannot replace it. The fence is local, not a NAS/distributed lock. Its coverage depends on the owner's designated-host decision, or on independently excluding every authorized producer host.

A native bridge such as JNA may therefore remain justified. B/C does not promise dependency elimination at the expense of proof. Missing/replaced lease-object recovery is an **offline reprovisioning procedure with independent process exclusion**, not a normal-startup state machine with R-2L/R-3L controller adoption. If the owner instead accepts supervisor/OS evidence as the entire process-exclusion authority, the native fence might be eliminated, but that alternative requires equally strong demonstrated exclusion and is not assumed here.

### 5.4 Exclusive maintenance sequence

The following order is part of B's safety argument:

1. Select the exact installation/database/Archive ID and approved build; inventory every producer, credential and host. Record a maintenance receipt. Verify/recover a coherent pre-upgrade checkpoint when required.
2. Establish a **persistent administrative freeze**: mask/disable service auto-restart, tasks and ad hoc supported launch paths. Do not rely on a process heartbeat or an OFFLINE row that old executables do not read.
3. Close ordinary database admission under administrator control, for example dedicated runtime-role NOLOGIN and credential/access retirement as appropriate. This affects the dedicated installation role; never disable a role shared with an unrelated archive without an approved scoped alternative.
4. Stop and prove termination/exclusion of all effect-producing processes; take the native exclusive fence where selected. If a process is blocked or a host is unverifiable, refuse. Neither backend termination nor password rotation stops already-issued filesystem I/O.
5. Drain **all relevant ordinary/migration backends** using exact database/role/PID/start identity. Confirm absence and account for any prepared transactions or other retained authority; refuse unexpected privileged writers. No “unregistered therefore necessarily live installation” shortcut is used during authorized offline recovery.
6. Confirm the admission freeze and quiescence again, then enter/confirm OFFLINE in the singleton. Acquire Flyway/administrative serialization on the isolated database. No ordinary writers run during the following steps.
7. Apply only the approved SQL migration chain. Flyway history/checksums and per-migration commit are the authoritative checkpoint. Normal application compatibility is not required of this isolated administrator; the migrator validates its exact supported source/path and target. Unknown/future/hotfix histories refuse.
8. Run read-only post-migration and targeted inherited-state validation. Resolve only through accepted, specifically authorized preservation helpers and their existing durable intents; then rerun validation. No arbitrary “fix all unsafe rows” command.
9. Once validation succeeds, atomically record READY, the exact new ordinary contract/binding and report/receipt identity. Retain the freeze and exclusive authority until uncertain database outcomes are resolved.
10. Install/pin the new supported launch artifact and verify permissions/configuration; retire old access paths/credentials where needed. Reopen only the intended runtime admission and release the freeze in a documented order. Actual startup must still validate the exact build/schema contract.

NOLOGIN does not disconnect existing sessions, password changes do not revoke an already authenticated session, and a termination signal is not by itself confirmed backend exit. Those are separate steps. PostgreSQL's role and signaling facilities provide components, not the entire storage-exclusion proof. See [ALTER ROLE](https://www.postgresql.org/docs/16/sql-alterrole.html) and [server signaling](https://www.postgresql.org/docs/16/functions-admin.html).

Ordinary business-table mutation triggers do not all need a generic per-connection epoch gate if steps 2–5 genuinely exclude ordinary and old producers for the entire maintenance interval. The existing archival identity, evidence, ownership and restriction constraints remain intact. Runtime cannot reopen admission or change schema itself.

### 5.5 Interrupted maintenance and unknown COMMIT

A maintenance crash leaves the **external freeze and database admission closure in force**. OFFLINE persists once installed; before that first commit, the external/role freeze remains the fail-closed evidence. Automatic reopening after a timeout is forbidden.

A replacement administrator proves the former maintenance process/effects ended, acquires the exclusive fence, drains remaining relevant backends and reads authoritative history/state. It then either:

- continues the exact valid Flyway chain at its last committed checkpoint;
- performs a bounded specifically authorized operation-specific reconciliation; or
- restores the verified pre-upgrade checkpoint.

No generic maintenance controller generation, registered action-connection handover or publication sequence is required. Global exclusion replaces per-action concurrent authority, not durable file evidence.

For any unknown COMMIT, first establish the original backend cannot still commit; then read exact immutable keys/state. Committed results are reused, exact noncommitted results retried after fresh preconditions, inconsistent outcomes refused. This rule applies to singleton transitions, archive initialization and business operation results. Filesystem results are reconciled independently; they are never inferred solely from COMMIT.

### 5.6 Restore and binding repair

Restore is deliberately offline:

- exclude/retire the former host's access to both storage and database;
- restore a coherent database/files set into isolated credentials and disabled runtime admission;
- preserve Archive ID and all archival/Q/U/operation/history rows;
- inspect actual schema history before invoking any ordinary application;
- if at an intermediate migration checkpoint, run the compatible isolated migrator to its endpoint or restore the checkpoint; do not create a RESTORE run that must impersonate MIGRATE;
- provision the new local installation/host/fence configuration, record exact old/new operational binding and operator authority, and commit the compare-and-set while OFFLINE;
- validate volumes, schema, roles, identity and accepted preservation invariants, then set READY and reopen deliberately.

A crash after rebind simply leaves OFFLINE/freeze. On restart the exact new binding and receipt identify the completed rebind; no second rebind or supersession is needed. A crash before/after replacing a lease pathname requires repeat independent process exclusion and preservation of suspicious objects. No lock on the replacement proves absence of old inode holders.

Unknown binding commit is resolved after drain from the atomic binding/receipt state, not by assigning another Archive ID. Ordinary installation configuration may change; archival identity and portable references do not.

### 5.7 Historical integrity and admission

Use a **read-only preflight report** before enabling ordinary writers whose corrected claim/order theorem needs inherited-state assumptions. Preserve AU's necessary ownership, alias, active claimant and evidence checks, but do not attempt a universal reconstruction of historical target assignments.

Under owner-approved R39-T, accept writer-consistent historical Q/U associations with correctly scoped independent conflicts. Distinguish persisted X from private INGEST UUIDs and exact physical subjects from operation IDs. Repeated repairs and bound destruction-side U records remain valid.

Known unsafe active states remain blocked until an accepted administrator operation resolves them or a reviewed offline plan exists. Preflight failure does not authorize row rewriting, evidence upgrades, foreign claim release or unknown-byte deletion. If no authorized safe resolution exists, retain refusal and return a bounded owner question. A generic action journal is not required merely to promise automatic resolution of every possible unsafe archive.

R39-T cannot be eliminated by putting the audit offline. Option B strict historical refusal also remains possible, with its legitimate false-refusals and independent offline-authority requirements. Neither architecture can recover facts that were never recorded.

### 5.8 What B does not promise

B provides deliberate, fail-closed recovery, not uninterrupted availability during upgrades, automatic arbitrary forensic repair, instant failover or remote-host fencing by a local lock. None is an approved v1 requirement found in the inspected sources.

B cannot satisfy an added requirement for safe unattended takeover while a possibly live old producer retains storage access. Neither can an advisory lease alone. If the owner requires that, architectural selection must revisit fencing infrastructure or stronger deployment restrictions. B also cannot defend against an administrator deliberately bypassing roles, freeze and safeguards with arbitrary SQL/filesystem writes; A's configured-role declarations are not an adversarial security boundary against that actor either.

## 6. Common failure-scenario analysis

Both columns are **design analyses**, not claims of executed tests. A means corrected A, not the defective literal revision 3.9. B means the complete bounded design in §5, not just its existing-code subset.

| Scenario / invariant | Architecture A | Architecture B / recommended hybrid | Residual risk, recovery and verification |
|---|---|---|---|
| 1. Conflicting concurrent application processes; one consistent authorized result | Total order, final-strength parents, content serialization, claims and monotonic generation reservation | Retain the same ordinary corrections/constraints; multiple processes remain allowed | Neither Java guard observes every implicit lock. Use actual transactions, unique/FK/trigger paths, barriers and disabled retries; assert 40P01 explicitly and invariants at commit. |
| 2. Crash during ingestion; one identity and accountable bytes | Existing operation protocol plus admission/effect fencing | Existing operation protocol plus narrow effect-owner exclusion and current claims | Resume only after old effect cannot continue; keep staging/evidence. Re-run RecoveryIT and killed-worker tests at each boundary, not only graceful stop. |
| 3. Successful COMMIT with lost confirmation; no duplicate effect/identity | U-1…U-5 plus registered run/window/publication identities | Drain/confirm original backend, then inspect existing operation or singleton/receipt identity under offline authority as appropriate | A fresh read while old backend lives is not definitive. Inject committed and rolled-back outcomes with a live-backend barrier. |
| 4. Filesystem success before DB finalization; no adoption/deletion by guess | Generic maintenance journal for maintenance effects; accepted Q/operation evidence for ordinary ones | Accepted durable Q/operation intents and explicit role-specific reconciliation; no generic effect if no authorized recoverable helper exists | Both need actual source/destination bytes, hashes and object evidence. Test source-only, destination-only, both, absent and replaced object. |
| 5. Worker termination/restart; persistent recovery without overlap | Persistent operations/jobs and admission gates; preserve process-lifetime assumptions | Persistent operations/jobs, verified dead-owner reclaim; no time-based stealing of outstanding I/O | Job expiry/advisory disappearance is not exit. Kill test must prove process death; add session loss with old process still alive, which must not overlap. |
| 6. Migration while incompatible app active; no mixed semantics/effects | F1 exclusive, freeze, gates, controller/connection authority | Persistent freeze, native exclusive/process proof, runtime database closure and full backend drain | F1 alone misses old builds. Attempt migration with web upload, worker, CLI and blocked storage operation; it must refuse before migration effects. |
| 7. Old executable restarts after migration; no old writes | P/E/trigger gates plus old-build freeze | Exact schema/build startup refusal, protected pinned launch paths and retired old credentials/access; no compatibility fallback | Existing SchemaMigrator detects AHEAD, but verify **every old effect entry point**. If an accepted old path can act before checking, B needs a deployment-level access block for it; cannot claim startup status alone suffices. |
| 8. Restore changes installation identity; archive stays same | R-5/R-5H, exact binding event and restore run | Isolated offline restore, exact operational rebind/receipt and validation; Archive ID unchanged | Coherent snapshot and old-host exclusion are operator responsibilities in both. Test changed cluster/OID/host/path, same-identity physical restore and cross-OS. |
| 9. Intermediate migration crash; known recoverable checkpoint | Manifest-bearing MIGRATE run, checkpoint C and registered physical Flyway backends | Admission remains shut; compatible offline Flyway validates actual history/checksums and continues or restores pre-upgrade checkpoint | No whole-chain atomicity. Unknown migration COMMIT needs backend absence/history consistency. Test every committed checkpoint and wrong/hotfix build refusal. |
| 10. Repair/restore crashes after binding changes; no circular admission | R-2L/R-3L/R-5H recovery branches and generation/event reconciliation, after correction | OFFLINE remains; new binding/receipt is authoritative after outcome resolution; revalidate then READY | B still needs atomic receipt coupling and protected lease reprovisioning. Crash after rename, create, bind, validation and READY; do not treat successful rebind as validation completion. |
| 11. Orphan database sessions; no late commit | Generation-aware registered drain plus corrected authorized unregistered-orphan handling | Under offline administrative authority, close admission and drain every relevant session, not only registered ones | Wrong PID reuse avoided by PID/start comparisons. Confirm disappearance; refuse unknown privileged writer/prepared authority. Test dead-JVM live-backend and lost controller registration. |
| 12. Unreconstructable provenance; no fabricated history | RA/PC with corrected role/key equivalence and R39-T, or stricter policy | Same bounded trust policy; focused preflight, accepted evidence authorization and unresolved refusal | Neither proves prior target from absence or skipped g. Use real repeated-repair/U-without-Q positives and foreign-owner/alias negatives. |
| 13. Accidental duplicate installation; no unauthorized effects | Installation/host/database/archive/native-object binding and gates | Configured expected installation/host/archive binding, protected supported launches, runtime role/access and fail-closed startup; old access retired on host move | Full cloned credentials/config plus duplicated host IDs can defeat operational identity assumptions; require owner policy and physical host/access exclusion. A local lock is not a remote fence. Test wrong config before DB mutation/termination and wrong volume marker before write. |
| 14. Native locking/library failure; no effects without exclusion | JNA OFD/Windows handle contract, loading restrictions and installed permissions | Retain the same minimal native fence where needed; maintenance refuses on failure; no connection-lock fallback | Correct JNA fallback flags/ABI, Windows share-open semantics, common lock range and handle lifetime. Test installed Linux/Windows accounts; actual kernel-I/O stall separately from Java barriers. |

### Concrete counterexamples constraining simplification

**Database lock alone is insufficient:** W1 holds an advisory lock on connection L while a file-copy thread writes. L disappears during a network fault; PostgreSQL grants the lock to W2. If W2 takes that as proof W1 stopped, both can act on the same logical copy. A job lease, connection heartbeat or epoch check before the call does not stop an already executing filesystem write. B's outstanding effect-owner rule or a demonstrated process-lifetime operation fence must close that schedule.

**Stopping a service is insufficient:** the accepted WorkerProcess.stop waits ten seconds for its thread, then clears its local reference. A blocked thread can outlive that wait. Maintenance that interprets the return/timeout as exit could migrate or restore while bytes still change. Both architectures require actual exclusion, not “service stop requested.”

**Offline alone is not provenance:** R1 and R2 can legitimately repair persisted X at different generations, producing Q/U rows with the same content UUID. A literal “UUID occurs in another operation” conflict test falsely refuses a coherent archive regardless of whether the test runs online or offline. B must retain the eleventh assessment's exact-key/known-subject correction.

**Old build still matters:** an OFFLINE flag added by a new migration is invisible to an older executable. It cannot be the legacy transition fence. The supported launch/access freeze and existing old-build AHEAD rejection must be demonstrated together before B removes broad per-table gates.

## 7. Complexity comparison

Counts are deliberately separated into **verified proposal/baseline counts** and **design estimates**. Smaller counts are not themselves a safety proof.

| Criterion | A — corrected comprehensive protocol | B — minimal safe alternative / C hybrid |
|---|---|---|
| Approved requirement coverage | Potentially complete after unresolved corrections and owner choices; not implemented | Design coverage shown in §§3/6; conditional on narrow fencing and operational choices, not yet executable proof |
| Persistent control tables | **9 proposed**, beyond the current 40 application tables | **Target 1 new operational table; possible 2 with typed transition history**. Existing operation-owner columns may be needed. Estimate, not implemented DDL |
| Global trigger coverage | **49 proposed tables** for generic admission, in addition to archival triggers | No generic 49-table admission layer; retain all existing archival/security constraints and narrowly protect operational metadata |
| Control units | **36 proposed CU-0…CU-35** | No generic CU framework. Approximately six administrative stages: freeze/exclude; drain; migrate; validate/resolve; READY/rebind; reopen. Stages are not transaction counts |
| Business changes | **19 P2-8 / 22 Phase 2 windows**, plus Phase 3/4 corrections | Many or all demonstrated ordinary windows remain; no defensible numerical reduction claimed before per-window replacement proof |
| Coordination mechanisms | Native F1, controller advisory, run/generation authority, registrations, P/E gates, content/operation coordination, journal/window/publication identities | Native process fence where necessary; existing operation/content coordination; narrow durable effect-owner boundary; isolated administrative/Flyway serialization |
| Recovery states/transitions | Run states × controller g × registered connections × migration C/P/E × action/window/publication × old/new bindings | READY/OFFLINE plus persistent external freeze; actual Flyway checkpoint; existing operation/Q/U states; owner uncertainty blocks takeover. Fewer global cross-products |
| Dependencies | New native bridge/package and supported OS ABI contract | Native bridge may remain. No new broker/service; no promised elimination merely for aesthetics |
| Operational restrictions | Designated host and continuous old-build freeze already requested; elevated maintenance; journal rules | At least as deliberate an offline freeze; stronger reliance on separately controlled database/host administrative procedure; manual recovery when uncertainty persists |
| Windows/Linux portability | Native handles/OFD plus Java/domain portability | Same necessary native boundary; fewer platform-specific controller/rebind paths; existing service/account abstractions retained |
| Crash recovery | Generic autonomous controller/action reconstruction, requiring many special contracts | Operation-specific ordinary recovery; offline drain + checkpoint continuation/restore; no automatic reopening |
| Historical trust | Explicit R39-T or strict offline alternative; corrected classifier required | Same information limit and owner policy; no new retrospective target ledger |
| Implementation effort | High: new schema/functions/gates and coverage plus ordinary fixes | Lower expected global-framework work, but real role/launcher/fence/owner work remains. Qualitative estimate |
| Regression burden | Ordinary matrix plus controller/run/window/publication/generation/exception combinations | Ordinary matrix retained; offline/identity/native/owner handover matrix still substantial; generic controller/publication combinations removed |
| Maintainability | One uniform model, but changes to migration/restore interact with normal admission | Clear ordinary-domain versus administration boundary; procedural correctness and operational documentation become critical |
| Residual risk | Missed writer/gate; stale-controller assumption; false historical refusal; native coverage; operator premises | Missed producer/launch path; insufficient offline authority; owner-token coverage; operator error; native/host assumptions |
| Availability | Can support more resumable automated maintenance after implementation | Planned downtime and visible refusal/manual action are intentional; no online upgrade promise |

**Why the reduction is real:** B removes whole concurrency relationships. There is no action connection continuing under a dead controller while another controller is authorized; a replacement first excludes the former process and drains sessions. There is no ordinary writer admitted at an intermediate schema checkpoint; only the isolated migrator operates there. There is no publication-allocation sequence keeping a generic maintenance run open; reopening depends on one validated READY transition under freeze.

**Why the reduction is not free:** B increases the importance of an executable maintenance runbook and separately controlled administrative access. It may have more downtime and require deliberate intervention after ambiguous failures. These are acceptable only as explicit owner choices, not hidden assumptions.

## 8. Mechanisms to retain, eliminate or simplify

| Mechanism | Recommendation | Requirement/failure rationale |
|---|---|---|
| Correct ordinary total order and content-first windows | **Retain** | Demonstrated deadlock/unique/FK/trigger failures; approved LO requirement |
| Generation high water, unresolved reoccupation exclusion, scoped claimant release | **Retain** | F5-1…F5-3 and accepted byte-accountability requirements |
| Accepted O/Q/U/owner ledgers and object evidence | **Retain** | TX/IV/P2-I; PostgreSQL alone cannot transact filesystem effects |
| Process-lifetime maintenance exclusion | **Retain a minimal form** | Live I/O can survive loss of database connection; maintenance must not overlap |
| Native descriptor/handle identity where native fence selected | **Retain only for that boundary** | Avoid locking one object and identifying another; not a general native filesystem rewrite |
| Per-connection P/E admission on every database writer | **Remove from replacement target if offline exclusion is proved** | Defends concurrent maintenance/ordinary activity that B disallows through independent isolation; cannot remove before launch/access proof |
| maintenance_run/controller-generation/connection registry | **Remove generic framework** | Offline replacement drains all relevant sessions before work, rather than handing authority over live action backends |
| Generic action/window journal | **Remove for unneeded arbitrary maintenance operations** | Existing operation-specific intents remain authoritative. Unsupported repair stays refused until a bounded reviewed plan exists |
| Publication allocation and sequence state machine | **Replace with atomic validated READY transition under freeze** | Generic publication identity is introduced by A's maintenance model, not an archival requirement |
| Migration P1→P2 run compatibility | **Simplify to isolated migrator source/history/target validation** | Keep version/checksum/path validation; remove coupling to ordinary run admission and controller registration |
| installation_binding_event ninth-table representation | **Simplify, do not erase evidence** | Exact old/new operational transition remains durable via narrow typed history/receipt; if one existing mechanism cannot provide it, retain a small second table |
| Universal historical target-assignment proof | **Reject in either architecture** | Required facts were never recorded; R39-T or strict refusal is an owner policy |
| Audit of active unsafe claims and aliases | **Retain focused preflight** | New ordinary theorem cannot assume unsafe inherited state away |
| Automatic lease repair / restored-run supersession | **Replace with offline reprovision/rebind** | Rare disaster path need not carry ordinary controller state through broken identity |
| Continuous old-build freeze | **Retain** | Old binaries do not participate in new admission mechanisms |
| General protection against malicious root/DB owner forging data | **Do not introduce as an unapproved v1 requirement** | Both designs rely on bounded operator/producer trust; preserve approved P2-E limits |

A mutation gate is not redundant merely because PostgreSQL supplies ACID. It is redundant only if another demonstrated mechanism excludes the incompatible writer it was meant to stop. Similarly, an existing operation ledger does not replace a journal for an arbitrary new effect it cannot represent. B avoids offering that arbitrary effect until its recovery contract is separately authorized.

## 9. Technical uncertainties and verification boundaries

The following are not hidden owner decisions and cannot be settled by prose approval:

1. **Complete old executable admission coverage.** The current SchemaMigrator/worker contract rejects future schema; verify every web, receiving, CLI and recovery entry before effects. Test authentic old artifacts. If a path can mutate first, strengthen the deployment access fence before removing generic gates.
2. **Outstanding effect-owner coverage.** Demonstrate that one proposed owner token or equivalent platform fence covers every potentially continuing filesystem effect, including a dedicated advisory connection dying while the JVM lives. Do not use job lease expiry as proof. Determine whether existing operation/subject rows suffice without a new generic owner table.
3. **Native fence implementation.** JNA boot path alone does not prohibit fallback/extraction. Correct flags, native binary/ABI matching, common range/namespace, OFD ownership, Windows sharing-open behavior and handle lifetime require installed tests. See the primary [JNA 5.14.0 loader source](https://raw.githubusercontent.com/java-native-access/jna/5.14.0/src/com/sun/jna/Native.java), [Linux OFD semantics](https://man7.org/linux/man-pages/man2/fcntl_locking.2.html), [CreateFileW](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilew) and [LockFileEx](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-lockfileex).
4. **Least-privilege role split.** Current code/deployment was not built around a separate offline authority. Map required trigger/function DML privileges and dedicated runtime role admission; do not grant runtime DDL/superuser for convenience. PostgreSQL advisory locks are cooperative, not an access-control boundary ([locking documentation](https://www.postgresql.org/docs/16/explicit-locking.html)).
5. **Topology/inventory.** No evidence establishes a universal one-host or one-worker deployment restriction. A local lease is insufficient for multiple producer hosts. Enumerate and obtain the hosting decision.
6. **Native blocked-I/O proof.** A Java latch test establishes Java-level lifetime only; use separate Linux/Windows kernel/storage characterization, and refuse when exclusion cannot be established.
7. **Historical admission exactness.** Real repeated repair and cross-operation known-object fixtures must pass. A manual writer-shaped row remains information-theoretically indistinguishable; that is R39-T, not a missing query.
8. **Receipt atomicity and durability.** Define typed old/new state, immutable receipt identity and commit ordering for rebind/READY. Do not replace A's database evidence with a best-effort log line.
9. **NAS fallback evidence.** Preserve the documented limitation that workgroup NAS accounts are not CI-proven. Do not label local filesystem tests as proof of every NAS atomicity/permission behavior.
10. **Phase scope.** Do not implement the Phase 8 full backup/restore product inside WP1. Provide the required safe upgrade/recovery boundary and documented coherent checkpoint procedure; retain later full BK acceptance obligations.

These uncertainties prevent a READY gate, but they are a much smaller investigation than completing every A controller/action exception. The comparison makes their cost visible rather than claiming a shorter design is already proven.

## 10. Owner decision matrix

No decision below is approved by this report. “Blocks selection” means it affects whether the recommended architecture is an appropriate product/operational choice; “before implementation” means the direction can be chosen while its exact implementation remains pending.

| Decision | Options and safety/operational consequences | Recommendation | Timing |
|---|---|---|---|
| Hosting / Q-36-1 | Designated producer host with multiple processes; or explicitly inventoried multiple producer hosts requiring verified global offline exclusion. Local locks cannot fence the latter remotely | Approve designated host for v1 if suitable; do not assume one JVM/worker. Remote PostgreSQL and NAS may remain | **Blocks selection boundary** |
| Maintenance availability/automation | Planned exclusive downtime with manual ambiguous recovery; or unattended concurrent/general resumable maintenance | Choose planned exclusive downtime. No approved source requires the more expensive option | **Blocks selection** |
| Separate offline database authority | DBA/admin path can close dedicated runtime admission and drain; or configured runtime role must perform everything | Prefer narrow separate offline authority; runtime stays least privilege. No unrelated-role termination without DBA authorization | **Blocks B/C feasibility** |
| Continuous legacy freeze, D-LP-8(xi) | Enforce all supported launch paths/access during upgrade/recovery; or require old binaries to be fenced without operational control | Retain continuous freeze; retire obsolete credentials/artifacts and verify authentic old startup refusal | **Blocks selection safety premise** |
| Historical R39-T / D-LP-8(xii) | Trust accepted inherited writers plus independent conflict checks; or strict off-current active-history refusal with independent resolution | Recommend bounded R39-T and refusal of UNPROVED/conflicting records; no retrospective ledger | Before admission policy implementation |
| Native dependency | JNA/native bridge on Java 21; small reviewed JNI equivalent; approved JDK upgrade; or equally strong supervisor-only exclusion | Retain minimal proven native fence provisionally; review maintained pin/loading. Do not force JDK upgrade or waive exclusion | Before fencing implementation |
| Fatal runtime mismatch | Latch/stop effects on actual identity mismatch; ordinary unavailability stays diagnostic; safe blocked-I/O shutdown | Retain distinction and fail closed; never release fence before effects end | Before implementation |
| Disaster restore authority | Deliberate isolated rebind preserving Archive ID; or automatic host adoption | Prefer deliberate offline authority with coherent restore and old-host exclusion | Direction decision with architecture |
| Unverified former owner/host | Remain visibly blocked/manual recovery; or assume timeout/lease means stopped | Require proof or refusal. No timeout-based stealing | **Blocks safe recovery contract** |
| Typed binding history | Existing suitable immutable history plus exact durable receipt; or small dedicated transition table | Choose simplest representation that proves atomic old/new transition; budget up to two operational tables honestly | Before DDL |
| Strict historical alternative | Permit independent fresh authorization/verified absence; or leave unresolved states refused | If R39-T rejected, approve explicit limits and false-refusal cost. No waiver | Before admission policy |
| D-LP-9(a) | Separately bounded administrator coordination; or couple to global redesign | Remains eligible for separate authorization. This assessment starts no implementation | Independently deferrable |

The selection is not “owner approves nine tables versus one table.” It is a decision about the supported operational boundary and recovery automation. Table count follows that decision.

## 11. Architectural recommendation and governance gate

**Choose C — hybrid**, with Architecture B's exclusive maintenance/restore boundary and A's demonstrated ordinary safety corrections.

A pure return to accepted Phase 0–3 code is not adequate: the File/Record cycle and preservation ordering/claim problems are source-supported, and LO remains a Phase 4 obligation. A purely administrative “please stop things” alternative is also inadequate without verified process exclusion, durable closed admission, backend drain and old-launch proof.

Retain the narrow safety mechanisms whose removal has a concrete counterexample. Remove the general controller/action/publication machinery unless a specific approved requirement cannot be satisfied without it. Retain operation-specific durable intent and evidence; do not replace it with a maintenance log.

The engineering-governance questions have explicit answers:

1. **Does A satisfy approved requirements?** Not yet; it could after substantive corrections, but no implementation demonstrates that result.
2. **Are all A mechanisms necessary?** No. Its generic resumable maintenance framework exceeds the automation required by the inspected specifications.
3. **Is a materially simpler alternative available?** Yes, the isolated offline boundary in §5 with the retained ordinary/fencing protections and stated decisions.
4. **Have corrections added disproportionate complexity?** Yes: schema/protocol compatibility, controller registration, publication and identity repair now interact across ordinary and disaster paths.
5. **Is the architecture converging?** Not sufficiently; three successive reviews found new structural interactions despite closing individual findings.
6. **Should incremental revision continue?** No routine revision 3.10 effort. Proceed with bounded simplification comparison review and owner selection.

**Gate recommendation: SIMPLIFY.** The mandated RECONSIDER assessment has produced a preferred direction. This is not a change to governing requirements, an owner selection, acceptance or implementation authority. Until the owner acts, the recorded project gate remains RECONSIDER and implementation remains unauthorized.

## 12. Proposed next engineering step and completion boundary

### 12.1 Bounded next step

First have ChatGPT independently evaluate **this assessment**, then present the architecture choice and blocking decisions to the owner, as assigned. Do not initiate a Claude Code redesign.

If the owner selects C, the next engineering artifact should be a short replacement boundary contract, not an expanded comprehensive proposal. It must contain:

- the exact approved producer-host/launch inventory and supported administrative authority;
- the ordinary corrections retained by ID/window, with no unsupported claim that all 22 windows disappear;
- a two-state operational admission schema and the exact typed receipt/history representation;
- the freeze→exclude→drain→migrate→validate→READY→reopen sequence and failure exits;
- the narrow effect-owner lifetime/reclaim rule or simpler proven equivalent;
- historical admission policy and exact known-object equivalence;
- a requirements/test mapping without generic controller/publication state.

Before full implementation authorization, require two bounded **non-production feasibility proofs**, separately authorized:

1. **Exclusive boundary proof:** authentic accepted old build, active receiver/worker, lost controller response, live orphan PostgreSQL backend, intermediate migration checkpoint and a post-rebind crash. Show no migration/restore effect until exclusion; show deliberate recovery and exact identity preservation.
2. **Effect-owner proof:** lose the advisory backend while the old worker remains blocked in an effect, then start another worker. Show no overlapping takeover; after actual old-process termination, show accepted idempotent recovery to one result. Run both supported host platforms and PostgreSQL 16/18 as applicable.

These proofs may show that the candidate needs a small revision or that a retained A mechanism is necessary. If either proof demands another generic multi-state framework, return to the owner with the exact failed requirement and complexity comparison. Do not let it restart an unbounded correction cycle.

Then, and only after explicit selection/authorization, implement the selected design, execute the full accepted Phase 0–3/Phase 4 regression matrix and obtain independent implementation review. No architecture-only report closes implementation findings.

### 12.2 Acceptance preservation and issue status

Preserve immutable originals and hashes, distinct File identity for deliberate ingestion, shared-content protection, evidence-bound attestation/release, no automatic adoption/deletion of unknown objects, restriction inheritance/non-disclosure, original attribution/events/timestamps, grouping order/boundaries/continuation, optimistic conflicts and permanent Archive ID.

F5-1…F5-3 remain architecturally resolved and must survive any replacement. F5-4, WP1-R1 and GAS-P4-002…005 remain OPEN pending appropriate selected-design implementation and evidence. The eleventh assessment is preserved as input; it is not edited or made to disappear by replacing the architecture.

### 12.3 Completion statement

- Recommended architecture: **C — hybrid with explicit offline maintenance and recovery**.
- Current proposal contains unnecessary complexity: **yes**, principally its generic maintenance/controller/action/publication coupling.
- Simplified alternative satisfies all approved requirements: **design coverage is achievable under the stated decisions and proof obligations; it is not yet demonstrated**. No approved requirement requiring A's extra automation was found. Pure DB-lock-only simplification would fail.
- Blocking decisions: hosting/producer boundary, planned offline versus unattended recovery, separate administrative authority, continuous legacy freeze and proof-based handling of unverifiable old owners.
- Authorized changed path: **docs/PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md** only.
- This is an assessment commit; commit identity is reported in the completion message rather than embedded recursively in this document.
- No application code, tests, migrations, existing proposal, accepted specifications or owner decisions are changed.
- No WP1 implementation, migration execution, merge, tag, WP2 start or Claude Code redesign is initiated.

**WP1 remains unaccepted. WP2 remains HOLD.**
