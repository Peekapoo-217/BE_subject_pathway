-- V1 Schema Placeholder: User will paste DDL here

CREATE TABLE public.admission_combinations ( 
    combination_id varchar(255) NOT NULL, 
    combination_display varchar(255) NOT NULL, 
    component_count int2 NOT NULL, 
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT admission_combinations_pkey PRIMARY KEY (combination_id), 
    CONSTRAINT chk_combination_component_count CHECK ((component_count > 0))
);

CREATE TABLE public.etl_sync_logs ( 
    id int8 GENERATED ALWAYS AS IDENTITY NOT NULL, 
    sync_type varchar(20) NOT NULL, 
    status varchar(20) NOT NULL, 
    records_processed int4 DEFAULT 0 NULL, 
    error_message text NULL, 
    started_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    completed_at timestamp NULL, 
    CONSTRAINT etl_sync_logs_pkey PRIMARY KEY (id)
);

CREATE TABLE public.high_schools ( 
    code varchar(30) NOT NULL, 
    "name" varchar(255) NOT NULL, 
    "location" varchar(255) NULL, 
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT high_schools_pkey PRIMARY KEY (code)
);

CREATE TABLE public.majors ( 
    program_code varchar(30) NOT NULL, 
    program_name text NOT NULL, 
    description text NULL, 
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT majors_pkey PRIMARY KEY (program_code)
);

CREATE TABLE public.universities ( 
    university_code varchar(20) NOT NULL, 
    university_name text NOT NULL, 
    website text NULL, 
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT universities_pkey PRIMARY KEY (university_code)
);

CREATE TABLE public.admission_offers ( 
    id int8 GENERATED ALWAYS AS IDENTITY NOT NULL, 
    university_code varchar(20) NOT NULL, 
    admission_year int2 NOT NULL, 
    program_code varchar(30) NOT NULL, 
    method_codes text NOT NULL, 
    quota int4 NULL, 
    source_url text NULL, 
    retrieved_at timestamptz NULL, 
    loaded_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT admission_offers_pkey PRIMARY KEY (id), 
    CONSTRAINT uq_admission_offer UNIQUE (university_code, admission_year, program_code), 
    CONSTRAINT admission_offers_university_code_fkey FOREIGN KEY (university_code) REFERENCES public.universities(university_code), 
    CONSTRAINT fk_offer_major FOREIGN KEY (program_code) REFERENCES public.majors(program_code) ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE INDEX idx_admission_offers_admission_year ON public.admission_offers USING btree (admission_year);
CREATE INDEX idx_admission_offers_program ON public.admission_offers USING btree (program_code);
CREATE INDEX idx_admission_offers_program_code ON public.admission_offers USING btree (program_code);
CREATE INDEX idx_admission_offers_univ_year ON public.admission_offers USING btree (university_code, admission_year);

CREATE TABLE public.cutoff_scores ( 
    id int8 GENERATED ALWAYS AS IDENTITY NOT NULL, 
    university_code varchar(20) NOT NULL, 
    admission_year int2 NOT NULL, 
    program_code varchar(30) NOT NULL, 
    method_code varchar(20) DEFAULT '100'::character varying NOT NULL, 
    cutoff_score_thpt numeric(5, 2) NULL, 
    cutoff_score_sat numeric(6, 2) NULL, 
    source_url text NULL, 
    retrieved_at timestamptz NULL, 
    loaded_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT cutoff_scores_pkey PRIMARY KEY (id), 
    CONSTRAINT uq_cutoff_score UNIQUE (university_code, admission_year, program_code, method_code), 
    CONSTRAINT cutoff_scores_university_code_fkey FOREIGN KEY (university_code) REFERENCES public.universities(university_code), 
    CONSTRAINT fk_cutoff_major FOREIGN KEY (program_code) REFERENCES public.majors(program_code) ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE INDEX idx_cutoff_scores_admission_year ON public.cutoff_scores USING btree (admission_year);
CREATE INDEX idx_cutoff_scores_program_code ON public.cutoff_scores USING btree (program_code);
CREATE INDEX idx_cutoff_scores_program_year ON public.cutoff_scores USING btree (program_code, admission_year);
CREATE INDEX idx_cutoff_scores_univ_year ON public.cutoff_scores USING btree (university_code, admission_year);

CREATE TABLE public.offer_combinations ( 
    offer_id int8 NOT NULL, 
    canonical_combination_id varchar(255) NOT NULL, 
    CONSTRAINT offer_combinations_pkey PRIMARY KEY (offer_id, canonical_combination_id), 
    CONSTRAINT fk_oc_combination FOREIGN KEY (canonical_combination_id) REFERENCES public.admission_combinations(combination_id) ON DELETE CASCADE, 
    CONSTRAINT fk_oc_offer FOREIGN KEY (offer_id) REFERENCES public.admission_offers(id) ON DELETE CASCADE
);

CREATE TABLE public.subjects ( 
    code varchar(30) NOT NULL, 
    "name" varchar(150) NOT NULL, 
    subject_type varchar(20) NOT NULL, 
    is_mandatory bool DEFAULT false NOT NULL, 
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    canonical_subject_code varchar(30) NULL, 
    CONSTRAINT chk_subject_type CHECK (((subject_type)::text = ANY ((ARRAY['ACADEMIC'::character varying, 'ACTIVITY'::character varying, 'LOCAL'::character varying])::text[]))), 
    CONSTRAINT subjects_name_key UNIQUE (name), 
    CONSTRAINT subjects_pkey PRIMARY KEY (code), 
    CONSTRAINT fk_subject_canonical FOREIGN KEY (canonical_subject_code) REFERENCES public.subjects(code) ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE TABLE public.admission_combination_components ( 
    combination_id varchar(255) NOT NULL, 
    component_code varchar(100) NOT NULL, 
    component_name varchar(150) NOT NULL, 
    component_type varchar(20) NOT NULL, 
    subject_code varchar(30) NULL, 
    weight numeric(6, 3) DEFAULT 1 NOT NULL, 
    CONSTRAINT admission_combination_components_pkey PRIMARY KEY (combination_id, component_code), 
    CONSTRAINT chk_combination_component_subject_reference CHECK (((((component_type)::text = 'SUBJECT'::text) AND (subject_code IS NOT NULL)) OR (((component_type)::text = 'APTITUDE'::text) AND (subject_code IS NULL)))), 
    CONSTRAINT chk_combination_component_type CHECK (((component_type)::text = ANY ((ARRAY['SUBJECT'::character varying, 'APTITUDE'::character varying])::text[]))), 
    CONSTRAINT chk_combination_component_weight CHECK ((weight > (0)::numeric)), 
    CONSTRAINT fk_combination_component_combination FOREIGN KEY (combination_id) REFERENCES public.admission_combinations(combination_id) ON DELETE CASCADE ON UPDATE CASCADE, 
    CONSTRAINT fk_combination_component_subject FOREIGN KEY (subject_code) REFERENCES public.subjects(code) ON DELETE RESTRICT ON UPDATE CASCADE
);
CREATE INDEX idx_combination_components_subject_code ON public.admission_combination_components USING btree (subject_code) WHERE (subject_code IS NOT NULL);
CREATE INDEX idx_combination_components_type ON public.admission_combination_components USING btree (component_type);

CREATE TABLE public.subject_groups ( 
    school_code varchar(30) NOT NULL, 
    group_code varchar(30) NOT NULL, 
    group_name varchar(100) NOT NULL, 
    subject_code varchar(30) NOT NULL, 
    academic_year varchar(20) NOT NULL, 
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, 
    CONSTRAINT subject_groups_pkey PRIMARY KEY (school_code, academic_year, group_code, subject_code), 
    CONSTRAINT fk_subject_group_school FOREIGN KEY (school_code) REFERENCES public.high_schools(code) ON DELETE CASCADE ON UPDATE CASCADE, 
    CONSTRAINT fk_subject_group_subject FOREIGN KEY (subject_code) REFERENCES public.subjects(code) ON DELETE RESTRICT ON UPDATE CASCADE
);