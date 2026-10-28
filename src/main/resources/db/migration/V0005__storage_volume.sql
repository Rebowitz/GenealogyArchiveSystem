-- V0005 - Phase 1: Storage Volume model (BUILD-SPEC s.28). Archive-level identity and description
-- only. Native root locations are installation configuration (storageRoots) and never stored here.
-- No archival File is written in Phase 1.
--
-- purpose is an open, validated descriptive code (e.g. 'primary', 'backup', 'removable-backup'),
-- deliberately not a closed vocabulary (owner decision D), so later approved roles fit without
-- reinterpreting existing volumes.

CREATE TABLE storage_volume (
    volume_id    uuid        PRIMARY KEY,
    name         text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 100),
    purpose      text        NOT NULL CHECK (purpose ~ '^[a-z][a-z0-9-]{0,63}$'),
    description  text        CHECK (description IS NULL OR length(description) BETWEEN 1 AND 2000),
    required     boolean     NOT NULL,
    volume_type  text        NOT NULL DEFAULT 'filesystem' CHECK (volume_type = 'filesystem'),
    created_at   timestamptz NOT NULL DEFAULT now(),
    retired_at   timestamptz,
    row_version  bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),
    CHECK (retired_at IS NULL OR NOT required)
);

CREATE UNIQUE INDEX storage_volume_active_name ON storage_volume (lower(name)) WHERE retired_at IS NULL;

CREATE TRIGGER storage_volume_identity_immutable
    BEFORE UPDATE ON storage_volume
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('volume_id', 'volume_type', 'created_at');
CREATE TRIGGER storage_volume_no_delete
    BEFORE DELETE ON storage_volume
    FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER storage_volume_no_truncate
    BEFORE TRUNCATE ON storage_volume
    FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
