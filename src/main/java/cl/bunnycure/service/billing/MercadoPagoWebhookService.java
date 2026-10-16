package cl.bunnycure.service.billing;

import cl.bunnycure.domain.enums.PaymentProvider;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.SubscriptionPaymentRepository;
import cl.bunnycure.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Procesa las notificaciones de Mercado Pago ya verificadas (firma válida):
 * - subscription_preapproval: la suscripción quedó autorizada → se asocia al salón.
 * - subscription_authorized_payment: cobro recurrente aprobado → se registra el pago
 *   (idempotente por id de pago) y se extiende el período.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoWebhookService {

    private final MercadoPagoGateway gateway;
    private final SubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;
    private final SubscriptionPaymentRepository paymentRepository;

    @Value("${bunnycure.scheduler.timezone:America/Santiago}")
    private String timezone;

    /** Sin @Transactional: los pagos se guardan en la transacción propia de su salón (SubscriptionService). */
    public void handle(String type, String dataId) {
        if (type == null || dataId == null) {
            return;
        }
        switch (type) {
            case "subscription_preapproval", "preapproval" -> handlePreapproval(dataId);
            case "subscription_authorized_payment", "authorized_payment" -> handleAuthorizedPayment(dataId);
            default -> log.debug("[MERCADOPAGO] Notificación ignorada: type={}", type);
        }
    }

    private void handlePreapproval(String preapprovalId) {
        Map<String, Object> preapproval = gateway.fetchPreapproval(preapprovalId);
        Optional<MercadoPagoGateway.CheckoutReference> ref = MercadoPagoGateway.parseExternalReference(preapproval.get("external_reference"));
        if (ref.isEmpty()) {
            log.warn("[MERCADOPAGO] preapproval {} sin external_reference reconocible", preapprovalId);
            return;
        }
        String status = String.valueOf(preapproval.get("status"));
        tenantRepository.findById(ref.get().tenantId()).ifPresent(tenant -> {
            if ("authorized".equalsIgnoreCase(status)) {
                tenant.setPaymentProvider(PaymentProvider.MERCADO_PAGO);
                tenant.setExternalSubscriptionId(preapprovalId);
                tenantRepository.save(tenant);
                log.info("[MERCADOPAGO] Suscripción {} autorizada para salón {}", preapprovalId, tenant.getId());
            } else if ("cancelled".equalsIgnoreCase(status) && preapprovalId.equals(tenant.getExternalSubscriptionId())) {
                // Se mantiene el período ya pagado; al vencer seguirá el ciclo normal de mora.
                tenant.setExternalSubscriptionId(null);
                tenantRepository.save(tenant);
                log.info("[MERCADOPAGO] Suscripción {} cancelada para salón {}", preapprovalId, tenant.getId());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleAuthorizedPayment(String authorizedPaymentId) {
        Map<String, Object> authorized = gateway.fetchAuthorizedPayment(authorizedPaymentId);
        Map<String, Object> payment = authorized.get("payment") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String paymentStatus = String.valueOf(payment.getOrDefault("status", authorized.get("status")));
        if (!"approved".equalsIgnoreCase(paymentStatus)) {
            log.info("[MERCADOPAGO] Cobro {} no aprobado (estado {})", authorizedPaymentId, paymentStatus);
            return;
        }
        String externalPaymentId = String.valueOf(payment.getOrDefault("id", authorizedPaymentId));
        if (paymentRepository.existsGlobally(PaymentProvider.MERCADO_PAGO, externalPaymentId)) {
            log.info("[MERCADOPAGO] Pago {} ya registrado (notificación repetida)", externalPaymentId);
            return;
        }

        Object preapprovalId = authorized.get("preapproval_id");
        Object externalReference = authorized.get("external_reference");
        if (externalReference == null && preapprovalId != null) {
            externalReference = gateway.fetchPreapproval(preapprovalId.toString()).get("external_reference");
        }
        Optional<MercadoPagoGateway.CheckoutReference> ref = MercadoPagoGateway.parseExternalReference(externalReference);
        if (ref.isEmpty()) {
            log.warn("[MERCADOPAGO] Cobro {} sin external_reference reconocible", authorizedPaymentId);
            return;
        }
        Tenant tenant = subscriptionService.findTenant(ref.get().tenantId());
        long amount = authorized.get("transaction_amount") instanceof Number n
                ? Math.round(n.doubleValue())
                : ref.get().plan().priceFor(ref.get().cycle());

        subscriptionService.applyApprovedPayment(tenant, ref.get().plan(), ref.get().cycle(), amount,
                PaymentProvider.MERCADO_PAGO, externalPaymentId, "Mercado Pago " + preapprovalId,
                "mercadopago", LocalDate.now(ZoneId.of(timezone)));
        log.info("[MERCADOPAGO] Pago {} aplicado al salón {}", externalPaymentId, tenant.getId());
    }
}
