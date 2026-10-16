package cl.bunnycure.web.controller;

import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.UnauthorizedException;
import cl.bunnycure.service.WebPushNotificationService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.WebPushSubscriptionRequestDto;
import cl.bunnycure.web.dto.WebPushSubscriptionResponseDto;
import cl.bunnycure.web.dto.WebPushUnsubscribeRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Suscripciones Web Push del personal del salón.
 * Solo /public-key es público; el resto exige usuario autenticado (ver SecurityConfig)
 * y cada suscripción queda ligada a ese usuario y a su salón.
 */
@RestController
@RequestMapping("/api/push-subscriptions")
@RequiredArgsConstructor
public class WebPushSubscriptionApiController {

    private final WebPushNotificationService webPushNotificationService;
    private final UserRepository userRepository;

    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicKey() {
        String key = webPushNotificationService.getPublicKey();
        return ResponseEntity.ok(ApiResponse.success(Map.of("publicKey", key != null ? key : "")));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebPushSubscriptionResponseDto>> register(
            @Valid @RequestBody WebPushSubscriptionRequestDto request,
            Authentication authentication
    ) {
        Long userId = resolveCurrentUserId(authentication);
        WebPushSubscriptionResponseDto response = webPushNotificationService.saveSubscription(request, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<String>> unsubscribe(
            @Valid @RequestBody WebPushUnsubscribeRequestDto request,
            Authentication authentication
    ) {
        webPushNotificationService.deactivateSubscription(request.getEndpoint(), resolveCurrentUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success("Suscripción eliminada"));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTest(
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        String endpoint = body != null ? body.get("endpoint") : null;
        boolean sent = webPushNotificationService.sendTestNotification(endpoint, resolveCurrentUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(Map.of("success", sent)));
    }

    @GetMapping("/debug")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debug() {
        return ResponseEntity.ok(ApiResponse.success(webPushNotificationService.getDiagnostics()));
    }

    /** User tiene @TenantId: la búsqueda queda limitada al salón del token. */
    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Autenticación requerida");
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado en el salón actual"));
    }
}
