# Phase 4 WP1 Architecture C: Draft 5 verification response (D5-N1, D5-N2)

| | |
|---|---|
| Verification | [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT5-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT5-VERIFICATION.md) (Codex; verified `519ce68`; ACCEPT WITH SPECIFIED CORRECTIONS; 0 BLOCKING, 0 MAJOR, 2 MINOR) |
| Baseline | `2dd6250c206c4432554080163b511fe5ac2989c3` |
| Revised contract | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), **Draft 6**. Changes are tagged **[G6-D5N1]** and **[G6-D5N2]**. |
| Status | Documentation only. DRAFT — NOT ACCEPTED. No protocol change; nothing implemented or executed; no finding closed. |

## 1. D5-N1: §12.1 test reachability

**Contract section:** §12.1, rewritten.

| Assignment requirement | Where |
|---|---|
| **Variant A**, actual post-reclaim overlap | §12.1 Variant A, steps A-1 to A-10 |
| Two RECEIVING REPAIRs on the same content | A-1 |
| The surviving renewal held at a named barrier **before** any row is locked | A-2 |
| Client exit confirmed while the exact backend stays alive; an ended backend makes the run invalid | A-3 |
| Both reclaims committed | A-4 |
| Second barrier **after** B is locked, before the transaction ends | A-5 |
| Replacement prefix observed holding A and blocked on B | A-6 |
| The renewal never requests A | A-7 |
| Success (release barrier 2) and timeout (hold through the bounded wait; rollback; no partial effects) | A-8, A-9 |
| **Variant B**, busy reclaim and refusal, kept separate | Variant B, B-1 to B-3: 55P03, whole-attempt rollback, unchanged interval, no replacement effect; a separately recorded fresh attempt after the holder ends |
| **Variant C**, characterisation of the superseded multi-row renewal | Variant C, C-1 to C-3: labelled negative control only; deterministic B-before-A and an opposing wait graph if reproduction is claimed; an uncontrolled `ANY` scan is not evidence; "not reproduced" reported explicitly; never counts as validation |
| Common rules for all variants | §12.1 common rules: real PostgreSQL; named-backend `pg_locks` and `pg_blocking_pids`; barriers that establish lock state (no timed sleeps); retries disabled, with attempts enumerated; explicit SQLSTATEs; zero 40P01 on corrected paths; distinct success, refusal and timeout; rollback and no-partial-effect checks; slow-upload and failed-STAGED-CAS regressions preserved |

These tests are specified only. None has been executed.

## 2. D5-N2: T-m timeout language

**Contract section:** trace T-m (§5.4 traces).

The claim "delays the accepted timeout by at most one lease period" is **removed**. T-m now states:
- a surviving single-row renewal statement may update `receiver_alive_at` **once**;
- the value is the time at which its `clock_timestamp()` expression is evaluated;
- the accepted stale predicate decides eligibility from the stored progress and renewal timestamps;
- **no fixed elapsed-time bound** from process death, or from the previously expected expiry, is established;
- renewal changes neither token ownership nor EDT authority.

No expiry mechanism, timer, lease policy or owner decision is introduced.

## 3. Requested answers

- **Changed files:**
  - the boundary contract (Draft 6);
  - this document;
  - a superseding note in the Draft 5 response, which otherwise described the unreachable combined schedule and the
    removed bound.
- **Residual architectural contradiction:** none known. The protocol is unchanged. Single-row outermost renewal,
  E-1/E-2, the UUID-ordered O prefix, File and content coordination, diagnostic-only labels, the offline drain,
  historical classification, D4-32, and the closed V2, V3, V4 and D4-N1 corrections are all as in Draft 5.
- **Is the owner contract-acceptance gate supportable?** The Draft 5 verification said the design is coherent enough
  for owner contract-acceptance consideration once these two corrections are recorded. **Subject to independent
  verification of this draft, the builder believes the gate is now supportable.** That would be acceptance of the
  architectural contract only, separate from implementation readiness, the feasibility proofs and the open decisions.
- **Remaining obligations** (unchanged):
  - **Feasibility:** P1, P2 and P3; Proof 1; Proof 2 (a)–(q); §12.1 variants A to C.
  - **Inventory:** the complete EDT and O-writer inventory, with implicit FK, uniqueness and trigger effects, event
    `seq` allocation, note call sites, the private DESTROY insertion and the window reorderings (§5.4.2).
  - **Enforcement:** outermost T-1, T-4 and T-6 despite the ordinary Spring `TransactionTemplate`.
  - **Renewal performance:** per-operation renewal must keep up with the supported workload without batching locks
    again.
  - **Other:** B-9 grouping current-run revalidation; §5.8 integrity and recovery compatibility; the privilege
    inventory (OD-6); realistic inherited fixtures; the full Phase 0–3 and Phase 4 regressions on Windows and Linux ×
    PostgreSQL 16 and 18.
  - **Decisions:** OD-1…OD-6 and OD-8.

WP1-R1, F5-4 and GAS-P4-002…GAS-P4-005 remain OPEN. WP1 remains unaccepted. Implementation is unauthorized. WP2
remains on HOLD.
