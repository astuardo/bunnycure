-- Add reminder_sent column to appointments table (only if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='appointments' AND column_name='reminder_sent') THEN
        ALTER TABLE appointments
        ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;
    END IF;
END $$;

-- Create index for better query performance (only if not exists)
CREATE INDEX IF NOT EXISTS idx_appointments_reminder_sent 
ON appointments(reminder_sent, appointment_date, status);
