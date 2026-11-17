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
| Direction | Phase 4 WP1 — Locking-Protocol Revision 3 Direction (first received truncated in §8; complete text received afterwards) |

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
- The direction was first received truncated in §8 (R3-OPEN-2). The complete text arrived later and is now
  addressed: test classes, acceptance rules, the SA-R1…SA-R6 matrix, counterexample schedules and evidence classes.

Both are recorded in proposal §12.

**Not implemented**, as directed. No production locking code has changed.


### Reconciliation of revision 3 with the second Stage A review (revision 3.1)

| | |
|---|---|
| Review | `Phase-4-WP1-Stage-A-Second-Review.md`, 16 November 2026, review head `d2c96f2` (received after revision 3 was written) |
| Reconciled against | revision 3 at `569753f` |
| Result | revision 3.1 of the proposal (same file) |

The review's findings are quoted or cited by section; their text is not altered. The reviewer's own words remain the
authority. IDs (SR2-n) are the builder's, for cross-reference only.

**Status columns:**
- **Rev 3:** how revision 3 at `569753f` stood against the finding:
  - **FULL:** fully addressed;
  - **PARTIAL:** partly addressed;
  - **OPEN:** not addressed.
- **After 3.1:**
  - **ADDRESSED (design):** the proposal now answers it, as a design or specification; nothing is implemented;
  - **OPEN:** still unresolved; the reason is given;
  - **DECISION:** needs an owner decision.

#### §2 Disposition of SA-R1…SA-R6

| ID | Finding (review) | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-1 | SA-R1 closed as a direction; overall freedom depends on SA-R3, PO-1 and PO-2 | FULL | none | ADDRESSED (design); the global theorem stays conditional (proposal §5) |
| SR2-2 | SA-R2 closed with boundary clarifications (§5) | PARTIAL | §4.6 implementation boundaries, overlapping roots, publisher rollback (see SR2-66…73; **[3.2] pointer corrected** from "SR2-60…66") | ADDRESSED (design) |
| SR2-3 | SA-R3 open: I-ADV false; shared generation and path writes; trigger writes | PARTIAL | matrix rows P14b, P17, D5b, U2 corrected; ledger K8, K8b, K19, K20; P2-8c extended | ADDRESSED (design), subject to PO-A…PO-D and D-LP-8 |
| SR2-4 | SA-R4 closed as a direction; implementation and Phase 3 assertions required | FULL | LP-4d added | ADDRESSED (design) |
| SR2-5 | SA-R5 partial: parent re-lock conflict; pending batches; public keys; explicit locks on supposedly excluded tables | PARTIAL | §6: `privateNew` limited to ledger-private tables; ownership context derived from held locks; equal-replacement plan; table and level binding | ADDRESSED (design) |
| SR2-6 | SA-R6 partial: LP-1, LP-10 symmetric trap, universal baseline rule, rollback and history isolation | PARTIAL | §10 amendments listed under SR2-80…92 | ADDRESSED (design) |

#### §3 Writer-by-writer assessment

| ID | Writer | Finding (review §3) | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|---|
| SR2-10 | G | Run and group FKs and uniqueness must appear in the inventory | PARTIAL (K14, K18 only) | ledger K20 | ADDRESSED |
| SR2-11 | A | INSERTED K keeps a public uniqueness key in the guard and theorem | FULL (§6 `insertKey`, K1) | K1 marked "public resource, not private-row exemption" | ADDRESSED |
| SR2-12 | M | Preserve per-edit semantics, duplicates, version and no-op precedence | FULL (§4.4) | none | ADDRESSED |
| SR2-13 | X | DELETE holds the K uniqueness resource; model it against a claim of the same File | OPEN | K1 contender X; LP-2c | ADDRESSED |
| SR2-14 | O | Catalog FKs are forward waits, not "already held"; give the terminal argument | FULL (TR, L7) | TR and L7 revised (see SR2-30) | ADDRESSED with D-LP-9 |
| SR2-15 | SC | Same as O | FULL | as SR2-14 | ADDRESSED with D-LP-9 |
| SR2-16 | L | Correct direction; validation precedence | FULL | none | ADDRESSED |
| SR2-17 | RL | Existing R protection appropriate | FULL | none | ADDRESSED |
| SR2-18 | RR | Catalog and L7 arguments; future Role withdrawal at its work-package boundary | PARTIAL (L7 later found flawed) | L7 revised; W12 noted in §9 | ADDRESSED with D-LP-9 |
| SR2-19 | RC | State the fresh-ID and sequence conditions and the S → catalog FK path | PARTIAL | ledger K16, K17 (fresh ID, `nextval`); RC fk waits are terminal (TR) or a first wait on S; the RC example in §11.5 shows why L7 needed revision | ADDRESSED with D-LP-9 |
| SR2-20 | CI | Terminal catalog waits; unattached creation is reference-only | PARTIAL | covered by TR revision | ADDRESSED with D-LP-9 |
| SR2-21 | CA | Source U then Repository KS violates the literal (level, UUID) order | FULL (TR rule, no typed order) | TR revised; L7 chain W → W' ends at terminal rows | ADDRESSED with D-LP-9 |
| SR2-22 | FC | Sound; no Ct needed | FULL | none | ADDRESSED |
| SR2-23 | FL | Document reads versus blocking locks | FULL (§2.2) | none | ADDRESSED |
| SR2-24 | FD | Repair-operation writes need a valid PO-1 argument; same-id replacement needs one U entry, then the accepted message | PARTIAL (rev 3 §6 said equality is refused before locking, contradicting accepted precedence) | §6 corrected; repair O under xadv (CR-2 owner) | ADDRESSED |
| SR2-25 | OS | Remove the redundant parent re-lock after Ct; use an assertion | FULL (`assertHeld`) | none | ADDRESSED |
| SR2-26 | ON | Parent follows shared operation and ledger writes; include the deferred FK to its own new File | FULL (P5, L2 (e), CR-2, K8) | K8b terminal-holder argument | ADDRESSED, subject to PO-C |
| SR2-27 | OR | Cannot be excluded by its own advisory; the old generation and path belong to existing content; Ct U protects only cooperating users | PARTIAL (Option C makes all content-scoped writers Ct users, but missed DestructionProcessor `newRecord`) | P2-8c extended (D5b) | ADDRESSED (design), DECISION D-LP-8 |
| SR2-28 | OC | Filesystem exclusivity is not the SQL-key proof | PARTIAL (PO-C rested on CREATE_NEW) | K8b: claim is the last blocking statement (terminal holder); Ct serialisation for repairs | ADDRESSED, subject to PO-C |
| SR2-29 | IR | Batch/item is a shared logical key; do not claim no wait; a first-public-wait may cover it once the FK order is stated | FULL (K3 first-wait) | P13/K3 unchanged; the index check precedes the FK triggers in one statement (the AFTER triggers run after index insertion), so the batch/item wait is first; later fk waits hold only the operation's own key, whose waiters are other receivers with nothing held | ADDRESSED |
| SR2-30 | Repair receive | Not represented by IR's row; no derivation parent; fk to a finding | OPEN | P17 rewritten | ADDRESSED |
| SR2-31 | IS | `record` before `setState`; no advisory; triggers defeat I-ADV | FULL (I1, I2, P2-8e) | none | ADDRESSED (design), DECISION P2-8e |
| SR2-32 | DP | Dynamic lock set, completed-row writers, Ct and trigger alternatives need a real proof | PARTIAL | D5b; P2-8c extended; LP-10k | ADDRESSED (design), DECISION P2-8c |
| SR2-33 | UQ | Blanket no-contention false | FULL (§2.3, CR-2) | U2 corrected | ADDRESSED |
| SR2-34 | RecoveryService | Single SQL is not a single resource (triggers); final cleanup outside the RECEIVING exception | PARTIAL (P15, P16; cleanup missing) | P14b | ADDRESSED |
| SR2-35 | AI | Reasonable with the initialisation precondition | FULL | none | ADDRESSED |
| SR2-36 | Phase 4 | Plausible; future writers to be inventoried | FULL | LP-7 marked synthetic | ADDRESSED |
| SR2-37 | Advisory try | Narrow statement valid; the 2 s loop stays outside row locking; neither proves exclusive ownership | FULL | none | ADDRESSED |

#### §4.1 PO-1 counterexamples and requirements

| ID | Finding | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-40 | (1) retry and cancel without advisory; `note` FOR UPDATE | FULL (P9, P10 single-resource) | ownership context now derived from held locks | ADDRESSED, subject to PO-A (LP-10b, LP-10g) |
| SR2-41 | (2) `cleanStaging` on final operations without advisory | **OPEN** | P14b | ADDRESSED, subject to PO-A (LP-10i) |
| SR2-42 | (3) `abandon` and `cleanup` without advisory; timer and stale paths need state-specific treatment | PARTIAL | P14 state-specific; P14b | ADDRESSED |
| SR2-43 | (4) V0015 trigger mutates ownership rows from Recovery and Integrity under NKU, not explicit Ct U | PARTIAL (P16 relied on NKU, CR-1 said FOR UPDATE) | CR-1 clarified (a first-lock NKU of the content qualifies); Integrity moves to Ct U (P2-8e) | ADDRESSED, subject to PO-B (LP-10e) |
| SR2-44 | (5) `releaseTargetsAt` by volume and path | FULL (P2-8a, PO-B) | none | ADDRESSED, subject to PO-B |
| SR2-45 | (6) `quarantineSuperseded` and another entry; different operation ids do not exclude | FULL (P2-8b, P2-8a) | none | ADDRESSED (design), DECISION |
| SR2-46 | (7) `attest` reaches `attestUnderLock` without the advisory | **OPEN** (U2 claimed advisory) | U2 corrected; P2-8d applies to both branches | ADDRESSED |
| SR2-47 | Concrete wait: worker holds O, retry waits | FULL (accepted as CR-2 single-resource) | none | ADDRESSED, subject to PO-A |
| SR2-48 | "Advisory or Ct U" is not a common exclusion | FULL (Option C: Ct is the common coordinator for content-scoped rows) | none | ADDRESSED (design) |
| SR2-49 | The static "no explicit lock" condition already fails | FULL (withdrawn) | LP-10b rewritten to registered ownership | ADDRESSED |
| SR2-50 | Mandatory: truthful matrix; common mechanism or explicit order; D-LP-8 active; no late advisory | FULL | none added; D-LP-9 adds a **first** acquisition, not a late one | ADDRESSED (design), DECISION D-LP-8 |
| SR2-51 | Tests: retry versus worker; final cleanup versus worker; missing trigger versus ownership; concurrent repairs; pending-generation reconciliation; destruction versus these | PARTIAL (LP-10d, LP-10e, LP-10g) | LP-10h, LP-10i, LP-10j, LP-10k | ADDRESSED (specification) |

#### §4.2 PO-2 key table and requirements

| ID | Key | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-52 | Fresh UUID PKs: state the premise; do not extend it to secondary indexes | FULL (K17) | `privateNew` limited to ledger-private tables | ADDRESSED |
| SR2-53 | Friendly numbers | FULL (K16) | none | ADDRESSED |
| SR2-54 | `created_by_operation`, `ingest_operation_id` | FULL (K5, K6) | none | ADDRESSED |
| SR2-55 | Batch/item shared; the no-wait claim is false | FULL (K3 first-wait) | FK order stated (SR2-29) | ADDRESSED |
| SR2-56 | Event sequence; include row locking | FULL (K4) | none | ADDRESSED |
| SR2-57 | Generation PK and one-CURRENT index; include all generation-changing paths | PARTIAL (K7 only) | K19 | ADDRESSED |
| SR2-58 | Generation location; two repairs plan the same generation (`planRepair`) | **OPEN** (claimed private by construction) | K8 split; LP-10h | ADDRESSED, subject to PO-C |
| SR2-59 | `physical_location_owner` is a shared key (V0017) | PARTIAL | K8b terminal-holder argument | ADDRESSED, subject to PO-C |
| SR2-60 | Unresolved OPEN location shared; repair, reconciliation, destruction | PARTIAL (missed DP `newRecord`) | D5b, P2-8c extended | ADDRESSED (design), DECISION |
| SR2-61 | Quarantine destination conditionally valid; source effects open | PARTIAL | covered by P2-8a and PO-B | ADDRESSED, subject to PO-B |
| SR2-62 | Worksheet `file_id` is ordered, not an L3 exemption | FULL | K1 wording | ADDRESSED |
| SR2-63 | Sequence, grouping and `record_file` keys: retain parents throughout writes | FULL (K2, K14, K15) | none | ADDRESSED |
| SR2-64 | Concrete batch/item schedule | FULL | none | ADDRESSED |
| SR2-65 | Mandatory tests: same item; same-content repair planning; commit and rollback during claiming; old-generation unresolved; cross-operation settlement | PARTIAL | LP-10h, LP-10f, LP-10d, LP-10k | ADDRESSED (specification) |

#### §5 Derivative closure

| ID | Finding | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-66 | A new transaction per attempt; loop outside the callback; no swallowed exception or savepoint | PARTIAL (new transaction stated) | loop and callback boundary explicit | ADDRESSED |
| SR2-67 | READ COMMITTED required | FULL | none | ADDRESSED |
| SR2-68 | Original version, actor and request | FULL | none | ADDRESSED |
| SR2-69 | Three total attempts | FULL | none | ADDRESSED |
| SR2-70 | Absent root; a missing acquisition must not count as growth | PARTIAL | MISSING handling explicit | ADDRESSED |
| SR2-71 | Checks before exhaustion or a friendly id; no hidden-root leak | FULL | restated | ADDRESSED |
| SR2-72 | History on the stabilised set; a post-commit child gets creation and inheritance history | PARTIAL | history semantics explicit; LP-12 asserts creation history | ADDRESSED |
| SR2-73 | All-state, deep, overlapping-root and publisher-rollback cases | PARTIAL (no overlapping or rollback) | LP-11b, LP-12b | ADDRESSED (specification) |

#### §6 Insertion and compaction

| ID | Finding | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-74 | Affected-row count 1 per compaction UPDATE | FULL | none | ADDRESSED |
| SR2-75 | Immediate uniqueness in PostgreSQL with winner rollback and partial overlap | FULL (LP-2b, LP-3) | none | ADDRESSED (specification) |
| SR2-76 | Track the K key when INSERTED | FULL | none | ADDRESSED |
| SR2-77 | Integer bounds before allocation | FULL | none | ADDRESSED |
| SR2-78 | Entry-point count distinctions | FULL | none | ADDRESSED |
| SR2-79 | KS conflicts with FC's root FOR UPDATE; the timing change must be stated | FULL (moved to SHARE; listed in §7) | none | ADDRESSED, DECISION D-LP-7 |
| SR2-80 | Pre-wait classification ≠ per-item reads; races including descendant restriction via another root; state the linearization rule | PARTIAL (re-read under lock, no stated rule) | linearization rule; LP-3b | ADDRESSED |
| SR2-81 | Preserve history order, content, version, timestamps, atomicity | FULL | none | ADDRESSED |
| SR2-82 | `saveMarks` duplicates separate from `add` deduplication | FULL | none | ADDRESSED |
| SR2-83 | LP-3 wording: "a gap or a just-vacated slot" | FULL in §4.3 | LP-3 wording | ADDRESSED |

#### §7 Guard contract

| ID | Finding | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-84 | A: parent re-lock after Ct | FULL (`assertHeld`) | none | ADDRESSED |
| SR2-85 | B: stale batch | FULL (per statement, one open ticket) | none | ADDRESSED |
| SR2-86 | C: row privacy ≠ key ownership, including path, batch and CURRENT indexes | PARTIAL (worksheet only) | `privateNew` restricted; shared keys use `insertKey` | ADDRESSED |
| SR2-87 | Modes, mixed plans, duplicate strongest mode, table and level consistency, harmless re-locks, rollback reset | PARTIAL (no table and level check; equal-replacement error) | both added | ADDRESSED |
| SR2-88 | The static no-explicit-lock condition must be replaced; the guard cannot certify uninstrumented waits | FULL | ownership derived from held locks | ADDRESSED |

#### §8 Bounded evidence

| ID | Finding | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-89 | Harness correction and four-job results verified | FULL | none | ADDRESSED |
| SR2-90 | Limits: the SA-R1 test does not collect all outcomes or txids; teardown rollback; not reusable as an acceptance test | PARTIAL | LP-9b extension; BASE only | ADDRESSED (specification) |

#### §9 Test designs

| ID | Test | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-91 | LP-1: hold each at its first different K lock; corrected path with an independent blocker; sampling insufficient | PARTIAL (baseline right; corrected relied on sampling) | LP-1b independent blocker | ADDRESSED |
| SR2-92 | LP-2: distinct keys and seams; rollback and partial overlap | FULL | none | ADDRESSED |
| SR2-93 | LP-3: control the claimant's commit; verify every row and both entry points | PARTIAL | controlled commit; unchanged rows | ADDRESSED |
| SR2-94 | LP-4: extension, later-chunk source, fault and replay, omitted source lock, validation precedence | PARTIAL | LP-4d | ADDRESSED |
| SR2-95 | LP-5: both orders, all states, multiple depths | PARTIAL | states and depths added | ADDRESSED |
| SR2-96 | LP-6 kept as characterisation | FULL | none | ADDRESSED |
| SR2-97 | LP-7 cannot certify W1–W11 now | PARTIAL | marked synthetic | ADDRESSED |
| SR2-98 | LP-8 negative tests | FULL (most) | additions | ADDRESSED |
| SR2-99 | LP-9: observe explicit closure acquisition; full outcomes | PARTIAL | LP-9a wording | ADDRESSED |
| SR2-100 | LP-10 symmetric trap | FULL | none | ADDRESSED |
| SR2-101 | LP-10b cannot pass I-ADV; a manual flag is not ownership | PARTIAL (rev 3 used manually set context flags) | ownership registered by the lock calls; `pg_locks` cross-check; delegated helpers in the call graph | ADDRESSED |
| SR2-102 | LP-11: acknowledged publisher commit; original version; independent commit not undone | PARTIAL | LP-12 exhaustion wording ("restriction's own writes absent") | ADDRESSED |
| SR2-103 | LP-12: control timing; uncommitted child; post-commit history; exhaustion needs an acknowledged commit before each re-query | PARTIAL | rewritten | ADDRESSED |
| SR2-104 | Delete the universal baseline rule; four classes | FULL | none | ADDRESSED |
| SR2-105 | Corrected path: disable automatic retries; zero 40P01 and 55P03; collect all futures; independent controller bound | PARTIAL (no retry or controller-bound rule) | added | ADDRESSED |

#### §10 Decisions and §11 required amendments

| ID | Finding | Rev 3 | Revision 3.1 action | After 3.1 |
|---|---|---|---|---|
| SR2-106 | D-LP-1 revise: PO-1, PO-2, triggers, Source → Repository | PARTIAL | TR and L7 revised; D-LP-9 | DECISION |
| SR2-107 | D-LP-2 acceptable with amendments | FULL | none | DECISION |
| SR2-108 | D-LP-3 acceptable with clarifications; three-attempt conflict is an owner decision | PARTIAL | clarifications done | DECISION |
| SR2-109 | D-LP-4 revise: re-lock, batch, shared keys, static premise | PARTIAL | done | DECISION; GAS-P4-002 not closed by specification |
| SR2-110 | D-LP-5 recommended | FULL | none | DECISION |
| SR2-111 | D-LP-6a conditional on ownership proof | FULL | none | DECISION, subject to PO-A |
| SR2-112 | D-LP-6b recommended; audit new-content and repair paths | FULL | K8, K8b, K19 | DECISION |
| SR2-113 | D-LP-7 conditionally acceptable | FULL | linearization rule | DECISION |
| SR2-114 | D-LP-8 active now | FULL (Option C) | P2-8c extended | DECISION |
| SR2-115 | §11 (1): withdraw the readiness implication; PO-1 contradicted; PO-2 not established | PARTIAL (withdrawn, but not stated as "contradicted / not established") | proposal header states it | ADDRESSED |
| SR2-116 | §11 (2)–(8). **[3.2] Expanded; this row resolves nothing on its own:** (2) writer matrix → SR2-10…51; (3) uniqueness ledger → SR2-52…65; (4) catalog references → SR2-14…21 and SR2-B1 / D-LP-9; (5) guard → SR2-84…88; (6) closure → SR2-66…73; (7) `add` authorization and counts → SR2-74…83; (8) tests → SR2-89…105 | see the referenced rows | see the referenced rows | the status of each referenced row |

#### Builder finding during reconciliation

| ID | Finding | Evidence | Action | Status |
|---|---|---|---|---|
| SR2-B1 | **Revision 3's rule TR (c), (d) and lemma L7 were wrong.** The accepted administrator check (`UserService.update` → `lockEnabledAdministrators`, one statement, several rows FOR UPDATE ascending) waits on later administrators while holding earlier ones. With history foreign keys this forms A → T1 → T2 → A. | Executed on local PostgreSQL 16 against the real `app_user`, `change_history` and administrator-check SQL: 40P01 (proposal §11.5) | TR and L7 revised; PO-E withdrawn; **new decision D-LP-9** (first-statement advisory key for role and enable changes); LP-14 | **DECISION D-LP-9.** The defect exists in accepted code; recorded in `docs/issues.md` as GAS-P4-003 |

**R3-OPEN-1 closed by reconciliation.** The register above has **111 populated numeric SR2 rows** (SR2-1…SR2-116,
with **SR2-7, SR2-8, SR2-9, SR2-38 and SR2-39 unused**) **plus SR2-B1**: 112 rows in all. **[3.2] Correction:**
the 3.1 checkpoint said "110 individual findings (SR2-1 to SR2-115)" plus one aggregate. The accurate count is above.
- The unused IDs are gaps in the builder's numbering, not omitted findings.
- SR2-116 is an aggregate cross-reference, not seven independent resolutions.
- SR2-B1 is an additional discovery, not a resolution.
- "ADDRESSED" in this register means a **design reconciliation** answer, not independent architectural closure and
  not implementation closure. The third assessment's Appendix A gives the independent disposition of every row (see
  the next section).

This closes the reconciliation item only. It does not close any finding the reviewer owns, WP1-R1 or GAS-P4-002.

**What remains open:**
- **Decisions:** D-LP-1…D-LP-9, including D-LP-8 with P2-8a…f and the new D-LP-9.
- **Proof obligations:** PO-A…PO-D, unproven until their tests run on an implementation.
- **Tests:** every corrected-path test is a specification only.


### Third independent architectural assessment (revision 3.1 → revision 3.2)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Third-Architectural-Assessment.md`, 16 November 2026, reviewed head `2227b10` |
| Disposition received | PROPOSAL REQUIRES REVISION. WP1-R1, GAS-P4-002, GAS-P4-003 OPEN; WP1 unaccepted; WP2 HOLD. |
| Direction | Phase 4 WP1 — Locking-Protocol Revision 3.2 Direction |
| Result | proposal revision 3.2 (same file) |

The assessment's text is not altered; findings are cited by section. IDs T3-n are the builder's, for
cross-reference only. **Response column:** what revision 3.2 does. **Status:** RESPONDED (a design or specification
answer, awaiting independent assessment) or OPEN (with the reason).

**Independent statuses recorded as the assessor gave them (§3), not changed by this response:**

| Finding | Assessor's status | Builder note |
|---|---|---|
| SA-R1 | CLOSED, architectural direction only | none |
| SA-R2 | CLOSED, architectural direction only | none |
| SA-R3 | PARTIAL | answered by T3-10…T3-32 |
| SA-R4 | CLOSED, architectural direction only | none |
| SA-R5 | PARTIAL | answered by T3-48…T3-54 |
| SA-R6 | PARTIAL | answered by T3-55…T3-70 |

None of these closes WP1-R1 or certifies a production change.

| ID | Assessment section and finding | Response (revision 3.2) | Status |
|---|---|---|---|
| T3-1 | §3: the register count is 111 numeric rows plus SR2-B1; five IDs unused | Corrected in the reconciliation text above | RESPONDED |
| T3-2 | §3: SR2-116 is an aggregate; expand it | Row expanded into the seven references; it resolves nothing on its own | RESPONDED |
| T3-3 | §3: "ADDRESSED" ≠ independent closure; named rows need qualification | Register text now says "design reconciliation"; Appendix A dispositions adopted as the independent status (T3-71) | RESPONDED |
| T3-4 | §3, Appendix A: SR2-2's pointer should be SR2-66…73 | Corrected | RESPONDED |
| T3-10 | §4 R3-R1 (1): quarantine intent not covered by CR-1 | Intent inventoried separately (proposal §4.9 Wa1) as exception E3 and lemma A4: one autocommit statement, the destination key embeds the operation id, callers hold that operation's advisory, fk KS on O and volume only (no fk on `content_id`). Durable intent-before-move kept; no transaction spans measurement or rename. | RESPONDED |
| T3-11 | §4 R3-R1 (2): standalone NOT_PROTECTED, LINK_OR_REPARSE, UNREADABLE, `observeUnresolved` and `observeExisting` | §4.9 We5 and We6; exceptions E2 and E1; lemma A3 with the finding and observation keys and the scan, finding and operation fks. Classified as content-scoped, not operation-only. | RESPONDED |
| T3-12 | §4: the contentless `attest` fast path is a named exception, not an owner path | §4.9 Wd3, exception E5, lemma A4; CR-3 text | RESPONDED |
| T3-13 | P2-8a: classify intent and every reconciliation entry point; PO-B for `releaseTargetsAt` | Wa1, Wa2, Wa3; PO-B (§5.1) proves source identity per source class | RESPONDED; PO-B conditional on INV-U for attested sources |
| T3-14 | P2-8b: re-read the generation under Ct | Wb1 | RESPONDED |
| T3-15 | P2-8c: include completion, stopped-repair conversion, `markVacated`, `newRecord`/classify/refuse in the operative table | Wc1…Wc7 in the operative table; no "single statement therefore safe" substitution for fk or trigger statements | RESPONDED |
| T3-16 | P2-8d: release, both attest branches, close, delegated settlement; identity read versus revalidated | Wd1…Wd5, with entry identity and revalidation columns | RESPONDED |
| T3-17 | P2-8e: extend or exempt the other finding, observation and verification writes | We1…We6 | RESPONDED |
| T3-18 | P2-8f: branch on actual persisted content identity; contentless ingest; private publication | §4.9 preamble (the lock returns MISSING, so CR-3 applies), Wf1…Wf3 | RESPONDED |
| T3-19 | §4: modified Option C; distinguish six groups from window count | Six groups change 15 windows; Phase 2 total 18 windows under 9 items (proposal §4.9, §7) | RESPONDED |
| T3-20 | §5 PO-A: `renewReceivers` is not single-O | Lemma A2 (disjoint receiving sets) | RESPONDED; conditional (A2) |
| T3-21 | PO-A: I5 does not meet the O/event premise | Lemma A3 | RESPONDED |
| T3-22 | PO-A: event and inserted-row fks, triggers, REQUIRED joining | Lemma A1 proof (columns written, fk columns, K4); guard G9 | RESPONDED |
| T3-23 | PO-A: CR-2 "only O" wording; event key and terminal fks as stated exceptions | CR-2 rewritten | RESPONDED |
| T3-24 | PO-A: forbid joining a resource-holding transaction; LP-10b audits SQL, not flags; LP-10g controller | G9; LP-10b; LP-10g | RESPONDED |
| T3-25 | PO-B: prove qualifying identity for generation retirement and settlement, including historical, vacated and unresolved paths | §5.1 with fact F1 (GENERATION owner rows are never deleted) | RESPONDED; generation and own-target cases source-supported; attested case conditional on INV-U |
| T3-26 | PO-B: intermediate versions of blocked UPDATEs; handoffs | §5.1 handoff and blocked-UPDATE rows | RESPONDED |
| T3-27 | PO-B tests: real triggers, matching and non-matching, handoff, rollback, controller locks excluded | LP-10e (i)–(iv) | RESPONDED (future test) |
| T3-28 | PO-C: both owner keys, release DELETE and claim INSERT triggers, generation PK and CURRENT, later holder waits | §5.2 S1, S2, S3 | RESPONDED |
| T3-29 | PO-C: CREATE_NEW is not SQL proof; "terminal holder" applies only to `createOwned`; `commitNewContent` needs a private-path proof | §5.2 S1 (terminal), S3 (INV-P) | RESPONDED; conditional on INV-P |
| T3-30 | PO-C tests: an uncommitted SQL claim, external removal, commit and rollback, the owner key | LP-10f rewritten; LP-10h scoped | RESPONDED (future test) |
| T3-31 | PO-D: every existing-content caller (incl. `newRecord`); cross-content exclusion | §5.3 | RESPONDED; conditional on INV-P |
| T3-32 | PO-D tests: actual path, independent operations, commit and rollback, returned existing record | LP-10d (unchanged ID; contract per §10 common rules) | RESPONDED (future test) |
| T3-33 | §6: worksheet key and DELETE retain the key | K1; guard G3 | RESPONDED |
| T3-34 | §6: batch/item first-wait needs a harmless holder lifecycle | K3 and lemma A4 analogue: the receive insert's later waits are fk KS on parent F (blocked only by restriction or destruction holders, which never wait on receivers) and on terminal rows | RESPONDED |
| T3-35 | §6: findings have no OPEN constraint; observation PK and scan/finding fks are separate | Lemma A3 | RESPONDED |
| T3-36 | §6: "one scan at a time" must not be an asserted lock | Lemma A3 no longer needs it (an observation-key holder is a terminal autocommit statement) | RESPONDED |
| T3-37 | §6: deferred `file_operation.file_id` fk at COMMIT; check every writer that sets it | §5.2 S3, §4.9 Wf3; FD (File held); A1 windows set no fk column; LP common rule "commit-time outcomes collected" | RESPONDED |
| T3-38 | §6: deferred V0016 checks are reads; V0017 effects inventoried separately | §5.1 and §5.2 | RESPONDED |
| T3-39 | §7: put the key before any database lock; reject an outer transaction | §4.10 position and entry contract; guard G11 | RESPONDED |
| T3-40 | §7: a documented, collision-free key | (7340, 1) two-int4 key; builder-executed key-space check (proposal §11.6) | RESPONDED |
| T3-41 | §7: READ COMMITTED; a fresh read in a subsequent statement | §4.10 | RESPONDED |
| T3-42 | §7: `create` and CLI `create-admin` participate, or a proven insert-only exception | Insert-only exception documented with its waits | RESPONDED |
| T3-43 | §7: identity columns immutable; target NKU compatible with history KS | §4.10 | RESPONDED |
| T3-44 | §7: preserve the precedence chain and history | §4.10 precedence row | RESPONDED |
| T3-45 | §7: restrict the entry point to its unit of work | entry contract; G11 | RESPONDED |
| T3-46 | §7: last-administrator invariant and bootstrap | §4.10 | RESPONDED |
| T3-47 | §7: NOWAIT is not an equivalent fix; two-transaction A → P → A | NOWAIT withdrawn as an equivalent; the schedule was **reproduced** by the builder (40P01, local PostgreSQL 16; proposal §11.7), with the D-LP-9(a) variant completing without deadlock | RESPONDED; builder-executed, not CI |
| T3-48 | §8 (1): which shared keys are instrumented; multi-index statements; delegated trigger keys | §6.2 G2, G3, G7 and the instrumented-key list | RESPONDED |
| T3-49 | §8 (2): row locks versus uniqueness positions; `privateNew` must not discard a shared key | §6.1 state; G4 | RESPONDED |
| T3-50 | §8 (3): TAKEN is a passed position | G2 | RESPONDED |
| T3-51 | §8 (4): statement exceptions, savepoints, REQUIRED nesting, reset | §6.2 statement exceptions and nesting; G12 | RESPONDED |
| T3-52 | §8 (5): session advisory ownership across transactions; cross-thread; real release | G8 (registry survives reset; `handOff`; deregister on real unlock) | RESPONDED |
| T3-53 | §8 (6): reject single-resource entry with earlier resources | G9 | RESPONDED |
| T3-54 | §8 (7): one contract for L1p, CR-2, CR-3 and fast paths | G10 | RESPONDED |
| T3-55 | §9 LP-1a/b | Unchanged (already adequate) | RESPONDED |
| T3-56 | LP-2a/b/c | Unchanged; LP-2c exists | RESPONDED |
| T3-57 | LP-3/3b: slots 6, 8, 9; both claimant variants | Wording corrected; variants retained | RESPONDED |
| T3-58 | LP-4: replay paths must match accepted validation | LP-4c/4d retained; noted | RESPONDED |
| T3-59 | LP-5: no forced seam after a shared first lock | common rule "barriers only at reachable seams" | RESPONDED |
| T3-60 | LP-6, LP-7 | Unchanged (DB; synthetic) | RESPONDED |
| T3-61 | LP-8: ownership-survives-reset; invalid single-resource entry | LP-8b additions | RESPONDED |
| T3-62 | LP-9a: P < D; reverse order separately | LP-9a precondition; LP-9c | RESPONDED |
| T3-63 | LP-9b extension | Retained | RESPONDED |
| T3-64 | LP-10a and baseline | Retained | RESPONDED |
| T3-65 | LP-10b: audit SQL, triggers and fks | Rewritten | RESPONDED |
| T3-66 | LP-10c/d: actual shared keys, grant order, rollback, returned record | Covered by common rules and §5.3; contracts unchanged in ID | RESPONDED |
| T3-67 | LP-10e/f/h | Rewritten (§10) | RESPONDED |
| T3-68 | LP-10g: controller-held Ct; observe both waits; release | Rewritten | RESPONDED |
| T3-69 | LP-13: incompatible holdings; both orders; terminal chain | Rewritten (i)–(iii) | RESPONDED |
| T3-70 | LP-14: BASE; queueing with a second role change; last-admin, create-admin, password reset, history; four jobs | LP-14a and LP-14b (i)–(vi) | RESPONDED |
| T3-70b | §9: BASE list (LP-1b and LP-2b are ACC; LP-10a-baseline; add LP-14 BASE) | Corrected | RESPONDED |
| T3-71 | Appendix A: independent dispositions of every SR2 row (D, C, P, O) | **Adopted** as the independent status of each SR2 row. The rows marked P are answered by T3-10…T3-70; C rows depend on the named invariants; O rows await owner decisions. | RESPONDED |
| T3-72 | §10: D-LP-1…D-LP-9 recommendations | Proposal §8 shows the assessor's recommendation beside the builder's for each | RESPONDED; DECISION (owner) |
| T3-73 | §11 (7): stale statements: §4.8 administrator "none after first statement"; L6 administrator exception; "all advisory acquisitions remain try-locks"; §13.2 D-LP-1…8 wording; the six/seven count | All corrected in proposal §4.8, §5 (L6, preamble), §13.2, §0, §3.3 and §7 | RESPONDED |
| T3-74 | §11 (8) and §12: owner decisions; preserve Phase 0–3 contracts | Proposal §7 preamble and §8 | DECISION (owner) |
| T3-75 | §12: WP1-R2 and WP1-R3 not finally closed | Acknowledged; no closure claimed | RESPONDED |

**Remaining open:**
- **Decisions:** D-LP-1…D-LP-9.
- **Lemmas and obligations:** A1–A4 and PO-B…PO-D, with their invariants INV-U, INV-P and A2's disjointness. These
  are source-supported or conditional, and unproven by execution.
- **Tests:** every ACC test is a future test.
- **Issues:** WP1-R1, GAS-P4-002 and GAS-P4-003 are not closed.

**[3.3] T3 register count correction:** the register above has **71 populated rows**: 70 numeric rows plus
T3-70b. T3-5…T3-9 are unused; T3-75 is the highest label, not a count. The fourth assessment's Appendix A
dispositions (D, C, P, O) are adopted as the independent status of each T3 row. "RESPONDED" means a design response
only. The P rows are answered by F4-1…F4-12 below.

### Fourth independent architectural assessment (revision 3.2 → revision 3.3)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Fourth-Architectural-Assessment.md`, 16 November 2026, reviewed head `b4c4aca` |
| Disposition received | PROPOSAL REQUIRES REVISION; complete implementation authorization: NO at 3.2. D-LP-9(a) architecturally acceptable as a separately bounded item. WP1-R1, GAS-P4-002, GAS-P4-003 OPEN; WP1 unaccepted; WP2 HOLD. |
| Direction | Phase 4 WP1 — Locking-Protocol Revision 3.3 Direction. **Received truncated** at §2.3 ("Compare A and B against: -"). The criteria and any later items were not received; this response follows the assessment's §11 minimum amendments. |
| Result | proposal revision 3.3 (same file) |

**SA-R statuses as the assessor gave them:**

| Finding | Status |
|---|---|
| SA-R1 | CLOSED (direction) |
| SA-R2 | CLOSED (direction) |
| SA-R3 | PARTIAL |
| SA-R4 | CLOSED (direction) |
| SA-R5 | PARTIAL |
| SA-R6 | PARTIAL |

None is implementation closure.

| ID | Assessment section and finding | Response (revision 3.3) | Status |
|---|---|---|---|
| F4-1 | §3 (BLOCKING): INV-U contradicted; settlement Ct → O(W) against repair O(W) → Ct after path reoccupation | INV-U withdrawn, not renamed. Both counterexamples documented (destruction refusal; later-repair reclaim). Alternatives A and B compared; **A recommended** as new group **P2-8g**: Wh1 monotonic planning; Wh2 OPEN check in `createOwned` under Ct(X) Sh, serialised against every OPEN-record creator, converter and resolver. PO-B's settlement case rewritten (proposal §5.1, §5.4). Accepted-code semantic consequence recorded as GAS-P4-004. | RESPONDED (design); DECISION D-LP-8 / P2-8g |
| F4-2 | §7 (BLOCKING): the G2 index-name ordering rejects valid inserts; request-order sequences; G1 ignores higher-level held resources | Key classes (ordered, coordinated, delegated, private); common projection π; G1 compares against all held or passed ordered resources at every level; G2 and G3 rewritten with no cross-index ordering; acceptance and rejection witnesses (proposal §6.3) | RESPONDED (design); DECISION D-LP-4 |
| F4-3 | §4: `FileService.download` MISSING window omitted; standalone download SIZE_MISMATCH and `tryShare` NOT_PROTECTED | We7 (Ct U first, state re-read; effects committed before the accepted exception); We5 additions; counts updated to 18 P2-8 windows and 21 in Phase 2; LP-16 | RESPONDED (design) |
| F4-4 | §4: A3 says KS waits on NKU (wrong); owner `note` FOR UPDATE holders omitted from A3 and A4; held earlier KS references | A3 corrected (KS conflicts only with FOR UPDATE and key updates); new lemma **A0** (owner `note` holders are terminal after the note); A3 holder argument for earlier KS; A4 corrected | RESPONDED |
| F4-5 | §4: E1–E5 assessments (E1 is not intrinsically single-resource; E2 state-changing callers must not enter standalone) | G9 admits `standalone()` only at named entry points; helpers in We1, We2 and We7 join the window tracker | RESPONDED |
| F4-6 | §5: INV-P is not a database constraint; define the admitted data; legacy and case-fold audit | Admitted-data scope and a read-only preflight data-audit obligation (proposal §12) | RESPONDED; conditional |
| F4-7 | §5: receiver sets per `IngestionService` instance; `ANY` has no row order; prohibit direct overlap | Lemma A2 rescoped; ArchUnit rule and staging assertion; overlap not supported; LP-10l uses the real registration | RESPONDED; conditional |
| F4-8 | §6: PO table (A1–A4, B, C, D) and deferred COMMIT work | PO-B revised; others unchanged in substance; COMMIT outcomes are a test rule | RESPONDED |
| F4-9 | §7 rule table (G4, G7, G8, G9, G11, G12; savepoints; native JDBC) | G7 window CLOSED; G8 hand-off and LOST; G9 per-lemma effects; G11 entry versus SQL; G12 unknown completion; savepoint and native-JDBC interception | RESPONDED |
| F4-10 | §8: D-LP-9(a) acceptable; GAS-P4-003's actor count; four-job gate items 1–8 | `issues.md` now says "two or three actors depending on the schedule" (the target-update cycle also applies to the accepted ordinary FOR UPDATE check); LP-14b adopts gate items 1–8 | RESPONDED |
| F4-11 | §9: LP-8, LP-9c, LP-10b (`pg_locks` observability), LP-10d, LP-10e, LP-10f, LP-10l, LP-10m, LP-13, LP-14 | All amended (proposal §10, "[3.3] Amendments"); new LP-15 (F4-1) and LP-16 (download) | RESPONDED (future tests) |
| F4-12 | §2, §11 (6): stale text (summary CR wording; K13 scan-row protection; D4 single-resource alternative; rule 3 "no other exception") | All corrected | RESPONDED |
| F4-13 | §10: decision recommendations and counts | Proposal §7 and §8 updated: seven P2-8 groups, 18 windows; Phase 2 total 21 under ten subdecision items | DECISION (owner) |
| F4-14 | §12: evidence limits; WP1-R2 and WP1-R3 not finally closed | Acknowledged; no closure claimed | RESPONDED |

**Remaining open:**
- **Decisions:** D-LP-1…D-LP-9, including P2-8g.
- **Conditions:** INV-P (admitted data) and lemma A2 (registration protocol).
- **Tests:** every ACC test is a future test.
- **Issues:** WP1-R1, GAS-P4-002, GAS-P4-003 and GAS-P4-004 are not closed.

### Fifth independent architectural assessment (revision 3.3 → revision 3.4)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Fifth-Architectural-Assessment.md`. **Not received by the builder.** This response follows the Revision 3.4 direction's relay of its findings (F5-1…F5-4 and direction §6–§10). |
| Direction | Phase 4 WP1 — Locking-Protocol Revision 3.4 Direction (starting revision `e5bf8c0`); first received truncated in §4.2, then complete |
| Result | proposal revision 3.4 (same file) |

**Limits.** The assessment's own text, its F4 reconciliation, its source references and its counterexample wording
could not be checked. The findings are cited by the direction's section numbers. Reconciliation against the
assessment itself remains open (R34-OPEN-1).

| ID | Finding (direction section) | Response (revision 3.4) | Status |
|---|---|---|---|
| F5-1 | §2 (BLOCKING): an unlocked maximum is not a reservation; covers `planRepair` and `quarantinePartialAndReplan`; inventory all allocators | Allocator inventory (`plan`, `planRepair`, `quarantinePartialAndReplan`; nothing clears `target_generation`). Reservation transaction steps 0–7: Ct(X) U first; revalidation; high water in a subsequent statement over recorded and planned generations of **all** states; checked overflow (BLOCKED STATE_UNKNOWN before any filesystem change); compare-and-set transition with the event; commit = reservation. Durability, rollback, crash, same-operation replanning and CAS failure specified. Ct → O(own) safety argued. A counter is not introduced (the existing durable column suffices). Proposal §4.9 P2-8g. | RESPONDED (design); DECISION D-LP-8 |
| F5-1 tests | §2.4 | LP-15 reservation cases; LP-10h aligned | RESPONDED (future tests) |
| F5-2 | §3 (BLOCKING): L1p rows treated as globally UUID-ordered; Ct → U → own O rejected when U > O | G1 orders only S, F, Ct, worksheet, R and catalog. G10 admits content-coordinated U (Ct(X) U), owned O (registered advisory, not LOST) and terminal single-resource O/U, each with modes, later-permitted statements, exit, records, missing-coordinator and LOST handling. Late-global prohibition after U or a terminal lock. Implicit effects are those of the window inventory. LP-8c service-path tests. | RESPONDED (design); DECISION D-LP-4 |
| F5-3 | §4 (BLOCKING): We7 trusts ACTIVE alone; stale g1 evidence vacates repaired g2 | Observation identity (content, state, generation, volume, path); compared under Ct(X) U. Equal: finding and MISSING committed, then the accepted message. Changed: nothing inserted or changed; the accepted state or "cannot be read right now; nothing was changed" message. No filesystem work in the transaction. Authorization snapshot inventoried and unchanged; no File lock (none may follow Ct). LP-16 (i)–(ix). | RESPONDED (design); DECISION D-LP-8 |
| F5-4 | §5 (BLOCKING): a read-only report does not prevent unsafe rows from participating | Fail-closed admission and controlled isolation (Alternative A): audit AU-1…AU-8 over relational identity; classes (admitted; final stale; UNSAFE-ACTIVE; UNSAFE-AMBIGUOUS; legacy identity; aliases); exact disabled scope; visible outcome; Administrator workflow; activation boundary (the audit runs at startup before any writer starts; no old writer can run after it); recomputed at restart; no schema change, no rewriting. Proposal §5.5. | RESPONDED (design); DECISION D-LP-8 |
| F5-4 (§5.4) | GAS-P4-004 to MAJOR | Done in `issues.md`; effect bounded as directed; OPEN, source-derived | RESPONDED |
| §6 | Wh2 tightening | Ct(X) Sh; a separate fresh OPEN query; identity revalidation; no CREATE_NEW or claim when OPEN; Sh held through commit; the COLLISION block committed (not rolled back); retry replans | RESPONDED |
| §7 | PO-B reconciliation; A3 wording | PO-B attested case (a)–(d) with admission; A3 now names the resources a Ct requester may already hold; KS/NKU compatibility retained; INV-U not restored | RESPONDED; conditional |
| §8 | LP-10d, LP-10h, LP-10b, LP-15, LP-16 | LP-10d replaced with valid fixtures; LP-10h distinct reservations; the `pg_locks` claim marked historical; LP-15 three classes; LP-16 stale-generation and the authorization contract | RESPONDED (future tests) |
| §9 | Window counts | P2-8: 19 windows (adds Wh1a, Wh1b, We7, Wh2 to 3.2's 15); Phase 2: 22. Superseded counts marked historical. | RESPONDED |
| §10 | Decision register | Proposal §8 updated (D-LP-3 explicit Conflict approval; D-LP-4 classes; D-LP-8 scope; D-LP-9 no implementation authorised) | DECISION (owner) |
| R34-OPEN-1 | Assessment text not received | Reconcile when supplied | OPEN at revision 3.4; **[3.5] reconciled by the sixth assessment for reviewer access only** (see the next section) |

**Status:** revision 3.4 submitted (responds to the Revision 3.4 direction). Earlier: revision 3.3 submitted (responds to the fourth assessment). Earlier: revision 3.2 submitted (responds to the third assessment). WP1-R1, GAS-P4-002 and GAS-P4-003 are not
closed. Awaiting owner decisions D-LP-1…D-LP-9 and an independent assessment. WP1 is not accepted; WP2 remains on
HOLD.

---

### Sixth independent architectural assessment (revision 3.4 → revision 3.5)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Sixth-Architectural-Assessment.md` (reviewed head `fe197ed`), received complete |
| Direction | Phase 4 WP1 — Locking-Protocol Revision 3.5 Direction (starting commit `fe197ed8b0db232a30e8656f247741df07b5efd6`), received complete |
| Result | proposal revision 3.5 (same file), documentation only |
| Disposition received | PROPOSAL REQUIRES REVISION. F5-1, F5-2 and F5-3 CLOSED at architectural level; F5-4 OPEN; R34-OPEN-1 CLOSED for access and reconciliation only; WP1 unaccepted; WP2 HOLD |

The assessment's findings are cited by its section numbers and are not restated or altered here.

**F5 matrix reconciled against the fifth and sixth assessments.**
- The builder never received the fifth report. The sixth assessment states that it reconciled the fifth report
  directly, and that the fifth report's labels F5-G and F5-W correspond to the relayed F5-2 and F5-3.
- It also states that the admission and Wh2 findings span the fifth report's §§4–5 and §8.
- The rows below therefore take the sixth assessment's dispositions as the reconciliation. They do **not** claim
  any reading of the fifth report by the builder.

| Item | Sixth-assessment disposition | Revision 3.5 response | Status |
|---|---|---|---|
| F5-1 (fifth §2) | CLOSED (architectural), subject to the stated assumptions and future tests | Retained unchanged. Restart boundary, exhaustion retry and the defensive CAS fixture added to LP-15 (§10 [3.5]). | RESPONDED (future tests) |
| F5-2 (fifth F5-G) | CLOSED (architectural); update stale §6.3 | §6.3 Ordered row restated as G1's list; new "L1p admitted (G10)" class row | RESPONDED |
| F5-3 (fifth F5-W) | CLOSED for the stale-generation defect; point-in-time residual to be documented | Residual documented (§12 [3.5]); LP-16 (x) same-generation restoration | RESPONDED |
| F5-4 (fifth §§4–5, §8) | OPEN / PARTIAL | Replaced by global fail-closed activation (§5.6). See the §7–§8 rows below. | RESPONDED (design); DECISION D-LP-8 (v) |
| R34-OPEN-1 | CLOSED as a documentation and access reconciliation item only | Recorded as reconciled **for reviewer access**. The builder did not receive the fifth report, and no claim to the contrary is made. | RECONCILED (reviewer) |

**Sixth-assessment response matrix.**

| Ref | Finding (sixth assessment) | Response (revision 3.5) | Status |
|---|---|---|---|
| §3 F4 rows | F4-1 PARTIAL, F4-6 PARTIAL, F4-11 (multi-process fixtures; LP-10d(ii)), F4-12 (stale tables; dangling §11), F4-13 (control units separate), F4-14 (no closure) | F4-1 and F4-6: via §5.6. F4-11: LP-17 and LP-10d(ii-a/ii-b). F4-12: §11 restored, counts marked historical. F4-13: §5.6.8. F4-14: no closure claimed. | RESPONDED |
| §4 allocation | the inventory must keep forbidding unclassified allocators | unchanged; LP-10b inventory retained | RETAINED |
| §4 restart | distinguish a committed old reservation from an uncommitted computed number | §12 residual; LP-15 restart cases | RESPONDED (future tests) |
| §4 CAS | defensive CAS failure through a labelled fault or DB fixture, not a canceller | LP-15 reservation list: CAS case uses a labelled fault fixture; LP-10d(ii-a) shows the canceller is excluded by the advisory | RESPONDED |
| §4 INGEST-only fallback | a REPAIR without persisted content must not be treated as private | §4.9 content-lock rule and reservation step 1: INGEST only; a REPAIR is refused (BLOCKED STATE_UNKNOWN); AU-2 refuses activation | RESPONDED |
| §5 §6.3 | align §6.3 with G1/G10 | done | RESPONDED |
| §6 We7 | point-in-time residual; same-generation restoration test | §12; LP-16 (x) | RESPONDED |
| §7 A | the activation model does not match deployment (separate processes; local barrier) | §5.6.1 feasibility from source. The **old web process is not excluded by accepted code** (no readiness gate on its handlers). Durable activation record plus write gate layers A (old builds read-only at the database) and B (protocol and epoch); controlled maintenance command; database-verified quiescence. | RESPONDED (design); schema requirement for owner decision |
| §7 counterexample | old worker B and new N recreate the Ct/O graph | §5.6.3: excluded at three points (AHEAD startup refusal; session check; layer A refuses B's locking reads and writes before any row lock) | RESPONDED |
| §7 B | unclassified mutating entry points (RecoveryService P16 loop, receivers, intake, retry, E2 indirect, delegated helpers) | §5.6.4 E-1…E-27, one admission check per entry; `recover()` not called while disabled; helpers only from admitted entries (ArchUnit, AA-7) | RESPONDED |
| §7 C | allowed cancellation is not harmless | cancellation disabled while not ACTIVE; no isolation exception exists; offline resolution with preconditions (§5.6.7) | RESPONDED |
| §7 D | isolation must follow all conflicting identities | component closure over all identities and AU-8 keys; any unsafe component refuses activation as a whole | RESPONDED |
| §8 SHARED_CONTENT | valid COMMITTED SHARED_CONTENT ingest falsely isolated, with a recovery dead end | AU-2 classifies it ADMITTED-CLEANUP and verifies the abandoned Y plan; it completes through `finish` after activation (§5.6.5); AA-11 | RESPONDED |
| §8 AU-1 | volume, Content and current-generation relations as well as the constructor | AU-1 rewritten (creator, CURRENT count by content state, vacated, quarantine_path) | RESPONDED |
| §8 AU-2 | the meaning of the target by kind, state, outcome, `target_created` and ownership; persisted Content for REPAIR and DESTROY | AU-2 table | RESPONDED |
| §8 AU-3 | both directions; no silent repair | AU-3 (a), (b), (c) | RESPONDED |
| §8 AU-4 | real states; COMMITTED not final; no FAILED; U identity even without an owner | AU-4 (a)–(c) | RESPONDED |
| §8 AU-5 | own, stale, foreign active, ambiguous; evidence and pending-move binding | AU-5 role table | RESPONDED |
| §8 AU-6 | operation, suffix, roles, basis, unresolved linkage | AU-6 | RESPONDED |
| §8 AU-7 | states capable of claiming versus terminal and cleanup | AU-7 (can create or holds) | RESPONDED |
| §8 AU-8 | cross-column comparison, normalisation and case semantics | AU-8 (`PortableRelativePath.caseFoldKey`, NFC parse, non-ASCII refused, same-object relations) | RESPONDED |
| §8 restart list | second process, single restarts, interrupted activation, re-audit against resolution, backup, mixed rollout, in-flight jobs, stale claims, non-cancellable operations | §5.6.6 (15 cases); §5.6.7 for non-cancellable operations | RESPONDED |
| §8 alternatives | Option 1 or Option 2 | Option 1 selected; Option 2 not proposed | DECISION D-LP-8 (v) |
| §8 activation lock | must precede the resources its barrier can wait on | L-ACT before L0; every activation acquisition NOWAIT; never waits (§5.6.3) | RESPONDED |
| §9 Wh2 | retained; existing owners depend on admission | unchanged; existing owners handled by §5.6 | RETAINED |
| §9 GAS-P4-004 | MAJOR, OPEN, source-derived; no closure | unchanged in severity and status; `issues.md` status text points to §5.6 | RETAINED |
| §9 PO-B | conditional at full-system level | §5.6.9: case (d) cannot coexist with ACTIVE | RESPONDED (conditional) |
| §10 control units | admission units need their own inventory | §5.6.8 CU-0…CU-11 | RESPONDED |
| §11 item 1 | no symmetric barrier after competing Ct locks | LP-10h and LP-15 amendment | RESPONDED (future tests) |
| §11 item 2 | Wh2 claimant-first creator must be legitimate | LP-15 amendment | RESPONDED (future tests) |
| §11 item 3 | inherited overlap tested through real activation | LP-15 amendment plus AA-14 | RESPONDED (future tests) |
| §11 item 4 | LP-10d(ii) advisory trap | replaced by (ii-a) service-level skip and retry, and (ii-b) helper/DB OPEN-key fixture | RESPONDED (future tests) |
| §11 item 5 | NOWAIT probe meaning | §10 [3.5] probe rules | RESPONDED |
| §11 item 6 | reservation restart boundary | LP-15 restart cases | RESPONDED (future tests) |
| §11 item 7 | We7 same-generation restoration | LP-16 (x) | RESPONDED (future tests) |
| §11 item 8 | real admission and activation group | LP-17 AA-1…AA-16 | RESPONDED (future tests) |
| §11 items 9–10 | real enum states; full outcomes | §10 [3.5] | RESPONDED |
| §12 D-LP-1…9 | recommendations | proposal §8 rewritten (D-LP-8 split into (i)–(vii)) | DECISION (owner) |
| §12 smallest correction 1–7 | participants, boundary, enforcement, audit, resolution, failure, tests and docs | §5.6.1–§5.6.8, §10, §11 | RESPONDED |

**Builder finding during this revision.** The accepted web process keeps serving preservation writes against a
schema migrated by a newer build. Only the home page checks readiness, and ingestion readiness ignores schema state.
This is recorded as GAS-P4-005 (MINOR) in `issues.md`; layer A of §5.6.2 is its proposed remedy.

**Status:** revision 3.5 submitted (responds to the sixth assessment and the Revision 3.5 direction). Nothing is
implemented. WP1-R1 and GAS-P4-002…GAS-P4-005 are not closed. Awaiting owner decisions D-LP-1…D-LP-9 (D-LP-8 (i)–(vii)
listed separately) and an independent assessment. WP1 is not accepted; WP2 remains on HOLD.

---

### Seventh independent architectural assessment (revision 3.5 → revision 3.6)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Seventh-Architectural-Assessment.md` (reviewed head `9993220`), received complete |
| Direction | Phase 4 WP1 — Locking Protocol Revision 3.6 Direction (baseline `99932207b1fe4f4f8d1a4ec8b2a9c7c7f7ee42b0`), received complete |
| Result | proposal revision 3.6 (same file), documentation only |
| Disposition received | PROPOSAL REQUIRES REVISION. F5-4 OPEN; F5-1…F5-3 retained as resolved; all five named issues OPEN; WP1 unaccepted; WP2 HOLD |

The assessment's findings are cited by section and are not restated or altered here. "Proposal" means
`PHASE-4-WP1-LOCK-PROTOCOL-PROPOSAL.md` revision 3.6.

| Ref | Finding | Response (revision 3.6) | Status |
|---|---|---|---|
| §3 Layer A scope | A role default does not change existing sessions; it is overridable; it is a client premise | Narrowed (§5.7.4). Existing sessions are terminated after the default is installed (T4→T5, §5.7.3; builder check §11.9 (a)/(b)). The client premise is R36-3, tested by AA-3. P/E are never placed in role or database defaults. | RESPONDED |
| §3 identity and reset | Fail-closed settings; `SET LOCAL` reversion; reconnect must revalidate; prepared statements; RR; configured role, not `CURRENT_USER` | §5.7.4 installation contract; F3 validates every physical connection (§5.7.5); maintenance connections are never pooled; the role default is for the configured role | RESPONDED |
| §4 R7-1 connected-old cycle | The old administrator's locking `SELECT` precedes any DML gate; pre-ACTIVE metadata writers | F2: OS verification → role default → same-role termination before any compatible writer is admitted, including pre-ACTIVE; schedule in §5.7.6; AA-3a | RESPONDED (design); future test |
| §5 Case A | Fresh legacy reconnect is read-only | retained as layer A's proved scope | RETAINED |
| §5 Case B | Disconnected old process continues filesystem work | Legacy: OS verification at T2 (a backend count is not evidence). Compatible: F1 is proof of termination. AA-3b. | RESPONDED |
| §5 Case C | Stale new worker resumes an INTENDED move after the epoch changes | The epoch changes only under MA, which needs F1 exclusive, so it cannot happen while the worker lives; schedule in §5.7.6; AA-3c, AA-3d | RESPONDED |
| §5 checks | Sessions, presence, EXCLUSIVE locks and bootstrap establish observation, not termination | Demoted to refusal evidence; the authority is F1 and F2 | RESPONDED |
| §6 CU-0 | Migration DDL can wait; the role default must roll back with the DDL | CU-0 is one Flyway transaction with `lock_timeout` (§5.7.11) | RESPONDED |
| §6 CU-1/CU-2 | Real process exclusion, not sessions | F1, F2 (CU-2, CU-9, CU-13) | RESPONDED |
| §6 CU-3/CU-4 | NOWAIT barrier scope; audit coverage | CU-4 retained, with narrowed claims; AU rebuilt | RESPONDED |
| §6 CU-5 | Singleton invariant; event and finding constraints; fail-closed trigger | CU-5: exactly one row (trigger); fail-closed gate (§5.7.4) | RESPONDED |
| §6 CU-6 | Startup result not a lifetime capability through reconnects | It is valid for the life because of F1; it is revalidated per connection by F3 (CU-14, CU-8) | RESPONDED |
| §6 CU-7 | Gate limits; "never waits" | Narrowed (§5.7.11): compatible with B0; can wait behind ACCESS EXCLUSIVE migration DDL | RESPONDED |
| §6 CU-8 | Unknown COMMIT while the backend may still commit | CU-21: undecided until the recorded backend is confirmed gone | RESPONDED |
| §6 CU-9…CU-11 | Re-admission fencing; resolution lifetime; protocol-change identity | CU-6, CU-10…CU-20; §5.7.12 protocol change under MA (declares the current identity, then `SET LOCAL` the new one) | RESPONDED |
| §6 attribution | `--administrator` is not authentication | `operator_principal` = the OS identity; the named Administrator is recorded as named only (§5.7.8) | RESPONDED |
| §6 restore | Role defaults are not restored automatically; dump ACTIVE is not a certificate | Database identity (`system_identifier`, OID) recorded; `verify-restore`; CU-14 checks layer A (§5.7.12); AA-13 | RESPONDED |
| §7 DML coverage | Statement triggers only; partitions, sequences, DDL, privileges, replica mode | §5.7.4: `ENABLE ALWAYS`, `VOLATILE`, `search_path`; catalog coverage check; sequence policy (gaps accepted); DDL only under MA | RESPONDED |
| §7 E-1…E-27 | Coverage policy | §5.7.5 corrections (E-2/3/4, E-12, E-14, E-15…E-21, E-23/25, E-24, E-26, E-27); E-11 (§5.7.10) | RESPONDED |
| §8 R7-A1 | First generation need not be 1 | AU-1 g₀ by creator; AU-2 NEW_CONTENT; AA-11b | RESPONDED |
| §8 R7-A2 | Historical vacated evidence | `vacated` is evidence only; constraint check only; UNRESOLVED state included; AA-11c | RESPONDED |
| §8 R7-A3 | Trusted partial-prefix evidence | AU-5 per-reason binding; no downgrade; AA-14b | RESPONDED |
| §8 R7-A4 | Destruction U names DESTROY; generation-null QUARANTINED_COPY at a Q destination | AU-4 writer roles W1–W3; AU-6 bound references; AA-10b, AA-10c | RESPONDED |
| §8 AU-1…AU-8 table | Per-check revisions, including AU-7 PROMOTED and AU-8 closure | §5.7.9 (invariant, constructors, pass, fail, joins, legacy, fixtures) | RESPONDED |
| §8 non-ASCII | A blanket refusal is a new boundary | Withdrawn; constructor-output checks instead; no Unicode rule proposed (D-LP-8 (viii) not proposed) | RESPONDED |
| §9 run-long exclusion | B0 does not survive commits; the resolver key excludes nothing else | One MA for every maintenance command (§5.7.7) | RESPONDED |
| §9 atomicity and journal | Multi-window actions; a final row is not atomic; staging deletion | Journal tables and per-window rows in the window's transaction; resume from business state; byte classes (§5.7.8) | RESPONDED |
| §9 A-1 | Conditional; full identity rules | A-1 under MA, with V0020 identity | RESPONDED |
| §9 A-2 | "Foreign only" inconsistent with AU-4 | A-2 refuses any non-final claimant | RESPONDED |
| §9 A-3 | Eligibility; revalidation before keyed release; multi-window | Accepted `requireCancellable`; revalidation in J1 and immediately before `releaseTargetsAt`; windows kept | RESPONDED |
| §9 A-4 | Bound evidence; the same protocol | A-4 journalled; full identity | RESPONDED |
| §9 A-5 | INGEST/REPAIR only; CAS; not DESTROY | §5.7.8: PROMOTING and PROMOTED only; CAS; attribution; target, high water and ownership unchanged; D-LP-8 (vi) | DECISION (owner) |
| §9 A-6 | Not acceptable as bounded | Deferred (D-LP-8 (vii)) | DEFERRED |
| §10 download | Precedence; no false "recorded"; no new rehash | §5.7.10; AA-22 | RESPONDED |
| §11 AA-1…AA-16 | Per-case amendments | LP-17 rebuilt as AA-1…AA-24 (§10) | RESPONDED (future tests) |
| §11 DB companions | Do not infer the absence of row locks from `pg_locks` | Probe rules in the LP-17 preamble | RESPONDED |
| §11 LP-10d(ii) | (ii-b) contradictory | (ii-b1) coordinator test waiting at Ct; (ii-b2) raw unique-key characterisation, labelled; (ii-a) retained | RESPONDED (future tests) |
| §12 counts | 19 / 22 business windows; control scope separate; 40 tables not a theorem | Unchanged 19 / 22; CU-0…CU-21 separate; 47 tables with a catalog coverage check | RESPONDED |
| §13 decisions | D-LP recommendations | Proposal §8 grouped (A) supported, (B) behaviour, (C) schema and control, (D) D-LP-9(a), (E) deferred or optional | DECISION (owner) |
| §13 GAS-P4-005 | The remedy cannot claim all older sessions become read-only | `issues.md` remedy text narrowed | RESPONDED |
| §14 1–8 | Minimum revision | §5.7.1–§5.7.13, §10, §11.9 | RESPONDED (design) |

**Builder observations (no new issue entry).** Source checks for this revision confirmed:
- the existing-session behaviour of role defaults (§11.9 (a));
- that `DestructionProcessor.newRecord` names the DESTROY operation;
- that `quarantinePartialAndReplan` records trusted partial evidence;
- that `commitNewContent` stores `op.targetGeneration`.

These are corrections to the 3.5 proposal, recorded above against the assessment's findings, not new accepted-code
defects.

**Status:** revision 3.6 submitted. Nothing implemented. WP1-R1 and GAS-P4-002…GAS-P4-005 are OPEN. Awaiting the
eighth independent assessment and owner decisions (proposal §8). WP1 is not accepted; WP2 remains on HOLD.

---

### Eighth independent architectural assessment (revision 3.6 → revision 3.7)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Eighth-Architectural-Assessment.md` (reviewed head `7a97e70`), received complete |
| Direction | Phase 4 WP1 — Locking Protocol Revision 3.7 Direction (baseline `7a97e7077bee22cb72d364674fa1fe1ed24b0783`), received complete |
| Result | proposal revision 3.7 (same file), documentation only; no new local execution |
| Disposition received | PROPOSAL REQUIRES REVISION. F5-4 PARTIAL, OPEN; F5-1…F5-3 retained; all five named issues OPEN; WP1 unaccepted; WP2 HOLD |

The findings are cited by section and are not restated or altered here.

| Ref | Finding | Response (proposal revision 3.7) | Status |
|---|---|---|---|
| §1 / §4 Q-36-1 | Single host is essential and not owner-confirmed; remote PostgreSQL is distinct from a second application host; host move, failover and NAS | Designed conditionally. Q-36-1 is restated in five parts in §8 and **not** recorded as approved. The binding's host ID is checked by ordinary and maintenance admission. The alternative is in §5.8.10. | DECISION (owner) |
| §4 R8-1 installation identity | Fixed host-wide path couples installations; copies with other paths reach one database | Installation ID; fixed per-platform lease root; one object per ID; database binding (installation, host, lease object, sysid/OID, Archive ID); GA002 for a mismatched copy (§5.8.1) | RESPONDED (design) |
| §5 F1 contract | Early release, multiple channels, interruption, unsupported shared mode, protected directories, links and reparse points, local filesystem, no unlocked recreation, children, launchers, shutdown | §5.8.1 lifetime rules; Windows `NOSHARE_DELETE`; Linux inode check and FENCED latch; offline-only provisioning; no child processes (source); WinSW child versus parent; supervised wait | RESPONDED (design); future tests AA-17 |
| §5 replacement counterexample | Inode I versus J | Installer and uninstaller non-replacement contract; refusal on a missing object; inode/file-index re-check latch | RESPONDED |
| §5 service accounts | Migrating as the service account versus admin visibility | Maintenance as root/Administrator; installer instruction change listed in D-LP-8 (v-a) | RESPONDED; DECISION |
| §6 database semantics | Role-default scope; privileges for the configured role; terminate after known COMMIT; termination not rolled back | V0026 sets the default for the connecting configured role; L8 after known COMMIT on a read-write connection; termination stated as non-transactional (§5.8.2, §5.8.3) | RESPONDED |
| §6 R8-2 continuous exclusion | A point scan does not exclude a restart | Owner-approved freeze D-LP-8 (xi), L1…L12; automated aids (mask or disable, the required configuration key that accepted builds reject, OS verification at L5 and L10 by PID and start instant, fail-closed on unverifiable); operator residuals stated (§5.8.2) | RESPONDED (design); DECISION (owner) |
| §6 administrator/worksheet schedule | Valid if an old session survives | Shown to be excluded after a successful transition, with its conditions (freeze, Q-36-1, R36-3) (§5.8.2) | RESPONDED (conditional) |
| §7 F3 | Compiled protocol; reconnect revalidation; fatal-signal path; versions to record | §5.8.1 binding checks; §5.8.8 GA002 runtime exit (owner approval); AA-1 version recording | RESPONDED |
| §7 stale RecoveryService | Excluded under one protected lease object | Retained; AA-3c/AA-3d amended (no inevitable failure asserted) | RETAINED |
| §7 startup behaviour | Diagnostic web start versus hard exit; `check-config` and `help` | Diagnostic mode kept; effect-free commands without lease or database; `status` on a read-only diagnostic connection; writers require COMPLETE (§5.8.8) | RESPONDED |
| §8 R8-3(a) bootstrap circularity | MA requires objects V0026 creates | States S0…S9; B1…B10 (+ B11); in-process registration before V0026; V0026's single-transaction order; S3 refuses ordinary admission; known-COMMIT determination (§5.8.3) | RESPONDED (design); future AA-19 |
| §8 Flyway contract | "First statement of every maintenance transaction" with Flyway internals | Connection registration by factory; V0026 sets its own local identity; later migrations call `maintenance_begin` explicitly; one transaction per migration (R37-2) | RESPONDED |
| §8 R8-3(b) stale identity | Publication leaves the controller at E; GA001 at close | No session-level maintenance declaration; per-transaction `maintenance_begin`; closure sequence without GA001 (§5.8.4) | RESPONDED |
| §8 R8-4 controller loss | A point check is not a fencing token; drain; resume; single phase; exact predicate; bound admission; cross-host | F1 as the ultimate authority; latch; bounded continuation with a non-overlap argument; drain by `(pid, backend_start)`; resume reconciliation; single active phase; exact advisory predicate; `admit_maintenance`; no remote controller (§5.8.5) | RESPONDED |
| §8 CU table | CU-0, CU-1…3/13, CU-4…6, CU-7…8, CU-9, CU-10…12, CU-14…16, CU-17…19, CU-20, CU-21 | Amended in §5.8.3–§5.8.6; CU-22…CU-30 added (31 units); "nothing waits" made inoperative; CU-21 runs in resume under F1 exclusive, never from read-only status | RESPONDED |
| §9 R8-5 replay premise | `markStagingCleaned`, `note`, `release`, `move`, `settle` | R36-5 inoperative; stable window keys; attempt, observation and reconciliation records; 9-step algorithm; filesystem matrix (§5.8.6) | RESPONDED |
| §9 A-3 branches | INTENDED, own Q INTENDED, QUARANTINED, SOURCE_ABSENT, unverified atomic, CANCELLED with or without window, staging, ineligible | Resume table; window 4 keeps `applyCancel`'s single transaction; revalidation on every release path; the owned target is the passing case | RESPONDED |
| §9 A-1, A-2, A-4 | Completion predicates; A-4 eligibility; byte terminology | Predicates; eligibility unchanged; authoritative originals versus an attested unidentified object at an UNRESOLVED generation path | RESPONDED |
| §9 A-5, A-6 | Kind in the predicate; A-6 inoperative | SQL with kind and state; A-6 marked inoperative in §5.6 | RESPONDED; DECISION (vi) |
| §10 R8-6(a) targetless BLOCKED | `plan` blocks before planning | AU-2 targetless branches by kind, state and reason; no manufactured target; REPAIR never private | RESPONDED |
| §10 R8-6(b) recorded absence | Null evidence at intent | AU-5 recorded-absent rules for every reason; later objects unauthorised; SOURCE_ABSENT; the A-3 wording corrected | RESPONDED |
| §10 R8-6(c) pointer | Pointer replaced by a CANCELLED_COPY destination; crash before replan | Transition-specific rule from the two source writers (replan sets it for QUARANTINED **or** SOURCE_ABSENT; crash-then-rewrite partials never set it) | RESPONDED |
| §10 constructor and Unicode scope | Eight identity columns only; observation paths; NFC | R37-6 scoped; observation paths never refuse; Unicode NFC accepted | RESPONDED |
| §10 AU-1…AU-8 table | Per-check residuals | Summary table and fixtures (§5.8.7) | RESPONDED |
| §11 table count and coverage | 40 + 7; catalog verification | 40 + **8** = 48 (`maintenance_action_event` added for attempts and observations); coverage by function OID, `tgtype`, `tgenabled`, digest, per partition | RESPONDED |
| §11 statement semantics | Relation locks before triggers; MERGE, ON CONFLICT, TRUNCATE | §5.8.8 | RESPONDED |
| §11 control invariants | Singleton, events, run, action, window rows | §5.8.8 invariants | RESPONDED |
| §11 restore | Identity is not a certificate; rewind | §5.8.8 restore procedure and limitation; B11 | RESPONDED |
| §11 disabled behaviour | Exact exceptions | §5.8.8 list; D-LP-8 (v-c) wording corrected | RESPONDED |
| §12 AA-1…AA-24 | Per-case amendments | LP-17 3.7 amendments table (§10) | RESPONDED (future tests) |
| §12 LP-10d(ii-b) | Reachable recorders | No reachable service pair found; (ii-b1) labelled helper-level; (ii-b2) raw; (ii-a) unchanged | RESPONDED |
| §13 decisions | Matrix | §8 regrouped; (v-a/b/d) pending; (vi) conditional; (vii) deferred; (x) optional; (xi) new; D-LP-9(a) separately eligible | DECISION (owner) |
| §14 1–10 | Minimum corrections | 1: owner decisions requested, not granted; 2–10: §5.8.1–§5.8.9 and §10 | RESPONDED (design); items 1 open |

**Status:** revision 3.7 submitted. Nothing implemented; no new execution. F5-4 is **not claimed closed**: the design
is conditional on Q-36-1 and D-LP-8 (xi), both unapproved. WP1-R1 and GAS-P4-002…GAS-P4-005 remain OPEN. Awaiting
the ninth independent assessment and owner decisions. WP1 is not accepted; WP2 remains on HOLD.

---

### Ninth independent architectural assessment (revision 3.7 → revision 3.8)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Ninth-Architectural-Assessment.md` (reviewed head `dcd44f1`), received complete |
| Direction | Phase 4 WP1 — Locking Protocol Revision 3.8 Direction (baseline `dcd44f1aa98ac6d81efd757e7f1f8eea3387e88f`), received complete |
| Result | proposal revision 3.8 (same file), documentation only; no new local execution |
| Disposition received | PROPOSAL REQUIRES REVISION; A: NO; B: NO; D: YES for separately bounded D-LP-9(a); F5-4 OPEN; F5-1…F5-3 retained; all five named issues OPEN |

The findings are cited by section and are not altered here. "Proposal" means revision 3.8. A response is not
closure.

| Item | Source contradiction (assessment) | 3.8 correction | Proposal § | Accepted source / migration | Future fixture | Remaining uncertainty | Builder disposition |
|---|---|---|---|---|---|---|---|
| **R9-M1** compatibility | `maintenance_begin` copied P from the database; an old jar could adopt P2 (§6) | Compiled `(schema_top, protocol)` checked against the history and the singleton, never copied; bounded `maintenance_begin_upgrade` for P → P+1 inside the new build's migration only | 5.9.1 | `SchemaMigrator.status` (AHEAD/FAILED/INVALID refusal retained) | AA-1b, AA-16b | the pinned Flyway behaviour for placeholders and validation | RESPONDED (design) |
| **R9-M1** binding | The maintenance predicate omitted installation, lease, cluster/database and Archive ID; the I2 copy | BC items 1–10 in Java and in every entry function before any effect; I2 schedule | 5.9.1 | `InstallationConfigLoader`; `archive_instance` (V0002) | AA-1c | opened-identity API (R38-1) | RESPONDED (design) |
| **R9-M1** generation | Old registrations joining a later controller | Registration carries the controller generation; `maintenance_begin` requires the current one; R-3 requires every old-generation backend gone | 5.9.2, 5.9.8 | — | AA-20b | — | RESPONDED |
| **R9-B1** registration cycle | B5 called `maintenance_begin` before registration; `admit_maintenance` registered undeclared | R-1: V0026 inserts the controller and Flyway connection rows; R-4 `maintenance_register` sets its declaration after its own checks | 5.9.2 | — | AA-19b | — | RESPONDED |
| **R9-B2** new run | No gate-compatible run creation after CLOSED; a possible generic advisory-holder exception | R-2 `maintenance_open` with self-advisory, COMPLETE, BC and allowed kinds; R-3 rebind; R-5 restore; the 3.7 exceptions inoperative | 5.9.2 | — | AA-19c | — | RESPONDED |
| **R9-B3** pre-gate states | Partial fresh chains classified as S0 or S1; registrations lost before V0026 | PRE_GATE_BEHIND; SchemaMigrator recognition; drain of every application-role backend before determining COMMIT; history-row determination; freeze maintained | 5.9.3 | `SchemaStatus` (NOT_INITIALIZED, BEHIND, AHEAD, FAILED, INVALID); `SchemaMigrator.migrate` | AA-19b | R38-2 (Flyway transaction per migration for the whole chain) | RESPONDED |
| **R9-B4** archive binding | `init-archive` was no lawful binding writer | `bind_archive_id`, atomic with the accepted insert; the trigger allows NULL → value only; unknown-COMMIT consistency (CU-31) | 5.9.4 | `init-archive`; V0002 | AA-19d | — | RESPONDED |
| **R9-B4** restore | Restored OPEN run; S8 wording; physical restore | R-5 supersedes the old run with its journal preserved; no second OPEN; `verify-restore` mandatory for physical restore (CU-33) | 5.9.4 | — | AA-19e | physical restore is undetectable: an operational obligation | RESPONDED |
| **R9-L1** lease | Detection presented as prevention; missing or replaced object recovery; opened identity | Prevention (protected namespace, mount and namespace rules, installer contract) is primary; detection only; handle identity; `repair-lease` with verified stop and kept exclusive lock (CU-32) | 5.9.5 | `deploy/linux/*`, `deploy/windows/*` | AA-17, AA-17b | R38-1; a privileged administrator outside the supported paths is out of scope | RESPONDED |
| **R9-L1** scans | L5/L10 refused self and other installations | Classification: self exempt by PID + start; other installation allowed; unrelated allowed; same or unverifiable refused; copies stay inside the freeze | 5.9.5 | — | AA-17c | copied or renamed jars cannot always be found (freeze) | RESPONDED |
| **R9-L1** AA-3a | psql "orphaned backend" mislabelled | Relabelled as a non-Java pre-default connection | 5.9.5 | — | AA-3a (ii) | — | RESPONDED |
| **R9-J1** | A-2 used a non-existent RELEASED | NOT_ARCHIVAL predicate with decision evidence; eligibility consistent with AU-4 (final stale claims pass) | 5.9.6 | `UnresolvedObjectRepository.release`; V0019 `Resolution` | AA-21b | — | RESPONDED |
| **R9-J2** | One window number for several pending settlements; post-state before J0 | `window_key` per subject (Q id), the plan fixed at J0 with SATISFIED_AT_J0 and NOT_PLANNED; separate transactions kept; `publication_seq` per run | 5.9.6 | `applyCancel` pending loop; `QuarantineMoves.settle` | AA-8b, AA-4b | — | RESPONDED |
| **R9-J3** | Destination-only UNTRUSTED arrival omitted | Matrix row: non-attested reason with evidence, destination matching → QUARANTINED, basis kept UNTRUSTED; never authorises a new source move | 5.9.6 | `QuarantineMoves.arrived`, `isRecordedObject`, `settle` | AA-14f | — | RESPONDED |
| §9 A-4 / byte classes | QUARANTINE_PENDING mislabelled as authoritative | Eligibility as `quarantineAttested`; `retireGenerationAt` (QUARANTINE_PENDING or UNRESOLVED → QUARANTINED); authoritative = CURRENT only | 5.9.6 | `UnresolvedObjects.quarantineAttested`; `FileContentRepository.retireGenerationAt` | AA-15 | — | RESPONDED |
| §11 action states and publication identity | INTENDED → NOT_APPLIED; publications per run | Forward state machine with direct INTENDED → NOT_APPLIED or ABANDONED; `(run_id, publication_seq)` keys | 5.9.6 | — | AA-21b, AA-4b | — | RESPONDED |
| §8 liveness | "bounded by its own completion" is not a time bound | Hold F1 while waiting; kill-and-resume; never release while a thread survives | 5.9.8 | — | AA-20c | R38-3 | RESPONDED |
| **R9-A1** historical plans | Path inequality taken as proof of a replan; COLLISION retry gives a fresh UUID | Inventory of every target-assignment transition; relation H(O, uuid, g) from machine-readable provenance; AU-2 pointer, AU-4 W1, AU-5 own-copy and AU-8 corrected; refusal where H cannot be established; no waiver | 5.9.7 | `OperationProcessor` `plan` / `planRepair` / `quarantinePartialAndReplan`; `OperationAdminService.retry` / `applyCancel` | AA-11h…AA-11o | R38-4, R38-5 | RESPONDED |
| §14-1 | Maintenance identity | as R9-M1 | 5.9.1 | | | | RESPONDED |
| §14-2 | Run bootstrap and adoption | as R9-B1/B2 | 5.9.2 | | | | RESPONDED |
| §14-3 | Pre-gate and restore states | as R9-B3/B4 | 5.9.3, 5.9.4 | | | | RESPONDED |
| §14-4 | Archive initialisation | as R9-B4 | 5.9.4 | | | | RESPONDED |
| §14-5 | Lease repair and scan scope | as R9-L1 | 5.9.5 | | | | RESPONDED |
| §14-6 | Journal and source semantics | as R9-J1…J3 | 5.9.6 | | | | RESPONDED |
| §14-7 | Historical audit | as R9-A1 | 5.9.7 | | | | RESPONDED |
| §14-8 | Specification alignment | A-4, stale claims, publication identity, forward states, stall liveness | 5.9.6, 5.9.8 | | | | RESPONDED |
| §14-9 | Owner choices | §8 (II): Q-36-1, (xi), elevated maintenance, GA002 and the rest; none granted | 8 | | | | DECISION (owner) |
| §14-10 | Future acceptance | §8 (III); LP-17 3.8 | 8, 10 | | | | FUTURE |

**Counts:** 40 application tables + 8 control tables = 48 gated (unchanged). Control units 31 → **34** (CU-31
Archive ID binding, CU-32 lease repair, CU-33 run supersession). Business windows unchanged at 19 / 22.

**Status:** revision 3.8 submitted. Nothing implemented; no new execution. F5-4 is not claimed closed; the design
remains conditional on the unapproved Q-36-1 and D-LP-8 (xi). WP1-R1 and GAS-P4-002…GAS-P4-005 remain OPEN. Awaiting
the tenth independent assessment. WP1 is not accepted; WP2 remains on HOLD.

---

### Tenth independent architectural assessment (revision 3.8 → revision 3.9)

| | |
|---|---|
| Assessment | `Phase-4-WP1-Tenth-Architectural-Assessment.md` (reviewed head `48eeb8b`), received complete |
| Direction | Phase 4 WP1 — Locking Protocol Revision 3.9 Direction (baseline `48eeb8bd574572bf63bfb3c2be260faaf210abd3`), received complete |
| Result | proposal revision 3.9 (same file, §5.10), documentation only; no new local execution |
| Disposition received | PROPOSAL REQUIRES REVISION; A: NO; B: NO; D: NO; E: YES for separately bounded D-LP-9(a); F5-4 OPEN; F5-1…F5-3 retained; all five named issues OPEN |

The findings are cited by section and are not altered here. "Proposal" means revision 3.9. A response is not
closure.

| Item | Contradiction (assessment) | Accepted source evidence | Revised rule | Exact transaction / recovery sequence | Fixture | Remaining assumption | Builder disposition |
|---|---|---|---|---|---|---|---|
| **R10-1** upgrade entry | A P2 build cannot pass R-2/R-4 to reach `maintenance_begin_upgrade` (§4) | `SchemaMigrator` (BEHIND before validation); Flyway one transaction per migration | Compiled immutable manifest; predicate C (MIGRATE) in R-2M, R-3, R-4, `maintenance_begin`; per-checkpoint operations; unrecognised checkpoint GA003; old build refuses newer DB; newer build no general write on older DB | §5.10.1 trace steps 1–12 (V0026/P1 → V0030/P2, crash after V0028, resume through R-3, unknown V0029 COMMIT, publication, close) | AA-1d, AA-1e, AA-1f | R39-M (manifest generated from the migration directory; first statement check) | RESPONDED (design) |
| **R38-2** chain atomicity | Per migration, not chain; BEHIND is not validation (§8) | `SchemaMigrator.status`/`migrate` | `validateWithResult` before any control write; per-migration history interpretation | §5.10.1 step 1; §5.10.4 U-3 (history row) | AA-1e, AA-19b | R38-2 amended | RESPONDED |
| **R10-2** repair with OPEN run | R-2 requires no OPEN run (§7) | — | R-3L: rebind passing only the recorded old → new lease pair, generation g + 1 | §5.10.2 steps 1–7, step 6 "OPEN" row | AA-17b (ii) | — | RESPONDED (design) |
| **R10-2** DRAIN_PENDING | R-2 requires COMPLETE (§7) | — | R-3L without COMPLETE; transition continues at B6 | §5.10.2 step 6 "DRAIN_PENDING" row | AA-17b (iii) | — | RESPONDED (design) |
| **R10-2** present replacement | CREATE_NEW fails at an occupied path (§7) | — | Rename aside within the protected root without following links, recorded, never deleted or reused; only after process verification | §5.10.2 step 3 | AA-17b (iv) | — | RESPONDED (design) |
| **R10-2** binding transaction | Binding updated in a later transaction fails BC item 4 (§7) | — | Binding changes only in the opening (R-2L) or rebind (R-3L) transaction, with an `installation_binding_event` row (9th control table) | §5.10.2 step 6 | AA-17b (i)–(iii) | — | RESPONDED (design) |
| **R10-2** replacement as evidence | A replacement-file lock is not old-holder exclusion (§7, §13) | — | Exclusion only from process verification under the freeze (steps 2 and 5) | §5.10.2 steps 2, 5 | AA-17b (vii) | D-LP-8 (xi) | RESPONDED |
| **R10-2** unknown binding COMMIT | Undefined | — | U-1…U-4 with the attempted old/new identities; rerun of `repair-lease` adopts nothing on trust | §5.10.2 "Unknown binding COMMIT" | AA-17b (vi) | — | RESPONDED (design) |
| §7 new-host / cross-platform restore | BC 3/4 fail on new host; FK ordering for `superseded_by` (§7) | — | R-5H: exact old → new binding, caller's own values, Archive ID preserved, surviving-backend rule, deferred FK, mandatory `verify-restore`, re-audit | §5.10.3 steps 1–7 | AA-19e (extended) | operator statements (coherence, former-host exclusion) are responsibilities, not verifiable | RESPONDED (design) |
| §7 classification | Another configuration ID pointing at this database is not automatically unrelated (§7) | — | Unregistered application-role backends on this database refuse | §5.10.3 step 3 (a) | AA-17c (extended) | — | RESPONDED |
| §5 unknown outcomes | "Connection error ⇒ session gone" is false (§5, §12) | PostgreSQL administrative functions (signal is not termination) | U-1…U-5: attempted identity; confirmed backend absence; exact row read; reuse or refuse; old versus new controller identity; no obsolete generation | §5.10.4 | AA-1f, AA-17b (vi), AA-19b/d, AA-20b, AA-4c | — | RESPONDED (design) |
| §8 Archive ID seed | No seed for an existing archive (§8) | V0002 `archive_instance`; `init-archive` | V0026 copies the existing value with an ARCHIVE_ID_SEED event; CU-31 only for null | §5.10.5 table | AA-19b, AA-19d (extended) | — | RESPONDED (design) |
| **R10-3** H overstated | Absence of references and g ≤ current are not proof (§10) | V0014 Q immutability; `move` callers; `claimBecomesUnresolved`; V0009 event fields | RA, proven assignment (none claimed) and PC distinguished; W_Q, W_U; independent checks 1–7; UNPROVED refuses | §5.10.6 admission; sufficiency argument (P1)–(P4) | AA-11p…AA-11t; AA-11l, AA-11o | **R39-T** trust boundary; alternative D-LP-8 (xii) | RESPONDED (design); DECISION (owner) for R39-T |
| §9 per-Q dedup | Pending and cancellation roles produce two inserts (§9) | `applyCancel` pending loop | One window per Q key with roles; deterministic cancellation Q; PLAN_EXPANDED event | §5.10.7 | AA-8b | — | RESPONDED |
| §9 publication allocation | Allocation not durable (§9) | — | PUBLISH action allocates `(run, seq)` in its J0 before the effect; resolution by U-2/U-3; never inferred from digest or state | §5.10.7 | AA-4c, AA-5c | — | RESPONDED |
| R38-1 native identity | FFM is preview on Java 21; pom has no setup (§11) | `pom.xml` (Java 21, no preview, no JNA) | JNA 5.14.0, same descriptor/handle (OFD + `fstat`; `CreateFileW` + `LockFileEx` + `GetFileInformationByHandle`); fail closed | §5.10.8 | AA-17, AA-17d | R39-N1, R39-N2; dependency approval | RESPONDED (design); DECISION (owner) |
| R38-3 stalled I/O | Liveness versus safety; seam is not kernel I/O (§11, §13) | — | Lease held until real exit; refusal, not bounded availability | §5.10.8 | AA-20c split | R39-N2 | RESPONDED |
| R38-4, R38-5 scope | Reused destination not restamped; inventory is not lineage (§11) | `QuarantineMoves.move` | Scope corrected | §5.10.10 | — | — | RESPONDED |
| §15-1 | Upgrade entry | as R10-1 | | 5.10.1 | | | RESPONDED |
| §15-2 | Recovery transitions | as R10-2, restore | | 5.10.2, 5.10.3 | | | RESPONDED |
| §15-3 | Authoritative unknown outcomes | as §5 | | 5.10.4 | | | RESPONDED |
| §15-4 | Initialization / seed | as §8 | | 5.10.5 | | | RESPONDED |
| §15-5 | Historical proof | as R10-3 | | 5.10.6 | | | RESPONDED; DECISION (owner) |
| §15-6 | Journal alignment | as §9 | | 5.10.7 | | | RESPONDED |
| §15-7 | Runtime integration | as R38-1, R38-3 | | 5.10.8 | | | RESPONDED; DECISION (owner) |
| §15-8 | Tests | LP-17 3.9 amendments | | §10 | | | FUTURE |
| §15-9 | Approvals | §8 (II); none granted | | §8 | | | DECISION (owner) |

**Counts:** 40 application tables + **9** control tables (adds `installation_binding_event`) = **49** gated.
Control units 34 → **36** (CU-34 manifest and MIGRATE admission; CU-35 binding-event recording). Business windows
unchanged at 19 / 22.

**Status:** revision 3.9 submitted. Nothing implemented; no new execution. F5-4 is not claimed closed; the design
remains conditional on the unapproved Q-36-1 and D-LP-8 (xi), and now on D-LP-8 (xii) and the native-lease
dependency. WP1-R1 and GAS-P4-002…GAS-P4-005 remain OPEN. Awaiting the eleventh independent assessment. WP1 is not
accepted; WP2 remains on HOLD.

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
