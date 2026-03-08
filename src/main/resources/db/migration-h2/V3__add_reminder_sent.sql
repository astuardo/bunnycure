-- H2 version - Add reminder_sent column to appointments table
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;

-- Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_appointments_reminder_sent 
ON appointments(reminder_sent, appointment_date, status);
