-- ==========================================================
-- V56: Add Specialist attribution to Appointments and Records (H2)
-- ==========================================================

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS specialist_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_appointments_specialist ON appointments(specialist_id);

ALTER TABLE customer_service_records ADD COLUMN IF NOT EXISTS specialist_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_customer_service_records_specialist ON customer_service_records(specialist_id);
