-- ==========================================================
-- V71: Sincronizar la identidad de tenants.id (H2)
-- ==========================================================
-- V58 insertó el salón principal con id = 1 explícito; la identidad seguía en 1.
ALTER TABLE tenants ALTER COLUMN id RESTART WITH 1000;
