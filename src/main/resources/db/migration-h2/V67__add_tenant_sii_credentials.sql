-- ==========================================================
-- V67: Add SII Electronic Billing Credentials to Tenants (H2)
-- Permite a cada salón configurar su propio RUT y credenciales ante el SII.
-- ==========================================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS sii_rut VARCHAR(20);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS sii_password VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS sii_token VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS sii_actividad_economica VARCHAR(120);
