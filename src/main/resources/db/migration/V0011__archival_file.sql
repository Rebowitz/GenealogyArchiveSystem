-- V0011 - Phase 2: archival Files (BUILD-SPEC s.8, s.9, s.26, s.30, s.34, s.37).
--
-- An archival File is the permanent archival object: global UUIDv7 identity, friendly ID (F00001),
-- immutable capture provenance (original filename, who/when/how it was ingested) and a reference to
-- its stored content. Each deliberate ingestion creates its own File even when its bytes already
-- exist (P2-B). ingest_operation_id is unique: retrying an operation can never create a second File
-- (TX-003, BI-008). A destroyed File's row remains as its tombstone: identifiers stay retired.

CREATE SEQUENCE friendly_id_seq_archival_file NO CYCLE;
INSERT INTO friendly_id_series (object_type, prefix, min_digits, sequence_name)
VALUES ('archival_file', 'F', 5, 'friendly_id_seq_archival_file');

CREATE TABLE archival_file (
    file_id               uuid        PRIMARY KEY,
    friendly_number       bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    content_id            uuid        NOT NULL REFERENCES file_content (content_id),
    ingest_operation_id   uuid        NOT NULL UNIQUE REFERENCES file_operation (operation_id),
    original_filename     text        NOT NULL CHECK (length(original_filename) BETWEEN 1 AND 1024),
    managed_filename      text        NOT NULL CHECK (length(btrim(managed_filename)) BETWEEN 1 AND 255),
    media_type            text        NOT NULL CHECK (media_type ~ '^[a-z]+/[a-z0-9.+-]{1,100}$'),
    ingested_at           timestamptz NOT NULL,
    ingested_by           uuid        NOT NULL REFERENCES app_user (user_id),
    batch_id              uuid        REFERENCES file_upload_batch (batch_id),
    client_last_modified  timestamptz,
    source_note           text        CHECK (source_note IS NULL OR length(source_note) BETWEEN 1 AND 2000),
    lifecycle             text        NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle IN ('ACTIVE', 'DELETED', 'DESTROYED')),
    restriction           text        NOT NULL DEFAULT 'NORMAL' CHECK (restriction IN ('NORMAL', 'RESTRICTED')),
    display_rotation      integer     NOT NULL DEFAULT 0 CHECK (display_rotation IN (0, 90, 180, 270)),
    deleted_at            timestamptz,
    deleted_by            uuid        REFERENCES app_user (user_id),
    destroyed_at          timestamptz,
    destroyed_by          uuid        REFERENCES app_user (user_id),
    destruction_reason    text        CHECK (destruction_reason IS NULL OR length(destruction_reason) BETWEEN 1 AND 2000),
    replacement_file_id   uuid        REFERENCES archival_file (file_id),
    row_version           bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),
    CHECK ((lifecycle = 'DESTROYED') = (destroyed_at IS NOT NULL)),
    CHECK ((lifecycle = 'ACTIVE') = (deleted_at IS NULL)),
    CHECK (replacement_file_id IS NULL OR replacement_file_id <> file_id)
);

CREATE INDEX archival_file_content ON archival_file (content_id);
CREATE INDEX archival_file_recent ON archival_file (ingested_at DESC);

CREATE TRIGGER archival_file_identity_immutable
    BEFORE UPDATE ON archival_file
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change(
        'file_id', 'friendly_number', 'content_id', 'ingest_operation_id', 'original_filename', 'ingested_at',
        'ingested_by', 'batch_id', 'client_last_modified', 'source_note');
CREATE TRIGGER archival_file_no_delete
    BEFORE DELETE ON archival_file FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER archival_file_no_truncate
    BEFORE TRUNCATE ON archival_file FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- A destroyed File is a tombstone: it never becomes ACTIVE or DELETED again.
CREATE FUNCTION archival_file_tombstone_final() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.lifecycle = 'DESTROYED' AND NEW.lifecycle <> 'DESTROYED' THEN
        RAISE EXCEPTION 'A destroyed File cannot be restored; its identifiers are retired'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER archival_file_tombstone_final
    BEFORE UPDATE ON archival_file FOR EACH ROW EXECUTE FUNCTION archival_file_tombstone_final();

-- Derivatives (s.30, F-009): own identity and content, plus a parent link and method.
CREATE TABLE file_derivation (
    file_id         uuid        PRIMARY KEY REFERENCES archival_file (file_id),
    parent_file_id  uuid        NOT NULL REFERENCES archival_file (file_id),
    method          text        NOT NULL CHECK (length(btrim(method)) BETWEEN 1 AND 200),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CHECK (file_id <> parent_file_id)
);
CREATE INDEX file_derivation_parent ON file_derivation (parent_file_id);

CREATE TRIGGER file_derivation_immutable
    BEFORE UPDATE OR DELETE ON file_derivation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_derivation_no_truncate
    BEFORE TRUNCATE ON file_derivation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

ALTER TABLE file_operation
    -- Deferred: the compare-and-set that commits an operation records its new File ID, and the File
    -- row is inserted later in the same transaction (both or neither are committed).
    ADD CONSTRAINT file_operation_file_fk FOREIGN KEY (file_id) REFERENCES archival_file (file_id)
        DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT file_operation_parent_fk FOREIGN KEY (parent_file_id) REFERENCES archival_file (file_id);
