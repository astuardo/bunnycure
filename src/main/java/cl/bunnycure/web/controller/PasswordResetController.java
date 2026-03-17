package cl.bunnycure.web.controller;

import cl.bunnycure.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam String email,
                               RedirectAttributes flash) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

        passwordResetService.requestReset(email, baseUrl);
        flash.addFlashAttribute("successMsg", "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token,
                                    RedirectAttributes flash,
                                    Model model) {
        if (!passwordResetService.isTokenValid(token)) {
            flash.addFlashAttribute("errorMsg", "El enlace de recuperación es inválido o expiró.");
            return "redirect:/forgot-password";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes flash) {
        if (!newPassword.equals(confirmPassword)) {
            flash.addFlashAttribute("errorMsg", "Las contraseñas no coinciden.");
            return "redirect:/reset-password?token=" + token;
        }

        if (newPassword.length() < 8) {
            flash.addFlashAttribute("errorMsg", "La contraseña debe tener al menos 8 caracteres.");
            return "redirect:/reset-password?token=" + token;
        }

        try {
            passwordResetService.resetPassword(token, newPassword);
            flash.addFlashAttribute("successMsg", "Contraseña actualizada. Ya puedes iniciar sesión.");
            return "redirect:/login";
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("errorMsg", ex.getMessage());
            return "redirect:/forgot-password";
        }
    }
}
