-- V0001 - Phase 0 foundation.
--
-- Technical data only. No historical/domain tables are created in Phase 0 (PHASE-0 "data scope").
-- The schema "gas" is created by the migration tool before this script runs; every application
-- table lives there, never in "public".
--
-- schema_identity marks this database as a Genealogy Archive System database so the application
-- can fail visibly when pointed at the wrong database. It is NOT the Archive ID; the persistent
-- globally unique Archive ID is introduced in Phase 1.

CREATE TABLE schema_identity (
    singleton    boolean     PRIMARY KEY DEFAULT true CHECK (singleton),
    application  text        NOT NULL CHECK (application = 'genealogy-archive-system'),
    created_at   timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE schema_identity IS
    'Technical marker identifying this database as a Genealogy Archive System schema. Exactly one row. Not the Archive ID.';

INSERT INTO schema_identity (application) VALUES ('genealogy-archive-system');
