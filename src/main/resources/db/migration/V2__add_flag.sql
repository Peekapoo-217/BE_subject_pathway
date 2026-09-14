ALTER TABLE subjects ADD COLUMN is_elective BOOLEAN DEFAULT FALSE;

INSERT INTO subjects (code, name, subject_type, is_mandatory, is_elective, created_at) 
VALUES 
('MUSIC', 'Âm nhạc', 'ACADEMIC', FALSE, TRUE, CURRENT_TIMESTAMP),
('FINE_ARTS', 'Mĩ thuật', 'ACADEMIC', FALSE, TRUE, CURRENT_TIMESTAMP);

UPDATE subjects 
SET is_elective = TRUE 
WHERE code IN ('PHYSICS', 'CHEMISTRY', 'BIOLOGY', 'GEOGRAPHY', 'ECONOMIC_LAW', 'TECHNOLOGY', 'INFORMATICS');