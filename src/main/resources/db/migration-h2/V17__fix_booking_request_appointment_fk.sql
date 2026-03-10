-- Mantiene consistencia con PostgreSQL: al borrar cita, desvincula booking_requests
ALTER TABLE booking_requests DROP CONSTRAINT IF EXISTS fk_booking_request_appointment;

ALTER TABLE booking_requests
    ADD CONSTRAINT fk_booking_request_appointment
    FOREIGN KEY (appointment_id)
    REFERENCES appointments(id)
    ON DELETE SET NULL;