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

    @Value("${bunnycure.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${bunnycure.mail.from:noreply@bunnycure.cl}")
    private String mailFrom;

    @Value("${bunnycure.whatsapp.number:56964499995}")
    private String whatsappNumber;

    public NotificationService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
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
        sendConfirmation(appointment);
    }

    @Async
    public void sendCancellationNotice(Appointment appointment) {
        if (!mailEnabled) {
            log.info("[MAIL-SKIP] Cancelación para {} (mail deshabilitado)",
                    appointment.getCustomer().getEmail());
            return;
        }
        send(appointment, "mail/cancellation",
                "Tu cita ha sido cancelada – BunnyCure");
    }

    // ── Solicitudes de reserva ───────────────────────────────────────────────

    /**
     * Email a la clienta confirmando que su solicitud fue recibida y está en revisión.
     */
    @Async
    public void sendBookingRequestReceived(BookingRequest request) {
        if (!mailEnabled) {
            log.info("[MAIL-SKIP] Recepción solicitud para {} (mail deshabilitado)",
                    request.getEmail());
            return;
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) return;

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

    /**
     * Email a la clienta informando que su solicitud fue rechazada.
     */
    @Async
    public void sendBookingRequestRejected(BookingRequest request) {
        if (!mailEnabled) {
            log.info("[MAIL-SKIP] Rechazo solicitud para {} (mail deshabilitado)",
                    request.getEmail());
            return;
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) return;

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
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(mailFrom, "BunnyCure \uD83D\uDC85");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("[MAIL-OK] {} → {}", subject, to);
    }
}