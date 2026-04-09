package cl.bunnycure.service;

import cl.bunnycure.domain.model.PasswordResetToken;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.PasswordResetTokenRepository;
import cl.bunnycure.domain.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final AppSettingsService appSettingsService;

    @Value("${bunnycure.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${bunnycure.mail.from:noreply@bunnycure.cl}")
    private String mailFrom;

    @Transactional
    public void requestReset(String email, String appBaseUrl) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            log.info("[RESET-PASSWORD] Solicitud para email no registrado: {}", email);
            return;
        }

        User user = userOpt.get();
        tokenRepository.markAllActiveAsUsedByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build();

        tokenRepository.save(resetToken);
        sendResetEmail(user, token, appBaseUrl);
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return tokenRepository.findValidToken(token, LocalDateTime.now()).isPresent();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findValidToken(token, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("El enlace de recuperación es inválido o expiró."));

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    private void sendResetEmail(User user, String token, String appBaseUrl) {
        if (!appSettingsService.isMailEnabled(mailEnabled)) {
            log.info("[RESET-PASSWORD] Mail deshabilitado, no se envía correo a {}", user.getEmail());
            return;
        }

        String resetLink = appBaseUrl + "/reset-password?token=" + token;

        String html = """
                <html>
                <body style="font-family:Arial,sans-serif;color:#1f2937;">
                  <h2 style="margin-bottom:8px;">Recuperar contraseña</h2>
                  <p>Hola %s,</p>
                  <p>Recibimos una solicitud para restablecer tu contraseña en BunnyCure.</p>
                  <p>
                    <a href="%s" style="display:inline-block;padding:10px 16px;background:#8B5CF6;color:#fff;text-decoration:none;border-radius:8px;">
                      Restablecer contraseña
                    </a>
                  </p>
                  <p>Este enlace expira en 30 minutos.</p>
                  <p>Si no solicitaste este cambio, puedes ignorar este correo.</p>
                </body>
                </html>
                """.formatted(user.getFullName() != null ? user.getFullName() : user.getUsername(), resetLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom, "BunnyCure");
            helper.setTo(user.getEmail());
            helper.setSubject("Recuperación de contraseña - BunnyCure");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[RESET-PASSWORD] Correo de recuperación enviado a {}", user.getEmail());
        } catch (Exception e) {
            log.error("[RESET-PASSWORD] Error enviando correo de recuperación", e);
        }
    }
}
