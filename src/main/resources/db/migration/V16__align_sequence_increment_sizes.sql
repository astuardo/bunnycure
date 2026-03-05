-- Align PostgreSQL sequence increment sizes with JPA allocationSize=1
-- This fixes Hibernate schema-validation errors like:
-- "sequence [...] defined inconsistent increment-size; found [50] but expecting [1]"

CREATE SEQUENCE IF NOT EXISTS customers_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS booking_requests_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS appointments_seq START WITH 1 INCREMENT BY 1;

ALTER SEQUENCE customers_seq INCREMENT BY 1;
ALTER SEQUENCE booking_requests_seq INCREMENT BY 1;
ALTER SEQUENCE appointments_seq INCREMENT BY 1;

SELECT setval('customers_seq', COALESCE((SELECT MAX(id) FROM customers), 0) + 1, false);
SELECT setval('booking_requests_seq', COALESCE((SELECT MAX(id) FROM booking_requests), 0) + 1, false);
SELECT setval('appointments_seq', COALESCE((SELECT MAX(id) FROM appointments), 0) + 1, false);
