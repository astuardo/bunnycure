-- Create sequences for entities that use @GeneratedValue(strategy = GenerationType.SEQUENCE)
-- This is needed for PostgreSQL compatibility

CREATE SEQUENCE IF NOT EXISTS appointments_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS customers_seq START WITH 1 INCREMENT BY 1;
