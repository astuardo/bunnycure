-- Flyway migration: create inventory-related tables

-- Sequence generators
CREATE SEQUENCE IF NOT EXISTS products_seq START 1 INCREMENT 1;
CREATE SEQUENCE IF NOT EXISTS service_material_usages_seq START 1 INCREMENT 1;
CREATE SEQUENCE IF NOT EXISTS inventory_movements_seq START 1 INCREMENT 1;

-- Products table
CREATE TABLE IF NOT EXISTS products (
  id bigserial PRIMARY KEY,
  name varchar(200) NOT NULL,
  purchase_price numeric(12,2) NOT NULL,
  purchase_url varchar(500),
  purchase_unit varchar(60) NOT NULL,
  consumption_unit varchar(60) NOT NULL,
  conversion_factor numeric(14,4) NOT NULL,
  stock_consumption_unit numeric(14,4) NOT NULL DEFAULT 0,
  created_at timestamp with time zone,
  updated_at timestamp with time zone
);

-- Service material usages (history of consumption per service)
CREATE TABLE IF NOT EXISTS service_material_usages (
  id bigserial PRIMARY KEY,
  product_id bigint NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
  service_id bigint NOT NULL,
  quantity numeric(14,4) NOT NULL,
  used_by_user_id bigint,
  used_at timestamp with time zone
);

-- Inventory movements (purchases, adjustments, consumption audit)
CREATE TABLE IF NOT EXISTS inventory_movements (
  id bigserial PRIMARY KEY,
  product_id bigint NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
  movement_type varchar(20) NOT NULL,
  quantity_consumption_unit numeric(14,4) NOT NULL,
  quantity_purchase_unit numeric(14,4),
  unit_purchase_price numeric(12,4),
  reference varchar(300),
  created_by bigint,
  created_at timestamp with time zone
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_movements_product_created_at ON inventory_movements(product_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_usages_service_id ON service_material_usages(service_id);
