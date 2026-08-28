-- Agregar columnas para soporte de ApiGateway SII v2 en entorno H2
ALTER TABLE invoice_logs ADD COLUMN IF NOT EXISTS sii_code VARCHAR(100);
ALTER TABLE invoice_logs ADD COLUMN IF NOT EXISTS sii_barcode VARCHAR(255);
ALTER TABLE invoice_logs ADD COLUMN IF NOT EXISTS email_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE invoice_logs ADD COLUMN IF NOT EXISTS email_sent_at TIMESTAMP;
ALTER TABLE invoice_logs ADD COLUMN IF NOT EXISTS email_recipient VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_invoice_logs_sii_code ON invoice_logs(sii_code);
CREATE INDEX IF NOT EXISTS idx_invoice_logs_invoice_number ON invoice_logs(invoice_number);
