ALTER TABLE products
    ADD COLUMN IF NOT EXISTS observed_price numeric(12,2);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS observed_available boolean;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS last_observed_at timestamp with time zone;
