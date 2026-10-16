package cl.bunnycure.service.billing;

import cl.bunnycure.domain.enums.BillingCycle;
import cl.bunnycure.domain.enums.SubscriptionPlan;
import cl.bunnycure.domain.enums.SubscriptionStatus;
import cl.bunnycure.domain.model.EmailVerificationToken;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.EmailVerificationTokenRepository;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.ConflictException;
import cl.bunnycure.exception.ValidationException;
import cl.bunnycure.service.PlatformMailService;
import cl.bunnycure.service.TenantExecutionService;
import cl.bunnycure.web.dto.billing.BillingDtos.SignupRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.SignupResponse;
import cl.bunnycure.web.dto.billing.BillingDtos.VerifyEmailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Registro autoservicio de salones.
 *
 * 1. register: crea el salón (PENDING_VERIFICATION) y su administradora, y envía el enlace
 *    de verificación. El login queda bloqueado hasta verificar.
 * 2. verify: valida el token y comienza la prueba gratuita (TRIALING).
 *
 * Sin @Transactional a propósito: Hibernate fija el tenant de una sesión al abrirla, así que
 * el salón y su administradora se guardan en sesiones separadas (la usuaria, en el contexto del
 * salón nuevo). Si un paso falla después de crear el salón, se elimina (compensación).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "app", "admin", "api", "demo", "registro", "login", "soporte", "ayuda", "help",
            "mail", "static", "assets", "status", "blog", "terminos", "privacidad", "suscripcion");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final TenantExecutionService tenantExecutionService;
    private final SubscriptionService subscriptionService;
    private final PlatformMailService platformMailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${bunnycure.signup.terms-version:2026-10}")
    private String termsVersion;

    @Value("${bunnycure.signup.verification-hours:48}")
    private int verificationHours;

    @Value("${app.frontend.base-url:https://app.bunnycure.cl}")
    private String frontendBaseUrl;

    @Value("${bunnycure.scheduler.timezone:America/Santiago}")
    private String timezone;

    public SignupResponse register(SignupRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Honeypot: los bots completan el campo oculto. Respuesta idéntica sin crear nada.
        if (request.website() != null && !request.website().isBlank()) {
            log.warn("[SIGNUP] Registro descartado por honeypot desde IP {}", clientIp);
            return new SignupResponse(null, email, checkInboxMessage());
        }

        validatePassword(request.password());
        if (userRepository.existsByUsernameOrEmailGlobally(email)) {
            throw new ConflictException("Ya existe una cuenta con este email. Inicia sesión o recupera tu contraseña.");
        }
        String slug = resolveSlug(request.slug(), request.salonName());

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(request.salonName().trim())
                .slug(slug)
                .email(email)
                .phone(request.phone() != null ? request.phone().trim() : null)
                .active(true)
                .planTier(SubscriptionPlan.fromString(request.plan()).name())
                .subscriptionStatus(SubscriptionStatus.PENDING_VERIFICATION)
                .billingCycle(BillingCycle.MONTHLY)
                .termsVersion(termsVersion)
                .termsAcceptedAt(LocalDateTime.now())
                .termsAcceptedIp(clientIp)
                .build());

        try {
            User admin = tenantExecutionService.executeInTenantTransaction(tenant.getId(), () -> userRepository.save(User.builder()
                    .tenantId(tenant.getId())
                    .username(email)
                    .email(email)
                    .fullName(request.ownerName().trim())
                    .password(passwordEncoder.encode(request.password()))
                    .role("ADMIN")
                    .enabled(true)
                    .passwordChangeRequired(false)
                    .build()));
            sendVerification(tenant, admin);
        } catch (RuntimeException e) {
            log.error("[SIGNUP] Falló el alta del salón '{}'; se revierte: {}", slug, e.getMessage());
            tenantRepository.deleteById(tenant.getId());
            throw e;
        }

        log.info("[SIGNUP] Salón '{}' (id={}) registrado; pendiente de verificación", slug, tenant.getId());
        return new SignupResponse(slug, email, checkInboxMessage());
    }

    /** Reenvía el enlace si hay un registro pendiente. Respuesta genérica para no revelar cuentas. */
    public void resendVerification(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        userRepository.findTenantIdByUsername(email)
                .flatMap(tenantRepository::findById)
                .filter(t -> t.getSubscriptionStatus() == SubscriptionStatus.PENDING_VERIFICATION)
                .ifPresent(tenant -> tenantExecutionService.executeInTenantTransaction(tenant.getId(), () -> {
                    userRepository.findByUsername(email).ifPresent(admin -> sendVerification(tenant, admin));
                    return null;
                }));
    }

    public VerifyEmailResponse verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new ValidationException("El enlace de verificación no es válido."));
        Tenant tenant = subscriptionService.findTenant(token.getTenantId());

        if (token.getUsedAt() != null && tenant.getSubscriptionStatus() != SubscriptionStatus.PENDING_VERIFICATION) {
            return response(tenant); // doble clic en el enlace: ya verificado
        }
        if (!token.isUsable(LocalDateTime.now())) {
            throw new ValidationException("El enlace de verificación expiró. Solicita uno nuevo.");
        }

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        if (tenant.getSubscriptionStatus() == SubscriptionStatus.PENDING_VERIFICATION) {
            subscriptionService.startTrial(tenant, LocalDate.now(ZoneId.of(timezone)));
            log.info("[SIGNUP] Salón '{}' verificado; prueba hasta {}", tenant.getSlug(), tenant.getTrialEndsOn());
        }
        return response(tenant);
    }

    public boolean isSlugAvailable(String slug) {
        String s = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        return s.matches("^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$")
                && !RESERVED_SLUGS.contains(s)
                && !tenantRepository.existsBySlugIgnoreCase(s);
    }

    // ── Internos ──────────────────────────────────────────────────────────

    private void sendVerification(Tenant tenant, User admin) {
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash(sha256(rawToken))
                .tenantId(tenant.getId())
                .userId(admin.getId())
                .expiresAt(LocalDateTime.now().plusHours(verificationHours))
                .build());

        String link = frontendBaseUrl + "/registro/verificar?token=" + rawToken + "&salon=" + tenant.getSlug();
        platformMailService.send(admin.getEmail(),
                "Confirma tu email",
                "¡Bienvenida, " + admin.getFullName() + "!",
                "Gracias por registrar " + tenant.getName() + " en " + platformMailService.getProductName() + ".\n\n"
                        + "Confirma tu email para activar tu salón y comenzar tu prueba gratuita. "
                        + "El enlace es válido por " + verificationHours + " horas.\n\n"
                        + "Si no fuiste tú, ignora este correo.",
                "Confirmar mi email", link);
    }

    private VerifyEmailResponse response(Tenant tenant) {
        return new VerifyEmailResponse(tenant.getSlug(), frontendBaseUrl + "/login?salon=" + tenant.getSlug(), tenant.getTrialEndsOn());
    }

    private String resolveSlug(String requested, String salonName) {
        if (requested != null && !requested.isBlank()) {
            String slug = requested.trim().toLowerCase(Locale.ROOT);
            if (!isSlugAvailable(slug)) {
                throw new ConflictException("La dirección '" + slug + "' no está disponible. Prueba con otra.");
            }
            return slug;
        }
        String base = slugify(salonName);
        if (base.length() < 3) {
            base = (base + "-salon").replaceAll("^-+", "");
        }
        String candidate = base;
        for (int i = 2; !isSlugAvailable(candidate); i++) {
            candidate = base.substring(0, Math.min(base.length(), 36)) + "-" + i;
        }
        return candidate;
    }

    static String slugify(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.length() > 40 ? normalized.substring(0, 40).replaceAll("-+$", "") : normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 10
                || !password.chars().anyMatch(Character::isLetter)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new ValidationException("La contraseña debe tener al menos 10 caracteres e incluir letras y números.");
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String checkInboxMessage() {
        return "Te enviamos un correo para confirmar tu email. Revisa tu bandeja de entrada (y la carpeta de spam).";
    }
}
