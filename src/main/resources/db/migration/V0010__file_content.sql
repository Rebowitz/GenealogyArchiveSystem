-- V0010 - Phase 2: stored content (BUILD-SPEC s.26, s.27, s.32).
--
-- file_content is a body of preserved bytes identified by its own UUID. Several archival Files may
-- share one content when an exact duplicate is ingested (P2-B): each File keeps its own identity and
-- provenance; only the physical bytes are shared. Size and SHA-256 are the authoritative ingestion
-- measurements and are immutable: an integrity scan never replaces them (IV-003).
--
-- file_content_generation records every physical location the content has had on its volume. The
-- first generation is written at ingestion; a verified repair writes a new generation and keeps the
-- damaged one accountable (QUARANTINE_PENDING) until it is confirmed in quarantine.

CREATE TABLE file_content (
    content_id            uuid        PRIMARY KEY,
    volume_id             uuid        NOT NULL REFERENCES storage_volume (volume_id),
    byte_size             bigint      NOT NULL CHECK (byte_size >= 0),
    sha256                bytea       NOT NULL CHECK (octet_length(sha256) = 32),
    state                 text        NOT NULL CHECK (state IN ('ACTIVE', 'MISSING', 'DAMAGED', 'DESTROY_PENDING', 'DESTROYED')),
    created_by_operation  uuid        NOT NULL UNIQUE REFERENCES file_operation (operation_id),
    created_at            timestamptz NOT NULL DEFAULT now(),
    last_verified_at      timestamptz,
    row_version           bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);

CREATE INDEX file_content_duplicate_candidates ON file_content (byte_size, sha256) WHERE state = 'ACTIVE';

CREATE TRIGGER file_content_identity_immutable
    BEFORE UPDATE ON file_content
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change(
        'content_id', 'volume_id', 'byte_size', 'sha256', 'created_by_operation', 'created_at');
CREATE TRIGGER file_content_no_delete
    BEFORE DELETE ON file_content FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_content_no_truncate
    BEFORE TRUNCATE ON file_content FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

CREATE TABLE file_content_generation (
    content_id       uuid        NOT NULL REFERENCES file_content (content_id),
    generation       integer     NOT NULL CHECK (generation >= 1),
    volume_id        uuid        NOT NULL REFERENCES storage_volume (volume_id),
    storage_path     text        NOT NULL CHECK (length(storage_path) BETWEEN 1 AND 1024),
    state            text        NOT NULL CHECK (state IN ('CURRENT', 'QUARANTINE_PENDING', 'QUARANTINED', 'MISSING', 'DESTROYED')),
    quarantine_path  text        CHECK (quarantine_path IS NULL OR length(quarantine_path) BETWEEN 1 AND 1024),
    operation_id     uuid        NOT NULL REFERENCES file_operation (operation_id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (content_id, generation),
    CHECK ((state = 'QUARANTINED') = (quarantine_path IS NOT NULL))
);

-- A physical location is never reused, and a content has at most one current generation.
CREATE UNIQUE INDEX file_content_generation_location ON file_content_generation (volume_id, storage_path);
CREATE UNIQUE INDEX file_content_generation_current ON file_content_generation (content_id) WHERE state = 'CURRENT';

CREATE TRIGGER file_content_generation_identity_immutable
    BEFORE UPDATE ON file_content_generation
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change(
        'content_id', 'generation', 'volume_id', 'storage_path', 'operation_id', 'created_at');
CREATE TRIGGER file_content_generation_no_delete
    BEFORE DELETE ON file_content_generation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_content_generation_no_truncate
    BEFORE TRUNCATE ON file_content_generation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
