package cl.bunnycure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Interceptor que bloquea el acceso a páginas admin si el usuario
 * debe cambiar su contraseña obligatoriamente.
 */
@Slf4j
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true; // No hay sesión, dejar pasar (Spring Security se encargará)
        }

        Boolean mustChangePassword = (Boolean) session.getAttribute("mustChangePassword");
        if (mustChangePassword == null || !mustChangePassword) {
            return true; // No necesita cambiar contraseña
        }

        // Si debe cambiar contraseña, solo permitir acceso a:
        // - /admin/change-password (la página de cambio)
        // - /logout (para poder salir)
        // - Recursos estáticos
        String requestURI = request.getRequestURI();
        
        if (requestURI.equals("/admin/change-password") ||
            requestURI.startsWith("/css/") ||
            requestURI.startsWith("/js/") ||
            requestURI.startsWith("/images/") ||
            requestURI.equals("/logout")) {
            return true;
        }

        // Bloquear acceso a cualquier otra página
        log.warn("[SECURITY] Usuario intentó acceder a {} pero debe cambiar contraseña", requestURI);
        response.sendRedirect(request.getContextPath() + "/admin/change-password?required=true");
        return false;
    }
}
