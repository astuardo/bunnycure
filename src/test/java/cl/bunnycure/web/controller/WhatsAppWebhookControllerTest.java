package cl.bunnycure.web.controller;

import cl.bunnycure.service.WhatsAppWebhookService;
import cl.bunnycure.web.dto.WhatsAppWebhookDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private WhatsAppWebhookService webhookService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        WhatsAppWebhookController controller = new WhatsAppWebhookController(
                webhookService,
                new MockEnvironment().withProperty("spring.profiles.active", "test"),
                objectMapper
        );
        ReflectionTestUtils.setField(controller, "verifyToken", "verify-token-test");
        ReflectionTestUtils.setField(controller, "appSecret", "secret123");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void receiveNotification_WithValidSignature_ProcessesWebhook() throws Exception {
        byte[] payload = webhookPayload();
        String signature = "sha256=" + hmacSha256Hex(payload, "secret123");
        when(webhookService.isSignatureValid(any(byte[].class), eq(signature), eq("secret123"))).thenReturn(true);

        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .contentType("application/json")
                        .content(payload)
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(webhookService).isSignatureValid(payloadCaptor.capture(), eq(signature), eq("secret123"));
        assertEquals(new String(payload, StandardCharsets.UTF_8), new String(payloadCaptor.getValue(), StandardCharsets.UTF_8));
        verify(webhookService).processWebhookNotification(any(WhatsAppWebhookDto.class));
    }

    @Test
    void receiveNotification_WithInvalidSignature_ReturnsForbidden() throws Exception {
        byte[] payload = webhookPayload();
        when(webhookService.isSignatureValid(any(byte[].class), anyString(), eq("secret123"))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .contentType("application/json")
                        .content(payload)
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "admin.bunnycure.cl"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid signature"));

        verify(webhookService, never()).processWebhookNotification(any());
    }

    @Test
    void receiveNotification_WithoutSignatureHeader_ReturnsForbidden() throws Exception {
        byte[] payload = webhookPayload();
        when(webhookService.isSignatureValid(any(byte[].class), isNull(), eq("secret123"))).thenReturn(false);

        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid signature"));

        verify(webhookService, never()).processWebhookNotification(any());
    }

    @Test
    void receiveNotification_WithLegacySignatureHeader_ProcessesWebhook() throws Exception {
        byte[] payload = webhookPayload();
        String signature = "sha256=" + hmacSha256Hex(payload, "secret123");
        when(webhookService.isSignatureValid(any(byte[].class), eq(signature), eq("secret123"))).thenReturn(true);

        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .contentType("application/json")
                        .content(payload)
                        .header("X-Hub-Signature", signature))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        verify(webhookService).processWebhookNotification(any(WhatsAppWebhookDto.class));
    }

    private byte[] webhookPayload() throws Exception {
        String json = """
                {
                  \"object\": \"whatsapp_business_account\",
                  \"entry\": [
                    {
                      \"id\": \"entry-1\",
                      \"changes\": [
                        {
                          \"field\": \"messages\",
                          \"value\": {
                            \"messaging_product\": \"whatsapp\",
                            \"messages\": [
                              {
                                \"from\": \"56912345678\",
                                \"id\": \"wamid-test-1\",
                                \"timestamp\": \"1710000000\",
                                \"type\": \"text\",
                                \"text\": {
                                  \"body\": \"Hola\"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private String hmacSha256Hex(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
