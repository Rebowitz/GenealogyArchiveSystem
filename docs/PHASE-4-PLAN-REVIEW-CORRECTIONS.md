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
