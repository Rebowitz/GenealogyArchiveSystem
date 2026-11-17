# Builder-identified issues

These are issues the builder found in its own work. Independent audit findings are kept in the
`PHASE-N-*CORRECTIONS.md` files. Where an issue here and an audit finding describe the same problem, each
cross-references the other.

## GAS-P4-PLAN-001 — MAJOR — Removing a Record Role that has Role-backed Mentions (Phase 4 planning)

| | |
|---|---|
| Found | 2026-11-15, while correcting review finding PR4-R3 (lock order) |
| Phase | 4 (planning; no code) |
| Status | **DECIDED: Option C approved by the owner, 2026-11-15.** Design recorded in planning documents (no code). Implementation pending Phase 4 authorization (WP4). |
| Related | PR4-R3 (`PHASE-4-PLAN-REVIEW-CORRECTIONS.md`); D4-12 B; D4-16 policy S; Phase 3 accepted Role removal |

**Cause.**
- Phase 3 removes a Record Role with a physical `DELETE` (`ArchivalRecordRepository`, accepted behaviour).
- Approved D4-12 B makes a Mention *Role-backed*: `mention.record_role_id` references the Role, and the Mention's
  wording is read from the Role's `party_as_written`. A collective may be Role-backed the same way (D4-14).
- With the foreign keys, removing a Role that has a Mention or collective fails with a foreign-key error.

**Consequences.**
1. **Behaviour change.** A Role that has a Mention can no longer be removed. Existing data is unaffected, because
   no Mentions exist before Phase 4.
2. **Disclosure.** Under policy S, an Editor who sees no Mention on a Role may be refused removal because of a
   hidden RESTRICTED Mention or collective. The refusal itself reveals that a hidden object exists. This is the
   collision class of C-1.
3. **Lost wording.** If removal were simply allowed (for example `ON DELETE SET NULL`), the Mention would lose its
   wording, which D4-12 B keeps only on the Role. That violates A-004.

**Options presented to the owner** (Option C was approved; A and B were not chosen):
- **A. Block removal** whenever any Mention or collective references the Role, with one generic message for
  everyone. Simple, but it leaves the disclosure in consequence 2.
- **B. Allow removal, preserving the wording.** In the same transaction, each Role-backed Mention and collective
  becomes a free one carrying the Role's `party_as_written`, with history. This changes D4-12 B's
  "never duplicated" rule for this one event, and the transaction touches hidden objects.
- **C. Replace physical Role removal with a recoverable WITHDRAWN state** once Phase 4 is installed. A withdrawn
  Role keeps its wording and identity, and is hidden from the Record's active Roles. No foreign-key failure and no
  disclosure. This changes accepted Phase 3 behaviour (removal becomes withdrawal) and needs a migration that adds
  a lifecycle column without changing existing rows.

**Resolution (Option C, approved 2026-11-15).** Design and tests are in clarifications §11 and ADR-0012 §5a; the
register records the decision.
- An additive `record_role.lifecycle` (ACTIVE or WITHDRAWN, default ACTIVE with no row rewrite), withdrawal
  attribution, and a `BEFORE DELETE` `reject_removal()` trigger.
- "Withdraw role" replaces "Remove role"; "Restore role" returns the same Role.
- Withdrawal never reads, locks or changes Mentions or collectives, so neither its outcome nor its timing can
  disclose a hidden object.
- A withdrawn Role's references stay, labelled and frozen for writes until restoration. Wording corrections of
  ACTIVE Roles change no Phase 4 row.
- **Lock order.** Role withdraw, restore and correct are L1-only lifecycle updates (W12). The implicit L3/L4
  foreign-key lock that physical deletion would have taken no longer exists (clarifications §5.5.3 and §11.5;
  LO-4, LO-5, RW-10).
- **Tests.** RW-1…RW-11.

**Residual note.** Roles removed physically *before* the Phase 4 upgrade cannot be recovered; their Phase 3
`ROLE_REMOVED` history stays as written.

## GAS-P4-001: MINOR. Envelope of FROM and TO dates (Phase 4 WP1)

| | |
|---|---|
| Found | 2026-11-16, implementing WP1 |
| Status | **FIXED.** Owner decision 2026-11-16; implemented in `3db9c4b`; specification text corrected in the WP1 documentation commit. |

**Cause.** In the accepted clarifications, §2.3 step 5 said "POINT, FROM and TO use p1's envelope", which is a
closed range. Verified worked example 18 stores `from 1840` as 1840-01-01 … open, an open end. No example covered
TO.

**Decision (owner).**
- FROM runs from p1.earliest to an open end.
- TO runs from an open start to p1.latest.
- The §2.3 text is corrected to match. Plan §4.1 is aligned.

**Implementation.**
- `DateEnvelopes`.
- Test `HistoricalDateTest.openFormsFollowOwnerDecisionGasP4_001`.
- Example 18 in the 37-row fixture is unchanged.

## GAS-P4-002: MINOR. Internal order of lock level L1 (Phase 4 WP1)

| | |
|---|---|
| Found | 2026-11-16, registering the Phase 3 row locks (LO-1 to LO-3) |
| Status | **OPEN, escalated.** **SUPERSEDED ASSERTION:** the deadlock-freedom premise stated below (L1 is safe because session-first locking serialises it) is **wrong across sessions and subsystems** and must not be relied on; see WP1-R1 (BLOCKING), Scenarios A–D and GAS-P4-003. The replacement protocol is proposed in [`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md), now revision 3.5 (D-LP-1…D-LP-9; modified Option C with P2-8g; global fail-closed activation, §5.6), and is not implemented. Not closed by specification. Response record: [`PHASE-4-WP1-AUDIT-CORRECTIONS.md`](PHASE-4-WP1-AUDIT-CORRECTIONS.md). |

> **Superseded assertion (kept for the record, do not rely on it):** the text below describes the original premise.

**Cause.**
- Clarifications §5.5.3 rule 1 says rows within one level are locked in ascending UUID order. It also defines L1
  as "the accepted Phase 3 chain, unchanged".
- Accepted Phase 3 grouping (`GroupingService.applyChunk`, GAS-P3-004) does not lock in UUID order:
  - it locks worksheet rows and Files in *plan* order, not UUID order;
  - in the new-Record branch, it re-locks the classification-source row after the Files.
- Phase 3's own safety comes from locking the capture session first. Every multi-row path holds the session lock
  before locking any rows.
  *(Corrected 2026-11-16: this holds within one session only. The audit's Scenarios A and B are cross-session
  cycles; see the proposal.)*

**Effect on Phase 4.** None on deadlock freedom.
- A Phase 4 writer takes at most one L1 lock, on the Record, and takes it first.
- Phase 3 transactions take no Phase 4 lock.
- So no wait cycle can cross between them.

**Implementation.**
- `LockSequence` treats L1 as one opaque level. It does not check Phase 3's internal order there; it does check that
  no L1 lock follows a lock at a higher level.
- Above L1, the full rules apply: level order, UUID order and no upgrade.
- Covered by `LockSequenceTest.lo2_level1KeepsTheAcceptedPhase3OrderUnchecked` and `LockSequenceIT`.

**Proposed wording, not applied** (it is a change to accepted architecture text, so it is for the reviewer and
owner): §5.5.3 rule 1 would add "Within L1 the accepted Phase 3 order applies, serialised by the capture-session
lock; UUID ordering applies from L2 upward."

## GAS-P4-003: MAJOR. Administrator check can deadlock with history writers (accepted user administration)

| | |
|---|---|
| Found | 2026-11-16, reconciling lock-protocol revision 3 with the second Stage A review |
| Phase | Defect in accepted code (user administration); found during Phase 4 WP1 |
| Status | **OPEN, needs owner decision D-LP-9.** Not fixed; no production code changed. **[3.2]** The third assessment recommends amended option (a), specified in proposal §4.10. NOWAIT is withdrawn as an equivalent: a two-transaction cycle (password reset of the role change's target) was reproduced locally (proposal §11.7). Closure requires implementation and service-level LP-14b on all four CI configurations. |
| Related | Second-review reconciliation SR2-B1 (`PHASE-4-WP1-AUDIT-CORRECTIONS.md`); proposal §3.2 rule TR, §5 L7, §11.5 |

**Cause.**
- `UserService.update` runs `AppUserRepository.lockEnabledAdministrators`:
  `SELECT user_id FROM gas.app_user WHERE role = 'ADMINISTRATOR' AND enabled ORDER BY user_id FOR UPDATE`.
- That single statement locks several rows and waits on a later row while holding earlier ones.
- Every history insert takes KEY SHARE on its actor's `app_user` row through a foreign key. That conflicts with FOR
  UPDATE.
- So the check (A) can wait on transaction T1, which holds KEY SHARE on administrator a2 and waits on a row that T2
  holds. T2 then writes history as administrator a1 and waits on A.

**Effect.**
- PostgreSQL detects the cycle and aborts one transaction with 40P01, so it fails visibly and nothing partial is
  committed.
- **[3.3, corrected]** It needs **two or three concurrent actors depending on the schedule.** The three-actor form
  goes through history writers. The two-actor form is a password reset (or other target update) of the role change's
  target, whose history insert waits on the administrator rows. That form also applies to the accepted ordinary
  FOR UPDATE check once it is granted; NOWAIT is not essential to it (proposal §11.7).

**Evidence.** Reproduced on the builder's local PostgreSQL 16 against the real `app_user`, `change_history` and
administrator-check SQL, with a stand-in row for the T1/T2 resource: 40P01 (proposal §11.5). Not yet in CI
(planned LP-14).

**Proposed fix (D-LP-9(a), not applied; proposal §4.10).** Key `pg_advisory_xact_lock(7340, 1)` (two-int4 space,
collision-free with existing keys) as the first statement of `UserService.update`, outermost transaction only; a
fresh plain read of the enabled administrators in a later statement; then the target update (NO KEY UPDATE,
compatible with KEY SHARE). `create` and CLI `create-admin` have a documented insert-only exception. **NOWAIT alone is
not a fix** (proposal §11.7). Keeping the current design is an explicit risk decision only.

## GAS-P4-004: MAJOR. Quarantine settlement can release a newer repair's claim after path reoccupation (accepted Phase 2 code)

| | |
|---|---|
| Found | 2026-11-16, analysing fourth-assessment finding F4-1 |
| Phase | Defect in accepted Phase 2 code; found during Phase 4 WP1 |
| Status | **OPEN; source-derived, not executed.** **[3.4] Severity raised from MINOR to MAJOR,** as independently recommended. Not fixed; no production code changed. Proposed fix: P2-8g (Wh1a, Wh1b, Wh2) plus fail-closed admission of pre-existing overlaps (proposal §5.4; **[3.5]** global fail-closed activation, §5.6, replacing 3.4's §5.5 isolation), part of decision D-LP-8. P2-8g is proposed, not implemented or accepted. |
| Related | Fourth assessment F4-1; `PHASE-4-WP1-AUDIT-CORRECTIONS.md` F4-1; proposal §5.4, LP-15(a) |

**Cause.**
- An attested quarantine move of an object at path p is recorded (intent), and the bytes are moved before the
  settlement transaction.
- Between the move and settlement, another repair of the same content can plan the same unrecorded generation
  (`planRepair` ignores other repairs' planned generations). It creates p (CREATE_NEW succeeds on the emptied path)
  and claims it.
- Settlement's `releaseTargetsAt(volume, p)` then releases **every** operation's claim at p, including the new
  repair's legitimate claim on its own copy.

**Effect (bounded, as demonstrated by source analysis) [3.4]:**
- An older settlement may release a newer operation's legitimate ownership claim.
- A subsequent cancellation of that newer operation may leave its copy unaccounted for.

No direct byte loss has been established, and the safety of the bytes is **not** asserted in general.

**Evidence.** Source analysis only (`QuarantineMoves.settle`, `OperationProcessor.planRepair` and `createOwned`).
LP-15(a) characterises it.

**Proposed fix (not applied).** P2-8g: monotonic generation planning (Wh1) and the OPEN-record check in
`createOwned` under Ct(X) Sh (Wh2).

## GAS-P4-005: MINOR. An older web process keeps writing against a newer schema (accepted Phase 1–2 code)

| | |
|---|---|
| Found | 2026-11-16, feasibility analysis for lock-protocol revision 3.5 (proposal §5.6.1) |
| Phase | Defect in accepted code (web process); found during Phase 4 WP1 |
| Status | **OPEN; source-derived, not executed against the application.** Not fixed; no production code changed. Proposed remedy: layer A of the activation write gate (proposal §5.6.2, part of decision D-LP-8 (v)). |
| Related | Sixth assessment §7 A (`PHASE-4-WP1-AUDIT-CORRECTIONS.md`, sixth-assessment section); proposal §5.6.1, LP-17 AA-3 and AA-13 |

**Cause.**
- `SchemaMigrator.status()` reports AHEAD when the database holds migrations unknown to the build ("do not run this
  older version against it").
- The worker refuses to start, and stops claiming work, when the status is not ready.
- The web process does not:
  - only `HomeController` checks `SystemStatus.ready()`, and no filter or interceptor gates requests;
  - `IngestionService.readiness` checks the ingestion volume and staging, not the schema;
  - the readiness health group reports OUT_OF_SERVICE without stopping handlers.

**Effect.** A web process of an older build left running, or started by mistake, after a newer build's migration
still accepts uploads, retries, cancellation requests, unresolved decisions, destruction requests and download
evidence writes against a schema it does not know. Nothing is known to be lost. The writes fail visibly where a
constraint rejects them, but the "fail visibly when integrity is uncertain" rule is not met by construction.

**Evidence.** Source analysis at `fe197ed`: `HomeController`, `IngestionService.readiness`, `WorkerProcess.start` and
heartbeat, `SchemaMigrator.status`, `WebSecurityConfiguration`, and the absence of any `HandlerInterceptor` or request
filter other than `TransportSecurityFilter`. The database primitive of the remedy was checked locally (proposal
§11.8 (c)).

**Proposed remedy (not applied).** The activation migration sets the role-in-database default
`default_transaction_read_only = on`. New builds override it at connection start, so any older build becomes
read-only at the database, for writes and locking reads alike. This is part of D-LP-8 (v), not a separate change.

**[3.6] Remedy narrowed** (seventh assessment §3, §13). The role default affects only **new** sessions whose client
does not override it. Existing older-build sessions keep their read-write setting and are ended by same-role
termination during the one-time transition (proposal §5.7.3). Compatible builds are fenced by the instance lease
and the per-connection identity check (§5.7.1–§5.7.5). The remedy does **not** make "all older sessions" read-only.
Status unchanged: OPEN.

**[3.7]** Excluding a running older build is now an **operating-system** matter: the continuous legacy change freeze
(proposal §5.8.2, owner decision D-LP-8 (xi)), with automated aids. Two of those aids apply directly here: an
older build reading the shared configuration exits at load because of the new required key, and a fresh older-build
session is read-only. Neither stops an older process already running filesystem work. Status unchanged: OPEN.

