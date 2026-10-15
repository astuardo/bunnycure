package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.service.PwaRedirectService;
import cl.bunnycure.web.dto.LoyaltyCardPublicDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Controlador Web MVC (Thymeleaf) para la renderización dinámica de SEO y metadatos PWA
 * en rutas públicas de tarjetas de fidelización (/mi-tarjeta, /tarjeta, /pass).
 *
 * Entrega el HTML inicial pre-renderizado con OpenGraph (Facebook/WhatsApp),
 * Apple Mobile Web App tags, y la etiqueta rel="manifest" apuntando a Spring Boot,
 * eliminando la necesidad de Edge Functions o Serverless Functions propietarias en Vercel.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PwaCardWebController {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final PwaRedirectService pwaRedirectService;

    @GetMapping({"/mi-tarjeta/{publicId}", "/tarjeta/{publicId}", "/pass/{publicId}"})
    public String renderCardPage(@PathVariable String publicId, Model model, HttpServletRequest request) {
        String cleanPublicId = publicId != null ? publicId.trim() : "";
        log.info("[PWA CARD SSR] Generando vista HTML y metadatos SEO para publicId: {}", cleanPublicId);

        String firstName = "Club";
        String customerName = "Cliente";
        String salonName = "BunnyCure";
        String primaryColor = "#d48b70";
        String logoUrl = null;
        String googleWalletUrl = null;
        int stamps = 0;
        int maxStamps = 10;
        String rewardStatusMessage = "Acumula sellos en cada visita para canjear beneficios.";

        if (!cleanPublicId.isBlank()) {
            try {
                customerRepository.findTenantIdByPublicId(cleanPublicId)
                        .ifPresent(TenantContext::setCurrentTenantId);

                LoyaltyCardPublicDto card = customerService.getPublicLoyaltyCard(cleanPublicId);
                if (card != null) {
                    if (card.getFirstName() != null && !card.getFirstName().isBlank()) {
                        firstName = card.getFirstName().trim();
                    }
                    if (card.getCustomerName() != null && !card.getCustomerName().isBlank()) {
                        customerName = card.getCustomerName().trim();
                    } else {
                        customerName = firstName;
                    }
                    if (card.getSalonName() != null && !card.getSalonName().isBlank()) {
                        salonName = card.getSalonName().trim();
                    }
                    if (card.getPrimaryColor() != null && !card.getPrimaryColor().isBlank()) {
                        primaryColor = card.getPrimaryColor().trim();
                    }
                    if (card.getLogoUrl() != null && !card.getLogoUrl().isBlank()) {
                        logoUrl = card.getLogoUrl().trim();
                    }
                    if (card.getGoogleWalletUrl() != null && !card.getGoogleWalletUrl().isBlank()) {
                        googleWalletUrl = card.getGoogleWalletUrl().trim();
                    }
                    if (card.getLoyaltyStamps() != null) {
                        stamps = card.getLoyaltyStamps();
                    }
                    if (card.getMaxStamps() != null && card.getMaxStamps() > 0) {
                        maxStamps = card.getMaxStamps();
                    }
                    if (card.getRewardStatusMessage() != null && !card.getRewardStatusMessage().isBlank()) {
                        rewardStatusMessage = card.getRewardStatusMessage().trim();
                    }
                }
            } catch (Exception e) {
                log.warn("[PWA CARD SSR] Error cargando datos de tarjeta para publicId '{}': {}", cleanPublicId, e.getMessage());
            }
        }

        String frontendBase = pwaRedirectService != null ? pwaRedirectService.getFrontendBaseUrl() : "https://app.bunnycure.cl";
        String cardTitle = salonName + " Pass - " + firstName;
        String ogDescription = "Tarjeta de fidelización digital " + salonName + " Club de " + firstName + ". Consulta tus sellos acumulados y recompensas exclusivas.";
        String canonicalUrl = frontendBase + "/mi-tarjeta/" + cleanPublicId;
        String ogImage = (logoUrl != null && !logoUrl.isBlank()) ? logoUrl : (frontendBase + "/icon-512.png");
        String appleTouchIcon = (logoUrl != null && !logoUrl.isBlank()) ? logoUrl : "/icon-192.png";
        String manifestUrl = "/api/public/loyalty-card/" + cleanPublicId + "/manifest.json";
        String frontendSpaUrl = frontendBase + "/mi-tarjeta/" + cleanPublicId;

        model.addAttribute("publicId", cleanPublicId);
        model.addAttribute("firstName", firstName);
        model.addAttribute("customerName", customerName);
        model.addAttribute("salonName", salonName);
        model.addAttribute("cardTitle", cardTitle);
        model.addAttribute("ogTitle", cardTitle);
        model.addAttribute("ogDescription", ogDescription);
        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("ogImage", ogImage);
        model.addAttribute("appleTouchIcon", appleTouchIcon);
        model.addAttribute("manifestUrl", manifestUrl);
        model.addAttribute("themeColor", primaryColor);
        model.addAttribute("stamps", stamps);
        model.addAttribute("maxStamps", maxStamps);
        model.addAttribute("rewardStatusMessage", rewardStatusMessage);
        model.addAttribute("frontendSpaUrl", frontendSpaUrl);
        model.addAttribute("googleWalletUrl", googleWalletUrl);

        return "pwa/card-page";
    }
}
