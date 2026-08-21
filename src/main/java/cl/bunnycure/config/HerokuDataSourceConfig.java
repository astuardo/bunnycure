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
 * OPTIMIZADO PARA HEROKU & AWS RDS:
 * - Soporte explícito de SSL (sslmode=require) tanto en URL como en propiedades de driver
 * - Pool cálido (minimumIdle=3) y keepaliveTime (30s) para evitar timeouts por desconexión en reposo
 * - Tiempos de espera resilientes para arranques y consultas concurrentes
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
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + port + dbUri.getPath() + "?sslmode=require";

        System.out.println("Connecting to PostgreSQL at: " + dbUri.getHost() + ":" + port + dbUri.getPath() + " with sslmode=require");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // PostgreSQL Driver properties
        config.addDataSourceProperty("sslmode", "require");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("connectTimeout", "30");
        config.addDataSourceProperty("loginTimeout", "30");
        
        // Pool configuration for Heroku Eco/Basic with background schedulers
        config.setMaximumPoolSize(6);               // Capacidad optimizada para Dyno de 512MB
        config.setMinimumIdle(2);                   // Mantener 2 conexiones cálidas listas
        config.setKeepaliveTime(30000);             // Ping periódico de 30s para evitar cortes de NAT en AWS RDS
        config.setIdleTimeout(300000);              // 5 minutos
        config.setMaxLifetime(1200000);             // 20 minutos
        config.setConnectionTimeout(20000);         // 20s
        config.setValidationTimeout(3000);          // 3s
        config.setInitializationFailTimeout(60000); // 60s en arranque inicial
        config.setLeakDetectionThreshold(60000);
        config.setAutoCommit(true);
        
        System.out.println("✅ HikariCP Configured with SSL & KeepAlive (maxPoolSize=6, minIdle=2, keepaliveTime=30s, connectionTimeout=20s)");
        
        return new HikariDataSource(config);
    }
}
