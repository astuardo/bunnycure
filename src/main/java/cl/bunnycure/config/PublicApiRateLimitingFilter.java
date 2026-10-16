package cl.bunnycure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filtro de seguridad defensiva para limitar la tasa de peticiones (Rate Limiting)
 * en endpoints públicos sensibles como búsqueda de clientas (/api/customers/lookup)
 * e inicio de sesión (/api/auth/login) para prevenir escaneo masivo de PII y fuerza bruta.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PublicApiRateLimitingFilter extends OncePerRequestFilter {

    @Value("${bunnycure.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${bunnycure.ratelimit.lookup.max-per-minute:15}")
    private int maxLookupPerMinute;

    @Value("${bunnycure.ratelimit.login.max-per-minute:10}")
    private int maxLoginPerMinute;

    private static class RequestTracker {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();

        boolean allow(int maxRequests) {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                synchronized (this) {
                    if (now - windowStart > 60_000) {
                        count.set(0);
                        windowStart = now;
                    }
                }
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }

    private final Map<String, RequestTracker> lookupTrackers = new ConcurrentHashMap<>();
    private final Map<String, RequestTracker> loginTrackers = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String clientIp = extractClientIp(request);

        if (path.endsWith("/api/customers/lookup")) {
            RequestTracker tracker = lookupTrackers.computeIfAbsent(clientIp, k -> new RequestTracker());
            if (!tracker.allow(maxLookupPerMinute)) {
                log.warn("[RATE-LIMIT] Bloqueada petición excedida a lookup desde IP: {}", clientIp);
                sendTooManyRequestsResponse(response, "Has excedido el límite de consultas permitidas. Intenta nuevamente en un minuto.");
                return;
            }
        } else if (path.endsWith("/api/auth/login")) {
            RequestTracker tracker = loginTrackers.computeIfAbsent(clientIp, k -> new RequestTracker());
            if (!tracker.allow(maxLoginPerMinute)) {
                log.warn("[RATE-LIMIT] Bloqueada petición excedida a login desde IP: {}", clientIp);
                sendTooManyRequestsResponse(response, "Demasiados intentos de inicio de sesión. Por seguridad, espera un minuto.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        // La IP real la resuelve Tomcat (server.forward-headers-strategy=native).
        // No leer X-Forwarded-For a mano: su primer valor lo controla el cliente.
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        String json = String.format("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"%s\"}", message);
        response.getWriter().write(json);
    }
}
