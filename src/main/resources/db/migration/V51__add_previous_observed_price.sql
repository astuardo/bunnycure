ALTER TABLE products
    ADD COLUMN IF NOT EXISTS previous_observed_price numeric(12,2);
