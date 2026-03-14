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
        model.addAttribute("whatsappHumanNumber", settingsService.getHumanWhatsappNumber());
        model.addAttribute("whatsappAdminAlertNumber", settingsService.getAdminAlertWhatsappNumber("56964499995"));
        model.addAttribute("whatsappHumanDisplayName", settingsService.getHumanWhatsappDisplayName());
        model.addAttribute("whatsappHandoffEnabled", settingsService.isWhatsappHandoffEnabled());
        model.addAttribute("whatsappHandoffClientMessage", settingsService.getWhatsappHandoffClientMessage());
        model.addAttribute("whatsappHandoffAdminPrefill", settingsService.getWhatsappHandoffAdminPrefill());
        model.addAttribute("msgTemplate",      settingsService.getBookingMessageTemplate());
        model.addAttribute("morningBlock",     settingsService.getMorningBlock());
        model.addAttribute("afternoonBlock",   settingsService.getAfternoonBlock());
        model.addAttribute("nightBlock",       settingsService.getNightBlock());
        model.addAttribute("morningEnabled",   settingsService.isMorningEnabled());
        model.addAttribute("afternoonEnabled", settingsService.isAfternoonEnabled());
        model.addAttribute("nightEnabled",     settingsService.isNightEnabled());
        model.addAttribute("reminderStrategy", settingsService.getReminderStrategy());
        model.addAttribute("reminderStrategyOptions", java.util.List.of(
                java.util.Map.entry("2hours",     "Solo 2 horas antes de la cita"),
                java.util.Map.entry("morning",    "Solo aviso mañana (08:00 del día de la cita)"),
                java.util.Map.entry("day_before", "Solo aviso día anterior (09:00)"),
                java.util.Map.entry("both",       "Mañana del día + 2 horas antes")
        ));
        return "admin/settings/index";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       RedirectAttributes ra) {
        String humanWhatsappNumber = params.getOrDefault("whatsappHumanNumber", params.getOrDefault("whatsappNumber", "56988873031"));
        String whatsappAdminAlertNumber = params.getOrDefault("whatsappAdminAlertNumber", "56964499995");
        settingsService.saveAll(Map.ofEntries(
                Map.entry("booking.enabled", params.getOrDefault("bookingEnabled", "false")),
                Map.entry("whatsapp.number", humanWhatsappNumber),
                Map.entry("whatsapp.human.number", humanWhatsappNumber),
                Map.entry("whatsapp.admin-alert.number", whatsappAdminAlertNumber),
                Map.entry("whatsapp.human.display-name", params.getOrDefault("whatsappHumanDisplayName", "Equipo BunnyCure")),
                Map.entry("whatsapp.handoff.enabled", params.getOrDefault("whatsappHandoffEnabled", "false")),
                Map.entry("whatsapp.handoff.client-message", params.getOrDefault("whatsappHandoffClientMessage", "")),
                Map.entry("whatsapp.handoff.admin-prefill", params.getOrDefault("whatsappHandoffAdminPrefill", "")),
                Map.entry("booking.message.template", params.getOrDefault("msgTemplate", "")),
                Map.entry("booking.block.morning", params.getOrDefault("morningBlock", "09:00 – 13:00")),
                Map.entry("booking.block.afternoon", params.getOrDefault("afternoonBlock", "15:00 – 18:00")),
                Map.entry("booking.block.night", params.getOrDefault("nightBlock", "19:00 – 22:00")),
                Map.entry("booking.block.morning.enabled", params.getOrDefault("morningEnabled", "false")),
                Map.entry("booking.block.afternoon.enabled", params.getOrDefault("afternoonEnabled", "false")),
                Map.entry("booking.block.night.enabled", params.getOrDefault("nightEnabled", "false")),
                Map.entry("reminder.strategy", params.getOrDefault("reminderStrategy", "2hours"))
        ));
        ra.addFlashAttribute("successMsg", "Configuración guardada ✅");
        return "redirect:/admin/settings";
    }
}