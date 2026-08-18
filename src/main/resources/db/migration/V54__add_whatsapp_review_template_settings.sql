-- V54__add_whatsapp_review_template_settings.sql
-- Configuración para plantilla de WhatsApp de valoración de servicio (Google Review)

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'whatsapp.template.review.name', 'valoracion_servicio_google', 'Nombre del template de WhatsApp para solicitud de reseña/valoración en Google'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp.template.review.name');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'whatsapp.template.review.enabled', 'true', 'Habilitar despacho de plantilla de WhatsApp para solicitud de valoración'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp.template.review.enabled');
