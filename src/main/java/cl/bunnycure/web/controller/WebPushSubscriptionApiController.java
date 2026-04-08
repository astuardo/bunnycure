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

@RestController
@RequestMapping("/api/push-subscriptions")
@RequiredArgsConstructor
public class WebPushSubscriptionApiController {

    private final WebPushNotificationService webPushNotificationService;

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
}
