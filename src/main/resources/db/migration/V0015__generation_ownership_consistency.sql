-- Phase 2 correction IV01 (missing-content repair): a generation's storage location and an operation's
-- ownership of the same location must never contradict one another.
--
-- An operation owns the object at its target (file_operation.target_created) from its exclusive create.
-- Once the generation stored there is retired - QUARANTINED (bytes moved to quarantine), MISSING
-- (superseded while absent) or DESTROYED - that location is only historical, so no operation may still
-- claim it. Enforced here for every retirement path (repair of missing or damaged content, quarantine
-- settlement, destruction), in the same transaction as the retirement, so it is crash-safe.

CREATE FUNCTION file_content_generation_retires_ownership() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    UPDATE file_operation
       SET target_created = false, updated_at = clock_timestamp()
     WHERE volume_id = NEW.volume_id AND target_path = NEW.storage_path AND target_created;
    RETURN NULL;
END
$$;

CREATE TRIGGER file_content_generation_retires_ownership
    AFTER UPDATE OF state ON file_content_generation
    FOR EACH ROW
    WHEN (NEW.state IN ('QUARANTINED', 'MISSING', 'DESTROYED') AND OLD.state IS DISTINCT FROM NEW.state)
    EXECUTE FUNCTION file_content_generation_retires_ownership();

-- Content recorded as MISSING: the object at its current generation's location has vanished, so whatever
-- appears there later is not the operation's copy either. (The generation record stays CURRENT: if the
-- original bytes reappear and are verified, that record - not the operation - accounts for them.)
CREATE FUNCTION file_content_missing_retires_ownership() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    UPDATE file_operation o
       SET target_created = false, updated_at = clock_timestamp()
      FROM file_content_generation g
     WHERE g.content_id = NEW.content_id AND g.state = 'CURRENT'
       AND o.volume_id = g.volume_id AND o.target_path = g.storage_path AND o.target_created;
    RETURN NULL;
END
$$;

CREATE TRIGGER file_content_missing_retires_ownership
    AFTER UPDATE OF state ON file_content
    FOR EACH ROW
    WHEN (NEW.state = 'MISSING' AND OLD.state IS DISTINCT FROM NEW.state)
    EXECUTE FUNCTION file_content_missing_retires_ownership();

-- The reverse: an operation can never take ownership of a retired generation's location.
CREATE FUNCTION file_operation_ownership_not_historical() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM file_content_generation g
                WHERE g.volume_id = NEW.volume_id AND g.storage_path = NEW.target_path
                  AND g.state IN ('QUARANTINED', 'MISSING', 'DESTROYED')) THEN
        RAISE EXCEPTION 'operation % cannot own %: a retired generation was stored there', NEW.operation_id, NEW.target_path
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER file_operation_ownership_not_historical
    BEFORE UPDATE OF target_created ON file_operation
    FOR EACH ROW
    WHEN (NEW.target_created AND NOT OLD.target_created)
    EXECUTE FUNCTION file_operation_ownership_not_historical();

-- Existing data: end every claim that already contradicts a retired generation.
UPDATE file_operation o
   SET target_created = false, updated_at = clock_timestamp()
  FROM file_content_generation g
 WHERE g.volume_id = o.volume_id AND g.storage_path = o.target_path
   AND g.state IN ('QUARANTINED', 'MISSING', 'DESTROYED') AND o.target_created;

UPDATE file_operation o
   SET target_created = false, updated_at = clock_timestamp()
  FROM file_content c JOIN file_content_generation g ON g.content_id = c.content_id AND g.state = 'CURRENT'
 WHERE c.state = 'MISSING' AND g.volume_id = o.volume_id AND g.storage_path = o.target_path AND o.target_created;
