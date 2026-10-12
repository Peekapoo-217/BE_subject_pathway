CREATE TABLE app_users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(80) NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role varchar(20) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
    active boolean NOT NULL DEFAULT true,
    token_version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_users_page ON app_users (created_at DESC, id DESC);

CREATE TABLE refresh_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_sessions_user ON refresh_sessions (user_id, expires_at);

CREATE TABLE school_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_code varchar(30) REFERENCES high_schools(code) ON DELETE RESTRICT,
    proposed_school_name varchar(255),
    proposed_school_location varchar(255),
    title varchar(255) NOT NULL,
    academic_year varchar(20) NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'DRAFT_READY', 'NEEDS_REVIEW', 'PUBLISHED', 'REJECTED', 'DELETE_REQUESTED', 'DELETED', 'FAILED')),
    created_by uuid NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    deletion_requested_by uuid REFERENCES app_users(id) ON DELETE RESTRICT,
    deleted_by uuid REFERENCES app_users(id) ON DELETE RESTRICT,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (school_code IS NOT NULL OR proposed_school_name IS NOT NULL)
);
CREATE INDEX idx_school_documents_page ON school_documents (created_at DESC, id DESC);
CREATE INDEX idx_school_documents_status_page ON school_documents (status, created_at DESC, id DESC);

CREATE TABLE school_document_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL REFERENCES school_documents(id) ON DELETE RESTRICT,
    version_number integer NOT NULL CHECK (version_number > 0),
    object_key text NOT NULL UNIQUE,
    original_filename varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    byte_size bigint NOT NULL CHECK (byte_size BETWEEN 1 AND 52428800),
    sha256 char(64) NOT NULL,
    uploaded_by uuid NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, version_number)
);

CREATE TABLE extraction_jobs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id uuid NOT NULL REFERENCES school_document_versions(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'RETRY', 'FAILED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    error_code varchar(80),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_extraction_jobs_claim ON extraction_jobs (available_at, created_at) WHERE status IN ('PENDING', 'RETRY');

CREATE TABLE subject_group_drafts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id uuid NOT NULL REFERENCES school_document_versions(id) ON DELETE RESTRICT,
    group_code varchar(30) NOT NULL,
    group_name varchar(100) NOT NULL,
    subject_codes jsonb NOT NULL CHECK (jsonb_typeof(subject_codes) = 'array'),
    source_pages integer[] NOT NULL DEFAULT '{}',
    confidence numeric(4,3),
    status varchar(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHED')),
    reviewed_by uuid REFERENCES app_users(id) ON DELETE RESTRICT,
    reviewed_at timestamptz,
    review_note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_version_id, group_code)
);
CREATE INDEX idx_subject_group_drafts_review ON subject_group_drafts (status, created_at DESC, id DESC);

CREATE TABLE admin_approval_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id uuid NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    document_id uuid REFERENCES school_documents(id) ON DELETE RESTRICT,
    draft_id uuid REFERENCES subject_group_drafts(id) ON DELETE RESTRICT,
    action varchar(40) NOT NULL,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE llm_configurations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose varchar(30) NOT NULL CHECK (purpose IN ('EXTRACTION', 'INTENT', 'CHAT')),
    endpoint text NOT NULL,
    model varchar(255) NOT NULL,
    encrypted_api_key text NOT NULL,
    active boolean NOT NULL DEFAULT false,
    updated_by uuid NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (purpose)
);
