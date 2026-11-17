# Genealogy Archive System
## Engineering Design Instructions — Architectural Oversight and Complexity Control

**Status:** Owner-approved governing project methodology amendment  
**Adopted:** 2026-11-17  
**Applies to:** All current and future development phases, architecture proposals, implementation plans, corrections, and independent reviews.

These instructions supplement, and do not supersede, archival invariants, accepted specifications, or explicit owner decisions. They do not authorize implementation or relax acceptance criteria.

### 1. Primary engineering objective

Build a reliable, maintainable, auditable Genealogy Archive System that satisfies approved requirements. Passing the next independent review is not the objective.

Prioritize (1) correctness and archival preservation, (2) simplicity consistent with safety, (3) maintainability and operational clarity, (4) executable verification, and (5) engineering quality per dollar. Speed is secondary; there is no deadline requiring additional paid AI usage.

### 2. Mandatory architectural simplicity review

Every substantial proposal must identify:
- Minimum mechanisms needed for approved requirements;
- Existing platform capabilities that provide the same protections;
- Materially simpler alternatives and their tradeoffs;
- Operational assumptions that could reduce complexity;
- Exceptional and recovery-path complexity;
- The requirement and failure mode justifying each additional persistent state, control table, transaction, dependency, or recovery mechanism.

Do not add machinery merely to resolve one counterexample without examining system-wide interactions. Prefer the simplest design **demonstrably** satisfying all approved requirements. Never waive essential integrity, security, or recovery requirements for simplicity.

### 3. Mandatory architectural convergence monitoring

Maintain a review history for each work package. After every independent assessment, assess whether:
- Earlier structural contradictions are actually resolved;
- Corrections avoid repeatedly introducing new structural contradictions;
- Interacting states, exceptions, and coupling are stabilizing;
- The design remains understandable, implementable, and testable; and
- Implementation readiness is measurably improving.

A declining finding count alone does not prove convergence.

**Mandatory escalation triggers:**
1. **Two consecutive reviews identifying new structural contradictions:** perform and record a complexity/trend assessment before another revision.
2. **Three consecutive reviews without demonstrated convergence:** suspend incremental architecture revisions and require an alternative-design assessment.
3. **Any correction introducing substantial new machinery:** justify it against a simpler alternative before incorporation.

These are escalation triggers, not permission to accept unresolved defects.

### 4. Mandatory alternative-design assessment

When a trigger occurs, compare at least:
- **A — Corrected current architecture:** preserve the existing design and resolve its findings.
- **B — Simplified alternative:** start from approved requirements and accepted code; propose the least-complex architecture meeting the same obligations.

Compare requirement coverage, archival integrity, concurrency and transaction safety, crash and unknown-COMMIT recovery, authorization, deployment and maintenance, Windows/Linux behavior, implementation and regression-test burden, long-term maintainability, dependencies, and operational restrictions. Identify requirements the simpler design cannot meet. Never silently remove approved requirements.

### 5. Scope and responsibility separation

Separate ordinary operations, maintenance, migration, and disaster restoration where practicable. Do not burden ordinary startup with rare exceptional recovery states without a demonstrated safety need. Prefer explicit, controlled, fail-closed administrative recovery when safer and simpler than complex automatic recovery. Do not assume all recovery must be automatic.

### 6. Historical data and proof limits

Before asserting an invariant or historical safety theorem, establish that accepted data records the facts necessary to prove it. Explicitly distinguish recorded facts, derivable conclusions, historical operational assumptions, owner-approved trust policies, and unreconstructable facts.

Do not repeatedly redesign the system to prove historical facts that were never recorded. Escalate missing provenance and present bounded alternatives. Never manufacture retrospective evidence or weaken accepted archival evidence.

### 7. Claude Code — primary builder

Claude Code must propose the simplest viable design before elaborating exceptional cases; identify alternatives and tradeoffs; maintain a complexity/convergence record; report increased coupling and recovery complexity; map each mechanism to requirements; stop incremental expansion at escalation triggers; and request architectural reconsideration instead of indefinite patching.

Satisfying the latest reviewer findings alone is not proof of architectural quality.

### 8. Codex — independent reviewer

Every substantial architectural assessment must independently answer:
1. Does the design satisfy approved requirements?
2. Are its mechanisms necessary?
3. Is a materially simpler design available?
4. Have corrections introduced disproportionate complexity?
5. Is the architecture converging?
6. Should incremental revision continue, or is reconsideration required?

Identify unnecessary complexity even when individual mechanisms are technically correct. Do not limit review to defects inside the builder's selected design.

### 9. ChatGPT — engineering oversight

Independently monitor the cumulative architectural trend, detect repeated structural contradictions, challenge unnecessary complexity, recognize diminishing returns, distinguish executable progress from documentation growth, and alert the owner before review cycles accumulate.

Do not automatically recommend another builder/reviewer iteration because the latest findings appear correctable. **Failure to converge is itself an engineering issue requiring escalation.**

### 10. Architectural review decision gate

Record one supported disposition after each review:
- **CONTINUE:** converging; bounded corrections appropriate.
- **SIMPLIFY:** unnecessary complexity; targeted simplification required.
- **RECONSIDER:** structural instability/nonconvergence; compare alternative designs before further incremental revisions.
- **READY FOR IMPLEMENTATION REVIEW:** sufficiently stable to consider implementation authorization, subject to owner decisions and acceptance requirements.

Builder and reviewer recommend dispositions; only the owner approves changes to governing requirements and implementation authorization.

### 11. Immediate Phase 4 WP1 application

The Phase 4 WP1 locking-protocol sequence has exceeded convergence triggers. It therefore reached gate **RECONSIDER**, which required the following:

- Suspend routine incremental revision of the comprehensive locking proposal.
- Preserve the proposal, source-derived counterexamples, and independent assessments as evidence.
- Compare the corrected comprehensive protocol with a minimal safe alternative derived from approved requirements and accepted Phase 0–3 code.
- Preserve all approved integrity requirements and accepted Phase 0–3 behavior.
- Do not implement, merge, or tag WP1 without the owner's subsequent approval of the selected architecture.
- **WP1 remains unaccepted; WP2 remains HOLD.**
- Eleventh-assessment findings remain inputs to the comparison; reconsideration does not erase them.

**Outcome of the reconsideration (2026-11-17).** The required comparison (`PHASE-4-WP1-ARCHITECTURAL-SIMPLIFICATION-ASSESSMENT.md`) led to the owner's decision D4-31 in `PHASE-4-DECISIONS.md`. The decision is the result of this governance process and does not cancel it; §3's triggers and §4's assessment continue to apply to the replacement design.
- **Architecture C: SIMPLIFY — OWNER APPROVED.** This is the current WP1 gate.
- Replacement WP1 design (architectural contract) and feasibility proofs: **pending**.
- Existing WP1 work: unchanged and **unaccepted**.
- Implementation of the Architecture C design: **not authorized**. Architecture approval is not implementation authorization.
- **WP2 remains HOLD.**

### 12. Governance

These instructions govern Claude Code build assignments, Codex independent reviews, and ChatGPT engineering oversight. They supplement approved project specifications and do not waive testing, alter existing owner decisions, or grant implementation authority. Any exception requires explicit owner justification and approval.

**Governing principle:** Architectural complexity must be justified by requirements, not by the history of previous design corrections.
