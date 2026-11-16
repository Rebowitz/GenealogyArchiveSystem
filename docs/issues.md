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
