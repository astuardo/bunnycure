package cl.bunnycure.web.controller;

import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.BookingRequestService;
import cl.bunnycure.service.PwaRedirectService;
import cl.bunnycure.service.ServiceCatalogService;
import cl.bunnycure.web.dto.BookingRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @deprecated En Fase 2, la vista pública de reservas se deprecia a favor de la PWA React/Vite.
 */
@Deprecated(since = "Phase 2 - PWA Migration", forRemoval = true)
@Controller
@RequiredArgsConstructor
public class BookingController {

    private final ServiceCatalogService serviceCatalogService;
    private final AppSettingsService    appSettingsService;
    private final BookingRequestService bookingRequestService;
    private final PwaRedirectService    pwaRedirectService;

    // ── GET /reservar ────────────────────────────────────────────────────────
    @GetMapping({"/reservar", "/reservar/"})
    public Object index(Model model, HttpServletRequest request) {
        if (pwaRedirectService != null && pwaRedirectService.isRedirectionEnabled()) {
            String redirectUrl = pwaRedirectService.resolvePwaRedirectUrl(request);
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, redirectUrl)
                    .header("X-Deprecation-Notice", "Monolith view is deprecated. Redirected to BunnyCure PWA.")
                    .build();
        }
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
        
        // Configuración de campos dinámicos (Fase 3)
        model.addAttribute("fieldEmailMode", appSettingsService.getFieldEmailMode());
        model.addAttribute("fieldGenderMode", appSettingsService.getFieldGenderMode());
        model.addAttribute("fieldBirthDateMode", appSettingsService.getFieldBirthDateMode());
        model.addAttribute("fieldEmergencyPhoneMode", appSettingsService.getFieldEmergencyPhoneMode());
        model.addAttribute("fieldHealthNotesMode", appSettingsService.getFieldHealthNotesMode());
        model.addAttribute("fieldGeneralNotesMode", appSettingsService.getFieldGeneralNotesMode());
        
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