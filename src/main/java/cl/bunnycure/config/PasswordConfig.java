package cl.bunnycure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración aislada del codificador de contraseñas.
 * Desacopla PasswordEncoder de SecurityConfig para evitar ciclos de dependencia
 * con UserService y JwtAuthenticationFilter.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
