package cl.bunnycure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WhatsAppConfigTest {

    @Test
    void restTemplate_IsCreatedWithBuilder() {
        WhatsAppConfig config = new WhatsAppConfig();
        RestTemplateBuilder builder = new RestTemplateBuilder();

        RestTemplate restTemplate = config.restTemplate(builder);

        assertNotNull(restTemplate);
        assertNotNull(restTemplate.getRequestFactory());
    }
}
