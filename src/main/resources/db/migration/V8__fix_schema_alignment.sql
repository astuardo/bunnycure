-- Schema alignment verification
-- This migration ensures consistency across different database states

-- Verify service_catalog_id constraint (V2 should have set this)
-- Set default for any null values in service_catalog_id
UPDATE appointments SET service_catalog_id = 1 WHERE service_catalog_id IS NULL;

-- Note: ALTER COLUMN to SET NOT NULL is handled at application startup
-- Different databases handle this differently (PostgreSQL vs H2)
-- This approach is compatible with both databases
