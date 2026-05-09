-- Ensure service_catalog IDs are generated from a dedicated sequence.
-- This repairs environments where the table exists but Hibernate cannot rely on IDENTITY.

CREATE SEQUENCE IF NOT EXISTS service_catalog_seq START WITH 1 INCREMENT BY 1;

DO $$
DECLARE
    next_id BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) + 1
    INTO next_id
    FROM service_catalog;

    IF next_id < 1 THEN
        next_id := 1;
    END IF;

    EXECUTE format('SELECT setval(%L, %s, false)', 'service_catalog_seq', next_id);
END $$;
