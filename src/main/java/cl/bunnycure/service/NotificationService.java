package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final WhatsAppService whatsAppService;
    private final WhatsAppAdminAlertOutboxService whatsAppAdminAlertOutboxService;
    private final AppSettingsService appSettingsService;
    private final WebPushNotificationService webPushNotificationService;

    @Value("${bunnycure.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${bunnycure.mail.from:noreply@bunnycure.cl}")
    private String mailFrom;

    @Value("${bunnycure.whatsapp.number:56964499995}")
    private String whatsappNumber;

    // ── Citas ────────────────────────────────────────────────────────────────

    @Async
    public void sendConfirmation(Appointment appointment) {
        if (!isMailEnabled()) {
            log.info("[MAIL-SKIP] Confirmación para {} (mail deshabilitado)",
                    appointment.getCustomer().getEmail());
            return;
        }
        send(appointment, "mail/confirmation",
                "💅 Tu cita está confirmada – BunnyCure");
    }

    // Compatibility alias used by booking approval flow.
    @Async
    public void sendAppointmentConfirmation(Appointment appointment) {
        if (appointment == null || appointment.getCustomer() == null) {
            return;
        }
        
        Customer customer = appointment.getCustomer();
        cl.bunnycure.domain.enums.NotificationPreference pref = customer.getNotificationPreference();
        
        // Enviar email solo si la preferencia lo permite
        if (pref != null && pref.allowsEmail()) {
            sendConfirmation(appointment);
        }
        
        // Enviar WhatsApp solo si la preferencia lo permite
        if (pref != null && pref.allowsWhatsApp() && customer.getPhone() != null) {
            log.info("[NOTIFICATION] Enviando confirmación de cita por WhatsApp a {}", 
                    customer.getPhone());
            whatsAppService.sendCitaConfirmadaTemplate(appointment);
        }
        
        // Siempre enviar notificación al admin/dueña
        log.info("[NOTIFICATION] Enviando alerta de nueva cita al admin");
        whatsAppService.sendAdminAppointmentCreatedAlert(appointment);
    }

    @Async
    public void sendCancellationNotice(Appointment appointment) {
        if (appointment == null || appointment.getCustomer() == null) {
            return;
        }
        
        Customer customer = appointment.getCustomer();
        cl.bunnycure.domain.enums.NotificationPreference pref = customer.getNotificationPreference();
        
        // Enviar email solo si está configurado Y la preferencia lo permite
        if (isMailEnabled() && pref != null && pref.allowsEmail()) {
            send(appointment, "mail/cancellation",
                    "Tu cita ha sido cancelada – BunnyCure");
        }
        
        // Enviar WhatsApp solo si la preferencia lo permite
        if (pref != null && pref.allowsWhatsApp() && customer.getPhone() != null) {
            log.info("[NOTIFICATION] Enviando notificación de cancelación por WhatsApp a {}", 
                    customer.getPhone());
            whatsAppService.sendCancelacionCitaTemplate(appointment);
        }
    }

    // ── Solicitudes de reserva ───────────────────────────────────────────────

    /**
     * Email a la clienta confirmando que su solicitud fue recibida y está en revisión.
     */
    @Async
    public void sendBookingRequestReceived(BookingRequest request) {
        if (request == null) {
            return;
        }
        
        cl.bunnycure.domain.enums.NotificationPreference pref = request.getNotificationPreference();
        
        // Enviar email solo si está configurado, tiene email Y la preferencia lo permite
        if (isMailEnabled() && pref != null && pref.allowsEmail() 
                && request.getEmail() != null && !request.getEmail().isBlank()) {
            try {
                String fechaFormateada = request.getPreferredDate()
                        .format(longDateFormatter());

                Context ctx = new Context(resolveAppLocale());
                ctx.setVariable("request",          request);
                ctx.setVariable("customerName",     request.getFullName());
                ctx.setVariable("serviceName",      request.getService().getName());
                ctx.setVariable("fechaFormateada",  fechaFormateada);
                ctx.setVariable("preferredBlock",   request.getPreferredBlock());

                String html = templateEngine.process("mail/booking-received", ctx);
                sendHtml(request.getEmail(), "🐇 Recibimos tu solicitud – BunnyCure", html);

            } catch (Exception e) {
                log.error("[MAIL-ERROR] booking-received → {}: {}", request.getEmail(), e.getMessage());
            }
        }
        
        // Enviar WhatsApp solo si tiene teléfono Y la preferencia lo permite
        if (pref != null && pref.allowsWhatsApp() 
                && request.getPhone() != null && !request.getPhone().isBlank()) {
            log.info("[NOTIFICATION] Enviando confirmación de recepción por WhatsApp a {}", 
                    request.getPhone());
            whatsAppService.sendAgendaEnRevisionTemplate(request);
        }
    }

    /**
     * Email a la clienta informando que su solicitud fue rechazada.
     */
    @Async
    public void sendBookingRequestRejected(BookingRequest request) {
        if (request == null) {
            return;
        }
        
        cl.bunnycure.domain.enums.NotificationPreference pref = request.getNotificationPreference();
        
        // Enviar email solo si está configurado, tiene email Y la preferencia lo permite
        if (isMailEnabled() && pref != null && pref.allowsEmail() 
                && request.getEmail() != null && !request.getEmail().isBlank()) {
            try {
                Context ctx = new Context(resolveAppLocale());
                ctx.setVariable("request",          request);
                ctx.setVariable("customerName",     request.getFullName());
                ctx.setVariable("serviceName",      request.getService().getName());
                ctx.setVariable("rejectionReason",  request.getRejectionReason());
                ctx.setVariable("whatsappNumber",   whatsappNumber);

                String html = templateEngine.process("mail/booking-rejected", ctx);
                sendHtml(request.getEmail(), "Sobre tu solicitud – BunnyCure", html);

            } catch (Exception e) {
                log.error("[MAIL-ERROR] booking-rejected → {}: {}", request.getEmail(), e.getMessage());
            }
        }
        
        // Enviar WhatsApp solo si tiene teléfono Y la preferencia lo permite
        if (pref != null && pref.allowsWhatsApp() 
                && request.getPhone() != null && !request.getPhone().isBlank()) {
            log.info("[NOTIFICATION] Enviando rechazo de solicitud por WhatsApp a {}", 
                    request.getPhone());
            whatsAppService.sendSolicitudRechazadaTemplate(request);
        }
    }

    /**
     * Punto de entrada para alertas internas de nuevas reservas.
     *
     * Nota: se mantiene como método "queue" para facilitar migración a outbox/cola.
     */
    public void queueAdminNewBookingAlert(BookingRequest request) {
        if (request == null || request.getId() == null) {
            log.warn("[WHATSAPP-ADMIN] No se pudo encolar alerta: solicitud nula o sin id");
            return;
        }

        Long bookingRequestId = request.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    whatsAppAdminAlertOutboxService.enqueueAndTryDispatch(bookingRequestId);
                }
            });
            return;
        }

        whatsAppAdminAlertOutboxService.enqueueAndTryDispatch(bookingRequestId);
    }

    /**
     * Genera la URL de WhatsApp con el mensaje de confirmación de cita pre-armado.
     * El admin hace click en este enlace después de aprobar para enviar el mensaje.
     */
    public String buildWhatsAppConfirmationUrl(Appointment appointment) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(
                "EEEE dd 'de' MMMM 'de' yyyy", resolveAppLocale());
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        String msg = "\u00a1Hola " + appointment.getCustomer().getFullName() + "! \uD83D\uDC30\n"
                + "Tu cita en *Bunny Cure* ha sido *confirmada* \u2705\n\n"
                + "\uD83D\uDC85 *Servicio:* " + appointment.getService().getName() + "\n"
                + "\uD83D\uDCC5 *Fecha:* "
                + appointment.getAppointmentDate().format(dateFmt) + "\n"
                + "\u23F0 *Hora:* "
                + appointment.getAppointmentTime().format(timeFmt) + "\n\n"
                + "Te esperamos. Si necesitas cancelar av\u00edsanos con anticipaci\u00f3n \u2764\uFE0F";

        String phone = appointment.getCustomer().getPhone().replaceAll("[^0-9]", "");
        return "https://wa.me/" + phone
                + "?text=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);
    }

    // Keeps existing call sites compiling until a real WhatsApp sender is implemented.
    public void sendWhatsAppConfirmation(Appointment appointment) {
        String url = buildWhatsAppConfirmationUrl(appointment);
        log.info("[WHATSAPP-URL] {}", url);
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private void sendEmail(String to, String subject, String html) throws Exception {
        sendHtml(to, subject, html);
    }

    private void send(Appointment appointment, String template, String subject) {
        try {
            if (appointment.getCustomer() == null
                    || appointment.getCustomer().getEmail() == null
                    || appointment.getCustomer().getEmail().isBlank()) {
                log.info("[MAIL-SKIP] {} (cliente sin email)", subject);
                return;
            }

            String fechaFormateada = appointment.getAppointmentDate()
                    .format(longDateFormatter());

            Context ctx = new Context(resolveAppLocale());
            ctx.setVariable("appointment",     appointment);
            ctx.setVariable("customer",        appointment.getCustomer());
            ctx.setVariable("fechaFormateada", fechaFormateada);

            String html = templateEngine.process(template, ctx);
            sendHtml(appointment.getCustomer().getEmail(), subject, html);
            log.info("[MAIL-OK] {} → {}", subject, appointment.getCustomer().getEmail());

        } catch (Exception e) {
            log.error("[MAIL-ERROR] No se pudo enviar a {}: {}",
                    appointment.getCustomer().getEmail(), e.getMessage());
        }
    }

    private void sendHtml(String to, String subject, String html) throws Exception {
        if (!isMailEnabled()) {
            log.info("[MAIL-SKIP] {} (mail deshabilitado globalmente)", subject);
            return;
        }

        int maxRetries = 3;
        int delayMs = 1000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(mailFrom, "BunnyCure 💅");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(msg);
                log.info("[MAIL-OK] {} → {}", subject, to);
                return; // Success, exit
            } catch (Exception e) {
                log.warn("[MAIL-RETRY] Intento {} de {} fallido para {}: {}", 
                        attempt, maxRetries, to, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs);
                        delayMs *= 2; // exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    log.error("[MAIL-FAILED] Todos los intentos fallaron para {}: {}", to, e.getMessage());
                    throw e; // Re-throw after all retries exhausted
                }
            }
        }
    }

    // ── Recordatorios de citas ───────────────────────────────────────────────

    @Async
    public void sendReminder(String email, String firstName, String serviceName, 
                            String appointmentTime, String appointmentDate,
                            Appointment appointment) {
        if (!isMailEnabled()) {
            log.info("[MAIL-SKIP] Recordatorio para {} (mail deshabilitado)", email);
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("serviceName", serviceName);
            context.setVariable("appointmentTime", appointmentTime);
            context.setVariable("appointmentDate", appointmentDate);
            context.setVariable("whatsappNumber", whatsappNumber);

            String html = templateEngine.process("mail/reminder", context);
            sendEmail(email, "🐰 Recordatorio de tu cita - Bunny Cure", html);

        } catch (Exception e) {
            log.error("[MAIL-ERROR] Recordatorio → {}: {}", email, e.getMessage());
        }
    }

    /**
     * Envía recordatorios automáticos para citas próximas
     * @param appointment La cita para la que enviar el recordatorio
     * @param type El tipo de recordatorio: "tomorrow", "2hours" o manual/custom
     */
    @Async
    public void sendReminderNotification(Appointment appointment, String type) {
        if (appointment == null || appointment.getCustomer() == null) {
            return;
        }

        try {
            Customer customer = appointment.getCustomer();
            cl.bunnycure.domain.enums.NotificationPreference pref = customer.getNotificationPreference();
            
            String customerName = customer.getFirstName();
            String serviceName = appointment.getService().getName();
            String email = customer.getEmail();
            String phone = customer.getPhone();
            String appointmentTime = appointment.getAppointmentTime() != null ? 
                    appointment.getAppointmentTime().toString() : "";
            String appointmentDate = appointment.getAppointmentDate().toString();

            String subject, templateName;

            if ("tomorrow".equals(type)) {
                subject = "🐰 Tu cita es mañana - Bunny Cure";
                templateName = "mail/reminder-tomorrow";
            } else if ("2hours".equals(type)) {
                subject = "⏰ Tu cita es en 2 horas - Bunny Cure";
                templateName = "mail/reminder-2hours";
            } else {
                // Soporta envío manual desde panel admin.
                subject = "🐰 Recordatorio de tu cita - Bunny Cure";
                templateName = "mail/reminder";
            }

            // Enviar email solo si la preferencia lo permite
            if (isMailEnabled() && pref != null && pref.allowsEmail()) {
                try {
                    Context context = new Context();
                    context.setVariable("firstName", customerName);
                    context.setVariable("serviceName", serviceName);
                    context.setVariable("appointmentTime", appointmentTime);
                    context.setVariable("appointmentDate", appointmentDate);
                    String html = templateEngine.process(templateName, context);
                    sendEmail(email, subject, html);
                } catch (Exception e) {
                    log.warn("[MAIL-WARN] No se pudo enviar email recordatorio: {}", e.getMessage());
                }
            }

            // Enviar WhatsApp solo si tiene teléfono Y la preferencia lo permite
            if (pref != null && pref.allowsWhatsApp() && phone != null && !phone.isEmpty()) {
                try {
                    whatsAppService.sendRecordatorioCitaTemplate(appointment);
                } catch (Exception e) {
                    log.warn("[WHATSAPP-WARN] No se pudo enviar WhatsApp recordatorio: {}", e.getMessage());
                }
            }

            // Notificación push para admin/dueña (PWA)
            webPushNotificationService.sendAdminAppointmentReminder(appointment, type);

        } catch (Exception e) {
            log.error("[REMINDER-ERROR] Error al enviar recordatorio para cita ID {}: {}", 
                appointment.getId(), e.getMessage());
        }
    }

    private Locale resolveAppLocale() {
        try {
            return appSettingsService.getAppJavaLocale();
        } catch (Exception ex) {
            log.warn("[MAIL] No se pudo resolver app.locale, usando fallback es_CL", ex);
            return new Locale("es", "CL");
        }
    }

    private DateTimeFormatter longDateFormatter() {
        return DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy", resolveAppLocale());
    }

    private boolean isMailEnabled() {
        return appSettingsService.isMailEnabled(mailEnabled);
    }

}
