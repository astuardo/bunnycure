-- ==========================================================
-- V69: Aislar suscripciones Web Push por salón y usuario
-- ==========================================================
-- Antes las suscripciones eran globales y se registraban sin autenticación:
-- cualquier dispositivo recibía los avisos de administración de todos los salones.

ALTER TABLE web_push_subscriptions ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE web_push_subscriptions ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_web_push_subscriptions_tenant_active
    ON web_push_subscriptions (tenant_id, active);

-- Las suscripciones existentes no tienen dueño verificable: se desactivan.
-- Cada usuario autenticado se re-registra automáticamente al abrir la PWA.
UPDATE web_push_subscriptions SET active = FALSE, last_failure_reason = 'Desactivada por V69: re-registro autenticado requerido';
