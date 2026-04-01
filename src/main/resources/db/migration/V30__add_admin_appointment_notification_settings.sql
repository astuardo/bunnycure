-- V30__add_admin_appointment_notification_settings.sql
-- Configuración para notificaciones de WhatsApp al admin cuando se crea una cita desde el dashboard
-- Incluye número de teléfono del admin, nombre de la dueña y configuración del template

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'whatsapp.admin-alert.number', '56964499995', 'Número de WhatsApp del admin/dueña para recibir alertas de citas creadas'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp.admin-alert.number');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'app.owner.name', 'Dueña', 'Nombre de la dueña del negocio para personalizar notificaciones'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'app.owner.name');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'whatsapp.template.admin-appointment-alert.enabled', 'true', 'Habilitar notificaciones de WhatsApp al admin cuando se crea una cita'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp.template.admin-appointment-alert.enabled');

INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'whatsapp.template.admin-appointment-alert.name', 'confirmacion_hora', 'Nombre del template de WhatsApp para alertas de citas al admin'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp.template.admin-appointment-alert.name');
