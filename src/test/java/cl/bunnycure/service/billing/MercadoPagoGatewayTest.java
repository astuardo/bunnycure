package cl.bunnycure.service.billing;

import cl.bunnycure.domain.enums.BillingCycle;
import cl.bunnycure.domain.enums.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Mercado Pago: firma de webhooks, referencia externa y activación")
class MercadoPagoGatewayTest {

    private static final String SECRET = "secreto-de-prueba";

    private MercadoPagoGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MercadoPagoGateway(new RestTemplate());
        ReflectionTestUtils.setField(gateway, "enabled", true);
        ReflectionTestUtils.setField(gateway, "accessToken", "TEST-token");
        ReflectionTestUtils.setField(gateway, "webhookSecret", SECRET);
    }

    @Test
    @DisplayName("Firma válida: HMAC-SHA256 de 'id:{data.id};request-id:{x-request-id};ts:{ts};'")
    void validSignature_IsAccepted() throws Exception {
        String v1 = hmac("id:12345;request-id:req-1;ts:1700000000;");
        assertThat(gateway.isValidSignature("ts=1700000000,v1=" + v1, "req-1", "12345")).isTrue();
    }

    @Test
    @DisplayName("Firma alterada, de otro id o con otra clave → rechazada")
    void tamperedSignature_IsRejected() throws Exception {
        String v1 = hmac("id:12345;request-id:req-1;ts:1700000000;");
        assertThat(gateway.isValidSignature("ts=1700000000,v1=" + v1, "req-1", "99999")).isFalse();
        assertThat(gateway.isValidSignature("ts=1700000001,v1=" + v1, "req-1", "12345")).isFalse();
        assertThat(gateway.isValidSignature(null, "req-1", "12345")).isFalse();
        assertThat(gateway.isValidSignature("v1=" + v1, "req-1", "12345")).isFalse();
    }

    @Test
    @DisplayName("Sin x-request-id, esa parte se omite del manifiesto")
    void signatureWithoutRequestId() throws Exception {
        String v1 = hmac("id:abc;ts:1700000000;");
        assertThat(gateway.isValidSignature("ts=1700000000,v1=" + v1, null, "ABC")).isTrue();
    }

    @Test
    @DisplayName("La referencia externa transporta salón, plan y ciclo")
    void externalReferenceRoundTrip() {
        String ref = MercadoPagoGateway.externalReference(42L, SubscriptionPlan.PRO, BillingCycle.ANNUAL);
        assertThat(MercadoPagoGateway.parseExternalReference(ref)).hasValueSatisfying(r -> {
            assertThat(r.tenantId()).isEqualTo(42L);
            assertThat(r.plan()).isEqualTo(SubscriptionPlan.PRO);
            assertThat(r.cycle()).isEqualTo(BillingCycle.ANNUAL);
        });
        assertThat(MercadoPagoGateway.parseExternalReference("otra-cosa")).isEmpty();
        assertThat(MercadoPagoGateway.parseExternalReference(null)).isEmpty();
    }

    @Test
    @DisplayName("Apagado por configuración o sin credenciales → deshabilitado")
    void disabledWithoutFlagOrCredentials() {
        assertThat(gateway.isEnabled()).isTrue();
        ReflectionTestUtils.setField(gateway, "enabled", false);
        assertThat(gateway.isEnabled()).isFalse();
        ReflectionTestUtils.setField(gateway, "enabled", true);
        ReflectionTestUtils.setField(gateway, "webhookSecret", "");
        assertThat(gateway.isEnabled()).isFalse();
    }

    private static String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
