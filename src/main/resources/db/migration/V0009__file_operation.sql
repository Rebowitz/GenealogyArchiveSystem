-- V0009 - Phase 2: persistent archival file operations (BUILD-SPEC s.35, s.36, s.46; decision P2-J).
--
-- Every multi-step file action (ingest, destroy, repair) has a file_operation row whose state is the
-- ONLY source of truth for recovery: success is never inferred from filenames (TX-002). Planned
-- targets (content ID, generation, storage path) are written BEFORE any file is created.
-- file_operation_event is the append-only archival file-operation history. It is separate from
-- background_job (scheduling plumbing) and change_history (researcher-facing changes).

CREATE TABLE file_upload_batch (
    batch_id     uuid        PRIMARY KEY,
    created_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid        NOT NULL REFERENCES app_user (user_id),
    volume_id    uuid        NOT NULL REFERENCES storage_volume (volume_id),
    note         text        CHECK (note IS NULL OR length(note) BETWEEN 1 AND 2000)
);

CREATE TRIGGER file_upload_batch_immutable
    BEFORE UPDATE ON file_upload_batch
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('batch_id', 'created_at', 'created_by', 'volume_id');
CREATE TRIGGER file_upload_batch_no_delete
    BEFORE DELETE ON file_upload_batch FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_upload_batch_no_truncate
    BEFORE TRUNCATE ON file_upload_batch FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

CREATE TABLE file_operation (
    operation_id          uuid        PRIMARY KEY,
    kind                  text        NOT NULL CHECK (kind IN ('INGEST', 'DESTROY', 'REPAIR')),
    state                 text        NOT NULL CHECK (state IN (
                              'RECEIVING', 'STAGED', 'PROMOTING', 'PROMOTED', 'COMMITTED', 'COMPLETED',
                              'WAITING_FOR_VOLUME', 'BLOCKED', 'ABANDONED', 'CANCELLED', 'REJECTED',
                              'CLEANUP_PENDING', 'CLEANUP_INCOMPLETE')),
    actor_kind            text        NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM')),
    actor_user_id         uuid        REFERENCES app_user (user_id),
    actor_label           text        NOT NULL CHECK (length(actor_label) BETWEEN 1 AND 200),
    volume_id             uuid        REFERENCES storage_volume (volume_id),
    batch_id              uuid        REFERENCES file_upload_batch (batch_id),
    item_key              text        CHECK (item_key IS NULL OR item_key ~ '^[A-Za-z0-9_-]{1,64}$'),
    staging_name          text        CHECK (staging_name IS NULL OR staging_name ~ '^[0-9a-f-]{36}\.part$'),
    -- Planned content identity: the file_content row exists only once the operation commits.
    content_id            uuid,
    target_generation     integer     CHECK (target_generation IS NULL OR target_generation >= 1),
    target_path           text        CHECK (target_path IS NULL OR length(target_path) BETWEEN 1 AND 1024),
    file_id               uuid,
    -- Ingestion request (provenance captured at the time of upload).
    original_filename     text        CHECK (original_filename IS NULL OR length(original_filename) BETWEEN 1 AND 1024),
    client_last_modified  timestamptz,
    source_note           text        CHECK (source_note IS NULL OR length(source_note) BETWEEN 1 AND 2000),
    requested_restriction text        CHECK (requested_restriction IS NULL OR requested_restriction IN ('NORMAL', 'RESTRICTED')),
    parent_file_id        uuid,
    derivation_method     text        CHECK (derivation_method IS NULL OR length(derivation_method) BETWEEN 1 AND 200),
    -- Measurements taken by the receiving web process while staging.
    expected_size         bigint      CHECK (expected_size IS NULL OR expected_size >= 0),
    expected_sha256       bytea       CHECK (expected_sha256 IS NULL OR octet_length(expected_sha256) = 32),
    received_bytes        bigint      NOT NULL DEFAULT 0 CHECK (received_bytes >= 0),
    last_progress_at      timestamptz,
    outcome               text        CHECK (outcome IS NULL OR outcome ~ '^[A-Z][A-Z_]{0,63}$'),
    blocked_reason        text        CHECK (blocked_reason IS NULL OR blocked_reason IN (
                              'COLLISION', 'PROTECTION', 'STAGING_INVALID', 'VERIFY_MISMATCH', 'UNSAFE_PATH',
                              'STATE_UNKNOWN')),
    message               text        CHECK (message IS NULL OR length(message) <= 4000),
    quarantine_path       text        CHECK (quarantine_path IS NULL OR length(quarantine_path) BETWEEN 1 AND 1024),
    staging_cleaned       boolean     NOT NULL DEFAULT false,
    attempts              integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    completed_at          timestamptz,
    CHECK ((actor_kind = 'USER') = (actor_user_id IS NOT NULL)),
    CHECK ((state = 'BLOCKED') = (blocked_reason IS NOT NULL)),
    CHECK ((item_key IS NULL) OR (batch_id IS NOT NULL)),
    UNIQUE (batch_id, item_key)
);

CREATE INDEX file_operation_open ON file_operation (state, updated_at)
    WHERE state NOT IN ('COMPLETED', 'ABANDONED', 'CANCELLED', 'REJECTED');
CREATE INDEX file_operation_file ON file_operation (file_id);
CREATE INDEX file_operation_content ON file_operation (content_id);

CREATE TRIGGER file_operation_identity_immutable
    BEFORE UPDATE ON file_operation
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change(
        'operation_id', 'kind', 'actor_kind', 'actor_user_id', 'actor_label', 'batch_id', 'item_key',
        'staging_name', 'created_at');
CREATE TRIGGER file_operation_no_delete
    BEFORE DELETE ON file_operation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_operation_no_truncate
    BEFORE TRUNCATE ON file_operation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

COMMENT ON TABLE file_operation IS
    'Persistent state of multi-step archival file operations; the basis of crash recovery. Not background_job.';

-- Append-only archival file-operation history (P2-J).
CREATE TABLE file_operation_event (
    operation_id  uuid        NOT NULL REFERENCES file_operation (operation_id),
    seq           integer     NOT NULL CHECK (seq >= 1),
    occurred_at   timestamptz NOT NULL DEFAULT clock_timestamp(),
    from_state    text,
    to_state      text        NOT NULL,
    detail        text        CHECK (detail IS NULL OR length(detail) <= 4000),
    PRIMARY KEY (operation_id, seq)
);

CREATE TRIGGER file_operation_event_append_only
    BEFORE UPDATE OR DELETE ON file_operation_event FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_operation_event_no_truncate
    BEFORE TRUNCATE ON file_operation_event FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
