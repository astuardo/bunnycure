-- Alinea el FK booking_requests.appointment_id con ON DELETE SET NULL
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE con.contype = 'f'
          AND rel.relname = 'booking_requests'
          AND nsp.nspname = current_schema()
          AND con.confrelid = 'appointments'::regclass
          AND con.conkey = ARRAY[
              (SELECT attnum FROM pg_attribute WHERE attrelid = rel.oid AND attname = 'appointment_id')
          ]
    LOOP
        EXECUTE format('ALTER TABLE booking_requests DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    ALTER TABLE booking_requests
        ADD CONSTRAINT fk_booking_request_appointment
        FOREIGN KEY (appointment_id)
        REFERENCES appointments(id)
        ON DELETE SET NULL;
END $$;