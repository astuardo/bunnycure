package cl.bunnycure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "whatsapp.api")
public class WhatsAppConfig {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    private String token;
    private String phoneId;
    private String citaConfirmadaTemplateName = "cita_confirmada";
    private String citaConfirmadaLanguageCode = "es";
    private boolean useTemplateForConfirmation = true;
    private String businessName = "BunnyCure";

    @PostConstruct
    public void init() {
        log.info("[WHATSAPP-CONFIG] Token configured: {}", token != null && !token.isEmpty() ? "YES (length: " + token.length() + ")" : "NO");
        log.info("[WHATSAPP-CONFIG] PhoneId configured: {}", phoneId != null && !phoneId.isEmpty() ? phoneId : "NO");
        log.info("[WHATSAPP-CONFIG] Confirmation template: {} / {} (enabled={})",
                citaConfirmadaTemplateName, citaConfirmadaLanguageCode, useTemplateForConfirmation);
        log.info("[WHATSAPP-CONFIG] Business name: {}", businessName);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPhoneId() {
        return phoneId;
    }

    public void setPhoneId(String phoneId) {
        this.phoneId = phoneId;
    }

    public String getCitaConfirmadaTemplateName() {
        return citaConfirmadaTemplateName;
    }

    public void setCitaConfirmadaTemplateName(String citaConfirmadaTemplateName) {
        this.citaConfirmadaTemplateName = citaConfirmadaTemplateName;
    }

    public String getCitaConfirmadaLanguageCode() {
        return citaConfirmadaLanguageCode;
    }

    public void setCitaConfirmadaLanguageCode(String citaConfirmadaLanguageCode) {
        this.citaConfirmadaLanguageCode = citaConfirmadaLanguageCode;
    }

    public boolean isUseTemplateForConfirmation() {
        return useTemplateForConfirmation;
    }

    public void setUseTemplateForConfirmation(boolean useTemplateForConfirmation) {
        this.useTemplateForConfirmation = useTemplateForConfirmation;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
