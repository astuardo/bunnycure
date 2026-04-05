package cl.bunnycure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración CORS para permitir acceso desde frontend separado (React PWA).
 * 
 * En desarrollo: permite localhost:5173 (Vite default)
 * En producción: configurar CORS_ALLOWED_ORIGINS con el dominio del frontend
 */
@Configuration
public class CorsConfig {
    
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:4173,http://localhost:3000}")
    private String allowedOriginsConfig;
    
    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethodsConfig;
    
    @Value("${cors.allowed-headers:*}")
    private String allowedHeadersConfig;
    
    @Value("${cors.exposed-headers:}")
    private String exposedHeadersConfig;
    
    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;
    
    @Value("${cors.max-age:3600}")
    private long maxAge;
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (frontend URLs)
        List<String> origins = Arrays.asList(allowedOriginsConfig.split(","));
        configuration.setAllowedOrigins(origins);
        
        // Métodos HTTP permitidos
        List<String> methods = Arrays.asList(allowedMethodsConfig.split(","));
        configuration.setAllowedMethods(methods);
        
        // Headers permitidos
        if ("*".equals(allowedHeadersConfig)) {
            configuration.addAllowedHeader("*");
        } else {
            List<String> headers = Arrays.asList(allowedHeadersConfig.split(","));
            configuration.setAllowedHeaders(headers);
        }
        
        // Headers expuestos en la respuesta (si se necesitan)
        if (!exposedHeadersConfig.isEmpty()) {
            List<String> exposed = Arrays.asList(exposedHeadersConfig.split(","));
            configuration.setExposedHeaders(exposed);
        }
        
        // Permitir credenciales (cookies, headers de autenticación)
        configuration.setAllowCredentials(allowCredentials);
        
        // Tiempo de caché de preflight (OPTIONS)
        configuration.setMaxAge(maxAge);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        
        // Aplicar CORS a rutas públicas y API
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/login", configuration);
        source.registerCorsConfiguration("/logout", configuration);
        source.registerCorsConfiguration("/dashboard", configuration);
        source.registerCorsConfiguration("/admin/**", configuration);
        source.registerCorsConfiguration("/reservar/**", configuration);
        source.registerCorsConfiguration("/forgot-password", configuration);
        source.registerCorsConfiguration("/reset-password", configuration);
        
        return source;
    }
}
