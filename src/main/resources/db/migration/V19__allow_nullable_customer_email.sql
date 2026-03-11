-- Permite clientes sin email (flujo de reservas por telefono)
-- 1) Normaliza strings vacios a NULL para evitar conflictos por unique
UPDATE customers
SET email = NULL
WHERE email IS NOT NULL
  AND btrim(email) = '';

-- 2) Permite NULL en email
ALTER TABLE customers
    ALTER COLUMN email DROP NOT NULL;
