-- V0025 - Phase 4 WP1 audit correction WP1-R2: NULL-safe Historical Date point constraints.
--
-- V0024's dual-year rule "p1_dual_year IS NULL OR p1_dual_year = p1_year + 1" evaluates to NULL (and so passes)
-- when p1_year is NULL, so an orphan dual year could be stored on an UNKNOWN or NOT_INTERPRETED date, and a ghost
-- p2_dual_year on a form without a second point. This forward-only migration replaces the two point-part
-- constraints with explicitly NULL-safe ones and adds one constraint stating, field by field, that a form without
-- a point carries none of that point's fields. V0024 itself is unchanged (its applied checksum stays valid).
--
-- Rows that already break the corrected rules are not repaired or deleted: the upgrade stops and names how many
-- there are, so the archive's integrity scan can report them for a deliberate decision (fail visibly).

DO $$
DECLARE
    bad bigint;
BEGIN
    SELECT count(*) INTO bad FROM historical_date
     WHERE (p1_dual_year IS NOT NULL AND (p1_year IS NULL OR p1_dual_year <> p1_year + 1))
        OR (p2_dual_year IS NOT NULL AND (p2_year IS NULL OR p2_dual_year <> p2_year + 1))
        OR (form IN ('UNKNOWN', 'NOT_INTERPRETED')
            AND (p1_year IS NOT NULL OR p1_month IS NOT NULL OR p1_day IS NOT NULL OR p1_modifier IS NOT NULL
                 OR p1_uncertain OR p1_dual_year IS NOT NULL))
        OR (form NOT IN ('BETWEEN', 'FROM_TO')
            AND (p2_year IS NOT NULL OR p2_month IS NOT NULL OR p2_day IS NOT NULL OR p2_modifier IS NOT NULL
                 OR p2_uncertain OR p2_dual_year IS NOT NULL));
    IF bad > 0 THEN
        RAISE EXCEPTION 'V0025 stopped: % historical_date row(s) hold point fields their form does not allow. '
            'Nothing was changed. Run the Historical Date integrity scan to list them, decide on each deliberately, '
            'then upgrade again.', bad
            USING ERRCODE = 'check_violation';
    END IF;
END;
$$;

ALTER TABLE historical_date
    DROP CONSTRAINT historical_date_p1_parts,
    DROP CONSTRAINT historical_date_p2_parts;

ALTER TABLE historical_date
    -- A point with a year has a modifier; the uncertain flag and a dual year need a year; a dual year is the
    -- following year. Every comparison is guarded so that no part evaluates to NULL.
    ADD CONSTRAINT historical_date_p1_parts CHECK (
        (p1_year IS NULL) = (p1_modifier IS NULL)
        AND (p1_year IS NOT NULL OR NOT p1_uncertain)
        AND (p1_dual_year IS NULL OR (p1_year IS NOT NULL AND p1_dual_year = p1_year + 1))),
    ADD CONSTRAINT historical_date_p2_parts CHECK (
        (p2_year IS NULL) = (p2_modifier IS NULL)
        AND (p2_year IS NOT NULL OR NOT p2_uncertain)
        AND (p2_dual_year IS NULL OR (p2_year IS NOT NULL AND p2_dual_year = p2_year + 1))),
    -- A form without a point holds none of that point's fields: no orphan or ghost values.
    ADD CONSTRAINT historical_date_points_by_form CHECK (
        (form NOT IN ('UNKNOWN', 'NOT_INTERPRETED')
         OR (p1_year IS NULL AND p1_month IS NULL AND p1_day IS NULL AND p1_modifier IS NULL
             AND NOT p1_uncertain AND p1_dual_year IS NULL))
        AND (form IN ('BETWEEN', 'FROM_TO')
             OR (p2_year IS NULL AND p2_month IS NULL AND p2_day IS NULL AND p2_modifier IS NULL
                 AND NOT p2_uncertain AND p2_dual_year IS NULL)));
