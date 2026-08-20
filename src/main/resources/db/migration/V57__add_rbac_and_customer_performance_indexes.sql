-- ==========================================================
-- V57: Add missing indexes for RBAC Phase 1, Users & Customer lookup performance
-- Optimiza consultas de calendario de especialistas, asignaciones y filtros por rol.
-- ==========================================================

-- 1. Consultas de Agenda / Calendario filtradas por especialista y rango de fechas
CREATE INDEX IF NOT EXISTS idx_appointments_specialist_date 
    ON appointments(specialist_id, appointment_date, appointment_time);

-- 2. Historial de fotos y fichas técnicas por especialista
CREATE INDEX IF NOT EXISTS idx_customer_service_records_specialist_created 
    ON customer_service_records(specialist_id, created_at DESC);

-- 3. Usuarios: filtrado por rol y estado activo (asignación de especialistas) y búsqueda por email
CREATE INDEX IF NOT EXISTS idx_users_role_enabled 
    ON users(role, enabled);

CREATE INDEX IF NOT EXISTS idx_users_email 
    ON users(email);

-- 4. Clientes: búsquedas rápidas y validaciones por email y RUT
CREATE INDEX IF NOT EXISTS idx_customers_email 
    ON customers(email);

CREATE INDEX IF NOT EXISTS idx_customers_rut 
    ON customers(rut);
