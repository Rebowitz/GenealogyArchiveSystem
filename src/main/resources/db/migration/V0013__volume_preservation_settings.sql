-- V0013 - Phase 2: volume initialization record, ingestion destination, retirement guard.
--
-- marker_initialized_at records that 'init-volume' wrote this archive's identity marker to the
-- volume (P2-D); it is set once. archive_instance.ingestion_volume_id names, by permanent Volume
-- ID, where newly ingested originals are written (P2-C) - archive configuration, not installation
-- configuration. A volume that still holds content that has not been destroyed cannot be retired.

ALTER TABLE storage_volume ADD COLUMN marker_initialized_at timestamptz;
ALTER TABLE archive_instance ADD COLUMN ingestion_volume_id uuid REFERENCES storage_volume (volume_id);

CREATE FUNCTION storage_volume_preservation_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.marker_initialized_at IS NOT NULL
       AND NEW.marker_initialized_at IS DISTINCT FROM OLD.marker_initialized_at THEN
        RAISE EXCEPTION 'A volume''s initialization time cannot be changed'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.retired_at IS NULL AND NEW.retired_at IS NOT NULL AND EXISTS (
           SELECT 1 FROM file_content_generation g
            WHERE g.volume_id = NEW.volume_id AND g.state IN ('CURRENT', 'QUARANTINE_PENDING')) THEN
        RAISE EXCEPTION 'Storage Volume % still holds archival content and cannot be retired', NEW.volume_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.retired_at IS NULL AND NEW.retired_at IS NOT NULL AND EXISTS (
           SELECT 1 FROM archive_instance a WHERE a.ingestion_volume_id = NEW.volume_id) THEN
        RAISE EXCEPTION 'Storage Volume % is the ingestion destination and cannot be retired', NEW.volume_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER storage_volume_preservation_guard
    BEFORE UPDATE ON storage_volume FOR EACH ROW EXECUTE FUNCTION storage_volume_preservation_guard();
