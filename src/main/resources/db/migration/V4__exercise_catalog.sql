-- V3 is reserved by ALE-68 (PR 29); stack on that migration before deployment.
ALTER TABLE exercises ADD COLUMN source_key TEXT;
ALTER TABLE exercises ADD COLUMN retired BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exercises ADD CONSTRAINT exercises_source_key_unique UNIQUE (source_key);
