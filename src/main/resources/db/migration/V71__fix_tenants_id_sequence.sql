-- ==========================================================
-- V71: Sincronizar la secuencia de tenants.id
-- ==========================================================
-- V58 insertó el salón principal con id = 1 explícito, lo que no avanza la identidad:
-- el primer salón creado después recibiría id = 1 y fallaría por clave duplicada.
SELECT setval(pg_get_serial_sequence('tenants', 'id'), (SELECT COALESCE(MAX(id), 1) FROM tenants), true);
