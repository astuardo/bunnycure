CREATE SEQUENCE IF NOT EXISTS customer_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS appointment_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE customers (
    id            BIGINT PRIMARY KEY DEFAULT nextval('customer_seq'),
    full_name     VARCHAR(100)  NOT NULL,
    phone         VARCHAR(15)   NOT NULL,
    email         VARCHAR(150)  NOT NULL UNIQUE,
    notes         VARCHAR(500),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_email ON customers(email);
CREATE INDEX idx_customer_name  ON customers(full_name);

CREATE TABLE appointments (
    id                BIGINT PRIMARY KEY DEFAULT nextval('appointment_seq'),
    customer_id       BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    appointment_date  DATE         NOT NULL,
    appointment_time  TIME         NOT NULL,
    service_type      VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    observations      VARCHAR(500),
    notification_sent BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status       CHECK (status IN ('PENDING','COMPLETED','CANCELLED')),
    CONSTRAINT chk_service_type CHECK (service_type IN (
        'BATH_AND_GROOMING','NAIL_TRIMMING','HAIRCUT','FULL_SPA','CONSULTATION'
    ))
);

CREATE INDEX idx_appointment_date      ON appointments(appointment_date);
CREATE INDEX idx_appointment_status    ON appointments(status);
CREATE INDEX idx_appointment_customer  ON appointments(customer_id);
