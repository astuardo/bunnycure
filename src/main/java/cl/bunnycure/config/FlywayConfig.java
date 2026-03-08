package cl.bunnycure.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de Flyway para reparar migraciones fallidas antes de migrar.
 * Útil cuando el schema ya existe pero Flyway marcó migraciones como fallidas,
 * o cuando se cambian los scripts de migración (ej: al cambiar de PostgreSQL a H2).
 */
@Configuration
@Profile({"heroku", "local"})
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
