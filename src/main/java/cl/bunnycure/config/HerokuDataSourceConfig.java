package cl.bunnycure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
 * 
 * ⚠️ OPTIMIZADO PARA MEMORIA:
 * - HikariCP con pool reducido (5 conexiones max para Heroku Eco)
 * - Connection lifetime y timeout optimizados
 * - Idle timeout agresivo para liberar memoria
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
        
        // ✅ OPTIMIZED: HikariCP configuration for Heroku memory constraints
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Pool size optimization for Heroku (Postgres supports ~20 connections on eco)
        config.setMaximumPoolSize(5);  // Reduced from default 10
        config.setMinimumIdle(1);      // Only keep 1 idle connection (was 10)
        config.setIdleTimeout(120000); // Close idle connections after 2 min (was 10 min)
        config.setMaxLifetime(600000); // Recycle connections after 10 min
        config.setConnectionTimeout(10000);
        config.setLeakDetectionThreshold(60000); // Log connections held > 1 min
        
        // Disable auto-commit for better performance
        config.setAutoCommit(true);
        
        System.out.println("✅ HikariCP Optimized: maxPoolSize=5, minIdle=1, idleTimeout=120s");
        
        return new HikariDataSource(config);
    }
}
