-- Phase 2 finalization (owner decision V0020): persisted object-identity evidence.
--
-- An unresolved-object record already keeps the size and SHA-256 observed and the time of the observation
-- (detected_at). This adds the object's filesystem identity at that observation, and binds an Administrator's
-- attestation or release to the object actually examined when the decision was made. Times and file keys are
-- evidence, not proof of physical identity; they are compared for exact equality, never against a clock.
--
-- Existing rows are preserved and marked LEGACY: they carry no such evidence, and the application never treats a
-- LEGACY attestation or release as identifying an object.

ALTER TABLE unresolved_object
    ADD COLUMN observed_created_at  timestamptz,
    ADD COLUMN observed_modified_at timestamptz,
    ADD COLUMN observed_file_key    text CHECK (observed_file_key IS NULL OR length(observed_file_key) BETWEEN 1 AND 400),
    ADD COLUMN identity_evidence    text NOT NULL DEFAULT 'LEGACY'
        CHECK (identity_evidence IN ('RECORDED', 'NONE', 'LEGACY')),
    ADD COLUMN decision_evidence    text CHECK (decision_evidence IN ('CONFIRMED', 'UNCONFIRMED')),
    ADD COLUMN decision_checked_at  timestamptz;

ALTER TABLE unresolved_object
    -- RECORDED: a plain file was measured with its times. NONE: there was no plain file to identify.
    ADD CONSTRAINT unresolved_object_recorded_evidence_complete CHECK (
        identity_evidence <> 'RECORDED' OR (observed_size IS NOT NULL AND observed_sha256 IS NOT NULL
                                            AND observed_created_at IS NOT NULL AND observed_modified_at IS NOT NULL)),
    ADD CONSTRAINT unresolved_object_none_has_no_evidence CHECK (
        identity_evidence <> 'NONE' OR (observed_size IS NULL AND observed_sha256 IS NULL AND observed_created_at IS NULL
                                        AND observed_modified_at IS NULL AND observed_file_key IS NULL)),
    ADD CONSTRAINT unresolved_object_legacy_has_no_new_evidence CHECK (
        identity_evidence <> 'LEGACY' OR (observed_created_at IS NULL AND observed_modified_at IS NULL
                                          AND observed_file_key IS NULL AND decision_evidence IS NULL)),
    ADD CONSTRAINT unresolved_object_decision_checked CHECK ((decision_evidence IS NULL) = (decision_checked_at IS NULL)),
    -- Only an object re-examined against RECORDED evidence can be CONFIRMED.
    ADD CONSTRAINT unresolved_object_confirmed_needs_evidence CHECK (
        decision_evidence IS DISTINCT FROM 'CONFIRMED' OR identity_evidence = 'RECORDED'),
    -- An open attestation made under V0020 is bound to the object it was made about.
    ADD CONSTRAINT unresolved_object_attestation_confirmed CHECK (
        status <> 'OPEN' OR attested_at IS NULL OR identity_evidence = 'LEGACY' OR decision_evidence = 'CONFIRMED');

-- The observation is immutable, like the rest of the record's identity.
DROP TRIGGER unresolved_object_identity_immutable ON unresolved_object;
CREATE TRIGGER unresolved_object_identity_immutable
    BEFORE UPDATE ON unresolved_object
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('unresolved_id', 'volume_id', 'path', 'content_id', 'generation',
                                                         'operation_id', 'kind', 'observed_size', 'observed_sha256',
                                                         'detail', 'detected_at', 'observed_created_at',
                                                         'observed_modified_at', 'observed_file_key', 'identity_evidence');

-- Decision evidence changes only together with the decision it belongs to (an attestation or a resolution).
CREATE FUNCTION unresolved_object_decision_evidence() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.decision_evidence IS DISTINCT FROM OLD.decision_evidence
        OR NEW.decision_checked_at IS DISTINCT FROM OLD.decision_checked_at)
       AND NEW.attested_at IS NOT DISTINCT FROM OLD.attested_at
       AND NEW.status IS NOT DISTINCT FROM OLD.status THEN
        RAISE EXCEPTION 'unresolved object % at %: decision evidence changes only with an attestation or a resolution',
            OLD.unresolved_id, OLD.path USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER unresolved_object_decision_evidence
    BEFORE UPDATE ON unresolved_object
    FOR EACH ROW EXECUTE FUNCTION unresolved_object_decision_evidence();

COMMENT ON COLUMN unresolved_object.observed_created_at IS
    'Filesystem creation time of the object at the observation (detected_at). Evidence, not proof, of identity.';
COMMENT ON COLUMN unresolved_object.observed_modified_at IS
    'Filesystem last-modification time of the object at the observation. Evidence, not proof, of identity.';
COMMENT ON COLUMN unresolved_object.observed_file_key IS
    'Platform file key at the observation (device/inode on POSIX); NULL where the platform provides none.';
COMMENT ON COLUMN unresolved_object.identity_evidence IS
    'RECORDED: size, SHA-256 and times observed; NONE: no plain file to identify; LEGACY: recorded before V0020 - '
    'never identifies an object for an attestation or a release.';
COMMENT ON COLUMN unresolved_object.decision_evidence IS
    'At the Administrator''s attestation or release: CONFIRMED if the object then at the location had exactly the '
    'recorded evidence, UNCONFIRMED otherwise. Only a CONFIRMED decision identifies an object later.';
