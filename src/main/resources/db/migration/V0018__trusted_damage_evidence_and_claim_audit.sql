-- Phase 2 V0017 audit corrections.
--
-- 1. Trusted damage evidence. A generation keeps the size and SHA-256 its copy had when the damage was first
--    detected (the transition ACTIVE -> DAMAGED). It is written once and never replaced: later scans that find
--    different bytes there report the change instead, so a replacement object can never become "the damaged
--    copy" by being hashed. Repairs (planning and executing quarantine moves), recovery and destruction verify
--    the object against this evidence.
-- 2. Ownership claims audit. V0017 backfilled claims with ON CONFLICT DO NOTHING, which would have skipped a
--    conflicting legacy claim silently. Every claim an operation or generation holds must now have its
--    physical_location_owner row; if any does not, this migration stops with a diagnostic listing them, so an
--    Administrator decides - nothing is chosen or discarded automatically, and the evidence stays as it was.

-- 1. Trusted damage evidence ----------------------------------------------------------------------------

ALTER TABLE file_content_generation ADD COLUMN damaged_size bigint CHECK (damaged_size IS NULL OR damaged_size >= 0);
ALTER TABLE file_content_generation ADD COLUMN damaged_sha256 text
    CHECK (damaged_sha256 IS NULL OR damaged_sha256 ~ '^[0-9a-f]{64}$');
ALTER TABLE file_content_generation ADD CONSTRAINT file_content_generation_damage_evidence_complete
    CHECK ((damaged_size IS NULL) = (damaged_sha256 IS NULL));

CREATE FUNCTION file_content_generation_damage_evidence_once() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.damaged_sha256 IS NOT NULL AND (NEW.damaged_sha256 IS DISTINCT FROM OLD.damaged_sha256
                                           OR NEW.damaged_size IS DISTINCT FROM OLD.damaged_size) THEN
        RAISE EXCEPTION 'generation % of content %: damage evidence recorded when the damage was found is never replaced',
            OLD.generation, OLD.content_id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER file_content_generation_damage_evidence_once
    BEFORE UPDATE OF damaged_size, damaged_sha256 ON file_content_generation
    FOR EACH ROW EXECUTE FUNCTION file_content_generation_damage_evidence_once();

-- Existing data: the earliest damage finding that recorded the copy's hash is the evidence from detection.
UPDATE file_content_generation g
   SET damaged_size = e.observed_size, damaged_sha256 = e.sha
  FROM (SELECT DISTINCT ON (f.content_id, f.generation)
               f.content_id, f.generation, f.observed_size, encode(f.observed_sha256, 'hex') AS sha
          FROM integrity_finding f
         WHERE f.kind IN ('HASH_MISMATCH', 'SIZE_MISMATCH') AND f.observed_sha256 IS NOT NULL
           AND f.observed_size IS NOT NULL AND f.content_id IS NOT NULL AND f.generation IS NOT NULL
         ORDER BY f.content_id, f.generation, f.found_at, f.finding_id) e
 WHERE g.content_id = e.content_id AND g.generation = e.generation;

-- 2. Ownership claims audit ------------------------------------------------------------------------------

DO $$
DECLARE
    problems text;
BEGIN
    SELECT string_agg(line, E'\n') INTO problems FROM (
        SELECT format('operation %s claims %s on volume %s, but that location is owned by %s',
                      o.operation_id, o.target_path, o.volume_id,
                      coalesce(p.owner_kind || ' ' || coalesce(p.operation_id::text,
                               p.content_id::text || ' generation ' || p.generation), 'nobody')) AS line
          FROM file_operation o
          LEFT JOIN physical_location_owner p ON p.volume_id = o.volume_id AND p.path = o.target_path
         WHERE o.target_created AND o.target_path IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM physical_location_owner c
                            WHERE c.owner_kind = 'OPERATION' AND c.operation_id = o.operation_id
                              AND c.volume_id = o.volume_id AND c.path = o.target_path)
        UNION ALL
        SELECT format('generation %s of content %s records %s on volume %s, but that location is owned by %s',
                      g.generation, g.content_id, g.storage_path, g.volume_id,
                      coalesce(p.owner_kind || ' ' || coalesce(p.operation_id::text,
                               p.content_id::text || ' generation ' || p.generation), 'nobody'))
          FROM file_content_generation g
          LEFT JOIN physical_location_owner p ON p.volume_id = g.volume_id AND p.path = g.storage_path
         WHERE NOT EXISTS (SELECT 1 FROM physical_location_owner c
                            WHERE c.owner_kind = 'GENERATION' AND c.content_id = g.content_id
                              AND c.generation = g.generation)
        ORDER BY 1) conflicts;
    IF problems IS NOT NULL THEN
        RAISE EXCEPTION USING
            ERRCODE = 'check_violation',
            MESSAGE = 'Ownership claims conflict; the upgrade stopped and nothing was changed.',
            DETAIL = problems,
            HINT = 'Inspect each named location on its volume. For an operation that did not create the object there, '
                || 'clear its claim (UPDATE gas.file_operation SET target_created = false WHERE operation_id = ...), '
                || 'then run the upgrade again. Nothing is chosen automatically.';
    END IF;
END
$$;
