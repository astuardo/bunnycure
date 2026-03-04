package cl.bunnycure.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(HttpServletRequest request) {
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


