-- Add reminder_sent column to appointments table (only if not exists)
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;

-- Create index for better query performance (only if not exists)
CREATE INDEX IF NOT EXISTS idx_appointments_reminder_sent 
ON appointments(reminder_sent, appointment_date, status);
