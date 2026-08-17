-- Flyway migration: create service_supplies table for service recipes (BOM)

CREATE SEQUENCE IF NOT EXISTS service_supplies_seq START 1 INCREMENT 1;

CREATE TABLE IF NOT EXISTS service_supplies (
  id bigserial PRIMARY KEY,
  service_id bigint NOT NULL REFERENCES service_catalog(id) ON DELETE CASCADE,
  product_id bigint NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  quantity_consumption_unit numeric(14,4) NOT NULL,
  created_at timestamp with time zone,
  updated_at timestamp with time zone,
  CONSTRAINT uk_service_supply_product UNIQUE (service_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_service_supplies_service_id ON service_supplies(service_id);
CREATE INDEX IF NOT EXISTS idx_service_supplies_product_id ON service_supplies(product_id);
