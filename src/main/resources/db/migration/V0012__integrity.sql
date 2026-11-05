-- V0012 - Phase 2: integrity verification history (BUILD-SPEC s.48; IV-001..IV-007).
--
-- integrity_scan keeps lightweight history of Quick and Full checks. integrity_finding records each
-- discrepancy found. Findings are never deleted; only their resolution may be recorded later (for
-- example by a verified repair or by the file reappearing with its authoritative hash).

CREATE TABLE integrity_scan (
    scan_id          uuid        PRIMARY KEY,
    kind             text        NOT NULL CHECK (kind IN ('QUICK', 'FULL')),
    state            text        NOT NULL CHECK (state IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    requested_by     uuid        REFERENCES app_user (user_id),
    requested_at     timestamptz NOT NULL DEFAULT now(),
    started_at       timestamptz,
    finished_at      timestamptz,
    contents_checked integer     NOT NULL DEFAULT 0 CHECK (contents_checked >= 0),
    findings_count   integer     NOT NULL DEFAULT 0 CHECK (findings_count >= 0),
    message          text        CHECK (message IS NULL OR length(message) <= 4000)
);
CREATE INDEX integrity_scan_recent ON integrity_scan (requested_at DESC);

CREATE TRIGGER integrity_scan_identity_immutable
    BEFORE UPDATE ON integrity_scan
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('scan_id', 'kind', 'requested_by', 'requested_at');
CREATE TRIGGER integrity_scan_no_delete
    BEFORE DELETE ON integrity_scan FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER integrity_scan_no_truncate
    BEFORE TRUNCATE ON integrity_scan FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

CREATE TABLE integrity_finding (
    finding_id              uuid        PRIMARY KEY,
    scan_id                 uuid        REFERENCES integrity_scan (scan_id),
    kind                    text        NOT NULL CHECK (kind IN (
                                'MISSING', 'SIZE_MISMATCH', 'HASH_MISMATCH', 'UNEXPECTED_OBJECT',
                                'LINK_OR_REPARSE', 'VOLUME_UNAVAILABLE', 'UNREADABLE', 'NOT_PROTECTED',
                                'STALE_OPERATION', 'STAGING_LEFTOVER', 'INCONSISTENT_RECORD')),
    volume_id               uuid        REFERENCES storage_volume (volume_id),
    content_id              uuid        REFERENCES file_content (content_id),
    generation              integer,
    storage_path            text        CHECK (storage_path IS NULL OR length(storage_path) <= 1024),
    operation_id            uuid        REFERENCES file_operation (operation_id),
    observed_size           bigint,
    observed_sha256         bytea       CHECK (observed_sha256 IS NULL OR octet_length(observed_sha256) = 32),
    detail                  text        NOT NULL CHECK (length(detail) BETWEEN 1 AND 4000),
    found_at                timestamptz NOT NULL DEFAULT now(),
    status                  text        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution              text        CHECK (resolution IS NULL OR length(resolution) BETWEEN 1 AND 2000),
    resolved_at             timestamptz,
    resolved_by_operation   uuid        REFERENCES file_operation (operation_id),
    CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL AND resolution IS NOT NULL))
);
CREATE INDEX integrity_finding_open ON integrity_finding (content_id) WHERE status = 'OPEN';
CREATE INDEX integrity_finding_scan ON integrity_finding (scan_id);

-- Only the resolution of a finding may change, and only from OPEN to RESOLVED.
CREATE FUNCTION integrity_finding_resolution_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'RESOLVED' THEN
        RAISE EXCEPTION 'A resolved integrity finding cannot be changed'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER integrity_finding_resolution_only
    BEFORE UPDATE ON integrity_finding FOR EACH ROW EXECUTE FUNCTION integrity_finding_resolution_only();
CREATE TRIGGER integrity_finding_identity_immutable
    BEFORE UPDATE ON integrity_finding
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change(
        'finding_id', 'scan_id', 'kind', 'volume_id', 'content_id', 'generation', 'storage_path', 'operation_id',
        'observed_size', 'observed_sha256', 'detail', 'found_at');
CREATE TRIGGER integrity_finding_no_delete
    BEFORE DELETE ON integrity_finding FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER integrity_finding_no_truncate
    BEFORE TRUNCATE ON integrity_finding FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

ALTER TABLE file_operation ADD COLUMN finding_id uuid REFERENCES integrity_finding (finding_id);
