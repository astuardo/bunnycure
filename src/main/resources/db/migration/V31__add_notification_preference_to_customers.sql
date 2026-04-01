-- V31__add_notification_preference_to_customers.sql
-- Agrega campo para almacenar la preferencia de notificación del cliente
-- Opciones: EMAIL_ONLY, WHATSAPP_ONLY, BOTH, NONE

-- Agregar columna con valor por defecto temporal NULL
ALTER TABLE customers 
ADD COLUMN notification_preference VARCHAR(20);

-- Establecer valores por defecto basados en datos existentes
-- Si tiene email y teléfono: BOTH
-- Si solo tiene teléfono (email is null): WHATSAPP_ONLY  
-- Si solo tiene email: EMAIL_ONLY
UPDATE customers 
SET notification_preference = CASE
    WHEN email IS NOT NULL AND email != '' AND phone IS NOT NULL AND phone != '' THEN 'BOTH'
    WHEN (email IS NULL OR email = '') AND phone IS NOT NULL AND phone != '' THEN 'WHATSAPP_ONLY'
    WHEN email IS NOT NULL AND email != '' AND (phone IS NULL OR phone = '') THEN 'EMAIL_ONLY'
    ELSE 'BOTH'
END;

-- Hacer la columna NOT NULL ahora que tiene valores
ALTER TABLE customers 
ALTER COLUMN notification_preference SET NOT NULL;

-- Comentario descriptivo
COMMENT ON COLUMN customers.notification_preference IS 'Preferencia de notificación del cliente: EMAIL_ONLY, WHATSAPP_ONLY, BOTH, NONE';
