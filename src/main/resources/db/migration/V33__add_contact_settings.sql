-- V33__add_contact_settings.sql
-- Añade configuraciones de contacto y redes sociales para completar Fase 2

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.website.url', 'https://www.bunnycure.cl', 'URL del sitio web principal'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.website.url');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.instagram.url', 'https://www.instagram.com/bunny.cure', 'URL de Instagram'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.instagram.url');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.instagram.handle', '@bunny.cure', 'Handle de Instagram (@usuario)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.instagram.handle');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.phone.display', '+56 9 6449 9995', 'Teléfono para mostrar en templates'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.phone.display');
