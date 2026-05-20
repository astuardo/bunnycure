-- Add RUT column to customers table
-- RUT format: XX.XXX.XXX-X (e.g., 12.345.678-9)
ALTER TABLE customers ADD COLUMN rut VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE customers ADD CONSTRAINT uk_customers_rut UNIQUE (rut);
-- Remove the default after adding the constraint
UPDATE customers SET rut = 'TEMP-' || id WHERE rut = '';
