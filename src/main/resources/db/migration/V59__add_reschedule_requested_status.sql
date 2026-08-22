-- V59: Add RESCHEDULE_REQUESTED to appointments status check constraint
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'RESCHEDULE_REQUESTED'));
