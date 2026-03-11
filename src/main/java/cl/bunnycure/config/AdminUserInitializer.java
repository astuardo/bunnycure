package cl.bunnycure.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Inicializador de usuarios por defecto en Heroku
 * Asegura que el usuario admin existe con la contraseña correcta
 * Se ejecuta después de que JPA está listo
 */
@Configuration
@Profile("heroku")
public class AdminUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final Set<String> LEGACY_DEFAULT_PASSWORDS = Set.of("changeme", "changeme-local-only");

    @Value("${bunnycure.admin.username:}")
    private String adminUsername;

    @Value("${bunnycure.admin.password:}")
    private String adminPassword;

    @Bean
    public CommandLineRunner initAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                if (adminUsername == null || adminUsername.isBlank()) {
                    throw new IllegalStateException("bunnycure.admin.username debe estar configurado en heroku");
                }

                if (adminPassword == null || adminPassword.isBlank() || "changeme".equalsIgnoreCase(adminPassword)) {
                    throw new IllegalStateException("bunnycure.admin.password inválido: define un secreto fuerte distinto de 'changeme'");
                }

                // Check if admin user exists
                var adminUser = userRepository.findByUsername(adminUsername);

                if (adminUser.isPresent()) {
                    // Preserve existing password unless the account still uses a legacy insecure password.
                    User user = adminUser.get();
                    user.setEnabled(true);
                    user.setRole("ADMIN");

                    boolean hasLegacyDefaultPassword = LEGACY_DEFAULT_PASSWORDS.stream()
                            .anyMatch(legacyPassword -> passwordEncoder.matches(legacyPassword, user.getPassword()));

                    if (hasLegacyDefaultPassword) {
                        user.setPassword(passwordEncoder.encode(adminPassword));
                        log.warn("[INIT] Admin user '{}' had legacy insecure password and was rotated from environment config", adminUsername);
                    } else {
                        log.info("[INIT] Admin user '{}' already exists; password preserved", adminUsername);
                    }

                    userRepository.save(user);
                } else {
                    // Create admin user if doesn't exist
                    User adminNewUser = User.builder()
                            .username(adminUsername)
                            .password(passwordEncoder.encode(adminPassword))
                            .fullName("Administrador")
                            .email("admin@bunnycure.cl")
                            .enabled(true)
                            .role("ADMIN")
                            .build();
                    userRepository.save(adminNewUser);
                    log.info("[INIT] Admin user '{}' created successfully", adminUsername);
                }
            } catch (Exception e) {
                log.error("[INIT] Error initializing admin user", e);
                throw e;
            }
        };
    }
}
