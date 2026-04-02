-- Fix service_catalog sequence after initial data load
-- The V2 migration inserted records with IDs 1-10, but didn't reset the identity sequence
-- This causes primary key violations when trying to insert new services

-- For PostgreSQL: Use setval to restart the sequence at 100
-- For H2: Use ALTER TABLE to restart the identity column
SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM information_schema.sequences 
        WHERE sequence_name = 'service_catalog_id_seq'
    ) THEN (SELECT setval('service_catalog_id_seq', 100, false))
    ELSE 0
END;
