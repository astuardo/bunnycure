CREATE TABLE appointment_services (
    appointment_id BIGINT NOT NULL,
    service_catalog_id BIGINT NOT NULL,
    PRIMARY KEY (appointment_id, service_catalog_id),
    CONSTRAINT fk_appointment_services_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_services_service
        FOREIGN KEY (service_catalog_id) REFERENCES service_catalog(id) ON DELETE RESTRICT
);

CREATE INDEX idx_appointment_services_service_id
    ON appointment_services(service_catalog_id);

INSERT INTO appointment_services (appointment_id, service_catalog_id)
SELECT a.id, a.service_catalog_id
FROM appointments a
WHERE a.service_catalog_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE service_catalog_compatibility (
    service_id BIGINT NOT NULL,
    compatible_service_id BIGINT NOT NULL,
    PRIMARY KEY (service_id, compatible_service_id),
    CONSTRAINT fk_service_compatibility_service
        FOREIGN KEY (service_id) REFERENCES service_catalog(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_compatibility_compatible
        FOREIGN KEY (compatible_service_id) REFERENCES service_catalog(id) ON DELETE CASCADE,
    CONSTRAINT chk_service_compatibility_no_self
        CHECK (service_id <> compatible_service_id)
);

CREATE INDEX idx_service_compatibility_compatible_id
    ON service_catalog_compatibility(compatible_service_id);
