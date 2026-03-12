ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);

UPDATE customers
SET public_id = CAST(RANDOM_UUID() AS VARCHAR)
WHERE public_id IS NULL;

ALTER TABLE customers
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_public_id
    ON customers(public_id);
