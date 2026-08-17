package cl.bunnycure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
 * 
 * OPTIMIZADO PARA HEROKU:
 * - Soporte explícito de SSL (sslmode=require)
 * - Tiempos de espera resilientes para arranques en frío de la base de datos
 * - Pool optimizado para memoria de dynos Eco/Basic
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
        int port = dbUri.getPort() > 0 ? dbUri.getPort() : 5432;
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + port + dbUri.getPath();

        System.out.println("Connecting to PostgreSQL at: " + dbUri.getHost() + ":" + port + dbUri.getPath());
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Heroku PostgreSQL requires SSL
        config.addDataSourceProperty("sslmode", "require");
        config.addDataSourceProperty("connectTimeout", "30");
        config.addDataSourceProperty("socketTimeout", "60");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        
        // Pool size optimization for Heroku Eco/Basic
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(120000);  // 2 min
        config.setMaxLifetime(600000);  // 10 min
        config.setConnectionTimeout(30000); // 30s
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(60000); // Wait up to 60s for DB on dyno cold starts
        config.setLeakDetectionThreshold(60000);
        config.setAutoCommit(true);
        
        System.out.println("✅ HikariCP Configured with SSL (sslmode=require, connectionTimeout=30s, initializationFailTimeout=60s)");
        
        return new HikariDataSource(config);
    }
}
