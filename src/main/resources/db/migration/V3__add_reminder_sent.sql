-- Add reminder_sent column to appointments table
ALTER TABLE appointments
ADD COLUMN reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;

-- Create index for better query performance
CREATE INDEX idx_appointments_reminder_sent 
ON appointments(reminder_sent, appointment_date, status);

-- Create index for finding pending reminders
CREATE INDEX idx_appointments_pending_reminders
ON appointments(status, appointment_date) 
WHERE reminder_sent = FALSE;
