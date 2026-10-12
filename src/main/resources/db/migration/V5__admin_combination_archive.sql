ALTER TABLE admission_combinations
    ADD COLUMN is_active boolean NOT NULL DEFAULT true;

CREATE INDEX idx_admission_combinations_active_page
    ON admission_combinations (combination_id)
    WHERE is_active;
