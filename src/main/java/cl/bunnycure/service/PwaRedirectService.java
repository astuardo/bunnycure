package cl.bunnycure.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Servicio encargado de calcular las rutas de redirección HTTP 301 hacia la PWA
 * (React/Vite) para las vistas legadas del monolito Thymeleaf (Fase 2).
 */
@Slf4j
@Service
public class PwaRedirectService {

    @Value("${app.frontend.base-url:https://bunnycure-frontend.vercel.app}")
    private String frontendBaseUrl;

    @Value("${app.monolith.deprecation.redirect-to-pwa:true}")
    private boolean redirectionEnabled;

    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/api/",
            "/w/",
            "/css/",
            "/js/",
            "/images/",
            "/assets/",
            "/webjars/",
            "/swagger-ui/",
            "/v3/api-docs",
            "/.well-known/",
            "/h2-console"
    );

    private static final Set<String> EXCLUDED_EXACT_PATHS = Set.of(
            "/favicon.ico",
            "/error",
            "/swagger-ui.html"
    );

    public boolean isRedirectionEnabled() {
        return redirectionEnabled;
    }

    public String getFrontendBaseUrl() {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            return "https://bunnycure-frontend.vercel.app";
        }
        String clean = frontendBaseUrl.trim();
        if (clean.endsWith("/")) {
            return clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    /**
     * Determina si la ruta solicitada corresponde a una vista legada del monolito que debe ser
     * redirigida a la PWA.
     */
    public boolean isLegacyMonolithPath(String path) {
        if (path == null) {
            return false;
        }

        String normalized = normalizePath(path);

        // Rutas excluidas (APIs, recursos estáticos, webhooks, console H2, wallet passes)
        if (EXCLUDED_EXACT_PATHS.contains(normalized)) {
            return false;
        }

        for (String prefix : EXCLUDED_PREFIXES) {
            if (normalized.startsWith(prefix) || normalized.equals(prefix.substring(0, prefix.length() - 1))) {
                return false;
            }
        }

        // Rutas del monolito que son redirigidas a la PWA
        return normalized.equals("/")
                || normalized.equals("/reservar") || normalized.startsWith("/reservar/")
                || normalized.equals("/login") || normalized.startsWith("/login/")
                || normalized.equals("/dashboard") || normalized.startsWith("/dashboard/")
                || normalized.equals("/appointments") || normalized.startsWith("/appointments/")
                || normalized.equals("/customers") || normalized.startsWith("/customers/")
                || normalized.equals("/admin") || normalized.startsWith("/admin/")
                || normalized.equals("/forgot-password") || normalized.startsWith("/forgot-password/")
                || normalized.equals("/reset-password") || normalized.startsWith("/reset-password/");
    }

    /**
     * Resuelve la URL de destino completa en la PWA a partir de la petición HTTP recibida.
     */
    public String resolvePwaRedirectUrl(HttpServletRequest request) {
        String path = request.getRequestURI();
        String queryString = request.getQueryString();

        if ((queryString == null || queryString.isBlank())
                && request.getParameterMap() != null
                && !request.getParameterMap().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            request.getParameterMap().forEach((key, values) -> {
                for (String val : values) {
                    if (sb.length() > 0) {
                        sb.append("&");
                    }
                    sb.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8))
                            .append("=")
                            .append(java.net.URLEncoder.encode(val, java.nio.charset.StandardCharsets.UTF_8));
                }
            });
            queryString = sb.toString();
        }

        String host = request.getHeader("Host");

        return resolvePwaRedirectUrl(path, queryString, host);
    }

    /**
     * Resuelve la URL de destino completa en la PWA para una ruta, query string y host dados.
     */
    public String resolvePwaRedirectUrl(String path, String queryString, String host) {
        String baseUrl = getFrontendBaseUrl();
        String normalizedPath = normalizePath(path);

        String targetPath;

        if ("/".equals(normalizedPath)) {
            if (host != null && host.contains("admin.bunnycure.cl")) {
                targetPath = "/dashboard";
            } else if (host != null && host.contains("reservar.bunnycure.cl")) {
                targetPath = "/reservar";
            } else {
                targetPath = "/";
            }
        } else if (normalizedPath.startsWith("/reservar")) {
            targetPath = "/reservar";
        } else if (normalizedPath.startsWith("/login")) {
            targetPath = "/login";
        } else if (normalizedPath.startsWith("/dashboard")) {
            targetPath = "/dashboard";
        } else if (normalizedPath.startsWith("/appointments")) {
            targetPath = "/appointments";
        } else if (normalizedPath.startsWith("/customers")) {
            targetPath = "/customers";
        } else if (normalizedPath.startsWith("/admin/services")) {
            targetPath = "/admin/services";
        } else if (normalizedPath.startsWith("/admin/users")) {
            targetPath = "/admin/users";
        } else if (normalizedPath.startsWith("/admin/appointments")) {
            targetPath = "/admin/appointments";
        } else if (normalizedPath.startsWith("/admin/reminders")) {
            targetPath = "/admin/reminders";
        } else if (normalizedPath.startsWith("/admin/settings")) {
            targetPath = "/admin/settings";
        } else if (normalizedPath.startsWith("/admin/booking-requests")) {
            targetPath = "/admin/booking-requests";
        } else if (normalizedPath.startsWith("/admin/change-password")) {
            targetPath = "/admin/change-password";
        } else if (normalizedPath.startsWith("/forgot-password")) {
            targetPath = "/forgot-password";
        } else if (normalizedPath.startsWith("/reset-password")) {
            targetPath = "/reset-password";
        } else {
            targetPath = normalizedPath;
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (!targetPath.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(targetPath);

        if (queryString != null && !queryString.isBlank()) {
            urlBuilder.append("?").append(queryString.trim());
        }

        String finalUrl = urlBuilder.toString();
        log.debug("[PWA REDIRECT] Path '{}' con host '{}' mapeado a '{}'", path, host, finalUrl);
        return finalUrl;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
