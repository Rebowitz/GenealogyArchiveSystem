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

Before preparing an architectural proposal, a correction or a response to an independent review, Claude Code also applies §10A:
- read the review plan and the gate's acceptance criteria;
- classify each finding by the gate it affects;
- separate required corrections from deferred work, and consolidate related corrections;
- apply the round-escalation points;
- do not revise the governing architecture merely to perfect later-gate implementation details;
- stop and request owner direction when the process requires reassessment.

### 8. Codex — independent reviewer

Every substantial architectural assessment must independently answer:
1. Does the design satisfy approved requirements?
2. Are its mechanisms necessary?
3. Is a materially simpler design available?
4. Have corrections introduced disproportionate complexity?
5. Is the architecture converging?
6. Should incremental revision continue, or is reconsideration required?

Identify unnecessary complexity even when individual mechanisms are technically correct. Do not limit review to defects inside the builder's selected design.

Before every independent review, Codex also applies §10A:
- confirm the gate and the written review plan;
- review the full acceptance-critical scope together;
- classify findings by severity, gate and blocking effect;
- report deferred obligations separately;
- apply the stopping and escalation criteria;
- do not recommend another architectural revision for a nonblocking finding.

Reviewer independence and integrity are unchanged.

### 9. ChatGPT — engineering oversight

Independently monitor the cumulative architectural trend, detect repeated structural contradictions, challenge unnecessary complexity, recognize diminishing returns, distinguish executable progress from documentation growth, and alert the owner before review cycles accumulate.

Do not automatically recommend another builder/reviewer iteration because the latest findings appear correctable. **Failure to converge is itself an engineering issue requiring escalation.**

When preparing assignments or evaluating review outcomes, ChatGPT must also apply §10A **proactively**:
- identify the current acceptance gate, and confirm a review plan exists before the first review;
- distinguish acceptance-blocking findings from deferred obligations;
- monitor cumulative review cost against engineering value, and recognize diminishing returns;
- challenge unnecessary additional rounds;
- recommend consolidation, deferral, correction, acceptance consideration or escalation, as the evidence justifies;
- alert the owner **before** repeated review cycles accumulate.

The project owner must not be expected to detect inefficient review cycling before the assistant reassesses the
process.

Repository governance is a durable reference; it does not enforce itself in any conversation. ChatGPT must actively
consult and apply it.

### 10. Architectural review decision gate

Record one supported disposition after each review:
- **CONTINUE:** converging; bounded corrections appropriate.
- **SIMPLIFY:** unnecessary complexity; targeted simplification required.
- **RECONSIDER:** structural instability/nonconvergence; compare alternative designs before further incremental revisions.
- **READY FOR IMPLEMENTATION REVIEW:** sufficiently stable to consider implementation authorization, subject to owner decisions and acceptance requirements.

Builder and reviewer recommend dispositions; only the owner approves changes to governing requirements and implementation authorization. Each disposition is recorded against the acceptance gate named in the review plan (§10A.2).

### 10A. Mandatory Pre-Cycle Review Planning and Acceptance Gates

**Status:** owner-approved, adopted 2026-11-18.
- It supplements §3 (convergence triggers), §4 (alternative designs), §8 (reviewer questions) and §10 (dispositions).
  It does not replace or weaken them.
- It does not change any technical acceptance criterion.
- It does not retroactively alter prior independent findings or owner decisions.
- It applies to all future work packages and phases.

#### 10A.1 Mandatory review plan

Before the **first** substantive independent architectural, feasibility or implementation-acceptance review of a work
package or artifact, a written review plan must exist. It is never reconstructed after several correction rounds. It
specifies:
- the artifact and its exact repository baseline;
- the review scope and explicit exclusions;
- the acceptance gate under review (§10A.2);
- the acceptance-critical requirements for that gate;
- the evidence required to pass the gate;
- the finding severity and materiality criteria (§10A.3), including which findings must block acceptance and which
  may be deferred to a later gate;
- the planned review sequence and review-round budget (§10A.5);
- explicit stopping (§10A.6) and escalation conditions;
- who decides whether another review is justified;
- the decisions reserved to the project owner.

The plan may be a short document or a section of the review assignment. It must be recorded before review begins.

A review-round budget is an **escalation trigger**, not a fixed ceiling on safety-critical review.

#### 10A.2 Three separate acceptance gates

| Gate | Question | Notes |
|---|---|---|
| **A — Architectural contract acceptance** | Does the architecture satisfy governing requirements, define mandatory behavior, stay internally consistent, and state sufficiently precise verification obligations? | It does not require executing future feasibility experiments, or building their test harnesses, unless an essential architectural claim cannot otherwise be evaluated. |
| **B — Feasibility acceptance** | Have the required technical assumptions and mechanisms been demonstrated through authorized proofs, experiments and platform-specific validation? | Unexecuted feasibility requirements remain open after Gate A. |
| **C — Implementation acceptance** | Do the implemented code, database behavior, filesystem operations, deployment and regression results conform to accepted requirements? | — |

**Passing one gate does not authorize or satisfy another.**

The §10 dispositions (CONTINUE, SIMPLIFY, RECONSIDER, READY FOR IMPLEMENTATION REVIEW) remain the per-review
recommendation. The review plan states which gate a disposition concerns. Only the owner grants acceptance or
authorization at any gate.

#### 10A.3 Finding classification and materiality

Every independent finding states:
1. its severity: BLOCKING, MAJOR or MINOR;
2. the acceptance gate it affects;
3. whether it blocks that gate;
4. the evidence supporting the classification;
5. the required correction, or the deferred obligation;
6. its closure criteria.

**Blocking rules:**
- BLOCKING and MAJOR defects that affect mandatory correctness, archival preservation, security, safety or
  architectural consistency must be resolved before the affected gate can pass.
- MINOR findings do not automatically block acceptance.
- A MINOR finding **does** block the current gate when it materially prevents safe acceptance, implementation,
  understanding or verification of mandatory behavior.
- Nonblocking findings are recorded with their required resolution stage and closure criteria.

Genuine defects are never reclassified or suppressed to reduce review cycles.

#### 10A.4 Consolidated independent review

Reviewers examine the complete acceptance-critical scope of the planned gate, and report related defects together
wherever reasonably possible. They distinguish:
- architectural defects;
- missing owner decisions;
- unproven feasibility assumptions;
- implementation obligations;
- test-harness construction details;
- editorial or explanatory improvements.

A future test-harness implementation detail is not automatically an architectural defect. A fundamentally
contradictory or unverifiable test obligation may still block Gate A.

#### 10A.5 Review-round budget and escalation

| Point | Required action |
|---|---|
| Before the first review | The review plan, with its acceptance and stopping criteria, is approved (§10A.1). |
| After the first review | All findings are evaluated against the planned gate (§10A.3) **before** corrections are assigned. Blocking corrections are consolidated, and deferred items are recorded. |
| After the second correction round | Record an explicit assessment of consolidation opportunities, the remaining acceptance-critical defects, and whether another round is justified. |
| Before a third correction round | Record a process-efficiency reassessment, and notify the owner why another cycle is necessary. |
| Any later round | Requires explicit justification tied to unresolved acceptance-critical issues. Rounds never continue automatically. |

These process controls **supplement** §3's structural-convergence triggers (two consecutive reviews with new
structural contradictions; three without demonstrated convergence; substantial new machinery). They do not replace
or weaken them. **An unresolved safety-critical defect always takes precedence over the planned budget.**

#### 10A.6 Stopping rule

A review cycle concludes when:
- every acceptance-critical finding for the current gate is resolved;
- no known unresolved finding materially prevents that gate's acceptance;
- deferred obligations are explicitly recorded, with their resolution stage;
- required owner decisions are identified;
- the reviewer gives an evidence-supported disposition.

Another architectural revision is not required solely because a reviewer identifies an improvement that belongs to a
later gate. A review is not stopped merely because a numerical round count has been reached.

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

These instructions govern Claude Code build assignments, Codex independent reviews, and ChatGPT engineering oversight. They supplement approved project specifications and do not waive testing, alter existing owner decisions, or grant implementation authority. §10A adds process controls only, with no rigid maximum number of reviews and no waiver of unresolved critical defects. Any exception requires explicit owner justification and approval.

**Governing principle:** Architectural complexity must be justified by requirements, not by the history of previous design corrections.
