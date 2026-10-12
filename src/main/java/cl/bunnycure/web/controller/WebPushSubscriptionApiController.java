package cl.bunnycure.web.controller;

import cl.bunnycure.service.WebPushNotificationService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.WebPushSubscriptionRequestDto;
import cl.bunnycure.web.dto.WebPushSubscriptionResponseDto;
import cl.bunnycure.web.dto.WebPushUnsubscribeRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push-subscriptions")
@RequiredArgsConstructor
public class WebPushSubscriptionApiController {

    private final WebPushNotificationService webPushNotificationService;

    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicKey() {
        String key = webPushNotificationService.getPublicKey();
        return ResponseEntity.ok(ApiResponse.success(Map.of("publicKey", key != null ? key : "")));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebPushSubscriptionResponseDto>> register(
            @Valid @RequestBody WebPushSubscriptionRequestDto request
    ) {
        WebPushSubscriptionResponseDto response = webPushNotificationService.saveSubscription(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<String>> unsubscribe(
            @Valid @RequestBody WebPushUnsubscribeRequestDto request
    ) {
        webPushNotificationService.deactivateSubscription(request.getEndpoint());
        return ResponseEntity.ok(ApiResponse.success("Suscripción eliminada"));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTest(
            @RequestBody(required = false) Map<String, String> body
    ) {
        String endpoint = body != null ? body.get("endpoint") : null;
        boolean sent = webPushNotificationService.sendTestNotification(endpoint);
        return ResponseEntity.ok(ApiResponse.success(Map.of("success", sent)));
    }

    @GetMapping("/debug")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debug() {
        return ResponseEntity.ok(ApiResponse.success(webPushNotificationService.getDiagnostics()));
    }
}
