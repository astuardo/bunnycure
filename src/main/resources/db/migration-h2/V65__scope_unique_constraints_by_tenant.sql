-- ==========================================================
-- V65: Scope unique constraints by tenant for SaaS multi-tenancy (H2)
-- ==========================================================

ALTER TABLE customers DROP CONSTRAINT IF EXISTS uk_customers_rut;
CREATE UNIQUE INDEX IF NOT EXISTS uk_customers_tenant_rut ON customers(tenant_id, rut);
