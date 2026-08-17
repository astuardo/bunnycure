CREATE SEQUENCE IF NOT EXISTS service_supplies_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS service_supplies (
  id BIGINT PRIMARY KEY,
  service_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity_consumption_unit DECIMAL(14,4) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT uk_service_supply_product UNIQUE (service_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_service_supplies_service_id ON service_supplies(service_id);
CREATE INDEX IF NOT EXISTS idx_service_supplies_product_id ON service_supplies(product_id);
