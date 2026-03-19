-- V29__add_branding_settings_fase_1.sql
-- Fase 1: Parametrización de Identidad & Branding
-- Agrega claves de configuración dinámica para nombre, eslogan, colores, 
-- zona horaria, locale y moneda del negocio.

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.name', 'BunnyCure', 'Nombre del negocio' 
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.name');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.slogan', 'Arte en tus manos ✨', 'Eslogan del negocio'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.slogan');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.email', 'contacto@bunnycure.cl', 'Email de contacto del negocio'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.email');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.logo-url', '/images/logo.png', 'URL del logo del negocio (ruta relativa o absoluta)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.logo-url');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.primary-color', '#F472B6', 'Color primario en HEX (ej: #F472B6 para rosa)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.primary-color');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.secondary-color', '#8B5CF6', 'Color secundario en HEX (ej: #8B5CF6 para púrpura)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.secondary-color');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.timezone', 'America/Santiago', 'Zona horaria del negocio (IANA timezone)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.timezone');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.locale', 'es_CL', 'Locale del negocio (ej: es_CL, en_US, pt_BR)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.locale');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.currency', 'CLP', 'Moneda del negocio (ej: CLP, USD, ARS, MXN)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.currency');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.service-tip', 'Llega con las uñas limpias y sin esmalte', 'Consejo personalizado para el servicio (visible en emails)'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.service-tip');
