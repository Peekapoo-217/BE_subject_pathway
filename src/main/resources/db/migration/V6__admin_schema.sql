-- Group authentication, document workflow, audit, and LLM administration tables
-- under a dedicated PostgreSQL schema. PostgreSQL preserves rows, indexes,
-- constraints, and foreign-key dependencies when a table changes schema.
CREATE SCHEMA IF NOT EXISTS admin;

ALTER TABLE public.app_users SET SCHEMA admin;
ALTER TABLE public.refresh_sessions SET SCHEMA admin;
ALTER TABLE public.school_documents SET SCHEMA admin;
ALTER TABLE public.school_document_versions SET SCHEMA admin;
ALTER TABLE public.extraction_jobs SET SCHEMA admin;
ALTER TABLE public.subject_group_drafts SET SCHEMA admin;
ALTER TABLE public.admin_approval_audit SET SCHEMA admin;
ALTER TABLE public.llm_configurations SET SCHEMA admin;
