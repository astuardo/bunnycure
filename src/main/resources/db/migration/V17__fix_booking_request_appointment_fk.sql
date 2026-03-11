-- Alinea el FK booking_requests.appointment_id con ON DELETE SET NULL
ALTER TABLE booking_requests
    DROP CONSTRAINT IF EXISTS fk_booking_request_appointment;

ALTER TABLE booking_requests
    ADD CONSTRAINT fk_booking_request_appointment
    FOREIGN KEY (appointment_id)
    REFERENCES appointments(id)
    ON DELETE SET NULL;