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

## Current Phase 4 WP1 status
**WP1 gate: RECONSIDER. WP1 unaccepted. WP2 HOLD.** Do not treat the prior locking-protocol proposal as approved or initiate implementation, migrations, merge, tagging, or WP2 work absent explicit owner authorization. Preserve accepted Phase 0–3 behavior and the findings from the eleventh independent architectural assessment. The next architectural work is a comparison of the comprehensive protocol and a minimal safe alternative against the same approved requirements.

## Review integrity
Remain independent of the builder's assertions. Verify source and operative contracts, cite exact files and commits, distinguish design from executed test evidence, and report limitations. Do not edit auditor findings to make corrections appear closed. Do not weaken accepted archival or security requirements for simplicity.
