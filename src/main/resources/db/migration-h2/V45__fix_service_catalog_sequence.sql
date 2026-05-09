-- H2 equivalent for service_catalog sequence generation.

CREATE SEQUENCE IF NOT EXISTS service_catalog_seq START WITH 1 INCREMENT BY 1;

-- V2 inserted the baseline services with IDs 1-10, so advance the sequence
-- beyond those seed rows for subsequent inserts.
ALTER SEQUENCE service_catalog_seq RESTART WITH 11;
