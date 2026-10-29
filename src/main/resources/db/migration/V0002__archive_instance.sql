-- V0002 - Phase 1: Archive Instance and shared integrity guards.
--
-- archive_instance holds the persistent, globally unique Archive ID (BUILD-SPEC s.7) and the
-- archive-level configuration that travels with the archive (s.55). It is NOT the Phase 0
-- schema_identity marker, which only says "this is a Genealogy Archive System schema".
--
-- The row is created explicitly by the 'init-archive' command (owner decision B), never by a
-- migration. Restoring the same archive restores this row and therefore the same Archive ID.
-- Copying an initialized database to start an unrelated archive is unsupported: it would
-- duplicate the Archive ID.

-- Shared guard: rejects any change to the named columns of an existing row.
-- Usage: CREATE TRIGGER ... BEFORE UPDATE ... EXECUTE FUNCTION reject_identity_change('col', ...)
CREATE FUNCTION reject_identity_change() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    col text;
BEGIN
    FOREACH col IN ARRAY TG_ARGV LOOP
        IF (to_jsonb(NEW) -> col) IS DISTINCT FROM (to_jsonb(OLD) -> col) THEN
            RAISE EXCEPTION 'Immutable column %.% cannot be changed', TG_TABLE_NAME, col
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$;

-- Shared guard: rejects DELETE and TRUNCATE (retire/disable instead; identifiers are never reused).
CREATE FUNCTION reject_removal() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Rows of % cannot be removed or rewritten (identifiers and history are permanent)', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TABLE archive_instance (
    singleton     boolean     PRIMARY KEY DEFAULT true CHECK (singleton),
    archive_id    uuid        NOT NULL UNIQUE,
    archive_name  text        NOT NULL CHECK (length(btrim(archive_name)) BETWEEN 1 AND 200),
    created_at    timestamptz NOT NULL DEFAULT now(),
    row_version   bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);

COMMENT ON TABLE archive_instance IS
    'The Archive Instance: exactly one row once initialized. archive_id is immutable and globally unique.';

CREATE TRIGGER archive_instance_identity_immutable
    BEFORE UPDATE ON archive_instance
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('archive_id', 'created_at');
CREATE TRIGGER archive_instance_no_delete
    BEFORE DELETE ON archive_instance
    FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER archive_instance_no_truncate
    BEFORE TRUNCATE ON archive_instance
    FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
