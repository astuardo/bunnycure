package cl.bunnycure.web.controller;

import cl.bunnycure.service.PwaRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @deprecated En Fase 2, las vistas del monolito se deprecian a favor de la PWA React/Vite.
 * Las peticiones son redirigidas con HTTP 301 mediante {@link cl.bunnycure.config.PwaRedirectFilter}.
 */
@Deprecated(since = "Phase 2 - PWA Migration", forRemoval = true)
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PwaRedirectService pwaRedirectService;

    @GetMapping("/")
    public Object home(HttpServletRequest request) {
        if (pwaRedirectService.isRedirectionEnabled()) {
            String redirectUrl = pwaRedirectService.resolvePwaRedirectUrl(request);
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .header("X-Deprecation-Notice", "Monolith view is deprecated. Redirected to BunnyCure PWA.")
                    .build();
        }

        String host = request.getHeader("Host");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Si es el dominio admin.bunnycure.cl
        if (host != null && host.contains("admin.bunnycure.cl")) {
            // Si está autenticado y tiene rol ADMIN, ir al dashboard
            if (auth != null && auth.isAuthenticated() && 
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return "redirect:/dashboard";
            }
            // Si no está autenticado, ir al login
            return "redirect:/login";
        }
        
        // Para cualquier otro caso (incluyendo reservar.bunnycure.cl), ir a /reservar
        return "redirect:/reservar";
    }
}


