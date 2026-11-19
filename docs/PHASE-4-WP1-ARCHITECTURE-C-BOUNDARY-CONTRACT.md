# Phase 4 WP1: Architecture C replacement boundary contract

**DRAFT — NOT ACCEPTED — NOT AUTHORIZED FOR IMPLEMENTATION**

| | |
|---|---|
| Repository | `Rebowitz/GenealogyArchiveSystem`, branch `phase-4` |
| Draft | **Draft 4 (Draft 3 verification corrections V1, V4)**. Draft 1 was `55041be`; Draft 2 `095ba3f` (verified at `ef5859e`); Draft 3 `a9ba91d` (verified at `63c86e4`). |
| Baseline for this draft | `63c86e413de0e2dcb7b179b4274d841947345f3a` |
| Governing decisions | **D4-31**, Architecture C, Rules C-01…C-12. **D4-32**, OD-7 global-first historical admission refusal. Both are in [`PHASE-4-DECISIONS.md`](PHASE-4-DECISIONS.md). |
| Review addressed | [`PHASE-4-WP1-ARCHITECTURE-C-INDEPENDENT-REVIEW.md`](PHASE-4-WP1-ARCHITECTURE-C-INDEPENDENT-REVIEW.md) (Gate 2, ACCEPT WITH SPECIFIED CORRECTIONS), checklist C1–C8, response in [`PHASE-4-WP1-ARCHITECTURE-C-GATE2-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-GATE2-CORRECTIONS.md). Gate 2 verification [`PHASE-4-WP1-ARCHITECTURE-C-GATE2-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-GATE2-VERIFICATION.md) (V1–V4), response in [`PHASE-4-WP1-ARCHITECTURE-C-V1-V4-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-V1-V4-CORRECTIONS.md). Draft 3 verification [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT3-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT3-VERIFICATION.md), response in [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-CORRECTIONS.md). |
| Technical basis | [`PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md`](PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md) |
| Historical evidence | [`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md) revision 3.9. It is superseded as direction and cited only for the retained ordinary corrections. |
| Authorization | Design documentation only. Nothing here is implemented, executed or approved, beyond D4-31 and D4-32. |
| Status | WP1 unaccepted. WP2 HOLD. WP1-R1, F5-4 and GAS-P4-002…GAS-P4-005 are OPEN. F5-1…F5-3 are architecturally resolved and preserved. |

Changes from Draft 1 are tagged **[G2-C1]**…**[G2-C8]**, after the review's checklist item. Changes from Draft 2
are tagged **[G3-V1]**…**[G3-V4]**, after the verification finding. Changes from Draft 3 are tagged **[G4-V1]** and
**[G4-V4]**, after the Draft 3 verification.

---

## 1. Purpose and how to read this contract

This contract states the smallest architecture the builder believes satisfies D4-31 for WP1. It is a set of testable
safety requirements, plus the few mechanisms proposed to meet them. It is not a revision 3.10, a schema, or an
implementation plan.

**Normative words.** MUST and MUST NOT are contract requirements. "Proposed" marks a mechanism that the builder
recommends and that still needs review, proof or an owner decision.

**Labels.**

| Label | Meaning |
|---|---|
| **[SRC]** | Read from the baseline source, migrations or deployment files |
| **[R]** | An approved requirement: accepted specification, accepted Phase 0–3 decision, D4-31 rule, or D4-32 |
| **[RET]** | A WP1 correction retained from earlier review work. It is designed, not implemented. |
| **[NEW]** | A mechanism proposed here |
| **[UNPROVED]** | A safety claim that needs a feasibility proof or an implementation test before it may be relied on |
| **[OD-n]** | An open owner decision (§13) |

**Design rule** (ENGINEERING-DESIGN-INSTRUCTIONS §2):
- Every new durable field, table or coordination mechanism names the safety property it serves.
- It also says why an existing record cannot serve that property, and which simpler alternative was rejected.

---

## 2. Scope and operating assumptions (D4-31 C-04, C-05, C-06, C-09, C-12)

### 2.1 Assumptions

| # | Assumption | Precise boundary |
|---|---|---|
| A-1 | **One designated producer host per installation** (C-05) | A **producer** is an ordinary runtime process (web `serve`, `worker`, or an ordinary CLI command) that can perform archival database writes or the filesystem effects of §5.1. Every producer runs on the designated host. A process on any other host is unsupported and is excluded operationally (§6, §7.6), never by a local lock. **[G2-C6]** The maintenance tool (§6) is **not** a producer. It is the administrative authority, and its own known administrative process and database connections are expected during maintenance. |
| A-2 | **Many processes on that host** | Any number of supported web, worker and CLI processes may run concurrently. Nothing assumes one JVM, one worker or one thread. |
| A-3 | **Remote PostgreSQL and NAS storage are allowed** | PostgreSQL may run on another machine, and Storage Volumes may be on a NAS. The local fence does **not** extend to other hosts. Network storage stalls are expected and must fail visibly (TX-004/TX-008). NAS support is not withdrawn, but completion guarantees on a given NAS topology are a feasibility question (§5.5, P3). |
| A-4 | **Planned, exclusive, offline maintenance** (C-04) | Incompatible migrations, the one-time transition into Architecture C, exceptional restoration, installation rebinding, and administrative bootstrap (§7.5) run only while no producer runs. Downtime is accepted. |
| A-5 | **Separate runtime and administrative authority** (C-06) | The runtime database role cannot change schema, operational state, receipts or role settings. |
| A-6 | **Deliberate manual intervention** (C-04, C-09) | Ambiguous failures stop in a visible, fail-closed state, and an administrator resolves them. Ownership is never taken over automatically on a timeout. |
| A-7 | **No cross-host takeover and no availability during maintenance** | — |
| A-8 | **Practical threat model** (accepted P2-E limits) | Covers supported processes, operator mistakes, crashes, network faults and stalled storage. Does not cover a privileged operator who deliberately bypasses roles, the freeze and constraints. |

### 2.2 Out of scope (C-12)

- The Phase 8 backup and restore product, and general disaster recovery.
- Automated restore orchestration and reverse migrations. BUILD-SPEC §56 allows restoring a pre-upgrade checkpoint
  instead.
- Multi-host producers.
- D-LP-9 (a) administrator coordination: separately eligible, not authorized [OD-1].
- D-LP-3 [OD-2].

---

## 3. Safety invariants

| ID | Invariant |
|---|---|
| **INV-1 Identity** | Archive ID, File, content, generation, operation, quarantine and unresolved identities, and every other permanent identifier, are never changed, reused or reassigned. This holds through migration, recovery, restore and rebinding (C-11; BUILD §7–8). |
| **INV-2 Immutable originals** | A verified preservation original is never overwritten, moved or deleted, except by an accepted, authorized, evidence-bound operation (TX, IV). |
| **INV-3 Accountable bytes** **[G2-C6]** | Every byte object that an accepted writer creates or relies on is accounted for by an accepted owner, quarantine or unresolved record. An **unexplained** object found on a managed volume is reported through the accepted integrity and unexpected-object behaviour (TX-006, IV). It is never adopted, deleted or given an invented owner. Its presence alone is not a failure of this invariant. |
| **INV-4 No overlapping effects** | A conflicting database or filesystem effect is never performed while a former writer of the same subject could still perform its own (C-07; §5). |
| **INV-5 Ordered locking** | Supported writers acquire locks in the approved global order. No supported pair or cycle deadlocks through an approved path (PR4-R3, LO-1…LO-7). |
| **INV-6 Durable evidence** **[G2-C6]** | Immutable identity and evidence fields of operation, quarantine, unresolved, generation and finding records are never rewritten. Append-only event and history records are never updated or deleted. Accepted **authorized lifecycle transitions** (state, evidence decisions, settlement, claim release, and the token columns of §5.4) remain permitted through their accepted writers. Recovery reuses these records and never infers success from names or from a COMMIT the process did not observe (TX-002). |
| **INV-7 Exclusive maintenance** **[G2-C6]** | While schema, operational state or binding changes, no effect-capable **admitted producer** runs, whether compatible or legacy, and no database authority of the installation other than the maintenance tool's own known administrative connections survives. A compatible process blocked **before admission** (waiting on the fence, or refused by `NOLOGIN` or by `installation_state`) is safe and does not violate this invariant. |
| **INV-8 Fail closed** | When ownership, exclusion, identity or recovery evidence cannot be established, the conflicting operation is refused visibly. Unrelated safe work continues where the accepted rules allow (C-09). |
| **INV-9 Bounded history** | Inherited records are admitted only under R39-T, with independent conflict checks. Missing provenance is never manufactured (C-10; §8). |
| **INV-10 Restriction and authorization** | Accepted restriction, non-disclosure and authorization behaviour is unchanged (SEC-004, D4-16, D4-27). |

---

## 4. Ordinary concurrency (C-01, C-02)

Ordinary operation is every supported producer running concurrently on the designated host while the installation is
READY. There is **no** maintenance state machine, no epochs, no per-connection declarations, no controller and no
generic action journal.

### 4.1 Lock ordering and deadlock avoidance [RET]

**Retained global order:** L0 → S → F → Ct → (P under its Ct) → K → R → Source → Repository → L2…L5, with rules 1–11
(proposal §3.1–§3.2).

**Retained rules:**
- File before Record;
- final strength first;
- parents first;
- worksheet locking through the session predicate;
- private new rows;
- terminal references (TR);
- the G10 admission classes. **[G3-V1]** The G10 **owned O** premise (an operation row locked by the live holder of
  its advisory lock) is **replaced** by the token-owned O premise and the O-row lock classes of §5.4.1.
  **[G4-V1]** Token-owned O is the first lock of every EDT, followed by π-checked ordered locks (retained G1).
  Terminal O-only writers are outermost and hold no earlier ordered lock.

**Retained writer sequences:**
- G `applyChunk`;
- A `CaptureSessionService.add` (D-LP-2);
- L `linkFile`;
- FC `FileService.correct`;
- the Phase 2 File-before-content writers;
- the catalog writers.

**Requirements:**
- **B-1:** every supported multi-row writer follows this order, including implicit foreign-key, unique-key and trigger
  waits, using the proposal's §2.2 trigger ledger and §2.5 conflict-key ledger.
- **B-2:** `LockSequence` records attempted and acquired locks correctly, without claiming to observe implicit SQL
  locks.
- **B-3:** a deadlock (40P01) on a supported path is a test failure.
- GAS-P4-003 (the administrator-check cycle) needs D-LP-9 (a) or an equivalent [OD-1]. D-LP-3 [OD-2] does not
  address it.

### 4.2 Physical content, generations and claims [RET]

**Retained:**
- CR-1 to CR-4;
- the window inventory **P2-8a…P2-8g**: 19 P2-8 windows and 22 Phase 2 windows (proposal §4.9);
- PO-B to PO-D (proposal §5.1–§5.3);
- F5-1: monotonic generation reservation, with the high water mark taken over all states and a checked overflow;
- F5-2: the G10 classes;
- F5-3: observation identity compared under Ct(X).

**Requirements:**
- **B-4:** a generation number is never reused.
- **B-5:** a location reoccupied by a newer operation is never released or moved on the authority of older evidence
  (GAS-P4-004, F4-1/Wh2).
- **B-6:** release only by a current claimant, revalidated in the releasing transaction.

No window is claimed removable.

### 4.3 Competing archival operations

- **B-7:** two processes never act on the same file operation at once. The accepted session advisory lock
  `file-operation:<id>` (`OperationLocks`) is kept [SRC]. Note that different REPAIR operations of one content X can
  legitimately produce private copies at distinct targets. Content coordination serialises their conflicting
  publications and claims; it does not promise that only one operation exists for X.
- **B-8:** the advisory lock gives only mutual exclusion among **live database sessions**. It is not evidence that a
  former holder stopped, and it is no longer the premise for locking an operation row (§5.4.1). Every takeover also
  requires §5.
- **B-9 (grouping)** **[G2-C8]:** grouping (`capture-session-grouping:<session>`) is a multi-transaction,
  database-only writer.
  - **Source finding (verified at `bac22ae`, review S9):**
    - `applyChunk` locks the session, rechecks each row (unapplied, version-matched) and revalidates Files, Record
      and classification;
    - it does **not** check that its run is still the current RUNNING run;
    - `recordChunk` inserts the chunk row and then updates counters `WHERE state = 'RUNNING'`, ignoring a zero-row
      result.
  - **Schedule left unprevented today:** runner A loses its advisory session; B closes A's run (`closeStale`) and
    starts a new run; A's late chunk still applies, because its rows are unapplied.
  - **Requirement:** each chunk transaction MUST, under the capture-session lock it already takes, verify that its
    run is RUNNING and is the session's current run. If not, it rolls back the whole chunk.
  - This is an implementation obligation [UNPROVED]. It needs a test of the late-runner schedule. No global registry
    is introduced.

### 4.4 Claims and publication consistency [RET]

Retained:
- private new-content publication (`commitNewContent`, E4);
- the `physical_location_owner` claim rules;
- `target_created` accounting;
- the P2-8g current-claim revalidation.

Revision 3.9's activation publications, epochs and publication sequences are removed (§15).

### 4.5 Durable operation evidence and interrupted work

- **B-10:** the accepted operation-specific records remain the only recovery truth for ordinary effects:
  `file_operation` and its events, the Q and U ledgers, owner rows, background jobs with lease tokens (V0008), and
  grouping runs.
- **B-11:** no generic action journal is added.
- **B-12:** recovery of an interrupted operation begins only after §5's ownership rule holds for that operation. A job
  lease expiry makes a job eligible to be scheduled. It never makes an outstanding effect safe to resume.

### 4.6 Separation from maintenance

Ordinary paths do not read, wait for or write maintenance state, with two exceptions:
- token acquisition checks READY and the binding in the same statement (§5.4);
- startup checks of §10.3.

---

## 5. Former-writer exclusion (C-07, C-09)

### 5.1 Definitions

- **Writer:** a process instance on the designated host, identified by a random `process_instance_id`, the host ID,
  the OS PID and the OS process start time. A thread is not a writer; its process is.
- **Subject:** a `file_operation` row, together with every path it names: staging, target, quarantine paths, and the
  source of a move it plans.
- **Conflicting filesystem effect:** creating, writing, renaming, moving, deleting or changing permissions of a path
  of the subject.
- **Effect-dependent mutation transaction (EDT)** **[G2-C1]:** any database transaction whose writes are correct only
  because of the interval's filesystem results or decisions. That includes:
  - operation state transitions (PROMOTING → PROMOTED and the like);
  - `target_created` and `staging_cleaned`;
  - quarantine intent and settlement;
  - U intent, attest and release;
  - owner claim and release;
  - generation insertion and retirement;
  - findings and observations written inside a state-changing window;
  - destruction marks and completion;
  - **[G3-V3]** cancellation finalization (CANCELLED with its U or claim change, and `staging_cleaned`), and
    administrator retry, attest and release.
- **Effect interval** **[G2-C1]:** the span during which a writer may issue conflicting effects or EDTs for the
  subject. It starts when the writer's token acquisition **commits** (§5.4). It ends only when **all** of the
  following hold:
  - **(a)** every thread working for the interval has returned from its last filesystem call, and no asynchronous
    work for it remains pending;
  - **(b)** the per-effect storage outcome of §5.5 is reached, or the interval is deliberately left open for
    recovery;
  - **(c)** every EDT the interval issued has a **known** outcome (committed or rolled back);
  - **(d)** the token release commits.

  Process termination also ends the process's filesystem activity. It does **not** prove that the process's database
  backends have ended. §5.4 handles those.
- **Token-free transitions** **[G3-V3]**: the **only** transitions that may run without holding the subject's
  interval. Each is owner-independent and request-only: it observes no filesystem object, and changes no Q, U, claim,
  generation, staging or accountability record.

  | Transition [SRC] | Why it is token-free |
  |---|---|
  | Stale RECEIVING → ABANDONED (`abandonStaleReceiving`): a CAS rechecking state **and** the stale predicate, with its ABANDONED event | Accepted logical abandonment. It only ends the upload's eligibility. The late receiver's renewal and its RECEIVING → STAGED CAS then fail, as accepted. |
  | A cancellation **request** (`OperationAdminService.cancel`): the "cancellation requested" note and its queued worker job | It records a request. Finalization happens later in `performCancel`, under the interval. |
  | Notes that change no state (`FileOperationRepository.note`) | Message text only |
  | Receiver lease renewal (`renewReceivers`), run by the receiving process for its own uploads | Liveness evidence for the accepted timeout. It changes no state. |
  | The token's own acquire, release and reclaim transactions (T-1, T-4, T-6) | They are the ownership protocol itself |

  **Every other transition requires the interval (T-1 or T-6) and is an EDT under T-3, even when no archival bytes
  are moved.** That includes any transition that:
  - relies on a filesystem observation;
  - changes Q or U records, or a physical ownership claim;
  - releases or changes archival accountability;
  - performs staging cleanup or any other physical effect.

  **Administrator cancellation finalization** (`performCancel`) is one such transition [SRC
  `OperationAdminService` 161–290]. It:
  - observes the target (`OperationCopies.check`, `Presence.probe`);
  - may move the copy to a CANCELLED_COPY quarantine;
  - may record the claim unresolved (`claimBecomesUnresolved`) or release it (`releaseTarget`), in the CANCELLED
    transaction;
  - then deletes staging and marks it cleaned.

  The whole of `performCancel` therefore runs inside an interval of the operation. Each of its database steps is an
  EDT. Administrator retry, attest and release are classified the same way (§5.8).

### 5.2 The invariant

**A writer W2 MUST NOT issue a conflicting filesystem effect or commit an EDT on subject O while a former writer W1's
interval on O may still be open.**

### 5.3 Evidence of exclusion

| Evidence | Sufficient? | Why |
|---|---|---|
| W1's own token release committed and observed | **Yes** | The release is the interval's last step (§5.1 (d)) |
| W1's process is gone on the designated host: the recorded PID is absent at a reliable observation, or the PID is present with a reliably read **different** start time. The check is made by a reclaimer on that host. | **Yes, hypothesis P1** [UNPROVED: Proof 2] | A terminated process issues no further calls. A process blocked in kernel I/O remains present. An absent PID observed reliably proves the recorded process ended, and a later reuse does not resurrect it. |
| An administrator's recorded verification that W1's host is powered off or isolated from storage and database | **Yes, manual only** | C-07 host or access exclusion. Used for restore, rebind and tokens naming another host. |
| Lost PostgreSQL connection; a free advisory lock; `pg_terminate_backend` returned true | **No** | The JVM and its I/O can outlive the session. A signal is not confirmed exit. |
| Job lease expired, stale presence, missing heartbeat | **No** | These are time-based (C-09) |
| Service manager reports "stopped"; `WorkerProcess.stop` returned after its 10 s join [SRC] | **No** | Neither proves exit |
| The process start time cannot be read; an access or query error; a permissions error (distinct from "no such process") | **No** | The identity is not established. This means refuse. |
| The token names another host | **No** (automatic) | A local check cannot observe another host |
| The random instance UUID | **No** (attribution only) | An unrelated OS process cannot be asked for it |
| Contention on the O row (a lock wait, or 55P03) | **No** (never attribution) | Another legitimate same-role writer can hold O (§5.4.1). 55P03 also arises from `lock_timeout`. |

**P1 hypothesis** **[G2-C7]:** under the actual service accounts on both platforms, Java 21 `ProcessHandle` can
distinguish these cases, or fail closed when it cannot:
- an absent recorded process;
- the same process;
- a different process reusing the PID.

The proof must establish:
- `ProcessHandle.Info.startInstant` availability and permissions;
- the difference between an empty result and a query error;
- start-time precision after a `timestamptz` round trip. A canonicalisation that could make a live writer look like a
  different process is a failure.

If the hypothesis fails, the reclaim rule refuses, or a separately reviewed equivalent is used. Takeover is never
automatic.

### 5.4 Mechanism 1: durable effect-owner token on `file_operation` [NEW] [UNPROVED] **[G2-C1]**

**Fields** (on the existing row, set together or null together):
- `effect_owner_instance` (uuid);
- `effect_owner_host`;
- `effect_owner_pid` (bigint);
- `effect_owner_started_at` (OS start, in a representation that round-trips exactly per P1);
- `effect_interval_id` (uuid, unique per interval).

The runtime role may change only these columns, and the accepted lifecycle columns, through accepted writers.
Granting the token CAS does not permit changing immutable archival fields (INV-6).

**Connection labels are diagnostic only** **[G4-V1]:**
- PostgreSQL `application_name` is a client-supplied, session-changeable setting (`PGC_USERSET`), intended for
  statistics and logging. The accepted label is `genealogy-archive-<purpose>` [SRC `DataSources`].
- The implementation **may** append the `process_instance_id` to it for diagnosis.
- Under this contract, a label:
  - is **diagnostic, candidate and refusal information only**;
  - is **not** authenticated process identity;
  - is **not** proof that a census of backends is complete;
  - is **never** authority to reclaim or to terminate a backend.
- **A negative label census proves nothing and authorizes nothing.**
- **A positive label match** may make a reclaim refuse, conservatively. It never authorizes termination.
- `pg_stat_clear_snapshot()`, same-role visibility, label length and the UUID string are not guarantees of
  attribution or completeness.

**Rule T-1: acquisition commits before any effect.**
- Acquisition is its own short, **outermost** transaction: nothing else is open on that connection, and the thread
  holds no other transaction or lock. The writer holds the operation's advisory lock when it acquires (§5.4.1).
- It consists of one compare-and-set:

  ```sql
  UPDATE file_operation SET <five fields> = <mine>
   WHERE operation_id = :op AND effect_interval_id IS NULL
     AND EXISTS (SELECT 1 FROM installation_state s WHERE s.mode = 'READY'
                 AND s.installation_id = :installation AND s.host_id = :host)
  ```

- It COMMITs before the first conflicting effect or EDT. Zero rows updated means no effect: report "in progress
  elsewhere" or "installation not ready".
- When the subject's row is created by the writer that will act on it (a receiving upload), the token is set in the
  **inserting** transaction, which commits before the staging file is created.

**Rule T-2: unknown outcome of the writer's own token transaction** (acquire or release).
- No effect is issued.
- The writer knows its **own** attempted backend exactly: the `(pid, backend_start)` it read from the server when
  that connection opened, and recorded independently in its log. It closes that connection.
- From a fresh connection, it waits a bounded time for that exact `(pid, backend_start)` to be absent. Runtime code
  never signals any backend.
- If the backend is absent, it reads the row:
  - its own interval ⇒ acquired (or, for a release, not committed, so it retries the release);
  - anything else ⇒ not acquired (or released).
- If the backend is not absent within the bound, the writer decides nothing and reports "own token transaction
  outcome unknown; backend still present", with the recorded tuple. Administrative resolution follows §10.4 (a).
- A token left set by an unresolved acquisition is later reclaimed under T-6, or offline.

**Rule T-3: every EDT checks the interval, as its first lock** **[G4-V1].**
- **The first lock of every EDT** is the subject's row, taken under the interval predicate:
  `SELECT … FROM file_operation WHERE operation_id = :op AND effect_interval_id = :interval FOR NO KEY UPDATE`.
  A single-statement EDT on O itself may instead carry `AND effect_interval_id = :interval` in its UPDATE.
- A multi-operation EDT (destruction over the operations whose tokens it holds) first locks **all** its token-owned O
  rows, in operation-UUID order.
- **Retained ordering:** only then does the EDT take the window's ordered locks (S, F, Ct, K, R, …), under G1's π
  check. "Owned O first, then π-checked ordered acquisitions" is a placement the retained G1 and G10 already permit
  (proposal §6, G1: "After an owned O lock, ordered acquisitions proceed under the normal π check").
  - Draft 3's alternative placements (O after Ct; O "at its existing position") are **withdrawn**.
  - A retained window that today takes Ct or another ordered lock before O is **reordered** to this placement.
  - A converted effect-dependent finding becomes: O (token-owned), then its own Ct-level row.
  - The implementation inventory must confirm this for every window [UNPROVED].
- Zero rows, or a mismatch, means the whole transaction rolls back. The writer stops the interval, issues nothing
  more, and reports "ownership lost".
- All EDTs of one interval are issued **sequentially by the interval's owning thread**. Helper threads perform only
  filesystem I/O.

**Rule T-4: release.**
- When §5.1 (a)–(c) hold, the owner commits, as an outermost transaction,
  `UPDATE … SET <five fields> = NULL WHERE operation_id = :op AND effect_interval_id = :interval`.
- **Unknown release outcome:** the former owner issues no further effects (the interval has ended locally) and
  resolves the outcome by T-2.
- A clean release is the ordinary handoff. It needs no reclaim, no census and no maintenance.

**Rule T-5: stale-owner rollback.** If the owner ever finds a mismatch (T-3), it rolls back, issues nothing more,
and leaves recovery to the current owner.

**Rule T-6: automatic reclaim, under independently established exclusion** **[G4-V1].**

Automatic reclaim is permitted only when **both** independent conditions hold:
- **(E-1) Filesystem and process exclusion:** the recorded former process is gone (P1). The token names this host,
  and the interval is not active in the reclaimer's own JVM.
- **(E-2) Database fence over every surviving EDT authority:** established inside the reclaim transaction by the
  `NOWAIT` row lock on the exact old interval, combined with T-3's mandatory first-lock predicate (argument below).

Then **one short, outermost transaction**, under the operation's advisory lock:

```sql
BEGIN;
SET LOCAL lock_timeout = '<short bound>';            -- bounds the ROW SHARE table-lock wait,
                                                      -- which NOWAIT does not cover
SELECT 1 FROM file_operation
 WHERE operation_id = :op AND effect_interval_id = :old_interval
   FOR NO KEY UPDATE NOWAIT;                          -- 0 rows: token changed; roll back and re-read
UPDATE file_operation SET <five fields> = <mine>
 WHERE operation_id = :op AND effect_interval_id = :old_interval;   -- this row is already locked by us
INSERT INTO file_operation_event (… EFFECT_OWNER_RECLAIMED, old and new instance and interval …);
COMMIT;
```

There is no label census in the reclaim. If the implementation runs a diagnostic one, a positive match may only
cause a refusal, and a negative result is ignored.

**Outcomes** (never a termination by the runtime; nothing inferred):
- **E-1 not established** (process present, start time unreadable, another host, own JVM active) ⇒ refuse.
- **55P03**, from NOWAIT or `lock_timeout` ⇒ roll back and report "operation row busy". 55P03 identifies no
  backend: the holder may be a legitimate same-role writer (§5.4.1) or a surviving backend of the former process.
  Recovery retries later.
- **Busy persists**, or exclusion is otherwise ambiguous ⇒ the subject stays refused (§5.7). The administrator
  waits, or uses the offline procedure (§6.4 M-2 to M-5: producer exclusion, admission closure, drain of every
  backend), and then reclaims during that attempt.
- **Unknown reclaim COMMIT** ⇒ T-2.

**Why E-1 and E-2 exclude every surviving EDT authority** (no label, census or registry involved):
1. Every EDT commits only while holding the subject's row under its own interval predicate (T-3). That is the EDT's
   **commit authority**.
2. If any transaction holds that row, under any predicate, the reclaim's `NOWAIT` fails and nothing changes.
3. Otherwise the reclaim locks the row and changes `effect_interval_id`. From its COMMIT on, a statement carrying the
   old predicate matches zero rows; PostgreSQL rechecks the predicate after waiting for a concurrent update. So no
   transaction holding the old interval can ever acquire the row.
4. A surviving backend of the former process therefore either:
   - **(i)** committed before the reclaim, and its result is in the reclaimer's fresh read;
   - **(ii)** held the row, and the reclaim refused;
   - **(iii)** had not yet taken the row. Because the row is every EDT's **first** lock, such a backend holds no lock
     of that EDT window. When its in-flight statement runs, it matches zero rows, and the transaction rolls back
     (T-3, T-5).
5. With E-1, the former process issues no new statements or filesystem calls.

**[UNPROVED: Proof 2 negative variants (§12) and the EDT and O-writer inventory]**

**Residual (liveness only, not safety).** A surviving backend of the former process may still **hold** ordered locks
from its own **other** subjects' windows. This happens until PostgreSQL ends it, after the client socket closes or
TCP keepalive expires. It cannot commit an EDT of this subject, and by the retained order it waits only on resources
ordered after those it holds. A new owner that needs such a lock therefore waits, or hits `lock_timeout` and retries,
reporting "busy". The administrator may wait, or use the offline drain.

#### 5.4.1 Ownership premise and O-row lock classes (replaces the G10 "owned O" premise) **[G3-V1, G4-V1]**

The retained G10 premise ("owned O: `adv[O]` registered to this thread and not LOST") **no longer holds**, because
trace T-d lets W1 continue after losing its advisory session.

**Premise.**
- A **non-terminal** acquisition of an operation row O (holding O while acquiring further locks) is permitted only to
  the **current interval's owning thread**.
- That thread establishes the premise in the statement itself, through T-3's predicate, as the **first** lock of the
  EDT.
- The advisory lock still schedules work among live sessions (B-7). It is no longer a premise for locking O.
- **"Exactly one owning thread" is conditional on E-2.** A surviving backend of a former interval can neither hold the
  row at reclaim time nor acquire it afterwards (T-6). This is not an independent substitute for that exclusion.

**The terminal exception is narrow** **[G4-V1].**
- Every class marked "terminal" below **MUST run in an outermost transaction that holds no previously acquired
  ordered lock** (no S, F, Ct, K, R, catalog or other operation row) on that connection or in any enclosing
  transaction on that thread.
- A statement being the **last** SQL of a transaction is **not** sufficient if the caller already holds Ct, or another
  earlier ordered resource, while it waits for O.
- A call site that issues a note or token transaction while holding such locks is **not terminal**. It must be
  restructured, or classified as token-owned O.

| Class | Who | Locks held while waiting for O | Terminal? |
|---|---|---|---|
| **Token-owned O** | the current interval's owning thread (EDTs, T-3) | nothing: O is its first lock (several token-owned O rows in UUID order for a multi-operation EDT) | No; the only non-terminal class. It holds O while acquiring ordered locks afterwards under G1. |
| Token acquire, release, reclaim (T-1, T-4, T-6) | any writer, outermost | nothing | **Yes** |
| Stale RECEIVING abandonment CAS | recovery, outermost | nothing (its event insert follows the O update) | **Yes** |
| Request-only cancel note, other notes | web or worker, outermost, as a single statement | nothing | **Yes**, only under the outermost, no-prior-lock rule |
| Receiver renewal (`renewReceivers`) | the receiving process, outermost, as a single statement over its own uploads' RECEIVING rows | the rows of that statement already updated | Terminal with respect to every other resource. Its only competitors on those rows are single-row, terminal (abandonment CAS, the receiver's STAGED CAS, notes, token transactions) or token-owned O, which holds nothing before O. |

**No deadlock cycle passes through an O row:**
- Every terminal locker waits holding nothing, except renewal, which holds only RECEIVING O rows that no other
  multi-row locker takes.
- Token-owned O is the first lock of its window. A token-owned waiter for O therefore also holds nothing, unless it
  is a multi-operation EDT holding lower-UUID token-owned rows, which are ordered.
- So no waiter for O holds a resource that the holder of O needs. Cycles through other resources are excluded by the
  retained order (G1 π after owned O).

**Implementation inventory** (future; not executed) [UNPROVED]. The inventory MUST validate every operation-row
writer and lock acquisition, including:
- implicit trigger and foreign-key locks (for example the `file_operation_event` insert and claims-trigger effects);
- multi-row renewal;
- destruction's multi-operation path;
- every note call site, for the outermost, no-prior-lock rule;
- the reordering of retained windows to O first.

Any unclassified writer is a contract failure until classified. The inventory and the lock proofs have **not** been
executed.

**Other content windows** (another operation's EDTs on content X, integrity re-verification without an operation):
- They lock their **own** token-owned O rows first, if any, then Ct(X) and content-scoped rows in the retained order.
- They lock another operation's O only through that operation's token (as token-owned O), never while holding
  ordered locks as a "terminal" note.

**Rule T-7: token-free transitions never touch the token** **[G3-V3]:**
- The transitions listed in §5.1 never clear or transfer the token.
- They never authorise deletion, promotion, release, adoption, a Q or U change, a claim change, staging cleanup or
  any other conflicting physical work.
- Physical cleanup or finalization after them is a separate interval, under T-1 or T-6.

**Traces** (W = writer, O = operation):

| # | Trace | Outcome |
|---|---|---|
| T-a | W1: T-1 COMMIT ok → copy and force → EDT (Ct, Q, then token-owned O) COMMIT → T-4 release COMMIT | Normal |
| T-b | W1: T-1 statement executed, then the transaction rolled back (or the backend died before COMMIT) | No effect. If the outcome is unknown, T-2 finds the token null and W1 does nothing. |
| T-c | W1: T-1 COMMIT unknown; its own backend is still present | W1 issues nothing. It closes the connection and waits a bounded time for its exact `(pid, backend_start)` to disappear. Absent ⇒ read: its own interval ⇒ proceed; null ⇒ not acquired. Still present ⇒ report; the administrative path resolves it. |
| T-d | W1 is copying; its advisory session drops; W2 takes the advisory lock and sees the token. W1's PID is present with the same start time. | W2 refuses (W2 may only add a terminal note). W1 continues, and its EDTs commit through T-3 on new pool connections. |
| T-e **[G4-V1]** | W1's JVM dies while its backend B1 is still connected **and holds O** (an EDT in progress, or idle in transaction after its first lock): its socket close was not delivered, for example after a network partition | E-1 holds, but the reclaim's NOWAIT gets 55P03 ⇒ "busy"; refuse and retry later. Nothing is terminated by the runtime. When PostgreSQL ends B1 (socket close or TCP keepalive), B1 rolls back and a later reclaim commits. To resolve sooner, the administrator uses the offline drain (M-2 to M-5), not a label. |
| T-f **[G4-V1]** | W1's JVM dies; B1 holds no lock of this subject's window (it ended, or had not yet taken its first O lock) | E-1 and E-2 hold, so the reclaim commits. If B1 still runs an in-flight statement of that EDT, it matches zero rows and rolls back. Labels play no part: B1's label may be missing, changed or truncated without effect on the outcome. |
| T-k **[G4-V1]** | W1's JVM dies; B1 survives holding Ct(X) from W1's EDT on **another** subject O2 | O1's reclaim commits (E-2 holds for O1). O1's new owner waits for Ct(X), or hits `lock_timeout` and retries, reporting "busy". O2's reclaim refuses (55P03) until B1 ends. No deadlock (B1 waits only on resources ordered after Ct), and no termination by the runtime. |
| T-g | W1 finishes; T-4 release COMMIT unknown | W1 issues nothing more and resolves by T-2 |
| T-h | Receiver W1 is alive but cannot reach the database; the timeout fires | Recovery commits RECEIVING → ABANDONED (token-free, T-7). The token stays. Cleanup tries T-6: W1's PID is present ⇒ refuse ("renewal and progress absent; cleanup pending"). W1's later STAGED CAS fails, so W1 deletes its own temporary file and releases (T-4). Cleanup then finds the token null, or reclaims after W1 exits. |
| T-i **[G3-V3]** | An administrator requests cancellation of a BLOCKED operation whose worker W1 lost its advisory session but is alive with the token | `cancel` records only the request (token-free). `performCancel` on worker W2 must acquire the interval: T-1 matches zero rows, and T-6 refuses because W1's process is present. So nothing is observed, moved, released, recorded unresolved or cleaned. The request remains visible and is retried. When W1 releases or exits, W2 acquires or reclaims and finalizes entirely within its interval, with each database step an EDT. |
| T-j **[G3-V1]** | T-6 meets 55P03 because a legitimate same-role writer holds O (for example the receiving process's renewal) | Roll back and retry later. No backend is terminated, and nothing is inferred from the contention. |
| T-l **[G4-V1]** | A live, unrelated backend carries the dead former process's label string | It has no effect on authority. The reclaim does not consult labels (a diagnostic census, if run, may only cause a refusal). It is never terminated on the label. |

### 5.5 End of an interval: storage outcome, blocked I/O and NAS **[G2-C7]**

**C-1 Required per-effect storage outcome.** Before an EDT that relies on a filesystem result, the writer MUST have
observed that result. The interval may end (T-4) only after the following:

| Effect | Required outcome before relying on it or releasing |
|---|---|
| File content written | `FileChannel.force(true)` returned without error. `ContentIo` does this for copied content [SRC]. |
| Create, rename, move, delete (namespace) | The call returned without error. On Linux the parent directory has been flushed (`SafeFiles.flushDirectory`) [SRC]. **On Windows, `flushDirectory` is deliberately a no-op [SRC]**, so the contract does **not** claim namespace durability there. It relies only on the call having returned. |

**Limits stated, not claimed:**
- Java documents that the `force` guarantee may not hold for non-local storage (FileChannel.force).
- This contract does not require, and the accepted implementation does not provide, a native filesystem durability
  subsystem.
- If a flush or force fails, the interval does not end. The token stays, and the operation is left to recovery.

**C-2 Blocked I/O.** A process blocked in storage I/O keeps its token, its share of the fence and its PID until the
I/O returns and the process ends. Recovery and maintenance refuse for as long as that lasts. This is fail-closed, not
bounded availability.

**C-3 Reverify after reclaim.** The new owner treats every path the former owner may have written as unverified. It
checks size and hash before relying on any byte. On a mismatch it follows the accepted partial-copy route (quarantine,
then replan to a new generation). It never rewrites a former owner's target in place.

**P3 hypothesis.** After the former process has terminated, and the storage outcome above was reached or the interval
was left open, an old client cannot later change the bytes **or the namespace state** relied on by the new owner.
Examples of such late changes: delayed write-back, a delayed rename or delete after client/server reconnection.
- A local process being gone rules out future calls by that process. It does not prove that remote accepted requests
  are settled.
- Re-hashing now cannot prove a later delayed write will not happen.
- **If P3 fails for a topology**, conflicting takeover on that topology remains refused until the administrator
  verifies storage or access resolution (§5.7). NAS support is not globally withdrawn, but a test on one share
  certifies only that share's type and configuration.

[UNPROVED: Proof 2]

### 5.6 Lost PostgreSQL session while the original JVM lives

This is trace T-d, together with T-3 for W1's later EDTs.
- If W1 cannot reach the database, it stops at its next checkpoint and keeps its token.
- Recovery waits for W1 to exit or reconnect.
- If W1 dies, the reclaim follows T-e or T-f.

### 5.7 When exclusion cannot be established

- The subject stays as it is, with its token.
- A visible condition names the operation and the reason: another host, process still present, identity unreadable,
  former-owner transaction open, or a storage topology not proven.
- Nothing retries automatically into a takeover. Other subjects are unaffected.
- Administrator routes:
  - wait;
  - terminate the process and confirm its exit (P1); or, by the administrative path only, terminate a backend
    under **independently established authority** (§10.4: its own recorded tuple, or the offline drain), and
    confirm its absence;
  - for another-host tokens or an unproven storage topology, record a host or storage exclusion statement and reclaim
    through the offline procedure (§6, §7.6).

### 5.8 Effect coverage inventory **[G2-C3]**

| Effect / source [SRC, review §8] | Subject | Treatment |
|---|---|---|
| Upload receiving into staging; the receiver's own failure cleanup, including after a failed STAGED CAS (`IngestionService`) | the existing INGEST (or REPAIR) operation | Token set in the inserting transaction (T-1). The receiver cleans its own temporary file after its channel closes, then releases. The accepted receiver lease renewal and failed-STAGED-CAS behaviour are **preserved unchanged**: a late receiver cannot finalize an abandoned upload. |
| Receiving timeout (`abandonStaleReceiving`) | same operation | **Preserved unchanged** as a token-free transition (§5.1, T-7): the stale predicate is rechecked in the UPDATE with its ABANDONED event. A renewing slow upload is never selected. Its effect on the token: none. |
| Staged-file cleanup (`RecoveryService.cleanStaging`) | the **existing** operation | Today it deletes with no owner exclusion [SRC]. It MUST acquire (T-1) or reclaim (T-6) an interval before deleting. The `staging_cleaned` and `target_created` guards are kept. `markStagingCleaned` is an EDT (T-3). If ownership cannot be established, the visible state is "renewal/progress absent; cleanup pending". |
| Promotion, verification, commit, repair copies and partial replanning (`OperationProcessor`, `OperationCopies`) | INGEST or REPAIR | Interval; EDTs under T-3 |
| Quarantine moves, `reconcileAll` and settlement (`QuarantineMoves`) | the Q entry's operation | Interval per entry; settlement is an EDT. `reconcileAll` acquires or reclaims per entry, never by elapsed time. |
| Cancellation request (`OperationAdminService.cancel`) | the operation | Token-free: request note plus queued job only (§5.1) |
| **Cancellation finalization** (`performCancel`) **[G3-V3]** | the operation | **Whole method inside an interval** (T-1, or T-6), even when nothing is moved: the target observation, the CANCELLED_COPY move, `claimBecomesUnresolved` / `releaseTarget` with the CANCELLED transition (one EDT), staging delete and `markStagingCleaned` (EDT). The accepted outcomes and messages are unchanged. Without the interval it refuses and the request stays pending (trace T-i). |
| Administrator retry (`OperationAdminService.retry`); unresolved attest, release and quarantine (`UnresolvedObjects`) | the row's operation | Inside an interval. Each state, U, Q or claim change is an EDT. A step may be reclassified as token-free only if the inventory shows it is request-only by the §5.1 criteria [UNPROVED inventory]. |
| Destruction, with `tryLockAll` over the other writers (`DestructionProcessor`) | DESTROY, plus every other writer's token | Every other writer's token must be null or reclaimable under T-6. Otherwise refuse. |
| Integrity re-verification (`IntegrityService.reverify`): restoring read-only protection, re-hashing, updating content state and findings | content X (no operation row) | A permission change on an original is a filesystem effect. It runs under Ct(X) coordination and F5-3 observation revalidation. Whether it conflicts with an open REPAIR or DESTROY interval on X, and needs that interval's exclusion, is an inventory obligation [UNPROVED]. "Reads hashes" is not treated as effect-free. |
| `init-volume` marker and managed directories (`VolumePreservationService`, `VolumeMarker`, `ManagedVolume`) | the volume (no operation) | Kept as ordinary administrative provisioning: exclusive create plus force; the accepted refusal of an existing marker. A crash that leaves a marker without the database timestamp or the directories remains refused. Any reconciliation compares the full marker identity and the directory and database state under administrator authority. Nothing is overwritten or adopted. The command keeps its accepted ready-archive precondition, so it is not run during OFFLINE. |
| Staging directory creation (`StagingArea.verify`) | — | Idempotent protected create, or refuse. It never deletes or recreates staged files. |
| Write-denial probe (`VolumePreservationService` 146–181) | a fresh UUID probe object | Exclusive unique probe with cleanup. A leftover after a crash is reported, never adopted or deleted as an original. |
| Thumbnails and cache | derived, rebuildable output | Not archival. The accepted temporary, replace and cleanup behaviour stays. Maintenance still excludes the producer. |

No effect may run uncovered. The completeness of this inventory is implementation obligation [UNPROVED].

---

## 6. Exclusive offline maintenance boundary (C-04, C-06, C-08, C-11)

### 6.1 What maintenance is for

Maintenance is for:
- incompatible migration;
- the one-time transition into Architecture C;
- bootstrap of a fresh archive (§7.5);
- installation rebinding;
- validation after restore;
- administrative resolution the runtime cannot do safely (another-host tokens, historical findings).

Ordinary administration does not require maintenance.

### 6.2 Durable state: one singleton [NEW] (§9)

`installation_state` holds exactly one row once created:
- `mode` (READY or OFFLINE);
- `installation_id`;
- `host_id`;
- `maintenance_attempt_id`;
- `ready_report_digest`;
- `changed_at`.

The Archive ID stays only in `archive_instance`.

### 6.3 Mechanism 2: installation fence (local, process-lifetime) [NEW] [UNPROVED] **[G2-C7]**

**How it works.**
- A lock object exists per installation, in a protected directory on the designated host. Root or Administrators own
  the directory, and it is not writable by the service account.
- Every compatible producer holds a **shared** lock on it, from before its first database connection until process
  exit.
- The maintenance tool takes an **exclusive, non-blocking** lock. If that succeeds, no compatible producer of this
  installation is alive on this host.

**Why it is needed.** The token covers only subjects with an open interval. Maintenance must also exclude idle and
between-request processes. A stopped service is not proof (§5.3), and a process scan cannot recognise every
compatible process.

**Boundary.** The fence is local only (C-05). Legacy builds are excluded by §6.4 steps M-2 to M-5 and §7.7.

**P2 hypothesis** (FileChannel candidate, OD-3): a dependency-free `FileChannel` lock gives the same protected-object,
JVM-lifetime exclusion on the supported local Windows and Linux filesystems, provided that:
- the process keeps a strong reference to a single unique channel and lock for its whole life;
- no shutdown or cleanup code releases the lock before every effect-capable thread has ended;
- no other code in the JVM opens the lock path (a closed second channel can release POSIX locks);
- the lock uses a fixed range and a canonical local protected pathname;
- shared-lock behaviour is checked, and a platform that silently upgrades shared to exclusive refuses, rather than
  serialising every JVM;
- partial startup (lock taken, then failure) releases only on exit;
- a missing or replaced object makes startup and maintenance refuse.

Java documents release on `release`, on channel close and on JVM exit, and platform caveats for shared locks.
- **If P2 fails:** JNA with lock and identity on the same handle is the alternative, needing a dependency decision.
- **Supervisor or process-scan equivalents** need their own proof.

### 6.4 Entry sequence

The maintenance tool is the **target** build, run by an administrator with administrative OS rights and the
administrative database credential (§10). Its own administrative connections, including the physical connections
Flyway opens, belong to the tool and are expected. Any other authority is enumerated, drained or refused.

| Step | Action | Recorded in the attempt's receipts (§9.2) |
|---|---|---|
| **M-1 Bind** | Load the configuration and connect with the admin credential. Classify the archive state (§7.5): **pristine** (no `archive_instance` row and no expected Archive ID configured); **initialized** (exactly one row, equal to the configured expected Archive ID); **legacy-initialized** (one row; the configuration has no expected ID: the one-time transition, §7.5); or **mismatch** (refuse). For an existing `installation_state`, the installation and host IDs must equal this host's, unless the step is a rebind (§7.6). | Archive ID or "pristine"; installation and host IDs; database identity |
| **M-2 Freeze** | Disable and mask the services, their restart policies (systemd `Restart=on-failure` [SRC]; WinSW restart actions), the scheduled tasks and the supported ad-hoc launch paths. The freeze is persistent across reboots and tool crashes (C-08). | Unit and task states |
| **M-3 Close runtime admission** | Runtime role `NOLOGIN`; retire legacy credentials (§7.7). Existing sessions are not ended by this. | Role state |
| **M-4 Exclude producers** | Stop services. Take the exclusive fence (refuse on failure). For legacy builds without a fence, and for former hosts, record verified termination or isolation. Anything unverifiable means refuse. | Fence result; exclusion statements |
| **M-5 Drain** | List every backend of this database other than the tool's own known connections. Terminate each by PID and confirm absence by `(pid, backend_start)` on a fresh snapshot. A remaining backend, a prepared transaction (`pg_prepared_xacts`; never resolved automatically) or an unexpected privileged backend means refuse. | Backends before and after |
| **M-6 OFFLINE** | If `installation_state` exists: one transaction does a CAS to OFFLINE with a new attempt ID and inserts an ENTER_OFFLINE receipt (§9.2). If it does not yet exist: the first Architecture C migration creates it with its seed receipt (§7.5). | Attempt ID |

### 6.5 Exit sequence

| Step | Action |
|---|---|
| **X-1** | Validate (§7.2, §8). Read-only. The full report is kept as a file in the protected administrative location. Its digest is recorded. |
| **X-2** | One transaction: CAS OFFLINE → READY on the current attempt ID, set `ready_report_digest`, insert a READY receipt. |
| **X-3** | Reopen, as separate external actions, each idempotent: (a) install or pin the launch artifacts; (b) point the configuration at the runtime credential; (c) runtime `LOGIN`; (d) unmask and enable the services. |
| **X-4** | Release the fence by exiting the tool. Services start, and each process performs the §10.3 checks before its first effect. |

### 6.6 Actual-state recovery **[G2-C6]**

There are no new controller states. Recovery reads the actual state and never assumes a step completed.

| Last actual state found on re-entry | Meaning | Recovery |
|---|---|---|
| No `installation_state` row; schema before the Architecture C control migration | Pre-table entry, or legacy | M-1 to M-5 from the start (re-freeze, re-exclude), then the bootstrap or transition path (§7.5). The freeze may or may not have been established before the crash, so it is re-applied. |
| Freeze applied, `NOLOGIN` not yet (crash after M-2) | Partial entry | Repeat M-2 (idempotent), then M-3 onwards |
| `NOLOGIN` set, row READY (crash after M-3, before M-6) | Partial entry; nothing mutated | Repeat M-4 and M-5, then M-6 with a **new** attempt ID |
| Row OFFLINE, attempt A | Inside attempt A | Repeat M-2 to M-5 to re-prove exclusion; **reuse attempt A**; continue (migrate, validate) from the actual history |
| Row READY with attempt A's READY receipt; reopening not done or partial (crash after X-2, or during X-3 a–d) | Validated; reopening incomplete | **Do not replay** X-2. Complete the remaining X-3 actions after reading their actual state (artifact, configuration, role LOGIN, unit state). A crash during X-3 leaves either the freeze still in place or the services enabled. In both cases the database row is already READY by validated decision. |
| Row READY, services running | Completed reopen | Normal. A new maintenance operation starts a **new** attempt, with full re-freeze and re-exclusion before any mutation. |
| X-2 COMMIT unknown | — | §7.4: confirm the backend is gone, read the row and receipt (§9.2 lookup) |
| Bootstrap states | — | §7.5 table |

A READY receipt is never replayed, and a successful rebind is never reset, merely because the tool lost a response.

---

## 7. Migration and recovery (C-04, C-08, C-09, C-11)

### 7.1 Migration

Migration runs only in an open attempt (OFFLINE, or pre-table under M-2 to M-5), with the admin credential and the
accepted Flyway configuration [SRC].

| Rule | Requirement |
|---|---|
| E-1 | `validateWithResult` passes before any migration. `status()` BEHIND is reported before validation [SRC] and is not validation. |
| E-2 | The history is a prefix of the target chain, with matching checksums. A hotfix or foreign history refuses. |
| E-3 | Every migration is transactional. A non-transactional statement is refused at build time. |
| E-4 | A coherent pre-upgrade checkpoint is recorded in the receipt. For the first Architecture C migration, it is recorded in the seed receipt. |
| E-5 **[G3-V2]** | **Native Flyway evidence is authoritative for migration steps.** Each committed step is proved only by its Flyway history row, the validated checksums of the whole history, and the schema validation, all read under exclusion after the attempting backend is confirmed absent. No migration writes a maintenance receipt (except the control migration's SEED, §7.5), and no receipt proves a migration. A **MIGRATED** receipt is an aggregate checkpoint record written **after** a run of migrations, in its own admin transaction. It records the start and final history versions observed at that moment. It is never atomic with the migrations it summarises. |

### 7.2 Validation before reopening

- Schema CURRENT for the tool's build (`PostMigrationValidator` [SRC]).
- The role and privilege layout of §10.1.
- The binding, and the Archive ID equal to the configured expected value.
- Historical admission (§8).
- The accepted integrity checks.

### 7.3 Recovery behaviour

| Situation | Behaviour |
|---|---|
| Successful migration | Validation → READY → reopen |
| Failure before a committed checkpoint | The migration transaction rolls back and the history is unchanged. The attempt state is as found (§6.6). Fix and rerun, or restore the checkpoint. |
| Failure after a committed checkpoint | The history shows an intermediate prefix, and ordinary processes cannot run (OFFLINE, `NOLOGIN`, freeze, fence). Rerun the same target build, which validates the prefix and continues, or restore the checkpoint. Intermediate schemas are never compatible for ordinary admission. |
| **History committed, MIGRATED receipt missing** **[G3-V2]** (crash or connection loss after the last migration commit, before the aggregate receipt) | §7.4 Flyway branch. After confirming the backend is absent, validate the actual history (prefix of the target chain, checksums), and the schema if the top is the target. Then write a MIGRATED receipt with `reconciled = true`, recording the receipted or SEED start version and the actual final version. The history, not the receipt, is the evidence. Continue the remaining migrations, or validate. |
| Unknown COMMIT | §7.4 |
| Interrupted maintenance | §6.6 |
| Runtime identity mismatch | §10.3 [OD-4] |
| Rebinding | §7.6 |
| Recovery after verified former-writer termination | Ordinary: T-6 reclaim, then accepted recovery. Maintenance: M-4 and M-5, then continue. |
| Exclusion cannot be proved | Ordinary: §5.7. Maintenance: entry refuses at M-4 or M-5, and the state is as found. |

### 7.4 Unknown COMMIT (administrative transactions)

1. Before COMMIT, the tool records its own backend `(pid, backend_start)` and the exact transition: attempt ID, kind,
   and expected old and new values (§9.2).
2. After an error, from a fresh admin connection, it confirms that backend is gone, terminating it if needed. If
   absence cannot be confirmed, it decides nothing and the state stays as it is.
3. **[G3-V2]** It decides by kind, using the evidence that is atomic with that kind (§9.2 table):
   - **Flyway migration step:** read the history. The attempted version present with a valid checksum ⇒ committed;
     absent ⇒ not committed. Then validate the prefix (E-1, E-2). No receipt is consulted or required. A missing
     MIGRATED receipt is reconciled afterwards (§7.3).
   - **SEED** (control migration): the history row, the `installation_state` row and the SEED receipt, all
     together, or none.
   - **ARCHIVE_INITIALIZED:** the `archive_instance` row, its accepted change-history entry and the receipt, all
     together, or none (§7.5).
   - **ENTER_OFFLINE, REBIND, READY:** look up the exact receipt:
     - **the receipt is present, with this attempt, kind and old/new values**, and the singleton shows the new value
       ⇒ committed: reuse it, with no second receipt;
     - **no such receipt, and the singleton shows the expected old value** ⇒ not committed: retry with freshly read
       preconditions;
     - **anything else** (another attempt's receipt, another binding, a mode not predicted) ⇒ refuse with
       diagnostics.
   - **MIGRATED** (aggregate): present ⇒ reuse; absent ⇒ write it again from the actual validated history. It is
     never evidence of a migration.

**Success is never identified from the new binding or mode alone.**

Ordinary transactions follow T-2 and T-4.

### 7.5 Fresh-archive bootstrap and the one-time transition **[G2-C2]**

The accepted clean-install workflow is: migrate, then `init-archive`. `init-archive` requires only a current schema,
not a ready archive [SRC]. `ArchiveService.initialize` takes the `archive_instance` initialization lock, refuses if a
row exists, and mints the Archive ID **inside its own transaction** with its change-history entry [SRC, V0002].

Under Architecture C it runs as a narrow administrative step:

| # | Step (fresh install, M-1 classified **pristine**) | Durable result |
|---|---|---|
| B-1 | Install; provision the fence object; create the runtime and admin roles (§10.1). The configuration has `installation.id`, a host-ID source and **no** `archive.expectedId`. | — |
| B-2 | Maintenance tool: M-1 (pristine) → M-2 → M-3 → M-4 → M-5 | External freeze; no other sessions |
| B-3 | Migrations V0001…V0025, then the first Architecture C control migration, one transaction per migration. The control migration creates `installation_state` (mode OFFLINE, installation and host IDs passed as placeholders, a new attempt ID A) and inserts a **SEED** receipt (attempt A, archive state "pristine", starting and final history versions) in the **same** transaction. | Schema CURRENT; row OFFLINE/A; SEED receipt |
| B-4 | **Bootstrap initialization**, in one admin transaction: the accepted `initialize` steps (table lock, refuse if any row exists, mint the UUID, insert, change history), plus, in the same transaction, an ARCHIVE_INITIALIZED receipt (attempt A, the new Archive ID, installation and host IDs). Allowed only when `installation_state` is OFFLINE/A **and** the SEED receipt for A records "pristine" **and** no `archive_instance` row exists. | One permanent Archive ID, with its receipt |
| B-5 | Write `archive.expectedId` into the configuration file, as a separate, non-atomic write | Configuration associated |
| B-6 | Validation (§7.2), X-2 READY, X-3 reopen, X-4 | READY |

**Initialization exception is narrow.** The only state in which an Archive ID may be created is: OFFLINE, a SEED
receipt of the current attempt recording "pristine", no existing row, and no configured expected ID.
- Every other state keeps strict equality between the configured expected ID and the stored Archive ID.
- An initialized archive is never re-initialized, rewritten or rebound to another Archive ID.
- Ordinary runtime has no initialization path, because its first effect requires READY and an equal expected ID
  (§10.3).

**Bootstrap crash and unknown outcomes:**

| Found on re-entry | Recovery |
|---|---|
| No row and no `installation_state`; some migrations committed | Classified pristine → M-1 to M-5 → continue the migrations (validated prefix) |
| B-3 control migration COMMIT unknown | §7.4: confirm the backend gone; history row, row and SEED present ⇒ continue; absent ⇒ rerun |
| OFFLINE/A, SEED "pristine", no archive row | Repeat B-4 |
| B-4 COMMIT unknown | Confirm the backend is gone, then read. One row plus an ARCHIVE_INITIALIZED receipt for A ⇒ committed: reuse that Archive ID. **Never mint another.** No row and no receipt ⇒ retry B-4. Anything else ⇒ refuse. |
| A row exists with A's ARCHIVE_INITIALIZED receipt, but the configuration lacks `archive.expectedId` (crash at B-5) | The tool, still OFFLINE, writes the expected ID from that receipt. This is allowed only when the receipt's installation ID equals the configuration's and its attempt is the current one. |
| A row exists with **no** matching ARCHIVE_INITIALIZED receipt, and the configuration lacks the expected ID | Not a bootstrap. Treat as **legacy-initialized** (below), or refuse. |
| A row exists and the configured expected ID differs | Mismatch: refuse |

**One-time transition of a legacy-initialized archive** (an existing Phase 0–3 or earlier Phase 4 database):
1. M-1 finds one row, with no configured expected ID.
2. The administrator supplies the expected Archive ID explicitly. The tool compares it with the database row **and**
   with the volume markers [SRC] (an equality check, never an assignment).
3. The tool writes it to the configuration only after both match.
4. Then M-2 to M-5, the migrations (including the control migration, with SEED recording "legacy-initialized" and
   the Archive ID), validation including §8, and READY.

No Archive ID is created, copied or changed.

### 7.6 Installation rebinding (new host, platform or installation ID)

**Preconditions, recorded in the REBIND receipt:**
- a coherent restore of database and storage;
- the former host verified powered off or isolated;
- the former host's credentials retired;
- the freeze on the new host.

**Transaction (one):**
- CAS of `installation_state` from (OFFLINE, attempt A, old installation ID, old host ID) to (OFFLINE, A, new
  installation ID, new host ID), using the caller's own new values;
- insert a REBIND receipt carrying both pairs and the Archive ID (compared, never changed).

**After a crash or unknown COMMIT:**
1. Re-prove exclusion (M-4, M-5).
2. Look up the REBIND receipt for A with the exact old and new pairs (§7.4):
   - present, and the singleton shows the new pair ⇒ done;
   - absent, and the singleton shows the old pair ⇒ retry;
   - else ⇒ refuse.

Then validation, READY and reopen. Tokens naming the former host are reclaimed by the administrator in this attempt,
citing the receipt's former-host exclusion statement.

### 7.7 Legacy executables (C-08)

Accepted Phase 0–3 builds and pre-Architecture C Phase 4 builds hold no fence, no token and no state check.

**During maintenance:** M-2, M-3, M-5 and M-4 (verified termination).

**After maintenance:**
- **(a)** The former schema-owner credential is retired: rotated, and removed from service-readable configuration.
- **(b)** The accepted AHEAD refusal.
- **(c)** Launch artifacts are replaced or pinned.
- **(d)** A defence, not a substitute: the accepted `InstallationConfigLoader` rejects unknown root keys [SRC], so the
  new `installation` and `archive` keys may make old binaries refuse the new configuration. Old configuration files
  and the old credential must still be retired.

GAS-P4-005 stays OPEN. An already-running legacy web process keeps writing until M-4 and M-5. Every legacy entry point
is tested in Proof 1 [UNPROVED].

---

## 8. Historical admission (C-10, R39-T, D4-32) **[G2-C4]**

### 8.1 When it runs, and the approved refusal policy

Historical admission runs read-only during the one-time transition, and after any restore or rebind.

**D4-32 (owner-approved): global-first refusal.**
- Initial historical admission **refuses READY** while any unresolved UNPROVED evidence, or any genuine ownership or
  alias conflict, prevents safe admission.
- Scoped or class-specific admission is **deferred**. It needs separate owner approval and a proof covering every
  writer and recovery path for the affected subjects. Setting one operation BLOCKED is not sufficient:
  `QuarantineMoves.reconcileAll` processes every INTENDED Q regardless of its operation's state [SRC], and
  content-scoped scanners and destruction have other entry paths.

### 8.2 Two different identities

| Identity | Meaning | Used for |
|---|---|---|
| **Content coordination domain** X | All rows that concern persisted content X | Serialisation (Ct(X)), and the scope in which relations are looked for |
| **Exact physical object** | A normalised key (volume, path) of a byte object, with alias equivalence (case, Unicode normalisation, links) per the AU-8 rules | Equivalence and conflicts |

- Two rows refer to the **same physical object** only if their exact keys are equivalent.
- A generation label or a content UUID **cannot** separate two real aliases of one physical path.
- Sharing X does **not** merge distinct paths or generations into one object.

### 8.3 Acceptable inherited evidence (R39-T): exhaustive witness classification **[G3-V4, G4-V4]**

**Normative source.** The witness for each inherited row class is the retained, accepted-writer-derived audit
classification of revision 3.9:
- **AU-1…AU-8** of proposal §5.6.5, as rebuilt on writer behaviour in **§5.7.9** (R7-A1…A4);
- extended by the recorded-absent intent rules of **§5.8.7**;
- refined by the positive-witness reading of **§5.10.6** (W_Q, W_U, PC, R39-T).

Those sections are evidence of the derivations only. Revision 3.9's admission machinery (activation, publication,
gates) is **not** retained.

**Precedence.** Where retained text conflicts, the rules of §8.2, §8.4 and §8.5 of this contract win:
- exact physical key versus content coordination domain;
- role-scoped conflict checks and known relations;
- movement authority from current authorization;
- witness, then conflicts, then disposition.

The retained A-6 waiver is inoperative, so no waiver exists.

**How rows are classified** **[G4-V4]:** two kinds of entry, applied together:
- **Producer classifications** (column "Kind" = **P**): mutually exclusive alternatives that say which accepted
  writer produced the row.
  - Generation creator: initial g₀ by INGEST, **or** later g by REPAIR.
  - Owner type: GENERATION **or** OPERATION.
  - The one applicable AU-2 operation-target row.
  - The U writer role (W1, W2 or W3 sub-role).
  - The Q role by reason.
- **Consistency checks** (Kind = **C**): applied **cumulatively**; every applicable check must pass. They are **not**
  competing classes and never count toward "exactly one". They cover:
  - superseded-state rules;
  - the generation set;
  - forward and reverse ledger directions, and owner-kind consistency;
  - settled-Q destinations;
  - the quarantine pointer;
  - aliases and claimant sets.
- **Rule:** each row must match **exactly one applicable producer classification** of its row type, **and** pass
  **all applicable consistency checks**.
  - A row matching **no** producer classification, or more than one where the alternatives should be exclusive, is
    **UNPROVED** (the retained UNSAFE-UNCLASSIFIED default).
  - A failed consistency check is UNPROVED or UNSAFE, per the outcome mapping below.

**Example:** a superseded INGEST-created g₀ matches the **initial g₀** producer classification once. It must also
satisfy the superseded-state rules and the generation-set check. Those are checks, not a second class.

| Row class | Kind | Witness (positive, structured, writer-derived) | Retained source |
|---|---|---|---|
| **Generation, initial g₀(X)** | P | `operation_id = X.created_by_operation`, which is an INGEST of X with outcome NEW_CONTENT, `target_generation = g₀`, in COMMITTED or COMPLETED. **g₀ may be any number ≥ 1**: partial replans raise an ingest's target before Content exists, and `commitNewContent` stores `op.targetGeneration` [SRC]. `storage_path = originalPath(X, g₀)`; `volume_id = X.volume_id`. | AU-1 (§5.7.9, R7-A1) |
| **Generation, later g ≠ g₀** | P | Created by a REPAIR R of X with `R.target_generation = g`, in COMMITTED or COMPLETED, g > g₀. Numbers may skip. Same path and volume rules. | AU-1 |
| **Generation, superseded** (any state other than CURRENT), whether created by INGEST (g₀) or by REPAIR | C | Its **creator stays as above**; supersession does not change the creator. The state follows the accepted constructors: `commitRepair` (old CURRENT → MISSING or UNRESOLVED); `quarantineSuperseded` (QUARANTINE_PENDING → UNRESOLVED, MISSING, or QUARANTINED via settlement); destruction completion (→ DESTROYED). `quarantine_path` is non-null only for QUARANTINED or DESTROYED (V0014). An UNRESOLVED generation has an OPEN U of that (content, generation) at its storage path. Historical `vacated` is evidence, not identity (R7-A2). | AU-1 |
| **Generation set of X** | C | Exactly one CURRENT while X is not DESTROYED; all DESTROYED when X is DESTROYED | AU-1 |
| **Operation target meaning** | P | The (kind, state, outcome, `target_created`) table: INGEST NEW_CONTENT ⇒ its target generation **is** g₀(X); SHARED_CONTENT checked relationally; REPAIR and DESTROY name a persisted X | AU-2 (§5.6.5 table, §5.7.9 corrections) |
| **Owner, GENERATION type** | P (type) + C (forward, reverse) | **Forward:** names an existing generation with the same volume and `storage_path = path`. **Reverse:** every generation, in **every** state, has its GENERATION owner row (rows are never deleted, F1). A ledger row never implies that bytes are present. | AU-3 (a), (b) |
| **Owner, OPERATION type** | P (type) + C (forward, reverse, kind consistency) | **Forward:** names an operation with `target_created`, the same volume and `target_path = path`. **Reverse:** every operation with `target_created` and a non-null target has exactly one OPERATION row at (volume, `target_path`); a legacy row lost to V0017's `ON CONFLICT DO NOTHING` backfill fails the reverse direction (UNPROVED). **Kind consistency:** no OPERATION row at a path any generation records. | AU-3 (a), (b), (c) |
| **U, by writer role** (exactly one role) | P | **W1** own copy (`claimBecomesUnresolved`: OPERATION_COPY, generation null, path = O's target, content = O's or null for a private ingest). **W2** supersession (`commitRepair`, `quarantineSuperseded`: DAMAGED_COPY or OBJECT_AT_VACATED_LOCATION at the storage path of generation g of X, generation = g) [SRC `OperationProcessor` 650–660]. **W3** destruction (`DestructionProcessor.newRecord`: OPERATION_COPY at a writer's target; CURRENT_COPY or OBJECT_AT_VACATED_LOCATION at the CURRENT storage path; DAMAGED_COPY at a QUARANTINE_PENDING or UNRESOLVED generation; QUARANTINED_COPY at a QUARANTINED generation's or Q's destination with generation null; OBJECT_AT_VACATED_LOCATION at a former location) [SRC `DestructionProcessor` 331–369]. Attestation fields consistent (V0019/V0020). | AU-4 (§5.7.9 writer-role table) |
| **Q, INTENDED, by reason** (exactly one role) | P | Own copy: PARTIAL_COPY (trusted partial prefix), UNVERIFIED_COPY, CANCELLED_COPY, with the source read per **W_Q** (`originalPath(content, g)`, g from the row; this supersedes AU-5's "source = O's target"), the allowed suffix and the evidence basis per reason. ATTESTED_OBJECT: bound to an OPEN, attested, confirmed U at the source. **DAMAGED_GENERATION (legacy):** written by earlier Phase 2 builds, not by the current baseline [SRC: enum and V0014/V0019 constraint only]; source = storage path of a QUARANTINE_PENDING generation of Q's content; UNTRUSTED allowed. Pre-V0019 UNTRUSTED rows are admitted when the role relation holds. | AU-5 (§5.7.9); §5.8.7 recorded-absent; §5.10.6 W_Q |
| **Q, settled** (QUARANTINED, SOURCE_ABSENT, REMOVED) and destinations | C | `quarantine_path = quarantinePath(Q.operation, suffix)`, with the suffix allowed for the reason; same-object references to a destination as listed | AU-5, AU-6 |
| **Operation quarantine pointer** | C | Reference consistency only (§5.10.6): null, or the destination of a witnessed own-copy Q of that operation. It authorises nothing. | §5.10.6 |
| **Aliases and claimant sets** | C | AU-7 and AU-8, applied with this contract's exact-key and known-relation rules (§8.2, §8.4) | AU-7, AU-8 |

**Outcome mapping** to §8.5:

| Retained classes | Outcome here |
|---|---|
| ADMITTED, ADMITTED-STALE, ADMITTED-CLEANUP | Admitted (inert where settled) |
| UNSAFE-LEGACY, UNSAFE-UNCLASSIFIED | UNPROVED |
| UNSAFE-ACTIVE, UNSAFE-AMBIGUOUS | UNSAFE |

Every refusal refuses READY (D4-32).

**Required passing fixtures** (healthy ordinary archives must pass):
- g₀ = 1 and g₀ = 3;
- skipped generation numbers;
- an INGEST-created g₀ superseded after a repair, in each superseded state;
- R1 and R2 repairs of X at different generations;
- GENERATION owners of every generation state;
- a final operation's stale OPERATION owner;
- each W1/W2/W3 U kind;
- each Q reason, including a legacy DAMAGED_GENERATION and a recorded-absent intent.

**Required failing fixtures:**
- a dangling owner in each direction;
- an OPERATION owner at a generation path;
- an unknown creator;
- a U matching no role;
- a foreign non-final claimant;
- an alias.

[UNPROVED: obligation 7]

**Not evidence:**
- absence of other references;
- a generation inequality;
- event detail text;
- current byte presence alone;
- the current target as proof of a past target.

### 8.4 Independent conflict checks (role-scoped)

1. **Active foreign owner** at the exact key: a non-final owner row of an operation with no known relation to the
   row's operation.
2. **Private ingest UUID**, scoped to the INGEST role: a private ingest UUID of one operation appearing as the content
   of **another** operation's row. This check does not apply to persisted content.
3. **Creator role**, scoped to generation ownership: generation g of X was created by an operation that is neither
   the row's operation nor connected to it by a known relation (repeated REPAIRs of X, destruction of X, attestation).
4. **Incompatible OPEN U** at the exact key, from a writer role that cannot coexist with it.
5. **Alias**: an equivalent exact key referenced outside the row's known-relation class.
6. **Movement authority** comes from the accepted **current authorization for the physical subject**: a durable
   INTENDED Q's recorded inputs; the current bound attestation, claim and evidence; or the operation's current target
   for its own target. `file_operation`'s current target is **not** the sole source. A historical association alone
   grants nothing.
7. **Release authority** comes only from the current claimant set, revalidated in the releasing transaction.

### 8.5 Order of classification and outcomes

Every inherited operation, generation, owner, Q and U row is classified **in this order**:
1. Witness (§8.3).
2. Conflict checks (§8.4).
3. Disposition (below).

| Class | Outcome |
|---|---|
| Witness present and no conflict, with live authority | **Admitted** |
| Witness present and no conflict, settled (no live claim or intent) | **Admitted, inert**: retained as history with no effect. "Inert" is decided only **after** steps 1 and 2. It never exempts an UNPROVED row or a real alias. |
| No witness | **UNPROVED** → refuses READY (D4-32) |
| Witness present but a conflict check fails | **UNSAFE** (ACTIVE or AMBIGUOUS) → refuses READY (D4-32) |

**Preserved:**
- legitimate repeated repairs of X at different generations, which pass without merging distinct objects;
- bound Q/U relationships;
- destruction-side evidence;
- attested objects;
- current durable intent authority.

**Never permitted:** rewriting rows, upgrading evidence, releasing foreign claims, deleting unknown bytes, waiving a
finding, or manufacturing a target assignment.

Findings are resolved only by accepted administrator operations, or by a separately reviewed offline plan. Admission
is then rerun.

### 8.6 What the records cannot answer

- Whether a writer-shaped row came from an accepted writer. This is R39-T itself, owner-approved.
- The chronology of past target assignments.
- A W1 U's old target snapshot.
- Whether bytes changed between observations.

Each gap resolves to refusal or to an administrator question.

---

## 9. Minimal durable state

### 9.1 Inventory

| Item | Safety property | Why existing records are not enough | Writer | Crash survival | Recovery validation |
|---|---|---|---|---|---|
| **`installation_state`** [NEW] | INV-7, INV-8: an archive-scoped "not validated" (OFFLINE). Binding (C-05, C-11). The READY check in T-1. | Flyway history does not say validation completed. `archive_instance` has no installation or host. `NOLOGIN` is cluster-global and can be re-enabled by hand. Configuration is host-local and copyable. | Admin only; runtime has SELECT | Transactional; travels with a restored database | M-1, X-2 CAS, §7.4 |
| **Token fields on `file_operation`** [NEW] | INV-4 | The advisory lock is session-scoped. Leases and presence records are time-based. No record of which process owns an effect exists. | Runtime, by CAS only (T-1, T-3, T-4, T-6) | Transactional; a crash leaves the token, so exit must be proved | T-6; §8 reports tokens naming another host |
| **Maintenance receipt** [NEW] **[G2-C5]** | INV-1, INV-6: exact, atomic transition evidence (§9.2) | `change_history` (V0004) **can** be inserted in the same transaction as another row, because `ChangeRecorder` writes within the caller's transaction [SRC]. Its default payload, visibility and writer policy, however, are researcher-facing (ADR-0008) and not admin-only. Reusing it would need narrowly validated admin-only operational entries and a visibility rule. A dedicated table may be simpler [OD-5]. | Admin only; append-only | Same transaction as the singleton change | §7.4 lookup |
| **Installation fence** (local file) [NEW] | INV-7 | No database object can prove that a process without a session is gone | Installer creates it; producers hold it shared; the tool holds it exclusive | The OS releases it at exit | M-4; missing or replaced ⇒ refuse, then reprovision offline |
| **Configuration keys** `installation.id`, host-ID source, `archive.expectedId`, admin secret source [NEW] | Binding and authority (§10) | The configuration has none today [SRC] | Installer or administrator (B-5 for `archive.expectedId`) | File | `check-config`, M-1, startup |

**New tables:** one (`installation_state`), or two with a dedicated receipt table (OD-5). **New columns:** five.
There are no other tables, gates, declarations or journals.

### 9.2 Receipt data contract **[G2-C5]**

The data contract is required now. The representation (a dedicated table versus admin-only typed history entries) is
OD-5.

| Field | Contract |
|---|---|
| `receipt_id` | A UUID generated by the tool **before** the transaction and stored with the attempt's local record. Immutable, unique. |
| `attempt_id` | The maintenance attempt. Several receipts per attempt are allowed. **No foreign key** to the singleton's current attempt, so historical receipts never block a later attempt. |
| `kind` | SEED, ENTER_OFFLINE, MIGRATED, ARCHIVE_INITIALIZED, REBIND, READY. A MIGRATED receipt also carries `reconciled` (boolean). |
| Old and new fields relevant to the kind | **ENTER_OFFLINE:** old mode READY with the previous attempt; new mode OFFLINE with this attempt. **REBIND:** old and new installation and host IDs. **READY:** old OFFLINE/attempt; new READY plus `ready_report_digest`. **MIGRATED:** starting and final history version. **SEED:** archive state (pristine or legacy-initialized), with the Archive ID when initialized. **ARCHIVE_INITIALIZED:** the new Archive ID. |
| Archive ID, database identity | Compared values (never changed) |
| Operator principal; recorded statements | Freeze, exclusion, former-host and coherent-restore statements, as text with no secrets. The full validation report is kept as a file; its digest is in the receipt. |
| `at` | Database clock |

**Uniqueness.**
- `receipt_id` is unique.
- (`attempt_id`, `kind`) is unique for SEED, ENTER_OFFLINE, ARCHIVE_INITIALIZED and READY.
- (`attempt_id`, `kind`, final version) is unique for MIGRATED checkpoints.
- (`attempt_id`, old pair, new pair) is unique for REBIND.

**Atomic target by kind** **[G3-V2]**. Draft 2's single universal "CAS + receipt + singleton change" applied
literally only to the state transitions.

| Kind | Atomic with | Authoritative evidence after an unknown COMMIT |
|---|---|---|
| Native Flyway migration step | its own schema changes and Flyway history row (accepted, one transaction per migration). **No receipt.** | History row, validated checksums, schema (§7.4) |
| **SEED** | the first Architecture C control migration: schema, history row, `installation_state` insert and SEED receipt, in one transaction | All present, or none |
| **ARCHIVE_INITIALIZED** | the accepted initialization transaction: table lock, `archive_instance` insert, change-history entry, plus the receipt | All present, or none |
| **ENTER_OFFLINE, REBIND, READY** | one admin transaction: compare the expected singleton state (CAS predicate), insert the exact receipt, change the singleton. A zero-row CAS or a failed insert rolls everything back. | The exact receipt, plus the singleton showing the new value |
| **MIGRATED** (aggregate checkpoint) | nothing else: its own admin transaction, written **after** the migrations it summarises; `reconciled = true` when written by recovery | Informational. The history decides. |

One permanent Archive ID is preserved, because only ARCHIVE_INITIALIZED can create it, and only in the pristine
state (§7.5).

**Append-only.** UPDATE, DELETE and TRUNCATE are refused, and the runtime role has no INSERT, so runtime cannot
fabricate a success receipt.

**Lookup after an unknown COMMIT** (§7.4), for ENTER_OFFLINE, REBIND and READY:
- by `receipt_id`, **and** its kind and old/new values matching the attempted transition;
- the singleton must show the new value;
- a receipt of another attempt, or a different new value, means refuse.

SEED and ARCHIVE_INITIALIZED are decided by the all-or-none presence of their atomic partners. Flyway steps are
decided by the history.

---

## 10. Authorization and identity (C-06, C-11)

### 10.1 Database authority [NEW] **[G2-C8]**

| Authority | Required capabilities | Never |
|---|---|---|
| **Runtime** (dedicated per installation; LOGIN only while READY) | DML on the application tables, as the accepted writers need; SELECT on `installation_state`; CAS on the token columns; read `pg_stat_activity` for its own attempted backend tuple (T-2) | DDL; writing `installation_state` or receipts; role administration; a `pg_signal_backend` grant; superuser. **Runtime code never signals a backend** **[G4-V1]**. PostgreSQL's default same-role signalling capability still exists; it is a supported-program rule, not an enforced function-permission boundary. The privilege inventory must record that distinction. |
| **Administrative** (maintenance only) | Schema owner for Flyway; write `installation_state` and receipts; change the runtime role's LOGIN or NOLOGIN; terminate this database's installation backends | Use by services; storage where the service account can read it |

**Terminology corrected.**
- PostgreSQL roles are not "owned". Changing another role's attributes requires `CREATEROLE` with ADMIN OPTION on
  that role (PostgreSQL 16 rules) or superuser.
- Terminating backends of other roles requires membership in those roles or `pg_signal_backend`. That right can reach
  wider than one installation.

**[G3-V1, G4-V1]** The runtime is granted no signalling right, and its code never signals. **OD-6 is not a contract blocker.** The required capabilities above are fixed now; C-06 already approves the
separation. The exact principal (a dedicated non-superuser role with bounded grants, or deliberately authorized DBA
action) and the exact grants are settled before the proofs and implementation. Today a single role owns the schema
and migrates "as the service account" [SRC]. That is a later deployment change.

### 10.2 Identities

| Identity | Source | Use |
|---|---|---|
| Archive ID | `archive_instance` (V0002); volume markers | Never changed. The configured expected value is compared at M-1 and at startup. Bootstrap and transition per §7.5. |
| Installation ID | configuration | Bound in `installation_state` |
| Host ID | OS machine identifier, or a provisioned protected file [OD-8] | Bound. A clone can match; that residual is covered operationally by C-05. |
| Process identity | instance UUID, PID, OS start time | Token (§5.4), under P1 |
| Executable and schema | the compiled Flyway chain and checksums | Startup CURRENT check; maintenance validation |

### 10.3 Runtime identity checks [OD-4]

Before its first effect, every producer checks:
- the fence is held;
- the schema is CURRENT;
- `installation_state` is READY;
- the binding equals its configuration;
- the Archive ID equals the configured expected value.

On failure it refuses effects. Web diagnostics stay up on ordinary database unavailability. T-1 rechecks READY and the
binding at every interval start.

**Open (OD-4): a mismatch detected after startup.**
- **Recommended:** latch, start no new intervals, let open intervals reach T-4 (or stop at a checkpoint keeping
  their tokens), then exit.
- **Alternative:** diagnostic-only continued running.
- **Either way:** no effects under the wrong identity, and no fence release while effects remain.

### 10.4 Recovery and rebinding authority

Only the administrative path, under §6, can:
- reopen;
- rebind;
- bootstrap an Archive ID (§7.5);
- reclaim tokens that name another host or an unproven storage topology;
- apply historical resolutions.

The runtime may reclaim only under T-6's independent conditions E-1 and E-2. No label is consulted as authority.

**Backend termination** **[G4-V1]** is administrative only. It requires **independently established authority** for
the specific backend:
- **(a) Targeted:** permitted only for an attempting backend whose `(pid, backend_start)` the attempting process
  itself recorded, independently of any label (T-2's report; the maintenance tool's own tuple, §7.4). The
  administrator revalidates the tuple immediately before signalling, then confirms absence on a fresh snapshot.
  Signal success alone proves nothing.
- **(b) Bulk:** the offline drain (§6.4 M-5). It runs after M-2 to M-4 have excluded every producer and closed
  admission. Every non-tool backend of the database is then terminated and confirmed absent.

**Never grounds for termination:**
- a positive label match;
- lock contention or a 55P03;
- same-role visibility;
- an unlabelled backend;
- a backend whose client process cannot be independently attributed.

Such a session is investigated, or the subject stays refused until the offline procedure (b) is used.

PostgreSQL's same-role signalling permission is a permission boundary, not an ownership boundary, so it is not used
as one. M-5's maintenance drain is the bulk case (b): there, every non-tool backend of the database is
terminated, and only after M-2 to M-4 have excluded all producers.

---

## 11. Failure matrix

"Covered" means a contract rule is present. It does **not** mean a test passed.

| # | Case | Required behaviour | Mechanism | Unproved? |
|---|---|---|---|---|
| 1 | Two workers compete for the same content | One acts per operation. Conflicting publications and claims are serialised. No deadlock. Generations are never reused. | Advisory lock; Ct and P2-8 windows; F5-1; T-1/T-3 | Ordinary corrections not implemented; LO/LP future tests |
| 2 | Session loss while the original worker lives | No takeover | T-1 to T-6; P1 | **Yes:** Proof 2 |
| 3 | Worker dies during I/O | One result; no adopted unknown bytes | T-6 under E-1 and E-2 (T-e, T-f, T-k); C-3 | **Yes:** Proof 2 (P1, P3) |
| 4 | Blocked NAS, then recovery | No takeover or maintenance during the stall | C-2; token and fence persist | **Yes:** P3 |
| 5 | Legacy restart during maintenance | No start; no write | M-2, M-3, M-5; §7.7 | **Yes:** Proof 1 |
| 6 | Automatic service restart during migration | No producer admitted | Mask and restart freeze; fence; `NOLOGIN` | **Yes:** Proof 1, P2 |
| 7 | Database connection survives shutdown | No late commit | M-5; prepared transactions refuse | Proof 1 |
| 8 | Migration crash before a checkpoint | No partial migration | Transactional migration; offline boundary; bootstrap per §7.5 | Partly |
| 9 | Migration crash after a checkpoint | No ordinary use of an intermediate schema | Prefix validation; continue or restore | Proof 1 |
| 10 | Unknown transaction outcome | Decide only after the backend is confirmed gone; reuse or refuse | §7.4 by kind (Flyway history; SEED and ARCHIVE_INITIALIZED all-or-none; CAS + receipt) **[G3-V2]**; T-2 and T-4 | **Yes:** Proof 1, Proof 2 |
| 11 | Runtime identity mismatch | No effects under the wrong identity | §10.3; T-1 | OD-4; unproved |
| 12 | Replacement host while the old host may write | No reopen until the old host is excluded | §7.6; REBIND receipt; credential retirement | Operator premise; P3 |
| 13 | Inherited ownership ambiguity | Refuse READY (D4-32) | §8 | **Yes:** obligation 7 fixtures |
| 14 | Repeated repair or cross-operation relation | Admit known relations; refuse real conflicts | §8.2 to §8.5 | **Yes:** obligation 7 |
| 15 **[G2-C2]** | Fresh install, including crashes at B-3, B-4 and B-5 | Exactly one Archive ID, or a safe OFFLINE refusal | §7.5 | **Yes:** Proof 1 |
| 16 **[G2-C3]** | Receiving timeout with a live, isolated receiver | Token-free ABANDONED; no cleanup while it may write; the late STAGED CAS fails; no File created | T-7; §5.8; trace T-h | **Yes:** Proof 2 |
| 17 **[G3-V3]** | Administrator cancellation while a lost-advisory owner lives | No observation, Q/U, claim or staging change until the interval is held; the request stays pending | §5.1; §5.8; trace T-i | **Yes:** Proof 2 |
| 18 **[G3-V2]** | Migration history committed, MIGRATED receipt missing | Continue from the validated history; reconcile the receipt; no refusal on the missing receipt alone | E-5; §7.3; §7.4 | **Yes:** Proof 1 |
| 19 **[G3-V1]** | Reclaim meets O-row contention from a legitimate same-role writer | Roll back and retry; no termination, no inference | T-6; trace T-j | **Yes:** Proof 2 |
| 20 **[G4-V1]** | A surviving former backend with an unusable label, a foreign backend with the former label, or no census available | No unjustified reclaim, no stale commit, no termination | T-6 E-1/E-2 (labels not used); §10.4; traces T-f, T-k, T-l | **Yes:** Proof 2 (n)–(q) |

---

## 12. Focused feasibility proofs (separately authorized; not executed)

**Common rules:**
- Non-production. Linux and Windows. PostgreSQL 16 and 18. Authentic accepted artifacts, and the installed service
  accounts.
- Deterministic seams for ordering. Bounded waits only to observe a named state.
- SQLSTATEs recorded. Retries must not hide 40P01 or unexpected lock timeouts.
- Exact assertions.
- Each proof covers only its stated claims, and passing it accepts nothing beyond them.

**[G2-C6] A failure criterion is an unsafe effect.** A compatible process that is safely blocked or refused before
admission is an expected observation, not a failure. Examples: waiting on the fence, refused by `NOLOGIN` or by
OFFLINE. The same applies to diagnostic status output.

### Proof 1: exclusive maintenance boundary

| | |
|---|---|
| **Prerequisites** | Prototype tool (M-1 to M-6, migration, validation, X-1 to X-4, bootstrap B-1 to B-6); prototype `installation_state` and receipt; runtime/admin role split on a scratch cluster; fence prototype (FileChannel candidate first); authentic Phase 3 and current Phase 4 builds as legacy artifacts; installed services (systemd; WinSW virtual accounts) |
| **Setup** | (a) Legacy web receiving an upload; legacy worker mid-job; legacy CLI effect commands. (b) A compatible worker with an open interval. (c) An orphan runtime-role backend in an open transaction. (d) A prepared transaction. (e) A 3-step migration chain: fault after step 2 commits; unknown COMMIT on step 3, with the **original backend held alive** while a fresh read sees the old state. (f) A rebind with a crash after its transaction and before READY; unknown REBIND COMMIT with the backend alive. (g) Restart policies left enabled (negative control). **[G2-C7 additions]** (h) **Bootstrap:** no Archive ID and no state table → a successful single-ID initialization; crashes and unknown COMMITs at B-3 and B-4; interrupted B-5 configuration write. (i) **Entry and exit suffixes:** crashes after M-2, M-3 and M-6, after X-2, and during each X-3 action. (j) **Unknown READY COMMIT** with the backend alive. (k) **Legacy access combinations:** old configuration with the retired credential, and the new supported configuration presented to old binaries. (l) **Role capability negatives:** runtime cannot run DDL, write state or receipts, administer roles or signal other roles; admin can complete the bounded procedure. (m) **Wrong archive, wrong host, wrong configuration; missing or replaced fence object.** **[G3-V2 additions]** (n) **History committed, MIGRATED receipt absent:** a crash after the last migration commit and before the aggregate receipt, and after an intermediate commit; recovery continues from the validated history and writes a `reconciled` receipt. Negative: a MIGRATED receipt whose history does not match is not accepted as evidence. (o) **Unknown COMMIT by kind:** the SEED control migration and ARCHIVE_INITIALIZED decided by all-or-none presence; ENTER_OFFLINE, REBIND and READY by the exact receipt. |
| **Observable results** | Entry refuses while (a), (b) or (g) is alive, unverified or able to restart. (c) is drained; (d) refuses and is never resolved automatically. Recovery always reads the actual state (§6.6). Every unknown COMMIT is classified only after the original backend is confirmed gone, using the exact receipt. Bootstrap ends with one Archive ID or OFFLINE refusal. Legacy actors cause zero database mutation and zero protected filesystem effect. (m) refuses before migration, backend termination or a binding change. |
| **Failure criteria** | Any producer effect, or any committed runtime-role transaction, during OFFLINE. Reopening without validation. A second Archive ID or a second rebind. A decision while the original backend lived. A legacy write. Fence acquisition while a compatible process lived. Automatic resolution of a prepared transaction. |
| **Coverage** | Linux and Windows. PostgreSQL 16 and 18. Local volumes; NAS where available, labelled with its type. |

### Proof 2: former-writer fencing

| | |
|---|---|
| **Prerequisites** | Prototype token (T-1 to T-7) wired into one INGEST promotion, one quarantine move with settlement (a Q EDT), one U release (an EDT), receiving and `cleanStaging`. A test seam that blocks inside a filesystem call. A stalled storage target (Linux FUSE or a paused NFS export; Windows SMB with the server paused). |
| **Setup** | (a) W1 acquires and starts promotion; its advisory backend is terminated while its JVM writes; W2 attempts the same operation. (b) W1 is blocked in a real kernel or NAS call. (c) W1 is killed (SIGKILL / TerminateProcess) mid-write, and W2 or recovery follows. **[G2-C7 additions]** (d) **B1 durability:** failure before the acquisition commits; unknown acquire and release COMMITs; W1's JVM killed while its backend holds the O row lock in an open EDT (T-e); and while its backend has not yet reached the check (T-f); Q and U EDT validation, not only the operation state transition. (e) **Receiving (M1):** renewal loss with a live receiver; timeout ABANDONED; attempted external cleanup; then the actual receiver closes or exits, followed by safe cleanup. (f) **Process identity across accounts:** the Windows web and worker virtual accounts; Linux service and admin visibility; same and different start; unavailable start; permission error; PID reuse; start time after a `timestamptz` round trip. (g) **Storage completion:** a stalled copy **and** a stalled rename; client/server reconnection after termination; observation of late write-back and namespace changes (a pre-exit size and hash sample is not enough). (h) **Same JVM and several JVMs:** the in-memory interval set never clears a still-running asynchronous effect. (i) Tokens naming another host; an unreadable identity. **[G3-V1 and G3-V3 additions]** (j) **Executable reclaim:** `FOR NO KEY UPDATE NOWAIT` against a row held by a legitimate same-role writer (renewal, a terminal note), giving 55P03, a rollback and no termination; a `lock_timeout` 55P03 on the table lock (a concurrent `LOCK TABLE`), giving the same outcome; **[G4-V1 replaces the former label-census variant]** a surviving former backend holding O, giving 55P03 and a refusal, then resolution only by PostgreSQL ending it or by the offline drain; no runtime termination and no label-based termination. (k) **Own-backend resolution (T-2):** an unknown acquire COMMIT with the own backend still present beyond the bound, giving a report and no signal by the runtime. (l) **O-row lock classes:** concurrent renewal, stale abandonment CAS, notes and token transactions against token-owned EDTs; zero 40P01. (m) **Cancellation finalization:** `performCancel` against a live lost-advisory owner, for each branch (UNVERIFIED with the claim recorded unresolved; ABSENT release-only; CANCELLED_COPY move; staging delete). Each is refused with no Q, U, claim or staging change until the interval is held; then finalized with every step an EDT. **[G4-V1 required negative cases; future, not executed]** (n) **A former EDT backend before its O check, whose label is missing, changed (`SET application_name`) or truncated:** expected, the decision is independent of the label. The reclaim commits only if E-1 holds and the row is free. The backend's in-flight statement matches zero rows and rolls back. No stale EDT commits, there is no 40P01 (the backend holds no lock of that window, because O is the first lock), and nothing is terminated. A variant where an implementation defect takes Ct before O must be **detected by the inventory and the lock tests**, not tolerated. (o) **A live, unrelated backend carrying the former process's label:** expected, no termination and no effect on reclaim authority (a diagnostic census may refuse, never authorize). (p) **Unavailable or incomplete backend census** (`pg_stat_activity` not readable, or rows hidden): expected, no effect on reclaim authority, because the census is not an input; nothing is inferred or terminated. (q) **Ambiguous former-writer exclusion** (P1 inconclusive or an access error; another host; a persistent 55P03; an unproven storage topology): expected, refusal, a visible "cleanup/recovery pending" state, no runtime termination; resolution only through §10.4 (a) or the offline procedure. |
| **Observable results** | No W2 effect or EDT while W1's process exists. W1's EDTs succeed through the predicate. After confirmed exit: a reclaim with an `EFFECT_OWNER_RECLAIMED` event, then byte verification and one accepted result. T-e refuses until the backend is terminated; T-f rolls back fully. (e) produces no STAGED and no File after cancellation, no cleanup while the receiver may write, and an unchanged result for an actively renewing slow upload. (i) refuses. |
| **Failure criteria** | Any W2 effect while W1 exists. A reclaim by elapsed time. A committed stale EDT. A reclaim of another host's token. A duplicate File or content. An adopted unverified byte. A late write or namespace change altering what W2 relied on. |
| **Coverage** | Linux and Windows; PostgreSQL 16 and 18; local and NAS volumes. Java-seam blocking is labelled separately from kernel or NAS stall characterisation. |

A failed hypothesis returns to the owner as evidence with a complexity comparison. It is not hidden by added
machinery.

**Left to implementation acceptance:**
- the privilege inventory (obligation 5);
- realistic inherited fixtures (obligation 7);
- the full Phase 0–3 and Phase 4 regression matrix (obligations 9 and 10);
- the B-9 late-runner test;
- the §5.8 inventory completeness;
- the complete EDT coverage.

---

## 13. Open owner decisions **[G2-C8]**

| ID | Decision | Status and recommendation | Gate |
|---|---|---|---|
| OD-1 | D-LP-9 (a), administrator coordination (GAS-P4-003) | Open. Authorize separately. | Before the applicable ordinary-order implementation and acceptance; not a boundary-contract blocker |
| OD-2 | D-LP-3, the Conflict after three abandoned attempts | Open, and **distinct from OD-1**: it does not fix the administrator-check cycle | Before implementing retry and closure behaviour |
| OD-3 | Fence library and identity | Open choice; the safety invariant (§6.3) is fixed now. Candidate: FileChannel under P2; JNA if P2 fails. A new dependency needs approval. | **Not a contract-acceptance blocker.** Choose before proof execution and implementation. |
| OD-4 | Runtime mismatch lifecycle | Open. Recommend latch and controlled exit (§10.3). | Before implementation |
| OD-5 | Receipt representation | Open. Recommend a dedicated append-only table. The data contract (§9.2) is fixed now. | Before DDL |
| OD-6 | Administrative principal and exact grants | Open choice. The capabilities (§10.1) are fixed now; separation is approved by C-06. **[G3-V1]** The runtime needs no signalling right; the administrative path needs targeted termination of independently recorded tuples, and the offline bulk drain (§10.4). | **Not a contract-acceptance blocker.** Settle and test before proofs and implementation. |
| OD-7 | Historical refusal scope | **APPROVED as D4-32**: global-first; scoped admission deferred | — |
| OD-8 | Host-ID source | Open. Recommend the OS machine identifier. | Before implementation |
| OD-9 | Receiving timeout behaviour | **No decision needed.** The contract adopts the narrower correction (§5.8, T-7): logical abandonment unchanged; physical cleanup gated. Draft 1's changed user-visible "receiving, stalled" behaviour is **withdrawn**. An owner decision would be needed only to choose that changed behaviour deliberately. | — |

The Architecture C operating model is not reopened.

---

## 14. Traceability

**Legend:** **P** preserved requirement; **R** replaced mechanism; **D** outstanding decision; **U** unproved claim.

### 14.1 D4-31 rules and D4-32

| Rule | Where | Class |
|---|---|---|
| C-01 | INV-1, INV-6, INV-10; §4.5; §5.8 (receiving preserved); §7.5 (initialization preserved) | P |
| C-02 | §4.1–§4.4 | P; U |
| C-03 | §15 | R |
| C-04 | §6, §6.6, §7 | P; U |
| C-05 | A-1, A-3; §6.3; §10.2 | P; D (OD-8) |
| C-06 | §10.1 | P; D (OD-6) |
| C-07 | §5 (T-1 to T-7) | U (P1, P3) |
| C-08 | M-2 to M-5; §7.7 | U |
| C-09 | §5.7, §7.3, §8.1, T-7 | P |
| C-10 | §8 | P; U |
| C-11 | §7.5, §7.6, §9.2, §10.2 | P; D (OD-5) |
| C-12 | §2.2 | P |
| D4-32 | §8.1, §8.5 | P |

### 14.2 Findings (none closed)

| Finding | Status | Coverage |
|---|---|---|
| WP1-R1 | OPEN | §4 (retained order), §5 to §7 |
| F5-4 | OPEN | §8 with D4-32 |
| GAS-P4-002 | OPEN | §4.1 B-1 |
| GAS-P4-003 | OPEN | OD-1 |
| GAS-P4-004 | OPEN | §4.2 B-5, B-6 |
| GAS-P4-005 | OPEN | §7.7; M-3 to M-5 |
| F5-1 to F5-3 | Resolved; preserved | §4.2 |
| Gate 2 review B1, B2, M1 to M3, N1, N2, P1 to P3 | Responded in Draft 2; verified (ef5859e) as CLOSED for B2, N1, N2 and C7; PARTIAL elsewhere | [`PHASE-4-WP1-ARCHITECTURE-C-GATE2-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-GATE2-CORRECTIONS.md) |
| Gate 2 verification V1 to V4 | Responded in Draft 3; verified at `63c86e4`: V2, V3 CLOSED; V1, V4 PARTIAL | [`PHASE-4-WP1-ARCHITECTURE-C-V1-V4-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-V1-V4-CORRECTIONS.md) |
| Draft 3 verification V1 (label authority, terminal-lock scope) and V4 (exactly-one scope) | Responded in this draft; verification is Codex's | [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-CORRECTIONS.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT4-CORRECTIONS.md) |
| B-9 grouping current-run check | Source lacks it [SRC]; required | §4.3; U |

### 14.3 Accepted Phase 0–3 invariants

| Invariant | Coverage |
|---|---|
| Permanent IDs and Archive ID (BUILD §7–8, V0002, ADR-0005/0006) | INV-1; §7.5, §7.6 |
| Immutable originals (TX, IV) | INV-2; C-3 |
| Unexpected objects reported (TX-006, IV) | INV-3 |
| No inferred success (TX-002); crash-safe ingestion (TX-003/005) | INV-6; T-2, T-4 |
| Visible storage failure (TX-004/008) | C-2; §5.7 |
| Receiver lease and failed-STAGED-CAS protections (Phase 2 audit regressions) | §5.8 |
| Optimistic concurrency (SEC-008) | §4 |
| Restriction (SEC-004, D4-16, D4-27) | INV-10 |
| Jobs and leases (OP-001/004, V0008) | B-12 |
| Portable paths and markers (ADR-0002) | §7.6; §5.8 |
| Upgrade refusal; recoverable migration (UP-004…010, BUILD §56) | §7.1, §7.7 |
| Grouping safeguards (Phase 3) | B-9 |

---

## 15. Complexity budget and comparison with revision 3.9

| Measure | Revision 3.9 | This contract (Draft 3) |
|---|---|---|
| New control tables | 9 | 1, or 2 with a dedicated receipt table |
| Tables under a generic gate | 49 | 0 |
| Controller, run and connection protocols | yes | none |
| Epochs, publications, action journal | yes | none |
| Ordinary ownership | advisory, content and claim coordination with broad admission premises | the same retained coordination plus one durable interval token (T-1 to T-7) |
| Maintenance durable state | multiple interacting control states | READY/OFFLINE plus typed receipts. The external facts (freeze, role admission, fence, drain, history checkpoint) are recorded, not modelled as states. |
| Native boundary | JNA same-handle | one local fence; candidate open (OD-3) |
| Ordinary business windows | 19 / 22 | unchanged |

**Administrative transitions, listed** (they are transactions, not a framework). **[G3-V2]** Each has a
kind-specific atomic target (§9.2):
1. Native Flyway migration steps: schema and history only, with no receipt;
2. SEED, inside the control migration;
3. ENTER_OFFLINE: CAS plus receipt;
4. MIGRATED: an aggregate checkpoint after migrations, never atomic with them;
5. ARCHIVE_INITIALIZED, inside the accepted initialization transaction;
6. REBIND: CAS plus receipt;
7. READY: CAS plus receipt.

The ordinary token transitions are T-1, T-3, T-4 and T-6.

**Three authority layers are kept deliberately separate**, because they address different failure domains:
- subject effect ownership (the token);
- process and installation exclusion (the fence and the freeze);
- database administrative admission (roles, `NOLOGIN`, drain).

Reducing them to one advisory lock would be unsafe.

**What the Gate 2 corrections added:** no new table, controller, journal, epoch or mandatory native dependency.
- B1 completes the token's transaction rules.
- B2 adds a narrow bootstrap case.
- M1 reuses the existing operation.
- M2 scopes the evidence rules.
- M3 completes the receipt's data contract.

**What the V1–V4 corrections added** **[G3]:** no table, state, controller, journal, epoch, registry or framework.
- **V1** replaces a premise (token-owned O), adds an O-row lock-class inventory, gives the reclaim an executable SQL
  form, and removes the runtime's signalling capability. **[G4-V1]** Draft 3's label census as authority is
  **removed**. Labels are diagnostic only. Reclaim relies on E-1 (process gone) and E-2 (the `NOWAIT` row fence
  with T-3's first-lock predicate), and refuses otherwise, falling back to the existing offline drain.
- **V2** narrows the receipts to kind-specific atomic targets.
- **V3** narrows the token-free list.
- **V4** adopts the retained derivations by reference. **[G4-V4]** Exactly-one applies only to mutually exclusive
  producer classes; the consistency checks are cumulative.

**Draft 4 net change:** one mechanism removed (label authority). Nothing added beyond restating the retained
owned-O-first placement as mandatory for EDTs.

---

## 16. Consistency updates for later (reported, not made)

- **`PHASE-4-PLAN.md` §10 and §10.1:** the WP1 gate content and the contract reference.
- **`issues.md`:** cross-references from GAS-P4-002…005 to this contract.
- **`deploy/linux/install.sh` and the Windows scripts:** "migrate as the service account" conflicts with C-06. That
  is a later implementation change.
- **`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md` §5.5.3:** describes L1 as the unchanged Phase 3 chain.
- **`PHASE-4-DECISIONS.md`:** future OD outcomes.

**WP1 remains unaccepted. WP2 remains on HOLD. No finding is closed. Nothing in this document is implemented or
authorized for implementation.**
