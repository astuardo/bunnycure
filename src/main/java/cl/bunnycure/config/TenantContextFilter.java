package cl.bunnycure.config;

import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Filtro HTTP que intercepta las peticiones y resuelve el Tenant/Salón.
 * Blindado contra ataques de suplantación de inquilinos (Tenant Impersonation).
 *
 * Para usuarios autenticados:
 * Extrae el tenantId directamente del token JWT verificado y lo fuerza en el contexto.
 * Si el cliente envía headers solicitando un tenant distinto y no es ROLE_SUPER_ADMIN,
 * se rechaza la petición con HTTP 403 Forbidden.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Autowired
    public TenantContextFilter(TenantRepository tenantRepository, JwtService jwtService, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public TenantContextFilter(TenantRepository tenantRepository) {
        this(tenantRepository, null, null);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            Long effectiveTenantId = resolveEffectiveTenantId(request, response);
            if (effectiveTenantId == null) {
                // Respuesta ya enviada (ej. 403 Forbidden por intento de acceso cruzado no autorizado)
                return;
            }
            TenantContext.setCurrentTenantId(effectiveTenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveEffectiveTenantId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;

        Long tokenTenantId = null;
        String username = null;

        if (jwt != null && jwtService != null) {
            try {
                if (jwtService.validateToken(jwt)) {
                    tokenTenantId = jwtService.extractTenantId(jwt);
                    username = jwtService.extractUsername(jwt);
                }
            } catch (Exception ex) {
                log.debug("[TENANT-FILTER] Error al validar JWT en TenantContextFilter: {}", ex.getMessage());
            }
        }

        // Si no hay JWT, verificar sesión HTTP como fallback
        if (tokenTenantId == null) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("tenantId") instanceof Long sId) {
                tokenTenantId = sId;
            }
        }

        // Si el usuario está autenticado (vía JWT o sesión)
        if (tokenTenantId != null) {
            // Verificar si el cliente intentó enviar un tenant específico por headers o query
            Long requestedTenantId = resolveRequestedTenantId(request);

            if (requestedTenantId != null && !requestedTenantId.equals(tokenTenantId)) {
                // Verificar si el usuario es SUPER_ADMIN
                boolean isSuperAdmin = false;
                if (username != null && userRepository != null) {
                    // username es único por tenant: buscar en el tenant del token verificado
                    Optional<User> userOpt = userRepository.findByUsernameAndTenantId(username, tokenTenantId);
                    isSuperAdmin = userOpt.map(User::isSuperAdmin).orElse(false);
                }

                if (!isSuperAdmin) {
                    log.warn("[TENANT-SECURITY] Acceso denegado: Usuario '{}' (Tenant {}) intentó acceder a Tenant {} sin rol ROLE_SUPER_ADMIN",
                            username, tokenTenantId, requestedTenantId);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"success\":false,\"error\":{\"message\":\"Acceso denegado: No tiene permisos para acceder a otro salón.\",\"code\":\"FORBIDDEN\"}}");
                    return null;
                }

                // Super admin autorizado a acceder al tenant solicitado
                log.info("[TENANT-FILTER] SUPER_ADMIN '{}' accediendo al Tenant {}", username, requestedTenantId);
                return requestedTenantId;
            }

            // Forzar el tenantId del token en el contexto
            return tokenTenantId;
        }

        // Flujo público (sin usuario autenticado): resolver tenant por dominio, slug o fallback
        return resolvePublicTenantId(request);
    }

    private Long resolveRequestedTenantId(HttpServletRequest request) {
        String tenantIdHeader = request.getHeader("X-Tenant-ID");
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            tenantIdHeader = request.getHeader("X-Salon-ID");
        }
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
                Long candidateId = Long.parseLong(tenantIdHeader.trim());
                if (tenantRepository.existsById(candidateId)) {
                    return candidateId;
                }
            } catch (NumberFormatException ignored) {}
        }

        String salonSlug = request.getHeader("X-Tenant-Slug");
        if (salonSlug == null || salonSlug.isBlank()) {
            salonSlug = request.getHeader("X-Salon-Slug");
        }
        if (salonSlug != null && !salonSlug.isBlank()) {
            Optional<Tenant> tenantOpt = tenantRepository.findBySlugIgnoreCase(salonSlug.trim());
            if (tenantOpt.isPresent()) {
                return tenantOpt.get().getId();
            }
        }

        return null;
    }

    private Long resolvePublicTenantId(HttpServletRequest request) {
        Long requested = resolveRequestedTenantId(request);
        if (requested != null) {
            return requested;
        }

        // Dominio personalizado: X-Salon-Domain o X-Tenant-Domain
        String customDomain = request.getHeader("X-Salon-Domain");
        if (customDomain == null || customDomain.isBlank()) {
            customDomain = request.getHeader("X-Tenant-Domain");
        }
        if (customDomain != null && !customDomain.isBlank()) {
            Optional<Tenant> tenantOpt = tenantRepository.findByCustomDomainIgnoreCase(cleanDomain(customDomain));
            if (tenantOpt.isPresent()) {
                return tenantOpt.get().getId();
            }
        }

        // Cabecera Host o X-Forwarded-Host
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host != null && !host.isBlank()) {
            String cleanedHost = cleanDomain(host);
            Optional<Tenant> tenantOpt = tenantRepository.findByCustomDomainIgnoreCase(cleanedHost);
            if (tenantOpt.isPresent()) {
                return tenantOpt.get().getId();
            }

            String subSlug = extractSubdomainSlug(cleanedHost);
            if (subSlug != null) {
                Optional<Tenant> subTenantOpt = tenantRepository.findBySlugIgnoreCase(subSlug);
                if (subTenantOpt.isPresent()) {
                    return subTenantOpt.get().getId();
                }
            }
        }

        // Parámetro de URL: ?tenant=slug o ?salon=slug
        String slugParam = queryParam(request, "tenant");
        if (slugParam == null || slugParam.isBlank()) {
            slugParam = queryParam(request, "salon");
        }
        if (slugParam != null && !slugParam.isBlank()) {
            Optional<Tenant> tenantOpt = tenantRepository.findBySlugIgnoreCase(slugParam.trim());
            if (tenantOpt.isPresent()) {
                return tenantOpt.get().getId();
            }
        }

        // Fallback por defecto al salón principal (ID=1)
        return TenantContext.DEFAULT_TENANT_ID;
    }

    /**
     * Lee solo la query string. request.getParameter() obligaría a parsear el cuerpo
     * (form/multipart) en este filtro, y un upload enorme reventaría antes de llegar al controller.
     */
    private String queryParam(HttpServletRequest request, String name) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) return null;
        String value = UriComponentsBuilder.newInstance().query(query).build().getQueryParams().getFirst(name);
        return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8);
    }

    private String extractSubdomainSlug(String host) {
        if (host == null || host.isBlank()) return null;
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            String sub = parts[0].toLowerCase();
            if (!sub.equals("www") && !sub.equals("app") && !sub.equals("admin") && !sub.equals("api") && !sub.equals("localhost")) {
                return sub;
            }
        }
        return null;
    }

    private String cleanDomain(String hostHeader) {
        if (hostHeader == null) return "";
        String host = hostHeader.trim().toLowerCase();
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) {
            host = host.substring(0, colonIdx);
        }
        return host;
    }
}
