package cl.bunnycure.config;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Manejador de éxito de autenticación que verifica si el usuario
 * debe cambiar su contraseña por defecto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordChangeAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Set<String> LEGACY_DEFAULT_PASSWORDS = Set.of("changeme", "changeme-local-only");

    private final UserRepository userRepository;
    
    @Lazy
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        
        String username = authentication.getName();
        log.info("[AUTH] Usuario autenticado exitosamente: {}", username);

        // Verificar si el usuario tiene la contraseña por defecto
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            boolean hasLegacyDefaultPassword = LEGACY_DEFAULT_PASSWORDS.stream()
                    .anyMatch(legacyPassword -> passwordEncoder.matches(legacyPassword, user.getPassword()));
            
            if (hasLegacyDefaultPassword) {
                log.warn("[SECURITY] Usuario '{}' tiene contraseña legacy insegura - redirigiendo a cambio obligatorio", username);
                
                // Marcar en sesión que debe cambiar contraseña
                request.getSession().setAttribute("mustChangePassword", true);
                request.getSession().setAttribute("changePasswordReason", "legacy-default");
                
                response.sendRedirect(request.getContextPath() + "/admin/change-password?required=true");
            } else {
                // Login normal - ir al dashboard
                log.info("[AUTH] Usuario '{}' con contraseña actualizada - acceso normal", username);
                response.sendRedirect(request.getContextPath() + "/dashboard");
            }
        } else {
            // Usuario no encontrado (no debería pasar)
            log.error("[AUTH] Usuario '{}' no encontrado en BD", username);
            response.sendRedirect(request.getContextPath() + "/dashboard");
        }
    }
}
