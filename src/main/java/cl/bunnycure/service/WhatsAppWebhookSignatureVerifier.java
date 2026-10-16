package cl.bunnycure.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * Verificación de la firma X-Hub-Signature-256 de los webhooks de WhatsApp (Meta):
 * HMAC-SHA256 del cuerpo crudo con el App Secret, comparado en tiempo constante.
 * Separado de WhatsAppWebhookService, que se ocupa de procesar los eventos ya verificados.
 */
@Slf4j
@Component
public class WhatsAppWebhookSignatureVerifier {

    /** Aceptar webhooks sin firmar si falta el App Secret. Solo para desarrollo local. */
    @org.springframework.beans.factory.annotation.Value("${whatsapp.webhook.allow-unsigned:false}")
    private boolean allowUnsigned;

    public boolean isSignatureValid(String rawPayload, String signatureHeader, String appSecret) {
        byte[] payloadBytes = rawPayload != null
                ? rawPayload.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        return isSignatureValid(payloadBytes, signatureHeader, appSecret);
    }

    public boolean isSignatureValid(byte[] rawPayloadBytes, String signatureHeader, String appSecret) {
        String cleanSecret = cleanAppSecret(appSecret);
        if (cleanSecret.isBlank()) {
            // Seguro por defecto: sin App Secret no se puede verificar el origen y se rechaza.
            // Solo el desarrollo local puede aceptar webhooks sin firmar (whatsapp.webhook.allow-unsigned=true).
            if (allowUnsigned) {
                log.warn("[WEBHOOK] ⚠️ App Secret no configurado: webhook aceptado SIN verificar firma (allow-unsigned=true)");
                return true;
            }
            log.error("[WEBHOOK] ❌ App Secret no configurado (WHATSAPP_WEBHOOK_APP_SECRET): webhook rechazado");
            return false;
        }

        if (cleanSecret.startsWith("EAAG") || cleanSecret.startsWith("EAA")) {
            log.error("[WEBHOOK] ❌ CRITICAL: whatsapp.webhook.app-secret appears to be an Access Token (starts with EAA...) instead of the Meta App Secret (App Dashboard -> Basic Settings -> App Secret). Signatures will fail!");
        }

        List<String> expectedSignatures = extractExpectedSignatures(signatureHeader);
        if (expectedSignatures.isEmpty()) {
            log.warn("[WEBHOOK] ⚠️ Missing or invalid X-Hub-Signature-256 header");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cleanSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawPayloadBytes != null ? rawPayloadBytes : new byte[0]);
            String actual = toHex(digest);
            boolean valid = expectedSignatures.stream().anyMatch(expected ->
                    MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))
            );
            if (!valid) {
                log.warn("[WEBHOOK] ⚠️ Signature mismatch. expectedPrefix={}, actualPrefix={}, payloadBytes={}",
                        safePrefix(expectedSignatures.get(0)), safePrefix(actual), rawPayloadBytes != null ? rawPayloadBytes.length : 0);
            }
            return valid;
        } catch (Exception e) {
            log.error("[WEBHOOK] ❌ Error validating webhook signature", e);
            return false;
        }
    }

    private List<String> extractExpectedSignatures(String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return List.of();
        }

        List<String> signatures = new ArrayList<>();
        String[] candidates = signatureHeader.split(",");
        for (String rawCandidate : candidates) {
            if (rawCandidate == null) {
                continue;
            }
            String candidate = rawCandidate.trim();
            if (candidate.isEmpty()) {
                continue;
            }

            String normalized = candidate.toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("sha256=")) {
                continue;
            }

            String value = normalized.substring("sha256=".length()).trim();
            if (value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
                continue;
            }
            signatures.add(value);
        }
        return signatures;
    }

    private String cleanAppSecret(String secret) {
        if (secret == null) {
            return "";
        }
        String s = secret.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String safePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        return value.substring(0, Math.min(value.length(), 8));
    }
}
