package cl.bunnycure.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import cl.bunnycure.service.WhatsAppWebhookService;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * Controller para recibir notificaciones de webhook de WhatsApp Cloud API
 * 
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/setup
 * 
 * Este endpoint debe ser:
 * 1. Público (accesible sin autenticación)
 * 2. HTTPS en producción
 * 3. Responder rápidamente (< 20 segundos)
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final int MAX_WEBHOOK_PAYLOAD_BYTES = 512 * 1024;

    private final WhatsAppWebhookService webhookService;
    private final Environment environment;
    private final ObjectReader webhookReader;

    public WhatsAppWebhookController(WhatsAppWebhookService webhookService,
                                     Environment environment,
                                     ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.environment = environment;
        this.webhookReader = buildSafeWebhookReader(objectMapper);
    }

    @Value("${whatsapp.webhook.verify-token:bunnycure_webhook_token_2024}")
    private String verifyToken;

    @Value("${whatsapp.webhook.app-secret:}")
    private String appSecret;

    /**
     * Endpoint para la verificación inicial del webhook (GET)
     * 
     * WhatsApp enviará una petición GET con los siguientes parámetros:
     * - hub.mode: "subscribe"
     * - hub.challenge: un número aleatorio
     * - hub.verify_token: el token que configuraste en Meta for Developers
     * 
     * Debes responder con el hub.challenge si el verify_token coincide
     * 
     * Ejemplo de URL que recibirás:
     * https://tu-dominio.com/api/webhooks/whatsapp?hub.mode=subscribe&hub.challenge=1234567890&hub.verify_token=tu_token
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            @RequestParam(name = "hub.verify_token", required = false) String token) {

        log.info("[WEBHOOK-VERIFY] 🔍 Verificación de webhook iniciada");
        log.info("[WEBHOOK-VERIFY] Mode: {}", mode);
        log.info("[WEBHOOK-VERIFY] Challenge: {}", challenge);
        log.info("[WEBHOOK-VERIFY] Token received: {}", token != null ? "***" : "null");
        log.info("[WEBHOOK-VERIFY] Token expected: {}", verifyToken != null ? "***" : "null");

        // Verificar que los parámetros no sean nulos
        if (mode == null || challenge == null || token == null) {
            log.error("[WEBHOOK-VERIFY] ❌ Parámetros faltantes");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Missing required parameters");
        }

        // Verificar que el mode sea "subscribe"
        if (!"subscribe".equals(mode)) {
            log.error("[WEBHOOK-VERIFY] ❌ Modo inválido: {}", mode);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid mode");
        }

        // Verificar que el token coincida
        if (!verifyToken.equals(token)) {
            log.error("[WEBHOOK-VERIFY] ❌ Token no coincide");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid verify token");
        }

        // Todo OK - responder con el challenge
        log.info("[WEBHOOK-VERIFY] ✅ Verificación exitosa - respondiendo con challenge");
        return ResponseEntity.ok(challenge);
    }

    /**
     * Endpoint para recibir notificaciones del webhook (POST)
     * 
     * WhatsApp enviará notificaciones en formato JSON con información sobre:
     * - Mensajes entrantes
     * - Estados de mensajes (enviado, entregado, leído)
     * - Cambios en el perfil del contacto
     * - Etc.
     * 
     * IMPORTANTE: Debes responder con status 200 lo antes posible (< 20 segundos)
     * El procesamiento pesado debe hacerse de forma asíncrona
     */
    @PostMapping
    public ResponseEntity<String> receiveNotification(
            @RequestBody byte[] rawPayload,
            HttpServletRequest request,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader) {
        try {
            log.info("[WEBHOOK] 📥 Notificación recibida de WhatsApp");

            if (isPayloadTooLarge(rawPayload)) {
                log.warn("[WEBHOOK] ❌ Payload rejected by size limit: {} bytes", rawPayload.length);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("Payload too large");
            }

            String resolvedSignatureHeader = resolveSignatureHeader(signatureHeader, request);

            if (!webhookService.isSignatureValid(rawPayload, resolvedSignatureHeader, appSecret)) {
                log.warn("[WEBHOOK] ❌ Invalid webhook signature");
                log.warn("[WEBHOOK] Proxy context contentType={}, contentLength={}, xForwardedFor={}, xForwardedProto={}, xForwardedHost={}",
                        request.getContentType(),
                        request.getContentLengthLong(),
                        request.getHeader("X-Forwarded-For"),
                        request.getHeader("X-Forwarded-Proto"),
                        request.getHeader("X-Forwarded-Host"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
            }

            WhatsAppWebhookDto webhook = webhookReader.readValue(rawPayload);
            log.debug("[WEBHOOK] Payload completo: {}", webhook);

            if (!hasMinimumWebhookStructure(webhook)) {
                log.warn("[WEBHOOK] ⚠️ Payload JSON válido pero estructura incompleta, se omite procesamiento");
                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            // Procesar la notificación de forma asíncrona (opcional pero recomendado)
            webhookService.processWebhookNotification(webhook);

            // Responder inmediatamente con 200 OK
            return ResponseEntity.ok("EVENT_RECEIVED");

        } catch (JsonProcessingException e) {
            log.warn("[WEBHOOK] ⚠️ JSON inválido en notificación: {}", e.getOriginalMessage());
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("[WEBHOOK] ❌ Error procesando notificación: {}", e.getMessage(), e);
            
            // Aún así, responder con 200 para evitar reintentos de WhatsApp
            // El error se manejará internamente
            return ResponseEntity.ok("EVENT_RECEIVED");
        }
    }

    /**
     * Endpoint de prueba para simular una notificación de webhook
     * Solo disponible en desarrollo
     */
    @PostMapping("/test")
    public ResponseEntity<String> testWebhook(@RequestBody String rawPayload) {
        if (!isLocalProfile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not Found");
        }

        log.info("[WEBHOOK-TEST] 🧪 Prueba de webhook recibida");
        log.info("[WEBHOOK-TEST] Payload: {}", rawPayload);
        return ResponseEntity.ok("TEST_RECEIVED");
    }

    /**
     * Endpoint para verificar el estado del webhook
     */
    @GetMapping("/status")
    public ResponseEntity<java.util.Map<String, Object>> getWebhookStatus() {
        if (!isLocalProfile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("status", "active");
        status.put("endpoint", "/api/webhooks/whatsapp");
        status.put("verifyTokenConfigured", verifyToken != null && !verifyToken.isEmpty());
        status.put("description", "WhatsApp Cloud API Webhook Endpoint");
        status.put("documentation", "https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks");
        
        log.info("[WEBHOOK-STATUS] 📊 Estado consultado");
        return ResponseEntity.ok(status);
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    private String resolveSignatureHeader(String signatureHeader, HttpServletRequest request) {
        if (signatureHeader != null && !signatureHeader.isBlank()) {
            return signatureHeader;
        }

        String forwardedHeader = request.getHeader("x-hub-signature-256");
        if (forwardedHeader != null && !forwardedHeader.isBlank()) {
            return forwardedHeader;
        }

        return request.getHeader("X-Hub-Signature");
    }

    private boolean isPayloadTooLarge(byte[] rawPayload) {
        return rawPayload != null && rawPayload.length > MAX_WEBHOOK_PAYLOAD_BYTES;
    }

    private boolean hasMinimumWebhookStructure(WhatsAppWebhookDto webhook) {
        return webhook != null
                && webhook.getObject() != null
                && webhook.getEntry() != null
                && !webhook.getEntry().isEmpty();
    }

    private ObjectReader buildSafeWebhookReader(ObjectMapper baseMapper) {
        ObjectMapper safeMapper = baseMapper.copy();
        safeMapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return safeMapper.readerFor(WhatsAppWebhookDto.class);
    }
}
