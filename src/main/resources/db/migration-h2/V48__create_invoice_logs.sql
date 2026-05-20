-- Create invoice logs table to track generated invoices (boletas de honorarios)
CREATE TABLE invoice_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    simple_api_transaction_id VARCHAR(100),
    invoice_number VARCHAR(50),
    amount_in_clp DECIMAL(15, 2) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED, ERROR
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_logs_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_invoice_logs_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    UNIQUE (appointment_id)
);

CREATE INDEX idx_invoice_logs_customer_id ON invoice_logs(customer_id);
CREATE INDEX idx_invoice_logs_status ON invoice_logs(status);
CREATE INDEX idx_invoice_logs_created_at ON invoice_logs(created_at);
