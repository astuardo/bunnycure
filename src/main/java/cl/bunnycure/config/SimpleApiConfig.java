package cl.bunnycure.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class SimpleApiConfig {

    @Value("${simple-api.key:}")
    private String apiKey;

    @Value("${simple-api.endpoint:https://servicios.simpleapi.cl}")
    private String endpoint;

    @Value("${simple-api.enabled:false}")
    private boolean enabled;

    @Value("${simple-api.owner-rut:}")
    private String apiOwnerRut;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
