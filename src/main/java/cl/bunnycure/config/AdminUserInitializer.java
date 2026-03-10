package cl.bunnycure.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inicializador de usuarios por defecto en Heroku
 * Asegura que el usuario admin existe con la contraseña correcta
 * Se ejecuta después de que JPA está listo
 */
@Configuration
@Profile("heroku")
public class AdminUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    @Bean
    public CommandLineRunner initAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                // Check if admin user exists
                var adminUser = userRepository.findByUsername("admin");

                if (adminUser.isPresent()) {
                    // Preserve existing password to avoid resets on each deploy.
                    User user = adminUser.get();
                    user.setEnabled(true);
                    user.setRole("ADMIN");
                    userRepository.save(user);
                    log.info("[INIT] Admin user already exists; password preserved");
                } else {
                    // Create admin user if doesn't exist
                    User adminNewUser = User.builder()
                            .username("admin")
                            .password(passwordEncoder.encode("changeme"))
                            .fullName("Administrador")
                            .email("admin@bunnycure.cl")
                            .enabled(true)
                            .role("ADMIN")
                            .build();
                    userRepository.save(adminNewUser);
                    log.info("[INIT] Admin user created successfully");
                }
            } catch (Exception e) {
                log.error("[INIT] Error initializing admin user", e);
            }
        };
    }
}
