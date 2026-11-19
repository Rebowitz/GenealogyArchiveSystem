# Phase 4 WP1 Architecture C: Gate B feasibility review plan

| | |
|---|---|
| Status | **DRAFT — NOT APPROVED. Revision 4 (2026-11-19).** Planning document only. It authorizes no experiment, prototype, environment, account or review. Prepared for **one consolidated independent Codex review** under §10A.4; that review is not initiated by this document. |
| Revision 2 | Incorporates the owner's final scope clarification of 2026-11-19: local storage as the primary baseline; NFS as the initial NAS evaluation target, without making it an exclusive dependency; SMB and other network filesystems supportable once independently verified; no exhaustive NAS protocol testing; conservative recovery for unproven configurations; bounded Gate B checks for transaction ordering, outermost-transaction ownership and renewal performance; planning questions consolidated (§11). |
| Revision 3 | Narrow correction for the consolidated Codex plan review (CORRECTIONS REQUIRED): **GB-P1** (MAJOR) one acceptance rule for F-R and Q-A (§5.9 F-R, §11.1); **GB-N1** (MINOR) P2 second-channel experiment as a labelled negative control (§5.2); **GB-N2** (MINOR) observer authority and placement (§4.4, §4.5). No scope, decision or contract change. |
| Revision 4 | Targeted verification closed GB-N1 and GB-N2 and left GB-P1 PARTIAL on two points. Correction confined to §5.9 F-R and §11.1 Q-A: (1) renewal XID captured with `pg_current_xact_id()` on the same connection and inside the same outermost transaction as the renewal UPDATE; (2) deadline-bound, uniquely identifying separate-connection readback as the completion evidence, with the commit timestamp as corroboration only. PASS, FAIL, INVALID and INCONCLUSIVE made consistent. Q-A threshold and diagnostic runs unchanged. |
| Governing rule | [`ENGINEERING-DESIGN-INSTRUCTIONS.md`](ENGINEERING-DESIGN-INSTRUCTIONS.md) §10A (owner-approved, adopted 2026-11-18), in particular §10A.1 (review plan) and §10A.2 (Gate B) |
| Artifact under Gate B | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), Draft 7, unchanged since `5cfb0b4df6cf244950d6dca52715ffd5c30504b5` and accepted at Gate A by **D4-33** |
| Owner selections incorporated | **D4-34** (2026-11-18): OD-3, OD-6 and OD-8, each Option A, each conditional on feasibility (§3) |
| Repository baseline for this plan | `phase-4` at `90de3abc68bcc21035eb12b11c776297ec2853ea` (Revision 1: `d93e74a`; Revision 2: `224ce73`, Q-A recorded `3410b7f`) |
| Gates | **A:** ACCEPTED 2026-11-18 (D4-33). **B:** NOT ACCEPTED; proof execution NOT AUTHORIZED. **C:** NOT AUTHORIZED. **WP2:** HOLD. |

---

## 1. Purpose and limits

This plan states, before any Gate B review or proof execution, what Gate B must demonstrate, how, on what
environments, how the evidence is judged, and how the review cycle starts, stops and escalates.

It does **not**:
- authorize any experiment, prototype code, scratch database, test host, service account or NAS access (§10);
- alter the accepted contract, D4-31…D4-34, or any review or correction record;
- approve OD-1, OD-2, OD-4 or OD-5;
- treat the D4-34 selections as feasible. They are approved preferences whose feasibility is **not verified**.

Where this plan and the accepted contract differ, the contract governs, and the difference is a finding against this
plan.

---

## 2. Basis verified

| Item | Verified state |
|---|---|
| Baseline | `phase-4` = `origin/phase-4` = `90de3ab`; clean working tree |
| Contract | Unchanged since `5cfb0b4` (Gate A baseline). Proof material: §5.3 (P1), §5.5 (P3), §6.3 (P2), §10.1 (authority), §10.2 (identities), §12 (Proof 1, Proof 2 (a)–(q)), §12.1 (variants A–C), §13 (OD table) |
| D4-33 | Gate A ACCEPTED; every feasibility and verification obligation open; Gate B requires a written §10A.1 plan first |
| D4-34 | OD-3 A (FileChannel first, conditional on P2); OD-6 A (dedicated non-superuser administrative role, conditional on privilege verification on PostgreSQL 16 and 18); OD-8 A (OS machine identifier, conditional on Windows and Linux validation). No automatic JNA, no silent privilege escalation, no silent identity-file substitution. |
| Platforms in the repository | Java 21 (`pom.xml`); Spring Boot 4.1.1; Flyway with the PostgreSQL module; Linux systemd units running as `genealogy-archive` (web and worker share it); Windows WinSW services under separate virtual accounts `NT SERVICE\GenealogyArchiveWeb` and `NT SERVICE\GenealogyArchiveWorker` |
| Legacy artifacts | Tag `phase-3-accepted` (`c494d6a`); the current, unaccepted Phase 4 build on `phase-4` |

---

## 3. Gate B scope

**Owner scope clarification (2026-11-19).** The split below follows the owner's clarification and the contract's own
division in §12 ("Left to implementation acceptance"):
- **Storage.** Local storage is the primary baseline in every cell. **NFS is the initial NAS evaluation target**, not
  an exclusive architectural dependency. SMB and other network filesystems remain supportable once their safety
  properties are independently verified (§6.2). Exhaustive NAS protocol testing is **not** required for Gate B.
- **Conservative recovery.** Any storage configuration whose safety properties are unproven keeps the contract's
  fail-closed route: conflicting takeover is refused until an administrator verifies storage or access resolution
  (contract §5.5, §5.7).
- **Bounded checks.** Transaction ordering, outermost-transaction ownership and renewal performance get **bounded
  feasibility checks only** (§5.9). Full implementation verification of each remains Gate C.
- The accepted contract is unchanged.

### 3.1 In Gate B: feasibility evidence

| ID | Item | Contract basis | Decides |
|---|---|---|---|
| **P1** | Process identity: absent, same, or PID-reused process, under the real service accounts | §5.3 | E-1 automatic reclaim |
| **P2** | `FileChannel` process-lifetime installation fence | §6.3 | **OD-3 condition** |
| **P3** | NAS data and namespace completion after former-process death, on **NFS** (initial NAS target) | §5.5 | Whether conflicting takeover may be automatic on the tested NFS configuration; otherwise the conservative route |
| **V-R** | Administrative principal capability verification | §10.1, §10.4 | **OD-6 condition** |
| **V-H** | OS machine identifier platform validation | §10.2 | **OD-8 condition** |
| **Proof 1** | Exclusive maintenance boundary, cases (a)–(o) | §6, §7, §12 | Offline maintenance feasibility |
| **Proof 2** | Former-writer fencing, cases (a)–(q) | §5, §12 | Token and reclaim feasibility |
| **§12.1** | Surviving-renewal regression, variants A–C | §12.1 | Single-row renewal under client death |
| **F-O** | Bounded check: token-owned O prefix in UUID order as the first lock, on the prototype paths | §5.4.1, §5.4.2, T-3 | Whether the O-first ordering is achievable on representative paths |
| **F-T** | Bounded check: outermost-transaction ownership of T-1, T-4 and T-6 under Spring `TransactionTemplate` | T-1, T-4, T-6 | Whether outermost ownership can be enforced, and fails closed when nested |
| **F-R** | Bounded check: per-operation receiver renewal throughput | §5.4.1 renewal row; T-m | Whether one-operation-per-transaction renewal keeps up at the planning load |

V-R and V-H are not separately named in the contract. They are the verifications D4-34 attaches to OD-6 and OD-8.
They test only the **capabilities** of the selected options. They are not the deployment privilege inventory.

### 3.2 Retained for Gate C (not Gate B evidence)

These stay open and are **not** satisfied by any Gate B result:
- the **complete** EDT, operation-row-writer and implicit-lock inventory, and **all** retained-window reorderings
  (§5.4.2); F-O covers representative paths only;
- **codebase-wide** enforcement of outermost T-1, T-4 and T-6; F-T covers the mechanism on the prototype paths only;
- §5.8 effect-inventory completeness and complete EDT coverage;
- the full privilege inventory and deployment changes (contract §12 "obligation 5");
- realistic inherited-state fixtures (obligation 7);
- B-9 grouping current-run revalidation and the late-runner test;
- §5.8 integrity and recovery compatibility;
- the full Phase 0–3 and Phase 4 regression matrix on Windows and Linux × PostgreSQL 16 and 18 (obligations 9, 10);
- receiver renewal performance under production deployment and workload; F-R is a bounded characterisation only;
- verification of any further storage topology for automatic takeover (§6.2).

A Gate B prototype that happens to exercise one of these is not evidence for it.

### 3.3 Excluded from Gate B

- OD-1 (D-LP-9 (a)), OD-2 (D-LP-3), OD-4 (post-startup mismatch lifecycle), OD-5 (receipt representation): not
  approved and not decided by any Gate B outcome. Where a proof needs a stand-in, §4.3 applies.
- Multi-host producers, Phase 8 backup and restore, reverse migrations (contract §2.2).
- JNA or any other new dependency. If P2 fails, the owner decides separately (D4-34).
- Exhaustive NAS protocol testing. SMB, NFS versions or server products other than the authorized NFS configuration,
  and other network filesystems are **not** Gate B items. They are neither withdrawn nor approved for automatic
  takeover; until verified (§6.2) they use the conservative route.
- Any production deployment or production data.

---

## 4. Common execution rules

### 4.1 Platform and version coverage

| Cell | Application host | PostgreSQL | Required for |
|---|---|---|---|
| L16 | Linux (systemd), x86-64 | 16, on the same host | All items |
| L18 | Linux (systemd), x86-64 | 18, on the same host | All items |
| W16 | Windows (WinSW, virtual accounts), x86-64 | 16, on the same host | All items |
| W18 | Windows (WinSW, virtual accounts), x86-64 | 18, on the same host | All items |

- Each cell records exact OS build, kernel or Windows build, filesystem type, JDK vendor and build, PostgreSQL minor
  version, and the relevant server settings (§4.4).
- **P1, P2 and V-H** do not depend on PostgreSQL; one run per OS suffices, unless the proof involves the database.
- **V-R** is a PostgreSQL question; it runs on both versions on each OS, because Windows and Linux packaging differ.
- **Remote PostgreSQL** (contract A-3) is a supported deployment but not in the base matrix (§11.2).
- A cell that cannot be run is reported as **NOT RUN**, never as PASS by analogy from another cell.

**Storage dimension.**

| Storage | Where | Status in Gate B |
|---|---|---|
| Local filesystem (Linux ext4 or xfs; Windows NTFS) | Every cell | **Primary baseline.** Required for every storage-touching item. |
| NFS, one authorized configuration (§6.1) | Linux cells (L16 or L18; one suffices for storage semantics) | **Initial NAS evaluation target.** P3, Proof 2 (b) and (g), and NAS-labelled Proof 1 cases. |
| NFS from a Windows client | — | Not required. Optional only if the owner authorizes it. |
| SMB and other network filesystems | — | Not tested in Gate B. Conservative route applies until verified (§6.2). |

### 4.2 Test-only prototype boundary

Gate B needs code that does not yet exist: the token columns, the fence, a maintenance-tool prototype, receipts, test
hooks and a client-death harness. Subject to owner authorization (AU-3):
- **Location.** A separate feasibility branch from `phase-4` (proposed `phase-4-gate-b-feasibility`). Nothing from it
  is merged into `phase-4`, `main`, or any accepted tag.
- **Production files.** Proof 2 needs the token "wired into one INGEST promotion, one quarantine move with
  settlement, one U release, receiving and `cleanStaging`". Those prototype edits exist **only** on the feasibility
  branch.
- **Marking.** Every prototype class, migration and SQL object is named and documented as test-only.
- **Reuse.** Prototype code is evidence, not an implementation. Gate C implementation is separately authorized and
  separately reviewed, whether or not it reuses prototype code (§11.2).
- **Dependencies.** No new runtime dependency. A test-only library needs owner approval and must not appear in a
  production artifact.
- **Seams.** Test hooks and server-side barrier functions exist only in scratch databases and test builds.

### 4.3 Stand-ins for open decisions

| Open decision | Gate B treatment |
|---|---|
| OD-5 receipt representation | Proof 1 uses a **prototype** receipt store satisfying the §9.2 data contract, labelled as a stand-in. It does not select OD-5; an outcome that depends on the representation is reported as such. |
| OD-4 post-startup mismatch | Gate B tests only the startup identity checks (§10.3 list) and refusals in Proof 1 (m). The post-startup lifecycle is **not** exercised. |
| OD-1, OD-2 | Not exercised. |

### 4.4 Scratch databases

- Dedicated, disposable PostgreSQL 16 and 18 clusters per cell. No production or user data. Created from scripts and
  destroyed after evidence capture.
- Harness superuser access is used **only** for setup, teardown and installing test-only functions. It is never the
  role under test.
- Roles under test: the runtime role (per installation), the dedicated administrative role (V-R), and, where a case
  needs it, a second installation's runtime role on the same cluster (to measure termination reach).
- **Independent observer** **[GB-N2]**: a separate, dedicated harness role, **not** under test. Its authority is
  read-only observation:
  - non-superuser, with `pg_monitor` (for full `pg_stat_activity` visibility) and SELECT on the named test tables it
    must read;
  - no write, DDL, role administration, backend signalling, `pg_signal_backend`, or membership in the runtime or
    administrative role.
  It never performs, and is never credited with, an operation of the administrative role. Every V-R capability and
  every maintenance-tool step is proved only through sessions of the administrative role itself (non-superuser,
  §5.4). The harness superuser stays limited to setup, teardown and installing test-only functions.
- `track_commit_timestamp = on` in every cluster that runs F-R (§5.9), and recorded.
- Settings recorded per run: `lock_timeout`, `deadlock_timeout`, `statement_timeout`, `idle_in_transaction_session_timeout`,
  `client_connection_check_interval`, TCP keepalive settings, `max_prepared_transactions` (non-zero for Proof 1 (d)),
  `log_lock_waits`.
- `client_connection_check_interval` is **0** for client-death cases unless a case tests it. A non-zero value can end
  the backend and invalidate §12.1 variant A (A-3).
- Schema state is produced from the authentic Flyway chain at a recorded commit, never hand-built, except for named
  test-only objects.

### 4.5 Process-death and process-identity instrumentation

The harness is the oracle for process facts; **it must not rely on the mechanism under test**.
- **Launch.** Every subject process is launched by a harness parent that records PID, OS account, command line, and an
  independent OS start time (Linux `/proc/<pid>/stat` field 22 with boot time; Windows `GetProcessTimes` through an
  OS tool) alongside the Java `ProcessHandle` value.
- **Kill.** Linux `SIGKILL`; Windows `TerminateProcess` (`taskkill /F` or equivalent). Graceful stops are a separate,
  labelled case.
- **Exit confirmation.** By the parent's wait on the child and its exit code, plus a fresh OS enumeration. Never by
  `ProcessHandle` alone where P1 is the subject.
- **Backend identity.** Every connection records `pg_backend_pid()` and `backend_start` at connect time (T-2). The
  harness separately records backend liveness from the independent observer (§4.4).
- **Observer placement** **[GB-N2]:**
  - The observer connects to a **different database** of the same cluster (for example `postgres`). Backend and lock
    views (`pg_stat_activity`, `pg_locks`, `pg_blocking_pids`) are cluster-wide, so liveness observation needs no
    connection to the target database.
  - A connection to the target database (only to read named test tables) is closed **before M-5 begins** and is not
    reopened until the case's M-5 observations are complete. This keeps the contract's maintenance connection
    boundary: during M-5 the target database has only the maintenance tool's own connections (§6.4).
  - An observer connection present in the target database during M-5 makes the run **INVALID**: it would either be
    drained, perturbing the case, or be a surviving non-tool backend.
- **Effective identities** **[GB-N2]:** for every connection used in a run, the evidence records the actor (tool,
  runtime process, observer or harness setup), `session_user`, `current_user`, `rolsuper` of each, database,
  `pid` and `backend_start`. An action whose recorded effective identity is not the role it is credited to does
  not count as evidence for that role.
- **Client death with live backend.** The server statement is held at a server-side barrier (§12.1 common rules) so
  it survives client death. If the backend has ended, the run is **INVALID**.
- **Clocks.** Wall-clock and monotonic timestamps on every event; NTP state recorded; P1 includes a clock-step case.

### 4.6 Evidence and outcome categories

Every run produces a recorded log: cell, commit of the feasibility branch, configuration, timeline, captured SQL and
parameters, SQLSTATEs, OS process facts, filesystem and hash observations, and the outcome.

| Outcome | Meaning |
|---|---|
| **PASS** | Every precondition was established, and every required observation was made, with no failure criterion met |
| **FAIL** | An unsafe effect occurred (contract §12: "a failure criterion is an unsafe effect"), or the hypothesis was falsified |
| **INVALID** | A precondition was not established, or the harness was shown defective. Says nothing about the hypothesis. |
| **INCONCLUSIVE** | The run was valid but could not produce the deciding observation. **Treated as not proven**, so the contract's fail-closed route applies. |
| **NOT RUN** | Not attempted in that cell |

- Safe blocking or refusal before admission is an expected observation, not a failure (contract §12, G2-C6).
- Retries never hide 40P01 or unexpected 55P03.
- A test changed after a FAIL to make it pass is prohibited (CLAUDE.md working method 9). A harness defect may be
  fixed and the run repeated as a new, recorded run, with the defect disclosed.

### 4.7 Safety of the test environment

- Destructive actions (kills, service masking, `NOLOGIN`, backend termination, NAS pauses) only on authorized test
  hosts and scratch clusters (AU-4).
- No step touches a production Archive Instance, a production Storage Volume, or user data.

---

## 5. Feasibility items

Each item states: hypothesis; prerequisites; environment; observable evidence; pass, fail and invalid criteria; and
safety implications. "Safety implications" says what a FAIL or INCONCLUSIVE means for the archive under the accepted
contract.

### 5.1 P1: process identity (§5.3)

| | |
|---|---|
| **Hypothesis** | Under the real service accounts on both platforms, Java 21 `ProcessHandle` distinguishes an absent recorded process, the same process, and a different process reusing the PID, or fails closed when it cannot. |
| **Prerequisites** | Harness launcher (§4.5); installed service accounts; test-only token column storing `effect_owner_started_at` as `timestamptz`. |
| **Environment** | Linux: `genealogy-archive` service user observing another process of the same user, and the admin account observing it. Windows: `NT SERVICE\GenealogyArchiveWeb` observing a `…Worker` process and the reverse (different virtual accounts), and the admin account. |
| **Test points** | (1) `ProcessHandle.of(pid)` empty versus present; (2) `Info.startInstant()` empty versus present for the same, a cross-account, and an elevated process; (3) distinguishing "no such process" from an access or query error; (4) round trip of the start instant through `timestamptz` (microsecond) and the canonicalisation rule, from Windows 100 ns and Linux clock-tick sources; (5) repeated reads of a long-lived process over hours and across a clock step, to detect drift in the derived start time; (6) PID reuse: real reuse where achievable (Linux: PID cycling on a host with a low `pid_max`; Windows: cycling until a recorded PID reappears), and simulated reuse (a live PID with a recorded different start time), each labelled. |
| **Evidence** | For each case: harness OS facts versus `ProcessHandle` results; the stored and re-read `timestamptz`; the classification produced (absent, same, different, refuse). |
| **Pass** | Every case is classified correctly or refuses; no live recorded process is ever classified absent or different, on any account pair, after the round trip, or across the clock step. |
| **Fail** | Any live recorded process classified absent or different (an unsafe reclaim would follow). |
| **Invalid** | The harness could not confirm the true process state independently. |
| **Safety implications** | A FAIL means E-1 cannot authorize automatic reclaim on that platform and account arrangement; the reclaim rule refuses and recovery is administrative (§5.3, §5.7). A cross-account "refuse" result is safe but may remove automatic recovery for some subjects; it is reported as an availability finding. |

### 5.2 P2: installation fence, FileChannel first (§6.3; OD-3 condition)

| | |
|---|---|
| **Hypothesis** | A dependency-free `FileChannel` lock gives protected-object, JVM-lifetime exclusion on the supported local Windows and Linux filesystems, under the seven §6.3 conditions. |
| **Prerequisites** | Prototype fence acquisition in a producer JVM (shared) and in the maintenance-tool prototype (exclusive, non-blocking); a protected directory owned by root or Administrators and not writable by the service accounts; the canonical local path. |
| **Environment** | Linux ext4 or xfs (recorded) local; Windows NTFS local. All service accounts of §4.1. |
| **Test points** | (1) Shared lock from a channel the service account can open (read-only access to the object), concurrently from several JVMs and both Windows virtual accounts; (2) exclusive non-blocking acquisition refused while any shared holder lives; (3) release on JVM exit after `SIGKILL` / `TerminateProcess` and after normal exit; (4) **no release** while the JVM lives: GC pressure with the strong reference held, shutdown hooks, partial startup failure; (5) **labelled negative control, outside supported operating conditions [GB-N1]:** a second channel to the lock path deliberately opened and closed in the same JVM, which §6.3 forbids. It shows whether the platform releases the lock (for example POSIX record-lock release), and so whether the "no other code opens the path" restriction is load-bearing. It is recorded separately as a control observation (release observed / not observed), never as a supported-path PASS or FAIL; (6) detection of a platform that silently upgrades shared to exclusive, or serialises JVMs; (7) a missing, replaced or re-created object makes producer startup and maintenance refuse: this needs a **file-identity check**. Java's `fileKey()` is available on Linux but may be `null` on Windows, so a dependency-free Windows identity method must be found or the test point fails; (8) child processes spawned by a producer do not hold or release the fence; (9) a symlink or junction substituted in the path is refused. |
| **Evidence** | Lock acquisition results and exceptions per process; OS lock views (`/proc/locks`; Windows handle tools); identity values; exit confirmations from §4.5. |
| **Pass** | Supported-path test points (1)–(4) and (6)–(9) hold on both platforms, and the control (5) was run and recorded. |
| **Fail** | On the supported paths: any acquisition of the exclusive lock while a compatible producer lives; any release before JVM exit not caused by the producer's own exit; inability to detect a replaced object on either platform. |
| **Negative control (5)** | Release observed **only** in the control demonstrates why the no-second-channel restriction matters; it does not falsify supported-path feasibility. No release observed does not relax the restriction. The control's result never changes a supported-path criterion above. |
| **Invalid** | Producer liveness not independently confirmed; directory permissions not as specified. |
| **Safety implications** | A FAIL means maintenance cannot prove the absence of compatible producers. Under D4-34, **no JNA is adopted automatically**: the result goes to the owner for a separate dependency and alternative-mechanism decision. Proof 1 cases that depend on the fence stop (§7). |

### 5.3 P3: NAS data and namespace completion (§5.5)

| | |
|---|---|
| **Hypothesis** | After the former process has terminated, and the storage outcome was reached or the interval was left open, an old client cannot later change the bytes or the namespace state relied on by the new owner, on the tested topology. |
| **Prerequisites** | NAS configuration information (§6), authorized; a stall injector able to pause the server or export; Proof 2 prototype paths for copy and rename. |
| **Environment** | The one authorized NFS configuration (§6.1), from a Linux client. Local volumes as the control. No other protocol. |
| **Test points** | (1) Stalled copy, then client kill, server resumed, reconnection; (2) stalled rename and stalled delete, same sequence; (3) unforced write left in the client cache, then client process death (client kernel write-back can outlive the process); (4) observation of bytes and namespace **at the server**, not only from the client, after the new owner verified size and hash; (5) after reconnection, whether the client replays or retransmits any request from the dead process (for NFSv4, state and lease recovery). |
| **Evidence** | Server-side and client-side timelines; hashes before and after; server logs where available. |
| **Pass** | A **mechanism-level** demonstration for the topology that no accepted late change is possible after the defined end state, plus no late change observed. An observation window with no late change is **not** sufficient alone. |
| **Fail** | Any late write or namespace change altering what the new owner relied on. |
| **Inconclusive** | No late change observed, but no mechanism-level demonstration. Expected to be common. |
| **Safety implications** | FAIL or INCONCLUSIVE: conflicting takeover on that NFS configuration stays refused until the administrator verifies storage or access resolution (§5.5, §5.7). NFS and NAS support are not withdrawn. A PASS certifies only that NFS version, server and mount configuration; every other configuration stays on the conservative route. |

### 5.4 V-R: administrative principal capabilities (§10.1; OD-6 condition)

| | |
|---|---|
| **Hypothesis** | A dedicated non-superuser administrative role, separate from the runtime role, can hold exactly the capabilities §10.1 requires, on PostgreSQL 16 and 18, while the runtime role holds none of the forbidden ones. |
| **Prerequisites** | Scratch clusters; a candidate grant script (test-only); the authentic Flyway chain. |
| **Environment** | All four cells. |
| **Capabilities to verify (admin)** | (1) own the schema and run the full Flyway chain, including any objects needing elevated rights (extensions, if used: to be checked); (2) write `installation_state` and receipts; (3) change the runtime role's LOGIN / NOLOGIN (`CREATEROLE` with ADMIN OPTION on that role, PostgreSQL 16 rules); (4) enumerate every backend of the database with `pid`, `backend_start`, role and state, including other roles' backends (visibility may need `pg_read_all_stats` or membership: to be established); (5) terminate this installation's runtime backends, by role membership or `pg_signal_backend`, and **measure the reach**: whether the same grant can terminate another installation's backends on a shared cluster. |
| **Negatives (runtime)** | No DDL; no write to `installation_state` or receipts; no role administration; no `pg_signal_backend` grant; cannot signal other roles' backends. The default same-role signalling capability is recorded as present and as a supported-program rule, not an enforced boundary (§10.1). |
| **Evidence** | Grant script; per-capability statement results and SQLSTATEs on both versions; `\du` and `has_*_privilege` output. |
| **Pass** | Every required capability works and every forbidden one is refused, on both versions, with no superuser attribute and no grant beyond the script. |
| **Fail** | A required capability needs superuser or an unbounded grant; or a forbidden runtime capability exists other than the documented same-role signalling. |
| **Safety implications** | Under D4-34, a capability that cannot be provided safely **returns to the owner**; privileges are not escalated to make a proof pass. Runtime code is never granted signalling authority. A termination reach wider than one installation is reported as a finding for the owner, not silently accepted. |

### 5.5 V-H: OS machine identifier (§10.2; OD-8 condition)

| | |
|---|---|
| **Hypothesis** | The OS machine identifier can be read reliably by every producer account and the maintenance tool on both platforms, is stable for the life of the installation, and every unavailable, unreadable or invalid case refuses. |
| **Sources under test** | Linux: `/etc/machine-id`. Windows: `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid` (64-bit registry view). No fallback source (for example `/var/lib/dbus/machine-id` or a provisioned file) is used (D4-34). |
| **Test points** | (1) Read by each service account and the admin account; all read the same value; (2) a **dependency-free Java read path on Windows** (Java has no registry API; a subprocess such as `reg query` is a candidate whose own failure modes must be tested); (3) format validation; (4) missing, empty, `uninitialized` (systemd first boot), unreadable (permissions) and malformed cases each refuse; (5) stability across reboot, service restart and OS update; (6) clone behaviour (VM clone, Windows sysprep) recorded as characterisation: a clone may match, which is the C-05 residual. |
| **Pass** | Points (1)–(5) hold on both platforms; (6) is recorded. |
| **Fail** | Any account reads a different or no value without refusing; any invalid case permits an operation; instability across a routine event. |
| **Safety implications** | An unusable identifier must not permit an unsafe operation, and no identity file is substituted (D4-34). A FAIL returns to the owner. |

### 5.6 Proof 1: exclusive maintenance boundary (§12, cases (a)–(o))

**Hypothesis.** The offline maintenance procedure (M-1…M-6, migration, validation, X-1…X-4, bootstrap B-1…B-6)
excludes every producer and legacy actor, classifies every unknown COMMIT only after the original backend is gone,
and recovers from the actual state.

**Prerequisites.** P2 PASS (fence-dependent cases); V-R PASS; V-H PASS (binding cases); the maintenance-tool
prototype; prototype `installation_state` and the OD-5 stand-in receipt store; installed systemd and WinSW services;
the `phase-3-accepted` and current Phase 4 builds as legacy artifacts.

**Environment.** All four cells on local volumes. NAS-labelled runs only on the authorized NFS configuration.

| Case | Specific setup (contract §12) | Required observation | Fail |
|---|---|---|---|
| (a) | Legacy web mid-upload; legacy worker mid-job; legacy CLI effects | Entry refuses while alive or able to restart | Any legacy write; entry proceeds |
| (b) | Compatible worker with an open interval | Entry refuses (fence held) | Fence acquired while it lives |
| (c) | Orphan runtime backend in an open transaction | Drained at M-5 and confirmed absent | Committed runtime transaction during OFFLINE |
| (d) | Prepared transaction | Refuses; never resolved automatically | Automatic resolution |
| (e) | 3-step migration; fault after step 2; unknown COMMIT on step 3 with the original backend held alive | Classification only after the backend is gone, from history and receipt | Decision while the backend lived |
| (f) | Rebind crash after its transaction; unknown REBIND COMMIT with the backend alive | As (e) | Second rebind |
| (g) | Restart policies left enabled (negative control) | Entry refuses | Entry proceeds |
| (h) | Bootstrap: crashes and unknown COMMITs at B-3, B-4; interrupted B-5 | One Archive ID or OFFLINE refusal | Second Archive ID |
| (i) | Crashes after M-2, M-3, M-6, X-2 and during each X-3 action | Recovery reads the actual state (§6.6) | Reopen without validation |
| (j) | Unknown READY COMMIT, backend alive | Exact-receipt classification after backend gone | Decision while alive |
| (k) | Old configuration with retired credential; new configuration given to old binaries | Zero database mutation; zero protected filesystem effect | Any legacy effect |
| (l) | Role capability negatives (overlaps V-R) | Runtime refused; admin completes | Any forbidden capability |
| (m) | Wrong archive, host or configuration; missing or replaced fence object | Refuses before migration, termination or binding change | Any of those |
| (n) | History committed, MIGRATED receipt absent; mismatching receipt | `reconciled` receipt from validated history; mismatch not accepted | Mismatching receipt accepted |
| (o) | Unknown COMMIT by kind (SEED, ARCHIVE_INITIALIZED, ENTER_OFFLINE, REBIND, READY) | Kind-specific classification | Any other classification basis |

**Invalid.** "Backend held alive" not established for (e), (f) or (j); legacy artifact not the recorded build.
**Safety implications.** Any FAIL is an unsafe effect against the offline boundary: Gate B cannot pass for Proof 1,
and the result goes to the owner (§8.5).

### 5.7 Proof 2: former-writer fencing (§12, cases (a)–(q))

**Hypothesis.** The effect-owner token (T-1…T-7) with E-1 and E-2 prevents any conflicting W2 effect or EDT while W1's
interval may be open, and reclaims safely after confirmed exit.

**Prerequisites.** P1 results per platform (E-1 cases); prototype token wired into one INGEST promotion, one
quarantine move with settlement, one U release, receiving and `cleanStaging` (feasibility branch only); a seam that
blocks inside a filesystem call; a stalled storage target for (b) and (g) (P3 environment).

**Environment.** All four cells on local volumes. Cases (b) and (g) also on the authorized NFS configuration. Java-seam
blocking is labelled separately from kernel or NFS stalls.

**Common failure criteria (contract §12).** Any W2 effect while W1 exists; reclaim by elapsed time; a committed stale
EDT; reclaim of another host's token; duplicate File or content; adopted unverified byte; a late write or namespace
change altering what W2 relied on; any runtime or label-based termination; 40P01 on a corrected path.

| Case | Claim tested | Specific setup and environment | Required evidence | Invalid if | Safety implication of FAIL |
|---|---|---|---|---|---|
| (a) | Lost advisory backend does not let W2 act | W1 promoting; its backend terminated while its JVM writes | No W2 effect while W1 lives | W1's JVM not confirmed alive | Concurrent writers on one subject |
| (b) | Blocked I/O keeps exclusion (C-2) | W1 blocked in a real kernel or NAS call | Refusal for the whole stall | Block not confirmed in the kernel | Takeover during a stall |
| (c) | Kill mid-write, then safe reclaim | `SIGKILL` / `TerminateProcess` mid-write | Reclaim event; byte verification (C-3); one result | Exit not confirmed by §4.5 | Adopted partial bytes |
| (d) | Token durability (B1) | Fail before acquire commits; unknown acquire and release COMMITs; kill with O held (T-e) and before the check (T-f); Q and U EDT validation | T-e refuses until backend terminated; T-f rolls back fully | Backend state not established | Stale EDT commits |
| (e) | Receiving (M1) | Renewal loss with live receiver; timeout ABANDONED; external cleanup attempt; receiver closes | No STAGED or File after cancel; no cleanup while receiver may write; slow upload unchanged | Receiver liveness unknown | Cleanup under a live writer |
| (f) | Process identity across accounts | P1 cases inside the token flow | Correct E-1 decisions | P1 harness invalid | Unsafe E-1 |
| (g) | Storage completion | Stalled copy and rename; reconnection after termination | As P3 | As P3 | As P3 |
| (h) | Same and several JVMs | In-memory interval set vs running async effect | Never cleared while effect runs | Effect state not observed | Self-takeover |
| (i) | Another host's token; unreadable identity | Token with foreign host ID; identity error | Refusal | — | Cross-host reclaim |
| (j) | Executable reclaim | NOWAIT vs a legitimate holder (renewal, note); `lock_timeout` 55P03 on table lock; surviving former backend holding O | 55P03, rollback, no termination; resolution only by PostgreSQL or offline drain | Holder not established (§12.1 evidence rule) | Reclaim under a live holder |
| (k) | Own-backend resolution (T-2) | Unknown acquire COMMIT; own backend still present | Report; no runtime signal | Own tuple not recorded | Runtime signalling |
| (l) | O-row lock classes | Renewal, stale CAS, notes, token transactions vs token-owned EDTs | Zero 40P01 | Retries enabled | Deadlock on a corrected path |
| (m) | Cancellation finalization | `performCancel` vs live lost-advisory owner, each branch | Refusal until interval held; then every step an EDT | Branch not reached | Q, U, claim or staging change without interval |
| (n) | Label-independent exclusion | Former EDT backend before its O check; label missing, changed or truncated | Decision independent of label; no stale EDT; no termination; zero 40P01 | Backend position not established by hook | Label-dependent authority |
| (o) | Unrelated backend with former label | Live unrelated backend | No termination; reclaim unaffected | — | Termination by label |
| (p) | Unavailable census | `pg_stat_activity` unreadable or rows hidden | No effect on reclaim authority | — | Census used as authority |
| (q) | Ambiguous exclusion | P1 inconclusive; another host; persistent 55P03; unproven topology | Refusal; visible pending state; no termination | — | Takeover under ambiguity |

### 5.8 §12.1: surviving-renewal regression, variants A–C

**Hypothesis.** Single-row outermost receiver renewal cannot close a lock cycle with a replacement owner's
UUID-ordered prefix, including when the renewal's backend survives its client's death (variant A); a busy holder makes
the reclaim refuse cleanly (variant B). Variant C only characterises the superseded multi-row form.

**Prerequisites.** P1 PASS on the cell (A-3 needs exit confirmation); server-side barrier functions and per-operation
acquisition instrumentation in the scratch database; hook placement verification; retries disabled;
`client_connection_check_interval = 0` (§4.4).

**Environment.** PostgreSQL 16 and 18; Linux and Windows clients (all four cells).

**Evidence rule.** The contract's §12.1 observation evidence rule applies without change: no assertion rests on tuple
entries in `pg_locks`; lock state comes from verified hook placement on the exact backend, recorded per-operation
statements and instrumentation, and known-statement outcomes; `pg_blocking_pids` and transaction-ID waits only
corroborate.

| Variant | Pass | Fail | Invalid | Safety implication of FAIL |
|---|---|---|---|---|
| **A** (A-1…A-10) | Both reclaims commit (A-4); R holds only B at barrier 2 (A-5); replacement holds A and is blocked on B (A-6); R never requests A (A-7, three sources); success run completes with zero 40P01 (A-8); timeout run gives 55P03 and no partial effect of the replacement (A-9); no stale EDT; R changes only B's `receiver_alive_at` (A-10) | Any 40P01; any stale EDT commit; R requests A; partial effect of the rolled-back replacement | Backend R ended before A-4; hook placement unverified; client exit not confirmed | The single-row renewal argument (§5.4.1) is refuted; returns to the owner (§8.5) |
| **B** (B-1…B-3) | 55P03; whole-attempt rollback; unchanged `effect_interval_id`; no event; no replacement effect; separately recorded fresh attempt commits after release | Reclaim commits under the holder; partial reclaim effect | E-1 not established, so NOWAIT not reached; holder placement unverified | Reclaim under a live holder |
| **C** (C-1…C-3) | Not applicable. Outcome is "reproduced" (with opposing waits or 40P01) or "not reproduced", stated explicitly | — | Acquisition order not established deterministically | None: characterisation only. **Never counts as validation.** |

Preserved regressions: the accepted slow-upload and failed-STAGED-CAS tests are run unchanged on the feasibility
branch with per-operation renewal. A regression there is a FAIL of §12.1.

### 5.9 Bounded feasibility checks (F-O, F-T, F-R)

These answer "can it be done" on representative paths. They do **not** verify the implementation; that remains
Gate C (§3.2). A PASS here is not evidence of complete coverage.

#### F-O: O-first transaction ordering

| | |
|---|---|
| **Hypothesis** | On representative paths, the token-owned O prefix can be the first lock of every EDT, in UUID order, followed by the retained F → Ct ordering, with late discovery causing rollback and rediscovery, without deadlock. |
| **Paths (bounded set)** | The Proof 2 prototype paths: INGEST promotion; quarantine move with settlement; U release; `cleanStaging`; per-operation renewal; one destruction-shaped multi-operation prefix (as in §12.1 A-6). |
| **Evidence** | Per-transaction acquisition timelines from the §12.1 instrumentation; captured statements; concurrent contention schedules (at least: two EDTs on overlapping operation sets in opposite discovery order; EDT versus renewal; EDT versus stale CAS); a forced late-discovery case. |
| **Pass** | On every path, the first acquired lock is the O prefix in UUID order; late discovery rolls back before any later business lock or mutation and rediscovers; zero 40P01 across the schedules. |
| **Fail** | A path that cannot take O first without breaking a retained Phase 2 window, authorization, provenance, restriction or atomicity rule; any 40P01 on a corrected path. |
| **Invalid** | Acquisition order not established by instrumentation (the §12.1 evidence rule applies). |
| **Safety implications** | A FAIL means the theorem-A premise ("O first") is not achievable on that path. It returns to the owner (§8.6); no lock is strengthened or reordered ad hoc. |

#### F-T: outermost-transaction ownership

| | |
|---|---|
| **Hypothesis** | T-1, T-4 and T-6 can be made to run only as outermost transactions under the ordinary Spring `TransactionTemplate`, and refuse before any lock or effect when called inside an existing transaction. |
| **Evidence** | The prototype enforcement mechanism (for example a guard checking for an active transaction, or a non-joining propagation), exercised on the prototype paths; negative cases invoking each of T-1, T-4 and T-6 inside an outer transaction and inside an outer EDT holding Ct; confirmation that no lock was taken and nothing written. |
| **Pass** | Each nested invocation refuses before any SQL that locks or writes; each outermost invocation commits or rolls back independently. |
| **Fail** | Any nested invocation that acquires a lock, writes, or joins the outer transaction. |
| **Invalid** | Nesting not actually established (for example a separate connection used by mistake). |
| **Safety implications** | Without outermost ownership, token commit or release could be rolled back or held by an outer transaction, breaking T-1 and T-4. A FAIL returns to the owner. |

#### F-R: per-operation renewal throughput

| | |
|---|---|
| **Hypothesis** | With one-operation-per-transaction receiver renewal, and no batching of operations into one lock-holding transaction, every receiving operation's required renewal completes within each renewal interval at the acceptance load. |
| **Parameters** | The accepted renewal interval is a quarter of `files.receivingTimeout`, clamped to 200 ms…5 s (`IngestionService.leaseIntervalMillis`); the configured timeout is PT1M…PT24H. **Acceptance-load run (Q-A, owner-approved 2026-11-19):** 100 concurrent RECEIVING uploads in one process; renewal interval **I = 5 s** (the minimum timeout, PT1M). **Diagnostic only:** runs at 10 and 1,000 uploads, and a 200 ms-interval stress run. |
| **Acceptance rule** **[GB-P1]** | **Interval boundaries.** The measurement window starts at t₀, once all 100 operations are RECEIVING and each has completed at least one renewal. It consists of at least 60 consecutive, contiguous, half-open intervals [t₀ + k·I, t₀ + (k+1)·I), k = 0, 1, …, each measured on the **PostgreSQL server clock**. The end of interval k is its **deadline**. **Required renewal.** For every operation and every interval k, one renewal of that operation must **complete** within interval k. **Completion** is established only by both of: **(1) Attribution** (in the renewing transaction): on the **same connection** and inside the **same outermost transaction** as the renewal's single-row UPDATE, before COMMIT, the renewal records `pg_current_xact_id()` together with the operation ID, the UPDATE's row count (exactly 1) and the exact `receiver_alive_at` value written (from `UPDATE … RETURNING`). That record identifies the renewal **R**. Its written `receiver_alive_at` must lie inside interval k, so R is a renewal of interval k. The renewal XID is **never** inferred from a later or separate transaction, including a later autocommit statement. **(2) Deadline-bound readback** (independent of the renewer): a statement on a **separate** connection, starting a fresh snapshot after R's COMMIT has returned, reads the operation row's `receiver_alive_at`, its `xmin`, and `clock_timestamp()` in the same statement. It identifies R **uniquely** only if `xmin` equals R's XID (compared as the 32-bit `xid`) **and** `receiver_alive_at` equals R's written value; a value match alone, or a match to an earlier value, is not identification. It is **deadline-bound** only if its in-statement `clock_timestamp()`, evaluated after its snapshot is taken, is **before the deadline** and the statement finished. A readback at or after the deadline cannot establish timely completion retroactively. **Corroboration only:** `pg_xact_commit_timestamp(R)` must be consistent (inside interval k); it never substitutes for (1) or (2). **Pending renewals.** A renewal that is scheduled, submitted, still executing, uncommitted, or committed but not yet shown by a deadline-bound readback at the deadline does **not** complete interval k, and cannot complete any earlier interval. The readback is a plain SELECT; it takes no row lock and does not delay the renewal. |
| **Evidence** | Per operation and interval: the attribution record (XID, operation ID, row count, written `receiver_alive_at`, connection `pid` and `backend_start`); every readback (connection, start time, in-statement `clock_timestamp()`, returned `xmin` and `receiver_alive_at`); `pg_xact_commit_timestamp` of each XID; the interval boundaries; the operations' state through the window. Diagnostic: per-cycle duration, per-statement latency distribution and percentiles, database connection usage. Local storage only (renewal touches no file). |
| **Pass** | On every cell, for **every** operation and **every** interval of the window, completion is established by (1) and (2), with consistent corroboration, so the acceptance-load run has **zero missed deadlines**. Nothing else yields PASS. |
| **Fail** | On any cell, a **demonstrated** missed deadline: for some operation and interval k, attribution is complete, and the record shows that no renewal of that operation written in interval k became visible before the deadline. Examples: the first uniquely identifying readback of the interval's renewal is at or after the deadline; its commit timestamp is at or after the deadline and no deadline-bound readback identified it; or no renewal of that operation was written in interval k. |
| **Invalid** | The run cannot support any verdict: `track_commit_timestamp` off; attribution records missing or unverifiable for renewals of the window (for example, an XID not captured inside the renewing transaction); interval boundaries not recorded on the server clock; an operation left RECEIVING for a reason other than renewal; fewer than 60 intervals; a commit timestamp inconsistent with a deadline-bound readback (a clock or evidence defect); or a readback harness defect. Never PASS. |
| **Inconclusive** | The run is valid, but for some operation and interval timely completion is neither established by (1) and (2) nor shown to be late (for example, the readback for that interval was not performed or returned nothing usable). Treated as **not proven** (§4.6). Never PASS. |
| **Diagnostic only** | Percentiles and latency distributions, and every 10-upload, 1,000-upload and 200 ms run. They are reported, and decide neither PASS nor FAIL. |
| **Safety implications** | Late renewal cannot cause an unsafe takeover (abandonment is logical only; physical cleanup is gated by the token, §5.8, T-7), but it can abandon live uploads. A FAIL is an availability finding returned to the owner; renewal is **not** re-batched (that would reopen D4-V1). |

---

## 6. Network storage

### 6.1 NFS information needed before P3, Proof 2 (b)/(g) or any NAS-labelled case

| Information | Why |
|---|---|
| NFS version (v3 or v4.x; minor version) and the deployment it represents | A result certifies only the tested configuration |
| Server product, OS and version | Late-write, lease and recovery semantics differ by server |
| Client mount options: `hard`/`soft`, `sync`/`async`, attribute and data caching, `timeo`/`retrans`, and for v4 the lease time | These decide whether late write-back or replay is possible |
| How a stall is induced and released (stop the service, block the port, pause the VM) and who controls it | Proof 2 (b) and (g) need a real stall |
| Access to server-side observation (shell, logs, snapshots) | P3 needs server-side evidence (§5.3 test point 4) |
| Whether the server may be paused and its test export destroyed | Safety of the test environment (§4.7) |

This is execution information, needed before NAS runs (AU-7). It does not block review of this plan. If no NFS
configuration is authorized, NAS cases are **NOT RUN**, P3 is unproven, and the conservative route applies at Gate C.

### 6.2 SMB and other network filesystems (protocol flexibility)

- Not tested in Gate B, and not excluded from the product.
- Each further configuration (protocol, version, server and client options) may later be verified by repeating P3 and
  Proof 2 (b) and (g) on it, with the same criteria (§5.3, §5.7) and the same review rule (§8.5). That is a separately
  authorized activity.
- Until a configuration is verified, it keeps the conservative route: no automatic conflicting takeover; administrator
  verification of storage or access resolution first (contract §5.5, §5.7). Ordinary operation on it is unaffected.
- No architecture change is needed to add a verified configuration; the contract already scopes P3 per topology.

---

## 7. Execution order and dependencies

1. **V-H, V-R, P1, P2, F-T** (independent, cheap, foundational).
2. **Proof 1**, after P2 and V-R (and V-H for binding cases).
3. **Proof 2** and **F-O**, after P1 (E-1 cases), V-R and F-T.
4. **§12.1** and **F-R**, after P1 on the cell.
5. **P3** and NFS cases, after the §6.1 information and authorization.

**Dependency stops.** A FAIL in a prerequisite stops every dependent case on that platform. The dependent cases are
reported NOT RUN with the reason. Independent items continue. No replacement mechanism is built to unblock them.

---

## 8. §10A review process

### 8.1 Plan review (before any execution)

- **One consolidated independent review** of this plan (§10A.4), by the independent reviewer (Codex, unless the owner
  names another), at a recorded baseline.
- **Scope:** completeness against the contract's proof obligations and D4-34; correctness of hypotheses and criteria;
  coverage; invalid-run rules; safety of the environment; the Gate B / Gate C split, including the bounded
  checks (§5.9) and the storage scope (§4.1, §6).
- **Exclusions:** the accepted contract's architecture (Gate A is closed); harness implementation details that do not
  change what a proof establishes (§10A.4).
- **Disposition** concerns readiness of the plan for owner approval only. It does not accept Gate B.

### 8.2 Finding classification (§10A.3)

Every finding, in plan review and in results review, states:
1. severity: BLOCKING, MAJOR or MINOR;
2. the affected gate (B, or a deferral to C);
3. whether it blocks that gate;
4. supporting evidence (contract section, plan section, run log);
5. the required correction, or the deferred obligation and its stage;
6. closure criteria.

Blocking rules as §10A.3. For plan review in particular:
- **Blocking:** a missing contract obligation; a pass criterion that could pass an unsafe effect; an invalid-run rule
  that could hide a failure; an environment that cannot establish a stated precondition.
- **Not blocking unless it prevents verification:** harness construction detail; editorial clarity.

Findings are never reclassified to reduce rounds.

### 8.3 Round budget and escalation (§10A.5)

| Point | Action |
|---|---|
| After the plan review | Evaluate every finding against §8.2 **before** assigning corrections. Consolidate blocking corrections into one revision. Record deferred items. |
| After the second correction round | Record a consolidation assessment and whether another round is justified. |
| Before a third correction round | Process-efficiency reassessment; owner notified why another cycle is needed. |
| Any later round | Only with explicit justification tied to an unresolved acceptance-critical issue. |

The §3 convergence triggers still apply. An unresolved safety-critical defect overrides the budget. **Only the owner
decides whether another review round is justified.**

### 8.4 Stopping rule (§10A.6)

Plan review concludes when every blocking finding is resolved, deferred items are recorded with their stage, owner
decisions are identified (§10), and the reviewer gives an evidence-supported disposition. A review is not extended
solely to perfect a later-gate detail.

### 8.5 Results review (after execution, separate)

- A **separate** consolidated independent review of the executed results, after all authorized runs, at a recorded
  feasibility-branch commit with the evidence logs.
- It checks each item's outcome against this plan's criteria, validity of every run, and that NOT RUN and
  INCONCLUSIVE items are reported as such.
- The reviewer recommends a disposition. **Only the owner accepts Gate B**, in whole or in part, by a recorded
  decision.

### 8.6 A failed proof does not trigger automatic redesign

- A FAIL is reported to the owner as evidence, with its safety implication and, where the contract requires it, a
  complexity comparison (§12: "A failed hypothesis returns to the owner as evidence with a complexity comparison. It
  is not hidden by added machinery.").
- No alternative mechanism, dependency, privilege or architectural revision is started without a new owner decision.
  In particular: P2 failure does not adopt JNA; V-R failure does not escalate privileges; V-H failure does not adopt
  an identity file.
- INCONCLUSIVE leaves the contract's fail-closed route in force.

---

## 9. Evidence record

Default (§11.2):
- results: `docs/PHASE-4-WP1-GATE-B-RESULTS.md`, one section per item and cell, with outcome and log references;
- raw logs: on the feasibility branch under a test-evidence directory, never in production paths;
- findings: in the results-review response, per CLAUDE.md (auditor finding text unaltered; builder issues in
  `docs/issues.md`).

---

## 10. Owner decisions and authorizations required before execution

Approval of the assignment that produced this plan authorizes **none** of the following.

| ID | Required decision or authorization |
|---|---|
| **AU-1** | Authorize the consolidated independent plan review (§8.1), name the reviewer, and approve the round budget and stopping rule (§8.3, §8.4) |
| **AU-2** | Scope split (§3): **given by the owner's clarification of 2026-11-19**; confirmed by approving this plan (AU-8) |
| **AU-3** | Authorize test-only prototype code on a separate feasibility branch (§4.2), including prototype edits to production classes there only, and any test-only library |
| **AU-4** | Authorize test hosts (Windows and Linux builds), scratch PostgreSQL 16 and 18 clusters, installation of the systemd and WinSW services with their service accounts on those hosts, and the destructive actions of §4.7 |
| **AU-5** | Authorize the OD-5 receipt stand-in and the OD-4 exclusion (§4.3), without deciding OD-4 or OD-5 |
| **AU-6** | Authorize use of `phase-3-accepted` and a named current Phase 4 commit as legacy artifacts in Proof 1 |
| **AU-7** | Supply and authorize the NFS configuration (§6.1), or defer it (P3 NOT RUN; conservative route) |
| **AU-8** | Approve the final plan after review (§8.4) |
| **AU-9** | Authorize execution, with its cells, after AU-1…AU-8 |
| **AU-10** | Approve the evidence-record locations (§9) and any commits of evidence |

OD-1, OD-2, OD-4 and OD-5 remain open; none is needed to execute Gate B under §4.3.

---

## 11. Planning questions (consolidated)

### 11.1 Acceptance-critical

| ID | Question | Resolution |
|---|---|---|
| **Q-A** | F-R pass threshold: what concurrent-upload load per process must per-operation renewal sustain? | **RESOLVED by owner approval, 2026-11-19:** 100 concurrent receiving uploads per process; 5-second renewal interval. Each required renewal must complete (committed transaction and observable change to that operation's `receiver_alive_at`) within its defined interval, shown by in-transaction XID attribution and a deadline-bound, uniquely identifying separate-connection readback; the commit timestamp corroborates only; scheduled, outstanding or uncommitted renewals do not count; **any demonstrated missed deadline fails** the acceptance-load run, and missing or unverifiable timely-completion evidence never passes (INVALID or INCONCLUSIVE). Percentiles and the 10- and 1,000-upload runs are diagnostic only. The single governing rule, including interval boundaries and pending renewals, is §5.9 F-R "Acceptance rule". |

No acceptance-critical planning question remains open.

### 11.2 Settled in this plan as nonblocking defaults (no further design round)

| Item | Default | Why it is nonblocking |
|---|---|---|
| Gate B scope | Owner clarification of 2026-11-19 (§3) | Given |
| NAS target | NFS first, from a Linux client; SMB and others later (§6.2) | Unverified configurations stay fail-closed |
| Windows file identity for the fence (P2 point 7) | A test point, not a pre-decision. If no dependency-free method works, P2 fails and returns to the owner (D4-34) | The outcome is decided by evidence |
| Windows MachineGuid read path (V-H point 2) | A built-in OS tool (for example `reg query`) is not a new dependency; its failure modes are tested and refuse | Failure returns to the owner (D4-34) |
| Administrative termination reach (V-R point 5) | Measured and reported. A reach wider than one installation returns to the owner | The outcome is decided by evidence |
| Remote PostgreSQL | Not in the base matrix; reported as not covered | Supported deployment unchanged; no Gate B claim made for it |
| Evidence locations | As §9 | Administrative |
| Reuse of prototype code at Gate C | Decided with Gate C authorization | Gate C is separately authorized and reviewed |

### 11.3 Execution inputs (needed before running, not before review)

The NFS configuration (§6.1, AU-7), test hosts and accounts (AU-4), and the legacy-artifact commits (AU-6).

---

## 12. Gate status (unchanged by this plan)

| Gate | Status |
|---|---|
| **A: architectural contract** | **ACCEPTED, 2026-11-18** (D4-33) |
| **B: feasibility** | **NOT ACCEPTED.** Proof execution **NOT AUTHORIZED**. This plan is a DRAFT — NOT APPROVED. |
| **C: implementation** | **NOT AUTHORIZED** |
| **WP2** | **HOLD** |

WP1-R1, F5-4 and GAS-P4-002…GAS-P4-005 remain OPEN. Nothing in this document has been executed.
