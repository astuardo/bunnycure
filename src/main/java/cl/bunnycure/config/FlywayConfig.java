package cl.bunnycure.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


/**
 * Solo desarrollo local: repara el historial antes de migrar para tolerar bases H2 a medio migrar.
 *
 * NO se aplica en producción: repair() recalcula los checksums de migraciones ya aplicadas,
 * lo que aceptaría en silencio cualquier edición de una migración antigua y anularía
 * spring.flyway.validate-on-migrate. En Heroku se usa la estrategia por defecto (migrate con
 * validación) y las migraciones se prueban antes sobre Postgres real (PostgresMigrationIntegrationTest).
 */
@Configuration
@Profile("local")
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            // Repair removes failed migration entries from the schema history table
            // and recalculates checksums for applied migrations
            flyway.repair();
            // Then run the migrations
            flyway.migrate();
        };
    }
}
