-- V0021 - Phase 3: record vocabularies, Repositories, Sources and Capture Sessions (BUILD-SPEC s.19, s.22, s.25,
-- s.38; ADR-0011; decisions D3-02, D3-06, D3-07, D3-08).
--
-- Every object here has a permanent UUIDv7 identity and a friendly number from its own never-cycling sequence.
-- Rows are never deleted (identifiers are never reused, A-002); identity columns cannot change. Correctable
-- attributes carry row_version for optimistic concurrency. Nothing here touches preservation originals.

-- Archive configuration vocabularies (D3-08). Codes are permanent: never deleted, only retired.
CREATE TABLE record_classification (
    code         text        PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label        text        NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100),
    description  text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 2000),
    retired      boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    row_version  bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER record_classification_identity_immutable
    BEFORE UPDATE ON record_classification
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('code', 'created_at');
CREATE TRIGGER record_classification_no_delete
    BEFORE DELETE ON record_classification FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER record_classification_no_truncate
    BEFORE TRUNCATE ON record_classification FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

CREATE TABLE record_role_type (
    code         text        PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label        text        NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100),
    description  text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 2000),
    retired      boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    row_version  bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER record_role_type_identity_immutable
    BEFORE UPDATE ON record_role_type
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('code', 'created_at');
CREATE TRIGGER record_role_type_no_delete
    BEFORE DELETE ON record_role_type FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER record_role_type_no_truncate
    BEFORE TRUNCATE ON record_role_type FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Only values the specifications name (BUILD-SPEC s.19; ACCEPTANCE-SPEC R-006). Administrators add the rest.
INSERT INTO record_classification (code, label) VALUES ('DEED', 'Deed'), ('DTRUST', 'Deed of trust');
INSERT INTO record_role_type (code, label) VALUES
    ('GRANTOR', 'Grantor'), ('GRANTEE', 'Grantee'), ('TESTATOR', 'Testator'), ('EXECUTOR', 'Executor'),
    ('WITNESS', 'Witness'), ('BENEFICIARY', 'Beneficiary'), ('ADJOINING_LANDHOLDER', 'Adjoining landholder'),
    ('PRIOR_OWNER', 'Prior owner'), ('SURVEYOR', 'Surveyor');

-- Friendly identifiers (D3-07).
CREATE SEQUENCE friendly_id_seq_repository NO CYCLE;
CREATE SEQUENCE friendly_id_seq_source NO CYCLE;
CREATE SEQUENCE friendly_id_seq_capture_session NO CYCLE;
INSERT INTO friendly_id_series (object_type, prefix, min_digits, sequence_name) VALUES
    ('repository', 'RP', 4, 'friendly_id_seq_repository'),
    ('source', 'S', 5, 'friendly_id_seq_source'),
    ('capture_session', 'CS', 4, 'friendly_id_seq_capture_session');

-- Repository: custody/stewardship (s.22). Distinct from historical creator, Access Provider, Jurisdiction and
-- building (O-004...O-006). The name is a researcher-facing label.
CREATE TABLE repository (
    repository_id    uuid        PRIMARY KEY,
    friendly_number  bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    name             text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 300),
    description      text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 4000),
    location_text    text        CHECK (location_text IS NULL OR length(btrim(location_text)) BETWEEN 1 AND 1000),
    created_at       timestamptz NOT NULL,
    created_by       uuid        REFERENCES app_user (user_id),
    row_version      bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER repository_identity_immutable
    BEFORE UPDATE ON repository
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('repository_id', 'friendly_number', 'created_at', 'created_by');
CREATE TRIGGER repository_no_delete BEFORE DELETE ON repository FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER repository_no_truncate BEFORE TRUNCATE ON repository FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Source / record book (s.19). The Repository is optional: an unknown custodian never blocks entry (O-003).
-- Creator, Access Provider and jurisdiction are kept as written, separately from the Repository (D3-06).
CREATE TABLE source (
    source_id             uuid        PRIMARY KEY,
    friendly_number       bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    title                 text        NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 500),
    repository_id         uuid        REFERENCES repository (repository_id),
    source_type           text        CHECK (source_type IS NULL OR length(btrim(source_type)) BETWEEN 1 AND 200),
    volume_designation    text        CHECK (volume_designation IS NULL OR length(btrim(volume_designation)) BETWEEN 1 AND 200),
    creator_text          text        CHECK (creator_text IS NULL OR length(btrim(creator_text)) BETWEEN 1 AND 1000),
    access_provider_text  text        CHECK (access_provider_text IS NULL OR length(btrim(access_provider_text)) BETWEEN 1 AND 1000),
    jurisdiction_text     text        CHECK (jurisdiction_text IS NULL OR length(btrim(jurisdiction_text)) BETWEEN 1 AND 1000),
    notes                 text        CHECK (notes IS NULL OR length(btrim(notes)) BETWEEN 1 AND 8000),
    created_at            timestamptz NOT NULL,
    created_by            uuid        REFERENCES app_user (user_id),
    row_version           bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE INDEX source_repository ON source (repository_id);
CREATE TRIGGER source_identity_immutable
    BEFORE UPDATE ON source
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('source_id', 'friendly_number', 'created_at', 'created_by');
CREATE TRIGGER source_no_delete BEFORE DELETE ON source FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER source_no_truncate BEFORE TRUNCATE ON source FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Capture Session (s.38): context entered once and inherited by many Files. capture_date is the calendar date
-- of photography, never a historical date (EV-003).
CREATE TABLE capture_session (
    capture_session_id  uuid        PRIMARY KEY,
    friendly_number     bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    title               text        NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 300),
    repository_id       uuid        REFERENCES repository (repository_id),
    source_id           uuid        REFERENCES source (source_id),
    capture_date        date,
    researcher          text        CHECK (researcher IS NULL OR length(btrim(researcher)) BETWEEN 1 AND 200),
    equipment_method    text        CHECK (equipment_method IS NULL OR length(btrim(equipment_method)) BETWEEN 1 AND 500),
    notes               text        CHECK (notes IS NULL OR length(btrim(notes)) BETWEEN 1 AND 8000),
    created_at          timestamptz NOT NULL,
    created_by          uuid        REFERENCES app_user (user_id),
    row_version         bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER capture_session_identity_immutable
    BEFORE UPDATE ON capture_session
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('capture_session_id', 'friendly_number', 'created_at', 'created_by');
CREATE TRIGGER capture_session_no_delete BEFORE DELETE ON capture_session FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER capture_session_no_truncate BEFORE TRUNCATE ON capture_session FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- A File's place in one Capture Session (BI-001; plan section 10.2). Each overridable context field has a mode:
-- INHERIT (use the session's value, resolved when read), VALUE (this File's own value) or BLANK (explicitly not
-- known for this File, even if the session has a value). Correcting the session changes the effective context of
-- every File that inherits; an override changes only its own File. The worksheet columns (D3-02) hold the
-- researcher's grouping marks, so a half-finished batch is ordinary saved state (R-005). The association is
-- correctable (a File not yet grouped can be removed from its session).
CREATE TABLE file_capture_context (
    file_id                    uuid        PRIMARY KEY REFERENCES archival_file (file_id),
    capture_session_id         uuid        NOT NULL REFERENCES capture_session (capture_session_id),
    sequence_number            integer     NOT NULL CHECK (sequence_number >= 1),
    repository_mode            text        NOT NULL DEFAULT 'INHERIT' CHECK (repository_mode IN ('INHERIT', 'VALUE', 'BLANK')),
    override_repository_id     uuid        REFERENCES repository (repository_id),
    source_mode                text        NOT NULL DEFAULT 'INHERIT' CHECK (source_mode IN ('INHERIT', 'VALUE', 'BLANK')),
    override_source_id         uuid        REFERENCES source (source_id),
    capture_date_mode          text        NOT NULL DEFAULT 'INHERIT' CHECK (capture_date_mode IN ('INHERIT', 'VALUE', 'BLANK')),
    override_capture_date      date,
    researcher_mode            text        NOT NULL DEFAULT 'INHERIT' CHECK (researcher_mode IN ('INHERIT', 'VALUE', 'BLANK')),
    override_researcher        text        CHECK (override_researcher IS NULL OR length(btrim(override_researcher)) BETWEEN 1 AND 200),
    equipment_method_mode      text        NOT NULL DEFAULT 'INHERIT' CHECK (equipment_method_mode IN ('INHERIT', 'VALUE', 'BLANK')),
    override_equipment_method  text        CHECK (override_equipment_method IS NULL OR length(btrim(override_equipment_method)) BETWEEN 1 AND 500),
    start_new_record           boolean     NOT NULL DEFAULT false,
    record_group               text        CHECK (record_group IS NULL OR length(btrim(record_group)) BETWEEN 1 AND 100),
    page_designation           text        CHECK (page_designation IS NULL OR length(btrim(page_designation)) BETWEEN 1 AND 50),
    classification_code        text        REFERENCES record_classification (code),
    applied_record_id          uuid,
    applied_run_id             uuid,
    added_at                   timestamptz NOT NULL,
    added_by                   uuid        REFERENCES app_user (user_id),
    row_version                bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),
    CONSTRAINT file_capture_context_sequence UNIQUE (capture_session_id, sequence_number) DEFERRABLE INITIALLY IMMEDIATE,
    CHECK ((repository_mode = 'VALUE') = (override_repository_id IS NOT NULL)),
    CHECK ((source_mode = 'VALUE') = (override_source_id IS NOT NULL)),
    CHECK ((capture_date_mode = 'VALUE') = (override_capture_date IS NOT NULL)),
    CHECK ((researcher_mode = 'VALUE') = (override_researcher IS NOT NULL)),
    CHECK ((equipment_method_mode = 'VALUE') = (override_equipment_method IS NOT NULL)),
    CHECK ((applied_record_id IS NULL) = (applied_run_id IS NULL))
);
CREATE INDEX file_capture_context_session ON file_capture_context (capture_session_id, sequence_number);
CREATE TRIGGER file_capture_context_identity_immutable
    BEFORE UPDATE ON file_capture_context
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('file_id', 'capture_session_id', 'added_at', 'added_by');
CREATE TRIGGER file_capture_context_no_truncate
    BEFORE TRUNCATE ON file_capture_context FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Once grouped, a row's application is permanent: it cannot be undone or re-pointed, and a grouped row cannot be
-- removed from its session (its Record links are corrected on the Record instead).
CREATE FUNCTION file_capture_context_applied_final() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.applied_record_id IS NOT NULL THEN
            RAISE EXCEPTION 'File % has been grouped in its capture session and cannot be removed from it', OLD.file_id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.applied_record_id IS NOT NULL AND (NEW.applied_record_id IS DISTINCT FROM OLD.applied_record_id
            OR NEW.applied_run_id IS DISTINCT FROM OLD.applied_run_id) THEN
        RAISE EXCEPTION 'The grouping of File % is permanent', OLD.file_id USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER file_capture_context_applied_final
    BEFORE UPDATE OR DELETE ON file_capture_context FOR EACH ROW EXECUTE FUNCTION file_capture_context_applied_final();
