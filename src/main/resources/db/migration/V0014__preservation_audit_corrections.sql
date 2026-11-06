-- V0014 - Phase 2 audit corrections (GAS-P2-001, -006, -008, -009, -011).
--
-- quarantine_object: a ledger of every object a file operation moves into quarantine. The row is
--   written (INTENDED) and committed before the move, and confirmed (QUARANTINED) after it, so a crash
--   between the filesystem move and the database update can always be reconciled: recovery looks at the
--   recorded source and destination and finishes the record without guessing. Permanent destruction
--   enumerates these rows to remove every known copy of a destroyed File's bytes.
-- file_operation.target_created: set only after this operation's exclusive create of its planned target
--   succeeded. An object at the planned location without it was not written by this operation; it is
--   never adopted, quarantined or cancelled as if it were.
-- file_operation.receiver_alive_at: liveness of the process receiving an upload, refreshed on a timer
--   independently of byte progress. Recovery abandons an upload only when its receiver has stopped.
-- integrity_finding_observation: which scans observed which findings, so a repeated scan reports damage
--   that is still present even though the finding was opened by an earlier scan.
-- file_content_generation: a destroyed generation keeps the quarantine path it had, for accountability.
-- Restriction inheritance (GAS-P2-004) as a database invariant: a derivative of a restricted File is
--   always restricted, at every depth, whatever code path changes a restriction or records a derivation.
--   The application also records each inherited restriction in Change History; these triggers are the
--   backstop that makes a violation impossible, including for derivations committed concurrently.

CREATE TABLE quarantine_object (
    quarantine_id    uuid        PRIMARY KEY,
    volume_id        uuid        NOT NULL REFERENCES storage_volume (volume_id),
    operation_id     uuid        NOT NULL REFERENCES file_operation (operation_id),
    content_id       uuid,
    source_path      text        NOT NULL CHECK (length(source_path) BETWEEN 1 AND 1024),
    quarantine_path  text        NOT NULL CHECK (length(quarantine_path) BETWEEN 1 AND 1024),
    reason           text        NOT NULL CHECK (reason IN ('PARTIAL_COPY', 'UNVERIFIED_COPY', 'CANCELLED_COPY',
                                                            'DAMAGED_GENERATION')),
    state            text        NOT NULL CHECK (state IN ('INTENDED', 'QUARANTINED', 'SOURCE_ABSENT', 'REMOVED')),
    detail           text        CHECK (detail IS NULL OR length(detail) <= 4000),
    created_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (volume_id, quarantine_path)
);
CREATE INDEX quarantine_object_open ON quarantine_object (state) WHERE state = 'INTENDED';
CREATE INDEX quarantine_object_content ON quarantine_object (content_id) WHERE content_id IS NOT NULL;

CREATE TRIGGER quarantine_object_identity_immutable
    BEFORE UPDATE ON quarantine_object
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('quarantine_id', 'volume_id', 'operation_id', 'content_id',
                                                         'source_path', 'quarantine_path', 'reason', 'created_at');
CREATE TRIGGER quarantine_object_no_delete
    BEFORE DELETE ON quarantine_object FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER quarantine_object_no_truncate
    BEFORE TRUNCATE ON quarantine_object FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

ALTER TABLE file_operation
    ADD COLUMN target_created    boolean     NOT NULL DEFAULT false,
    ADD COLUMN receiver_alive_at timestamptz;

CREATE TABLE integrity_finding_observation (
    scan_id     uuid        NOT NULL REFERENCES integrity_scan (scan_id),
    finding_id  uuid        NOT NULL REFERENCES integrity_finding (finding_id),
    observed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (scan_id, finding_id)
);
CREATE INDEX integrity_finding_observation_finding ON integrity_finding_observation (finding_id);
CREATE TRIGGER integrity_finding_observation_no_update
    BEFORE UPDATE OR DELETE ON integrity_finding_observation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER integrity_finding_observation_no_truncate
    BEFORE TRUNCATE ON integrity_finding_observation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Findings created before this migration were observed by the scan that opened them.
INSERT INTO integrity_finding_observation (scan_id, finding_id, observed_at)
SELECT scan_id, finding_id, found_at FROM integrity_finding WHERE scan_id IS NOT NULL;

ALTER TABLE file_content_generation DROP CONSTRAINT file_content_generation_check;
ALTER TABLE file_content_generation ADD CONSTRAINT file_content_generation_quarantine_path
    CHECK ((state <> 'QUARANTINED' OR quarantine_path IS NOT NULL)
       AND (quarantine_path IS NULL OR state IN ('QUARANTINED', 'DESTROYED')));

-- Restriction inheritance -------------------------------------------------------------------------
CREATE FUNCTION archival_file_restriction_inherited() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.restriction = 'NORMAL' AND OLD.restriction = 'RESTRICTED' AND EXISTS (
        SELECT 1 FROM file_derivation d JOIN archival_file p ON p.file_id = d.parent_file_id
         WHERE d.file_id = NEW.file_id AND p.restriction = 'RESTRICTED') THEN
        RAISE EXCEPTION 'A derivative of a restricted File must stay restricted'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER archival_file_restriction_inherited
    BEFORE UPDATE OF restriction ON archival_file
    FOR EACH ROW EXECUTE FUNCTION archival_file_restriction_inherited();

CREATE FUNCTION archival_file_restriction_cascade() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    -- Restricting a File restricts its direct derivatives; their own updates fire this trigger in turn,
    -- so the restriction reaches every level.
    UPDATE archival_file SET restriction = 'RESTRICTED', row_version = row_version + 1
     WHERE restriction = 'NORMAL'
       AND file_id IN (SELECT d.file_id FROM file_derivation d WHERE d.parent_file_id = NEW.file_id);
    RETURN NULL;
END;
$$;
CREATE TRIGGER archival_file_restriction_cascade
    AFTER UPDATE OF restriction ON archival_file
    FOR EACH ROW WHEN (NEW.restriction = 'RESTRICTED' AND OLD.restriction = 'NORMAL')
    EXECUTE FUNCTION archival_file_restriction_cascade();

CREATE FUNCTION file_derivation_inherit_restriction() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    parent_restriction text;
BEGIN
    -- FOR SHARE orders this against a concurrent restriction of the parent: whichever commits first,
    -- the derivative ends up restricted if the parent is.
    SELECT restriction INTO parent_restriction FROM archival_file WHERE file_id = NEW.parent_file_id FOR SHARE;
    IF parent_restriction = 'RESTRICTED' THEN
        UPDATE archival_file SET restriction = 'RESTRICTED', row_version = row_version + 1
         WHERE file_id = NEW.file_id AND restriction = 'NORMAL';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER file_derivation_inherit_restriction
    AFTER INSERT ON file_derivation
    FOR EACH ROW EXECUTE FUNCTION file_derivation_inherit_restriction();

-- Make existing data consistent (descendants of restricted Files become restricted).
WITH RECURSIVE down(file_id) AS (
    SELECT d.file_id FROM file_derivation d JOIN archival_file p ON p.file_id = d.parent_file_id
     WHERE p.restriction = 'RESTRICTED'
    UNION
    SELECT d.file_id FROM file_derivation d JOIN down ON d.parent_file_id = down.file_id)
UPDATE archival_file a SET restriction = 'RESTRICTED', row_version = row_version + 1
  FROM down WHERE a.file_id = down.file_id AND a.restriction = 'NORMAL';
