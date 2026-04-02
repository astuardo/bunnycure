-- Fix service_catalog sequence after initial data load
-- The V2 migration inserted records with IDs 1-10, but didn't reset the identity sequence
-- This causes primary key violations when trying to insert new services

-- For PostgreSQL: Find and update the sequence dynamically
DO $$
DECLARE
    seq_name TEXT;
BEGIN
    -- Find the sequence associated with service_catalog.id column
    SELECT pg_get_serial_sequence('service_catalog', 'id') INTO seq_name;
    
    -- If sequence exists, set it to 100
    IF seq_name IS NOT NULL THEN
        EXECUTE format('SELECT setval(%L, 100, false)', seq_name);
    END IF;
END $$;

-- For H2: This is a no-op since H2 handles identity columns differently
-- and this PostgreSQL-specific block will be ignored by H2
