-- V0024 - Phase 4 WP1: People and Place vocabularies, and the Historical Date value table (BUILD-SPEC s.12,
-- s.14, s.15; ADR-0012 s.2-s.4; decisions D4-04, D4-05, D4-06, D4-11, D4-25, D4-28 as modified, U-1, U-2).
--
-- Additive only: no accepted table or row is changed (UP-006). Vocabulary codes are permanent: never deleted,
-- only retired (D3-08). Seeds are only the values the specifications name (D4-11).

-- Relationship types (s.15; P-006, P-007, P-009). The category decides only whether a parentage qualifier
-- applies; it never triggers inference (s.61). Category and direction are part of a type's meaning, so they are
-- immutable like the code: changing them would reinterpret existing relationships.
CREATE TABLE relationship_type (
    code           text        PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label          text        NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100),
    inverse_label  text        NOT NULL CHECK (length(btrim(inverse_label)) BETWEEN 1 AND 100),
    directed       boolean     NOT NULL,
    category       text        NOT NULL CHECK (category IN ('PARENTAGE', 'SPOUSE', 'SIBLING', 'GUARDIANSHIP',
                                                            'DIRECT_ANCESTRY', 'SOCIAL', 'OTHER')),
    description    text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 2000),
    retired        boolean     NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    row_version    bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),
    CHECK (directed OR inverse_label = label)
);
CREATE TRIGGER relationship_type_identity_immutable
    BEFORE UPDATE ON relationship_type
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('code', 'created_at', 'category', 'directed');
CREATE TRIGGER relationship_type_no_delete
    BEFORE DELETE ON relationship_type FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER relationship_type_no_truncate
    BEFORE TRUNCATE ON relationship_type FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

INSERT INTO relationship_type (code, label, inverse_label, directed, category) VALUES
    ('PARENT_CHILD', 'Parent', 'Child', true, 'PARENTAGE'),
    ('SPOUSE', 'Spouse', 'Spouse', false, 'SPOUSE'),
    ('GUARDIAN_WARD', 'Guardian', 'Ward', true, 'GUARDIANSHIP'),
    ('SIBLING', 'Sibling', 'Sibling', false, 'SIBLING'),
    ('GRANDPARENT_GRANDCHILD', 'Grandparent', 'Grandchild', true, 'DIRECT_ANCESTRY'),
    ('MASTER_APPRENTICE', 'Master', 'Apprentice', true, 'SOCIAL'),
    ('HOUSEHOLD_MEMBER', 'Household member', 'Household member', false, 'SOCIAL');

-- Parentage qualifiers (s.15): fixed by the specification, not an administrator vocabulary (D4-11).
CREATE FUNCTION reject_fixed_vocabulary_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'The values of % are fixed by the specification and cannot be added or changed', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TABLE parentage_qualifier (
    code   text PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label  text NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100)
);
INSERT INTO parentage_qualifier (code, label) VALUES
    ('UNSPECIFIED', 'Unspecified'), ('BIOLOGICAL', 'Biological'), ('ADOPTIVE', 'Adoptive'), ('STEP', 'Step'),
    ('FOSTER', 'Foster');
CREATE TRIGGER parentage_qualifier_fixed
    BEFORE INSERT OR UPDATE ON parentage_qualifier FOR EACH ROW EXECUTE FUNCTION reject_fixed_vocabulary_change();
CREATE TRIGGER parentage_qualifier_no_delete
    BEFORE DELETE ON parentage_qualifier FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER parentage_qualifier_no_truncate
    BEFORE TRUNCATE ON parentage_qualifier FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();

-- Person name types (P-001; D4-11).
CREATE TABLE person_name_type (
    code         text        PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label        text        NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100),
    description  text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 2000),
    retired      boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    row_version  bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER person_name_type_identity_immutable
    BEFORE UPDATE ON person_name_type FOR EACH ROW EXECUTE FUNCTION reject_identity_change('code', 'created_at');
CREATE TRIGGER person_name_type_no_delete
    BEFORE DELETE ON person_name_type FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER person_name_type_no_truncate
    BEFORE TRUNCATE ON person_name_type FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
INSERT INTO person_name_type (code, label) VALUES
    ('BIRTH', 'Birth name'), ('MARRIED', 'Married name'), ('NICKNAME', 'Nickname'), ('VARIANT', 'Variant');

-- Place types with a category (D4-18; U-1). The category drives only grouping and lookup filters, never
-- inference, and is immutable. A Place's type is optional (U-1), so no setup is needed before the first Place.
CREATE TABLE place_type (
    code         text        PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label        text        NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100),
    category     text        NOT NULL CHECK (category IN ('LOCALITY', 'JURISDICTIONAL', 'OTHER')),
    description  text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 2000),
    retired      boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    row_version  bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER place_type_identity_immutable
    BEFORE UPDATE ON place_type FOR EACH ROW EXECUTE FUNCTION reject_identity_change('code', 'created_at', 'category');
CREATE TRIGGER place_type_no_delete
    BEFORE DELETE ON place_type FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER place_type_no_truncate
    BEFORE TRUNCATE ON place_type FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
INSERT INTO place_type (code, label, category) VALUES
    ('COUNTY', 'County', 'JURISDICTIONAL'), ('STATE', 'State', 'JURISDICTIONAL');

-- Place-name types (U-2): HISTORICAL, CURRENT, VARIANT (an alternate spelling; s.13, L-001).
CREATE TABLE place_name_type (
    code         text        PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    label        text        NOT NULL CHECK (length(btrim(label)) BETWEEN 1 AND 100),
    description  text        CHECK (description IS NULL OR length(btrim(description)) BETWEEN 1 AND 2000),
    retired      boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    row_version  bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1)
);
CREATE TRIGGER place_name_type_identity_immutable
    BEFORE UPDATE ON place_name_type FOR EACH ROW EXECUTE FUNCTION reject_identity_change('code', 'created_at');
CREATE TRIGGER place_name_type_no_delete
    BEFORE DELETE ON place_name_type FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER place_name_type_no_truncate
    BEFORE TRUNCATE ON place_name_type FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
INSERT INTO place_name_type (code, label) VALUES
    ('HISTORICAL', 'Historical name'), ('CURRENT', 'Current name'), ('VARIANT', 'Variant spelling');

-- Historical Date (s.12; D4-04 B, D4-05). An owned value: one row per dated field, never shared between owners.
-- The expression is the authority (A-004); the interpretation is optional and never adds precision. Single-row
-- rules are CHECKs here; calendar month lengths, point ordering and dual-year consistency are service rules
-- (ADR-0012 s.2). earliest_jdn/latest_jdn are the derived, non-authoritative calendar-unit envelope (Julian Day
-- Numbers; NULL = open end); they are never edited or displayed as the date.
CREATE TABLE historical_date (
    historical_date_id      uuid        PRIMARY KEY,
    expression              text        NOT NULL CHECK (length(btrim(expression)) BETWEEN 1 AND 500),
    interpreted_expression  text        CHECK (interpreted_expression IS NULL
                                                OR length(btrim(interpreted_expression)) BETWEEN 1 AND 500),
    form                    text        NOT NULL DEFAULT 'NOT_INTERPRETED'
                                        CHECK (form IN ('POINT', 'BEFORE', 'AFTER', 'BETWEEN', 'FROM_TO', 'FROM',
                                                        'TO', 'UNKNOWN', 'NOT_INTERPRETED')),
    calendar                text        NOT NULL DEFAULT 'UNSPECIFIED'
                                        CHECK (calendar IN ('UNSPECIFIED', 'GREGORIAN', 'JULIAN')),
    year_start              text        NOT NULL DEFAULT 'UNSPECIFIED'
                                        CHECK (year_start IN ('UNSPECIFIED', 'JANUARY_1', 'MARCH_25')),
    p1_year                 integer     CHECK (p1_year BETWEEN 1 AND 9999),
    p1_month                integer     CHECK (p1_month BETWEEN 1 AND 12),
    p1_day                  integer     CHECK (p1_day BETWEEN 1 AND 31),
    p1_modifier             text        CHECK (p1_modifier IN ('EXACT', 'ABOUT', 'ESTIMATED', 'CALCULATED')),
    p1_uncertain            boolean     NOT NULL DEFAULT false,
    p1_dual_year            integer,
    p2_year                 integer     CHECK (p2_year BETWEEN 1 AND 9999),
    p2_month                integer     CHECK (p2_month BETWEEN 1 AND 12),
    p2_day                  integer     CHECK (p2_day BETWEEN 1 AND 31),
    p2_modifier             text        CHECK (p2_modifier IN ('EXACT', 'ABOUT', 'ESTIMATED', 'CALCULATED')),
    p2_uncertain            boolean     NOT NULL DEFAULT false,
    p2_dual_year            integer,
    interpretation_origin   text        CHECK (interpretation_origin IN ('RESEARCHER', 'PARSER_CONFIRMED')),
    confirmed_by            uuid        REFERENCES app_user (user_id),
    confirmed_at            timestamptz,
    earliest_jdn            bigint,
    latest_jdn              bigint,
    approximate             boolean     NOT NULL DEFAULT false,
    uncertain_any           boolean     NOT NULL DEFAULT false,
    created_at              timestamptz NOT NULL,
    created_by              uuid        REFERENCES app_user (user_id),
    row_version             bigint      NOT NULL DEFAULT 1 CHECK (row_version >= 1),

    -- Part nesting: a day needs a month, a month needs a year.
    CONSTRAINT historical_date_p1_nesting CHECK ((p1_day IS NULL OR p1_month IS NOT NULL)
                                                 AND (p1_month IS NULL OR p1_year IS NOT NULL)),
    CONSTRAINT historical_date_p2_nesting CHECK ((p2_day IS NULL OR p2_month IS NOT NULL)
                                                 AND (p2_month IS NULL OR p2_year IS NOT NULL)),
    -- A point with a year has a modifier; flags and dual years need a year; a dual year is the following year.
    CONSTRAINT historical_date_p1_parts CHECK ((p1_year IS NULL) = (p1_modifier IS NULL)
                                               AND (p1_year IS NOT NULL OR NOT p1_uncertain)
                                               AND (p1_dual_year IS NULL OR p1_dual_year = p1_year + 1)),
    CONSTRAINT historical_date_p2_parts CHECK ((p2_year IS NULL) = (p2_modifier IS NULL)
                                               AND (p2_year IS NOT NULL OR NOT p2_uncertain)
                                               AND (p2_dual_year IS NULL OR p2_dual_year = p2_year + 1)),
    -- Points per form: the first for every structured form, the second exactly for BETWEEN and FROM_TO.
    CONSTRAINT historical_date_form_points CHECK (
        (p1_year IS NOT NULL) = (form NOT IN ('UNKNOWN', 'NOT_INTERPRETED'))
        AND (p2_year IS NOT NULL) = (form IN ('BETWEEN', 'FROM_TO'))),
    -- UNKNOWN and NOT_INTERPRETED carry no calendar, year start or envelope; NOT_INTERPRETED is unconfirmed.
    CONSTRAINT historical_date_uninterpreted CHECK (
        form NOT IN ('UNKNOWN', 'NOT_INTERPRETED')
        OR (calendar = 'UNSPECIFIED' AND year_start = 'UNSPECIFIED' AND earliest_jdn IS NULL AND latest_jdn IS NULL
            AND NOT approximate AND NOT uncertain_any)),
    CONSTRAINT historical_date_confirmation CHECK (
        (form = 'NOT_INTERPRETED') = (interpretation_origin IS NULL)
        AND (interpretation_origin IS NULL) = (confirmed_at IS NULL)
        AND (interpretation_origin IS NULL) = (interpreted_expression IS NULL)),
    -- A structured form always has at least one bound; bounds are ordered.
    CONSTRAINT historical_date_envelope CHECK (
        (form IN ('UNKNOWN', 'NOT_INTERPRETED') OR earliest_jdn IS NOT NULL OR latest_jdn IS NOT NULL)
        AND (earliest_jdn IS NULL OR latest_jdn IS NULL OR earliest_jdn <= latest_jdn))
);
CREATE INDEX historical_date_earliest ON historical_date (earliest_jdn);
CREATE INDEX historical_date_latest ON historical_date (latest_jdn);
CREATE TRIGGER historical_date_identity_immutable
    BEFORE UPDATE ON historical_date
    FOR EACH ROW EXECUTE FUNCTION reject_identity_change('historical_date_id', 'created_at', 'created_by');
CREATE TRIGGER historical_date_no_delete
    BEFORE DELETE ON historical_date FOR EACH ROW EXECUTE FUNCTION reject_removal();
CREATE TRIGGER historical_date_no_truncate
    BEFORE TRUNCATE ON historical_date FOR EACH STATEMENT EXECUTE FUNCTION reject_removal();
