-- V0003 - Phase 1: local users and roles (BUILD-SPEC s.43). Local authentication only.
-- Users are disabled, never deleted, so change-history attribution stays resolvable.

CREATE TABLE app_user (
    user_id        uuid        PRIMARY KEY,
    username       text        NOT NULL CHECK (username ~ '^[A-Za-z0-9._-]{1,64}$'),
    display_name   text        NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 200),
    role           text        NOT NULL CHECK (role IN ('ADMINISTRATOR', 'EDITOR', 'VIEWER')),
    password_hash  text        NOT NULL CHECK (length(password_hash) BETWEEN 20 AND 500),
    enabled        boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    row_version    bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);

-- Usernames are unique regardless of letter case.
CREATE UNIQUE INDEX app_user_username_ci ON app_user (lower(username));

CREATE TRIGGER app_user_identity_immutable
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('user_id', 'username', 'created_at');
CREATE TRIGGER app_user_no_delete
    BEFORE DELETE ON app_user
    FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER app_user_no_truncate
    BEFORE TRUNCATE ON app_user
    FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
