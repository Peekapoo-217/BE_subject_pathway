-- Labels collected from a separate combination catalogue. A canonical combination
-- can have several public codes, and an unverified catalogue row remains unlinked.
CREATE TABLE public.admission_combination_labels (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code varchar(100) NOT NULL,
    subject_details text NOT NULL,
    canonical_combination_id varchar(255) NULL,
    source_url text NOT NULL,
    source_period varchar(20) NOT NULL,
    retrieved_at timestamptz NOT NULL,
    CONSTRAINT uq_combination_label_source
        UNIQUE (source_url, source_period, code, subject_details),
    CONSTRAINT fk_combination_label_canonical
        FOREIGN KEY (canonical_combination_id)
        REFERENCES public.admission_combinations(combination_id),
    CONSTRAINT chk_combination_label_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT chk_combination_label_details CHECK (length(btrim(subject_details)) > 0)
);

CREATE INDEX idx_combination_labels_canonical
    ON public.admission_combination_labels (canonical_combination_id, retrieved_at)
    WHERE canonical_combination_id IS NOT NULL;
