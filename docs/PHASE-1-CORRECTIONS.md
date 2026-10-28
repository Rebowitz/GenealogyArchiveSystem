# Phase 1 corrections — GAS-P1-001, GAS-P1-002, GAS-P1-R01 and evidence strengthening

Response to the independent review of Phase 1 head `70c3b954437647f736273bee83f82fadc539b018`.
Scope is limited to the two MAJOR findings and the two authorized test-strengthening items.
**Phase 1 is not accepted; PR #3 remains open and unmerged; Phase 2 was not started.**

| | |
|---|---|
| Reviewed head | `70c3b954437647f736273bee83f82fadc539b018` |
| Corrected code head | `639d29da3a2ad84aed61b1db82e16c3beb2e079b` (CI run 40766776536) |
| Later commits | documentation only; CI re-run on the final head is reported in PR #3 |

## GAS-P1-001 — live job could be reclaimed while its handler was still executing

**Cause.** A claim took a fixed lease; nothing extended it while the handler ran, so after the lease
interval another worker could reclaim a job that a healthy worker was still executing. Completion was
checked only against the worker ID.

**Correction.**
- `V0008__job_lease_token.sql`: `background_job.lease_token` — a fresh UUID per claim (per attempt), required
  while RUNNING (`CHECK`). Jobs already RUNNING at upgrade receive a token nobody holds and are recovered by
  expiry as before.
- `JobRunner`: while a handler runs, a dedicated renewal thread renews the lease every `renewInterval`
  (production: one third of the 5-minute lease). A healthy worker therefore keeps ownership for as long as
  the handler runs; competing workers cannot reclaim it.
- `BackgroundJobRepository.renew/succeed/fail` require **the attempt's token and an unexpired lease**,
  compared on the database clock (`clock_timestamp()`). A refused renewal means ownership is lost
  permanently for that attempt (tokens are never reissued). A stale attempt can neither renew nor record
  success or failure after another worker has taken over.
- `JobExecution` (passed to `JobHandler.run`) exposes `leaseHeld()` / `requireLease()`. Ownership lapses
  locally when a renewal is refused, or when the locally known lease end passes without a successful
  renewal (e.g. the database is unreachable). The runner records no outcome for an attempt that has lost
  its lease.
- Crash recovery unchanged: if renewals stop (process killed, host lost, DB connection lost) the lease
  expires and the job is reclaimed with a new token; genuine crashes may replay a job (no universal
  exactly-once promise).
- Exhausted attempts: `failExpiredWithoutAttemptsLeft` acts only on genuinely expired leases, so a live job
  running past its original lease interval is never failed as exhausted.
- Worker shutdown: the worker loop closes the runner (renewal thread) after its last job returns.
- Not done: the lease was not merely lengthened; crash recovery was not disabled.

**Tests added** (`BackgroundJobIT`, real PostgreSQL, 2 s lease, 250 ms renewal):
| Test | Establishes |
|---|---|
| `leaseIsRenewedWhileAHealthyWorkerRunsAHandlerLongerThanTheLease` | handler blocked for 2× the lease; `lease_until` advanced beyond the original lease end; handler saw `leaseHeld()`; job SUCCEEDED with 1 attempt |
| `competingWorkerDoesNotClaimOrRunAJobWhoseOwnerIsRenewingItsLease` | a second runner polling for 3 lease intervals claims nothing; exactly one handler execution, max concurrency 1 |
| `jobAbandonedByAWorkerThatStoppedRenewingIsRecoveredAfterTheShortLeaseExpires` | unrenewed lease is not reclaimed early, is recovered after real expiry, abandoned token cannot complete |
| `afterOwnershipTransfersTheStaleAttemptCanNeitherRenewNorRecordAnOutcome` | after transfer, stale `renew`, `succeed`, `fail` all refused; new owner's token and state intact; new owner can renew and complete |
| `workerThatStopsRenewingLosesTheJobObservesTheLossAndRecordsNoOutcome` | a stalled (non-renewing) worker's lease expires, another worker runs the job, the stale handler sees `leaseHeld()==false`, its thrown failure is not recorded |
| `exhaustedAttemptsFailAnAbandonedJobButNotALiveOneRunningPastTheOriginalLease` | with `max_attempts=1`, a live job running 3 lease intervals stays RUNNING then SUCCEEDS while the expiry sweep runs; an abandoned one is FAILED visibly |

Also: `JobRunnerTest.executionLeaseLapsesLocallyWhenNotRenewedInTime`,
`lostOwnershipIsNeverRegainedByTheSameAttempt`, `leaseAndRenewalIntervalMustBePositive`;
`Phase1SchemaIT.jobRunningBeforeTheLeaseTokenMigrationGetsAnUnheldTokenAndRemainsRecoverable`.
The existing `jobOfAWorkerThatStoppedIsRecoveredAfterItsLeaseExpires` now checks the stale *token* (the
worker-ID completion API no longer exists); its assertions are otherwise unchanged.
`Phase1SchemaIT.acceptedPhase0DatabaseUpgradesCleanlyToPhase1` now expects 7 Phase 1 migrations (V0002–V0008).

## GAS-P1-002 — non-loopback password authentication lacked an enforced transport boundary

**Correction** (`config/TransportSecurity`, `web/TransportSecurityFilter`):
- **Deployment rule, fail-safe.** Loopback binds (`127.0.0.0/8`, `::1`, `localhost`) are accepted. Any other
  bind — LAN address, host name, wildcard `0.0.0.0` / `::`, or no address — is refused unless
  `server.tlsReverseProxy.addresses` declares the proxy. Refusal is enforced by `check-config` (exit 2), by
  `serve` before anything listens (exit 2), and again inside the web application context from the
  *effective* `server.address`, so an override cannot bypass it.
- **Explicit, validated proxy trust.** `server.tlsReverseProxy.addresses` takes exact IPv4/IPv6 literals
  only — host names, ranges/CIDR, wildcard, multicast and zone IDs are configuration errors; no DNS is used.
  Unknown keys are rejected.
- **Request rule.** Before sign-in or sessions, a request is accepted only (a) directly from a loopback
  peer that is not a declared proxy, or (b) from a declared proxy peer carrying exactly one
  `X-Forwarded-Proto: https`. Forwarding headers from any other peer are ignored. Only
  `GET /actuator/health/**` (no credentials, no archive data) is exempt. The container's own
  forwarded-header processing is disabled (`server.forward-headers-strategy=none`), so the peer address
  cannot be rewritten by a client.
- Behind a declared proxy, the session cookie is marked `Secure`.
- Error messages name the setting and value only; tests assert that the database password never appears.
- No built-in TLS and no general Phase 9 hardening were added.

**Tests added:**
| Test class | Covers |
|---|---|
| `TransportSecurityTest` (unit) | loopback IPv4/IPv6/`localhost` accepted; `0.0.0.0`, `::`, `[::]`, LAN IPv4/IPv6, link-local, host names, absent address refused; network bind accepted with declared proxy; proxy addresses must be unicast IP literals; spoofed `X-Forwarded-Proto` from undeclared IPv4/IPv6 peers refused; declared proxy must send exactly one `https`; loopback proxy means local traffic must also come through it |
| `TlsReverseProxyConfigTest` (unit) | proxy declaration loaded; invalid declarations (host name, CIDR, empty, non-list, unknown key) are configuration errors whose messages contain no secret |
| `CliTransportSecurityTest` (unit) | `check-config` exit 2 for `0.0.0.0` and `::`; `serve` exit 2 for a LAN address before any listener/DB; loopback IPv4/IPv6 accepted; network bind with proxy accepted; no secret in output |
| `TransportSecurityFilterTest` (unit, servlet doubles) | spoofed header from undeclared IPv4/IPv6 → 403; declared proxy + https → passes; declared proxy without https → 403; loopback passes; only health GET exempt |
| `TransportSecurityIT` (real web app) | `0.0.0.0` and `::` refused at start-up (no secret in message); `server.address` override cannot bypass; `::1` binding serves sign-in; declared proxy: direct and `http` refused, sign-in via `https` succeeds with `Secure` cookie, signed-in session unusable outside the proxy; **spoofed** `X-Forwarded-Proto`/`X-Forwarded-For`/`Forwarded` from this host's real non-loopback address → 403 for GET and POST `/login`, health probe still reachable |

## Evidence strengthening
| Test | Establishes |
|---|---|
| `ArchiveInitializationConcurrencyIT.concurrentInitializersSerializeOnTheLockAndExactlyOneArchiveIdentityIsCreated` | a gate transaction holds the initialization lock; 6 `init` calls are observed *waiting on that lock in `pg_locks`*; nothing exists meanwhile; on release exactly one succeeds, 5 are refused naming the winner's Archive ID; one row; one CREATED history record |
| `ChangeHistoryAtomicityIT.archiveRenameRollsBackWhenItsHistoryCannotBeRecorded` | history insert made to fail (test-only trigger) → rename fails, name and `row_version` unchanged, no history written |
| `ChangeHistoryAtomicityIT.accountCreationRollsBackWhenItsHistoryCannotBeRecorded` | no account row remains |
| `ChangeHistoryAtomicityIT.storageVolumeRegistrationRollsBackWhenItsHistoryCannotBeRecorded` | no volume row remains |

No defect was exposed; Change History was not redesigned.

## CI — corrected code head `639d29d`, run 40766776536
| Job | Tests | Failures | Errors | Skipped | Smoke | Native service |
|---|---|---|---|---|---|---|
| ubuntu-24.04 / PostgreSQL 16 | 337 | 0 | 0 | 0 | passed | systemd passed |
| ubuntu-24.04 / PostgreSQL 18 | 337 | 0 | 0 | 0 | passed | systemd passed |
| windows-2025 / PostgreSQL 16 | 337 | 0 | 0 | 0 | passed | WinSW passed |
| windows-2025 / PostgreSQL 18 | 337 | 0 | 0 | 0 | passed | WinSW passed |

268 → 337 tests per job (+69, matching the tests listed above). No existing assertion was removed or weakened.

## Remaining limitations relevant to these corrections
- A handler that hangs forever keeps its lease while its worker is alive (by design: ownership follows
  worker health, not elapsed time); it is visible as RUNNING on the Operations page.
- Phase 1 still has no built-in TLS; network access requires an administrator-run TLS reverse proxy.
- Proxy trust is by exact peer IP; a proxy on a shared host trusts everything on that host's address.

---

# Targeted correction GAS-P1-R01 — claim result not atomically bound to its attempt

Re-review of `3b698a95a136dccfd5be50fdc5ec94de86008886`: GAS-P1-001 partially resolved; GAS-P1-002,
both evidence items and CI passed. New MAJOR finding GAS-P1-R01. Scope of this correction: GAS-P1-R01
only, with its regression tests and documentation. **Phase 1 is not accepted; PR #3 remains open and
unmerged; Phase 2 was not started.**

**Defect.** `BackgroundJobRepository.claim` ran the claiming `UPDATE ... RETURNING j.job_id` and then
re-read the row with a separate `SELECT`. If worker A was delayed between the two statements long enough
for its lease to expire and worker B to reclaim, A's re-read returned B's row — B's lease token.

**Correction (claim query).** The claiming statement itself returns the attempt snapshot:

```sql
WITH next AS (SELECT job_id FROM gas.background_job
               WHERE kind IN (:kinds) AND attempts < max_attempts
                 AND ((state = 'QUEUED' AND run_after <= :now)
                      OR (state = 'RUNNING' AND lease_until < clock_timestamp()))
               ORDER BY run_after, job_id LIMIT 1
               FOR UPDATE SKIP LOCKED)
UPDATE gas.background_job j
   SET state = 'RUNNING', attempts = j.attempts + 1, lease_owner = :worker, lease_token = :token,
       lease_until = clock_timestamp() + make_interval(secs => CAST(:leaseMillis AS double precision) / 1000),
       updated_at = :now
  FROM next
 WHERE j.job_id = next.job_id
RETURNING j.job_id, j.kind, j.state, j.payload::text AS payload, j.attempts, j.max_attempts, j.run_after,
          j.lease_owner, j.lease_token, j.lease_until, j.last_error, j.created_at, j.updated_at, j.finished_at
```

- One autocommit statement; `RETURNING` yields the row values written by *this* statement, so the token,
  owner, attempt number and database-clock lease end belong to this attempt. There is no second read.
- The token is generated before the statement; the repository verifies that the returned `lease_token`
  and `lease_owner` equal the generated token and the claiming worker, and fails visibly otherwise.
- Claim selection, `SKIP LOCKED`, database-clock expiry, `renew`/`succeed`/`fail` token+expiry checks,
  renewal cadence, crash recovery and exhausted-attempt handling are unchanged.
- Test seam: `BackgroundJobRepository(DataSource, Consumer<Job> claimDelivery)` runs a callback after
  the claim statement completes and before the result is returned — the exact boundary where the old
  re-read happened. Production uses the one-argument constructor (no-op).

**Correction (pre-invocation safeguard, secondary).** In `JobRunner.runOne`, immediately after the claim
and before scheduling renewal or calling the handler:

```java
JobExecution execution = new JobExecution(job, clock, requestedAt.plus(lease));
if (!execution.leaseHeld()) {   // local time already past (time before claim + lease)
    log.warn("Job {} ({}) attempt {} was delivered after its lease ended; the handler was not started", ...);
    return true;                // no handler, no renewal, no outcome recorded
}
```

**Regression tests** (`BackgroundJobIT`):
| Test | Establishes |
|---|---|
| `claimResultCarriesItsOwnAttemptTokenEvenWhenDeliveredAfterAnotherWorkerReclaimed` | A's claim statement completes (token A); delivery is held at the former re-read boundary; A's 500 ms lease expires; B reclaims (token B, attempt 2) and the row holds token B; A's delivered result still carries token A, owner A, attempt 1 — never B's; A's token is refused by `renew`, `succeed` and `fail`; B keeps ownership, renews and completes |
| `runnerDoesNotStartAHandlerWhoseClaimWasDeliveredAfterItsLeaseEnded` | through `JobRunner`: with delivery held past the lease and B reclaiming, A's handler is never invoked, A records no outcome, B's token remains authoritative and B completes |

Applied to the previous implementation (with the delivery boundary at the same point, before its
re-read), the first test would observe token B in A's result and fail. All GAS-P1-001 live-renewal, contention, abandonment, stale-owner and
exhausted-attempt tests are retained unchanged.

**Unchanged and still verified by the full suite:** GAS-P1-002 transport protections (`TransportSecurityTest`,
`TlsReverseProxyConfigTest`, `CliTransportSecurityTest`, `TransportSecurityFilterTest`, `TransportSecurityIT`;
no transport code changed), concurrent Archive initialization (`ArchiveInitializationConcurrencyIT`),
Change History atomicity (`ChangeHistoryAtomicityIT`), all accepted Phase 0 tests. No schema change
(no new migration) and no Phase 2 functionality.

**Files changed (from `3b698a9`):** `data/BackgroundJobRepository.java` (claim `RETURNING` snapshot,
token/owner verification, delivery test seam), `jobs/JobRunner.java` (pre-invocation lease check),
`jobs/BackgroundJobIT.java` (+2 tests), `docs/PHASE-1-CORRECTIONS.md`, `docs/architecture/ADR-0008-history-and-jobs.md`.

**CI — corrected code head `15835eaa47ea4b6d8023fd30a5a616a86a9109ed`, run 40777566611:**
| Job | Tests | Failures | Errors | Skipped | Smoke | Native service |
|---|---|---|---|---|---|---|
| ubuntu-24.04 / PostgreSQL 16 | 339 | 0 | 0 | 0 | passed | systemd passed |
| ubuntu-24.04 / PostgreSQL 18 | 339 | 0 | 0 | 0 | passed | systemd passed |
| windows-2025 / PostgreSQL 16 | 339 | 0 | 0 | 0 | passed | WinSW passed |
| windows-2025 / PostgreSQL 18 | 339 | 0 | 0 | 0 | passed | WinSW passed |

337 → 339 (+2 GAS-P1-R01 tests). An intermediate push (`052ce95`, run 40776518070) failed because a Java
text block dropped the space after `RETURNING`; fixed in `15835ea`. Any later commit is documentation only
and its CI run is reported in PR #3.
