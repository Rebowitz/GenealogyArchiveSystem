# Phase 4 planning — responses to the independent architectural review

| | |
|---|---|
| Review | Independent review: initial findings on the Phase 4 planning documents |
| Reviewed head | `99b9e3e` |
| Corrected head | `d06c14c` (documentation only) |
| Status of every finding | **builder-corrected, pending independent verification.** Only the reviewer can close a finding, and only the owner can authorize Phase 4 implementation. |

Each finding is quoted exactly as the reviewer gave it. Only the response, the changes and the commit are the
builder's.

---

## PR4-R1 — U-3: the 50,000-item ceiling needs stronger protection

**Finding (reviewer's text, unaltered):**

> 1. U-3 — The 50,000-item ceiling needs stronger protection.
>
> ADR-0012 currently defines people.restriction.itemCeiling as an installation setting with a default of 50,000 and validates that it is at least the interactive limit.
>
> That permits an administrator to configure it above 50,000.
>
> Our approved recommendation was that increasing the absolute ceiling requires a separately reviewed architectural change.
>
> Recommended correction: Make 50,000 the maximum permitted configured value under the current architecture, with validation enforcing that maximum. A future increase requires a separately approved revision.

**Response.** Agreed. The ceiling bounds the size of the single transaction, and so how long it holds locks. The
safety argument of the D4-27 protocol relies on that bound, so a configuration value must not be able to raise it.

**Changes (`d06c14c`):**
- **ADR-0012 §8.** The `itemCeiling` rule is now "≥ `interactiveItemLimit` and **≤ 50,000** (architectural
  maximum)", and `interactiveItemLimit` must be ≤ `itemCeiling`. New text says:
  - a value above 50,000 is a validation error, not a warning;
  - the ceiling may be lowered by configuration but never raised;
  - raising it requires a separately reviewed and approved ADR revision.
- **`PHASE-4-DECISIONS.md` (U-3 row), `PHASE-4-PLAN.md` (§11.2 U-3 row) and clarifications §5.5.8.** The same cap
  is recorded in each.
- **New test definition RO-18** (clarifications §10.4). `check-config` rejects a ceiling above 50,000, an
  interactive limit above the ceiling, and non-positive durations; lowered values are accepted and take effect.
  It is referenced in plan §9.1a, §9.3 and WP5, and in ADR-0012 §13.

**Status:** builder-corrected, pending independent verification.

---

## PR4-R2 — obsolete planning statement about ADR-0012

**Finding (reviewer's text, unaltered):**

> 2. The Phase 4 plan contains an obsolete planning statement.
>
> Section 3 still describes ADR-0012 as "to be written in WP0," even though ADR-0012 now exists.
>
> Recommended correction: Change that statement to identify ADR-0012 as prepared and awaiting independent acceptance.

**Response.** Agreed.

**Changes (`d06c14c`).** Plan §3 now reads "**ADR-0012 (prepared; awaiting independent acceptance).**" It links to
the ADR and states that it awaits the final independent architectural review. A search of the four documents
finds no other "to be written" statements about ADR-0012.

**Status:** builder-corrected, pending independent verification.

---

## Disposition received after `0b70fa9`

The reviewer recorded **PR4-R1: CLOSED** and **PR4-R2: CLOSED**, and opened PR4-R3.

---

## PR4-R3 — MAJOR — Global lock-order inconsistency

**Finding (reviewer's text, unaltered):**

> PR4-R3 — Global lock-order inconsistency
> MAJOR — correction required
> In architectural clarifications §5.5.3, the global lock order is:
>
> L0 Operation → L1 Record/Role → L2 Person → L3 Mention → L4 Collective → L5 Identity proposal → L6 Child rows
>
> But §5.5.5 instructs the restriction confirmation transaction to lock the operation, then the Person, and subsequently lock affected objects.
>
> The specification does not clearly establish how required L1 Record/Role locks are acquired before the Person lock. This creates a potential deadlock risk if another transaction follows the documented global order.
>
> Required correction: Specify one consistent lock-acquisition sequence for every relevant writer, including restriction confirmation, Record/Role modifications, candidate resolution, collective membership and background jobs. Update the concurrency tests to demonstrate that these paths obey the same order.
>
> This finding does not require changing an owner-approved functional decision.

**Response.** Agreed. The order listed levels but did not state the acquisition rules, or which levels each writer
takes. In particular it did not say whether the confirmation needs L1. The correction makes the order a single
total order with explicit rules, and specifies every writer's sequence. No owner-approved functional decision
changes.

**Changes (clarifications §5.5.3, §5.5.5 and §10.7; ADR-0012 §8 and §13; plan §9.1a, §11.2 and WP1/WP5):**
- **One total order over (level, UUID).**
  - L1 is defined as the *accepted Phase 3 chain, unchanged* (session → worksheet rows → Files → Record). Roles,
    Citations and links are covered by the Record lock.
  - Phase 4 writers take the Record `FOR SHARE`.
  - Places are added at L2 after Persons, and reference rows at L7 (implicit foreign-key locks only).
- **Five rules:**
  1. monotonic acquisition; skipping a level is allowed, reversing is not;
  2. final lock strength at first acquisition (no `SHARE` → `UPDATE` upgrade);
  3. parents are locked explicitly before any write whose foreign keys reference them;
  4. no L0–L6 lock after an explicit L7 lock;
  5. background-job claim and lease transactions are separate and hold no archival locks.

  These rules, rather than an informal list, are what make the wait-for graph acyclic.
- **Per-writer sequences W1–W14.** Mention writes, candidate resolution, collective membership and named-Mention
  links, identity proposals, relationships, Names, Person edits, the **restriction confirmation (interactive and
  job)**, single-object restriction, **Phase 3 Record and Role paths (W12)**, Place writes, and preview, cancel and
  expiry.
- **The confirmation takes no L1 lock, and this is now stated with its justification.**
  - Its closure deliberately includes objects on deleted and restricted Records, so it never depends on Record
    state.
  - It writes no Record or Role row and no foreign-key column referencing one.
  - Skipping a level is allowed by rule 1.
  - A writer holding a Record `SHARE` lock may wait for the confirmation, but the confirmation never waits for a
    Record, so no cycle can form.
- **Closure re-verification after locking** (§5.5.5 step 4a). A closure that grows between computing it and
  locking its items becomes STALE, instead of failing the commit-time trigger.
- **Tests LO-1…LO-7** (§10.7):
  - `LockSequenceTest`: recorded sequences for W1–W14; a runtime guard that rejects order reversal and lock
    upgrade; an `ArchitectureTest` addition requiring every Phase 3 and Phase 4 row lock to go through the helper.
  - `LockOrderIT`: pairwise races with deadlock retry disabled, covering the confirmation × every writer
    (including the **job path**), Phase 3 Record paths × Phase 4 writers, Place deletion, and identity proposals ×
    relationships in both argument orders.
  - Also in `LockOrderIT`: Phase 3 paths unaffected; closure growth; implicit foreign-key locks inspected through
    `pg_locks`.

**New issue found while correcting (builder's own; in [`issues.md`](issues.md), cross-referenced here, not copied):**
GAS-P4-PLAN-001. Phase 3 physically deletes Record Roles, and approved D4-12 B makes Mentions and collectives
reference Roles. Its lock effect is monotonic and documented (W12). Its *semantics* are an owner decision: it
changes accepted Role removal, and under policy S a refusal could disclose a hidden restricted Mention. Options A, B
and C are listed. It is open and blocks WP4.

**Status:** builder-corrected, pending independent verification. GAS-P4-PLAN-001 is open, awaiting the owner.

---

## Disposition received after `a6d1d9f`

The reviewer recorded **PR4-R3: PARTIAL**, pending verification of the revised lock-order and database
concurrency specifications. The owner approved **Option C** for builder issue GAS-P4-PLAN-001 (Record Role
withdrawal).

### Builder update to PR4-R3 following the GAS-P4-PLAN-001 decision

This is not a new finding; it records how the Option C decision changes the PR4-R3 correction.
- **W12 is revised** (clarifications §5.5.3). Role add, correct, **withdraw** and **restore** are L1-only
  lifecycle or field updates: Record `FOR UPDATE`, then the Role row, then history.
  - Roles are never deleted (a `BEFORE DELETE` trigger), so the implicit L3/L4 foreign-key lock described in
    `a6d1d9f` no longer exists.
  - Phase 3 Record and Role paths now take **no** Phase 4 lock, explicit or implicit. The "why no L1 lock"
    justification is simplified accordingly.
- **Role lifecycle stability for Phase 4 writers** (§11.5). W1, W2, W3 and W5 read the Role's lifecycle under the
  Record `FOR SHARE` lock. Lifecycle changes need the Record `FOR UPDATE`, so the lifecycle a writer reads is
  stable until it commits.
- **Tests revised.**
  - LO-4 adds the confirmation × W12 (withdraw, restore) pair, and W12 (withdraw, restore, correct) × W1–W5 and
    W11.
  - LO-5 now asserts through `pg_locks` that no W12 transaction holds or awaits a Phase 4 lock.
  - The new RW-10 races withdrawal against W1, W2, W3, W5 and W10, with deadlock retry disabled.

**Status:** builder-corrected, pending independent verification (reviewer disposition: PARTIAL).
