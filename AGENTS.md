# Genealogy Archive System — Codex Repository Instructions

## Project identity and governing requirements
This repository is the **Genealogy Archive System**, a genealogy-first personal archival system. Do not rename or generalize it. Read `CLAUDE.md` for the project identity, accepted architectural boundaries, archival invariants, phase authority order, and Windows/Linux constraints. Accepted specifications and explicit owner decisions take precedence over implementation convenience.

## Mandatory engineering design governance
Before conducting an architectural review, proposing a design, evaluating a correction, or recommending implementation authorization, **read and follow `docs/ENGINEERING-DESIGN-INSTRUCTIONS.md`** (owner-approved 2026-11-17). This is a governing project-methodology document, not optional background reading.

Independently evaluate **necessity and simplicity**, not only technical correctness. For every substantial architectural assessment, address:
1. Whether the design satisfies approved requirements.
2. Whether its mechanisms are necessary.
3. Whether a materially simpler design exists.
4. Whether corrections add disproportionate coupling or recovery complexity.
5. Whether the architecture is converging.
6. Whether incremental revision should continue or reconsideration is required.

Track consecutive structural findings and convergence. After **two consecutive reviews with new structural contradictions**, require a documented complexity/trend assessment. After **three consecutive reviews without convergence**, recommend suspending incremental revisions and comparing the corrected current architecture with a minimal safe alternative. Require a simpler-alternative justification for substantial new machinery.

Conclude architectural reviews with an evidence-supported gate: **CONTINUE**, **SIMPLIFY**, **RECONSIDER**, or **READY FOR IMPLEMENTATION REVIEW**. A gate recommendation does not constitute owner authorization.

## Mandatory pre-cycle review planning and acceptance gates
Before initiating an independent review, read and apply **§10A of `docs/ENGINEERING-DESIGN-INSTRUCTIONS.md`** (owner-approved, adopted 2026-11-18).
- Confirm the acceptance gate under review: A (architectural contract), B (feasibility) or C (implementation). Confirm the written review plan that names it.
- Review the complete acceptance-critical scope together. Classify every finding by severity (BLOCKING, MAJOR, MINOR), the gate it affects, and whether it blocks that gate, with evidence and closure criteria.
- Report deferred obligations separately from acceptance-blocking findings.
- Apply the stopping rule and the round-escalation points.
- Do not recommend another architectural revision for a nonblocking finding, or for an improvement that belongs to a later gate.

These rules supplement, and do not weaken, the convergence triggers and review-integrity requirements above and below.

## Current Phase 4 WP1 status
**WP1 direction: Architecture C (decision D4-31 in `docs/PHASE-4-DECISIONS.md`, 2026-11-17).** The RECONSIDER comparison required by `docs/ENGINEERING-DESIGN-INSTRUCTIONS.md` produced this decision; the governance rules, including reconsideration after repeated structural nonconvergence, remain in force.
- **Gate A:** the Architecture C boundary contract is ACCEPTED (D4-33, 2026-11-18).
- **Gate B plan:** `docs/PHASE-4-WP1-GATE-B-PLAN.md` Revision 6 is ACCEPTED as the governing planning baseline (D4-36, 2026-11-19). Independent Codex disposition: ACCEPT CORRECTIONS; GB-P1, GB-N1 and GB-N2 CLOSED. **D4-36 is the authoritative plan-acceptance record**, notwithstanding the accepted plan's unchanged DRAFT header.
- **D4-35** governs the F-R monotonic-clock (`System.nanoTime()`) measurement convention.
- **Gate B:** feasibility proofs NOT EXECUTED; Gate B NOT ACCEPTED.
- **Gate C:** implementation NOT AUTHORIZED. Existing WP1 implementation remains unaccepted.
- **WP1 unaccepted. WP2 HOLD.**

The prior locking-protocol proposal (revision 3.9) is superseded as the intended direction and retained as historical evidence; do not treat it as approved or initiate implementation, migrations, merge, tagging, or WP2 work absent explicit owner authorization. Preserve accepted Phase 0–3 behavior and the findings from the eleventh independent architectural assessment.

## Review integrity
Remain independent of the builder's assertions. Verify source and operative contracts, cite exact files and commits, distinguish design from executed test evidence, and report limitations. Do not edit auditor findings to make corrections appear closed. Do not weaken accepted archival or security requirements for simplicity.
