package cl.bunnycure.config;

import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filtro HTTP que intercepta las peticiones y resuelve el Tenant/Salón
 * a partir de la cabecera Host, X-Salon-Domain, X-Salon-Slug o parámetros.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            Long resolvedTenantId = resolveTenantId(request);
            TenantContext.setCurrentTenantId(resolvedTenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveTenantId(HttpServletRequest request) {
        // 1. Cabecera numérica explícita: X-Tenant-ID o X-Salon-ID
        String tenantIdHeader = request.getHeader("X-Tenant-ID");
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            tenantIdHeader = request.getHeader("X-Salon-ID");
        }
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
                return Long.parseLong(tenantIdHeader.trim());
            } catch (NumberFormatException ignored) {
                log.debug("[TENANT-FILTER] Cabecera tenant ID inválida: {}", tenantIdHeader);
            }
        }

        // 2. Cabecera explícita de Slug: X-Tenant-Slug o X-Salon-Slug
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

        // 3. Cabecera de dominio personalizado: X-Salon-Domain o X-Tenant-Domain
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

        // 4. Cabecera Host o X-Forwarded-Host (dominio completo o subdominio slug)
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }

        if (host != null && !host.isBlank()) {
            String cleanedHost = cleanDomain(host);
            // 4a. Buscar por dominio personalizado completo (ej: saloncamila.cl)
            Optional<Tenant> tenantOpt = tenantRepository.findByCustomDomainIgnoreCase(cleanedHost);
            if (tenantOpt.isPresent()) {
                return tenantOpt.get().getId();
            }

            // 4b. Buscar por subdominio (ej: camila.bunnycure.cl -> slug 'camila')
            String subSlug = extractSubdomainSlug(cleanedHost);
            if (subSlug != null) {
                Optional<Tenant> subTenantOpt = tenantRepository.findBySlugIgnoreCase(subSlug);
                if (subTenantOpt.isPresent()) {
                    return subTenantOpt.get().getId();
                }
            }
        }

        // 5. Parámetro de URL: ?tenant=slug o ?salon=slug
        String slugParam = request.getParameter("tenant");
        if (slugParam == null || slugParam.isBlank()) {
            slugParam = request.getParameter("salon");
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
        // Remover puerto si viene (ej: localhost:8080 o app.bunnycure.cl:443)
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) {
            host = host.substring(0, colonIdx);
        }
        return host;
    }
}
