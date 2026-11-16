# Phase 4 WP1: responses to the independent audit

| | |
|---|---|
| Audit | `Phase-4-WP1-Independent-Audit.md`, November 16, 2026 |
| Audited head | `5600d782b5556179eac434fb785d9b8db059777c` |
| Disposition received | CORRECTIONS REQUIRED. WP2 on HOLD. |
| Direction | Claude Code Direction Sheet: Phase 4 WP1 Audit Corrections |
| Correction head | `c9dcdf9` (WP1-R2 and WP1-R3 code and tests); documentation in the following commit |
| Status of every item | **builder-corrected or builder-proposed, pending independent verification.** Only the reviewer can close a finding. Only the owner can authorize the WP1-R1 protocol change, accept WP1 or release WP2. |

The audit report itself was not available to the builder. Each finding below is therefore quoted from the owner's
direction sheet, unaltered. The audit's own text remains the authority.

---

## WP1-R1 and GAS-P4-002: L1 locking protocol (BLOCKING; proposal only)

**Finding (direction sheet, unaltered):**

> The independent audit identified two cross-session deadlock schedules that are not prevented by capture-session-first locking.
>
> **Scenario A: opposing explicit row locks**
>
> Two transactions lock different capture sessions and then acquire overlapping worksheet rows in opposing order. The existing membership check occurs after acquiring the row lock.
>
> **Scenario B: opposing implicit uniqueness waits**
>
> Two transactions add the same two previously unassigned Files to different sessions in opposite order. The global File-ID uniqueness constraint creates waits on uncommitted inserts.

**Response.** Agreed. The premise of GAS-P4-002, that Phase 3 L1 safety comes from locking the capture session first,
holds only within one session. The builder's proposal is
[`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md`](PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md). It covers:
- an inventory of every L1 writer, including the implicit and uniqueness waits;
- the L1a…L1e sub-order, with membership predicates in the locking SQL and ordered inserts;
- presentation order kept separate from lock order;
- a wait-for argument;
- test designs LP-1…LP-8;
- proposed wording for clarifications §5.5.3 and for GAS-P4-002.

It also records a third existing cycle found during the inventory (Scenario C: grouping against the Phase 2
restriction cascade). It asks for owner decisions D-LP-1…D-LP-4.

**Not implemented**, as directed. `LockSequence` keeps L1 as one level. Its internal order remains unchecked until
D-LP-4 is decided. No narrowing or broadening of that exception has been made.

**Status:** proposal submitted; awaiting owner review and independent Codex assessment.

### Stage A review of the proposal (revision 1 → revision 2)

| | |
|---|---|
| Review | `Phase-4-WP1-Stage-A-Lock-Protocol-Review.md` (not available to the builder; findings taken from the owner's revision direction, unaltered in that document) |
| Reviewed head | `9c36d5e` |
| Disposition received | PROPOSAL REQUIRES REVISION. WP1 not accepted. WP2 on HOLD. |
| Direction | Phase 4 WP1 — Stage A Locking-Protocol Revision Direction |

**Response.** The proposal is revised to revision 2 in the same file. Its §0 holds the SA-R1…SA-R6 response matrix.
Revision 1's "sink" lemma, its implicit-KEY-SHARE-after-Record exception and its global deadlock-freedom conclusion
are withdrawn. Revision 2:
- reorders L1 so Files (and then content) precede worksheet keys and the Record;
- specifies `linkFile` File-FOR-SHARE-before-Record (D-LP-5);
- specifies the stabilised derivative closure with complete SQL and bounded restart (D-LP-3);
- inventories the Phase 2 preservation writers and resolves the content/parent cycle (Scenario D, D-LP-6a/6b);
- restricts grouping's classification-source locks to the new-Record path (D-LP-7);
- redesigns the `LockSequence` contract (D-LP-4);
- replaces the proof with a wait-for argument whose open dependencies are named as decisions and proof obligations
  (PO-1, PO-2; conditional D-LP-8);
- rewrites LP-1…LP-8 and adds LP-9…LP-12.

**LP-6 and SA-R1 evidence (bounded, non-production).** Harness
`src/test/java/org/genealogyarchive/data/Lp6LockBehaviourIT.java` (commit `dc274cd`), plain SQL sessions against
the existing schema; no production code changed. Results are in proposal §11. LP-6 shows that a compatible KEY
SHARE (explicit or foreign-key) is not queued behind a waiting FOR UPDATE. This is **not** used to dismiss SA-R1:
the same harness reproduces the SA-R1 granted-lock cycle G → L → C → G, which PostgreSQL aborts with 40P01.

**Not implemented**, as directed. No production locking code has changed.

**Status:** revision 2 submitted; awaiting independent review and owner decisions D-LP-1…D-LP-7.

### Second Stage A review (revision 2 → revision 3)

| | |
|---|---|
| Review | `Phase-4-WP1-Stage-A-Second-Review.md` (**not available to the builder**) |
| Reviewed head | `d2c96f2` |
| Disposition received | PROPOSAL REQUIRES REVISION |
| Direction | Phase 4 WP1 — Locking-Protocol Revision 3 Direction (received **truncated in its §8**) |

**Response.** Revision 3 of the proposal, in the same file. Its §0 maps direction items §1–§8. In summary:
- **Withdrawn:** I-ADV, PO-1, the claim that operation-owned tables are never locked explicitly, the batch-preflight
  guard contract and the catalog paragraph of revision 2's lemma L2. The counter-evidence is in proposal §2.3.
- **Added:**
  - a writer-by-writer protection matrix for every preservation writer and cross-row trigger (§2.4);
  - a unique and logical conflict-key ledger (§2.5);
  - D-LP-8 resolved as **Option C**: content-row coordination (CR-1), operation-row classification (CR-2),
    content-less rows under their operation (CR-3) and location-key invariants (CR-4);
  - Phase 2 changes P2-8a…f, each "lock the content row FOR UPDATE first";
  - a terminal-reference rule for catalog, vocabulary and user references (TR);
  - the per-statement guard contract;
  - the closure implementation boundaries;
  - the worksheet entry-point specification;
  - amended LP-1…LP-12.
- **Retained:** the SA-R1, SA-R2 and SA-R4 directions.

**Non-production verification.** On the builder's local PostgreSQL 16, a throwaway table confirms that an UPDATE
whose predicate does not match a row's committed version does not wait on that row's lock, and one whose predicate
matches it does (proposal §11.3). Schema facts were read from a scratch database migrated with V0001…V0025.

**Limits of this response.**
- The second review's counterexamples were not available, so they could not be checked row by row against the new
  matrix (R3-OPEN-1).
- The direction text after "Delete the universal requirement that every test must reproduce a baseline dead…" was
  not received (R3-OPEN-2).

Both are recorded in proposal §12.

**Not implemented**, as directed. No production locking code has changed.

**Status:** revision 3 submitted; awaiting the full direction and the second review for reconciliation, independent
review, and owner decisions D-LP-1…D-LP-8.

---

## WP1-R2: Historical Date database constraints (BLOCKING; corrected)

**Finding (direction sheet, unaltered):**

> Correct the nullable dual-year CHECK defect in V0024's deployed schema.
>
> Required behavior:
>
> - A non-NULL `p1_dual_year` requires a non-NULL `p1_year`.
> - A non-NULL `p2_dual_year` requires a non-NULL `p2_year`.
> - UNKNOWN and NOT_INTERPRETED cannot contain forbidden point fields.
> - A form without p2 cannot retain ghost p2 values.
> - Invalid raw database rows must not be concealed by repository mapping or integrity scanning.

**Cause.**
- **The CHECK.** `p1_dual_year IS NULL OR p1_dual_year = p1_year + 1` is NULL, and so passes, when `p1_year` is NULL.
  The same applies to p2. An orphan dual year could therefore sit on an UNKNOWN or NOT_INTERPRETED date, and a ghost
  `p2_dual_year` on a form without a second point. Every other point field was already bound to its year by the
  nesting and part constraints.
- **The mapping.** The repository built a point only when its year was present, which hid such rows.
- **The scan.** It read only interpreted rows.

**Review of adjacent constraints.** Every other V0024 constraint was checked for three-valued-logic gaps:
- nesting, form/points, uninterpreted, confirmation and envelope;
- the column range checks, where a NULL pass is intended because the column is optional;
- the vocabulary checks.

The only gap is the dual-year comparison.

**Migration strategy.**
- **Where V0024 has been applied.** Only in throwaway databases: the CI test databases and the builder's local
  scratch databases, which are now dropped. No Archive Instance runs Phase 4, and `phase-4` is unmerged.
- **Forward-only correction.** V0024 is left byte-identical, so its Flyway checksum stays valid. The correction is a
  forward-only **V0025**:
  1. A pre-check counts rows that break the corrected rules. If any exist, it stops the upgrade with a message
     naming the count and pointing to the integrity scan. Nothing is repaired or deleted, and the transaction rolls
     back, so the database stays at V0024.
  2. It replaces `historical_date_p1_parts` and `historical_date_p2_parts` with explicitly NULL-safe versions.
  3. It adds `historical_date_points_by_form`: a form without a point holds none of that point's fields.
- **Renumbering.** Planned migration numbers shift by one (people, places and resolution become V0026–V0028). This is
  recorded in plan §8 and ADR-0012 §10; nothing else changes.

**Code changes.**
- `HistoricalDateRepository.point` returns a point whenever any of its stored fields is present. An orphan or ghost
  value therefore reaches validation instead of being dropped.
- The new `HistoricalDateRepository.all()` feeds the integrity scan every row, uninterpreted ones included.

The numerical algorithms and validation rules are unchanged. The 37 examples and the parser fixture are untouched.

**Regression tests.**

| Test | Covers |
|---|---|
| `Phase4SchemaIT.wp1r2_orphanDualYearsAreRejectedOnInsertAndUpdate` | p1 and p2 orphan dual years, on INSERT and UPDATE |
| `Phase4SchemaIT.wp1r2_unknownAndUninterpretedCarryNoPointFields` | UNKNOWN and NOT_INTERPRETED with dual year or uncertain flag, p1 and p2, INSERT and UPDATE |
| `Phase4SchemaIT.wp1r2_aFormWithoutASecondPointKeepsNoGhostValues` | POINT with a ghost p2 dual year, uncertain flag, modifier or month |
| `HistoricalDateIT.wp1r2_aValidDualYearPersistsAndReadsBack` | valid dual-year persistence and retrieval |
| `HistoricalDateIT.wp1r2_theScanReportsMalformedLegacyRowsFromV0024` | malformed V0024 rows (orphan dual year, ghost p2, UNKNOWN with a dual year) read faithfully and reported by the scan |
| `Phase4SchemaIT.wp1r2_upgradeFromV0024KeepsValidDatesUnchanged` | V0024 → V0025 with valid data byte-identical |
| `Phase4SchemaIT.wp1r2_upgradeStopsVisiblyOnMalformedRowsAndChangesNothing` | the upgrade stops, data unchanged, schema stays at V0024 |

The SQL of V0025 and these predicates was also run by hand against a local PostgreSQL 16 before CI. The CI matrix
(PostgreSQL 16 and 18, Windows and Linux) is the authoritative evidence.

**Status:** builder-corrected, pending independent verification.

---

## WP1-R3: lock-guard correctness (MAJOR; corrected)

**Finding (direction sheet, unaltered):**

> Correct `LockSequence` so that:
>
> 1. SHARE-to-UPDATE upgrades are rejected at L1 as well as higher levels.
> 2. A previously seen resource cannot bypass cross-level safety checks.
> 3. Final-strength locking is enforced consistently.
> 4. Harmless same-strength or weaker re-locks have explicitly defined semantics.
> 5. A genuinely new L1 resource after a higher-level acquisition remains prohibited.
> 6. Existing unsigned UUID ordering above L1 remains enforced.
> 7. The absence of L7 coverage is documented, with enforcement added before any writer requires L7.

**Cause.** The guard returned early for any row it had seen before, and it exempted L1 from the upgrade check. A
re-lock therefore skipped every other rule.

**Correction (`LockSequence.Tracker.check`).** A re-lock of a held row is now handled as follows:
- it must be announced at the **same level** as before;
- it may **never be stronger** than the strongest mode already held, at any level including L1;
- a same-strength or weaker re-lock never waits, because the row is already held. It is recorded and exempt only
  from the UUID check within its level;
- it is **rejected after a higher level**, like a new lock, because it would reveal a mis-ordered path.

Unchanged:
- a new L1 resource after a higher level is still rejected;
- the unsigned UUID order above L1 is still enforced;
- the L1 internal-order exemption is unchanged, not broadened (see WP1-R1).

The class documentation now states that L7 has no level and that one must be added before any writer locks an L7 row
explicitly.

**Regression tests.**

| Test | Covers |
|---|---|
| `LockSequenceTest.wp1r3_aLevel1ShareToUpdateUpgradeIsRejected` | L1 SHARE → UPDATE, same row |
| `LockSequenceTest.wp1r3_aLevel1UpgradeAfterAHigherLevelIsRejected` | L1 SHARE → L2 UPDATE → L1 UPDATE, same row |
| `LockSequenceTest.wp1r3_aPreviouslySeenRowCannotBypassTheLevelOrder` | same-strength L1 re-lock after L2 |
| `LockSequenceTest.wp1r3_aNewLevel1ResourceAfterLevel2IsRejected` | new L1 resource after L2 |
| `LockSequenceTest.wp1r3_harmlessReLocksAreAllowedAndRecorded` | allowed harmless re-locks, including a held row below the level's highest UUID |
| `LockSequenceTest.wp1r3_aRowIsAlwaysAnnouncedAtOneLevel` | one level per row |
| `LockSequenceIT.wp1r3_concurrentLevel1ShareHoldersCannotUpgrade` | two concurrent L1 SHARE holders upgrading: both rejected at once, well under `lock_timeout`, never a deadlock |
| `LockSequenceIT.wp1r3_theGuardRejectsBeforeExecutingBlockingSql` | repository-backed: the upgrade is rejected while another transaction holds the row, before any blocking SQL runs |

The guard stays on for the whole unit and integration suite (`-Dgas.lockOrderGuard=true`).

**Status:** builder-corrected, pending independent verification.

---

## HD-7 and prohibited-inference item 18: staged verification (Workstream D)

**Requirement, unchanged.**
- **HD-7.** The same expression and interpretation stored on Records whose Sources, jurisdiction text, linked
  Places or periods differ produce byte-identical `year_start`, `calendar` and envelope. A parser proposal never
  sets `year_start`.
- **Item 18.** No calendar or year-start convention is set from period, jurisdiction, Record, Source or Place.

**Traceability. HD-7 and item 18 are not complete.**

| Stage | What is verified | Tests |
|---|---|---|
| WP1 (done) | The model and parser cannot infer a convention. The parser always proposes calendar and year start as UNSPECIFIED, and the envelope is a pure function of the interpretation alone. | `HistoricalDateTest.hd7_theParserNeverProposesACalendarOrYearStart`, `HistoricalDateTest.hd7_theEnvelopeDependsOnlyOnTheInterpretation` |
| WP2 and later consumers | Persisted dates written by services: authenticated attribution, ownership, validated persistence, version checks and history. Each consumer stores exactly the confirmed interpretation and nothing derived from its owner. | Per consumer, in its own suites |
| WP3 and WP4 | Place and Record integration as those dependencies arrive. The same interpretation on Records with different Sources, `jurisdiction_text`, linked Places and periods is byte-identical. | `HistoricalDateIT.hd7_*` (to be added in WP4) |
| Final integration gate (WP6) | The complete HD-7 and item 18, mandatory for Phase 4 acceptance | The full suites on all four CI jobs |

**Conflict with the accepted plan (owner-approved staging).** Plan §10's WP1 gate reads:

> `HistoricalDateTest` HD-1…HD-7 green; LO-1…LO-3 for Phase 3 paths; prohibited-inference items 10, 11, 18

Approved wording:

> `HistoricalDateTest` HD-1…HD-6 and the model-and-parser part of HD-7 green; LO-1…LO-3 for Phase 3 paths; prohibited-inference items 10 and 11, and the model-and-parser part of item 18. The persisted parts of HD-7 and item 18 are verified as their consumers arrive (WP2–WP4) and are **mandatory at the final integration gate (WP6)**.

**Status:** **approved by the owner.** The staged gate wording above was previously approved by the owner and is
not awaiting another decision (recorded under the Stage A revision direction, §11). Plan §10's WP1 gate text is
to be updated to this wording in a documentation commit when WP1 acceptance is recorded.

---

## Validation evidence

- **Local.** The V0024 and V0025 SQL and the constraint behaviour were exercised by hand on PostgreSQL 16. The
  37-example and 39-case date fixtures were run with plain `javac`. Maven Central is unreachable from the builder's
  environment, so no Maven test suite was run locally.
- **CI.** The CI identifiers and per-job counts are in the checkpoint report for `c9dcdf9` and its documentation
  follow-up.
