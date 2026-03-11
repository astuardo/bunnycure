ALTER TABLE booking_requests
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20);

ALTER TABLE booking_requests
    ADD COLUMN IF NOT EXISTS birth_date DATE;

ALTER TABLE booking_requests
    ADD COLUMN IF NOT EXISTS emergency_phone VARCHAR(15);
