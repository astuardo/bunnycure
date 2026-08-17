package cl.bunnycure.config;

import cl.bunnycure.service.PwaRedirectService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro HTTP para la Fase 2 de migración hacia la PWA.
 * Intercepta peticiones a rutas de vistas del monolito tradicional y emite una
 * redirección permanente HTTP 301 (Moved Permanently) hacia la aplicación frontend (React/Vite).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PwaRedirectFilter extends OncePerRequestFilter {

    private final PwaRedirectService pwaRedirectService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Aplicar redirección solo si está habilitada y es una petición GET o HEAD a una vista legada
        if (pwaRedirectService.isRedirectionEnabled()
                && (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method))
                && pwaRedirectService.isLegacyMonolithPath(path)) {

            String redirectUrl = pwaRedirectService.resolvePwaRedirectUrl(request);
            log.info("[PWA REDIRECT 301] Redirigiendo petición {} '{}' -> '{}'", method, path, redirectUrl);

            response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
            response.setHeader(HttpHeaders.LOCATION, redirectUrl);
            response.setHeader("X-Deprecation-Notice", "Monolith view is deprecated. Redirected to BunnyCure PWA.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
