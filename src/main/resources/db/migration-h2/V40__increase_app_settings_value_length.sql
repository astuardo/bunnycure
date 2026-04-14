-- Aumentar la longitud de la columna setting_value para soportar configuraciones extensas (JSON)
-- En H2, CLOB o TEXT funcionan para longitudes grandes
ALTER TABLE app_settings ALTER COLUMN setting_value SET DATA TYPE TEXT;
