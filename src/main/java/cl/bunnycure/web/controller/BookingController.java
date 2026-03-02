package cl.bunnycure.web.controller;

import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/reservar")
@RequiredArgsConstructor
public class BookingController {

    private final AppSettingsService settingsService;
    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    public String index(Model model) {
        boolean enabled = settingsService.isBookingEnabled();
        model.addAttribute("bookingEnabled", enabled);

        if (enabled) {
            model.addAttribute("services", serviceCatalogService.findAllActive());
            model.addAttribute("whatsappNumber", settingsService.getWhatsappNumber());
            model.addAttribute("messageTemplate", settingsService.getBookingMessageTemplate());

            // Bloques habilitados
            Map<String, String> blocks = new LinkedHashMap<>();
            if (settingsService.isMorningEnabled())
                blocks.put("Mañana", settingsService.getMorningBlock());
            if (settingsService.isAfternoonEnabled())
                blocks.put("Tarde", settingsService.getAfternoonBlock());
            if (settingsService.isNightEnabled())
                blocks.put("Noche", settingsService.getNightBlock());

            model.addAttribute("blocks", blocks);
        }

        return "reservar/index";
    }
}