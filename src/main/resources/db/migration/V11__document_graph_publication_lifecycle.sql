ALTER TABLE
    admin.school_documents
ADD
    COLUMN status_before_delete varchar(30),
ADD
    COLUMN graph_publication_status varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (
        graph_publication_status IN ('PUBLISHED', 'UNPUBLISHED')
    );

CREATE TABLE admin.subject_group_publication_state (
    school_code varchar(30) NOT NULL REFERENCES public.high_schools(code) ON UPDATE CASCADE ON DELETE RESTRICT,
    academic_year varchar(20) NOT NULL,
    group_code varchar(30) NOT NULL,
    is_published boolean NOT NULL DEFAULT true,
    publication_revision bigint NOT NULL DEFAULT 1 CHECK (publication_revision > 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (school_code, academic_year, group_code)
);

CREATE TABLE admin.document_subject_group_sources (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_code varchar(30) NOT NULL,
    academic_year varchar(20) NOT NULL,
    group_code varchar(30) NOT NULL,
    source_kind varchar(20) NOT NULL CHECK (source_kind IN ('DOCUMENT', 'UNATTRIBUTED')),
    document_id uuid REFERENCES admin.school_documents(id) ON DELETE RESTRICT,
    draft_id uuid REFERENCES admin.subject_group_drafts(id) ON DELETE RESTRICT,
    CHECK (
        (
            source_kind = 'DOCUMENT'
            AND document_id IS NOT NULL
            AND draft_id IS NOT NULL
        )
        OR (
            source_kind = 'UNATTRIBUTED'
            AND document_id IS NULL
            AND draft_id IS NULL
        )
    )
);

CREATE UNIQUE INDEX idx_document_group_source_document ON admin.document_subject_group_sources (
    document_id,
    school_code,
    academic_year,
    group_code
)
WHERE
    source_kind = 'DOCUMENT';

CREATE UNIQUE INDEX idx_document_group_source_unattributed ON admin.document_subject_group_sources (school_code, academic_year, group_code)
WHERE
    source_kind = 'UNATTRIBUTED';

CREATE INDEX idx_document_group_source_lookup ON admin.document_subject_group_sources (school_code, academic_year, group_code);

ALTER TABLE
    admin.subject_group_graph_sync_jobs
ADD
    COLUMN document_id uuid REFERENCES admin.school_documents(id) ON DELETE RESTRICT;

CREATE INDEX idx_subject_group_graph_sync_document_latest
    ON admin.subject_group_graph_sync_jobs (document_id, created_at DESC, id DESC)
    WHERE document_id IS NOT NULL;

INSERT INTO
    admin.subject_group_publication_state (
        school_code,
        academic_year,
        group_code,
        is_published,
        publication_revision
    )
SELECT
    DISTINCT school_code,
    academic_year,
    group_code,
    true,
    1
FROM
    public.subject_groups
WHERE
    deleted_at IS NULL ON CONFLICT DO NOTHING;

-- Only publication audit records with a matching live PostgreSQL group prove
-- that a document contributed the logical group. Other groups stay visible
-- through an explicit unattributed source and require manual review to hide.
INSERT INTO
    admin.document_subject_group_sources (
        school_code,
        academic_year,
        group_code,
        source_kind,
        document_id,
        draft_id
    )
SELECT
    DISTINCT doc.school_code,
    doc.academic_year,
    upper(d.group_code),
    'DOCUMENT',
    doc.id,
    d.id
FROM
    admin.admin_approval_audit audit
    JOIN admin.subject_group_drafts d ON d.id = audit.draft_id
    AND d.status = 'PUBLISHED'
    JOIN admin.school_document_versions v ON v.id = d.document_version_id
    JOIN admin.school_documents doc ON doc.id = v.document_id
WHERE
    audit.action = 'SUBJECT_GROUP_PUBLISHED'
    AND doc.school_code IS NOT NULL
    AND upper(d.group_code) = upper(audit.details ->> 'groupCode')
    AND EXISTS (
        SELECT
            1
        FROM
            public.subject_groups sg
        WHERE
            sg.school_code = doc.school_code
            AND sg.academic_year = doc.academic_year
            AND upper(sg.group_code) = upper(d.group_code)
            AND sg.deleted_at IS NULL
    ) ON CONFLICT DO NOTHING;

INSERT INTO
    admin.document_subject_group_sources (
        school_code,
        academic_year,
        group_code,
        source_kind
    )
SELECT
    DISTINCT sg.school_code,
    sg.academic_year,
    upper(sg.group_code),
    'UNATTRIBUTED'
FROM
    public.subject_groups sg
WHERE
    sg.deleted_at IS NULL
    AND NOT EXISTS (
        SELECT
            1
        FROM
            admin.document_subject_group_sources src
        WHERE
            src.school_code = sg.school_code
            AND src.academic_year = sg.academic_year
            AND src.group_code = upper(sg.group_code)
            AND src.source_kind = 'DOCUMENT'
    ) ON CONFLICT DO NOTHING;

-- Backfill the prior active state where it is recoverable from publication
-- history. Other legacy deleted rows restore to UPLOADED and can be reprocessed.
UPDATE
    admin.school_documents doc
SET
    status_before_delete = CASE
        WHEN EXISTS (
            SELECT
                1
            FROM
                admin.subject_group_drafts d
                JOIN admin.school_document_versions v ON v.id = d.document_version_id
            WHERE
                v.document_id = doc.id
                AND d.status = 'PUBLISHED'
        ) THEN 'PUBLISHED'
        WHEN EXISTS (
            SELECT
                1
            FROM
                admin.admin_approval_audit audit
            WHERE
                audit.document_id = doc.id
                AND audit.action = 'SUBJECT_GROUP_DRAFT_DELETED'
        ) THEN 'DRAFT_READY'
        WHEN EXISTS (
            SELECT
                1
            FROM
                admin.school_document_versions v
                JOIN admin.extraction_log job ON job.document_version_id = v.id
            WHERE
                v.document_id = doc.id
                AND job.status = 'FAILED'
        ) THEN 'FAILED'
        ELSE 'UPLOADED'
    END
WHERE
    doc.deleted_at IS NOT NULL
    AND doc.status_before_delete IS NULL;

