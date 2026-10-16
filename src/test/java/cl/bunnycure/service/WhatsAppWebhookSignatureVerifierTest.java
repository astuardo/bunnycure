package cl.bunnycure.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Firma X-Hub-Signature-256 de webhooks de Meta (trasladados desde WhatsAppWebhookServiceTest). */
@DisplayName("Verificación de firma de webhooks de WhatsApp")
class WhatsAppWebhookSignatureVerifierTest {

    private static final byte[] PAYLOAD = "{\"test\":true}".getBytes(StandardCharsets.UTF_8);

    private final WhatsAppWebhookSignatureVerifier verifier = new WhatsAppWebhookSignatureVerifier();

    @Test
    void validSignature_IsAccepted() throws Exception {
        String signature = "sha256=" + hmacSha256Hex(PAYLOAD, "secret123");
        assertTrue(verifier.isSignatureValid(PAYLOAD, signature, "secret123"));
    }

    @Test
    void invalidSignature_IsRejected() {
        assertFalse(verifier.isSignatureValid(PAYLOAD, "sha256=deadbeef", "secret123"));
    }

    @Test
    void multipleHeaderValues_AcceptedWhenOneSignatureMatches() throws Exception {
        String header = "sha256=deadbeef, sha256=" + hmacSha256Hex(PAYLOAD, "secret123");
        assertTrue(verifier.isSignatureValid(PAYLOAD, header, "secret123"));
    }

    @Test
    void signatureWithAnotherSecret_IsRejected() throws Exception {
        String signature = "sha256=" + hmacSha256Hex(PAYLOAD, "otro-secreto");
        assertFalse(verifier.isSignatureValid(PAYLOAD, signature, "secret123"));
    }

    @Test
    void missingHeader_IsRejected() {
        assertFalse(verifier.isSignatureValid(PAYLOAD, null, "secret123"));
    }

    @Test
    @DisplayName("Sin App Secret configurado: rechaza por defecto (seguro por defecto)")
    void missingAppSecret_IsRejectedByDefault() {
        assertFalse(verifier.isSignatureValid(PAYLOAD, "sha256=cualquiera", null));
        assertFalse(verifier.isSignatureValid(PAYLOAD, null, "  "));
    }

    @Test
    @DisplayName("Sin App Secret y allow-unsigned=true (solo desarrollo local): acepta")
    void missingAppSecret_AcceptedOnlyWhenExplicitlyAllowed() {
        WhatsAppWebhookSignatureVerifier local = new WhatsAppWebhookSignatureVerifier();
        org.springframework.test.util.ReflectionTestUtils.setField(local, "allowUnsigned", true);
        assertTrue(local.isSignatureValid(PAYLOAD, null, null));
    }

    private static String hmacSha256Hex(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder sb = new StringBuilder();
        for (byte b : mac.doFinal(payload)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
