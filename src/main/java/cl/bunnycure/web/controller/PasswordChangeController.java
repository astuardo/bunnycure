package cl.bunnycure.web.controller;

import cl.bunnycure.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class PasswordChangeController {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeController.class);

    private final UserService userService;

    @Value("${bunnycure.admin.username:admin}")
    private String adminUsername;

    @GetMapping("/change-password")
    public String changePasswordPage(@RequestParam(required = false) Boolean required,
                                     HttpSession session,
                                     Authentication authentication,
                                     Model model) {
        
        if (authentication == null) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        boolean mustChange = Boolean.TRUE.equals(session.getAttribute("mustChangePassword"));
        
        model.addAttribute("username", username);
        model.addAttribute("required", required != null && required);
        model.addAttribute("mustChange", mustChange);
        
        if (mustChange) {
            log.info("[SECURITY] Usuario '{}' accediendo a cambio de contraseña obligatorio", username);
        }
        
        return "admin/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                Authentication authentication,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        
        if (authentication == null) {
            return "redirect:/login";
        }

        String username = authentication.getName();

        try {
            // Validaciones
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Las contraseñas nuevas no coinciden");
                return "redirect:/admin/change-password?required=true";
            }

            if (newPassword.length() < 8) {
                redirectAttributes.addFlashAttribute("error", "La contraseña debe tener al menos 8 caracteres");
                return "redirect:/admin/change-password?required=true";
            }

            if (newPassword.equals("changeme")) {
                redirectAttributes.addFlashAttribute("error", "No puedes usar 'changeme' como contraseña");
                return "redirect:/admin/change-password?required=true";
            }

            if (newPassword.equals(currentPassword)) {
                redirectAttributes.addFlashAttribute("error", "La nueva contraseña debe ser diferente a la actual");
                return "redirect:/admin/change-password?required=true";
            }

            // Cambiar contraseña
            userService.changePassword(username, currentPassword, newPassword);

            // Limpiar flag de sesión (guardar antes de remover)
            boolean wasRequired = Boolean.TRUE.equals(session.getAttribute("mustChangePassword"));
            session.removeAttribute("mustChangePassword");
            session.removeAttribute("changePasswordReason");

            log.info("[SECURITY] Contraseña cambiada exitosamente para usuario: {}", username);
            
            redirectAttributes.addFlashAttribute("success", "Contraseña actualizada exitosamente");
            
            // Si era obligatorio, ir al dashboard. Si no, volver a settings
            return wasRequired ? "redirect:/dashboard" : "redirect:/admin/settings";

        } catch (RuntimeException e) {
            log.error("[SECURITY] Error al cambiar contraseña para {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/change-password?required=true";
        }
    }
}
