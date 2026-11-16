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
| Status | **OPEN, escalated.** The independent WP1 audit showed that the premise below is wrong across sessions (finding WP1-R1, BLOCKING). The replacement protocol is proposed in [`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md), now revision 2 after the Stage A review (D-LP-1…D-LP-7; Scenarios A–D), and is not implemented. Response record: [`PHASE-4-WP1-AUDIT-CORRECTIONS.md`](PHASE-4-WP1-AUDIT-CORRECTIONS.md). |

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
