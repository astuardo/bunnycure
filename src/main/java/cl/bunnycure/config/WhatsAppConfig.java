package cl.bunnycure.config;

import cl.bunnycure.service.AppSettingsService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Optional to keep startup resilient in contexts where AppSettings is unavailable.
    @Autowired(required = false)
    private AppSettingsService appSettingsService;

    private String token;
    private String phoneId;

    // Templates
    private String citaConfirmadaTemplateName = "confirmacion_cita";
    private String recordatorioCitaTemplateName = "recordatorio_cita";
    private String cancelacionCitaTemplateName = "cancelacion_cita";
    private String agendaEnRevisionTemplateName = "agenda_en_revision";
    private String solicitudRechazadaTemplateName = "solicitud_rechazada";
    private String adminBookingAlertTemplateName = "";
    private String adminAppointmentAlertTemplateName = "confirmacion_hora";

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
    private boolean useTemplateForAdminAppointmentAlert = true;
    private String adminBookingRequestsUrl = "";

    private String businessName = "BunnyCure";

    @PostConstruct
    public void init() {
        log.info("[WHATSAPP-CONFIG] Token configured: {}", token != null && !token.isEmpty() ? "YES (length: " + token.length() + ")" : "NO");
        log.info("[WHATSAPP-CONFIG] PhoneId configured: {}", phoneId != null && !phoneId.isEmpty() ? phoneId : "NO");
        log.info("[WHATSAPP-CONFIG] Templates:");
        log.info("[WHATSAPP-CONFIG]   - Confirmacion: {} (enabled={})", getCitaConfirmadaTemplateName(), isUseTemplateForConfirmation());
        log.info("[WHATSAPP-CONFIG]   - Recordatorio: {} (enabled={})", getRecordatorioCitaTemplateName(), isUseTemplateForReminder());
        log.info("[WHATSAPP-CONFIG]   - Cancelacion: {} (enabled={})", getCancelacionCitaTemplateName(), isUseTemplateForCancellation());
        log.info("[WHATSAPP-CONFIG]   - Agenda en revision: {} (enabled={})", getAgendaEnRevisionTemplateName(), isUseTemplateForBookingRequest());
        log.info("[WHATSAPP-CONFIG]   - Solicitud rechazada: {} (enabled={})", getSolicitudRechazadaTemplateName(), isUseTemplateForBookingRejection());
        log.info("[WHATSAPP-CONFIG]   - Alerta admin (booking): {} (enabled={})", getAdminBookingAlertTemplateName(), isUseTemplateForAdminAlert());
        log.info("[WHATSAPP-CONFIG]   - Alerta admin (cita creada): {} (enabled={})", getAdminAppointmentAlertTemplateName(), isUseTemplateForAdminAppointmentAlert());
        log.info("[WHATSAPP-CONFIG] Language: {}", getCitaConfirmadaLanguageCode());
        log.info("[WHATSAPP-CONFIG] Admin alert language: {}", getAdminBookingAlertLanguageCode());
        log.info("[WHATSAPP-CONFIG] Admin booking requests URL: {}", getAdminBookingRequestsUrl());
        log.info("[WHATSAPP-CONFIG] Business name: {}", getBusinessName());
    }

    public String getCitaConfirmadaTemplateName() {
        return getDynamicSetting("whatsapp.template.confirmation.name", citaConfirmadaTemplateName);
    }

    public String getRecordatorioCitaTemplateName() {
        return getDynamicSetting("whatsapp.template.reminder.name", recordatorioCitaTemplateName);
    }

    public String getCancelacionCitaTemplateName() {
        return getDynamicSetting("whatsapp.template.cancellation.name", cancelacionCitaTemplateName);
    }

    public String getAgendaEnRevisionTemplateName() {
        return getDynamicSetting("whatsapp.template.booking-review.name", agendaEnRevisionTemplateName);
    }

    public String getSolicitudRechazadaTemplateName() {
        return getDynamicSetting("whatsapp.template.booking-rejected.name", solicitudRechazadaTemplateName);
    }

    public String getAdminBookingAlertTemplateName() {
        return getDynamicSetting("whatsapp.template.admin-alert.name", adminBookingAlertTemplateName);
    }

    public String getAdminAppointmentAlertTemplateName() {
        return getDynamicSetting("whatsapp.template.admin-appointment-alert.name", adminAppointmentAlertTemplateName);
    }

    public String getCitaConfirmadaLanguageCode() {
        return getDynamicSetting("whatsapp.template.language", citaConfirmadaLanguageCode);
    }

    public String getAdminBookingAlertLanguageCode() {
        return getDynamicSetting("whatsapp.template.admin-alert.language", adminBookingAlertLanguageCode);
    }

    public boolean isUseTemplateForConfirmation() {
        return getDynamicBooleanSetting("whatsapp.template.confirmation.enabled", useTemplateForConfirmation);
    }

    public boolean isUseTemplateForReminder() {
        return getDynamicBooleanSetting("whatsapp.template.reminder.enabled", useTemplateForReminder);
    }

    public boolean isUseTemplateForCancellation() {
        return getDynamicBooleanSetting("whatsapp.template.cancellation.enabled", useTemplateForCancellation);
    }

    public boolean isUseTemplateForBookingRequest() {
        return getDynamicBooleanSetting("whatsapp.template.booking-review.enabled", useTemplateForBookingRequest);
    }

    public boolean isUseTemplateForBookingRejection() {
        return getDynamicBooleanSetting("whatsapp.template.booking-rejected.enabled", useTemplateForBookingRejection);
    }

    public boolean isUseTemplateForAdminAlert() {
        return getDynamicBooleanSetting("whatsapp.template.admin-alert.enabled", useTemplateForAdminAlert);
    }

    public boolean isUseTemplateForAdminAppointmentAlert() {
        return getDynamicBooleanSetting("whatsapp.template.admin-appointment-alert.enabled", useTemplateForAdminAppointmentAlert);
    }

    public String getAdminBookingRequestsUrl() {
        return getDynamicSetting("whatsapp.admin.booking-requests.url", adminBookingRequestsUrl);
    }

    public String getBusinessName() {
        String resolvedBusinessName = getDynamicSetting("whatsapp.business.name", businessName);
        return getDynamicSetting("app.name", resolvedBusinessName);
    }

    private String getDynamicSetting(String key, String fallbackValue) {
        if (appSettingsService == null) {
            return fallbackValue;
        }
        try {
            return appSettingsService.get(key, fallbackValue);
        } catch (Exception ex) {
            log.debug("[WHATSAPP-CONFIG] Could not resolve dynamic setting '{}', using fallback", key, ex);
            return fallbackValue;
        }
    }

    private boolean getDynamicBooleanSetting(String key, boolean fallbackValue) {
        if (appSettingsService == null) {
            return fallbackValue;
        }
        try {
            return appSettingsService.getBoolean(key, fallbackValue);
        } catch (Exception ex) {
            log.debug("[WHATSAPP-CONFIG] Could not resolve dynamic boolean setting '{}', using fallback", key, ex);
            return fallbackValue;
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}