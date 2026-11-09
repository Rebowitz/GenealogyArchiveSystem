-- Phase 3 audit correction GAS-P3-008 (first independent audit, 2026-11-08).
--
-- Once a worksheet row has been grouped into a Record, its worksheet marks - Start New Record, explicit record
-- group, page designation and classification - are what the grouping applied (ADR-0011 s.4). V0021's trigger made
-- the applied Record and run permanent but still let a direct UPDATE rewrite those marks. This migration makes
-- them final at the database boundary as well.
--
-- Forward-only: V0021 is not edited, because databases that already applied it (every CI and test database built
-- from this branch) would then fail Flyway's checksum validation. Replacing the trigger function changes no row and
-- keeps the trigger itself (V0021). Per-File capture-context overrides (the *_mode and override_* columns) stay
-- correctable on applied rows: they describe the capture, not the grouping.
CREATE OR REPLACE FUNCTION file_capture_context_applied_final() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.applied_record_id IS NOT NULL THEN
            RAISE EXCEPTION 'File % has been grouped in its capture session and cannot be removed from it', OLD.file_id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.applied_record_id IS NOT NULL AND (NEW.applied_record_id IS DISTINCT FROM OLD.applied_record_id
            OR NEW.applied_run_id IS DISTINCT FROM OLD.applied_run_id) THEN
        RAISE EXCEPTION 'The grouping of File % is permanent', OLD.file_id USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.applied_record_id IS NOT NULL AND (NEW.start_new_record IS DISTINCT FROM OLD.start_new_record
            OR NEW.record_group IS DISTINCT FROM OLD.record_group
            OR NEW.page_designation IS DISTINCT FROM OLD.page_designation
            OR NEW.classification_code IS DISTINCT FROM OLD.classification_code) THEN
        RAISE EXCEPTION 'The worksheet marks of File % were applied by grouping and are permanent', OLD.file_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
