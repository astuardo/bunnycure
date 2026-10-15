package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Despachador especializado en alertas y notificaciones internas para administradores y dueños del salón.
 * Extraído de WhatsAppService para mantener separación de responsabilidades.
 */
@Slf4j
@Service
public class WhatsAppAdminAlertDispatcher {

    private final WhatsAppService whatsAppService;
    private final WhatsAppMessageBuilderService messageBuilder;
    private final WhatsAppConfig config;
    private final AppSettingsService appSettingsService;
    private final CalendarService calendarService;
    private final WhatsAppHandoffService whatsAppHandoffService;

    @Value("${bunnycure.whatsapp.admin-alert.enabled:true}")
    private boolean adminAlertEnabledFallback;

    public WhatsAppAdminAlertDispatcher(
            @Lazy WhatsAppService whatsAppService,
            WhatsAppMessageBuilderService messageBuilder,
            WhatsAppConfig config,
            AppSettingsService appSettingsService,
            CalendarService calendarService,
            WhatsAppHandoffService whatsAppHandoffService) {
        this.whatsAppService = whatsAppService;
        this.messageBuilder = messageBuilder;
        this.config = config;
        this.appSettingsService = appSettingsService;
        this.calendarService = calendarService;
        this.whatsAppHandoffService = whatsAppHandoffService;
    }

    /**
     * Envía alerta de nueva solicitud de reserva al teléfono del administrador de forma síncrona.
     */
    public boolean sendAdminBookingAlertSync(String toPhoneNumber, BookingRequest request) {
        if (request == null) {
            return false;
        }

        WhatsAppMessageBuilderService.TemplateParameters alertParams = messageBuilder.buildAdminBookingAlertParams(request);
        String cliente = alertParams.headerParam();
        List<String> bodyParams = alertParams.bodyParams();

        String adminTemplate = config.getAdminBookingAlertTemplateName();
        if (config.isUseTemplateForAdminAlert() && adminTemplate != null && !adminTemplate.isBlank()) {
            boolean templateSent = whatsAppService.sendTemplateSync(
                    toPhoneNumber,
                    adminTemplate,
                    resolveAdminAlertLanguageCode(),
                    cliente,
                    bodyParams
            );
            if (templateSent) {
                log.info("[WHATSAPP-ADMIN] ✅ Alerta enviada por template a {}", normalizePhoneNumber(toPhoneNumber));
                return true;
            }
            log.warn("[WHATSAPP-ADMIN] ⚠️ Fallo envío por template admin, se intentará texto personalizado");
        }

        String reviewLink = messageBuilder.buildAdminReviewLink(request.getId(), config.getAdminBookingRequestsUrl());
        String salonName = whatsAppService.resolveSalonName(request.getTenantId());
        String alertText = messageBuilder.buildAdminAlertText(request, salonName, reviewLink);

        boolean textSent = whatsAppService.sendTextMessageSync(toPhoneNumber, alertText);
        if (textSent) {
            return true;
        }

        if (config.isUseTemplateForBookingRequest()) {
            log.warn("[WHATSAPP-ADMIN] ⚠️ Fallo texto personalizado, se intentará template de respaldo '{}'", config.getAgendaEnRevisionTemplateName());
            return whatsAppService.sendTemplateSync(
                    toPhoneNumber,
                    config.getAgendaEnRevisionTemplateName(),
                    config.getCitaConfirmadaLanguageCode(),
                    cliente,
                    bodyParams
            );
        }

        return false;
    }

    /**
     * Envía notificación asíncrona al administrador tras la creación de una nueva cita.
     */
    @Async
    public void sendAdminAppointmentCreatedAlert(Appointment appointment) {
        if (appointment == null) {
            return;
        }

        if (!appSettingsService.isWhatsappAdminAlertEnabled(adminAlertEnabledFallback)) {
            log.info("[WHATSAPP-SKIP] Alertas WhatsApp admin deshabilitadas por configuración");
            return;
        }

        if (!config.isUseTemplateForAdminAppointmentAlert()) {
            log.info("[WHATSAPP-SKIP] Template de alerta admin para cita creada está deshabilitado");
            return;
        }

        String adminPhone = appSettingsService.getAdminAlertWhatsappNumber(null);
        if (adminPhone == null || adminPhone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Número de teléfono del admin no está configurado para alertas de citas");
            return;
        }

        String whatsappUrl = whatsAppHandoffService.generateWhatsAppUrl(
                appointment.getCustomer() != null ? appointment.getCustomer().getPhone() : null);
        String calendarUrl = calendarService.generateGoogleCalendarUrl(appointment);
        String nombreDuena = appSettingsService.get("app.owner.name", "Dueña");

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildAdminAppointmentCreatedParams(appointment, nombreDuena, whatsappUrl, calendarUrl);

        whatsAppService.sendTemplate(
                adminPhone,
                config.getAdminAppointmentAlertTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams()
        );

        log.info("[WHATSAPP-ADMIN] ✅ Alerta de cita creada enviada a admin {} con links: WA={}, Cal={}",
                normalizePhoneNumber(adminPhone),
                whatsappUrl != null && !whatsappUrl.isEmpty() ? "OK" : "N/A",
                calendarUrl != null && !calendarUrl.isEmpty() ? "OK" : "N/A");
    }

    private String resolveAdminAlertLanguageCode() {
        String configured = config.getAdminBookingAlertLanguageCode();
        if (configured == null || configured.isBlank()) {
            return config.getCitaConfirmadaLanguageCode();
        }
        return configured;
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return "";
        }
        String normalized = phone.replaceAll("[\\s\\-()]+", "");
        if (!normalized.startsWith("56") && !normalized.startsWith("+")) {
            normalized = "56" + normalized;
        }
        return normalized.replace("+", "");
    }
}
