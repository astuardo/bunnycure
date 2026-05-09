-- Hibernate expects allocationSize=1 for service_catalog_seq.
-- Some environments retained the default increment-size 50, which breaks startup validation.

ALTER SEQUENCE service_catalog_seq INCREMENT BY 1;

-- Re-assert the current value after changing the increment to keep future inserts safe.
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
