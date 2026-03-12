ALTER TABLE customer_service_records
    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(120);

ALTER TABLE customer_service_records
    ADD COLUMN IF NOT EXISTS media_sha256 VARCHAR(120);

ALTER TABLE customer_service_records
    ADD COLUMN IF NOT EXISTS photo_data BYTEA;
