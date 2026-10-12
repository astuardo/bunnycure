package cl.bunnycure.web.controller;

import cl.bunnycure.service.CustomerService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.LoyaltyCardPublicDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST público para consultar la tarjeta de fidelización digital (PWA Pass).
 * Permite a las clientas ver sus sellos, recompensa y código QR en tiempo real
 * desde cualquier dispositivo (iPhone/Safari, Android/Chrome) sin requerir autenticación.
 */
@Slf4j
@Tag(name = "Public Loyalty Card", description = "API pública para visualización de tarjeta digital PWA estilo Wallet")
@RestController
@RequestMapping("/api/public/loyalty-card")
@RequiredArgsConstructor
public class PublicLoyaltyCardApiController {

    private final CustomerService customerService;

    @Operation(summary = "Obtener datos públicos de la tarjeta de fidelización por publicId")
    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<LoyaltyCardPublicDto>> getLoyaltyCard(@PathVariable String publicId) {
        log.info("[PUBLIC-LOYALTY] Consultando tarjeta digital para publicId: {}", publicId);
        LoyaltyCardPublicDto card = customerService.getPublicLoyaltyCard(publicId);
        return ResponseEntity.ok(ApiResponse.success(card));
    }
}
