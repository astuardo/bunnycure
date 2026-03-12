ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);

UPDATE customers
SET public_id = lower(
        substr(md5(random()::text || clock_timestamp()::text), 1, 8) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 1, 12)
)
WHERE public_id IS NULL;

ALTER TABLE customers
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_public_id
    ON customers(public_id);
