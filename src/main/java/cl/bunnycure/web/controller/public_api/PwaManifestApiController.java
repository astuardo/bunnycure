package cl.bunnycure.web.controller.public_api;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.service.CustomerService;
import cl.bunnycure.web.dto.LoyaltyCardPublicDto;
import cl.bunnycure.web.dto.PwaManifestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Controlador público para la entrega de Web App Manifests (W3C PWA).
 * Genera manifiestos dinámicos y personalizados con nombre del cliente, marca del salón y colores,
 * eliminando la dependencia de Serverless Functions externas (Vercel).
 */
@Slf4j
@Tag(name = "PWA Manifest", description = "Generación dinámica de W3C Web Manifests personalizados para PWA")
@RestController
@RequiredArgsConstructor
public class PwaManifestApiController {

    private static final String MANIFEST_MEDIA_TYPE = "application/manifest+json; charset=utf-8";

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    /**
     * Endpoint canónico para el manifiesto de la tarjeta de fidelización por publicId.
     */
    @Operation(summary = "Obtener manifiesto PWA dinámico de tarjeta de fidelización por publicId")
    @GetMapping(value = "/api/public/loyalty-card/{publicId}/manifest.json", produces = MANIFEST_MEDIA_TYPE)
    public ResponseEntity<PwaManifestDto> getCardManifestByPublicId(@PathVariable String publicId) {
        log.debug("[PWA MANIFEST] Solicitando manifiesto para publicId: {}", publicId);
        return buildManifestResponse(publicId, null);
    }

    /**
     * Endpoint de compatibilidad retroactiva para clientes o configuraciones legadas.
     * Soporta parámetros de consulta ?id=...&name=...
     */
    @Operation(summary = "Endpoint de compatibilidad para manifest-card.json")
    @GetMapping(value = "/manifest-card.json", produces = MANIFEST_MEDIA_TYPE)
    public ResponseEntity<PwaManifestDto> getLegacyCardManifest(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String name) {
        log.debug("[PWA MANIFEST LEGACY] Solicitando manifest-card.json id: {}, name: {}", id, name);
        return buildManifestResponse(id, name);
    }

    private ResponseEntity<PwaManifestDto> buildManifestResponse(String publicId, String fallbackName) {
        String firstName = null;
        String salonName = "BunnyCure";
        String primaryColor = "#d48b70";
        String cardPath = (publicId != null && !publicId.isBlank()) ? "/mi-tarjeta/" + publicId.trim() : "/";

        if (publicId != null && !publicId.isBlank()) {
            try {
                customerRepository.findTenantIdByPublicId(publicId.trim())
                        .ifPresent(TenantContext::setCurrentTenantId);

                LoyaltyCardPublicDto card = customerService.getPublicLoyaltyCard(publicId.trim());
                if (card != null) {
                    if (card.getFirstName() != null && !card.getFirstName().isBlank()) {
                        firstName = card.getFirstName().trim();
                    }
                    if (card.getSalonName() != null && !card.getSalonName().isBlank()) {
                        salonName = card.getSalonName().trim();
                    }
                    if (card.getPrimaryColor() != null && !card.getPrimaryColor().isBlank()) {
                        primaryColor = card.getPrimaryColor().trim();
                    }
                }
            } catch (Exception e) {
                log.warn("[PWA MANIFEST] No se pudo cargar tarjeta para publicId '{}': {}", publicId, e.getMessage());
            }
        }

        // Fallback para el nombre si no se obtuvo de la base de datos
        if (firstName == null || firstName.isBlank()) {
            if (fallbackName != null && !fallbackName.isBlank()) {
                try {
                    firstName = URLDecoder.decode(fallbackName.trim(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    firstName = fallbackName.trim();
                }
            } else {
                firstName = "Club";
            }
        }

        String displayName = salonName + " Pass - " + firstName;
        String description = "Tarjeta de fidelización digital " + salonName + " Club de " + firstName;
        String scope = (publicId != null && !publicId.isBlank()) ? "/mi-tarjeta/" + publicId.trim() : "/mi-tarjeta/";

        PwaManifestDto manifest = PwaManifestDto.builder()
                .id(cardPath)
                .name(displayName)
                .shortName(displayName)
                .description(description)
                .themeColor(primaryColor)
                .backgroundColor("#FDFCF8")
                .display("standalone")
                .scope(scope)
                .startUrl(cardPath)
                .orientation("portrait")
                .icons(List.of(
                        PwaManifestDto.ManifestIcon.builder()
                                .src("/icon-192.png")
                                .sizes("192x192")
                                .type("image/png")
                                .purpose("any")
                                .build(),
                        PwaManifestDto.ManifestIcon.builder()
                                .src("/icon-512.png")
                                .sizes("512x512")
                                .type("image/png")
                                .purpose("any")
                                .build(),
                        PwaManifestDto.ManifestIcon.builder()
                                .src("/icon-maskable-512.png")
                                .sizes("512x512")
                                .type("image/png")
                                .purpose("maskable")
                                .build()
                ))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MANIFEST_MEDIA_TYPE);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=0, must-revalidate");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

        return ResponseEntity.ok()
                .headers(headers)
                .body(manifest);
    }
}
