-- V0023 - Phase 3: researcher-driven consolidation of redundant captures (BUILD-SPEC s.33; ACCEPTANCE-SPEC
-- F-006, F-007; P2-G, D3-03; ADR-0011).
--
-- A consolidation records the researcher's decision: which File is preferred and which Files were redundant
-- captures. Redundant Files go to recoverable deletion; their own Record links stay as provenance, and a link
-- carried to the preferred File names this consolidation (record_file.consolidation_id). No derivation is ever
-- created between members. Permanent.

CREATE TABLE file_consolidation (
    consolidation_id   uuid        PRIMARY KEY,
    preferred_file_id  uuid        NOT NULL REFERENCES archival_file (file_id),
    reason             text        CHECK (reason IS NULL OR length(btrim(reason)) BETWEEN 1 AND 4000),
    decided_at         timestamptz NOT NULL,
    decided_by         uuid        REFERENCES app_user (user_id)
);
CREATE INDEX file_consolidation_preferred ON file_consolidation (preferred_file_id);
CREATE TRIGGER file_consolidation_immutable
    BEFORE UPDATE OR DELETE ON file_consolidation FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_consolidation_no_truncate
    BEFORE TRUNCATE ON file_consolidation FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

CREATE TABLE file_consolidation_member (
    consolidation_id  uuid NOT NULL REFERENCES file_consolidation (consolidation_id),
    file_id           uuid NOT NULL REFERENCES archival_file (file_id),
    PRIMARY KEY (consolidation_id, file_id)
);
CREATE INDEX file_consolidation_member_file ON file_consolidation_member (file_id);
CREATE TRIGGER file_consolidation_member_immutable
    BEFORE UPDATE OR DELETE ON file_consolidation_member FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER file_consolidation_member_no_truncate
    BEFORE TRUNCATE ON file_consolidation_member FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- The preferred File is never also a redundant member of its own consolidation.
CREATE FUNCTION file_consolidation_member_not_preferred() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM file_consolidation c
                WHERE c.consolidation_id = NEW.consolidation_id AND c.preferred_file_id = NEW.file_id) THEN
        RAISE EXCEPTION 'The preferred File of consolidation % cannot also be one of its redundant Files',
            NEW.consolidation_id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER file_consolidation_member_not_preferred
    BEFORE INSERT ON file_consolidation_member
    FOR EACH ROW EXECUTE FUNCTION file_consolidation_member_not_preferred();

ALTER TABLE record_file
    ADD CONSTRAINT record_file_consolidation FOREIGN KEY (consolidation_id) REFERENCES file_consolidation (consolidation_id);
