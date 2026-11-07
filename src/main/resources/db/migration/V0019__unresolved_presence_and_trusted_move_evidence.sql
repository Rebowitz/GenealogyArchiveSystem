-- Phase 2 V0018 audit corrections.
--
-- Bytes are moved or deleted only on evidence the archive established before the object could have been
-- replaced. An object that cannot be shown that way is left exactly where it is and its presence is recorded
-- durably as unresolved, until it is verifiably gone or an Administrator resolves it.
--
-- 1. unresolved_object: one open record per occupied location whose object the archive cannot identify - a
--    superseded damaged copy, an object at a location the bytes left, an operation's claimed copy that does
--    not verify, a quarantined object without trusted evidence. The observed size and SHA-256 are diagnostic
--    only. Destruction never completes while one is open for the content.
-- 2. Generation state UNRESOLVED: a superseded generation whose location is occupied by such an object. It is
--    neither MISSING (bytes absent) nor QUARANTINE_PENDING; it requires an open unresolved_object record.
-- 3. quarantine_object.evidence_basis: whether the move's recorded hash was trusted when it was recorded (the
--    worker verified the operation's own copy, or an Administrator attested the object). Moves recorded before
--    this migration hashed whatever was at the source at that moment, so they are UNTRUSTED.
-- 4. damaged_size / damaged_sha256 (V0018) are an observation made by the scan that first saw the mismatch -
--    diagnostic only. The evidence-once trigger stays; nothing authorises a move or deletion from them.

-- 1. Unresolved presence ---------------------------------------------------------------------------------

CREATE TABLE unresolved_object (
    unresolved_id      uuid        PRIMARY KEY,
    volume_id          uuid        NOT NULL REFERENCES storage_volume (volume_id),
    path               text        NOT NULL CHECK (length(path) BETWEEN 1 AND 1024),
    content_id         uuid        REFERENCES file_content (content_id),
    generation         integer,
    operation_id       uuid        NOT NULL REFERENCES file_operation (operation_id),
    kind               text        NOT NULL CHECK (kind IN ('DAMAGED_COPY', 'OBJECT_AT_VACATED_LOCATION', 'OPERATION_COPY',
                                                            'QUARANTINED_COPY', 'CURRENT_COPY')),
    observed_size      bigint      CHECK (observed_size IS NULL OR observed_size >= 0),
    observed_sha256    text        CHECK (observed_sha256 IS NULL OR observed_sha256 ~ '^[0-9a-f]{64}$'),
    detail             text        NOT NULL CHECK (length(detail) BETWEEN 1 AND 4000),
    detected_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    attested_by        uuid        REFERENCES app_user (user_id),
    attested_at        timestamptz,
    status             text        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution         text        CHECK (resolution IN ('ABSENT', 'NOT_ARCHIVAL', 'QUARANTINED', 'DESTROYED')),
    resolution_detail  text        CHECK (resolution_detail IS NULL OR length(resolution_detail) BETWEEN 1 AND 4000),
    resolved_by        uuid        REFERENCES app_user (user_id),
    resolved_at        timestamptz,
    CHECK (generation IS NULL OR content_id IS NOT NULL),
    CHECK ((observed_size IS NULL) = (observed_sha256 IS NULL)),
    CHECK ((attested_by IS NULL) = (attested_at IS NULL)),
    CHECK (attested_at IS NULL OR observed_sha256 IS NOT NULL),
    CHECK ((status = 'RESOLVED') = (resolution IS NOT NULL AND resolved_at IS NOT NULL AND resolution_detail IS NOT NULL))
);
CREATE UNIQUE INDEX unresolved_object_open_location ON unresolved_object (volume_id, path) WHERE status = 'OPEN';
CREATE INDEX unresolved_object_open_content ON unresolved_object (content_id) WHERE status = 'OPEN';

CREATE TRIGGER unresolved_object_identity_immutable
    BEFORE UPDATE ON unresolved_object
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('unresolved_id', 'volume_id', 'path', 'content_id', 'generation',
                                                         'operation_id', 'kind', 'observed_size', 'observed_sha256',
                                                         'detail', 'detected_at');
CREATE TRIGGER unresolved_object_no_delete
    BEFORE DELETE ON unresolved_object FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER unresolved_object_no_truncate
    BEFORE TRUNCATE ON unresolved_object FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

CREATE FUNCTION unresolved_object_resolution_final() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'RESOLVED' THEN
        RAISE EXCEPTION 'unresolved object % at % was already resolved (%); a resolution is never changed',
            OLD.unresolved_id, OLD.path, OLD.resolution USING ERRCODE = 'check_violation';
    END IF;
    IF OLD.attested_at IS NOT NULL AND (NEW.attested_at IS DISTINCT FROM OLD.attested_at
                                        OR NEW.attested_by IS DISTINCT FROM OLD.attested_by) THEN
        RAISE EXCEPTION 'unresolved object % at %: an attestation is never replaced', OLD.unresolved_id, OLD.path
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER unresolved_object_resolution_final
    BEFORE UPDATE ON unresolved_object
    FOR EACH ROW EXECUTE FUNCTION unresolved_object_resolution_final();

-- 2. Generation state UNRESOLVED -------------------------------------------------------------------------

ALTER TABLE file_content_generation DROP CONSTRAINT file_content_generation_state_check;
ALTER TABLE file_content_generation ADD CONSTRAINT file_content_generation_state_check
    CHECK (state IN ('CURRENT', 'QUARANTINE_PENDING', 'QUARANTINED', 'MISSING', 'DESTROYED', 'UNRESOLVED'));

-- An UNRESOLVED generation always has its open record; a record is never resolved while its generation is
-- still UNRESOLVED. Checked at commit, whichever code changes either table.
CREATE FUNCTION unresolved_generation_has_record() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    -- Both tables name the generation (content_id, generation).
    v_content    uuid    := NEW.content_id;
    v_generation integer := NEW.generation;
BEGIN
    IF v_generation IS NULL THEN
        RETURN NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM file_content_generation g
                WHERE g.content_id = v_content AND g.generation = v_generation AND g.state = 'UNRESOLVED')
       AND NOT EXISTS (SELECT 1 FROM unresolved_object u
                        WHERE u.content_id = v_content AND u.generation = v_generation AND u.status = 'OPEN') THEN
        RAISE EXCEPTION 'generation % of content % is UNRESOLVED without an open unresolved-object record',
            v_generation, v_content USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER file_content_generation_unresolved_has_record
    AFTER INSERT OR UPDATE OF state ON file_content_generation
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION unresolved_generation_has_record();

CREATE CONSTRAINT TRIGGER unresolved_object_generation_keeps_record
    AFTER UPDATE OF status ON unresolved_object
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION unresolved_generation_has_record();

-- 3. Trust basis of quarantine move evidence ---------------------------------------------------------------

ALTER TABLE quarantine_object ADD COLUMN evidence_basis text NOT NULL DEFAULT 'UNTRUSTED'
    CHECK (evidence_basis IN ('OPERATION_COPY_VERIFIED', 'ADMINISTRATOR_ATTESTED', 'UNTRUSTED'));
-- The default stays UNTRUSTED: a move is trusted only when its writer says why.

ALTER TABLE quarantine_object DROP CONSTRAINT quarantine_object_reason_check;
ALTER TABLE quarantine_object ADD CONSTRAINT quarantine_object_reason_check
    CHECK (reason IN ('PARTIAL_COPY', 'UNVERIFIED_COPY', 'CANCELLED_COPY', 'DAMAGED_GENERATION', 'ATTESTED_OBJECT'));

DROP TRIGGER quarantine_object_identity_immutable ON quarantine_object;
CREATE TRIGGER quarantine_object_identity_immutable
    BEFORE UPDATE ON quarantine_object
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('quarantine_id', 'volume_id', 'operation_id', 'content_id',
                                                         'source_path', 'quarantine_path', 'reason', 'created_at',
                                                         'source_size', 'source_modified_micros', 'source_sha256',
                                                         'evidence_basis');

-- 4. Damage observations are diagnostic --------------------------------------------------------------------

COMMENT ON COLUMN file_content_generation.damaged_size IS
    'Size observed by the scan that first found this generation damaged. Diagnostic only: never evidence that an object is the archive''s copy.';
COMMENT ON COLUMN file_content_generation.damaged_sha256 IS
    'SHA-256 observed by the scan that first found this generation damaged. Diagnostic only: never evidence that an object is the archive''s copy.';
COMMENT ON COLUMN quarantine_object.evidence_basis IS
    'Whether source_size/source_sha256 identified the archive''s object when recorded: OPERATION_COPY_VERIFIED, ADMINISTRATOR_ATTESTED, or UNTRUSTED (an observation, including every move recorded before V0019).';
