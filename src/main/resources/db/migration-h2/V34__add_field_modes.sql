-- V34: Agregar configuración de modos de campos dinámicos
-- Permite configurar qué campos del formulario de reserva son: REQUIRED, OPTIONAL o HIDDEN

-- Insertar configuraciones para modos de campos
-- Valores posibles: 'REQUIRED', 'OPTIONAL', 'HIDDEN'

INSERT INTO app_settings (setting_key, setting_value, description) VALUES
    ('field.email.mode', 'OPTIONAL', 'Modo del campo email en formulario de reserva: REQUIRED, OPTIONAL o HIDDEN'),
    ('field.gender.mode', 'OPTIONAL', 'Modo del campo género en formulario de reserva: REQUIRED, OPTIONAL o HIDDEN'),
    ('field.birth-date.mode', 'OPTIONAL', 'Modo del campo fecha de nacimiento en formulario de reserva: REQUIRED, OPTIONAL o HIDDEN'),
    ('field.emergency-phone.mode', 'HIDDEN', 'Modo del campo teléfono de emergencia en formulario de reserva: REQUIRED, OPTIONAL o HIDDEN'),
    ('field.health-notes.mode', 'HIDDEN', 'Modo del campo notas de salud en formulario de reserva: REQUIRED, OPTIONAL o HIDDEN'),
    ('field.general-notes.mode', 'OPTIONAL', 'Modo del campo notas generales en formulario de reserva: REQUIRED, OPTIONAL o HIDDEN');
