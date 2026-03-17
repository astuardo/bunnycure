package cl.bunnycure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "whatsapp.api")
public class WhatsAppConfig {

    private String token;
    private String phoneId;

    // Templates
    private String citaConfirmadaTemplateName = "confirmacion_cita";
    private String recordatorioCitaTemplateName = "recordatorio_cita";
    private String cancelacionCitaTemplateName = "cancelacion_cita";
    private String agendaEnRevisionTemplateName = "agenda_en_revision";
    private String solicitudRechazadaTemplateName = "solicitud_rechazada";
    private String adminBookingAlertTemplateName = "";

    // Language
    private String citaConfirmadaLanguageCode = "es_CL";
    private String adminBookingAlertLanguageCode = "es_CL";

    // Behavior
    private boolean useTemplateForConfirmation = true;
    private boolean useTemplateForReminder = true;
    private boolean useTemplateForCancellation = true;
    private boolean useTemplateForBookingRequest = true;
    private boolean useTemplateForBookingRejection = true;
    private boolean useTemplateForAdminAlert = false;
    private String adminBookingRequestsUrl = "";

    private String businessName = "BunnyCure";

    @PostConstruct
    public void init() {
        log.info("[WHATSAPP-CONFIG] Token configured: {}", token != null && !token.isEmpty() ? "YES (length: " + token.length() + ")" : "NO");
        log.info("[WHATSAPP-CONFIG] PhoneId configured: {}", phoneId != null && !phoneId.isEmpty() ? phoneId : "NO");
        log.info("[WHATSAPP-CONFIG] Templates:");
        log.info("[WHATSAPP-CONFIG]   - Confirmacion: {} (enabled={})", citaConfirmadaTemplateName, useTemplateForConfirmation);
        log.info("[WHATSAPP-CONFIG]   - Recordatorio: {} (enabled={})", recordatorioCitaTemplateName, useTemplateForReminder);
        log.info("[WHATSAPP-CONFIG]   - Cancelacion: {} (enabled={})", cancelacionCitaTemplateName, useTemplateForCancellation);
        log.info("[WHATSAPP-CONFIG]   - Agenda en revision: {} (enabled={})", agendaEnRevisionTemplateName, useTemplateForBookingRequest);
        log.info("[WHATSAPP-CONFIG]   - Solicitud rechazada: {} (enabled={})", solicitudRechazadaTemplateName, useTemplateForBookingRejection);
        log.info("[WHATSAPP-CONFIG]   - Alerta admin: {} (enabled={})", adminBookingAlertTemplateName, useTemplateForAdminAlert);
        log.info("[WHATSAPP-CONFIG] Language: {}", citaConfirmadaLanguageCode);
        log.info("[WHATSAPP-CONFIG] Admin alert language: {}", adminBookingAlertLanguageCode);
        log.info("[WHATSAPP-CONFIG] Admin booking requests URL: {}", adminBookingRequestsUrl);
        log.info("[WHATSAPP-CONFIG] Business name: {}", businessName);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}