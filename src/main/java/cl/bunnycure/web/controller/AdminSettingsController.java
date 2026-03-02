package cl.bunnycure.web.controller;

import cl.bunnycure.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AppSettingsService settingsService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("bookingEnabled",   settingsService.isBookingEnabled());
        model.addAttribute("whatsappNumber",   settingsService.getWhatsappNumber());
        model.addAttribute("msgTemplate",      settingsService.getBookingMessageTemplate());
        model.addAttribute("morningBlock",     settingsService.getMorningBlock());
        model.addAttribute("afternoonBlock",   settingsService.getAfternoonBlock());
        model.addAttribute("nightBlock",       settingsService.getNightBlock());
        model.addAttribute("morningEnabled",   settingsService.isMorningEnabled());
        model.addAttribute("afternoonEnabled", settingsService.isAfternoonEnabled());
        model.addAttribute("nightEnabled",     settingsService.isNightEnabled());
        return "admin/settings/index";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       RedirectAttributes ra) {
        settingsService.saveAll(Map.of(
                "booking.enabled",                params.getOrDefault("bookingEnabled", "false"),
                "whatsapp.number",                params.getOrDefault("whatsappNumber", "56964499995"),
                "booking.message.template",       params.getOrDefault("msgTemplate", ""),
                "booking.block.morning",          params.getOrDefault("morningBlock", "09:00 – 13:00"),
                "booking.block.afternoon",        params.getOrDefault("afternoonBlock", "15:00 – 18:00"),
                "booking.block.night",            params.getOrDefault("nightBlock", "19:00 – 22:00"),
                "booking.block.morning.enabled",  params.getOrDefault("morningEnabled", "false"),
                "booking.block.afternoon.enabled",params.getOrDefault("afternoonEnabled", "false"),
                "booking.block.night.enabled",    params.getOrDefault("nightEnabled", "false")
        ));
        ra.addFlashAttribute("successMsg", "Configuración guardada ✅");
        return "redirect:/admin/settings";
    }
}