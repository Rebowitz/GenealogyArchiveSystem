-- V0006 - Phase 1: PostgreSQL-backed background-job foundation (BUILD-SPEC s.47).
--
-- background_job is technical scheduling state (queued/running/finished work for the worker).
-- It is NOT the Archival Operation History: archival operations that need durable provenance
-- (ingestion, integrity scans, exports...) get their own explicit records in their own phases.
-- No production job kinds exist in Phase 1.

CREATE TABLE background_job (
    job_id        uuid        PRIMARY KEY,
    kind          text        NOT NULL CHECK (kind ~ '^[a-z][a-z0-9.-]{0,99}$'),
    state         text        NOT NULL CHECK (state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    payload       jsonb       NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload) = 'object'),
    attempts      integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts  integer     NOT NULL CHECK (max_attempts BETWEEN 1 AND 100),
    run_after     timestamptz NOT NULL,
    lease_owner   uuid,
    lease_until   timestamptz,
    last_error    text        CHECK (last_error IS NULL OR length(last_error) <= 4000),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    finished_at   timestamptz,
    CHECK ((state = 'RUNNING') = (lease_owner IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK ((state IN ('SUCCEEDED', 'FAILED')) = (finished_at IS NOT NULL))
);

CREATE INDEX background_job_runnable ON background_job (run_after) WHERE state = 'QUEUED';
CREATE INDEX background_job_leases ON background_job (lease_until) WHERE state = 'RUNNING';

CREATE TRIGGER background_job_identity_immutable
    BEFORE UPDATE ON background_job
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('job_id', 'kind', 'created_at');

-- Liveness of worker processes, for the status view (OP-004, OP-005). One row per running worker
-- process; a worker removes its row on clean shutdown. Contains no host names or paths.
CREATE TABLE worker_presence (
    worker_id     uuid        PRIMARY KEY,
    started_at    timestamptz NOT NULL,
    last_seen_at  timestamptz NOT NULL,
    app_version   text        NOT NULL CHECK (length(app_version) BETWEEN 1 AND 100)
);
