-- Add CONFIRMED status to AppointmentStatus enum
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE appointments ADD CONSTRAINT chk_status 
    CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'));
