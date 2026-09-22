-- V64: Create whatsapp_outbox_messages table for failed / pending WhatsApp messages
CREATE TABLE IF NOT EXISTS whatsapp_outbox_messages (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT REFERENCES appointments(id) ON DELETE SET NULL,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEMPLATE',
    template_name VARCHAR(100),
    language_code VARCHAR(20) DEFAULT 'es_CL',
    header_param VARCHAR(200),
    body_params_json TEXT,
    text_content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    attempt_count INTEGER NOT NULL DEFAULT 1,
    last_error TEXT,
    last_attempt_at TIMESTAMP WITHOUT TIME ZONE,
    sent_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_whatsapp_outbox_status
        CHECK (status IN ('PENDING', 'RETRY', 'SENT', 'FAILED', 'DISCARDED'))
);

CREATE INDEX idx_wa_outbox_status ON whatsapp_outbox_messages(status);
CREATE INDEX idx_wa_outbox_created_at ON whatsapp_outbox_messages(created_at DESC);
CREATE INDEX idx_wa_outbox_recipient ON whatsapp_outbox_messages(recipient_phone);
CREATE INDEX idx_wa_outbox_appointment ON whatsapp_outbox_messages(appointment_id);
