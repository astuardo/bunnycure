CREATE SEQUENCE IF NOT EXISTS customer_service_records_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS customer_service_records (
    id                       BIGINT PRIMARY KEY DEFAULT nextval('customer_service_records_seq'),
    customer_id              BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    source_message_id        VARCHAR(120) NOT NULL UNIQUE,
    whatsapp_media_id        VARCHAR(120) NOT NULL UNIQUE,
    source_from_phone        VARCHAR(20) NOT NULL,
    client_phone_in_payload  VARCHAR(20) NOT NULL,
    service_detail           VARCHAR(500) NOT NULL,
    photo_caption            VARCHAR(1000),
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_service_records_customer_id
    ON customer_service_records(customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_service_records_created_at
    ON customer_service_records(created_at);
