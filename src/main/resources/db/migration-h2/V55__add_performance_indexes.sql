-- ==========================================================
-- V55: Performance Optimization Indexes (H2 Compatibility)
-- ==========================================================

CREATE INDEX IF NOT EXISTS idx_customers_phone 
    ON customers(phone);

CREATE INDEX IF NOT EXISTS idx_customers_birth_date 
    ON customers(birth_date);

CREATE INDEX IF NOT EXISTS idx_customers_loyalty_ranking 
    ON customers(total_completed_visits DESC, loyalty_stamps DESC);

CREATE INDEX IF NOT EXISTS idx_appointments_date_time 
    ON appointments(appointment_date, appointment_time);

CREATE INDEX IF NOT EXISTS idx_appointments_status_date 
    ON appointments(status, appointment_date);

CREATE INDEX IF NOT EXISTS idx_appointments_customer_date 
    ON appointments(customer_id, appointment_date DESC, appointment_time DESC);

CREATE INDEX IF NOT EXISTS idx_appointments_service_catalog 
    ON appointments(service_catalog_id);

CREATE INDEX IF NOT EXISTS idx_customer_service_records_customer_created 
    ON customer_service_records(customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_booking_requests_status_created 
    ON booking_requests(status, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_booking_requests_phone 
    ON booking_requests(phone);

CREATE INDEX IF NOT EXISTS idx_loyalty_reward_history_customer_earned 
    ON loyalty_reward_history(customer_id, earned_at DESC);

CREATE INDEX IF NOT EXISTS idx_loyalty_reward_history_appointment 
    ON loyalty_reward_history(appointment_id);
