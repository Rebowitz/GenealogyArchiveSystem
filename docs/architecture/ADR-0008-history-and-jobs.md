# ADR-0008: Change history, field classes, optimistic concurrency and background jobs

Status: Accepted for Phase 1 implementation (owner decisions P1-A, P1-E). Phase 1.

## Field classes (Immutable / Correctable / Interpretive)
Each recorded field is classified (`history.FieldClass`). **Immutable** fields may be set once and never
changed — enforced both in Java (`FieldChange`) and by database triggers (`reject_identity_change`).
**Correctable** fields may be corrected with the change recorded. **Interpretive** is reserved for
research conclusions in later phases. Classes are properties of typed columns in typed tables; there is no
universal entity table and no EAV.

## Change history
- `gas.change_history` is append-only (update, delete and truncate are rejected by triggers). It records
  *who* (USER with account id, or SYSTEM with a label), *when*, *what object* (type + UUID), *which kind*
  of change, the changed fields with old/new values, and an **optional** reason. No explanation is ever
  required.
- It is written in the **same transaction** as the change, so a rolled-back change leaves no history.
  Reads write nothing; a save with no changes records nothing. Secrets (password hashes) are never recorded
  (`PASSWORD_CHANGED`/`PASSWORD_RESET` record only the event).
- It is **not** surveillance (no page views, searches or sign-in tracking), **not** a diagnostic log, and
  **not** the future Archival Operation History.

## Optimistic concurrency
Mutable rows carry `row_version`. Updates use `WHERE row_version = :expected`; a mismatch raises
`ConflictException` and nothing is overwritten. Browser forms carry the version they were rendered with.

## Background jobs
- `gas.background_job` is a lightweight persistent PostgreSQL job queue (BUILD-SPEC s.47): claimed with
  `FOR UPDATE SKIP LOCKED`, retried with exponential backoff (10 s · 2^n, capped at 1 h) up to
  `max_attempts`, then **FAILED visibly** with a bounded error; an Administrator may retry a failed job.
- **Lease ownership (correction GAS-P1-001, V0008)**: each claim issues a fresh `lease_token` for that
  attempt, and the claim result is that attempt's own row snapshot from the claiming
  `UPDATE ... RETURNING` (GAS-P1-R01) — never a separate re-read. A claim delivered after its local lease
  end does not start its handler. While the handler runs, a separate renewal thread in the owning worker extends the lease
  every third of its duration (production lease 5 min), so a healthy worker keeps a long-running job and
  no competing worker can reclaim it. `renew`, `succeed` and `fail` require the current token **and** an
  unexpired lease; lease times use the database clock (`clock_timestamp()`). If renewals stop (crash,
  kill, lost database connection) the lease expires and another worker reclaims the job with a new
  token; the stale attempt can then neither renew nor record an outcome, and its handler sees
  `JobExecution.leaseHeld() == false` (handlers call `requireLease()` before consequential side
  effects). Jobs with no attempts left are failed only when their lease has genuinely expired.
  A genuine crash may replay a job; Phase 1 promises no universal exactly-once side effects.
- `gas.worker_presence` lets the status view show whether a worker is alive.
- Phase 1 registers **no production job kinds**; unknown kinds wait QUEUED and are counted.
- The job table is operational plumbing. It is **not** the complete Archival Operation History (P1-E);
  that is designed in a later phase and may reference jobs, but not the reverse.
