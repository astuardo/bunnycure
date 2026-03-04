-- Tabla de configuraciones de la aplicacion
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500),
    description VARCHAR(200)
);

-- Insertar configuraciones por defecto (solo si no existen)
INSERT INTO app_settings (setting_key, setting_value, description)
SELECT 'email.enabled', 'true', 'Habilitar envio de emails'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'email.enabled')
UNION ALL
SELECT 'whatsapp.enabled', 'true', 'Habilitar notificaciones por WhatsApp'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'whatsapp.enabled')
UNION ALL
SELECT 'booking.enabled', 'true', 'Habilitar sistema de reservas publicas'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'booking.enabled')
UNION ALL
SELECT 'reminders.enabled', 'true', 'Habilitar envio automatico de recordatorios'
WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE setting_key = 'reminders.enabled');
