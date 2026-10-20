-- Texto de búsqueda normalizado de clientas (ver la versión PostgreSQL de esta migración).

ALTER TABLE customers ADD COLUMN IF NOT EXISTS search_text VARCHAR(400);

UPDATE customers
SET search_text = TRIM(REGEXP_REPLACE(
        REGEXP_REPLACE(
            TRANSLATE(LOWER(CONCAT_WS(' ', full_name, phone, rut, email)),
                      'áàäâãéèëêíìïîóòöôõúùüûñç',
                      'aaaaaeeeeiiiiooooouuuunc'),
            '[^a-z0-9\s]', ''),
        '\s+', ' '));
