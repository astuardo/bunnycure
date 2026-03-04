package cl.bunnycure.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Configuración de DataSource para Heroku.
 * Heroku proporciona DATABASE_URL en formato postgres://user:password@host:port/database
 * pero Spring Boot necesita jdbc:postgresql://host:port/database
 */
@Configuration
@Profile("heroku")
public class HerokuDataSourceConfig {

    @Bean
    public DataSource dataSource() throws URISyntaxException {
        String databaseUrl = System.getenv("DATABASE_URL");
        
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            throw new IllegalStateException(
                "DATABASE_URL environment variable is not set. " +
                "Make sure PostgreSQL addon is attached to your Heroku app: " +
                "heroku addons:create heroku-postgresql:essential-0 --app bunnycure"
            );
        }

        URI dbUri = new URI(databaseUrl);
        
        if (dbUri.getUserInfo() == null) {
            throw new IllegalStateException(
                "DATABASE_URL does not contain username and password. " +
                "URL format should be: postgres://user:password@host:port/database"
            );
        }
        
        String[] credentials = dbUri.getUserInfo().split(":");
        if (credentials.length < 2) {
            throw new IllegalStateException(
                "DATABASE_URL credentials are malformed. " +
                "Expected format: postgres://user:password@host:port/database"
            );
        }
        
        String username = credentials[0];
        String password = credentials[1];
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath();

        System.out.println("Connecting to PostgreSQL at: " + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath());
        
        return DataSourceBuilder
                .create()
                .url(dbUrl)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
