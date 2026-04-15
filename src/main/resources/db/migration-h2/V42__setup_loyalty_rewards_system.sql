CREATE TABLE loyalty_rewards (
    id IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    order_index INT NOT NULL DEFAULT 0
);

CREATE TABLE loyalty_reward_history (
    id IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    reward_name VARCHAR(255) NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    appointment_id BIGINT,
    CONSTRAINT fk_reward_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_reward_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id)
);

ALTER TABLE customers ADD COLUMN current_reward_index INT DEFAULT 0 NOT NULL;

-- Insertar premios por defecto iniciales
INSERT INTO loyalty_rewards (name, order_index) VALUES ('Servicio Gratis', 0);
INSERT INTO loyalty_rewards (name, order_index) VALUES ('50% Descuento en Próximo Servicio', 1);
INSERT INTO loyalty_rewards (name, order_index) VALUES ('Regalo Sorpresa', 2);
