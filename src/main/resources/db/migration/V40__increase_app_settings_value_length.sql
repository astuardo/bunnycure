-- Aumentar la longitud de la columna setting_value para soportar configuraciones extensas (JSON)
-- Se cambia a TEXT para evitar límites de VARCHAR
ALTER TABLE app_settings ALTER COLUMN setting_value TYPE TEXT;
