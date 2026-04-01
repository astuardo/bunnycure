-- V32__add_notification_preference_to_booking_requests.sql
-- Agrega campo para almacenar la preferencia de notificación en las solicitudes de reserva

-- Agregar columna
ALTER TABLE booking_requests 
ADD COLUMN notification_preference VARCHAR(20);

-- Establecer valor por defecto para registros existentes
UPDATE booking_requests 
SET notification_preference = 'BOTH'
WHERE notification_preference IS NULL;

-- Hacer la columna NOT NULL después de establecer valores
ALTER TABLE booking_requests 
ALTER COLUMN notification_preference SET NOT NULL;
