-- ==========================================================
-- V72: Suscripciones de salones, pagos y registro autoservicio
-- ==========================================================
-- Los salones existentes quedan ACTIVE y sin vencimiento (current_period_end NULL):
-- el ciclo de cobro solo afecta a salones con período pagado o en prueba.

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trial_ends_on DATE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS current_period_end DATE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS external_subscription_id VARCHAR(120);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS last_billing_notice VARCHAR(60);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS terms_version VARCHAR(20);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS terms_accepted_ip VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_tenants_subscription ON tenants(subscription_status, current_period_end);

CREATE TABLE IF NOT EXISTS subscription_payments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    amount_clp BIGINT NOT NULL,
    plan_tier VARCHAR(30) NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    provider VARCHAR(30) NOT NULL,
    external_payment_id VARCHAR(120),
    reference VARCHAR(200),
    recorded_by VARCHAR(100),
    paid_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subscription_payments_provider_ext UNIQUE (provider, external_payment_id)
);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_tenant ON subscription_payments(tenant_id, paid_at DESC);

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
