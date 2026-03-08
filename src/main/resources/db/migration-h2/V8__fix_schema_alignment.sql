-- H2 version - Schema alignment verification
-- This migration ensures consistency across different database states

-- Verify service_catalog_id constraint (V2 should have set this)
-- Set default for any null values in service_catalog_id
UPDATE appointments SET service_catalog_id = 1 WHERE service_catalog_id IS NULL;
