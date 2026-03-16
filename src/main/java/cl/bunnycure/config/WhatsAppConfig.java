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

    public String getRecordatorioCitaTemplateName() {
        return recordatorioCitaTemplateName;
    }

    public void setRecordatorioCitaTemplateName(String recordatorioCitaTemplateName) {
        this.recordatorioCitaTemplateName = recordatorioCitaTemplateName;
    }

    public String getCancelacionCitaTemplateName() {
        return cancelacionCitaTemplateName;
    }

    public void setCancelacionCitaTemplateName(String cancelacionCitaTemplateName) {
        this.cancelacionCitaTemplateName = cancelacionCitaTemplateName;
    }

    public String getAgendaEnRevisionTemplateName() {
        return agendaEnRevisionTemplateName;
    }

    public void setAgendaEnRevisionTemplateName(String agendaEnRevisionTemplateName) {
        this.agendaEnRevisionTemplateName = agendaEnRevisionTemplateName;
    }

    public String getSolicitudRechazadaTemplateName() {
        return solicitudRechazadaTemplateName;
    }

    public void setSolicitudRechazadaTemplateName(String solicitudRechazadaTemplateName) {
        this.solicitudRechazadaTemplateName = solicitudRechazadaTemplateName;
    }

    public String getAdminBookingAlertTemplateName() {
        return adminBookingAlertTemplateName;
    }

    public void setAdminBookingAlertTemplateName(String adminBookingAlertTemplateName) {
        this.adminBookingAlertTemplateName = adminBookingAlertTemplateName;
    }

    public String getCitaConfirmadaLanguageCode() {
        return citaConfirmadaLanguageCode;
    }

    public void setCitaConfirmadaLanguageCode(String citaConfirmadaLanguageCode) {
        this.citaConfirmadaLanguageCode = citaConfirmadaLanguageCode;
    }

    public String getAdminBookingAlertLanguageCode() {
        return adminBookingAlertLanguageCode;
    }

    public void setAdminBookingAlertLanguageCode(String adminBookingAlertLanguageCode) {
        this.adminBookingAlertLanguageCode = adminBookingAlertLanguageCode;
    }

    public boolean isUseTemplateForConfirmation() {
        return useTemplateForConfirmation;
    }

    public void setUseTemplateForConfirmation(boolean useTemplateForConfirmation) {
        this.useTemplateForConfirmation = useTemplateForConfirmation;
    }

    public boolean isUseTemplateForReminder() {
        return useTemplateForReminder;
    }

    public void setUseTemplateForReminder(boolean useTemplateForReminder) {
        this.useTemplateForReminder = useTemplateForReminder;
    }

    public boolean isUseTemplateForCancellation() {
        return useTemplateForCancellation;
    }

    public void setUseTemplateForCancellation(boolean useTemplateForCancellation) {
        this.useTemplateForCancellation = useTemplateForCancellation;
    }

    public boolean isUseTemplateForBookingRequest() {
        return useTemplateForBookingRequest;
    }

    public void setUseTemplateForBookingRequest(boolean useTemplateForBookingRequest) {
        this.useTemplateForBookingRequest = useTemplateForBookingRequest;
    }

    public boolean isUseTemplateForBookingRejection() {
        return useTemplateForBookingRejection;
    }

    public void setUseTemplateForBookingRejection(boolean useTemplateForBookingRejection) {
        this.useTemplateForBookingRejection = useTemplateForBookingRejection;
    }

    public boolean isUseTemplateForAdminAlert() {
        return useTemplateForAdminAlert;
    }

    public void setUseTemplateForAdminAlert(boolean useTemplateForAdminAlert) {
        this.useTemplateForAdminAlert = useTemplateForAdminAlert;
    }

    public String getAdminBookingRequestsUrl() {
        return adminBookingRequestsUrl;
    }

    public void setAdminBookingRequestsUrl(String adminBookingRequestsUrl) {
        this.adminBookingRequestsUrl = adminBookingRequestsUrl;
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
