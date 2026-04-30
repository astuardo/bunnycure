CREATE TABLE IF NOT EXISTS gift_cards (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    pin_hash VARCHAR(255) NOT NULL,
    pin_failed_attempts INTEGER NOT NULL DEFAULT 0,
    pin_locked_until TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    beneficiary_customer_id BIGINT NOT NULL,
    beneficiary_name_snapshot VARCHAR(120) NOT NULL,
    beneficiary_phone_snapshot VARCHAR(25) NOT NULL,
    beneficiary_email_snapshot VARCHAR(150),
    buyer_name VARCHAR(120),
    buyer_phone VARCHAR(25),
    buyer_email VARCHAR(150),
    issued_at TIMESTAMP NOT NULL,
    expires_on DATE NOT NULL,
    total_amount INTEGER NOT NULL,
    paid_amount INTEGER NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    public_url VARCHAR(500) NOT NULL,
    created_by_user_id BIGINT,
    cancelled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_gift_cards_customer FOREIGN KEY (beneficiary_customer_id) REFERENCES customers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_gift_cards_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gift_cards_code ON gift_cards(code);
CREATE INDEX IF NOT EXISTS idx_gift_cards_status ON gift_cards(status);
CREATE INDEX IF NOT EXISTS idx_gift_cards_expires_on ON gift_cards(expires_on);
CREATE INDEX IF NOT EXISTS idx_gift_cards_beneficiary_phone ON gift_cards(beneficiary_phone_snapshot);

CREATE TABLE IF NOT EXISTS gift_card_items (
    id BIGSERIAL PRIMARY KEY,
    gift_card_id BIGINT NOT NULL,
    service_catalog_id BIGINT NOT NULL,
    service_name_snapshot VARCHAR(150) NOT NULL,
    unit_price_snapshot INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    redeemed_quantity INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_gift_card_items_gift_card FOREIGN KEY (gift_card_id) REFERENCES gift_cards(id) ON DELETE CASCADE,
    CONSTRAINT fk_gift_card_items_service FOREIGN KEY (service_catalog_id) REFERENCES service_catalog(id) ON DELETE RESTRICT,
    CONSTRAINT chk_gift_card_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_gift_card_items_redeemed CHECK (redeemed_quantity >= 0 AND redeemed_quantity <= quantity)
);

CREATE INDEX IF NOT EXISTS idx_gift_card_items_gift_card ON gift_card_items(gift_card_id);
CREATE INDEX IF NOT EXISTS idx_gift_card_items_service ON gift_card_items(service_catalog_id);

CREATE TABLE IF NOT EXISTS gift_card_events (
    id BIGSERIAL PRIMARY KEY,
    gift_card_id BIGINT NOT NULL,
    gift_card_item_id BIGINT NULL,
    event_type VARCHAR(30) NOT NULL,
    quantity INTEGER NULL,
    note VARCHAR(500),
    actor VARCHAR(20) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_username VARCHAR(80),
    request_ip VARCHAR(80),
    user_agent VARCHAR(500),
    related_event_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_gift_card_events_gift_card FOREIGN KEY (gift_card_id) REFERENCES gift_cards(id) ON DELETE CASCADE,
    CONSTRAINT fk_gift_card_events_item FOREIGN KEY (gift_card_item_id) REFERENCES gift_card_items(id) ON DELETE SET NULL,
    CONSTRAINT fk_gift_card_events_related FOREIGN KEY (related_event_id) REFERENCES gift_card_events(id) ON DELETE SET NULL,
    CONSTRAINT fk_gift_card_events_user FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gift_card_events_gift_card ON gift_card_events(gift_card_id);
CREATE INDEX IF NOT EXISTS idx_gift_card_events_type ON gift_card_events(event_type);
CREATE INDEX IF NOT EXISTS idx_gift_card_events_created_at ON gift_card_events(created_at);
