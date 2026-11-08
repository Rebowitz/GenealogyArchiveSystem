-- V0022 - Phase 3: Archival Records, File-to-Record links, Record Roles, Citations and courthouse grouping
-- (BUILD-SPEC s.17, s.19, s.40; ADR-0011; decisions D3-01, D3-02, D3-04, D3-05, D3-07, D3-10, D3-12).
--
-- File-to-Record is many-to-many (R-003). Court/source page designation is distinct from image sequence
-- (R-001). Classification is correctable and never changes identity or physical storage (R-006). A Record
-- needs nothing beyond its identity (A-003). Records are never deleted, only moved to recoverable deletion.

CREATE SEQUENCE friendly_id_seq_archival_record NO CYCLE;
CREATE SEQUENCE friendly_id_seq_citation NO CYCLE;
INSERT INTO friendly_id_series (object_type, prefix, min_digits, sequence_name) VALUES
    ('archival_record', 'R', 5, 'friendly_id_seq_archival_record'),
    ('citation', 'CT', 5, 'friendly_id_seq_citation');

CREATE TABLE archival_record (
    record_id            uuid        PRIMARY KEY,
    friendly_number      bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    classification_code  text        REFERENCES record_classification (code),
    summary              text        CHECK (summary IS NULL OR length(btrim(summary)) BETWEEN 1 AND 500),
    source_id            uuid        REFERENCES source (source_id),
    capture_session_id   uuid        REFERENCES capture_session (capture_session_id),
    source_locator       text        CHECK (source_locator IS NULL OR length(btrim(source_locator)) BETWEEN 1 AND 500),
    -- Source expressions as written (A-004, D3-05); a structured interpretation is added alongside in Phase 4.
    record_date_text     text        CHECK (record_date_text IS NULL OR length(btrim(record_date_text)) BETWEEN 1 AND 200),
    filing_date_text     text        CHECK (filing_date_text IS NULL OR length(btrim(filing_date_text)) BETWEEN 1 AND 200),
    -- Researcher-entered transcription or abstract (D3-10); never OCR.
    transcription        text        CHECK (transcription IS NULL OR length(btrim(transcription)) BETWEEN 1 AND 100000),
    restriction          text        NOT NULL DEFAULT 'NORMAL' CHECK (restriction IN ('NORMAL', 'RESTRICTED')),
    lifecycle            text        NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle IN ('ACTIVE', 'DELETED')),
    deleted_at           timestamptz,
    deleted_by           uuid        REFERENCES app_user (user_id),
    created_at           timestamptz NOT NULL,
    created_by           uuid        REFERENCES app_user (user_id),
    row_version          bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),
    CHECK ((lifecycle = 'DELETED') = (deleted_at IS NOT NULL))
);
CREATE INDEX archival_record_source ON archival_record (source_id);
CREATE INDEX archival_record_session ON archival_record (capture_session_id);
CREATE INDEX archival_record_recent ON archival_record (created_at DESC);
CREATE TRIGGER archival_record_identity_immutable
    BEFORE UPDATE ON archival_record
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('record_id', 'friendly_number', 'capture_session_id',
                                                         'created_at', 'created_by');
CREATE TRIGGER archival_record_no_delete BEFORE DELETE ON archival_record FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER archival_record_no_truncate BEFORE TRUNCATE ON archival_record FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

ALTER TABLE file_capture_context
    ADD CONSTRAINT file_capture_context_applied_record FOREIGN KEY (applied_record_id) REFERENCES archival_record (record_id);

-- File-to-Record (s.19, s.40). sequence_number orders images within the Record; page_designation is the
-- court/source page as written, never derived from the sequence. A link carried to a preferred File by
-- consolidation names the consolidation and the File it came from (F-006); the original link is kept.
CREATE TABLE record_file (
    record_id             uuid        NOT NULL REFERENCES archival_record (record_id),
    file_id               uuid        NOT NULL REFERENCES archival_file (file_id),
    sequence_number       integer     NOT NULL CHECK (sequence_number >= 1),
    page_designation      text        CHECK (page_designation IS NULL OR length(btrim(page_designation)) BETWEEN 1 AND 50),
    note                  text        CHECK (note IS NULL OR length(btrim(note)) BETWEEN 1 AND 2000),
    consolidation_id      uuid,
    carried_from_file_id  uuid        REFERENCES archival_file (file_id),
    linked_at             timestamptz NOT NULL,
    linked_by             uuid        REFERENCES app_user (user_id),
    PRIMARY KEY (record_id, file_id),
    CHECK ((consolidation_id IS NULL) = (carried_from_file_id IS NULL)),
    CHECK (carried_from_file_id IS NULL OR carried_from_file_id <> file_id)
);
CREATE INDEX record_file_file ON record_file (file_id);
CREATE TRIGGER record_file_identity_immutable
    BEFORE UPDATE ON record_file
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('record_id', 'file_id', 'consolidation_id',
                                                         'carried_from_file_id', 'linked_at', 'linked_by');
CREATE TRIGGER record_file_no_truncate BEFORE TRUNCATE ON record_file FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Record Roles (s.19; D3-01). The party is kept as written and may be absent (unreadable). A Role creates no
-- Person, Person Relationship or Event Role (A-009, EV-004).
CREATE TABLE record_role (
    role_id           uuid        PRIMARY KEY,
    record_id         uuid        NOT NULL REFERENCES archival_record (record_id),
    role_code         text        NOT NULL REFERENCES record_role_type (code),
    party_as_written  text        CHECK (party_as_written IS NULL OR length(btrim(party_as_written)) BETWEEN 1 AND 500),
    note              text        CHECK (note IS NULL OR length(btrim(note)) BETWEEN 1 AND 2000),
    sequence_number   integer     NOT NULL CHECK (sequence_number >= 1),
    created_at        timestamptz NOT NULL,
    created_by        uuid        REFERENCES app_user (user_id),
    row_version       bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE INDEX record_role_record ON record_role (record_id, sequence_number);
CREATE TRIGGER record_role_identity_immutable
    BEFORE UPDATE ON record_role
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('role_id', 'record_id', 'created_at', 'created_by');
CREATE TRIGGER record_role_no_truncate BEFORE TRUNCATE ON record_role FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Courthouse grouping (s.40, D3-02): which Record each explicit record_group of a session created, so later rows
-- with the same group join it. Permanent.
CREATE TABLE capture_session_group (
    capture_session_id  uuid        NOT NULL REFERENCES capture_session (capture_session_id),
    group_key           text        NOT NULL CHECK (length(btrim(group_key)) BETWEEN 1 AND 100),
    record_id           uuid        NOT NULL REFERENCES archival_record (record_id),
    created_at          timestamptz NOT NULL,
    PRIMARY KEY (capture_session_id, group_key)
);
CREATE TRIGGER capture_session_group_immutable
    BEFORE UPDATE OR DELETE ON capture_session_group FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER capture_session_group_no_truncate
    BEFORE TRUNCATE ON capture_session_group FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Citation foundation (s.17): refers to a Source and/or a Record, with a locator and the citation as entered.
CREATE TABLE citation (
    citation_id      uuid        PRIMARY KEY,
    friendly_number  bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    source_id        uuid        REFERENCES source (source_id),
    record_id        uuid        REFERENCES archival_record (record_id),
    locator          text        CHECK (locator IS NULL OR length(btrim(locator)) BETWEEN 1 AND 500),
    citation_text    text        CHECK (citation_text IS NULL OR length(btrim(citation_text)) BETWEEN 1 AND 4000),
    note             text        CHECK (note IS NULL OR length(btrim(note)) BETWEEN 1 AND 2000),
    lifecycle        text        NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle IN ('ACTIVE', 'DELETED')),
    created_at       timestamptz NOT NULL,
    created_by       uuid        REFERENCES app_user (user_id),
    row_version      bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),
    CHECK (source_id IS NOT NULL OR record_id IS NOT NULL)
);
CREATE INDEX citation_source ON citation (source_id);
CREATE INDEX citation_record ON citation (record_id);
CREATE TRIGGER citation_identity_immutable
    BEFORE UPDATE ON citation
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('citation_id', 'friendly_number', 'created_at', 'created_by');
CREATE TRIGGER citation_no_delete BEFORE DELETE ON citation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER citation_no_truncate BEFORE TRUNCATE ON citation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
