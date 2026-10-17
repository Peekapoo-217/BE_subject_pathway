ALTER TABLE public.high_schools
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES public.app_users(id) ON DELETE RESTRICT;

ALTER TABLE public.subject_groups
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES public.app_users(id) ON DELETE RESTRICT;

ALTER TABLE admin.subject_group_drafts
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES public.app_users(id) ON DELETE RESTRICT;

CREATE INDEX idx_high_schools_active_code
    ON public.high_schools (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_subject_groups_active_school_year
    ON public.subject_groups (school_code, academic_year, group_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_admin_drafts_active_created
    ON admin.subject_group_drafts (created_at DESC, id DESC) WHERE deleted_at IS NULL;

ALTER TABLE admin.school_documents
    DROP CONSTRAINT school_documents_school_code_fkey,
    ADD CONSTRAINT school_documents_school_code_fkey
        FOREIGN KEY (school_code) REFERENCES public.high_schools(code)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE admin.subject_group_graph_sync_jobs
    DROP CONSTRAINT subject_group_graph_sync_jobs_school_code_fkey,
    ADD CONSTRAINT subject_group_graph_sync_jobs_school_code_fkey
        FOREIGN KEY (school_code) REFERENCES public.high_schools(code)
        ON UPDATE CASCADE ON DELETE RESTRICT;

CREATE TABLE admin.high_school_code_history (
    previous_code varchar(30) PRIMARY KEY,
    school_code varchar(30) NOT NULL
        REFERENCES public.high_schools(code) ON UPDATE CASCADE ON DELETE RESTRICT,
    changed_by uuid NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT high_school_code_history_not_current CHECK (previous_code <> school_code)
);

CREATE TABLE admin.high_school_graph_sync_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_code varchar(30) NOT NULL
        REFERENCES public.high_schools(code) ON UPDATE CASCADE ON DELETE RESTRICT,
    previous_code varchar(30),
    operation varchar(20) NOT NULL CHECK (operation IN ('UPSERT', 'DELETE')),
    status varchar(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'RETRY', 'SYNCED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    error_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_high_school_graph_sync_claim
    ON admin.high_school_graph_sync_jobs (available_at, created_at, id)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX idx_high_school_code_history_school
    ON admin.high_school_code_history (school_code, previous_code);
