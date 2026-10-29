-- V0004 - Phase 1: researcher-facing Change History (BUILD-SPEC s.45).
--
-- Records significant modifications: who, when, which object, what changed. Not a database
-- transaction log, not a log of reads or sign-ins, and not the diagnostic log. It never stores
-- secrets. Append-only.

CREATE TABLE change_history (
    change_id      uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    actor_kind     text        NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM')),
    actor_user_id  uuid        REFERENCES app_user (user_id),
    actor_label    text        NOT NULL CHECK (length(actor_label) BETWEEN 1 AND 200),
    object_type    text        NOT NULL CHECK (object_type ~ '^[a-z][a-z0-9_]{0,63}$'),
    object_id      uuid        NOT NULL,
    change_kind    text        NOT NULL CHECK (change_kind ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    field_changes  jsonb       NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(field_changes) = 'array'),
    reason         text        CHECK (reason IS NULL OR length(reason) BETWEEN 1 AND 4000),
    CHECK ((actor_kind = 'USER') = (actor_user_id IS NOT NULL))
);

CREATE INDEX change_history_object ON change_history (object_type, object_id, occurred_at);
CREATE INDEX change_history_recent ON change_history (occurred_at DESC);

CREATE TRIGGER change_history_append_only
    BEFORE UPDATE OR DELETE ON change_history
    FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER change_history_no_truncate
    BEFORE TRUNCATE ON change_history
    FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
