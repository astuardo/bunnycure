package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
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

    public NotificationService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendConfirmation(Appointment appointment) {
        if (!mailEnabled) {
            log.info("[MAIL-SKIP] Confirmación para {} (mail deshabilitado)",
                    appointment.getCustomer().getEmail());
            return;
        }
        // Confirmación
        send(appointment, "mail/confirmation",
                "💅 Tu cita está confirmada – BunnyCure");
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

    private void send(Appointment appointment, String template, String subject) {
        try {
            // ✅ Formatear fecha en Java, evita el problema de escape en SpEL
            String fechaFormateada = appointment.getAppointmentDate()
                    .format(DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy",
                            new Locale("es", "CL")));

            Context ctx = new Context(new Locale("es", "CL"));
            ctx.setVariable("appointment",      appointment);
            ctx.setVariable("customer",         appointment.getCustomer());
            ctx.setVariable("fechaFormateada",  fechaFormateada);  // ✅ nueva variable

            String html = templateEngine.process(template, ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom, "BunnyCure \uD83D\uDC85");
            helper.setTo(appointment.getCustomer().getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(msg);
            log.info("[MAIL-OK] {} → {}", subject, appointment.getCustomer().getEmail());

        } catch (Exception e) {
            log.error("[MAIL-ERROR] No se pudo enviar a {}: {}",
                    appointment.getCustomer().getEmail(), e.getMessage());
        }
    }
}