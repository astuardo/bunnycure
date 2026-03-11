-- Ensure appointments status constraint supports CONFIRMED in all environments/states.
-- Some existing databases use chk_status while others use appointments_status_check.
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED'));
