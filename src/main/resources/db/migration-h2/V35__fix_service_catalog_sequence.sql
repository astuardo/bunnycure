-- Fix service_catalog sequence for H2
ALTER TABLE service_catalog ALTER COLUMN id RESTART WITH 100;
