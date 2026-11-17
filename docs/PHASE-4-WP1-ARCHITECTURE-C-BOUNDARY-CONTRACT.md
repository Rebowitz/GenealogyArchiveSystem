# Phase 4 WP1: Architecture C replacement boundary contract

**DRAFT — NOT ACCEPTED — NOT AUTHORIZED FOR IMPLEMENTATION**

| | |
|---|---|
| Repository | `Rebowitz/GenealogyArchiveSystem`, branch `phase-4` |
| Baseline | `fad2b44c60887fac7c3aa70c5c4756b9dd6b9303` |
| Governing decision | **D4-31**, Architecture C, Rules C-01…C-12 ([`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md), owner-approved 2026-11-17) |
| Technical basis | [`PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md`](PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md) (§5 Architecture B, §8 retain/remove table) |
| Historical evidence | [`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md) revision 3.9 (superseded as direction; cited only for retained ordinary corrections), [`PHASE-4-WP1-AUDIT-CORRECTIONS.md`](PHASE-4-WP1-AUDIT-CORRECTIONS.md), [`issues.md`](issues.md) |
| Authorization | Gate 1, design documentation only. Nothing here is implemented, executed or approved. |
| Status | WP1 unaccepted. WP2 HOLD. WP1-R1, F5-4 and GAS-P4-002…GAS-P4-005 OPEN. F5-1…F5-3 architecturally resolved and preserved. |

---

## 1. Purpose and how to read this contract

This contract states the smallest architecture the builder believes satisfies D4-31 for WP1.
- **What it is:** a set of testable safety requirements, plus the few mechanisms proposed to meet them.
- **What it is not:** a revision 3.10, a schema, or an implementation plan.

**Normative words.** **MUST** and **MUST NOT** are contract requirements. **Proposed** marks a mechanism the
builder recommends but that still needs owner decision, review or proof.

**Evidence labels**, used throughout:

| Label | Meaning |
|---|---|
| **[SRC]** | Read from the baseline source, migrations or deployment files |
| **[R]** | An approved requirement: an accepted specification, an accepted Phase 0–3 decision, or a D4-31 rule |
| **[RET]** | A WP1 correction retained from earlier review work. It is designed, not implemented. |
| **[NEW]** | A mechanism proposed here |
| **[UNPROVED]** | A safety claim that needs a feasibility proof or an implementation test before it may be relied on |
| **[OD-n]** | An open owner decision (§13) |

**Design rule applied throughout (ENGINEERING-DESIGN-INSTRUCTIONS §2):**
- Every new durable field, table or coordination mechanism in §9 names the safety property it serves.
- It also says why an existing record cannot serve that property, and which simpler alternative was rejected.
- A mechanism without that justification is not in this contract.

---

## 2. Scope and operating assumptions (D4-31 C-04, C-05, C-06, C-09, C-12)

### 2.1 Assumptions

| # | Assumption | Precise boundary |
|---|---|---|
| A-1 | **One designated producer host per installation** (C-05) | A **producer** is any process that can perform archival database writes or filesystem effects on managed Storage Volumes or the staging directory (§5.1). Every producer of an installation runs on that installation's designated host. A process on any other host is unsupported. It is excluded operationally (§6, §7.6), never by a local lock. |
| A-2 | **Many processes on that host** | Any number of supported web (`serve`), worker (`worker`) and CLI processes may run at the same time. Nothing assumes one JVM, one worker or one thread. |
| A-3 | **Remote PostgreSQL and NAS storage are allowed** | PostgreSQL may run on another machine, and Storage Volumes may be on a NAS. A NAS volume does **not** extend the local fence (§5.4) to other hosts. Network storage stalls are expected and must fail visibly (TX-004/TX-008). |
| A-4 | **Planned, exclusive, offline maintenance** (C-04) | Incompatible migrations, exceptional restoration and installation rebinding run only while **no** producer of the installation runs. Downtime is accepted. Online schema change is not offered. |
| A-5 | **Separate runtime and administrative authority** (C-06) | The runtime database role cannot change schema, operational state or role settings. Only the administrative path can. |
| A-6 | **Deliberate manual intervention** (C-04, C-09) | Ambiguous failures stop in a visible, fail-closed state. These include interrupted maintenance, an unknown COMMIT, and a former writer that cannot be verified. An administrator resolves them. Nothing is taken over automatically after a timeout. |
| A-7 | **No cross-host takeover and no availability during maintenance** | There is no failover, no second active host, no remote fencing, and no service during maintenance. |
| A-8 | **Practical threat model** (accepted P2-E limits) | The model covers supported processes, operator mistakes, crashes, network faults and stalled storage. It does not cover a privileged operator who deliberately bypasses roles, the freeze and constraints with arbitrary SQL or filesystem writes. |

### 2.2 Out of scope (C-12)

The following are out of scope:
- the Phase 8 backup and restore product;
- general disaster recovery;
- automated restore orchestration;
- reverse migrations: BUILD-SPEC §56 allows restoring a pre-upgrade checkpoint instead;
- multi-host producer topologies;
- D-LP-9 (a) administrator coordination, which is separately eligible and not authorized ([OD-1]).

WP1 provides only the boundary that an upgrade, a restore or a host move must pass through safely.

---

## 3. Safety invariants

These invariants are the contract's acceptance targets. Each maps to D4-31 rules and accepted requirements in §14.

| ID | Invariant |
|---|---|
| **INV-1 Identity** | Archive ID, File, content, generation, operation and every other permanent identifier is never changed, reused or reassigned. This holds through migration, recovery, restore and rebinding (C-11; BUILD §7–8). |
| **INV-2 Immutable originals** | A verified preservation original is never overwritten, moved or deleted, except by an accepted, authorized and evidence-bound operation (TX, IV). |
| **INV-3 Accountable bytes** | Every byte object on a managed volume is accounted for by an accepted owner, quarantine or unresolved record. No unknown object is adopted or deleted by guess (P2-I). |
| **INV-4 No overlapping effects** | A conflicting database or filesystem effect is never performed while a former writer of the same subject could still perform its own (C-07). |
| **INV-5 Ordered locking** | The supported writers acquire locks in the approved global order, so no supported pair or cycle deadlocks through an approved path (PR4-R3, LO-1…LO-7). |
| **INV-6 Durable evidence** | Operation, quarantine, unresolved, event and history records are never deleted or rewritten. Recovery reuses them and never infers success from file names or from a COMMIT alone (TX-002). |
| **INV-7 Exclusive maintenance** | No producer, whether compatible or legacy, runs or holds a live database session of the installation while schema, operational state or binding changes (C-04, C-08). |
| **INV-8 Fail closed** | When ownership, exclusion, identity or recovery evidence cannot be established, the conflicting operation is refused visibly. Unrelated safe work continues where the accepted rules allow it (C-09). |
| **INV-9 Bounded history** | Inherited records are admitted only under R39-T with independent conflict checks. Missing provenance is never manufactured (C-10). |
| **INV-10 Restriction and authorization** | Accepted restriction, non-disclosure and authorization behaviour is unchanged on every path (SEC-004, D4-16, D4-27). |

---

## 4. Ordinary concurrency (C-01, C-02)

Ordinary operation means every supported process running concurrently on the designated host while the installation
is READY. It has **no** maintenance state machine, epochs, per-connection declarations, controller registrations or
generic action journal. The guarantees are the accepted Phase 0–3 guarantees, plus the WP1 ordinary corrections
retained below.

### 4.1 Lock ordering and deadlock avoidance [RET]

The following are retained:
- the global order L0 → S → F → Ct → (P under its Ct) → K → R → Source → Repository → L2…L5, with rules 1–11
  (proposal §3.1–§3.2);
- File before Record;
- final strength first;
- parents first;
- worksheet locking only through the session predicate;
- private new rows;
- terminal references (TR).

The retained writer sequences are:
- G `applyChunk`;
- A `CaptureSessionService.add` (D-LP-2);
- L `linkFile`;
- FC `FileService.correct`;
- the Phase 2 File-before-content writers;
- the catalog writers (proposal §4.2–§4.8).

**Requirements:**
- B-1: every supported multi-row writer follows that order, including implicit FK, unique-key and trigger waits,
  using the proposal's §2.2 trigger and §2.5 conflict-key ledgers.
- B-2: `LockSequence` records attempted and acquired locks correctly, and does not claim to observe implicit SQL
  locks.
- B-3: a deadlock (40P01) on a supported path is a test failure, never an accepted outcome.

**Open:** GAS-P4-003, the administrator check cycle, needs D-LP-9 (a) or an equivalent [OD-1].

### 4.2 Physical content, generations and claims [RET]

The following are retained:
- **CR-1:** Ct(X) FOR UPDATE before content-scoped writes of persisted X;
- **CR-2:** operation rows only by their owner or a classified window;
- **CR-3:** content-less U and Q only under the operation's advisory lock;
- **CR-4:** location keys only for locations created by exclusive create;
- the window inventory **P2-8a…P2-8g**: 19 P2-8 windows, 22 Phase 2 windows (proposal §4.9);
- **PO-B…PO-D** (§5.1–§5.3).

They depend on three resolved findings:
- **F5-1:** monotonic generation reservation (`planRepair`, `plan`, `quarantinePartialAndReplan`), with the
  high-water mark taken over all states and a checked overflow;
- **F5-2:** the G10 admission classes;
- **F5-3:** observation identity compared under Ct(X) before MISSING is written.

**Requirements:**
- B-4: no generation number is reused.
- B-5: a location reoccupied by a newer operation is never released or moved on the authority of an older
  operation's evidence (GAS-P4-004, F4-1/Wh2).
- B-6: a release is performed only by a current claimant, revalidated in the releasing transaction.

**Not claimed:** that any of the 22 windows can be dropped. The windows are ordinary corrections, not maintenance
machinery, and each needs its own replacement proof before removal.

### 4.3 Competing archival operations

- B-7: two processes never act on the same file operation at once. This keeps the accepted session advisory lock
  `file-operation:<id>` (`OperationLocks`, `tryLock`/`tryLockAll`) [SRC].
- B-8: the advisory lock is only **mutual exclusion among live database sessions**. It is **not** evidence that a
  former holder stopped (§5). Any takeover after a lost advisory session also requires the effect-owner rule of
  §5.3.
- Grouping (`capture-session-grouping:<session>`) [SRC] is a database-only writer that spans several transactions.
  - B-9: each chunk transaction MUST revalidate its own preconditions under the capture-session lock. This means its
    worksheet rows are unapplied and its run is still the current run.
  - With B-9, a late chunk from a former runner whose advisory session was lost cannot duplicate or reorder applied
    groups.
  - Whether the accepted chunk code already does this is **not established** here. It is a §12 obligation
    [UNPROVED].

### 4.4 Claims and publication consistency [RET]

Retained:
- private new-content publication (`commitNewContent`, E4);
- the `physical_location_owner` claim rules;
- `target_created` accounting;
- the P2-8g current-claim revalidation.

"Publication" in this contract means only these accepted archival publications. Revision 3.9's activation
publications, epochs and publication sequences are **removed** (§15).

### 4.5 Durable operation evidence and interrupted work

- B-10: the accepted operation-specific records stay the **only** recovery truth for ordinary effects:
  - `file_operation` and its events;
  - quarantine (Q) and unresolved (U) ledgers;
  - owner rows;
  - background jobs (with lease tokens, V0008);
  - grouping runs.
- B-11: no second, generic action journal is introduced for those effects.
- B-12: recovery of an interrupted operation (`RecoveryService`, `OperationProcessor`) starts only after §5's exclusion
  rule holds for that operation. Recovery then follows the accepted state machine unchanged. A job-lease expiry makes
  a job **eligible to be scheduled**; it never makes an outstanding effect safe to resume.

### 4.6 Separation from maintenance

Nothing in §4 reads, waits for or writes maintenance state, with one exception. The **start** of an effect interval
(§5.3) checks in the same statement that the installation is READY and bound to this host. That check is a WHERE
clause, not a protocol.

---

## 5. Former-writer exclusion (C-07, C-09)

### 5.1 Definitions

- **Writer.** A process instance on the designated host that performs, or may still perform, a **conflicting
  effect** on a subject. A process instance is identified by:
  - a random `process_instance_id` chosen at JVM start;
  - the host ID;
  - the OS process ID and the OS process start time.

  A thread is not a writer. Its process is.
- **Subject.** A `file_operation` row, together with every path it names: staging file, target path, quarantine
  paths, and the source path of a move it plans.
- **Conflicting effects** that require exclusion:
  - **filesystem:** creating, writing, renaming, moving or deleting a staging, target, quarantine or original path of
    the subject;
  - **database:** any state transition of the subject that depends on those filesystem results, such as
    PROMOTING → PROMOTED, quarantine settlement, a U intent or release, or destruction marks.

  Reads, hash verification by a non-owner that produces no transition, thumbnails and cache files are **not**
  conflicting effects.
- **Effect interval.** The span from the first conflicting effect a process starts on a subject to the moment that
  process can no longer perform any. The interval ends at one of two points:
  - **(a)** the owner returns from its last filesystem call and has flushed what it wrote (§5.5);
  - **(b)** the owner process has terminated.

### 5.2 The invariant (INV-4, restated)

**A writer W2 MUST NOT perform a conflicting effect on subject O while a former writer W1's effect interval on O may
still be open.** "May still be open" holds unless W1's interval is **known ended** under the evidence rules in §5.3.

### 5.3 Evidence of exclusion

| Evidence | Sufficient? | Why |
|---|---|---|
| W1 cleared its own effect-owner token (§5.4) in a committed transaction | **Yes** | W1 clears it only after its interval ends (§5.5) |
| W1's OS process (PID **and** start time) is absent on the designated host, checked by the reclaiming process on that host | **Yes, proposed** [UNPROVED: Proof 2] | A terminated process issues no further system calls. A process blocked in kernel I/O does **not** terminate until that I/O returns, so it still appears as present (Linux D state; on Windows the process persists while it has outstanding I/O). |
| An administrator's recorded statement that W1's host is powered off or isolated from storage and database | **Yes, manual only** | C-07 "verified host/access exclusion". Used for restore and host moves (§7.6), and in the rare case where a token's host is not this host. |
| Lost PostgreSQL connection, advisory lock now free, `pg_terminate_backend` returned true | **No** | W1's JVM and its I/O threads can outlive the session (assessment §6, counterexample 1) |
| Job lease expired, worker presence stale, heartbeat missing | **No** | These are time-based. C-09 forbids timeout takeover. |
| Service manager reported "stopped", or `WorkerProcess.stop` returned after its 10 s join [SRC] | **No** | A blocked thread outlives the join, and a kill request is not exit |
| The OS process ID is absent but its start time could not be read, or the PID was reused with an unknown start time | **No** | The identity is not established |
| W1's token names another host | **No** (automatically) | A local check cannot see another host (C-05). An administrator must resolve it. |

### 5.4 Proposed mechanism 1: durable effect-owner token on `file_operation` [NEW] [UNPROVED]

**Fields** (added to the existing `file_operation` row; no new table):
- `effect_owner_instance` (uuid);
- `effect_owner_host` (the host ID, §10.2);
- `effect_owner_pid` (bigint);
- `effect_owner_started_at` (timestamptz, the OS process start);
- `effect_interval_id` (uuid).

All five are null together, or set together.

**Acquire.** One compare-and-set statement, under the operation's advisory lock, before the interval's first
conflicting effect:

```sql
UPDATE file_operation SET effect_owner_instance = :me, effect_owner_host = :host, effect_owner_pid = :pid,
       effect_owner_started_at = :start, effect_interval_id = :interval
WHERE operation_id = :op AND effect_owner_instance IS NULL
  AND EXISTS (SELECT 1 FROM installation_state s WHERE s.mode = 'READY'
              AND s.installation_id = :installation AND s.host_id = :host)
```

- Zero rows updated means **no effect**: the operation is busy, or the installation is not READY or not bound here.
- The caller reports "in progress elsewhere" or "installation not ready". It never waits on a timer and never steals.

**During the interval:**
- Every operation state transition the owner commits includes `AND effect_interval_id = :interval` in its WHERE
  clause.
- If W1 kept running after a mistaken reclaim, its own database transitions would then fail. This is defence in
  depth; it does not replace §5.3.

**Release.** The owner clears all five fields with a CAS on its own `effect_interval_id`. It does so only after the
end condition of §5.5.

**Reclaim** (a new owner when the token is not null):
1. The token's host is this host. Otherwise refuse and leave it for administrator resolution.
2. The token is not an interval that is still active in this JVM. Each process keeps an in-memory set of its own
   open interval IDs, removed in a `finally` block.
3. The process with `effect_owner_pid` is absent, or present with a **different** start time.
4. Steps 1–3 hold. The reclaimer then sets its own token with a CAS on the old `effect_interval_id`, and records an
   operation event (`EFFECT_OWNER_RECLAIMED`, carrying the old and new instance IDs) in the same transaction.

If any step is not established, refuse (§5.7).

**Scope** (the coverage inventory is a §12 obligation; every entry must be shown):

| Effect path [SRC] | Process | Interval holder |
|---|---|---|
| Upload receiving into staging (`IngestionService`, RECEIVING) | web | the INGEST operation |
| Promotion, verification and commit (`OperationProcessor`) | worker | INGEST or REPAIR |
| Repair copies and partial replanning | worker | REPAIR |
| Quarantine moves and settlement (`QuarantineMoves`) | worker, web (administrator) | the Q entry's operation |
| Unresolved attest, release and quarantine (`UnresolvedObjects`) | web (administrator) | the U row's operation |
| Destruction (`DestructionProcessor`), including `tryLockAll` over the other writers | worker | the DESTROY operation. **Every** other writer's token must be null or reclaimable, otherwise refuse. |
| Retry and cancel (`OperationAdminService`) | web (administrator) | the operation itself |
| Volume marker creation (`init-volume`), staging cleanup | CLI, worker | **open:** a volume or staging effect with no operation row. These MUST be either run only under maintenance exclusion (§6) or given an owning record. They must never run uncovered. |

**Why this mechanism, and not a simpler one:**
- **The advisory lock alone:** fails counterexample 1. Session loss frees it while W1's I/O continues.
- **A heartbeat or lease:** time-based, so forbidden by C-09.
- **Stopping all processes before any recovery:** turns every killed worker into planned downtime, which C-09 does
  not require ("unrelated safe operations continue").
- **A per-operation OS file lock:** needs a lock object per operation, and a platform library on every effect path.
- **The token:** reuses the existing row. Its proof of exit is a same-host process-table query that Java 21 exposes
  portably (`ProcessHandle.of(pid)`, `info().startInstant()`), with no native library [UNPROVED that start-instant
  precision and availability suffice on both platforms: Proof 2].
- **Why not a separate table:** the existing operation row is already the subject's single recovery truth, and the
  token must change atomically with its transitions.

### 5.5 End of an effect interval, blocked I/O and NAS

- C-1: an owner releases its token only after its last filesystem call has returned **and** its written files and
  directory entries have been flushed (`force`/`fsync`, `FlushFileBuffers`) as the accepted `ContentIo` contract
  already does for copied content (`ContentIo` forces the target [SRC]). If the flush fails, the interval is not ended. The token stays, and the
  operation is left for recovery.
- C-2: a process blocked in storage or NAS I/O keeps its token, and holds its share of the installation fence (§6.3)
  until the I/O returns and the process ends. Recovery and maintenance therefore **refuse** for as long as the stall
  lasts. That is the intended fail-closed outcome, not bounded availability.
- C-3: after reclaim, the new owner treats every path the former owner may have written as **unverified**. It
  verifies by size and hash before relying on any byte, and follows the accepted partial-copy route (quarantine, then
  replan to a new generation) on a mismatch. It never rewrites a former owner's target in place.

  This bounds the residual of late kernel write-back of data the former owner wrote before it exited. Whether such
  write-back can occur on supported NAS clients after process exit is **[UNPROVED]**, and is characterised in Proof 2.

### 5.6 Lost PostgreSQL session while the original JVM lives

1. W1's advisory session drops. PostgreSQL frees `file-operation:<O>`.
2. W2 takes the advisory lock, reads O and finds W1's token. W1's process is present with the same start time, so W2
   **refuses** and does nothing to O. The operation shows "in progress in another process".
3. W1 continues its effect. Its next transition commits on a new pooled connection, with its interval ID in the
   WHERE clause. Then:
   - **if the commit succeeds:** W1 finishes normally and clears its token;
   - **if W1 cannot reach the database:** it stops at its next checkpoint and leaves the token. Recovery waits until
     W1 exits or reconnects.
4. If W1 dies while its effect is in progress, W2, or a later recovery pass, finds the PID absent, reclaims (§5.4)
   and runs accepted recovery.

### 5.7 When exclusion cannot be established

- The subject stays in its current state with its token.
- The process that tried reports a visible condition, naming the operation and the reason: other host, process still
  present, or identity unreadable.
- No retry loop takes the subject. Other subjects are unaffected.
- Administrator resolution has three routes:
  - wait for the process to end;
  - terminate it and confirm termination;
  - for another-host tokens, give a recorded host-exclusion statement through the offline procedure (§7.6).

---

## 6. Exclusive offline maintenance boundary (C-04, C-06, C-08, C-11)

### 6.1 What maintenance is for

Maintenance is for:
- incompatible schema migration;
- installation rebinding (new host, or a new installation ID);
- validation after restore;
- the one-time transition into Architecture C;
- an administrator resolution that the accepted runtime cannot perform safely.

Ordinary administration does **not** require maintenance. That includes D4-27 restriction confirmation, attestations
and retries.

### 6.2 Proposed durable state: one singleton [NEW] (justified in §9)

`installation_state`, exactly one row once created:
- `mode`: READY or OFFLINE;
- `installation_id`;
- `host_id`;
- `maintenance_attempt_id` (uuid of the current or last attempt);
- `ready_report_digest` (the SHA-256 of the validation report that permitted READY);
- `changed_at`.

Archive ID stays in `archive_instance` and is **not** duplicated. Binding history is in §9.2 [OD-5].

### 6.3 Proposed mechanism 2: installation fence, a local process-lifetime lock [NEW] [UNPROVED] [OD-3]

**How it works:**
- A lock object exists per installation, in a protected directory on the designated host. The directory is owned by
  root or Administrators and is not writable by the service account.
- Every compatible producer holds a **shared** lock on it from before its first database connection until process
  exit.
- The maintenance tool takes an **exclusive, non-blocking** lock.
- If the exclusive lock succeeds, no compatible producer process of this installation is alive on this host. If it
  fails, maintenance refuses.

**Why it is needed:**
- The token (§5.4) covers only subjects with an open interval.
- Maintenance must also exclude idle or between-request processes. Without the fence, such a process could start an
  interval, or a database-only write, after maintenance believed the host quiet.
- Stopping services is not proof (§5.3).
- An OS process scan cannot reliably recognise every compatible process (copied or renamed jars). The fence gives a
  positive answer for every process that runs the compatible startup code.

**Boundary:**
- The fence is local. It says nothing about other hosts (C-05). The administrator excludes those operationally
  (§6.4 step M-4).
- It also does not cover legacy builds that predate it. The freeze and credentials cover those (§6.4 steps M-2 and
  M-3, and §7.7).

**Library and identity questions are [OD-3]:**
- Java `FileChannel.lock` in a protected directory, with a rule that no other code opens the lock path, is
  dependency-free.
- JNA with a lock and identity on the same handle is stronger.
- Either way, the lock MUST be held for the process's life and never released while effect threads remain.

### 6.4 Entry sequence (each step fails closed; nothing is reopened automatically)

The maintenance tool is the **target** build, run by an administrator with administrative OS rights and the
administrative database credential (§10). These are separate from the service account and the runtime role.

| Step | Action | Evidence recorded in the maintenance receipt (§9.2) |
|---|---|---|
| **M-1 Bind** | Load the installation configuration. Connect with the admin credential. Read `archive_instance.archive_id` and, if present, `installation_state`. The Archive ID MUST equal the configured expected Archive ID. The installation and host IDs MUST equal this host's values, unless the step is a rebind (§7.6). | Archive ID, installation ID, host ID, database identity (`system_identifier`, database OID) |
| **M-2 Freeze** | Disable and mask the services and their restart policies (systemd `Restart=on-failure` [SRC]; WinSW restart actions), the scheduled tasks and the supported ad-hoc launch paths (C-08). The freeze persists across reboots and across tool crashes. | Unit and task states before and after |
| **M-3 Close runtime admission** | Set the runtime role `NOLOGIN`, and retire runtime credentials still held by legacy configurations (§7.7). This prevents new sessions; it does **not** end existing ones. | Role state |
| **M-4 Exclude producers** | Stop services. Take the **exclusive installation fence** (§6.3); on failure, refuse. For legacy builds that hold no fence, and for every former host, the administrator records verified termination or isolation. A process still present or unverifiable means refuse. | Fence result; process and host exclusion statements |
| **M-5 Drain** | Using the admin connection, list every backend connected to this database other than the tool's own: for the runtime role, for legacy roles, and for any other non-administrative role. Terminate each by PID and confirm absence by `(pid, backend_start)` on a fresh snapshot. Refuse if any backend remains, if a prepared transaction exists (`pg_prepared_xacts`), or if an unexpected privileged backend is present. | Backend list before and after |
| **M-6 Mark OFFLINE** | One transaction: `installation_state.mode = 'OFFLINE'` with a new `maintenance_attempt_id`, and a receipt row. Before the table exists (first transition), steps M-2 to M-5 are the fence. | Attempt ID |

**Interrupted entry.** The freeze, `NOLOGIN` and OFFLINE all persist, and the fence ends with the tool's process.
A rerun repeats M-1 to M-5 from the beginning, and M-6 is idempotent on the existing attempt. Nothing reopens
because a process died.

### 6.5 Exit sequence

| Step | Action |
|---|---|
| **X-1** | Validate (§7.2 and §8). Validation is read-only. |
| **X-2** | One transaction: compare-and-set `mode` OFFLINE → READY on the current `maintenance_attempt_id`, set `ready_report_digest`, and insert a receipt row. |
| **X-3** | Install or pin the new launch artifact. Point the configuration at the runtime credential. Restore runtime `LOGIN`. Unmask the services. |
| **X-4** | Release the fence by exiting the tool. Services then start. Each process re-checks the schema, the binding and READY before its first effect (§10.3). |

---

## 7. Migration and recovery (C-04, C-08, C-09, C-11)

### 7.1 Migration

Migration runs only in an open maintenance attempt (OFFLINE, fence held, sessions drained). The admin credential runs
Flyway with the accepted configuration [SRC]:
- schema `gas`;
- one transaction per migration, with the history row in the same transaction;
- `validateOnMigrate`;
- refusal of unknown and future migrations.

**Requirements:**
- E-1: `validateWithResult` MUST pass **before** any migration. `status()` = BEHIND is reported before validation
  [SRC] and is not validation.
- E-2: the actual history MUST be a prefix of the target build's chain, with matching checksums. A hotfix or foreign
  history refuses.
- E-3: every migration MUST be transactional. A non-transactional statement (for example `CONCURRENTLY`) is refused
  at build time.
- E-4: a coherent pre-upgrade checkpoint (backup of the database plus storage) MUST exist and be recorded in the
  receipt, so that BUILD-SPEC §56's restore path is available.

### 7.2 Validation before reopening

Validation is read-only and covers:
- schema CURRENT for the tool's build (post-migration validator [SRC]);
- the role and privilege layout of §10.1;
- the binding;
- historical admission (§8);
- the existing integrity checks named by the accepted acceptance specification.

The digest of the report goes into READY.

### 7.3 Recovery behaviour

| Situation | Required behaviour |
|---|---|
| Successful migration | Validation, then READY (§6.5). |
| Failure before a committed checkpoint | The failed migration's transaction rolls back, and the history is unchanged. The attempt stays OFFLINE with the freeze. Fix and rerun, or restore the checkpoint. |
| Failure after a committed checkpoint | The history shows the last committed migration, and the schema is an intermediate prefix. **No ordinary process can run:** OFFLINE, `NOLOGIN`, the freeze and the fence all hold. Rerun the same target build: it validates the prefix and continues. Alternatively restore the pre-upgrade checkpoint. An intermediate schema is never "compatible" for ordinary admission, so there is no manifest and no checkpoint admission. |
| Unknown COMMIT (migration, READY, rebind or init) | §7.4 |
| Interrupted maintenance (tool crash at any step) | The freeze, `NOLOGIN` and OFFLINE persist. The next administrator repeats M-1 to M-5, which re-proves exclusion: the former tool process is gone, its backends are drained and the fence is acquired. They then read the history and `installation_state` and continue from the actual state. |
| Runtime identity mismatch | §10.3 [OD-4] |
| Installation rebinding | §7.6 |
| Recovery after verified former-writer termination | Ordinary: reclaim the token (§5.4), then accepted `RecoveryService`/`OperationProcessor` recovery. Maintenance: M-4/M-5, then continue. |
| Exclusion cannot be proved | Ordinary: the subject is refused (§5.7). Maintenance: entry refuses at M-4 or M-5, and the installation stays in whatever state it was (READY with services stopped, or OFFLINE). Manual action follows. |

### 7.4 Unknown COMMIT

1. Before COMMIT, the tool knows its own backend `(pid, backend_start)` and the exact rows it is writing: the history
   version, the attempt ID, or the old and new binding.
2. After a connection error, it opens a fresh admin connection and confirms that backend is absent. If needed it
   terminates the backend and confirms again. If absence cannot be confirmed, it decides nothing and the state stays
   OFFLINE.
3. It reads the exact rows:
   - **the committed outcome is present:** reuse it, with no second event and no new identifier;
   - **the old state is present:** retry with freshly read preconditions;
   - **anything else:** refuse.

Ordinary business transactions keep their accepted handling (idempotent operation state machines and row versions).
The same rule applies to them: a fresh read while the original backend may still be alive is not conclusive.

### 7.5 Archive initialization

`init-archive` keeps its accepted single-row behaviour (V0002) [SRC]. The first Architecture C migration creates
`installation_state` with:
- `mode = 'OFFLINE'`;
- the installation and host IDs supplied by the maintenance tool.

That migration does not write `archive_instance`, and it never creates or copies an Archive ID. A second
`init-archive` refuses, as accepted.

### 7.6 Installation rebinding (new host, new platform, new installation ID)

Rebinding is a maintenance attempt, never ordinary startup.

**Preconditions, recorded in the receipt:**
- a coherent restore of database and storage, as one set;
- the **former host verified powered off or isolated** from storage and database;
- the former host's database credentials retired;
- the freeze on the new host.

**Rebinding transaction:** compare-and-set of `installation_state` from (old installation ID, old host ID) to the
caller's own (new installation ID, new host ID), with mode OFFLINE, plus a receipt row carrying both pairs.
- The Archive ID is compared and **never changed**.
- File locations stay volume-relative (ADR-0002), and volume markers are checked against the Archive ID [SRC].
- Platform paths are configuration only.

**After a crash or unknown COMMIT:**
1. Re-prove exclusion (M-4, M-5).
2. Read the binding:
   - the new pair is present ⇒ done;
   - the old pair is present ⇒ retry;
   - anything else ⇒ refuse.

  Validation (§7.2), then READY, then reopen.

Tokens naming the former host refuse automatically (§5.3). The receipt's former-host exclusion statement then lets
the administrator reclaim them deliberately as part of validation.

### 7.7 Legacy executables (C-08)

Legacy executables are the accepted Phase 0–3 builds and pre-Architecture C Phase 4 builds. They hold no fence,
carry no token and never read `installation_state`.

**Exclusion during maintenance:** M-2 (freeze), M-3 and M-5 (no sessions), and M-4 (verified termination).

**Exclusion after maintenance:**
- **(a)** Their configured credential is retired: the former schema owner's password is rotated, and it is moved out
  of the service-readable configuration. A restarted legacy JVM cannot authenticate.
- **(b)** The accepted AHEAD refusal at startup [SRC].
- **(c)** The launch artifacts are replaced or pinned.

**Not claimed:** that (b) covers every legacy entry point before its first effect. GAS-P4-005 shows an already
running legacy web process keeps writing. Verifying every entry point is proof obligation 1 (Proof 1).

---

## 8. Historical admission (C-10, R39-T)

### 8.1 When it runs

Historical admission runs as a read-only validation:
- during the one-time transition into Architecture C;
- after any restore or rebind.

Ordinary runtime under §4 and §5 maintains the invariants afterwards, so admission does not run at every startup.

### 8.2 Acceptable inherited evidence (R39-T)

Under R39-T (owner-approved in C-10), inherited Q and U rows are presumed to come from the accepted writers. They are
admitted only with a **positive structured witness**:

| Witness | Basis |
|---|---|
| **Own-copy Q** | The reason is PARTIAL_COPY, UNVERIFIED_COPY or CANCELLED_COPY. Its operation exists with kind INGEST or REPAIR. The source parses as `originalPath(content, g)` with g taken **from the row**. The destination parses as `quarantinePath(operation, allowed suffix)`. The volume equals the operation's. The evidence matches its reason's shape. |
| **W1 operation-copy U** | Kind OPERATION_COPY, operation INGEST or REPAIR, generation null. Content equals the operation's content, or is null for an unpersisted ingest. The path parses as `originalPath(uuid, g)`. |
| **Destruction-side and attested U** | Bound to the known object they record (accepted `DestructionProcessor.newRecord`, attestation evidence) |

**Not evidence:**
- the absence of other references;
- a generation inequality;
- event detail text;
- current byte presence alone;
- the operation's *current* target as proof of a past one.

### 8.3 Independent conflict checks (never satisfied by a witness)

1. An active foreign owner at the key: a non-final owner row of another operation.
2. A private ingest UUID belonging to another unpersisted INGEST.
3. Generation and physical ownership, where the persisted generation g of X was created by a different operation.
4. An OPEN U at the key from an incompatible writer role.
5. Filesystem aliases at the key: case, Unicode normalisation, or a link to a reference outside the class.
6. Movement authority, which is re-derived only from the current target, the current claim and current evidence.
7. Release authority, which comes only from the current claimant set.

### 8.4 Equivalence classes (the eleventh-assessment corrections, as relayed in assessment §4.2 and §6)

- A **persisted** content X is one subject across operations. Repeated REPAIRs R1 and R2 of X at different
  generations are **valid** and MUST be admitted. The UUID appearing in another operation's rows is not a conflict
  by itself; the exact physical key `(volume, path, generation)` decides.
- A **private** INGEST UUID belongs to its own operation only. Its appearance in another operation is a conflict.
- Legitimate cross-operation associations are preserved and are **not** reported as foreign owners. They are:
  - destruction-side U rows bound to a known object;
  - a later repair's Q of an earlier generation;
  - an administrator's attestation.

### 8.5 Outcomes

| Class | Outcome |
|---|---|
| Admitted | No action |
| Final and inert (settled, no live claim) | No action. Retained as history. |
| **UNPROVED** (fails its witness shape) | Refused. A finding names the row. |
| UNSAFE-ACTIVE or UNSAFE-AMBIGUOUS (a conflict check fails) | Refused. A finding names the row and the check. |

**Refusal scope is [OD-7]:**
- **Global:** READY is refused until the findings are resolved by accepted administrator operations, or by a
  reviewed offline plan.
- **Scoped:** the affected operations are placed in the accepted BLOCKED / STATE_UNKNOWN state and READY proceeds.
  This is only possible where every finding maps to an existing blocking state.

Never permitted, under either option: rewriting rows, upgrading evidence, releasing foreign claims, deleting unknown
bytes, or waiving a finding.

### 8.6 What the records cannot answer

These are information limits, not missing queries:
- whether a writer-shaped row was produced by an accepted writer or by manual SQL (this is R39-T itself);
- the chronology of past target assignments (there is no structured assignment record; V0009 events hold only
  from/to state and free text);
- for a W1 U, the old target snapshot (this is the writer contract only);
- whether bytes at a location were changed between two observations.

Each such gap resolves to refusal or to an administrator question, never to inference.

---

## 9. Minimal durable state

### 9.1 Inventory

| Item | Safety property | Why existing records cannot provide it | Writer | Crash survival | Validated in recovery by |
|---|---|---|---|---|---|
| **`installation_state` singleton** (§6.2) [NEW] | INV-7, INV-8: an archive-scoped, durable "not validated, do not reopen" (OFFLINE). Binding to one installation and host (C-05, C-11). Start-of-interval READY check (§5.4). | Flyway history says only which migrations ran, not whether validation finished. `archive_instance` holds the Archive ID, not the installation or host. Role `NOLOGIN` is cluster-global, can be re-enabled by hand, and does not travel with a restored database. Configuration is host-local and copyable. | Admin role only (the runtime role has SELECT) | A transactional row. OFFLINE persists, and a restored database brings its mode with it. | M-1 reads it. X-2 compare-and-sets on the attempt ID. §7.4 rereads it after an unknown COMMIT. |
| **Effect-owner fields on `file_operation`** (§5.4) [NEW] | INV-4 for ordinary takeover after session loss | The advisory lock is session-scoped (counterexample 1). Job leases and presence rows are time-based. Nothing records which process owns an in-progress effect. | Runtime role, by compare-and-set only, under the operation's advisory lock | A transactional row. A crash leaves the token, so recovery must prove exit. | The reclaim rule (§5.4). Admission (§8) reports tokens naming another host. |
| **Maintenance receipt** (§9.2) [NEW, representation OD-5] | INV-1, INV-6: durable old → new binding, the attempt, and the operator's exclusion statements, written atomically with each `installation_state` transition | `change_history` is the researcher-facing history, with its own visibility rules (ADR-0008). `file_operation_event` is per operation. Neither can hold a typed binding transition atomically with the singleton. | Admin role only; append-only (no UPDATE, DELETE or TRUNCATE) | Inserted in the same transaction as the singleton change | §7.4 and §7.6: the receipt with the attempted values identifies a committed transition |
| **Installation fence object** (§6.3) [NEW, local file, not database] | INV-7: no compatible producer is alive at maintenance entry | No database object can prove that a process without a session is gone | Installer creates it. Producers hold it shared; the tool holds it exclusive. | OS lock released at process exit; the object persists | M-4. A missing or replaced object makes maintenance refuse; it is then reprovisioned offline under verified process exclusion. |
| **Configuration keys** `installation.id`, `installation.hostId` source, `archive.expectedId`, admin secret source [NEW] | Binding and authority (§10) | The configuration has no installation or Archive ID today [SRC: root keys are database, server, logging, storageRoots, files] | Installer or administrator | File | `check-config`, M-1 and startup |

Proposed new database tables: **one** (`installation_state`), **or two** if OD-5 chooses a dedicated receipt table.
Proposed new columns: five on `file_operation`. There are no other control tables, no mutation gates, no
per-connection declarations and no journal.

### 9.2 Receipt representation [OD-5]

**Recommended:** a small append-only `installation_transition` table with these columns:
- attempt ID;
- kind (ENTER_OFFLINE, MIGRATED, REBIND, READY);
- old and new installation and host IDs;
- Archive ID (compared, never changed);
- database identity;
- validation report digest;
- operator principal and recorded statements;
- time.

**Alternative:** a typed entry in an existing history table, if the reviewer shows that one can hold it atomically
and with admin-only write access.

A log file is **not** acceptable as the only record.

---

## 10. Authorization and identity (C-06, C-11)

### 10.1 Database authority [NEW] [OD-6]

| Role | Rights | Never |
|---|---|---|
| **Runtime** (dedicated per installation; LOGIN only while READY) | DML on the application tables, as the accepted writers need; SELECT on `installation_state`; CAS on the token columns; execute on the accepted trigger functions | DDL; UPDATE of `installation_state` or the receipt; role changes; `pg_terminate_backend` of other roles; superuser |
| **Administrative** (maintenance only) | Schema owner (Flyway); write on `installation_state` and the receipt; `NOLOGIN`/`LOGIN` on the runtime role; terminate backends of this database's installation roles | Used by services; stored where the service account can read it |

Today a single role owns the schema and migrates "as the service account" [SRC: `install.sh` step 3]. Separating the
roles is a deployment change. Whether the administrative authority is a role owner or a DBA account is [OD-6].

The accepted D4-27 restriction protocol and preservation writers need only runtime DML. This is to be confirmed by a
privilege inventory, which is §12 obligation 5.

### 10.2 Identities

| Identity | Source | Use |
|---|---|---|
| Archive ID | `archive_instance` (V0002) and volume markers [SRC] | Never changed. The configured expected value is compared at M-1 and at startup. |
| Installation ID | configuration (new) | Bound in `installation_state` |
| Host ID | an OS machine identifier (Linux `/etc/machine-id`, Windows `MachineGuid`) or a provisioned protected file [OD-8] | Bound in `installation_state`. A copied configuration on another host does not match. A cloned VM can match: that is a residual, covered operationally by C-05. |
| Process identity | random instance ID, PID and OS start time | Effect-owner token (§5.4) |
| Executable and schema | build version, plus the compiled Flyway chain and checksums [SRC] | The startup schema check (CURRENT only); the maintenance tool's history validation |

### 10.3 Runtime identity mismatch [OD-4]

Every process MUST check the following before its first effect:
- schema CURRENT;
- `installation_state` READY;
- the binding equal to its configured installation and host;
- the Archive ID equal to the configured expected value.

On failure it refuses effects. Diagnostic web status stays available on ordinary database unavailability (accepted
behaviour), and every interval acquisition re-checks READY and the binding (§5.4).

**Open [OD-4]:** what a process does when a mismatch appears **after** it started:
- **Recommended:** latch, start no new intervals, let open intervals finish (or stop at their next checkpoint while
  keeping their tokens), then exit.
- **Alternative:** remain running in a diagnostic-only state.

### 10.4 Recovery and rebinding authority

Only the administrative path, under §6, can:
- reopen (READY);
- rebind;
- reclaim tokens that name another host;
- apply historical resolutions.

The runtime can reclaim only same-host tokens with proven exit (§5.4). No path is automatic after a timeout.

---

## 11. Failure matrix

"Unproved" means a feasibility proof or an implementation test must show the mechanism works. Describing the intended
behaviour is not equivalence with revision 3.9 or with anything else.

| # | Case | Required safety behaviour | Proposed mechanism | Unproved? |
|---|---|---|---|---|
| 1 | Two workers compete for the same archival content | Exactly one acts. No deadlock. Generations are never reused. Claims are released only by the current claimant. | Advisory `file-operation` lock (B-7); Ct(X) coordination and P2-8 windows; F5-1 reservation (§4.2) | Ordinary corrections designed, **not implemented**. LO and LP tests are future work. |
| 2 | PostgreSQL session lost while the original worker lives | No takeover while W1 may act | Effect-owner token; same-host PID and start-time check (§5.4, §5.6) | **Yes:** Proof 2 |
| 3 | Worker dies during filesystem I/O | Recovery to one result, with no adopted unknown bytes | Token reclaim after confirmed exit; accepted recovery; reverify all touched paths (C-3) | **Yes:** Proof 2, plus accepted RecoveryIT re-run |
| 4 | Blocked NAS I/O, then recovery | No takeover or maintenance during the stall. Visible state. Recovery after the I/O returns and the process ends. | C-2: the token and the fence share persist while the process exists; flush before release | **Yes:** native stall characterisation and NAS write-back (Proof 2) |
| 5 | Legacy executable restart during maintenance | No start, and no write if started | M-2 freeze; M-3 `NOLOGIN`; credential retirement; AHEAD refusal (§7.7) | **Yes:** every legacy entry point (Proof 1; obligation 1; GAS-P4-005) |
| 6 | Automatic service restart during migration | No producer start | M-2 masks units and restart policy. If a compatible process starts anyway, it blocks on the shared fence (it cannot get it while the tool holds it exclusive) and, when connecting, gets `NOLOGIN` refused. | **Yes:** Proof 1 |
| 7 | Database connection survives application shutdown | No late commit | M-5 drain by `(pid, backend_start)` with confirmed absence; prepared transactions refuse | **Yes:** Proof 1 (orphan backend) |
| 8 | Migration crash before a committed checkpoint | No partial migration; stays offline | Flyway transaction per migration; OFFLINE, freeze, fence | Partly: Flyway per-migration atomicity is a framework contract, and E-3 needs a build check |
| 9 | Migration crash after a committed checkpoint | No ordinary process on an intermediate schema; deliberate continue or restore | OFFLINE, `NOLOGIN`, freeze; history-prefix validation; rerun or restore (§7.3) | **Yes:** Proof 1 |
| 10 | Unknown transaction outcome | Decide only after the attempting backend is confirmed gone; reuse or refuse | §7.4; accepted idempotent business state machines | **Yes:** Proof 1 (READY, rebind, migration) |
| 11 | Runtime identity mismatch | No effects under the wrong binding, archive or schema | Startup checks and interval-start READY/binding check (§10.3) | Policy open ([OD-4]); mechanism unproved |
| 12 | Replacement host starts while the old host may still write | The new host does not reopen until the old host is verified excluded; the old host cannot rejoin | Rebind only under maintenance with a recorded former-host exclusion; binding mismatch refuses the old host's processes on reconnect; credential retirement; tokens naming another host refuse | Operator premise (C-07); binding check unproved (Proof 1, rebind crash) |
| 13 | Inherited historical ownership ambiguity | No inferred provenance; refuse UNPROVED and unsafe rows | §8 witnesses and conflict checks under R39-T | **Yes:** realistic inherited fixtures (obligation 7) |
| 14 | Repeated repair or cross-operation evidence conflict | Valid repeated repairs and bound associations admitted; real foreign owners refused | §8.4 exact-key equivalence | **Yes:** obligation 7 fixtures (R1/R2 repairs of X, a destruction-bound U, a foreign non-final owner) |

---

## 12. Focused feasibility proofs (to be separately authorized; not executed here)

Common rules for both proofs:
- **Environment:** non-production, on Linux and Windows, against PostgreSQL 16 and 18. Use authentic accepted build
  artifacts where named.
- **Determinism:** barriers and seams, not sleeps. Every session and process is recorded.
- **Exact assertions:** database state, bytes and hash per path, tokens, backends, and process existence.
- **Integrity:** a result is never relabelled to pass.
- **Scope:** each proof covers only its stated claims. Passing a proof does not accept WP1.

### Proof 1: exclusive maintenance boundary

| | |
|---|---|
| **Prerequisites** | A prototype (non-production) maintenance tool that runs M-1 to M-6, migration, validation and X-1 to X-4. A prototype `installation_state` with receipt. A runtime/admin role split on a scratch cluster. A fence prototype for each OD-3 candidate. The authentic accepted Phase 3 build and the current Phase 4 build as legacy artifacts. Installed services: systemd units, and WinSW under virtual accounts. |
| **Setup** | (a) A legacy web process receiving an upload, and a legacy worker mid-job. (b) A compatible worker with an open effect interval. (c) An orphan backend: a psql session as the runtime role in an open transaction, with no JVM. (d) A prepared transaction. (e) A test migration chain with 3 steps, a fault injected after step 2 commits, and an unknown-COMMIT fault on step 3. (f) A rebind to a new installation and host pair, with a crash injected after the rebind transaction and before READY. (g) Service restart policies left enabled in a negative control. |
| **Observable results** | Entry refuses while any of (a), (b) or (g) is alive, unverified or able to restart. (c) and (d) are drained, or entry refuses. No migration statement runs before M-5 completes. After the fault at step 2, nothing ordinary starts; a rerun continues from history, or the restore path is used. Step 3's unknown COMMIT is decided only after the backend is confirmed gone. After the rebind crash, a rerun sees the new pair and does not rebind twice. READY happens only after validation. Legacy artifacts restarted after X-4 cannot authenticate or refuse before any effect. Every legacy entry point (web upload, worker job, every CLI command) is enumerated with its observed outcome. |
| **Failure criteria** | Any producer effect, or any committed runtime-role transaction, during OFFLINE. Reopening without validation. A second rebind or a changed Archive ID. A decision taken while the original backend lived. Any legacy entry point that writes before refusing. A fence acquired while a compatible process lived. |
| **Coverage** | Linux and Windows. PostgreSQL 16 and 18. Local volume; a NAS volume where available, labelled with its type. |

### Proof 2: former-writer fencing

| | |
|---|---|
| **Prerequisites** | A prototype effect-owner token (§5.4) on a scratch schema, wired into one INGEST promotion path and one quarantine move. A test-only seam that blocks a copy inside a filesystem call. A stalled storage target: Linux FUSE or a paused NFS export; Windows SMB with the server paused. |
| **Setup** | (a) Worker W1 starts a promotion and acquires the token. (b) W1's advisory backend is terminated while W1's JVM continues writing. (c) W2 starts and attempts the same operation. (d) Variant: W1 is blocked in a real kernel or NAS call. (e) Variant: W1 is killed with SIGKILL / TerminateProcess during a write, and W2 or recovery runs afterwards. (f) Variant: the token names another host ID. (g) Variant: the OS process start time is unreadable, or the PID is reused. |
| **Observable results** | In (c) and (d), W2 performs no filesystem effect and no transition on O, and reports "in progress elsewhere". W1's later transitions succeed with its interval ID. In (e), after the process is confirmed absent, W2 reclaims with an `EFFECT_OWNER_RECLAIMED` event, verifies the bytes and reaches one accepted result (completed, or quarantined and replanned). In (f) and (g), the reclaim refuses. ProcessHandle start-instant availability and precision are recorded on both OSs. Bytes on the NAS are compared after the stall ends, to detect any late write-back. |
| **Failure criteria** | Any W2 effect while W1's process exists. A reclaim by elapsed time. A reclaim of another host's token. A duplicate File or content. An adopted unverified byte. Late write-back that changes bytes W2 relied on. |
| **Coverage** | Linux and Windows. PostgreSQL 16 and 18. Local and NAS volumes. The Java-seam blocking variant is labelled separately from kernel-stall characterisation. |

If either proof shows that a mechanism needs a generic multi-state framework to work, the result goes back to the
owner as a failed requirement with a complexity comparison. It does not trigger another expansion
(ENGINEERING-DESIGN-INSTRUCTIONS §3, §12 of the assessment).

**Proof obligations from D4-31 that these proofs leave to implementation testing:**
- 5: privilege inventory;
- 7: inherited-state fixtures;
- 9 and 10: the full Phase 0–3 regression matrix with Phase 4 on every platform;
- the B-9 grouping chunk revalidation;
- the §5.4 effect-path coverage inventory.

---

## 13. Open owner decisions

| ID | Decision | Recommendation | Alternatives and consequences | Blocks |
|---|---|---|---|---|
| **OD-1** | D-LP-9 (a), administrator coordination (GAS-P4-003) | Authorize separately, as already assessed | Leave GAS-P4-003 open: the administrator check deadlock remains | Implementation of B-1 for `app_user` paths only |
| **OD-2** | D-LP-3, the Conflict after three abandoned attempts | Approve as previously proposed | Keep the accepted retry behaviour: no conflict outcome change | Implementation only |
| **OD-3** | Installation-fence library and identity | Java `FileChannel` lock in a root- or Administrator-protected directory, with no new dependency, plus a build rule that nothing else opens the path. Move to JNA same-handle only if Proof 1 shows a gap. | JNA same-handle: stronger identity, but a native dependency to maintain. JDK 22+ FFM: a JDK baseline change. No fence: maintenance depends on process scans that cannot recognise copied jars. | Contract acceptance **(mechanism)**; implementation (library) |
| **OD-4** | Runtime identity mismatch after startup | Latch: no new intervals; open intervals finish or stop at a checkpoint keeping their tokens; exit. Web diagnostics stay up on ordinary database unavailability. | Diagnostic-only continued run: simpler, but processes linger with stale identity | Implementation |
| **OD-5** | Binding and transition receipt representation | A dedicated append-only `installation_transition` table (the second table) | A typed row in an existing history table, if it can be made atomic and admin-only. A log file alone is not acceptable. | Implementation (DDL) |
| **OD-6** | Administrative database authority | A dedicated per-installation admin role that owns the schema and the runtime role, with no superuser for routine maintenance | A DBA or superuser for maintenance: simpler privileges, wider blast radius | Contract acceptance (C-06 boundary) |
| **OD-7** | Historical refusal scope (§8.5) | Global READY refusal for the one-time transition; scoped BLOCKED only for finding classes proven to map to existing states | All-scoped: more availability, and each class needs a proof. All-global: one bad row stops the archive. | Contract acceptance |
| **OD-8** | Host ID source | OS machine identifier, recorded at install | A provisioned protected file: survives OS reinstall and differs from clones, but can be copied by an administrator | Implementation |
| **OD-9** | Receiving-timeout behaviour under C-09 | A timed-out RECEIVING upload is abandoned only when its interval has ended (thread finished) or its process is proven gone. Otherwise it stays visibly "receiving, stalled". | Keep timeout abandonment as is: violates C-07 if the receiving thread is merely stalled | Contract acceptance (behaviour visible to users) |

The Architecture C operating model (C-01…C-12) is **not** reopened by any of these.

---

## 14. Traceability

**Legend:**
- **P:** preserved requirement.
- **R:** mechanism replaced, relative to revision 3.9.
- **D:** outstanding decision.
- **U:** unproved safety claim.

### 14.1 D4-31 rules

| Rule | Where | Classification |
|---|---|---|
| C-01 Preserve accepted functionality | §3 INV-1, INV-6, INV-10; §4.5; §12 regression obligation | P |
| C-02 Retain WP1 corrections | §4.1–§4.4 (order, CR-1…CR-4, P2-8a…g, F5-1…F5-3, PO-B…D) | P; U (not implemented) |
| C-03 Remove generic machinery | §15 | R |
| C-04 Exclusive offline maintenance | §6, §7 | P; U (Proof 1) |
| C-05 Designated producer host | §2.1 A-1, A-3; §6.3 boundary; §10.2 | P; D (OD-8) |
| C-06 Separate authority | §10.1 | P; D (OD-6) |
| C-07 Verified former-writer exclusion | §5 | U (Proof 2) |
| C-08 Legacy exclusion | §6.4 M-2–M-5; §7.7 | U (Proof 1, obligation 1) |
| C-09 Fail closed | §5.7, §7.3, §8.5; OD-9 | P; D (OD-7, OD-9) |
| C-10 Bounded historical trust | §8 | P; U (obligation 7) |
| C-11 Permanent identity | §7.5, §7.6, §10.2, §9 receipt | P; D (OD-5) |
| C-12 Phase boundaries | §2.2 | P |

### 14.2 Findings (none closed by this contract)

| Finding | Status | Contract coverage |
|---|---|---|
| WP1-R1 (L1 locking protocol) | OPEN | §4 (ordinary order retained), §5–§7 (replaces 3.9's admission and maintenance machinery); U |
| F5-4 (unsafe inherited rows participating) | OPEN | §8 admission before READY; OD-7; U |
| GAS-P4-002 (L1 internal order) | OPEN | §4.1 B-1; U |
| GAS-P4-003 (administrator check deadlock) | OPEN | OD-1 |
| GAS-P4-004 (claim release after reoccupation) | OPEN | §4.2 B-5, B-6 (P2-8g); U |
| GAS-P4-005 (old web writes against newer schema) | OPEN | §7.7, M-3/M-5; obligation 1; U |
| F5-1 generation reservation | Resolved (architectural); preserved | §4.2 B-4 |
| F5-2 G10 classes | Resolved; preserved | §4.2 |
| F5-3 observation identity | Resolved; preserved | §4.2 |

### 14.3 Accepted Phase 0–3 invariants

| Invariant | Coverage |
|---|---|
| Permanent IDs, Archive ID (BUILD §7–8, V0002) | INV-1; §7.5, §7.6 |
| Immutable originals and hashes (TX, IV) | INV-2; C-3 reverify |
| No success inferred from names (TX-002); crash-safe ingestion (TX-003/005) | INV-6; B-10, B-12 |
| Storage failure visible (TX-004/008); WAITING_FOR_VOLUME | C-2; §5.7 |
| Optimistic concurrency (SEC-008) | §4 unchanged |
| Restriction and non-disclosure (SEC-004, D4-16, D4-27) | INV-10; runtime DML only (§10.1) |
| Jobs survive restart (OP-001/004); lease tokens (V0008) | B-12 (eligibility, not exclusion) |
| Portable volume paths and markers (ADR-0002) | §7.6 |
| Upgrade refusal AHEAD/BEHIND; known recoverable migration (UP-004/005, BUILD §56) | §7.1, E-4, §7.7 |
| Grouping order and boundaries (Phase 3) | B-9; U |

---

## 15. Complexity budget and comparison with revision 3.9

| Measure | Revision 3.9 (proposal counts) | This contract |
|---|---|---|
| New control tables | 9 | **1** (`installation_state`), **or 2** with OD-5's receipt table |
| New columns on existing tables | binding columns on the singleton, among others | 5 on `file_operation` (token) |
| Tables under a generic mutation gate | 49 | **0**. The archival constraints and triggers are unchanged. |
| Control units (CU) | 36 | 0. There are six administrative stages (enter, migrate, validate, rebind, ready, reopen); these are procedures, not transaction protocols. |
| Per-connection protocol and epoch declarations | every writer transaction | none |
| Maintenance run, controller generation, connection registry | yes | none: maintenance drains everything instead of handing over live backends |
| Generic action, window and publication journal | yes | none: operation-specific records stay the truth |
| Migration compatibility manifest and checkpoint admission | yes | none: no ordinary process runs on an intermediate schema |
| Lease repair and restore state machines (R-2L, R-3L, R-5H) | yes | offline reprovision and rebind (§7.6) |
| Native fence | JNA, same handle | one local fence; library choice open (OD-3) |
| Ordinary preservation windows | 19 P2-8 / 22 Phase 2 | **unchanged** (no reduction claimed) |
| Historical admission | RA, PC and W witnesses with seven checks | the same witnesses and checks; offline validation only; no runtime gate |

**What is removed and why it is safe to remove** (each depends on a stated replacement):

| Removed | Depends on |
|---|---|
| Gates, epochs and registrations | M-2 to M-5 excluding every producer and session before any change (Proof 1) |
| Controller generations | No live backend survives maintenance entry |
| Manifest | Intermediate schemas are never admitted |
| Journal and publications | The accepted operation records cover ordinary effects. Maintenance offers no arbitrary effect beyond migration, validation and rebind. |

**What is added** (each is the smallest found for its requirement):
- the token, because the advisory lock alone fails;
- the singleton, because history and roles do not carry "validated";
- the fence, because a stopped service is not proof;
- the receipt, because binding history must be atomic.

**What could be removed later if proofs allow:**
- the fence, if OD-3's alternative of supervisor and process evidence is shown equally strong for maintenance entry;
- the receipt table, if an existing history table is shown sufficient (OD-5).

---

## 16. Consistency updates for later (reported, not made)

Under this assignment, the builder has not edited any other document. If this contract is accepted, these would need
updating:
- **`PHASE-4-PLAN.md` §10 and §10.1:** the WP1 gate content and the contract reference. Its WP1 row still lists
  "LockSequence … LO-1…LO-3" as the whole WP1 locking scope.
- **`PHASE-4-DECISIONS.md`:** recording OD-1…OD-9 outcomes when the owner decides them.
- **`issues.md`:** cross-references from GAS-P4-002…005 to this contract.
- **`deploy/linux/install.sh` and `deploy/windows/*`:** the "migrate as the service account" instruction conflicts
  with C-06. That is a later implementation change, not a documentation edit now.
- **`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md` §5.5.3:** describes L1 as "the accepted Phase 3 chain, unchanged",
  which the retained order supersedes for WP1 (GAS-P4-002).

**WP1 remains unaccepted. WP2 remains on HOLD. No finding is closed. Nothing in this document is implemented or
authorized for implementation.**
