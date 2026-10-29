-- V0007 - Phase 1: archive-local friendly identifier infrastructure (BUILD-SPEC s.8).
--
-- A friendly ID (e.g. P00127) is a presentation/researcher identifier, never the underlying
-- global identity (each object's UUIDv7). Each object type that uses friendly IDs is registered
-- here by the migration of the phase that introduces that type, together with its own dedicated
-- PostgreSQL sequence. Sequences never hand out a number twice, so deletion, retirement, merge or
-- a rolled-back transaction never makes an identifier reusable; gaps are valid.
-- Phase 1 registers no domain object types.

CREATE TABLE friendly_id_series (
    object_type    text        PRIMARY KEY CHECK (object_type ~ '^[a-z][a-z0-9_]{0,63}$'),
    prefix         text        NOT NULL UNIQUE CHECK (prefix ~ '^[A-Z]{1,4}$'),
    min_digits     integer     NOT NULL DEFAULT 5 CHECK (min_digits BETWEEN 1 AND 9),
    sequence_name  text        NOT NULL UNIQUE CHECK (sequence_name ~ '^friendly_id_seq_[a-z0-9_]{1,60}$'),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER friendly_id_series_immutable
    BEFORE UPDATE ON friendly_id_series
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('object_type', 'prefix', 'min_digits', 'sequence_name', 'created_at');
CREATE TRIGGER friendly_id_series_no_delete
    BEFORE DELETE ON friendly_id_series
    FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER friendly_id_series_no_truncate
    BEFORE TRUNCATE ON friendly_id_series
    FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
