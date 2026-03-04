package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("activeMenu", "users");
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String newUser(Model model) {
        model.addAttribute("activeMenu", "users");
        return "admin/users/form";
    }

    @PostMapping
    public String create(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam String fullName,
                        @RequestParam(required = false) String email,
                        RedirectAttributes flash) {
        try {
            userService.createUser(username, password, fullName, email);
            flash.addFlashAttribute("successMsg", "Usuario creado exitosamente");
            return "redirect:/admin/users";
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/admin/users/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.findById(id));
        model.addAttribute("activeMenu", "users");
        return "admin/users/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                        @RequestParam String fullName,
                        @RequestParam(required = false) String email,
                        RedirectAttributes flash) {
        try {
            userService.updateUser(id, fullName, email);
            flash.addFlashAttribute("successMsg", "Usuario actualizado exitosamente");
            return "redirect:/admin/users";
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/admin/users/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/change-password")
    public String changePassword(@PathVariable Long id,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes flash) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new RuntimeException("Las contraseñas no coinciden");
            }
            userService.changePassword(id, newPassword);
            flash.addFlashAttribute("successMsg", "Contraseña actualizada exitosamente");
            return "redirect:/admin/users";
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/admin/users/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes flash) {
        try {
            userService.toggleEnabled(id);
            flash.addFlashAttribute("successMsg", "Estado del usuario actualizado");
            return "redirect:/admin/users";
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        try {
            userService.deleteUser(id);
            flash.addFlashAttribute("successMsg", "Usuario eliminado exitosamente");
            return "redirect:/admin/users";
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/admin/users";
        }
    }

    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        User user = userService.findByUsername(auth.getName());
        model.addAttribute("user", user);
        model.addAttribute("activeMenu", "profile");
        return "admin/users/profile";
    }

    @PostMapping("/profile/change-password")
    public String changeOwnPassword(Authentication auth,
                                   @RequestParam String currentPassword,
                                   @RequestParam String newPassword,
                                   @RequestParam String confirmPassword,
                                   RedirectAttributes flash) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new RuntimeException("Las contraseñas nuevas no coinciden");
            }
            userService.changePassword(auth.getName(), currentPassword, newPassword);
            flash.addFlashAttribute("successMsg", "Contraseña actualizada exitosamente");
            return "redirect:/admin/users/profile";
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
            return "redirect:/admin/users/profile";
        }
    }
}
