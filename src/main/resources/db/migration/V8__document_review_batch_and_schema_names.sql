-- Align table names and ownership with the current admin/auth boundary.
ALTER TABLE admin.app_users SET SCHEMA public;
ALTER TABLE admin.refresh_sessions SET SCHEMA public;

ALTER TABLE admin.extraction_jobs RENAME TO extraction_log;
ALTER TABLE admin.extraction_log RENAME CONSTRAINT extraction_jobs_pkey TO extraction_log_pkey;
ALTER TABLE admin.extraction_log RENAME CONSTRAINT extraction_jobs_document_version_id_fkey
    TO extraction_log_document_version_id_fkey;
ALTER TABLE admin.extraction_log RENAME CONSTRAINT extraction_jobs_status_check TO extraction_log_status_check;
ALTER INDEX admin.idx_extraction_jobs_claim RENAME TO idx_extraction_log_claim;

-- The generated grouping key depends on proposed_school_location, so rebuild it
-- before dropping the unused upload field. An empty location suffix preserves
-- keys for existing proposed-school documents whose location is null.
DROP INDEX admin.idx_school_documents_group_year;
ALTER TABLE admin.school_documents DROP COLUMN school_group_key;
ALTER TABLE admin.school_documents DROP COLUMN proposed_school_location;
ALTER TABLE admin.school_documents
    ADD COLUMN school_group_key text GENERATED ALWAYS AS (
        CASE
            WHEN school_code IS NOT NULL THEN 'S:' || school_code
            ELSE 'P:' || md5(
                lower(regexp_replace(btrim(coalesce(proposed_school_name, '')), '\s+', ' ', 'g'))
                || chr(31)
            )
        END
    ) STORED;
CREATE INDEX idx_school_documents_group_year
    ON admin.school_documents (school_group_key, academic_year)
    WHERE deleted_at IS NULL;

ALTER TABLE admin.subject_group_drafts
    ADD CONSTRAINT subject_group_drafts_subject_count_check
        CHECK (jsonb_array_length(subject_codes) BETWEEN 1 AND 4);

-- Transactional outbox: PostgreSQL publication remains authoritative while
-- the Python graph worker retries the Neo4j projection asynchronously.
CREATE TABLE admin.subject_group_graph_sync_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_code varchar(30) NOT NULL REFERENCES public.high_schools(code) ON DELETE RESTRICT,
    academic_year varchar(20) NOT NULL,
    group_codes text[] NOT NULL CHECK (cardinality(group_codes) > 0),
    status varchar(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'RETRY', 'SYNCED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    error_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_subject_group_graph_sync_claim
    ON admin.subject_group_graph_sync_jobs (available_at, created_at)
    WHERE status IN ('PENDING', 'RETRY');
CREATE INDEX idx_subject_group_graph_sync_school_year
    ON admin.subject_group_graph_sync_jobs (school_code, academic_year, created_at DESC, id DESC);
