package cl.bunnycure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Manejador de éxito de autenticación que verifica si el usuario
 * debe cambiar su contraseña por defecto.
 */
@Component
public class PasswordChangeAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeAuthenticationSuccessHandler.class);

    @Value("${bunnycure.admin.username:admin}")
    private String adminUsername;

    @Value("${bunnycure.admin.password:changeme}")
    private String defaultPassword;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        
        String username = authentication.getName();
        log.info("[AUTH] Usuario autenticado exitosamente: {}", username);

        // Verificar si es el usuario admin y si la contraseña es la por defecto
        if (adminUsername.equals(username) && "changeme".equals(defaultPassword)) {
            log.warn("[SECURITY] Usuario '{}' tiene contraseña por defecto - redirigiendo a cambio obligatorio", username);
            
            // Marcar en sesión que debe cambiar contraseña
            request.getSession().setAttribute("mustChangePassword", true);
            request.getSession().setAttribute("changePasswordReason", "default");
            
            response.sendRedirect(request.getContextPath() + "/admin/change-password?required=true");
        } else {
            // Login normal - ir al dashboard
            response.sendRedirect(request.getContextPath() + "/dashboard");
        }
    }
}
