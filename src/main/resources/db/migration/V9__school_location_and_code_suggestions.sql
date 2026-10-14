-- Persist province and generated school-code metadata while a proposed school
-- is still in review. Existing published schools and documents remain intact.
ALTER TABLE admin.school_documents
    ADD COLUMN proposed_school_location varchar(255),
    ADD COLUMN proposed_school_code varchar(30);

-- Proposed schools are grouped by normalized name and province. A missing
-- legacy location contributes an empty suffix, preserving existing keys.
DROP INDEX admin.idx_school_documents_group_year;
ALTER TABLE admin.school_documents DROP COLUMN school_group_key;
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
