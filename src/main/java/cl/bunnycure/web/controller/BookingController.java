package cl.bunnycure.web.controller;

import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.BookingRequestService;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.BookingRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class BookingController {

    private final ServiceCatalogService serviceCatalogService;
    private final AppSettingsService    appSettingsService;
    private final BookingRequestService bookingRequestService;

    // ── GET /reservar ────────────────────────────────────────────────────────
    @GetMapping({"/reservar", "/reservar/"})
    public String index(Model model) {
        boolean bookingEnabled = Boolean.parseBoolean(
                appSettingsService.get("booking.enabled", "true"));

        model.addAttribute("bookingEnabled",  bookingEnabled);
        model.addAttribute("whatsappNumber", appSettingsService.getHumanWhatsappNumber());
        model.addAttribute("whatsappHumanNumber", appSettingsService.getHumanWhatsappNumber());
        model.addAttribute("whatsappHumanDisplayName", appSettingsService.getHumanWhatsappDisplayName());
        model.addAttribute("whatsappHandoffEnabled", appSettingsService.isWhatsappHandoffEnabled());
        model.addAttribute("whatsappHandoffClientMessage", appSettingsService.getWhatsappHandoffClientMessage());
        model.addAttribute("messageTemplate",
                appSettingsService.getBookingMessageTemplate());
        model.addAttribute("bookingRequest",  new BookingRequestDto());
        model.addAttribute("services",
                serviceCatalogService.findAll().stream()
                        .filter(s -> s.isActive()).toList());
        model.addAttribute("timeBlocks",      buildTimeBlocks());
        
        // submitted attribute comes from flash (redirect after form submission)
        if (!model.containsAttribute("submitted")) {
            model.addAttribute("submitted", false);
        }

        return "reservar/index";
    }

    // ── POST /reservar/submit ────────────────────────────────────────────────
    @PostMapping("/reservar/submit")
    public String submit(@Valid @ModelAttribute("bookingRequest") BookingRequestDto dto,
                         BindingResult result,
                         RedirectAttributes flash) {
        if (result.hasErrors()) {
            // Return to form with errors
            return "redirect:/reservar?error";
        }

        try {
            bookingRequestService.create(dto);
        } catch (Exception e) {
            flash.addFlashAttribute("errorMsg",
                    "Hubo un error al enviar tu solicitud. Por favor intenta de nuevo.");
            return "redirect:/reservar";
        }

        // Redirect to /reservar with success flag
        flash.addFlashAttribute("submitted", true);
        return "redirect:/reservar";
    }

    // ── Bloques horarios configurables ───────────────────────────────────────
    private Map<String, String> buildTimeBlocks() {
        Map<String, String> blocks = new LinkedHashMap<>();
        blocks.put("Mañana", appSettingsService.get("booking.block.morning", "09:00 – 13:00"));
        blocks.put("Tarde",  appSettingsService.get("booking.block.afternoon","14:00 – 18:00"));
        blocks.put("Noche",  appSettingsService.get("booking.block.night",    "18:00 – 21:00"));
        return blocks;
    }
}