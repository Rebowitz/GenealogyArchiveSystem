-- Phase 2 ownership consistency (structural correction): every physical location has exactly one owner,
-- and the owner carries the evidence of whether the archive's bytes are there.
--
-- 1. One owner at a time. An operation owns only a copy it has created and not yet committed
--    (file_operation.target_created). When a commit records the copy as a generation, ownership passes to
--    the generation in the same transaction. No operation may claim a location any generation records, in
--    any state: checked at commit for every INSERT or UPDATE of either table, whichever code issues it.
-- 2. Generations carry their own missing evidence. file_content_generation.vacated records that the archive
--    saw the bytes leave the location. It is set in the same statement that records content as MISSING and
--    cleared only when content returns to ACTIVE (after the bytes were verified again). A vacated location
--    never regains ownership because something appears there: content cannot go from MISSING to DAMAGED,
--    and a vacated generation can never be QUARANTINE_PENDING (quarantining whatever is there).
-- 3. Quarantine moves record evidence of the object they move (size and last-modified time), so an
--    interrupted move is finished only for the same object.

-- 1. One owner at a time ---------------------------------------------------------------------------------

DROP TRIGGER file_operation_ownership_not_historical ON file_operation;
DROP FUNCTION file_operation_ownership_not_historical();

UPDATE file_operation o
   SET target_created = false, updated_at = clock_timestamp()
  FROM file_content_generation g
 WHERE g.volume_id = o.volume_id AND g.storage_path = o.target_path AND o.target_created;

CREATE FUNCTION physical_copy_single_owner() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    v_volume uuid;
    v_path   text;
BEGIN
    -- Deferred to commit: re-read the row as it is now, not as it was when the statement ran.
    IF TG_TABLE_NAME = 'file_operation' THEN
        SELECT o.volume_id, o.target_path INTO v_volume, v_path
          FROM file_operation o WHERE o.operation_id = NEW.operation_id AND o.target_created;
        IF v_path IS NOT NULL AND EXISTS (SELECT 1 FROM file_content_generation g
                                           WHERE g.volume_id = v_volume AND g.storage_path = v_path) THEN
            RAISE EXCEPTION 'single owner: operation % cannot own %, which a content generation records', NEW.operation_id, v_path
                USING ERRCODE = 'check_violation';
        END IF;
    ELSE
        IF EXISTS (SELECT 1 FROM file_operation o
                    WHERE o.volume_id = NEW.volume_id AND o.target_path = NEW.storage_path AND o.target_created) THEN
            RAISE EXCEPTION 'single owner: generation % of content % cannot record %, which an operation still owns',
                NEW.generation, NEW.content_id, NEW.storage_path
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER file_operation_single_owner
    AFTER INSERT OR UPDATE OF target_created, target_path, volume_id ON file_operation
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION physical_copy_single_owner();

CREATE CONSTRAINT TRIGGER file_content_generation_single_owner
    AFTER INSERT OR UPDATE OF storage_path, volume_id ON file_content_generation
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION physical_copy_single_owner();

-- 2. Vacated locations ---------------------------------------------------------------------------------

ALTER TABLE file_content_generation ADD COLUMN vacated boolean NOT NULL DEFAULT false;
ALTER TABLE file_content_generation ADD CONSTRAINT file_content_generation_vacated_not_quarantined
    CHECK (NOT (vacated AND state = 'QUARANTINE_PENDING'));

CREATE FUNCTION file_content_vacates_current_generation() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.state = 'MISSING' THEN
        UPDATE file_content_generation SET vacated = true, updated_at = clock_timestamp()
         WHERE content_id = NEW.content_id AND state = 'CURRENT' AND NOT vacated;
    ELSIF OLD.state = 'MISSING' AND NEW.state = 'ACTIVE' THEN
        UPDATE file_content_generation SET vacated = false, updated_at = clock_timestamp()
         WHERE content_id = NEW.content_id AND state = 'CURRENT' AND vacated;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER file_content_vacates_current_generation
    AFTER UPDATE OF state ON file_content
    FOR EACH ROW
    WHEN (OLD.state IS DISTINCT FROM NEW.state AND (NEW.state = 'MISSING' OR OLD.state = 'MISSING'))
    EXECUTE FUNCTION file_content_vacates_current_generation();

CREATE FUNCTION file_content_missing_is_not_damaged() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'content % was recorded missing: an object found at its location is not its copy until verified',
        NEW.content_id USING ERRCODE = 'check_violation';
END
$$;

CREATE TRIGGER file_content_missing_is_not_damaged
    BEFORE UPDATE OF state ON file_content
    FOR EACH ROW
    WHEN (OLD.state = 'MISSING' AND NEW.state = 'DAMAGED')
    EXECUTE FUNCTION file_content_missing_is_not_damaged();

-- Existing data: current generations of missing content are vacated; content whose current location has an
-- open MISSING finding but was never marked missing (an interrupted recording) is marked missing now.
UPDATE file_content_generation g SET vacated = true
  FROM file_content c
 WHERE c.content_id = g.content_id AND c.state = 'MISSING' AND g.state = 'CURRENT';

UPDATE file_content c SET state = 'MISSING'
 WHERE c.state = 'ACTIVE'
   AND EXISTS (SELECT 1 FROM integrity_finding f
                 JOIN file_content_generation g ON g.content_id = f.content_id AND g.state = 'CURRENT'
                WHERE f.content_id = c.content_id AND f.kind = 'MISSING' AND f.status = 'OPEN'
                  AND f.storage_path = g.storage_path);

-- 3. Evidence of moved objects ---------------------------------------------------------------------------

ALTER TABLE quarantine_object ADD COLUMN source_size bigint CHECK (source_size IS NULL OR source_size >= 0);
ALTER TABLE quarantine_object ADD COLUMN source_modified_micros bigint;

DROP TRIGGER quarantine_object_identity_immutable ON quarantine_object;
CREATE TRIGGER quarantine_object_identity_immutable
    BEFORE UPDATE ON quarantine_object
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('quarantine_id', 'volume_id', 'operation_id', 'content_id',
                                                         'source_path', 'quarantine_path', 'reason', 'created_at',
                                                         'source_size', 'source_modified_micros');
