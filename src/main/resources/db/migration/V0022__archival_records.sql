-- V0022 - Phase 3: Archival Records, File-to-Record links, Record Roles, Citations and courthouse grouping runs
-- (BUILD-SPEC s.17, s.19, s.40; ADR-0011; decisions D3-01, D3-02, D3-04, D3-05, D3-07, D3-10, D3-12; plan
-- section 10).
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
    -- D3-10: a researcher-entered verbatim transcription and a separate researcher-written abstract. Neither is OCR
    -- nor a Source Statement (Phase 5).
    transcription_text   text        CHECK (transcription_text IS NULL OR length(btrim(transcription_text)) BETWEEN 1 AND 100000),
    abstract_text        text        CHECK (abstract_text IS NULL OR length(btrim(abstract_text)) BETWEEN 1 AND 20000),
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

-- File-to-Record (s.19, s.40). sequence_number orders images within the Record; page_designation is the
-- court/source page as written, never derived from the sequence (R-001). A link is a correctable association.
CREATE TABLE record_file (
    record_id         uuid        NOT NULL REFERENCES archival_record (record_id),
    file_id           uuid        NOT NULL REFERENCES archival_file (file_id),
    sequence_number   integer     NOT NULL CHECK (sequence_number >= 1),
    page_designation  text        CHECK (page_designation IS NULL OR length(btrim(page_designation)) BETWEEN 1 AND 50),
    note              text        CHECK (note IS NULL OR length(btrim(note)) BETWEEN 1 AND 2000),
    linked_at         timestamptz NOT NULL,
    linked_by         uuid        REFERENCES app_user (user_id),
    PRIMARY KEY (record_id, file_id)
);
CREATE INDEX record_file_file ON record_file (file_id);
CREATE TRIGGER record_file_identity_immutable
    BEFORE UPDATE ON record_file
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('record_id', 'file_id', 'linked_at', 'linked_by');
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

-- Courthouse grouping runs (s.40, D3-02, plan section 10.4, ADR-0011 s.4 and the owner safeguards). A run is one
-- application of a session's worksheet: durable identity, who started it, whether its starter could see restricted
-- Files (which decides who may see the run), its state and its tallies. Work is done in bounded transactions of at
-- most 1,000 rows; each records its chunk here and marks its rows applied with the run and the Record, so a crash
-- loses at most the chunk in progress and a replay continues from durable state. A group whose boundaries depend on
-- rows its applier cannot see is held (counted, not applied). A run found RUNNING with no live owner is closed as
-- INTERRUPTED when the next run starts. Permanent.
CREATE TABLE grouping_run (
    run_id              uuid        PRIMARY KEY,
    capture_session_id  uuid        NOT NULL REFERENCES capture_session (capture_session_id),
    state               text        NOT NULL DEFAULT 'RUNNING' CHECK (state IN ('RUNNING', 'COMPLETED', 'INTERRUPTED')),
    started_at          timestamptz NOT NULL,
    started_by          uuid        REFERENCES app_user (user_id),
    restricted_view     boolean     NOT NULL,
    finished_at         timestamptz,
    rows_planned        integer     NOT NULL DEFAULT 0 CHECK (rows_planned >= 0),
    rows_applied        integer     NOT NULL DEFAULT 0 CHECK (rows_applied >= 0),
    rows_ungrouped      integer     NOT NULL DEFAULT 0 CHECK (rows_ungrouped >= 0),
    rows_conflicted     integer     NOT NULL DEFAULT 0 CHECK (rows_conflicted >= 0),
    rows_held           integer     NOT NULL DEFAULT 0 CHECK (rows_held >= 0),
    groups_held         integer     NOT NULL DEFAULT 0 CHECK (groups_held >= 0),
    records_created     integer     NOT NULL DEFAULT 0 CHECK (records_created >= 0),
    records_extended    integer     NOT NULL DEFAULT 0 CHECK (records_extended >= 0),
    CHECK ((state = 'RUNNING') = (finished_at IS NULL))
);
CREATE INDEX grouping_run_session ON grouping_run (capture_session_id, started_at DESC);
CREATE TRIGGER grouping_run_identity_immutable
    BEFORE UPDATE ON grouping_run
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('run_id', 'capture_session_id', 'started_at', 'started_by',
                                                         'restricted_view');
CREATE TRIGGER grouping_run_no_delete BEFORE DELETE ON grouping_run FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER grouping_run_no_truncate BEFORE TRUNCATE ON grouping_run FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- A run's final state never changes.
CREATE FUNCTION grouping_run_final() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.state <> 'RUNNING' THEN
        RAISE EXCEPTION 'Grouping run % is finished', OLD.run_id USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER grouping_run_final BEFORE UPDATE ON grouping_run FOR EACH ROW EXECUTE FUNCTION grouping_run_final();

-- One committed chunk (at most 1,000 rows) of a group in a run: the Record it created or extended, how many rows
-- it applied, and whether it completed the group (every intended row of the group applied). A group larger than
-- one chunk has several rows here; partial progress is never marked complete.
CREATE TABLE grouping_run_group (
    run_id           uuid        NOT NULL REFERENCES grouping_run (run_id),
    group_key        text        NOT NULL CHECK (length(group_key) BETWEEN 1 AND 200),
    chunk_number     integer     NOT NULL CHECK (chunk_number >= 1),
    record_id        uuid        NOT NULL REFERENCES archival_record (record_id),
    created          boolean     NOT NULL,
    rows_applied     integer     NOT NULL CHECK (rows_applied BETWEEN 1 AND 1000),
    group_completed  boolean     NOT NULL,
    completed_at     timestamptz NOT NULL,
    PRIMARY KEY (run_id, group_key, chunk_number)
);
CREATE TRIGGER grouping_run_group_immutable
    BEFORE UPDATE OR DELETE ON grouping_run_group FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER grouping_run_group_no_truncate
    BEFORE TRUNCATE ON grouping_run_group FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

ALTER TABLE file_capture_context
    ADD CONSTRAINT file_capture_context_applied_record FOREIGN KEY (applied_record_id) REFERENCES archival_record (record_id),
    ADD CONSTRAINT file_capture_context_applied_run FOREIGN KEY (applied_run_id) REFERENCES grouping_run (run_id);

-- The stable group-to-Record identity of a session: which Record each group created. Keys are 'g:' plus the
-- normalised explicit record_group (so later rows - in later runs or other upload batches - with the same group
-- join it) or 's:' plus the File id of the Start New Record row (so a group processed in several chunks or runs
-- keeps one Record). Written in the transaction that creates the Record. Permanent.
CREATE TABLE capture_session_group (
    capture_session_id  uuid        NOT NULL REFERENCES capture_session (capture_session_id),
    group_key           text        NOT NULL CHECK (group_key ~ '^[gs]:' AND length(btrim(group_key)) BETWEEN 3 AND 110),
    record_id           uuid        NOT NULL REFERENCES archival_record (record_id),
    created_at          timestamptz NOT NULL,
    PRIMARY KEY (capture_session_id, group_key)
);
CREATE TRIGGER capture_session_group_immutable
    BEFORE UPDATE OR DELETE ON capture_session_group FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER capture_session_group_no_truncate
    BEFORE TRUNCATE ON capture_session_group FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Citation foundation (s.17; plan section 10.1; ADR-0011 s.6): refers to a Source, a Record, or both, with a
-- locator and the citation as entered. Its targets are part of its identity (a different target is a different
-- Citation). original_locator keeps the locator exactly as first entered and never changes; the current locator,
-- text and note are correctable with history. A Record-only Citation stores no Source (none is inferred).
-- Reclassifying the cited Record changes nothing here.
CREATE TABLE citation (
    citation_id      uuid        PRIMARY KEY,
    friendly_number  bigint      NOT NULL UNIQUE CHECK (friendly_number >= 1),
    source_id        uuid        REFERENCES source (source_id),
    record_id        uuid        REFERENCES archival_record (record_id),
    original_locator text        CHECK (original_locator IS NULL OR length(btrim(original_locator)) BETWEEN 1 AND 500),
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
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('citation_id', 'friendly_number', 'source_id', 'record_id',
                                                         'original_locator', 'created_at', 'created_by');
CREATE TRIGGER citation_no_delete BEFORE DELETE ON citation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER citation_no_truncate BEFORE TRUNCATE ON citation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
