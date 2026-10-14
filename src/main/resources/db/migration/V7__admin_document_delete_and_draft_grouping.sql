-- Preserve audit history while supporting draft cleanup during document soft-delete.
ALTER TABLE admin.extraction_jobs
    DROP CONSTRAINT extraction_jobs_status_check,
    ADD CONSTRAINT extraction_jobs_status_check
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'RETRY', 'FAILED', 'CANCELLED'));

ALTER TABLE admin.school_documents
    ADD COLUMN school_group_key text GENERATED ALWAYS AS (
        CASE
            WHEN school_code IS NOT NULL THEN 'S:' || school_code
            ELSE 'P:' || md5(
                lower(regexp_replace(btrim(coalesce(proposed_school_name, '')), '\s+', ' ', 'g'))
                || chr(31)
                || lower(regexp_replace(btrim(coalesce(proposed_school_location, '')), '\s+', ' ', 'g'))
            )
        END
    ) STORED;

CREATE INDEX idx_school_documents_group_year
    ON admin.school_documents (school_group_key, academic_year)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_admin_approval_audit_draft_id
    ON admin.admin_approval_audit (draft_id)
    WHERE draft_id IS NOT NULL;
