-- H2 equivalent for keeping service_catalog_seq aligned with Hibernate allocationSize=1.

ALTER SEQUENCE service_catalog_seq INCREMENT BY 1;

ALTER SEQUENCE service_catalog_seq RESTART WITH 11;
