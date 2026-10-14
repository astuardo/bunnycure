-- ==========================================================
-- V66: Partition remaining entities by tenant for SaaS Data Isolation (H2)
-- ==========================================================

-- 1. Invoice logs
ALTER TABLE invoice_logs ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_invoice_logs_tenant_created ON invoice_logs(tenant_id, created_at DESC);

-- 2. Inventory movements
ALTER TABLE inventory_movements ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_inventory_movements_tenant ON inventory_movements(tenant_id, created_at DESC);

-- 3. Marketing templates
ALTER TABLE marketing_templates ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_marketing_templates_tenant_name ON marketing_templates(tenant_id, name);

-- 4. Notification logs
ALTER TABLE notification_logs ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_notification_logs_tenant_created ON notification_logs(tenant_id, created_at DESC);

-- 5. Service supplies
ALTER TABLE service_supplies ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_service_supplies_tenant ON service_supplies(tenant_id);
