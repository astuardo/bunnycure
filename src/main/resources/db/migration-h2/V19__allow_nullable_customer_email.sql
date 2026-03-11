-- Permite clientes sin email (flujo de reservas por telefono)
-- 1) Normaliza strings vacios a NULL para evitar conflictos por unique
UPDATE customers
SET email = NULL
WHERE email IS NOT NULL
  AND TRIM(email) = '';

-- 2) Permite NULL en email en H2
ALTER TABLE customers
    ALTER COLUMN email VARCHAR(150) NULL;
