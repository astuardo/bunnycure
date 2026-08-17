package cl.bunnycure.web.controller;

import cl.bunnycure.service.PwaRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @deprecated En Fase 2, la vista de login del monolito se deprecia a favor de la PWA React/Vite.
 */
@Deprecated(since = "Phase 2 - PWA Migration", forRemoval = true)
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final PwaRedirectService pwaRedirectService;

    @GetMapping("/login")
    public Object loginPage(HttpServletRequest request) {
        if (pwaRedirectService.isRedirectionEnabled()) {
            String redirectUrl = pwaRedirectService.resolvePwaRedirectUrl(request);
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .header("X-Deprecation-Notice", "Monolith view is deprecated. Redirected to BunnyCure PWA.")
                    .build();
        }
        return "login"; // → templates/login.html
    }

}

