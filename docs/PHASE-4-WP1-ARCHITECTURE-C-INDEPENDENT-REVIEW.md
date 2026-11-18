# Phase 4 WP1 Architecture C — Independent Gate 2 Review

**Review date:** 18 November 2026\
**Repository / branch:** Rebowitz/GenealogyArchiveSystem / phase-4\
**Starting review HEAD and target:** 55041be3de384233123c7993827ce75ee0c87823\
**Contract:** docs/PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md, DRAFT, 919 lines\
**Authority:** Gate 2 independent architectural review only; D4-31 C-01…C-12\
**Overall verdict:** **ACCEPT WITH SPECIFIED CORRECTIONS**

## 1. Executive verdict

Architecture C remains the appropriate approved direction. The replacement draft substantially removes the comprehensive controller/action/publication framework and supplies a credible, testable offline boundary. The evidence does **not** justify reconsidering its core approach.

A finite set of corrections is required before contract acceptance. The main gaps are the transaction boundary of durable effect ownership and the bootstrap path for a genuinely new archive. Receiving timeout policy should be narrowed: a timeout can cancel an upload logically without authorizing another physical writer or cleanup. Historical checks must preserve durable intent authority and distinguish an exact physical subject from a content-wide relationship.

**Findings: 2 BLOCKING, 3 MAJOR, 2 MINOR, 3 FEASIBILITY-DEPENDENT.** These are consolidated finding groups, not counts of every sentence needing adjustment. Details and acceptance criteria are in §§5–16.

The Java fence, process-start evidence and NAS behavior are feasibility questions. They are not proven by the draft, but neither is choosing FileChannel over JNA, or a named non-superuser administrative role over a DBA, by itself a reason to reject Architecture C. Do not turn those implementation choices into demands for the old generic machinery.

Only OD-7 needs an owner policy choice for the current refusal boundary, unless the contract adopts a conservative global-first rule and explicitly defers scoped admission. OD-9 requires an owner decision **if** a user-visible behavior change is retained; the narrower correction recommended here preserves logical timeout abandonment and does not require treating all time-based transitions as forbidden. Other unresolved choices must be settled at the appropriate proof/implementation gate, not automatically before architectural acceptance.

**Recommended next gate: CONTINUE with the finite contract corrections in §16.** After those corrections and the necessary policy disposition, request separate authorization for the two bounded feasibility proofs. This is not READY FOR IMPLEMENTATION REVIEW. The Architecture C contract remains DRAFT and unaccepted; WP1 implementation remains unauthorized; WP2 remains HOLD.

## 2. Verified repository baseline and review method

### 2.1 Baseline verification

The earlier Gate 2 attempt stopped because the target commit was absent. On this resumed attempt:

- origin/phase-4 fetched successfully and resolved to **55041be3de384233123c7993827ce75ee0c87823**;
- that commit exists and adds only the boundary-contract document;
- the clean local phase-4 checkout was advanced to that exact target by fast-forward, with no merge commit, rebase or history rewrite;
- the target contract is present and was read in full;
- the working tree was clean before the review document was created.

The fetched sequence is dd31e6c → b5748de → fad2b44 → 55041be: simplification assessment, D4-31 approval, governance reconciliation, then the draft contract. Those fetched upstream documentation changes are baseline material, not edits made by this review.

The source implementation is unchanged from the baseline examined in the simplification assessment. No feasibility experiment, database migration, application test, CI job or implementation was run in this review.

### 2.2 Governing and historical material

Read: AGENTS.md; CLAUDE.md; docs/ENGINEERING-DESIGN-INSTRUCTIONS.md; D4-31 in PHASE-4-DECISIONS.md; the full PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md; the full replacement contract; revision 3.9 and the relevant audit/correction history, including the eleventh assessment.

D4-31 approves the operating rules, **not** any particular token, singleton, fence library or receipt representation. C-05 hosting, C-08 continuous legacy exclusion and C-10 R39-T are now approved rules; they are no longer open architecture-selection questions. D-LP-3 and D-LP-9(a) are not approved merely because D4-31 selected C.

The accepted requirements used include BUILD-SPEC §§7–8, 35–36, 43–50, 55–56 and 67–69; SEC-004/007/008; TX-001…008; IV requirements; OP-001/004; UP-004…010; P2-I/V0020; the Phase 3 grouping safeguards; D4-16/D4-27; and PR4-R3/LO-1…007.

### 2.3 Source evidence key

All repository code references below are at **55041be3de384233123c7993827ce75ee0c87823**. Contract line numbers refer to the same fixed commit.

| Key | Actual source inspected / material evidence |
|---|---|
| S1 | preservation/IngestionService.java:59–80 independent receiver renewal; 173–216 operation creation; 223–294 streaming, force, STAGED CAS and failed-CAS cleanup; 307–320 abandonment/cleanup |
| S2 | data/FileOperationRepository.java:198–209 progress; 219–255 transactional state/event CAS; 277–295 cleanup flag/receiver renewal; 307–330 stale receiving CAS/event |
| S3 | preservation/RecoveryService.java:42–83 recovery ordering; 86–100 staged-file cleanup; files/StagingArea.java:80–83 exclusive create and 102–115 delete |
| S4 | preservation/OperationProcessor.java, OperationCopies.java, OperationAdminService.java, DestructionProcessor.java and data/OperationLocks.java: accepted operation/claim/evidence/advisory coordination |
| S5 | preservation/QuarantineMoves.java:167–254 source/destination reconciliation, stored evidence and attestation, rename and final database settlement; UnresolvedObjects.java: accepted attestation/release paths |
| S6 | preservation/ContentIo.java:58–83 target force; platform/SafeFiles.java:81–92 directory flush is deliberately a no-op on Windows |
| S7 | preservation/VolumePreservationService.java:48–77 marker creation then database finalization, 146–181 write probe; files/VolumeMarker.java:52–65 exclusive write/force; files/ManagedVolume.java:85–90 marker then directories |
| S8 | archive/ArchiveService.java:58–73 initialize; data/ArchiveInstanceRepository.java:33–40 initialization lock/insert; V0002__archive_instance.sql:38–57 singleton/identity constraints; cli/Cli.java:262–275 init and 279–320 volume commands |
| S9 | records/GroupingService.java:146–195 advisory run lifecycle and 355–440 chunk; data/GroupingRunRepository.java:89–104 chunk/counters and 120–134 finish/stale-run handling |
| S10 | migration/SchemaMigrator.java and PostMigrationValidator; service/SystemStatusService.java; boot/ProcessLauncher.java; worker/WorkerProcess.java; config/InstallationConfigLoader.java and InstallationConfig.java |
| S11 | V0004__change_history.sql:7–29; history/ChangeRecorder.java:11–14 and 29–36: existing history already supports same-transaction insertion, but not the proposed receipt's role/shape policy by default |
| S12 | deploy/linux/install.sh and systemd units; deploy/windows/install-services.ps1/service configuration and volume ACL scripts; ADR-0002/0005/0009/0010 |
| T1 | RecoveryIT:256–338; Phase2AuditCorrectionsIT receiver regressions; FileWebIT live-request timeout tests; FileSettingsConfigTest; WorkerProcessKillIT; IntegrityRepairIT; RepairLockPoolIT |
| T2 | GroupingIT; LockSequenceTest/IT; Lp6LockBehaviourIT; MigrationFailureIT; migration upgrade tests; installed-service tests |

These are source inspections, not new pass results. Where the contract deliberately labels a future inventory or prototype unproved, this report does not recast that omission as an executed failure.

## 3. D4-31 compliance matrix

Classifications concern the **draft contract**, not implementation acceptance.

| Rule | Classification | Independent assessment |
|---|---|---|
| C-01 Preserve accepted functionality | **PARTIALLY SATISFIED** | Ordinary invariants are retained, but fresh bootstrap is circular as written (B2), and OD-9 unnecessarily proposes changing abandonment instead of separating physical cleanup (M1). |
| C-02 Retain necessary corrections | **SATISFIED** | §§4.1–4.4 retain the ordinary order, all 19 P2-8 / 22 Phase 2 windows and F5-1…F5-3. Actual implementation and LO/LP evidence remain future obligations. |
| C-03 Remove unnecessary machinery | **SATISFIED** | One/two narrow tables, token columns and a local fence replace the controller, registry, epochs and generic journal. No evidence requires reinstating them. |
| C-04 Exclusive offline maintenance | **PARTIALLY SATISFIED** | M-1…M-6 / X-1…X-4 form a credible boundary. Bootstrap and crash-suffix wording require correction; installed exclusion remains Proof 1. |
| C-05 Designated producer host | **SATISFIED** | One host, multiple JVMs, remote PostgreSQL/NAS and no distributed-lock claim are explicit. Host-ID representation is deferred, not reopened hosting policy. |
| C-06 Separate authority | **REQUIRES FEASIBILITY PROOF** | Runtime/admin rights are separated. Exact principal and grants require a privilege inventory; “owns runtime role” needs precise PostgreSQL terminology. A specific role name is not a contract blocker. |
| C-07 Former-writer exclusion | **PARTIALLY SATISFIED** | Non-expiring ownership plus verified exit is credible, but B1 leaves acquisition/unknown COMMIT and all effect-dependent database transactions insufficiently specified. P1–P3 remain feasibility hypotheses. |
| C-08 Legacy exclusion | **REQUIRES FEASIBILITY PROOF** | Continuous freeze, credential retirement, backend drain and pinned artifacts address GAS-P4-005 in principle. Every authentic legacy path must be tested; AHEAD alone is insufficient. |
| C-09 Fail closed | **PARTIALLY SATISFIED** | Refusal is explicit. OD-9 conflates logical abandonment with takeover, and historical refusal precedence/scope needs a bounded policy (M1/M2; OD-7). |
| C-10 R39-T | **PARTIALLY SATISFIED** | Trust premise is correctly approved and bounded; repeated repairs are explicitly valid. Some historical equivalence/authority wording remains overbroad or incomplete (M2). |
| C-11 Permanent identity | **PARTIALLY SATISFIED** | Rebind preserves Archive ID, but bootstrap and exact receipt identity/atomic transition evidence need B2/M3. |
| C-12 Phase boundaries | **SATISFIED** | No general disaster-recovery controller or Phase 8 product is introduced; proofs and implementation are separately gated. |

No rule is classified NOT SATISFIED at the architectural-direction level. The bounded gaps do not require replacing Architecture C.

## 4. Ordinary-operation preservation

The retained total order is explicit and references the prior writer, trigger and conflict-key inventories. File-before-Record, final strength first, ordered UUID acquisition, content coordination, generation high water, unresolved-location exclusion and current-claim revalidation remain necessary. Removing maintenance machinery does not remove their justification.

The draft correctly claims **no reduction** in the 19 P2-8 / 22 Phase 2 business windows. F5-1 generation reservation, F5-2 G10 classes and F5-3 observed-object revalidation are preserved, not reopened. Restricted-information, grouping order/boundaries, creation-only classification dependencies, evidence-bound destruction and accepted history remain mandatory.

B-9 is a useful narrow addition for grouping, but part of its source question can already be answered. S9 shows that applyChunk locks the session, verifies rows unapplied/version-matched, and revalidates Files/Record/classification. It does **not** check that its run is still current/RUNNING before applying archival changes. recordChunk inserts a chunk row, then updates counters only WHERE state='RUNNING'; it ignores a zero-row counter update.

A plausible lost-advisory schedule is: runner A loses its separate advisory session; B closes A's run and starts a new one; A resumes a late chunk whose rows are still unapplied. Current row checks alone do not make A a current runner. This is not a new BLOCKING contract finding because **B-9 already requires the missing run revalidation** and labels implementation unproved. Amend traceability to say the source lacks that check, and test the late-runner case. Do not introduce a global maintenance registry for it.

D-LP-9(a) remains separate. An app_user administrator-check cycle is not solved by D-LP-3's three-attempt closure conflict outcome. OD-1 may block final ordinary-order implementation coverage; it does not authorize that work in this review.

The new token will touch more runtime paths than an operation state enum transition alone: Q/U settlement, claim release, generation retirement, observation-dependent findings, receiver cleanup and destruction completion need coverage. B1 specifies how to close the transaction boundary without a new controller.

## 5. Former-writer exclusion

### 5.1 Credible parts

The draft correctly distinguishes a process instance from its PID, a job lease from physical ownership, and a vanished database session from a vanished JVM. It permits many JVMs on one host, keeps tokens non-expiring, and refuses an unknown owner rather than guessing. An in-memory active-interval set is a useful local check but cannot replace durable token ownership or quiescence of other threads.

Verified old PID/start disappearance, or a different verified process-start identity at the reused PID, is a credible local exclusion candidate. Rejecting an unreadable identity is correct. No timer or service-manager acknowledgement is sufficient.

The maintenance fence and operation token are not redundant: the token serializes an outstanding subject effect against another ordinary process; the shared/exclusive fence excludes the installation's compatible processes during maintenance, including idle ones. Neither fences an unsupported second host.

### 5.2 BLOCKING B1 — make token durability and transaction fencing explicit

**Contract:** §§5.1, 5.4–5.6, §7.4; lines 221–284 and 329–340.\
**Sources:** S2 transactional transition/event code; S4 separate advisory connection; S5 settlement transaction; D4-31 C-02/C-07/C-09.

**Concrete schedule:** W1 acquires a token UPDATE inside a still-uncommitted transaction, then starts I/O. Its advisory session is lost. If its token transaction later rolls back or its backend dies, W2 sees null and acquires ownership while W1's filesystem call continues. “One CAS statement before the first effect” is insufficient unless its **durable committed outcome is known before the effect**.

There is a second boundary to define. The draft guards “every operation state transition” with interval ID. Q/U/claim/generation transactions such as S5 are not merely a state transition of file_operation. W1 can die while one of its database backends can still finish. Reclaiming its process token must not permit an old effect-dependent transaction to commit outside the ownership test. Process death does not itself prove backend death.

**Violated boundary:** INV-4 and C-07; the five fields are intended to be the durable authority, not an uncommitted intention.

**Smallest sufficient correction:**

- Acquisition and reclaim commit in an explicit, bounded transaction **before** any effect is issued. No enclosing transaction can defer that durability. An unknown acquisition outcome permits no first effect until ownership is authoritatively resolved.
- Define one interval lifecycle covering all its threads and outstanding database work, not just return from one filesystem call.
- Every **effect-dependent mutation transaction**, including Q/U/claim/generation/finding updates where applicable, validates and serializes against the same persisted interval in that transaction, under the retained lock order. A stale/zero-row ownership check causes full rollback, not a partially accepted result.
- Token release occurs only after I/O is quiescent under the supported storage contract and all effect-dependent transactions have known outcomes. An unknown release does not authorize the former owner to issue more effects.
- Reclaim CAS uses the exact old interval. Document why an old backend either completes before the replacement, is rolled back as stale, or is explicitly confirmed unable to commit. No new per-connection registry is required if transaction-local token serialization supplies that proof.
- Explicitly identify owner-independent logical cancellation, if retained under M1, so it cannot clear ownership or authorize physical work.

**Owner decision:** none needed to require these safety rules; they follow C-07. The precise SQL/API is implementation work. Acceptance criterion is an unambiguous acquire→commit→effect→resolve→release trace, including unknown COMMIT and an old live backend.

### 5.3 FEASIBILITY-DEPENDENT P1 — local process identity

**Hypothesis:** Java 21 can provide sufficient PID/start identity under the actual web/worker accounts to distinguish the recorded writer from PID reuse, or fail closed when it cannot.

Java's ProcessHandle documentation warns that process identity/liveness changes asynchronously, and Info attributes depend on OS privileges and may be absent. These APIs are a credible starting point, not universal proof. Preserve the original handle/snapshot appropriately and distinguish an empty “no such process” result from an access/query error. Compare start-time precision after database storage/round-trip; timestamptz conversion must not turn a still-live writer into a different instance.

An absent PID at a reliable observation proves that recorded process ended; a subsequently reused PID does not resurrect it. A random instance UUID is useful attribution but cannot independently be read from an unrelated OS process. Do not claim otherwise.

[Java 21 ProcessHandle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.html), [ProcessHandle.Info](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.Info.html).

Test same/different start, unavailable start, permission error, PID reuse and database precision under both actual service identities. Proof failure means refusal or a separately reviewed equivalent, not automatic takeover.

### 5.4 FEASIBILITY-DEPENDENT P2 — Java process-lifetime fence

**Hypothesis:** a dependency-free FileChannel fence can preserve the same protected-object, JVM-lifetime exclusion on the supported local Windows/Linux filesystems.

Java documents that a lock can end on release, channel close or JVM termination; shared requests may become exclusive on some platforms; locks are JVM-wide, not intra-JVM thread locks; and closing another channel can release locks on the same file on some systems. The draft's “nothing else opens the path” rule addresses a real caveat, rather than being obviously unsound. [Java 21 FileLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html).

Proof/implementation must retain a strong reference to the unique channel/lock, prohibit cleanup/shutdown release before all effect-capable threads end, check shared behavior, fix the common range and local protected pathname, handle partial startup, and refuse missing/replaced objects. Shared-lock fallback must not silently serialize or exclude all other supported JVMs.

Protected parent permissions and verified offline reprovisioning can justify a path-based object contract; JNA same-handle identity is an alternative if that proof fails. Do not mandate JNA merely because it is stronger in an unapproved threat model. OD-3's library choice need not block correction of the contract's safety invariant.

### 5.5 FEASIBILITY-DEPENDENT P3 — blocked I/O and NAS completion

**Hypothesis:** after the chosen quiescence/termination and storage completion conditions, an old client cannot later change bytes or namespace state relied on by a new owner.

The draft labels NAS late writeback unproved and includes a falsifiable failure condition. Retain that honesty. A local process being gone rules out future calls by that process; it does not automatically prove all remote accepted requests/writeback are durably settled. Rehashing now cannot prove a later delayed write or rename will not happen.

S6 forces copied file content. It does **not** establish a universal “all file and directory entries flushed on every platform” guarantee: SafeFiles.flushDirectory is deliberately a no-op on Windows. Java also limits its force guarantee for non-local storage. [Java 21 FileChannel.force](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)).

Amend §5.5's source claim accordingly. Define the required per-effect storage outcome and refused/uncertain case; do not silently require a new native filesystem durability subsystem or claim the accepted implementation already provides it. Proof 2 must characterize data **and namespace operations**, including stalled writes and renames and actual client/server reconnection after termination. If quiescence cannot be proved for a topology, conflicting takeover remains blocked pending verified storage/access resolution. NAS support is not globally withdrawn, but a test on one share is not universal certification.

This is a feasibility dependency, not evidence that C fundamentally fails.

## 6. Offline maintenance boundary

### 6.1 Positive assessment

M-1…M-6 and X-1…X-4 preserve the crucial order: establish continuous launch exclusion, block new ordinary admission, prove producers cannot continue, drain old database authority, then mutate under OFFLINE and reopen only after validation.

NOLOGIN and credential retirement cannot stop existing sessions; database termination cannot stop filesystem I/O. The draft explicitly separates those facts. M-5's inclusion of orphan sessions and prepared transactions is materially simpler and more complete than registered-backend-only handover.

Administrative operations are not ordinary runtime producers for purposes of the exclusion invariant. Runtime cannot edit operational state or reopen itself. The normal maintenance tool may have its own known administrative backend(s); any other authority must be enumerated, drained or refused before effects. Flyway's actual physical connections must belong to that isolated administrative tool and not be treated as unexpected ordinary sessions.

### 6.2 GAS-P4-005 and legacy launch paths

The existing issue is real: source S1 readiness checks volume/staging, not schema, and an already-running legacy web process can continue accepting writes. AHEAD reported by status is not a request-level fence.

The proposed combination is credible:

- freeze supported services, restart policies, tasks and CLI paths persistently;
- stop and verify old JVMs, not just new fence participants;
- retire the old schema-owner credential and remove it from service-readable configuration;
- end its existing sessions;
- pin supported artifacts and validate new runtime entry before effects.

There is an additional source-supported negative candidate: the accepted InstallationConfigLoader rejects unknown root keys; the new installation/archive keys may cause old binaries to reject the new config. That is useful defense, **not** a substitute for retiring old readable credentials/configurations or terminating an already-running old process.

Proof 1 must exercise authentic legacy serve/upload, worker/recovery and every CLI effect path with both old configuration/retired credentials and new supported configuration. Do not accept merely observing AHEAD or a worker startup failure. Assert zero database mutation and zero protected filesystem effects by the legacy actor.

### 6.3 MINOR N2 — distinguish interrupted prefixes from completed exit suffixes

**Contract:** §6.4 interrupted entry; §7.3 lines 426–428 and 478–481.

“The freeze, NOLOGIN and OFFLINE all persist” is not true at every crash point. Before M-3/M-6 they may not yet be established; after X-2/X-3 the row may be READY and LOGIN/services deliberately reopened. This is a wording/reachability correction, not a demonstrated unsafe reopening: the external freeze and the repeated entry can still supply the intended boundary.

Specify recovery by last actual state: pre-table entry; OFFLINE attempt; committed READY before reopen; partial X-3; completed reopen. A resumed OFFLINE attempt reuses its attempt ID; a new maintenance operation on READY gets a new attempt. Never blindly replay a READY receipt or reset a successful rebind just because the tool lost its response. For a new attempt, re-freeze and exclude again before mutation.

Acceptance criterion: a short state/phase table and crash cases after M-2, M-3, M-6, X-2 and each X-3 action. No new controller state is required.

### 6.4 Administrative authority

OD-6's capability boundary is already approved by C-06. Exact grants remain to be proved. PostgreSQL roles are not “owned” in the same sense as schema objects; role administration uses appropriate role attributes and ADMIN OPTION/membership. Signaling privileges can be wider than one installation. The deployed admin path must either use deliberately authorized DBA action or a bounded permission/function arrangement; runtime must not inherit it.

A dedicated non-superuser path is technically defensible if its capability inventory succeeds. The actor identity, role name and exact grants can be decided before proof/implementation, rather than forcing an architecture reopening. [PostgreSQL ALTER ROLE](https://www.postgresql.org/docs/16/sql-alterrole.html), [server signaling](https://www.postgresql.org/docs/16/functions-admin.html).

## 7. OD-9 investigation: logical abandonment is not physical takeover

### 7.1 What the timeout actually measures

The actual SQL (S2:307–330) selects RECEIVING when:

    greatest(last_progress_at, coalesce(receiver_alive_at, last_progress_at))
        < database_clock - files.receivingTimeout

It then rechecks state **and the stale predicate** in the UPDATE and writes the ABANDONED event in the same transaction. Default is PT15M; installation validation permits PT1M…PT24H. It is not simply a browser idle timer, process-exit detector or elapsed operation duration.

S1 renews receiver_alive_at independently of incoming bytes, at roughly one-quarter of the timeout bounded to 200 ms…5 s. Byte progress renews last_progress_at. A quiet but successfully renewing receiver is not abandoned. The separate browser/request read timeout ends an inactive request through the receiving thread's own error path.

If renewals cannot reach the database while the receiver thread is alive, the stale predicate can eventually hold. Existing code does not thereby establish OS termination.

### 7.2 Accepted behavior and physical consequences

The timeout method itself performs a **logical state/event transition**, not a copy/move/adoption. That is followed in RecoveryService by physical cleanup: ABANDONED falls through to cleanStaging in the same recovery pass. That cleanup calls StagingArea.delete without operation advisory/owner exclusion in current code.

The original receiver can still have its staging channel open. On Linux deletion can unlink an open file while writes continue; Windows sharing behavior can instead make deletion fail/retry. Neither outcome is proof that logical abandonment itself transferred archival ownership.

The late receiver's heartbeat and RECEIVING→STAGED CAS fail after abandonment. S1:285–294 then cleans its temporary rather than enqueueing a promoted File. The batch/item operation identity prevents “retry the same item” from silently creating a second File. Those are valuable existing safeguards, not evidence of receiver death.

T1's crashed-receiver test expects ABANDONED and staged-byte cleanup after timeout; the slow-live-upload and audit regressions require a renewing receiver to remain active. Keep both semantics, with the cleanup precondition made explicit.

### 7.3 MAJOR M1 — narrow OD-9 and cover staging cleanup with its existing subject

**Contract:** §§4.5, 5.4 coverage table, 5.7 and OD-9; lines 188–190, 292–299 and 805.\
**Sources:** S1–S3; T1 receiver/timeout tests; C-01/C-07/C-09.

**Scenario:** a receiver remains alive but loses renewal access long enough for timeout. The current recovery logic can mark ABANDONED and immediately delete its open staging path. The draft responds by delaying **all abandonment** until interval/thread/process exclusion and calls unchanged logical timeout abandonment inherently contrary to C-07. That is broader than necessary and changes accepted user-visible state timing.

**Smallest sufficient correction:**

- Keep lease/progress-based **logical abandonment/cancellation** where accepted, retaining its CAS/event and protection of successful live renewal.
- Logical cancellation must neither clear/steal the token nor authorize deletion, promotion, release or conflicting physical takeover.
- Gate physical cleanup by the existing operation's interval/exclusion rule; the original receiver may perform its own cleanup after its I/O closes, while another process waits for proven release/exit.
- Retain failed-STAGED-CAS behavior so a late receiver cannot finalize an abandoned upload.
- Correct the coverage table: ordinary staged-file cleanup already has a file_operation owner. It is not an unowned volume effect and should not be moved wholesale into maintenance.
- Report “renewal/progress absent; cleanup pending” rather than asserting the process is known stopped when it is not.
- If the owner prefers the draft's alternative “receiving, stalled” behavior, seek explicit approval of that change; do not infer it from C-09.

**Owner decision:** not needed for the narrower safety correction; required to choose a deliberately changed timeout UX. Acceptance criterion: stale renewal causes the chosen logical result, no physical cleanup while W1 may write, no token transfer by timeout, one final accepted archival result, and unchanged behavior for an actively renewing slow upload.

Thus OD-9 should not be treated as an unavoidable contract-acceptance policy blocker. The unsafe physical cleanup is a real correction; a prohibition on every timed logical transition is not required.

## 8. Filesystem effects without an operation record

The draft's last coverage row groups two different cases. Staged files are normally named by an existing operation and **already have a subject**; volume marker creation does not have a file_operation. They need different, narrow treatment.

| Effect / source | Ordinary occurrence and conflict | Smallest credible treatment |
|---|---|---|
| Receiver write/receiver-owned failure cleanup, S1 | Ordinary web work; another cleanup must not unlink a still-active staging file | Existing INGEST/REPAIR operation interval; preserve accepted request error path after its channel closes |
| RecoveryService.cleanStaging, S3 | Ordinary worker cleanup after final states; can race an old receiver or worker | Existing operation's token/exclusion; keep target_created/staging_cleaned guards. Do not require installation downtime for all cleanup |
| OperationProcessor/DestructionProcessor staging cleanup | Existing operation/content work; source may identify a partial copy | Map to the existing interval and accepted evidence/accountability rules, including retained claim protections |
| init-volume marker, S7 | CLI writes marker then creates directories then records initialized timestamp; two initializers can contend | Offline-only authorized provisioning is a simple option. Alternatively keep ordinary initialization under exclusive create and a bounded volume-specific rule. Do not invent a generic operation controller |
| StagingArea.verify directory creation, S3 | Ordinary readiness can create a missing protected staging directory | Installer/offline provisioning is simplest; or retain idempotent protected-directory create/refuse behavior. It is not permission to delete/recreate existing staged files |
| Web write-denial probe, S7:146–181 | Ordinary diagnostic may create/delete a **fresh UUID probe**, not an archival object | Keep installed read-only permissions; unique exclusive probe identity and cleanup/reporting. A crash leftover is reported, not a reason to adopt/delete unknown originals |
| IntegrityService.reverify:434–479 | Worker can restore read-only protection, rehash and update content/findings, without a new file_operation | Include metadata changes and their result-dependent transaction in coverage. Preserve content-specific coordination/observation revalidation and prove exclusion where required; no automatic assumption that “hash reads” means the whole method is effect-free |
| Thumbnails/cache | Derived, rebuildable output; contract excludes it from archival conflicting effects | Existing unique-temporary/replace/cleanup behavior and authorization. Installation maintenance still excludes the producer process; do not create archival owner records for disposable cache bytes |

**Marker crash behavior matters:** VolumeMarker uses exclusive create, write/force and directory flush; ManagedVolume creates managed directories afterward; only then VolumePreservationService records marker_initialized_at/history. A crash can leave a marker without the database timestamp or complete directories. The accepted next call refuses an existing marker rather than blindly replacing it. Keep that safe refusal; any reconciliation must compare the complete marker identity and current directory/DB state under authority. No overwrite/adoption is authorized merely because the row says “not initialized.”

If init-volume is confined to maintenance, define the narrow administrative command entry: the current CLI requires ready archive status before volume commands (S8), so it cannot be reused unchanged while installation_state is OFFLINE. This is a bounded entry adaptation, not grounds for a new generic journal.

No effect without an operation is allowed to run uncovered. However, a coverage inventory left to implementation is not itself a new fundamental architecture defect when the contract explicitly forbids uncovered effects. The inventory must accurately distinguish existing subjects, fresh private diagnostic objects, disposable derivatives and genuine administrative provisioning.

## 9. Durable-state assessment

### 9.1 Necessity of fields and objects

| Proposed item | Assessment |
|---|---|
| installation_state.mode READY/OFFLINE | Necessary narrow validated/not-validated boundary; Flyway history alone does not say validation/reopen completed |
| installation_id, host_id | Necessary operational binding under C-05/C-11; neither is archival identity |
| maintenance_attempt_id | Useful and sufficient as a CAS/receipt correlation key if active OFFLINE attempts reuse it and distinct attempts use distinct IDs; not a hidden controller generation |
| ready_report_digest | Useful binding to the actual validation artifact. Retain/recover that artifact; a hash alone is not the report and does not prove bytes remain unchanged |
| changed_at | Operational attribution/diagnostics; not authority or a substitute for CAS |
| expected Archive ID in config; Archive ID in archive_instance | Correct separation; no duplicate authoritative archive identity is needed in the singleton. Bootstrap must handle the no-row case (B2) |
| owner instance/host/PID/start/interval fields | Justified by the subject-lifetime problem. Host/PID/start support exclusion; random instance supports attribution; unique interval supports precise CAS and successive intervals in one JVM |
| all-null/all-set constraint | Necessary to prevent partial, uninterpretable ownership; timestamp representation must survive round-trip for P1 |
| installation fence file | Necessary positive exclusion candidate for compatible idle processes; local/protected; no generic per-operation file forest |
| append-only receipt | Durable atomic transition evidence is necessary. The dedicated second table is reasonable but not logically mandatory |
| executable/schema config/compiled chain | Exact build/schema check is necessary. A version display string alone is insufficient; immutable artifacts/checksums and supported prefix/endpoint rules carry the guarantee |

The five token columns should not become a new freely editable archival target history. Existing immutable operation identity/evidence remains protected; token columns are current operational ownership only. Runtime grants must not permit changing immutable archival fields simply because it may CAS a token.

### 9.2 MAJOR M3 — define receipt identity and complete transition coupling

**Contract:** §§6.2, 6.4 M-6, 6.5 X-2, 7.4, 7.6 and 9.1–9.2; lines 371–379, 424–435, 489–497, 522–533 and 644–669.\
**Sources:** S8 atomic identity history; S11 existing transactional history; C-06/C-11 and D4-31 proof obligation 6.

**Scenario:** a tool loses confirmation of READY or REBIND, then resumes the same attempt or another attempt changes the row. The listed receipt columns contain an attempt and kind, but no unique event key, explicit old/new mode or exact state-transition uniqueness/coupling rule. A binding pair alone cannot establish that the matching transition/validation evidence was committed once, rather than being a stale or duplicate row. The intended atomic guarantee is stated, but the minimal data contract is incomplete.

**Smallest sufficient correction:**

- Specify an immutable receipt identity (a UUID suffices), attempt correlation, exact kind and the old/new fields relevant to that transition, including mode/expected attempt and validation digest where used.
- Specify one authoritative atomic operation: compare expected singleton state, insert its exact receipt and change the singleton in the **same transaction**; any failed CAS/receipt insert rolls back everything.
- Specify the exact lookup/reuse rule after unknown COMMIT, including another attempt/another binding outcome. Do not identify success solely by seeing the new host pair.
- Require append-only/no-truncate history and admin-only operational receipt writes; runtime must not fabricate a success receipt.
- Define how the seed's first OFFLINE receipt and repeated checkpoints relate to uniqueness. Do not add an FK from all historical receipts to a changing “current attempt” value that would prevent the next attempt.
- Preserve the complete report/recorded exclusion statements behind the digest, without putting secrets into history.

**Owner decision:** OD-5 representation can be deferred until DDL; the safety contract cannot. A small second table is defensible.

Correct one source assertion: S11 demonstrates that change_history **can** be inserted atomically with another row. What it lacks by default is the typed operational payload/authority/visibility policy. Do not justify the second table with a false claim that same-transaction history is impossible. Reusing it would require narrowly validated admin-only operational entries and appropriate visibility; that extra coupling may make a dedicated table simpler.

Acceptance criterion: committed transition and matching immutable receipt are inseparable; rollback leaves neither change; unknown result is resolved by exact receipt/attempt/old-new state with no second event.

## 10. Historical admission and R39-T

R39-T is **already approved** by C-10. Do not reopen it as a trust-policy choice. The draft properly acknowledges that writer-shaped manual rows cannot be distinguished retrospectively, that no target-assignment ledger exists, and that absence, skipped generation numbers and event text cannot prove history.

Positive structured witnesses are credible under that bounded premise. Independent ownership/alias/evidence checks still matter; trust in origin does not authorize effects or hide real conflicts.

### MAJOR M2 — specify physical equivalence, active intent authority and refusal precedence

**Contract:** §§8.2–8.5; lines 575–620.\
**Sources:** S4/S5 accepted current evidence and Q/U/claim processing; physical_location_owner key (volume_id,path); retained AU/PO analysis; C-02/C-10/C-11 and the eleventh assessment's repeated-repair and bound-object findings.

**Concrete cases:**

1. Q for an accepted attested superseded object can name a source different from its operation's current target. S5 settles the Q's recorded source/destination under trusted evidence/current attestation. Requiring movement authority “only from the current target” in check 6 can reject that accepted operation.
2. “Persisted X is one subject across operations” is valid as a **content coordination domain**, but not as permission to merge all X's generations/paths into one physical object. Likewise, a generation's creator differing from a later repair/destruction operation is not automatically an alias when a known relational link explains it.
3. The “Final and inert: no action” outcome needs precedence. If it exempts a malformed/UNPROVED row before witness/conflict classification, it conflicts with C-10; if it means admitted history requires no effect, it is fine.
4. Setting one affected operation BLOCKED does not necessarily prevent all effects on a bad subject: S5.reconcileAll iterates every INTENDED Q regardless of the operation's BLOCKED state, and content-scoped scanners/destruction can have other entry paths.

**Smallest sufficient correction:**

- Distinguish content coordination identity X from exact physical keys (normalized volume/path), with role/generation/evidence relations used to determine known-object equivalence. A generation label cannot separate two real aliases of one physical path.
- Scope creator/private-UUID checks to their writer roles. Preserve repeated repair, bound destruction-side U and attested-object associations.
- State that movement follows the accepted **current authorization for the physical subject**, including durable INTENDED Q inputs and current bound attestation/claim/evidence. A historical association alone grants nothing; file_operation.current_target is not the sole source.
- Define witness/conflict classification before “inert, retain without effect”; inert is not a waiver for UNPROVED or a real alias.
- For scoped refusal, prove every competing writer/recovery path for that subject is disabled or safe, not just that one state maps to BLOCKED. Until such class-specific proof is approved, refuse READY globally for the initial admission.

**Owner decision:** OD-7 concerns availability/refusal policy. Correct equivalence/evidence semantics are already required and need no new owner choice. Recommended policy is global initial refusal, with scoped classes deferred rather than weakly enabled.

Acceptance criterion: real repeated repairs at different generations pass without merging distinct objects; bound Q/U/destruction/attestation relationships pass; foreign live owner/alias/UNPROVED rows refuse; no scoped class can still run through Q reconciliation or another unblocked writer.

## 11. Migration and identity

### 11.1 What is sound

The draft correctly relies on **per-migration**, not whole-chain, PostgreSQL transactions and Flyway history/checksum validation. It explicitly notices that status(BEHIND) precedes validation in current SchemaMigrator. Requiring a supported history prefix and target endpoint under exclusive administration removes the old controller/MIGRATE/RESTORE compatibility cycle.

Intermediate checkpoints remain offline. Continuing the exact compatible target chain or restoring a coherent pre-upgrade checkpoint is consistent with BUILD-SPEC §56. No ordinary schema compatibility waiver or manifest-bearing maintenance run is needed.

Unknown migration/READY/rebind COMMIT must be decided only after the original backend cannot still commit. “Old state still visible” is not enough while it lives. B1 extends this discipline to token acquisition/release and effect-dependent ordinary transactions.

Rebinding compares exact old operational IDs, uses caller-owned new IDs and leaves Archive ID/portable file identities unchanged. Old-host exclusion and credential retirement remain explicit authority, not facts inferred from an empty session list.

### 11.2 BLOCKING B2 — specify a reachable fresh-archive/bootstrap entry

**Contract:** §§6.4 M-1/M-6, 7.1, 7.5, 9.1 and 10.3; lines 419–424, 445, 502–510, 648 and 700–704.\
**Sources:** S8 ArchiveService.initialize creates the UUID inside its initialization transaction; V0002 permits zero archive rows until init; current CLI explicitly permits init on a current schema before archive readiness; C-01/C-11 and ADR-0005/0006.

**Failure trace:**

1. A clean supported installation has no archive_instance row and no installation_state.
2. M-1 requires the database Archive ID to equal a configured expected Archive ID before entry. The accepted initializer has not generated one yet.
3. M-6/§7.1 refer to an OFFLINE row before migration, while §7.5 says the first C migration creates it.
4. Ordinary initialization cannot supply a way out of that circle: §10.3 requires READY and the expected existing Archive ID before every first effect.

Saying “init-archive keeps accepted behavior” does not define which narrow entry bypasses those prerequisites. The result is an unreachable accepted clean-install workflow, or an implementation inventing a broad identity exemption.

**Smallest sufficient correction:** define a narrow administrative bootstrap case under the same external freeze/exclusion:

- distinguish genuinely empty pre-init archive state from an initialized archive with missing/mismatched identity;
- before installation_state exists, rely on M-2…M-5 and explicit admin exclusion, then create OFFLINE plus its seed receipt inside the first supported migration;
- allow only accepted one-time archive initialization under that authority; acquire the accepted table initialization lock and create the permanent UUID once;
- establish the expected configuration/receipt association without claiming configuration and PostgreSQL are one atomic store; interrupted config update remains offline and reconciles the single committed archive row;
- after unknown init COMMIT, establish backend absence and inspect the row/history before retrying; never mint another UUID merely because the client did not receive it;
- allow READY only after the normal identity/validation checks. Initialized archives keep strict expected-ID equality with no general rewrite/rebind of Archive ID.

An equivalent sequence that deliberately initializes before the C control migration is also possible, if explicit and fully guarded. Do not prescribe a new table just to avoid choosing the sequence.

**Owner decision:** none needed to preserve clean initialization. Any change in permanent Archive-ID semantics would need approval and is not recommended. Acceptance criterion: a successful fresh-install trace and every bootstrap crash/unknown-outcome branch terminate in one Archive ID or safe OFFLINE refusal, with no ordinary identity exemption.

## 12. Fourteen-case failure matrix

“Covered” means an architectural rule is present, **not that a test passed**. Corrected-contract dependencies are explicit.

| # | Contract case | Classification | Controlling invariant/mechanism and remaining obligation |
|---|---|---|---|
| 1 | Two workers compete for content | **COVERED CONDITIONALLY** | INV-4/5; per-operation ownership, Ct coordination, monotonic generations and claims. Different repairs can create private copies at distinct targets; serialize conflicting publication/claims rather than promising only one operation ever runs for X. Ordinary LP/LO evidence and B1 required. |
| 2 | Advisory session lost, JVM alive | **COVERED CONDITIONALLY** | Token plus PID/start refusal is credible; B1 durable acquisition/all-transaction coverage and P1 required. |
| 3 | Worker dies during filesystem I/O | **COVERED CONDITIONALLY** | Reclaim only after exclusion, then accepted byte/evidence reconciliation; B1/P1/P3. |
| 4 | Blocked NAS then recovery | **COVERED CONDITIONALLY** | No takeover on elapsed time; retained token/fence; P3 must establish remote completion/quiescence or retain refusal. |
| 5 | Legacy restart during maintenance | **COVERED CONDITIONALLY** | M-2…M-5, retired credentials and supported-launch exclusion; authentic entry-by-entry Proof 1, GAS-P4-005 remains OPEN. |
| 6 | Automatic service restart | **COVERED CONDITIONALLY** | Persistent mask/restart freeze plus local shared/exclusive fence and database admission closure; P2 and installed-service Proof 1. |
| 7 | Orphan backend survives shutdown | **COVERED BY CONTRACT** | M-5 drains by PID/start and confirms absence; prepared authority refuses. Actual grants/implementation still tested. |
| 8 | Migration crash before commit | **COVERED BY CONTRACT** | Transactional migration and external/DB offline boundary; first-table/bootstrap exception needs B2, but ordinary migration rollback rule is sound. |
| 9 | Migration crash after committed checkpoint | **COVERED BY CONTRACT** | Validated history prefix, no ordinary intermediate admission, deliberate continue/restore; proof must include wrong checksum/hotfix negative. |
| 10 | Unknown transaction outcome | **COVERED CONDITIONALLY** | §7.4 sound for admin transactions; B1/M3 complete token/receipt instantiation. |
| 11 | Runtime identity mismatch | **COVERED CONDITIONALLY** | Before-effect binding/READY checks and proposed latch; OD-4 lifecycle choice must not permit more effects or early fence release. |
| 12 | New host while old may write | **COVERED CONDITIONALLY** | Verified old-host/access exclusion, retired credentials and deliberate offline rebind; P3 for outstanding storage effects, M3 receipt. |
| 13 | Historical ambiguity | **COVERED CONDITIONALLY** | Approved R39-T plus independent checks; M2 and OD-7 determine exact refusal behavior. |
| 14 | Repeated repair/cross-operation relation | **COVERED CONDITIONALLY** | Explicit positive requirement is correct; M2 scopes key/evidence equivalence and prevents false refusal. |

No listed case requires abandoning C. The matrix does not cover clean bootstrap explicitly; B2 adds that missing acceptance trace. Logical abandonment/cleanup is another essential concrete variant, not a fifteenth generic protocol.

## 13. OD-1…OD-9 decision matrix

| OD | Genuinely unresolved? | Review of recommendation / simpler alternative | Actual gate and owner timing |
|---|---|---|---|
| OD-1 D-LP-9(a) | **Yes**, separate implementation authorization | Bounded administrator coordination is technically supported; leaving the known cycle open is not final acceptance | Does not block this boundary review; must be resolved before applicable ordinary-order implementation/acceptance. Do not confuse with D-LP-3 |
| OD-2 D-LP-3 | **Yes**, visible three-abandoned-attempt Conflict outcome not approved by D4-31 | Bounded retry outcome is defensible; retaining an accepted equivalent needs an actual safety/behavior mapping, not merely deleting a label | Before implementing derivative-closure/retry behavior. No need to decide to correct this contract's maintenance architecture |
| OD-3 fence mechanism/library | **Choice unresolved; invariant already required** | FileChannel with unique retained channel/protected object is a credible dependency-free candidate; JNA if evidence requires it. Supervisor-only equivalent needs proof | **Not inherently a contract-acceptance blocker.** Accept a performance/safety specification with a prototype candidate; choose before proof execution and implementation. Any new dependency needs approval |
| OD-4 mismatch lifecycle | **Yes**, exit versus diagnostic-only | Both can be safe if neither starts conflicting effects and neither releases the fence while effects remain. Latch/controlled exit is reasonable | Before implementation; not a new choice to allow wrong-identity effects |
| OD-5 receipt representation | **Yes**, one table plus typed history versus two tables | Dedicated narrow append-only table may be simpler than modifying researcher history. Existing same-transaction insertion is possible, contrary to draft wording | M3 data/atomicity contract before acceptance; representation/DDL before implementation. No need for a generic journal |
| OD-6 admin principal | **Exact principal/grants unresolved; C-06 already approved** | Dedicated non-superuser authority is defensible if capabilities suffice; deliberately scoped DBA intervention is simpler operationally but has wider privilege | **Not inherently a contract-acceptance blocker.** State required capabilities now; settle/test grants before proof/implementation. Owner approves credential/privilege deployment, not a reopened authority separation |
| OD-7 refusal scope | **Yes**, availability/safety scope | Global initial refusal is simplest. Scoped admission requires proof of closure over every effect path, not just one BLOCKED row | **Before accepting any scoped behavior.** Owner may choose global-first now and defer scoped classes. Do not silently choose availability policy |
| OD-8 host ID | **Yes**, representation | OS identity is a reasonable baseline; protected provisioned file is another. Both have clone/copy limitations, already bounded operationally | Before implementation/proof data. Hosting model itself is not open |
| OD-9 receiving timeout | **Draft's proposed behavior change is unresolved; safety correction need is clear** | M1 separates logical abandonment from physical ownership/cleanup and preserves more accepted behavior. Delayed “receiving, stalled” is not compelled by C-09 | **Not an unavoidable acceptance blocker.** Prefer narrower correction. Owner must approve if retaining changed user-visible timeout behavior |

No owner decision in this table is made by the reviewer.

## 14. Feasibility proof assessment

Both proposed proofs are appropriately non-production and separately authorized. They are observable and falsifiable, use authentic artifacts and installed service accounts, and distinguish a Java seam from actual kernel/NAS behavior. Their success would validate specific mechanisms, not accept WP1 or release WP2.

### 14.1 Proof 1 — essential corrections/additions

Retain the current active legacy/new producer, orphan backend, prepared transaction, checkpoint crash, unknown migration COMMIT, rebind crash and restart-policy negatives. Add only:

- **B2 bootstrap:** no Archive ID/no state table, then a successful one-ID initialization and interrupted configuration/DB association.
- **Entry/exit suffixes:** crash before first OFFLINE and after READY, during credential installation/LOGIN/unmask. Verify that recovery reads the actual attempt/state rather than assuming OFFLINE always persists.
- **Both actual COMMIT outcomes:** hold the original backend alive while a fresh read might see old state, then prove it cannot commit before classifying. Include READY/rebind as well as migration.
- **Real legacy access combinations:** old config/retired credential and new supported config, not just AHEAD output; record zero effects.
- **Role capability negatives:** runtime cannot DDL, alter state/receipt/roles or signal unrelated authority; admin can perform the required bounded procedure. A full inventory can remain implementation work, but the prototype's essential authority cannot.
- **Wrong archive/host/config and missing/replaced fence negatives:** refusal before migration, backend termination of an unrelated installation or binding change.

The proof should never resolve a prepared transaction automatically merely to progress. Refusal is an acceptable explicit result; any deliberate DBA resolution is separately recorded.

### 14.2 Proof 2 — essential corrections/additions

Retain promotion, quarantine move, lost advisory backend, same/different host, unreadable/reused PID and actual stalled storage. Add:

- **B1 durability:** failure before owner commit; unknown owner acquire/release COMMIT; old effect-dependent backend still alive after JVM termination; Q/U transaction validation as well as the operation enum transition.
- **M1 receiving:** renewal loss plus a live receiver, logical timeout abandonment, attempted external cleanup, then actual receiver close/exit and safe cleanup. Assert no STAGED/File completion after cancellation.
- **Cross-account process identity:** Windows web/worker virtual accounts and Linux service/admin visibility; canonical start time after storage round-trip.
- **Native completion:** stalled copy **and rename**, client/server reconnection and late writeback/namespace observation; a pre-exit size/hash sample is not enough.
- **Same-JVM concurrency and multiple JVMs:** an in-memory interval set must not be treated as authority to clear a still-running asynchronous effect.

Do not require sleep-free kernel behavior where a controllable external server/storage condition is the subject. Use deterministic seams for ordering and bounded waits only to observe a named state. Record SQLSTATEs explicitly in database races; retries must not hide 40P01 or unexpected lock timeouts.

Full realistic inherited fixtures, complete token-path coverage, all P2-8/Phase 0–3 regressions and implementation audit remain later acceptance requirements. The two prototypes need not become the full production system before an architectural decision.

## 15. Complexity and proportionality

The replacement converges materially toward the approved direction:

| Measure | Revision 3.9 | Target contract |
|---|---|---|
| New control tables | 9 | 1 singleton, or 2 with typed receipt |
| Generic table admission gates | 49 proposed covered tables | None; archival/security triggers retained |
| Controller/run/connection protocols | Run states, controller generation, physical backend registration | No generic controller; global offline drain |
| Epoch/publication/action machinery | Per-connection declarations, action/window/publication identity and recovery | Removed |
| Ordinary ownership | Advisory/content/claim coordination plus broad admission premises | Same retained ordinary corrections plus a narrow durable interval |
| Maintenance state | Multiple interacting control states | READY/OFFLINE plus explicit external freeze/role/lease facts |
| Native boundary | JNA/native same-handle contract | One local process-lifetime fence, candidate open |
| Exceptional recovery | Controller adoption, checkpoint compatibility, repair/restore run supersession | Deliberate re-entry, drain, history/identity inspection and validated reopen |
| Ordinary business windows | 19 P2-8 / 22 Phase 2 | Retained, not reduced by assertion |
| Administrative burden | More machine-driven recovery contracts | More deliberate operator work and accepted downtime |

“Zero CU” in the complexity table is a naming statement, not zero transaction complexity. Actual administrative transitions remain and should be listed without resurrecting CU-0…CU-35. There are three independent authority layers worth keeping explicit: subject effect ownership, process/installation exclusion and database administrative admission. They address different failure domains; reducing them to one advisory lock would be unsafe.

Likewise, “one/two states” must not conceal external facts: freeze, role admission, process/fence ownership, backend drain, migration checkpoint and receipt all matter. The draft generally acknowledges them. N2 requires truthful crash-phase wording, not another durable controller table.

The corrections requested here add **no required new table, global controller, generic action journal, protocol epoch or mandatory native dependency**. B1 completes the token's transaction contract; B2 supplies the first-install exception; M1 reuses an existing operation; M2 scopes known evidence rules; M3 completes a small receipt. They are proportionate.

Convergence judgment: this first C contract has bounded gaps, while its core separation is intact. No current evidence warrants triggering a new RECONSIDER cycle or reopening owner-approved hosting/downtime/R39-T rules. Continue only against the finite checklist, not by accumulating unbounded new exceptions.

## 16. Prioritized findings and finite correction checklist

### 16.1 Finding register

| ID | Severity | Finding | Required timing / owner |
|---|---|---|---|
| B1 | **BLOCKING** | Token acquisition durability and coverage of every effect-dependent database transaction are not explicit | Contract correction before acceptance; no new policy decision |
| B2 | **BLOCKING** | Fresh initialization and first OFFLINE creation have circular prerequisites | Contract correction before acceptance; preserve accepted initialization |
| M1 | **MAJOR** | OD-9 overstates timeout prohibition; staging cleanup already has an operation subject and needs physical exclusion | Narrow correction before acceptance; owner only if choosing changed timeout behavior |
| M2 | **MAJOR** | Historical physical equivalence, active intent authority and refusal precedence/scope need exact boundaries | Contract correction before acceptance; OD-7 for scope |
| M3 | **MAJOR** | Receipt identity, full transition inputs and atomic replay rules incomplete; history atomicity source claim incorrect | Safety data contract before acceptance; representation can await implementation |
| N1 | **MINOR** | Invariant language is broader than actual accepted mutation/exclusion semantics | Clarify as below |
| N2 | **MINOR** | Crash wording assumes OFFLINE/NOLOGIN at every prefix/suffix | Actual-state recovery table, §6.3 above |
| P1 | **FEASIBILITY-DEPENDENT** | Reliable same-host process identity under actual accounts/precision | Proof 2, §5.3 |
| P2 | **FEASIBILITY-DEPENDENT** | FileChannel local lifetime/object contract or justified equivalent | Proof 1 and lifetime variants, §5.4 |
| P3 | **FEASIBILITY-DEPENDENT** | Process exit/flush versus remote delayed data and namespace effects | Proof 2, §5.5 |

The B/M findings include exact contract areas, source references, failure traces, requirements, minimum corrections and owner implications in their detailed sections. P findings are specific falsifiable hypotheses, not alleged observed test failures.

### 16.2 MINOR N1 — align invariants and test terms with their intended scope

Contract §3 INV-3/6/7 and §12 failure criteria need small clarifications:

- INV-6 cannot literally prohibit every update of operation/Q/U records; accepted state, evidence-decision and lifecycle transitions are updates. Protect immutable identity/evidence fields and append-only history, while retaining authorized transitions.
- INV-7 excludes ordinary/legacy **effect-capable admitted producers and their surviving authority**, not the maintenance tool's own known administrative process/connections. A new compatible process blocked before admission on the fence is not evidence of an unsafe effect-capable old producer.
- “Every byte object accounted” must allow reported unexplained objects without automatically adopting them or inventing an owner. TX-006/IV unexpected-object behavior remains accepted.
- Test failure criteria must distinguish unsafe effects from the above diagnostic/startup/refusal states. Otherwise a safe blocked startup can make Proof 1 fail for the wrong reason.

These corrections need no owner decision and no new mechanism.

### 16.3 Exact bounded acceptance checklist

A corrected contract is ready for owner contract-acceptance consideration when each item below has an explicit answer:

- [ ] **C1 — B1:** State the durable token acquire/reclaim commit before any effect; unknown acquisition permits no effect; guard/serialize all effect-dependent mutation transactions; resolve pending DB outcomes before release; show a live-old-backend and rollback trace under the retained order.
- [ ] **C2 — B2:** Supply a complete fresh-install and first-control-migration trace, with a narrow pre-init identity exception, one permanent Archive ID, an OFFLINE seed receipt, non-atomic config/DB recovery and no general identity bypass.
- [ ] **C3 — M1:** Separate logical receiving cancellation from physical cleanup/ownership; map staging cleanup to its existing operation; preserve live renewal and failed-STAGED-CAS behavior. Either use the narrower policy or obtain approval for the proposed changed timeout UX.
- [ ] **C4 — M2/OD-7:** Distinguish exact physical subject from content coordination; retain active Q/attestation authority; define witness/inert refusal precedence. Choose global-first initial admission or obtain scoped class-specific policy/proof requirements covering every writer.
- [ ] **C5 — M3:** Define unique immutable receipt identity, exact old/new transition data and atomic CAS+receipt operation; specify replay/other-attempt outcomes; correct the false “existing history cannot be atomic” claim.
- [ ] **C6 — N1/N2:** Correct invariant scope and actual-state crash entry/exit descriptions, including partial bootstrap, committed READY and partial reopening. No additional controller states/tables.
- [ ] **C7 — P1/P2/P3:** Keep platform hypotheses explicitly conditional, correct Windows-directory/NAS force claims, define refusal when evidence is unavailable, and incorporate the essential proof variants in §14.
- [ ] **C8 — decisions/traceability:** Reclassify OD-3/6/9 as described in §13; record actual absence of current-run chunk validation; distinguish D-LP-3 from D-LP-9(a); carry all retained ordinary windows and existing OPEN findings forward without claiming closure.

This is a finite list. It does not request a new architecture selection, implementation, experiment or broader redesign. If a proposed correction would add generic maintenance machinery, it must first justify why these narrow alternatives cannot meet a concrete requirement.

## 17. Recommended next engineering gate and completion boundary

**Overall verdict: ACCEPT WITH SPECIFIED CORRECTIONS.**

**Recommended governance gate: CONTINUE** on the bounded contract corrections, within the owner's existing SIMPLIFY — OWNER APPROVED direction. Do not label the contract accepted or implementation-ready until its correction/policy gate is completed. The separately authorized feasibility proofs come afterward; they have not been run.

The engineering-governance checks are satisfied by this disposition:

- D4-31's approved requirements are the target; the noted gaps are finite.
- The retained ordinary corrections, durable subject ownership and offline exclusion each have a demonstrated necessity.
- The scope of new durable machinery remains small; no simpler advisory-lock-only alternative closes the lost-session/live-I/O case.
- The requested corrections do not add disproportionate coupling or repeat the comprehensive controller framework.
- Compared with revision 3.9, the draft is materially simpler and is converging at the boundary level.
- Another architecture reconsideration is not warranted by these findings. A failed feasibility hypothesis must return as evidence, not be hidden by more machinery.

Before local commit, verify only this new review document is staged, inspect its complete diff, run git diff --check and confirm the draft contract has not changed. The report's commit SHA is supplied in the completion message rather than embedded recursively here.

No audit finding is closed. F5-1…F5-3 remain architecturally resolved and preserved. F5-4, WP1-R1 and GAS-P4-002…005 remain OPEN pending the appropriate selected-design evidence. No application code, tests, migrations, CI, deployment files, owner decisions, accepted specifications or draft contract are changed by this review.

**No feasibility experiments, implementation, merge commit, rebase, tag or push are performed. The Architecture C contract remains DRAFT and unaccepted. WP1 implementation remains unauthorized. WP2 remains HOLD.**
