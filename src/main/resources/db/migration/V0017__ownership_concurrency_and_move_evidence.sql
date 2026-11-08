-- Phase 2 structural audit corrections (on top of the V0016 single-owner redesign).
--
-- 1. Concurrency-safe single ownership. V0016's deferred check reads committed rows, so two concurrent
--    transactions could each see no conflict and both commit. Every ownership claim - an operation's
--    uncommitted copy or a generation's recorded location - is now a row in physical_location_owner, whose
--    primary key (volume, path) PostgreSQL enforces across concurrent transactions: a second claimant waits
--    for the first to commit or roll back, then is refused (or, under REPEATABLE READ, fails to serialize).
-- 2. Quarantine intents record the SHA-256 of the object to be moved; recovery moves or confirms only an
--    object with those bytes. Intents recorded without it are unverified and are never finished silently.
-- 3. Content recorded missing from any prior state (ACTIVE or DAMAGED) with an open MISSING finding on its
--    current location is reconciled to MISSING (which vacates the location, V0016).

-- 1. Ownership claims ------------------------------------------------------------------------------------

CREATE TABLE physical_location_owner (
    volume_id      uuid        NOT NULL,
    path           text        NOT NULL CHECK (length(path) BETWEEN 1 AND 1024),
    owner_kind     text        NOT NULL CHECK (owner_kind IN ('OPERATION', 'GENERATION')),
    operation_id   uuid        REFERENCES file_operation (operation_id),
    content_id     uuid        REFERENCES file_content (content_id),
    generation     integer,
    claimed_at     timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (volume_id, path),
    CHECK ((owner_kind = 'OPERATION') = (operation_id IS NOT NULL AND content_id IS NULL AND generation IS NULL)),
    CHECK ((owner_kind = 'GENERATION') = (operation_id IS NULL AND content_id IS NOT NULL AND generation IS NOT NULL))
);
CREATE UNIQUE INDEX physical_location_owner_operation ON physical_location_owner (operation_id)
    WHERE owner_kind = 'OPERATION';

CREATE FUNCTION claim_physical_location(p_volume uuid, p_path text, p_kind text, p_operation uuid,
                                        p_content uuid, p_generation integer) RETURNS void
    LANGUAGE plpgsql AS $$
DECLARE
    existing physical_location_owner%ROWTYPE;
BEGIN
    -- The primary key serialises concurrent claimants: this waits for an uncommitted claim of the same
    -- location and then either inserts (it rolled back) or does nothing (it committed).
    INSERT INTO physical_location_owner (volume_id, path, owner_kind, operation_id, content_id, generation)
    VALUES (p_volume, p_path, p_kind, p_operation, p_content, p_generation)
    ON CONFLICT (volume_id, path) DO NOTHING;
    IF FOUND THEN
        RETURN;
    END IF;
    SELECT * INTO existing FROM physical_location_owner WHERE volume_id = p_volume AND path = p_path;
    IF existing.owner_kind = p_kind AND existing.operation_id IS NOT DISTINCT FROM p_operation
       AND existing.content_id IS NOT DISTINCT FROM p_content AND existing.generation IS NOT DISTINCT FROM p_generation THEN
        RETURN;
    END IF;
    RAISE EXCEPTION 'single owner: % is already owned by % %', p_path, existing.owner_kind,
        coalesce(existing.operation_id::text, existing.content_id::text || ' generation ' || existing.generation)
        USING ERRCODE = 'check_violation';
END
$$;

CREATE FUNCTION file_operation_claims_location() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.target_created THEN
        DELETE FROM physical_location_owner WHERE owner_kind = 'OPERATION' AND operation_id = OLD.operation_id;
    END IF;
    IF NEW.target_created AND NEW.target_path IS NOT NULL THEN
        PERFORM claim_physical_location(NEW.volume_id, NEW.target_path, 'OPERATION', NEW.operation_id, NULL, NULL);
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER file_operation_claims_location
    AFTER INSERT OR UPDATE OF target_created, target_path, volume_id ON file_operation
    FOR EACH ROW EXECUTE FUNCTION file_operation_claims_location();

CREATE FUNCTION file_content_generation_claims_location() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM claim_physical_location(NEW.volume_id, NEW.storage_path, 'GENERATION', NULL, NEW.content_id, NEW.generation);
    RETURN NULL;
END
$$;

CREATE TRIGGER file_content_generation_claims_location
    AFTER INSERT ON file_content_generation
    FOR EACH ROW EXECUTE FUNCTION file_content_generation_claims_location();

INSERT INTO physical_location_owner (volume_id, path, owner_kind, content_id, generation)
SELECT volume_id, storage_path, 'GENERATION', content_id, generation FROM file_content_generation;

INSERT INTO physical_location_owner (volume_id, path, owner_kind, operation_id)
SELECT volume_id, target_path, 'OPERATION', operation_id FROM file_operation
 WHERE target_created AND target_path IS NOT NULL
ON CONFLICT (volume_id, path) DO NOTHING;

-- 2. Content evidence for quarantine moves -------------------------------------------------------------

ALTER TABLE quarantine_object ADD COLUMN source_sha256 text CHECK (source_sha256 IS NULL OR source_sha256 ~ '^[0-9a-f]{64}$');

DROP TRIGGER quarantine_object_identity_immutable ON quarantine_object;
CREATE TRIGGER quarantine_object_identity_immutable
    BEFORE UPDATE ON quarantine_object
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('quarantine_id', 'volume_id', 'operation_id', 'content_id',
                                                         'source_path', 'quarantine_path', 'reason', 'created_at',
                                                         'source_size', 'source_modified_micros', 'source_sha256');

-- 3. Missing recorded from any prior state -------------------------------------------------------------

UPDATE file_content c SET state = 'MISSING'
 WHERE c.state IN ('ACTIVE', 'DAMAGED')
   AND EXISTS (SELECT 1 FROM integrity_finding f
                 JOIN file_content_generation g ON g.content_id = f.content_id AND g.state = 'CURRENT'
                WHERE f.content_id = c.content_id AND f.kind = 'MISSING' AND f.status = 'OPEN'
                  AND f.storage_path = g.storage_path);
