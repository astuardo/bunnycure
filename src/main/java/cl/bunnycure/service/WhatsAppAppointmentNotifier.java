package cl.bunnycure.service;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import java.util.Arrays;

/**
 * Mensajes de negocio por WhatsApp: confirmación, recordatorio y cancelación de citas, solicitudes
 * de reserva, valoración en Google, alertas a la administración y actualización de sellos.
 * Decide QUÉ enviar (texto o plantilla de Meta y sus parámetros); el envío lo hace WhatsAppService.
 */
@Slf4j
@Service
public class WhatsAppAppointmentNotifier {

    private final WhatsAppService whatsAppService;
    private final WhatsAppConfig config;
    private final WhatsAppMessageBuilderService messageBuilder;
    private final WhatsAppAdminAlertDispatcher adminAlertDispatcher;
    private final LoyaltyRewardService loyaltyRewardService;

    public WhatsAppAppointmentNotifier(WhatsAppService whatsAppService,
                                       WhatsAppConfig config,
                                       WhatsAppMessageBuilderService messageBuilder,
                                       @Lazy WhatsAppAdminAlertDispatcher adminAlertDispatcher,
                                       LoyaltyRewardService loyaltyRewardService) {
        this.whatsAppService = whatsAppService;
        this.config = config;
        this.messageBuilder = messageBuilder;
        this.adminAlertDispatcher = adminAlertDispatcher;
        this.loyaltyRewardService = loyaltyRewardService;
    }

    /**
     * Envía mensaje de confirmación de cita por WhatsApp
     */
    @Async
    public void sendAppointmentConfirmation(Appointment appointment) {
        try {
            String phone = appointment.getCustomer().getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                        appointment.getCustomer().getFullName());
                return;
            }

            if (config.isUseTemplateForConfirmation()) {
                sendCitaConfirmadaTemplate(appointment);
                return;
            }

            String salonName = whatsAppService.resolveSalonName(appointment.getTenantId());
            String message = messageBuilder.buildAppointmentConfirmationText(appointment, salonName);
            whatsAppService.sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar confirmación de cita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía mensaje de cancelación de cita por WhatsApp
     */
    @Async
    public void sendAppointmentCancellation(Appointment appointment) {
        try {
            String phone = appointment.getCustomer().getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                        appointment.getCustomer().getFullName());
                return;
            }

            String salonName = whatsAppService.resolveSalonName(appointment.getTenantId());
            String message = messageBuilder.buildAppointmentCancellationText(appointment, salonName);
            whatsAppService.sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar cancelación de cita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía mensaje de recordatorio de cita por WhatsApp
     */
    @Async
    public void sendAppointmentReminder(Appointment appointment) {
        try {
            String phone = appointment.getCustomer().getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                        appointment.getCustomer().getFullName());
                return;
            }

            String salonName = whatsAppService.resolveSalonName(appointment.getTenantId());
            String message = messageBuilder.buildAppointmentReminderText(appointment, salonName);
            whatsAppService.sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar recordatorio de cita: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía confirmación de recepción de solicitud de reserva
     */
    @Async
    public void sendBookingRequestReceived(BookingRequest request) {
        try {
            String phone = request.getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
                return;
            }

            String salonName = whatsAppService.resolveSalonName(request.getTenantId());
            String message = messageBuilder.buildBookingRequestReceivedText(request, salonName);
            whatsAppService.sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar confirmación de solicitud: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía mensaje de rechazo de solicitud de reserva
     */
    @Async
    public void sendBookingRequestRejected(BookingRequest request) {
        try {
            String phone = request.getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
                return;
            }

            String salonName = whatsAppService.resolveSalonName(request.getTenantId());
            String message = messageBuilder.buildBookingRequestRejectedText(request, salonName);
            whatsAppService.sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar rechazo de solicitud: {}", e.getMessage(), e);
        }
    }

    public boolean sendAdminBookingAlertSync(String toPhoneNumber, BookingRequest request) {
        return adminAlertDispatcher.sendAdminBookingAlertSync(toPhoneNumber, request);
    }

    @Async
    public void sendCitaConfirmadaTemplate(Appointment appointment) {
        sendCitaConfirmadaTemplate(appointment, false, false);
    }

    @Async
    public void sendCitaConfirmadaTemplate(Appointment appointment, boolean dateChanged, boolean timeChanged) {
        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        var customer = appointment.getCustomer();
        int stamps = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
        int rewardIndex = customer.getCurrentRewardIndex() != null ? customer.getCurrentRewardIndex() : 0;
        var reward = loyaltyRewardService.getRewardAt(rewardIndex);
        String rewardName = (reward != null) ? reward.getName() : "un premio especial";

        String loyaltyInfo;
        if (stamps >= 10) {
            loyaltyInfo = String.format("¡Felicidades! Esta es tu cita #11. ¡Hoy disfrutas de tu premio: %s! 🎉", rewardName);
        } else {
            loyaltyInfo = String.format("Llevas %d/10 marcas para ganarte el premio: %s 🐇", stamps, rewardName);
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildCitaConfirmadaParams(appointment, dateChanged, timeChanged, loyaltyInfo);

        whatsAppService.sendTemplateSync(
                phone,
                config.getCitaConfirmadaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams(),
                appointment
        );
    }

    /**
     * Envía el template recordatorio_cita con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha, {{3}}=hora
     */
    @Async
    public void sendRecordatorioCitaTemplate(Appointment appointment) {
        if (!config.isUseTemplateForReminder()) {
            log.info("[WHATSAPP] Template de recordatorio deshabilitado, enviando mensaje de texto");
            sendAppointmentReminder(appointment);
            return;
        }

        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildRecordatorioParams(appointment);

        whatsAppService.sendTemplateSync(
                phone,
                config.getRecordatorioCitaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams(),
                appointment
        );
    }

    /**
     * Envía el template cancelacion_cita con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha, {{3}}=hora, {{4}}=motivo
     */
    @Async
    public void sendCancelacionCitaTemplate(Appointment appointment) {
        if (!config.isUseTemplateForCancellation()) {
            log.info("[WHATSAPP-SKIP] Template de cancelación deshabilitado");
            return;
        }

        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return;
        }

        String motivo = resolveCancellationReason(appointment);

        log.info("[WHATSAPP] Enviando plantilla cancelacion_cita para cita {} con motivo: '{}'",
                appointment.getId(), motivo);

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildCancelacionParams(appointment, motivo);

        whatsAppService.sendTemplateSync(
                phone,
                config.getCancelacionCitaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams(),
                appointment
        );
    }

    /**
     * Resuelve el motivo y origen de la cancelación a partir de las observaciones de la cita.
     */
    public String resolveCancellationReason(Appointment appointment) {
        return messageBuilder.resolveCancellationReason(appointment);
    }

    /**
     * Envía la plantilla valoracion_servicio_google de Meta para solicitar reseña en Google.
     * BODY {{1}}=cliente, BODY {{2}}=servicio
     */
    @Async
    public void sendValoracionServicioGoogleTemplate(Appointment appointment) {
        sendValoracionServicioGoogleTemplateSync(appointment);
    }

    public boolean sendValoracionServicioGoogleTemplateSync(Appointment appointment) {
        if (!config.isUseTemplateForReview()) {
            log.info("[WHATSAPP-SKIP] Template de valoración de Google deshabilitado");
            return false;
        }

        if (appointment == null || appointment.getCustomer() == null) {
            log.warn("[WHATSAPP-SKIP] Cita o cliente nulo");
            return false;
        }

        String phone = appointment.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado",
                    appointment.getCustomer().getFullName());
            return false;
        }

        String cliente = appointment.getCustomer().getFullName();
        String servicio = appointment.getService() != null && appointment.getService().getName() != null
                ? appointment.getService().getName()
                : "Servicio";

        return whatsAppService.sendTemplateSync(
                phone,
                config.getReviewTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                null,
                Arrays.asList(cliente, servicio),
                appointment
        );
    }

    /**
     * Envía notificación al admin cuando se crea una cita desde el dashboard.
     */
    @Async
    public void sendAdminAppointmentCreatedAlert(Appointment appointment) {
        adminAlertDispatcher.sendAdminAppointmentCreatedAlert(appointment);
    }

    /**
     * Envía el template agenda_en_revision con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha solicitada, {{3}}=bloque
     */
    @Async
    public void sendAgendaEnRevisionTemplate(BookingRequest request) {
        if (!config.isUseTemplateForBookingRequest()) {
            log.info("[WHATSAPP-SKIP] Template de agenda en revisión deshabilitado");
            return;
        }

        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
            return;
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildAgendaEnRevisionParams(request);

        whatsAppService.sendTemplate(
                phone,
                config.getAgendaEnRevisionTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams()
        );
    }

    /**
     * Envía el template solicitud_rechazada con placeholders:
     * HEADER {{1}}=cliente, BODY {{1}}=servicio, {{2}}=fecha solicitada, {{3}}=bloque
     */
    @Async
    public void sendSolicitudRechazadaTemplate(BookingRequest request) {
        if (!config.isUseTemplateForBookingRejection()) {
            log.info("[WHATSAPP-SKIP] Template de solicitud rechazada deshabilitado");
            return;
        }

        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            log.warn("[WHATSAPP-SKIP] Solicitud {} no tiene teléfono configurado", request.getId());
            return;
        }

        WhatsAppMessageBuilderService.TemplateParameters params =
                messageBuilder.buildSolicitudRechazadaParams(request);

        whatsAppService.sendTemplate(
                phone,
                config.getSolicitudRechazadaTemplateName(),
                config.getCitaConfirmadaLanguageCode(),
                params.headerParam(),
                params.bodyParams()
        );
    }

    @Async
    public void sendLoyaltyUpdateMessage(cl.bunnycure.domain.model.Customer customer) {
        try {
            String phone = customer.getPhone();
            if (phone == null || phone.isEmpty()) {
                log.warn("[WHATSAPP-SKIP] Cliente {} no tiene teléfono configurado para loyalty",
                        customer.getFullName());
                return;
            }

            int rewardIndex = customer.getCurrentRewardIndex() != null ? customer.getCurrentRewardIndex() : 0;
            var reward = loyaltyRewardService.getRewardAt(rewardIndex);
            String message = messageBuilder.buildLoyaltyUpdateText(customer, reward);

            whatsAppService.sendTextMessage(phone, message);

        } catch (Exception e) {
            log.error("[WHATSAPP] Error al enviar mensaje de fidelización: {}", e.getMessage(), e);
        }
    }
}
