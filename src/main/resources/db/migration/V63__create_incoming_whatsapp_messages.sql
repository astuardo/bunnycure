-- V63: Create incoming_whatsapp_messages table for storing client messages from WhatsApp
CREATE TABLE incoming_whatsapp_messages (
    id BIGSERIAL PRIMARY KEY,
    wamid VARCHAR(120) UNIQUE,
    from_phone VARCHAR(30) NOT NULL,
    sender_name VARCHAR(120),
    content TEXT NOT NULL,
    message_type VARCHAR(30) DEFAULT 'text' NOT NULL,
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    is_read BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_incoming_wa_msgs_phone ON incoming_whatsapp_messages(from_phone);
CREATE INDEX idx_incoming_wa_msgs_created ON incoming_whatsapp_messages(created_at DESC);
CREATE INDEX idx_incoming_wa_msgs_read ON incoming_whatsapp_messages(is_read);
CREATE INDEX idx_incoming_wa_msgs_customer ON incoming_whatsapp_messages(customer_id);
