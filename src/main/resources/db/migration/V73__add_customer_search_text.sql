-- Texto de búsqueda normalizado de clientas (nombre, teléfono, RUT y email en minúsculas, sin tildes
-- ni signos). Permite encontrar "María Pérez" escribiendo "maria perez", "perez maria" o su RUT sin puntos.
-- Misma regla que cl.*.domain.model.CustomerSearchText, que lo mantiene al guardar cada clienta.

ALTER TABLE customers ADD COLUMN IF NOT EXISTS search_text VARCHAR(400);

UPDATE customers
SET search_text = btrim(regexp_replace(
        regexp_replace(
            translate(lower(concat_ws(' ', full_name, phone, rut, email)),
                      'áàäâãéèëêíìïîóòöôõúùüûñç',
                      'aaaaaeeeeiiiiooooouuuunc'),
            '[^a-z0-9[:space:]]', '', 'g'),
        '[[:space:]]+', ' ', 'g'));
