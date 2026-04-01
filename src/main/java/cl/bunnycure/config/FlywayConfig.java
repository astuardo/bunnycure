package cl.bunnycure.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


@Configuration
@Profile({ "heroku", "local" })
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
