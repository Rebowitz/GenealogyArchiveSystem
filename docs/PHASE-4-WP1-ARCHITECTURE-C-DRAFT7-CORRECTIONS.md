# Phase 4 WP1 Architecture C: Draft 6 verification response (D6-N1)

| | |
|---|---|
| Verification | [`PHASE-4-WP1-ARCHITECTURE-C-DRAFT6-VERIFICATION.md`](PHASE-4-WP1-ARCHITECTURE-C-DRAFT6-VERIFICATION.md) (Codex; verified `b04c6e2`; ACCEPT WITH SPECIFIED CORRECTIONS; 0 BLOCKING, 0 MAJOR, 1 MINOR) |
| Baseline | `68c91b38b616a79706d01607d3d6081b6f1dea5b` |
| Revised contract | [`PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md`](PHASE-4-WP1-ARCHITECTURE-C-BOUNDARY-CONTRACT.md), **Draft 7**. Changes are tagged **[G7-D6N1]**. Only §12.1 and its references change. |
| Status | Documentation only. DRAFT — NOT ACCEPTED. No protocol change; no harness built; no test run; no finding closed. |

## 1. D6-N1 mapping (contract §12.1)

| Requirement (assignment §2 and §3) | Revised text |
|---|---|
| Replace tuple-entry presence or absence assertions with reliable, corroborated observations | Common rules, **"Observation evidence rule"**: tuple locks are normally not in `pg_locks`; nothing rests on tuple entries or on a finite series of snapshots. Evidence is (a) verified hook placement on the exact backend, (b) a recorded per-operation statement and acquisition timeline, and (c) outcomes of known single-row statements. `pg_blocking_pids` and transaction-ID and advisory waits are corroboration only. Hooks are restricted to the intended backend and statement, so reclaims are never stopped by them. |
| A: barrier 1's verified pre-acquisition placement establishes the pre-lock phase; committed reclaims of A and B corroborate it | A-2 |
| A: barrier 2 verified after R acquires B and before its transaction ends | A-5 (the hook records the operation ID it fired for) |
| A: B-held corroborated by the replacement's B-only statement blocked by R | A-5 and A-6 |
| A: replacement A-held from its successful A acquisition in the still-open transaction before requesting B | A-6 |
| A: R never requests A, from the captured single-operation statement, the audited hook path and instrumentation | A-7 |
| A: no "never locked" claim from absent entries or snapshots | Common rule; A-7 |
| B: keep NOWAIT refusal, 55P03, rollback, unchanged interval, no replacement effect; reliable holder evidence; E-1 established first | B-1 (E-1 confirmed exited so T-6 reaches NOWAIT; the holder is established by hook placement and operation ID, corroborated by 55P03); B-2 and B-3 unchanged |
| C: deterministic per-operation instrumentation and observed opposing named-backend waits; "not reproduced" otherwise; never proof of the corrected path | C-2 and C-3 |
| Rollback checks concern the rolled-back transaction's own effects, not an independently committed renewal | Common rule "Rollbacks"; A-9 |
| Preserved | PostgreSQL 16 and 18; Windows and Linux; exact backend identity and barrier placement; retries disabled; explicit SQLSTATEs; zero 40P01 on corrected paths; distinct success, refusal and timeout; slow-upload and failed-STAGED-CAS regressions. All unchanged. |

## 2. Requested answers

- **Changed files:**
  - the boundary contract (Draft 7);
  - this document;
  - a superseding note in the Draft 6 response, about its `pg_locks` observation wording.
- **Residual contradictions:** none known. The protocol is unchanged: single-operation renewal, E-1/E-2, the ordered
  operation-row prefix, complete-set rollback and rediscovery, retained File and content coordination, the offline
  exclusion, historical admission and D4-32. D4-N1, D5-N2, V2, V3 and V4 are not reopened.
- **Ready for owner acceptance consideration?** The Draft 6 verification stated that once this observation amendment
  is made, the design is suitable for owner contract-acceptance consideration, independently of implementation
  readiness and the unexecuted proofs. **Subject to narrow independent verification of D6-N1, the builder believes
  the contract is ready for that consideration.** Acceptance would cover the architectural contract only.
- **Remaining, separately gated obligations:**
  - **Feasibility:** P1, P2 and P3; Proof 1; Proof 2 (a)–(q); building and validating the §12.1 hooks and harness,
    including preserving a server statement after client death; §12.1 variants A to C.
  - **Inventory:** the complete EDT, O-writer and implicit-lock inventory and the window reorderings (§5.4.2).
  - **Enforcement:** outermost T-1, T-4 and T-6.
  - **Renewal performance.**
  - **Other:** B-9 grouping current-run revalidation; §5.8 integrity and recovery compatibility; the privilege
    inventory and deployment (OD-6); realistic inherited fixtures; the full Phase 0–3 and Phase 4 regressions on
    Windows and Linux × PostgreSQL 16 and 18.
  - **Decisions:** OD-1…OD-6 and OD-8.

WP1-R1, F5-4 and GAS-P4-002…GAS-P4-005 remain OPEN. WP1 remains unaccepted. Implementation is unauthorized. WP2
remains on HOLD.
