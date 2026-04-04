package cl.bunnycure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de OpenAPI/Swagger para documentación de la API REST.
 * 
 * Accesible en:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${spring.application.name:BunnyCure}")
    private String applicationName;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BunnyCure API")
                        .version("1.0.0")
                        .description("""
                                API REST para el sistema de gestión de centro estético BunnyCure.
                                
                                Esta API permite:
                                - Gestión de citas (appointments)
                                - Gestión de clientes (customers)
                                - Catálogo de servicios (services)
                                - Solicitudes de reserva (booking requests)
                                - Configuración y notificaciones
                                
                                **Autenticación**: Actualmente usa sesiones HTTP. 
                                En el futuro se migrará a JWT para la PWA.
                                """)
                        .contact(new Contact()
                                .name("BunnyCure Team")
                                .email("info@bunnycure.cl"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://bunnycure.cl")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("https://bunnycure-b9a0d88cd51b.herokuapp.com")
                                .description("Production server (Heroku)")
                ));
    }
}
