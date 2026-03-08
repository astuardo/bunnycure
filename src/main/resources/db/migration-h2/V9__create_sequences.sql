-- H2 version - Create sequences explicitly for Hibernate validation
-- Aunque H2 maneja las secuencias internamente con IDENTITY,
-- Hibernate requiere que existan para la validación del esquema

CREATE SEQUENCE IF NOT EXISTS appointments_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS customers_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS booking_requests_seq START WITH 1 INCREMENT BY 1;

