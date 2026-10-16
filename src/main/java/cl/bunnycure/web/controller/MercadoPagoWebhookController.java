package cl.bunnycure.web.controller;

import cl.bunnycure.service.billing.MercadoPagoGateway;
import cl.bunnycure.service.billing.MercadoPagoWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Notificaciones de Mercado Pago. Público (lo llama Mercado Pago), pero solo procesa
 * peticiones con firma x-signature válida. Responde 404 mientras la integración esté apagada.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final MercadoPagoGateway gateway;
    private final MercadoPagoWebhookService webhookService;

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> receive(@RequestParam Map<String, String> query,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestHeader(name = "x-signature", required = false) String signature,
                                        @RequestHeader(name = "x-request-id", required = false) String requestId) {
        if (!gateway.isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        String type = query.getOrDefault("type", query.get("topic"));
        String dataId = query.get("data.id");
        if (body != null) {
            if (type == null && body.get("type") != null) type = body.get("type").toString();
            if (dataId == null && body.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
                dataId = ((Map<String, Object>) data).get("id").toString();
            }
        }

        if (!gateway.isValidSignature(signature, requestId, dataId)) {
            log.warn("[MERCADOPAGO] Webhook rechazado: firma inválida (type={}, id={})", type, dataId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            webhookService.handle(type, dataId);
        } catch (Exception e) {
            // 500 para que Mercado Pago reintente la notificación más tarde
            log.error("[MERCADOPAGO] Error procesando webhook type={} id={}: {}", type, dataId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }
}
