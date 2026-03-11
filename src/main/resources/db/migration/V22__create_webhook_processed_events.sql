CREATE TABLE IF NOT EXISTS webhook_processed_events (
    event_id VARCHAR(120) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_webhook_processed_events_expires_at
    ON webhook_processed_events (expires_at);
