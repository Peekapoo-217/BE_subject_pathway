-- Repair only source rows whose prior school code has a single historical mapping
-- and whose target does not already contain a row covered by the same unique key.
-- Conflicting rows remain untouched for manual provenance review.
WITH repairable AS (
    SELECT src.id, history.school_code AS current_school_code
    FROM admin.document_subject_group_sources src
    JOIN admin.high_school_code_history history
      ON history.previous_code = src.school_code
    JOIN public.high_schools school
      ON school.code = history.school_code
    WHERE src.school_code <> history.school_code
      AND NOT EXISTS (
          SELECT 1
          FROM admin.document_subject_group_sources existing
          WHERE existing.school_code = history.school_code
            AND existing.academic_year = src.academic_year
            AND existing.group_code = src.group_code
            AND (
                (src.source_kind = 'UNATTRIBUTED' AND existing.source_kind = 'UNATTRIBUTED')
                OR
                (src.source_kind = 'DOCUMENT' AND existing.source_kind = 'DOCUMENT'
                 AND existing.document_id = src.document_id)
            )
      )
)
UPDATE admin.document_subject_group_sources src
SET school_code = repairable.current_school_code
FROM repairable
WHERE src.id = repairable.id;
