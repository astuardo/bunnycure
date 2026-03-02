-- Crear tabla catálogo de servicios
CREATE TABLE service_catalog (
                                 id               BIGINT PRIMARY KEY,
                                 name             VARCHAR(100)   NOT NULL,
                                 duration_minutes INTEGER        NOT NULL,
                                 price            DECIMAL(10,2)  NOT NULL,
                                 description      VARCHAR(300),
                                 active           BOOLEAN        NOT NULL DEFAULT true,
                                 display_order    INTEGER        NOT NULL DEFAULT 0
);

-- Insertar servicios iniciales
INSERT INTO service_catalog (id, name, duration_minutes, price, active, display_order) VALUES
                                                                                           (1,  'Manicure + Brillo',            60,  10000, true, 1),
                                                                                           (2,  'Manicure Semi-Permanente',      90,  20000, true, 2),
                                                                                           (3,  'Manicure Men',                  90,  15000, true, 3),
                                                                                           (4,  'Kapping Gel',                  120,  22000, true, 4),
                                                                                           (5,  'Soft Gel',                     150,  27000, true, 5),
                                                                                           (6,  'Polygel Esculpido',            210,  35000, true, 6),
                                                                                           (7,  'Pedicure + Esmaltado',          90,  22000, true, 7),
                                                                                           (8,  'Pedicure + Esmaltado + Spa',   150,  25000, true, 8),
                                                                                           (9,  'Nail Art',                     240,  30000, true, 9),
                                                                                           (10, 'Retiro de Esmalte',             60,   7000, true, 10);

-- Modificar tabla appointments
ALTER TABLE appointments
    ADD COLUMN service_catalog_id BIGINT REFERENCES service_catalog(id);

-- Si ya tenías datos con service_type, migrar así:
-- UPDATE appointments SET service_catalog_id = 1 WHERE service_type = 'MANICURE_BRILLO';
-- Luego eliminar columna vieja:
ALTER TABLE appointments DROP COLUMN IF EXISTS service_type;