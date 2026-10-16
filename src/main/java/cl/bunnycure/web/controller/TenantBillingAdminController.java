package cl.bunnycure.web.controller;

import cl.bunnycure.service.TenantExecutionService;
import cl.bunnycure.service.billing.SubscriptionService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.ManualPaymentRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionPaymentDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionStatusDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Cobro manual desde la consola de plataforma (solo SUPER_ADMIN o ADMIN del salón raíz;
 * ver PlatformAccess y la regla de /api/admin/tenants/** en SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/billing")
@PreAuthorize("@platformAccess.isPlatformAdmin(authentication)")
@RequiredArgsConstructor
@Tag(name = "Admin Billing", description = "Gestión de suscripciones y pagos manuales de los salones")
public class TenantBillingAdminController {

    private final SubscriptionService subscriptionService;
    private final TenantExecutionService tenantExecutionService;

    @Value("${bunnycure.scheduler.timezone:America/Santiago}")
    private String timezone;

    @Operation(summary = "Estado de la suscripción de un salón")
    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> status(@PathVariable Long tenantId) {
        subscriptionService.findTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(
                tenantExecutionService.executeInTenantTransaction(tenantId, subscriptionService::currentStatus)));
    }

    @Operation(summary = "Historial de pagos de un salón")
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<List<SubscriptionPaymentDto>>> payments(@PathVariable Long tenantId) {
        subscriptionService.findTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.paymentsOf(tenantId)));
    }

    @Operation(summary = "Registrar un pago manual (transferencia) y extender el período")
    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<SubscriptionPaymentDto>> registerPayment(@PathVariable Long tenantId,
                                                                               @Valid @RequestBody ManualPaymentRequest request,
                                                                               Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.registerManualPayment(
                tenantId, request, authentication.getName(), LocalDate.now(ZoneId.of(timezone)))));
    }

    @Operation(summary = "Ajustar plan, ciclo, estado o fechas de la suscripción")
    @PatchMapping
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> update(@PathVariable Long tenantId,
                                                                     @RequestBody SubscriptionUpdateRequest request) {
        subscriptionService.updateSubscription(tenantId, request);
        return status(tenantId);
    }
}
