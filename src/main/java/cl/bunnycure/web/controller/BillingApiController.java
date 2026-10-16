package cl.bunnycure.web.controller;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.enums.SubscriptionPlan;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.service.billing.MercadoPagoGateway;
import cl.bunnycure.service.billing.SubscriptionService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.CheckoutRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.CheckoutResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionPaymentDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Suscripción del salón actual, para su administradora. Accesible aunque la suscripción
 * esté suspendida (ver PlanAccessWebConfig) para que pueda regularizar el pago.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Plan, estado de la suscripción y pagos del salón")
public class BillingApiController {

    private final SubscriptionService subscriptionService;
    private final MercadoPagoGateway mercadoPagoGateway;
    private final UserRepository userRepository;

    @Operation(summary = "Plan, estado, límites y funcionalidades del salón")
    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> subscription() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.currentStatus()));
    }

    @Operation(summary = "Historial de pagos de la suscripción")
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<List<SubscriptionPaymentDto>>> payments() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.paymentsOf(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Iniciar el pago en línea (Mercado Pago) del plan elegido")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(@Valid @RequestBody CheckoutRequest request,
                                                                  Authentication authentication) {
        if (!mercadoPagoGateway.isEnabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("El pago en línea aún no está disponible. Usa las instrucciones de pago manual.",
                            "ONLINE_PAYMENT_DISABLED"));
        }
        Tenant tenant = subscriptionService.findTenant(TenantContext.getCurrentTenantId());
        String payerEmail = userRepository.findByUsername(authentication.getName())
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .orElse(tenant.getEmail());
        String url = mercadoPagoGateway.createSubscriptionCheckout(tenant,
                SubscriptionPlan.fromString(request.plan()), request.billingCycle(), payerEmail);
        return ResponseEntity.ok(ApiResponse.success(new CheckoutResponse(url)));
    }
}
