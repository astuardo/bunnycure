-- ==========================================================
-- V56: Add Specialist attribution to Appointments and Records
-- Permite asociar la manicurista/especialista responsable de cada atención.
-- ==========================================================

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS specialist_id BIGINT;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_specialist FOREIGN KEY (specialist_id) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_appointments_specialist ON appointments(specialist_id);

ALTER TABLE customer_service_records ADD COLUMN IF NOT EXISTS specialist_id BIGINT;
ALTER TABLE customer_service_records ADD CONSTRAINT fk_customer_service_records_specialist FOREIGN KEY (specialist_id) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_customer_service_records_specialist ON customer_service_records(specialist_id);
