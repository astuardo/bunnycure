package cl.bunnycure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom AuthenticationEntryPoint para API REST endpoints.
 * 
 * Retorna JSON 401 en vez de redirect HTML cuando no hay autenticación.
 * Esto es crítico para que el frontend pueda manejar correctamente
 * la expiración de sesión y no recibir páginas HTML inesperadas.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        
        String requestUri = request.getRequestURI();
        
        // Si es request a /api/*, retornar JSON 401
        if (requestUri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            // Estructura de error consistente con ApiResponse
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            Map<String, String> error = new HashMap<>();
            error.put("message", "No autenticado. Por favor, inicie sesión.");
            error.put("errorCode", "UNAUTHORIZED");
            
            errorResponse.put("error", error);
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(jsonResponse);
        } else {
            // Para páginas HTML normales, hacer redirect tradicional
            response.sendRedirect("/login");
        }
    }
}
