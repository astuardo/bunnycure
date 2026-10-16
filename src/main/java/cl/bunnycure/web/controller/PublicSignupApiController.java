package cl.bunnycure.web.controller;

import cl.bunnycure.service.billing.SignupService;
import cl.bunnycure.service.billing.SubscriptionService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.PlanDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SignupRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.SignupResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.SlugAvailabilityResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.VerifyEmailRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.VerifyEmailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Catálogo de planes y registro autoservicio de salones (público).
 * Con rate-limit por IP en PublicApiRateLimitingFilter para los POST de /api/public/signup.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public Signup", description = "Planes y registro autoservicio de salones")
public class PublicSignupApiController {

    private final SignupService signupService;
    private final SubscriptionService subscriptionService;

    @Operation(summary = "Planes comerciales con precios, límites y funcionalidades")
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanDto>>> plans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.listPlans()));
    }

    @Operation(summary = "Registrar un salón nuevo (queda pendiente de verificar el email)")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request,
                                                              HttpServletRequest httpRequest) {
        SignupResponse response = signupService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Confirmar el email y comenzar la prueba gratuita")
    @PostMapping("/signup/verify")
    public ResponseEntity<ApiResponse<VerifyEmailResponse>> verify(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(signupService.verify(request.token())));
    }

    @Operation(summary = "Reenviar el correo de verificación")
    @PostMapping("/signup/resend")
    public ResponseEntity<ApiResponse<String>> resend(@RequestBody Map<String, String> body) {
        signupService.resendVerification(body != null ? body.get("email") : null);
        return ResponseEntity.ok(ApiResponse.success(
                "Si hay un registro pendiente con ese email, te enviamos un nuevo enlace de verificación."));
    }

    @Operation(summary = "Comprobar si una dirección (slug) de salón está disponible")
    @GetMapping("/signup/slug-available")
    public ResponseEntity<ApiResponse<SlugAvailabilityResponse>> slugAvailable(@RequestParam String slug) {
        return ResponseEntity.ok(ApiResponse.success(new SlugAvailabilityResponse(slug, signupService.isSlugAvailable(slug))));
    }
}
