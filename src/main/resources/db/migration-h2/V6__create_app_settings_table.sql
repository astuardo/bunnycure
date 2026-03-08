-- H2 version - Tabla de configuraciones de la aplicacion
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500),
    description VARCHAR(200)
);

-- Insertar configuraciones por defecto (H2 compatible)
MERGE INTO app_settings (setting_key, setting_value, description)
KEY(setting_key)
VALUES
('email.enabled', 'true', 'Habilitar envio de emails'),
('whatsapp.enabled', 'true', 'Habilitar notificaciones por WhatsApp'),
('booking.enabled', 'true', 'Habilitar sistema de reservas publicas'),
('reminders.enabled', 'true', 'Habilitar envio automatico de recordatorios');
