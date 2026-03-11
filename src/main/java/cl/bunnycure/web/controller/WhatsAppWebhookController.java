package cl.bunnycure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import cl.bunnycure.service.WhatsAppWebhookService;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppWebhookService webhookService;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.webhook.verify-token:bunnycure_webhook_token_2024}")
    private String verifyToken;

    @Value("${whatsapp.webhook.app-secret:}")
    private String appSecret;

    public WhatsAppWebhookController(WhatsAppWebhookService webhookService, Environment environment, ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

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

            if (!webhookService.isSignatureValid(rawPayload, signatureHeader, appSecret)) {
                log.warn("[WEBHOOK] ❌ Invalid webhook signature");
                log.warn("[WEBHOOK] Proxy context contentType={}, contentLength={}, xForwardedFor={}, xForwardedProto={}, xForwardedHost={}",
                        request.getContentType(),
                        request.getContentLengthLong(),
                        request.getHeader("X-Forwarded-For"),
                        request.getHeader("X-Forwarded-Proto"),
                        request.getHeader("X-Forwarded-Host"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
            }

            WhatsAppWebhookDto webhook = objectMapper.readValue(rawPayload, WhatsAppWebhookDto.class);
            log.debug("[WEBHOOK] Payload completo: {}", webhook);

            // Procesar la notificación de forma asíncrona (opcional pero recomendado)
            webhookService.processWebhookNotification(webhook);

            // Responder inmediatamente con 200 OK
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
}
