package cl.bunnycure.config;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * Manejador de éxito de autenticación que verifica si el usuario
 * debe cambiar su contraseña por defecto.
 */
@Component
public class PasswordChangeAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeAuthenticationSuccessHandler.class);

    private final UserRepository userRepository;
    
    @Lazy
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${bunnycure.admin.username:admin}")
    private String adminUsername;

    @Value("${bunnycure.admin.password:}")
    private String defaultPassword;

    public PasswordChangeAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
            boolean hasDefaultPassword = defaultPassword != null
                    && !defaultPassword.isBlank()
                    && passwordEncoder.matches(defaultPassword, user.getPassword());
            
            if (hasDefaultPassword) {
                log.warn("[SECURITY] Usuario '{}' tiene contraseña por defecto - redirigiendo a cambio obligatorio", username);
                
                // Marcar en sesión que debe cambiar contraseña
                request.getSession().setAttribute("mustChangePassword", true);
                request.getSession().setAttribute("changePasswordReason", "default");
                
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
