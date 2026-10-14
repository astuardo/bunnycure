-- ==========================================================
-- V65: Scope unique constraints by tenant for SaaS multi-tenancy
-- Permite que clientes con mismo RUT o email existan en diferentes salones.
-- ==========================================================

-- 1. Customers: permitir que el mismo RUT pertenezca a distintos salones
ALTER TABLE customers DROP CONSTRAINT IF EXISTS uk_customers_rut;
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_rut_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_tenant_rut ON customers(tenant_id, rut);

-- 2. Customers: permitir que el mismo email pertenezca a distintos salones
ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_email_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_tenant_email ON customers(tenant_id, email) WHERE email IS NOT NULL;
