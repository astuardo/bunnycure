-- Fix service_catalog sequence after initial data load
-- The V2 migration inserted records with IDs 1-10, but didn't reset the identity sequence
-- This causes primary key violations when trying to insert new services

-- For H2 database: restart the identity sequence at 100 to avoid conflicts
ALTER TABLE service_catalog ALTER COLUMN id RESTART WITH 100;
