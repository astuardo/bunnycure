-- ==========================================================
-- V55: Performance Optimization Indexes
-- Acelera consultas críticas de agenda, dashboard, búsqueda de clientas y fidelidad.
-- ==========================================================

-- 1. Búsqueda y matching de clientas por teléfono, cumpleaños y ranking de fidelidad
CREATE INDEX IF NOT EXISTS idx_customers_phone 
    ON customers(phone);

CREATE INDEX IF NOT EXISTS idx_customers_birth_date 
    ON customers(birth_date);

CREATE INDEX IF NOT EXISTS idx_customers_loyalty_ranking 
    ON customers(total_completed_visits DESC, loyalty_stamps DESC);

-- 2. Consultas de Agenda, Calendario y Rango de Fechas
CREATE INDEX IF NOT EXISTS idx_appointments_date_time 
    ON appointments(appointment_date, appointment_time);

CREATE INDEX IF NOT EXISTS idx_appointments_status_date 
    ON appointments(status, appointment_date);

CREATE INDEX IF NOT EXISTS idx_appointments_customer_date 
    ON appointments(customer_id, appointment_date DESC, appointment_time DESC);

CREATE INDEX IF NOT EXISTS idx_appointments_service_catalog 
    ON appointments(service_catalog_id);

-- 3. Historial de fotos / fichas técnicas de clientas
CREATE INDEX IF NOT EXISTS idx_customer_service_records_customer_created 
    ON customer_service_records(customer_id, created_at DESC);

-- 4. Solicitudes de reserva por estado y fecha
CREATE INDEX IF NOT EXISTS idx_booking_requests_status_created 
    ON booking_requests(status, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_booking_requests_phone 
    ON booking_requests(phone);

-- 5. Historial de premios de fidelidad por clienta y fecha
CREATE INDEX IF NOT EXISTS idx_loyalty_reward_history_customer_earned 
    ON loyalty_reward_history(customer_id, earned_at DESC);

CREATE INDEX IF NOT EXISTS idx_loyalty_reward_history_appointment 
    ON loyalty_reward_history(appointment_id);
