-- Add RUT column to customers table
-- RUT format: XX.XXX.XXX-X (e.g., 12.345.678-9)
ALTER TABLE customers ADD COLUMN rut VARCHAR(20) NOT NULL DEFAULT '';

-- First assign unique temporary values to existing rows
UPDATE customers SET rut = 'TEMP-' || id WHERE rut = '';

-- Then enforce uniqueness
ALTER TABLE customers ADD CONSTRAINT uk_customers_rut UNIQUE (rut);

-- Remove default so new records must provide a real RUT
ALTER TABLE customers ALTER COLUMN rut DROP DEFAULT;
