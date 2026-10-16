package cl.bunnycure.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Correos que envía la PLATAFORMA a los salones (verificación de registro, avisos de cobro).
 * No dependen de la configuración de correo de cada salón (AppSettings "mail.enabled"),
 * solo del interruptor global bunnycure.mail.enabled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformMailService {

    private final JavaMailSender mailSender;

    @Value("${bunnycure.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${bunnycure.mail.from:noreply@bunnycure.cl}")
    private String mailFrom;

    @Value("${bunnycure.product.name:BunnyCure}")
    private String productName;

    public String getProductName() {
        return productName;
    }

    /**
     * Envía un correo simple con título, párrafos y un botón opcional.
     * Los textos se escapan como HTML; devuelve false si no se pudo enviar.
     */
    public boolean send(String to, String subject, String title, String bodyText, String buttonLabel, String buttonUrl) {
        if (!mailEnabled) {
            log.info("[PLATFORM-MAIL] Correo deshabilitado; no se envía '{}' a {}", subject, to);
            return false;
        }
        if (to == null || to.isBlank()) {
            log.warn("[PLATFORM-MAIL] Sin destinatario para '{}'", subject);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, productName);
            helper.setTo(to);
            helper.setSubject(subject + " - " + productName);
            helper.setText(html(title, bodyText, buttonLabel, buttonUrl), true);
            mailSender.send(message);
            log.info("[PLATFORM-MAIL] '{}' enviado a {}", subject, to);
            return true;
        } catch (Exception e) {
            log.error("[PLATFORM-MAIL] Error enviando '{}' a {}: {}", subject, to, e.getMessage());
            return false;
        }
    }

    private String html(String title, String bodyText, String buttonLabel, String buttonUrl) {
        StringBuilder paragraphs = new StringBuilder();
        for (String paragraph : bodyText.split("\n\n")) {
            paragraphs.append("<p>").append(HtmlUtils.htmlEscape(paragraph).replace("\n", "<br>")).append("</p>");
        }
        String button = buttonLabel == null || buttonUrl == null ? "" : """
                <p><a href="%s" style="display:inline-block;padding:10px 16px;background:#8C4E3F;color:#fff;text-decoration:none;border-radius:8px;">%s</a></p>
                """.formatted(HtmlUtils.htmlEscape(buttonUrl), HtmlUtils.htmlEscape(buttonLabel));
        return """
                <html><body style="font-family:Arial,sans-serif;color:#1f2937;max-width:560px;">
                <h2 style="margin-bottom:8px;">%s</h2>%s%s
                <p style="color:#6b7280;font-size:12px;margin-top:24px;">%s</p>
                </body></html>
                """.formatted(HtmlUtils.htmlEscape(title), paragraphs, button, HtmlUtils.htmlEscape(productName));
    }
}
