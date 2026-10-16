package cl.bunnycure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Endpoints de diagnóstico: no expuestos sin autenticación")
class PublicDiagnosticsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Estado del webhook de WhatsApp: anónimo → 401, admin → 200")
    void whatsappWebhookStatus_RequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/webhook/whatsapp/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/webhooks/whatsapp/status").with(user("admin").roles("SALON_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("La verificación de Meta (GET del webhook) sigue siendo pública")
    void whatsappWebhookVerification_StaysPublic() throws Exception {
        int statusCode = mockMvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")
                        .param("hub.challenge", "123"))
                .andReturn().getResponse().getStatus();
        assertThat(statusCode).isNotEqualTo(401);
    }
}
