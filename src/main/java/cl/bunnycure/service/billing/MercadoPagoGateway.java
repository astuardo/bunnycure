package cl.bunnycure.service.billing;

import cl.bunnycure.domain.enums.BillingCycle;
import cl.bunnycure.domain.enums.SubscriptionPlan;
import cl.bunnycure.domain.model.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Integración con Mercado Pago Suscripciones (API "preapproval"). DESACTIVADA por defecto:
 * se habilita con MERCADOPAGO_ENABLED=true más MERCADOPAGO_ACCESS_TOKEN y MERCADOPAGO_WEBHOOK_SECRET.
 *
 * Antes de activarla en producción, validar en el sandbox de Mercado Pago los nombres de campos
 * y estados usados aquí contra la documentación vigente (preapproval, authorized_payments y
 * firma x-signature de webhooks).
 *
 * external_reference transporta salón, plan y ciclo ("tenant:{id}:plan:{PLAN}:cycle:{CYCLE}")
 * para que el webhook sea autosuficiente.
 */
@Slf4j
@Service
public class MercadoPagoGateway {

    static final String API_BASE = "https://api.mercadopago.com";

    private final RestTemplate restTemplate;

    @Value("${bunnycure.billing.mercadopago.enabled:false}")
    private boolean enabled;

    @Value("${bunnycure.billing.mercadopago.access-token:}")
    private String accessToken;

    @Value("${bunnycure.billing.mercadopago.webhook-secret:}")
    private String webhookSecret;

    @Value("${bunnycure.billing.mercadopago.back-url:https://app.bunnycure.cl/suscripcion}")
    private String backUrl;

    @Value("${bunnycure.product.name:BunnyCure}")
    private String productName;

    public MercadoPagoGateway(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** Activa por configuración y con credenciales presentes. */
    public boolean isEnabled() {
        return enabled && hasText(accessToken) && hasText(webhookSecret);
    }

    /** Crea la suscripción recurrente y devuelve la URL de pago (init_point) para la administradora. */
    @SuppressWarnings("unchecked")
    public String createSubscriptionCheckout(Tenant tenant, SubscriptionPlan plan, BillingCycle cycle, String payerEmail) {
        requireEnabled();
        Map<String, Object> autoRecurring = new LinkedHashMap<>();
        autoRecurring.put("frequency", cycle.getMonths());
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", plan.priceFor(cycle));
        autoRecurring.put("currency_id", "CLP");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", productName + " " + plan.getDisplayName() + (cycle == BillingCycle.ANNUAL ? " (anual)" : " (mensual)"));
        body.put("external_reference", externalReference(tenant.getId(), plan, cycle));
        body.put("payer_email", payerEmail);
        body.put("auto_recurring", autoRecurring);
        body.put("back_url", backUrl);
        body.put("status", "pending");

        Map<String, Object> response = restTemplate.exchange(API_BASE + "/preapproval", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), Map.class).getBody();
        Object initPoint = response != null ? response.get("init_point") : null;
        if (initPoint == null) {
            throw new IllegalStateException("Mercado Pago no devolvió init_point");
        }
        log.info("[MERCADOPAGO] Checkout creado para salón {} (plan {} {}) preapproval={}",
                tenant.getId(), plan, cycle, response.get("id"));
        return initPoint.toString();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchPreapproval(String preapprovalId) {
        requireEnabled();
        return restTemplate.exchange(API_BASE + "/preapproval/" + preapprovalId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchAuthorizedPayment(String authorizedPaymentId) {
        requireEnabled();
        return restTemplate.exchange(API_BASE + "/authorized_payments/" + authorizedPaymentId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()), Map.class).getBody();
    }

    /**
     * Verifica la cabecera x-signature ("ts=...,v1=...") de un webhook: HMAC-SHA256 con la clave
     * secreta sobre "id:{data.id};request-id:{x-request-id};ts:{ts};" (las partes ausentes se omiten).
     */
    public boolean isValidSignature(String xSignature, String xRequestId, String dataId) {
        if (!hasText(webhookSecret) || !hasText(xSignature)) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("ts")) ts = kv[1].trim();
            if (kv.length == 2 && kv[0].trim().equals("v1")) v1 = kv[1].trim();
        }
        if (ts == null || v1 == null) {
            return false;
        }
        StringBuilder manifest = new StringBuilder();
        if (hasText(dataId)) manifest.append("id:").append(dataId.toLowerCase()).append(';');
        if (hasText(xRequestId)) manifest.append("request-id:").append(xRequestId).append(';');
        manifest.append("ts:").append(ts).append(';');
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[MERCADOPAGO] No se pudo verificar la firma: {}", e.getMessage());
            return false;
        }
    }

    // ── external_reference ────────────────────────────────────────────────

    public record CheckoutReference(Long tenantId, SubscriptionPlan plan, BillingCycle cycle) {
    }

    static String externalReference(Long tenantId, SubscriptionPlan plan, BillingCycle cycle) {
        return "tenant:" + tenantId + ":plan:" + plan.name() + ":cycle:" + cycle.name();
    }

    static Optional<CheckoutReference> parseExternalReference(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] p = value.toString().split(":");
        if (p.length != 6 || !p[0].equals("tenant") || !p[2].equals("plan") || !p[4].equals("cycle")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CheckoutReference(Long.parseLong(p[1]), SubscriptionPlan.fromString(p[3]), BillingCycle.valueOf(p[5])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Mercado Pago está desactivado o sin credenciales");
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
