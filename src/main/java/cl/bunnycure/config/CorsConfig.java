package cl.bunnycure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración CORS para permitir acceso desde frontend separado (React PWA).
 * 
 * En desarrollo: permite localhost:5173 (Vite default)
 * En producción: configurar CORS_ALLOWED_ORIGINS con el dominio del frontend
 */
@Configuration
public class CorsConfig {
    
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:4173,http://localhost:3000,https://bunnycure-frontend.vercel.app}")
    private String allowedOriginsConfig;
    
    @Value("${cors.allowed-origin-patterns:https://*.vercel.app,https://*.bunnycure.cl}")
    private String allowedOriginPatternsConfig;
    
    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS,HEAD}")
    private String allowedMethodsConfig;
    
    @Value("${cors.allowed-headers:Authorization,Content-Type,Accept,Origin,X-XSRF-TOKEN,X-CSRF-TOKEN,X-Requested-With,Baggage,Sentry-Trace}")
    private String allowedHeadersConfig;
    
    @Value("${cors.exposed-headers:Authorization,Location,Content-Disposition,Set-Cookie,X-XSRF-TOKEN,X-Deprecation-Notice}")
    private String exposedHeadersConfig;
    
    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;
    
    @Value("${cors.max-age:3600}")
    private long maxAge;
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (frontend URLs específicas)
        List<String> rawOrigins = parseCsvList(allowedOriginsConfig);
        List<String> origins = new ArrayList<>();
        List<String> patterns = new ArrayList<>(parseCsvList(allowedOriginPatternsConfig));

        for (String origin : rawOrigins) {
            if ("*".equals(origin)) {
                if (allowCredentials) {
                    // Spring CORS no permite "*" en allowedOrigins cuando allowCredentials=true
                    if (!patterns.contains("*")) {
                        patterns.add("*");
                    }
                } else {
                    origins.add(origin);
                }
            } else {
                origins.add(origin);
            }
        }

        if (!origins.isEmpty()) {
            configuration.setAllowedOrigins(origins);
        }
        
        // Patrones de origen permitidos (para preview deployments Vercel y subdominios)
        if (!patterns.isEmpty()) {
            configuration.setAllowedOriginPatterns(patterns);
        }
        
        // Métodos HTTP permitidos
        List<String> methods = parseCsvList(allowedMethodsConfig);
        configuration.setAllowedMethods(methods);
        
        // Headers permitidos
        List<String> headers = parseCsvList(allowedHeadersConfig);
        configuration.setAllowedHeaders(headers);
        
        // Headers expuestos en la respuesta
        if (!exposedHeadersConfig.isEmpty()) {
            List<String> exposed = parseCsvList(exposedHeadersConfig);
            configuration.setExposedHeaders(exposed);
        }
        
        // Permitir credenciales (cookies, headers de autenticación)
        configuration.setAllowCredentials(allowCredentials);
        
        // Tiempo de caché de preflight (OPTIONS)
        configuration.setMaxAge(maxAge);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        
        // Aplicar CORS globalmente a todas las rutas
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    private List<String> parseCsvList(String value) {
        List<String> entries = new ArrayList<>();
        if (value == null) {
            return entries;
        }

        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }

        return entries;
    }
}
