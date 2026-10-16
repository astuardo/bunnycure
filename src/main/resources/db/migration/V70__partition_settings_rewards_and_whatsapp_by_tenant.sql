-- ==========================================================
-- V70: Aislar por salón la configuración, el catálogo de recompensas
--      y la bandeja / cola de WhatsApp (antes globales).
-- Los datos existentes pertenecen al salón principal (tenant 1).
-- ==========================================================

-- 1. app_settings: la clave pasa a ser única por salón.
--    Se introduce un id sustituto como PK para que los UPDATE afecten a una sola fila.
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS id BIGSERIAL;

DO $$
DECLARE pk_name TEXT;
BEGIN
    SELECT conname INTO pk_name FROM pg_constraint
     WHERE conrelid = 'app_settings'::regclass AND contype = 'p';
    IF pk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE app_settings DROP CONSTRAINT %I', pk_name);
    END IF;
END $$;

ALTER TABLE app_settings ADD PRIMARY KEY (id);
ALTER TABLE app_settings ADD CONSTRAINT uk_app_settings_tenant_key UNIQUE (tenant_id, setting_key);

-- 2. Catálogo de recompensas de fidelización
ALTER TABLE loyalty_rewards ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_loyalty_rewards_tenant_order ON loyalty_rewards(tenant_id, order_index);

-- 3. Bandeja de entrada de WhatsApp
ALTER TABLE incoming_whatsapp_messages ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_incoming_wa_msgs_tenant_created ON incoming_whatsapp_messages(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_incoming_wa_msgs_tenant_read ON incoming_whatsapp_messages(tenant_id, is_read);

-- 4. Cola de mensajes WhatsApp fallidos
ALTER TABLE whatsapp_outbox_messages ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_wa_outbox_tenant_status ON whatsapp_outbox_messages(tenant_id, status);
