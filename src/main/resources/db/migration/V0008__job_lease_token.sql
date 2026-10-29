-- V0008 - Phase 1 correction GAS-P1-001: explicit, attempt-specific job lease ownership.
--
-- A claim now issues a fresh lease_token for that attempt. While the handler runs, the owning
-- worker renews the lease with that token, so a healthy long-running job is never reclaimed merely
-- because the initial lease interval elapsed. Renewal and completion (succeed/fail) are accepted
-- only for the current token and only while the lease is unexpired; once another worker has
-- reclaimed an expired lease (new token), the stale attempt can neither renew nor complete.
-- Crash recovery is unchanged: a lease that is not renewed expires and the job is reclaimed.
-- lease_owner remains the worker process ID, for display only; it is not the ownership check.

ALTER TABLE background_job ADD COLUMN lease_token uuid;

-- Any job already RUNNING when this migration is applied gets a token nobody holds: it is
-- recovered by lease expiry exactly as before.
UPDATE background_job SET lease_token = gen_random_uuid() WHERE state = 'RUNNING';

ALTER TABLE background_job
    ADD CONSTRAINT background_job_lease_token_iff_running CHECK ((state = 'RUNNING') = (lease_token IS NOT NULL));
