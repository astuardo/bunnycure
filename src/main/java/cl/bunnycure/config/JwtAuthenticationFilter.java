package cl.bunnycure.config;

import cl.bunnycure.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que intercepta todas las requests y verifica si incluyen un token JWT.
 * Si el token es válido, autentica al usuario en el contexto de seguridad.
 * 
 * Permite autenticación dual: JWT (móvil) o Session Cookie (desktop).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        log.debug("[JWT-FILTER] ========== Request ==========");
        log.debug("[JWT-FILTER] {} {}", method, requestUri);

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Si no hay header Authorization o no es Bearer, continuar con el flujo normal
        // (puede ser autenticación por cookie de sesión)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("[JWT-FILTER] No Authorization header o no es Bearer");
            log.debug("[JWT-FILTER] Continuando con autenticación por sesión...");
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer el token JWT
        jwt = authHeader.substring(7);
        log.info("[JWT-FILTER] ========== JWT DETECTADO ==========");
        log.info("[JWT-FILTER] Request: {} {}", method, requestUri);
        log.info("[JWT-FILTER] Procesando token JWT recibido");

        try {
            // Extraer username del token
            username = jwtService.extractUsername(jwt);
            log.info("[JWT-FILTER] Username extraído del token: {}", username);

            // Si hay username y el usuario no está ya autenticado
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                log.debug("[JWT-FILTER] Cargando UserDetails para: {}", username);
                
                // Cargar datos del usuario
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                log.debug("[JWT-FILTER] UserDetails cargado. Authorities: {}", userDetails.getAuthorities());

                // Validar el token
                if (jwtService.validateToken(jwt, userDetails)) {
                    log.info("[JWT-FILTER] ✓ Token VÁLIDO para usuario: {}", username);

                    // Crear authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // Setear en el contexto de seguridad
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("[JWT-FILTER] ✓ Usuario autenticado vía JWT: {}", username);
                    log.info("[JWT-FILTER] =========================================");
                } else {
                    log.warn("[JWT-FILTER] ✗ Token INVÁLIDO o EXPIRADO");
                }
            } else if (username == null) {
                log.warn("[JWT-FILTER] ✗ No se pudo extraer username del token");
            } else {
                log.debug("[JWT-FILTER] Usuario ya autenticado en el contexto: {}", 
                    SecurityContextHolder.getContext().getAuthentication().getName());
            }
        } catch (ExpiredJwtException e) {
            // Token expirado: devolver 401 para que PWA refresque
            log.warn("[JWT-FILTER] ✗ Token expirado. El cliente debe refrescar. Mensaje: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"token_expired\",\"message\":\"El token ha expirado. Por favor, refresque la sesión.\"}");
            return;
        } catch (Exception e) {
            log.error("[JWT-FILTER] ✗ ERROR procesando token JWT: {} - {}", 
                e.getClass().getSimpleName(), e.getMessage());
            log.debug("[JWT-FILTER] Stack trace:", e);
        }

        filterChain.doFilter(request, response);
    }
}
