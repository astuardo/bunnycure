package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final WhatsAppService whatsAppService;

    @Value("${bunnycure.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${bunnycure.mail.from:noreply@bunnycure.cl}")
    private String mailFrom;

    @Value("${bunnycure.whatsapp.number:56964499995}")
    private String whatsappNumber;

    public NotificationService(JavaMailSender mailSender, TemplateEngine templateEngine, WhatsAppService whatsAppService) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
        this.whatsAppService = whatsAppService;
    }

    // ── Citas ────────────────────────────────────────────────────────────────

    @Async
    public void sendConfirmation(Appointment appointment) {
        if (!mailEnabled) {
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
        // Enviar email
        sendConfirmation(appointment);
        
        // Enviar WhatsApp con template
        if (appointment != null && appointment.getCustomer() != null && appointment.getCustomer().getPhone() != null) {
            log.info("[NOTIFICATION] Enviando confirmación de cita por WhatsApp a {}", 
                    appointment.getCustomer().getPhone());
            whatsAppService.sendCitaConfirmadaTemplate(appointment);
        }
    }

    @Async
    public void sendCancellationNotice(Appointment appointment) {
        // Enviar email si está configurado
        if (mailEnabled) {
            send(appointment, "mail/cancellation",
                    "Tu cita ha sido cancelada – BunnyCure");
        }
        
        // Enviar WhatsApp con template
        if (appointment != null && appointment.getCustomer() != null && appointment.getCustomer().getPhone() != null) {
            log.info("[NOTIFICATION] Enviando notificación de cancelación por WhatsApp a {}", 
                    appointment.getCustomer().getPhone());
            whatsAppService.sendCancelacionCitaTemplate(appointment);
        }
    }

    // ── Solicitudes de reserva ───────────────────────────────────────────────

    /**
     * Email a la clienta confirmando que su solicitud fue recibida y está en revisión.
     */
    @Async
    public void sendBookingRequestReceived(BookingRequest request) {
        // Enviar email si está configurado
        if (mailEnabled && request != null && request.getEmail() != null && !request.getEmail().isBlank()) {
            try {
                String fechaFormateada = request.getPreferredDate()
                        .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy",
                                new Locale("es", "CL")));

                Context ctx = new Context(new Locale("es", "CL"));
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
        
        // Enviar WhatsApp con template
        if (request != null && request.getPhone() != null && !request.getPhone().isBlank()) {
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
        // Enviar email si está configurado
        if (mailEnabled && request != null && request.getEmail() != null && !request.getEmail().isBlank()) {
            try {
                Context ctx = new Context(new Locale("es", "CL"));
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
        
        // Enviar WhatsApp con template
        if (request != null && request.getPhone() != null && !request.getPhone().isBlank()) {
            log.info("[NOTIFICATION] Enviando rechazo de solicitud por WhatsApp a {}", 
                    request.getPhone());
            whatsAppService.sendSolicitudRechazadaTemplate(request);
        }
    }

    /**
     * Genera la URL de WhatsApp con el mensaje de confirmación de cita pre-armado.
     * El admin hace click en este enlace después de aprobar para enviar el mensaje.
     */
    public String buildWhatsAppConfirmationUrl(Appointment appointment) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(
                "EEEE dd 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
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
            String fechaFormateada = appointment.getAppointmentDate()
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy",
                            new Locale("es", "CL")));

            Context ctx = new Context(new Locale("es", "CL"));
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
        if (!mailEnabled) {
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
     * @param type El tipo de recordatorio: "tomorrow" (mañana) o "2hours" (en 2 horas)
     */
    @Async
    public void sendReminderNotification(Appointment appointment, String type) {
        if (appointment == null || appointment.getCustomer() == null) {
            return;
        }

        try {
            String customerName = appointment.getCustomer().getFirstName();
            String serviceName = appointment.getService().getName();
            String email = appointment.getCustomer().getEmail();
            String phone = appointment.getCustomer().getPhone();
            String appointmentTime = appointment.getAppointmentTime() != null ? 
                    appointment.getAppointmentTime().toString() : "";
            String appointmentDate = appointment.getAppointmentDate().toString();

            String subject, templateName;
            String message;

            if ("tomorrow".equals(type)) {
                subject = "🐰 Tu cita es mañana - Bunny Cure";
                templateName = "mail/reminder-tomorrow";
                message = String.format("Hola %s, tu cita para %s está programada para mañana a las %s", 
                    customerName, serviceName, appointmentTime);
            } else if ("2hours".equals(type)) {
                subject = "⏰ Tu cita es en 2 horas - Bunny Cure";
                templateName = "mail/reminder-2hours";
                message = String.format("¡Hola %s! Tu cita para %s es en 2 horas a las %s. ¡No olvides!", 
                    customerName, serviceName, appointmentTime);
            } else {
                return;
            }

            // Enviar email
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

            // Enviar WhatsApp con template
            if (phone != null && !phone.isEmpty()) {
                try {
                    whatsAppService.sendRecordatorioCitaTemplate(appointment);
                } catch (Exception e) {
                    log.warn("[WHATSAPP-WARN] No se pudo enviar WhatsApp recordatorio: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[REMINDER-ERROR] Error al enviar recordatorio para cita ID {}: {}", 
                appointment.getId(), e.getMessage());
        }
    }

    /**
     * Envía un mensaje por WhatsApp (placeholder para integración futura)
     */
    private void sendWhatsAppMessage(String phoneNumber, String message) {
        // TODO: Integrar con servicio WhatsApp (Twilio, MessageBird, etc.)
        log.info("[WHATSAPP] Mensaje a {}: {}", phoneNumber, message);
    }
}