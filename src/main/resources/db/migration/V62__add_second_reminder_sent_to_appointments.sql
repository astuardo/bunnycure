-- Add second_reminder_sent column to appointments table
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS second_reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;

CREATE INDEX IF NOT EXISTS idx_appointments_second_reminder
    ON appointments(second_reminder_sent, status, appointment_date, appointment_time);
