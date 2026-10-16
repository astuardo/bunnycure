package cl.bunnycure.service.billing;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.enums.BillingCycle;
import cl.bunnycure.domain.enums.PaymentProvider;
import cl.bunnycure.domain.enums.PlanFeature;
import cl.bunnycure.domain.enums.SubscriptionPlan;
import cl.bunnycure.domain.enums.SubscriptionStatus;
import cl.bunnycure.domain.model.SubscriptionPayment;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.SubscriptionPaymentRepository;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.exception.ValidationException;
import cl.bunnycure.service.PlatformMailService;
import cl.bunnycure.service.TenantExecutionService;
import cl.bunnycure.web.dto.billing.BillingDtos.FeatureDto;
import cl.bunnycure.web.dto.billing.BillingDtos.ManualPaymentRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.PlanDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionPaymentDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionStatusDto;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Suscripciones de los salones a la plataforma.
 *
 * Ciclo: PENDING_VERIFICATION → TRIALING → ACTIVE ⇄ PAST_DUE → SUSPENDED.
 * - Un pago aprobado (manual o pasarela) activa y extiende el período pagado.
 * - Vencido el período (o la prueba), el salón pasa a PAST_DUE y sigue operando durante
 *   los días de gracia; después queda SUSPENDED (ver PlanAccessInterceptor).
 * - current_period_end = null significa "sin vencimiento" (salones existentes / cortesía).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TenantRepository tenantRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final TenantExecutionService tenantExecutionService;
    private final PlatformMailService platformMailService;

    @Value("${bunnycure.billing.trial-days:14}")
    private int trialDays;

    @Value("${bunnycure.billing.grace-days:7}")
    private int graceDays;

    @Value("${bunnycure.billing.manual.instructions:Para pagar tu suscripción escríbenos a contacto@bunnycure.cl y te enviaremos los datos de transferencia.}")
    private String manualPaymentInstructions;

    @Value("${bunnycure.billing.mercadopago.enabled:false}")
    private boolean mercadoPagoEnabled;

    @Value("${app.frontend.base-url:https://app.bunnycure.cl}")
    private String frontendBaseUrl;

    @Value("${bunnycure.scheduler.timezone:America/Santiago}")
    private String timezone;

    // ── Catálogo ──────────────────────────────────────────────────────────

    public List<PlanDto> listPlans() {
        return Arrays.stream(SubscriptionPlan.values())
                .map(plan -> new PlanDto(
                        plan.name(),
                        plan.getDisplayName(),
                        plan.getMonthlyPriceClp(),
                        plan.priceFor(BillingCycle.ANNUAL),
                        SubscriptionPlan.ANNUAL_DISCOUNT_PERCENT,
                        plan.getMaxStaffSeats(),
                        featuresOf(plan)))
                .toList();
    }

    private static List<FeatureDto> featuresOf(SubscriptionPlan plan) {
        return Arrays.stream(PlanFeature.values())
                .map(f -> new FeatureDto(f.name(), f.getDisplayName(), plan.includes(f)))
                .toList();
    }

    // ── Estado del salón actual ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubscriptionStatusDto currentStatus() {
        Tenant tenant = findTenant(TenantContext.getCurrentTenantId());
        SubscriptionPlan plan = tenant.getSubscriptionPlan();
        LocalDate due = dueDate(tenant);
        return new SubscriptionStatusDto(
                plan.name(),
                plan.getDisplayName(),
                tenant.getSubscriptionStatus().name(),
                tenant.getBillingCycle().name(),
                plan.priceFor(tenant.getBillingCycle()),
                tenant.getTrialEndsOn(),
                tenant.getCurrentPeriodEnd(),
                tenant.getSubscriptionStatus() == SubscriptionStatus.PAST_DUE && due != null ? due.plusDays(graceDays) : null,
                plan.getMaxStaffSeats(),
                userRepository.countEnabledStaffSeats(),
                featuresOf(plan),
                tenant.getPaymentProvider().name(),
                mercadoPagoEnabled,
                manualPaymentInstructions);
    }

    public List<SubscriptionPaymentDto> paymentsOf(Long tenantId) {
        return tenantExecutionService.executeInTenantTransaction(tenantId, () ->
                paymentRepository.findAllByOrderByPaidAtDesc().stream().map(SubscriptionService::toDto).toList());
    }

    // ── Operaciones de plataforma ─────────────────────────────────────────

    /** Registra un pago manual y extiende el período pagado. */
    public SubscriptionPaymentDto registerManualPayment(Long tenantId, ManualPaymentRequest request, String recordedBy, LocalDate today) {
        Tenant tenant = findTenant(tenantId);
        SubscriptionPlan plan = request.plan() != null && !request.plan().isBlank()
                ? SubscriptionPlan.fromString(request.plan())
                : tenant.getSubscriptionPlan();
        long amount = request.amountClp() != null ? request.amountClp() : plan.priceFor(request.billingCycle());
        SubscriptionPayment payment = applyApprovedPayment(tenant, plan, request.billingCycle(), amount,
                PaymentProvider.MANUAL, null, request.reference(), recordedBy, today);
        log.info("[BILLING] Pago manual registrado: salón={} plan={} ciclo={} monto={} hasta={} por={}",
                tenantId, plan, request.billingCycle(), amount, payment.getPeriodEnd(), recordedBy);
        return toDto(payment);
    }

    /**
     * Aplica un pago aprobado: activa la suscripción y extiende el período. Si el período
     * vigente aún no vence, el nuevo comienza al día siguiente de su término (pago anticipado).
     *
     * Sin @Transactional: el pago (@TenantId) se guarda en una transacción propia del salón
     * (executeInTenantTransaction); dentro de una transacción ajena quedaría en otro salón.
     */
    public SubscriptionPayment applyApprovedPayment(Tenant tenant, SubscriptionPlan plan, BillingCycle cycle, long amountClp,
                                                    PaymentProvider provider, String externalPaymentId, String reference,
                                                    String recordedBy, LocalDate today) {
        LocalDate currentEnd = tenant.getCurrentPeriodEnd();
        LocalDate periodStart = currentEnd != null && !currentEnd.isBefore(today) ? currentEnd.plusDays(1) : today;
        LocalDate periodEnd = periodStart.plusMonths(cycle.getMonths()).minusDays(1);

        tenant.setPlanTier(plan.name());
        tenant.setBillingCycle(cycle);
        tenant.setPaymentProvider(provider);
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        tenant.setCurrentPeriodEnd(periodEnd);
        tenant.setSuspendedAt(null);
        tenant.setLastBillingNotice(null);
        tenantRepository.save(tenant);

        return tenantExecutionService.executeInTenantTransaction(tenant.getId(), () -> paymentRepository.save(SubscriptionPayment.builder()
                .tenantId(tenant.getId())
                .amountClp(amountClp)
                .planTier(plan.name())
                .billingCycle(cycle)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .provider(provider)
                .externalPaymentId(externalPaymentId)
                .reference(reference)
                .recordedBy(recordedBy)
                .paidAt(LocalDateTime.now())
                .build()));
    }

    /** Ajuste manual desde la consola de plataforma (cambio de plan, estado o fechas). */
    @Transactional
    public Tenant updateSubscription(Long tenantId, SubscriptionUpdateRequest request) {
        Tenant tenant = findTenant(tenantId);
        if (request.plan() != null && !request.plan().isBlank()) {
            tenant.setPlanTier(SubscriptionPlan.fromString(request.plan()).name());
        }
        if (request.billingCycle() != null) {
            tenant.setBillingCycle(request.billingCycle());
        }
        if (request.status() != null && !request.status().isBlank()) {
            SubscriptionStatus status;
            try {
                status = SubscriptionStatus.valueOf(request.status().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Estado de suscripción inválido: " + request.status());
            }
            tenant.setSubscriptionStatus(status);
            tenant.setSuspendedAt(status == SubscriptionStatus.SUSPENDED ? LocalDateTime.now() : null);
        }
        if (request.currentPeriodEnd() != null) {
            tenant.setCurrentPeriodEnd(request.currentPeriodEnd());
        }
        if (request.trialEndsOn() != null) {
            tenant.setTrialEndsOn(request.trialEndsOn());
        }
        tenant.setLastBillingNotice(null);
        log.info("[BILLING] Suscripción ajustada: salón={} plan={} estado={} vence={}",
                tenantId, tenant.getPlanTier(), tenant.getSubscriptionStatus(), tenant.getCurrentPeriodEnd());
        return tenantRepository.save(tenant);
    }

    /** Comienza la prueba gratuita (registro autoservicio verificado). */
    @Transactional
    public void startTrial(Tenant tenant, LocalDate today) {
        tenant.setSubscriptionStatus(SubscriptionStatus.TRIALING);
        tenant.setTrialEndsOn(today.plusDays(trialDays));
        tenant.setCurrentPeriodEnd(null);
        tenantRepository.save(tenant);
    }

    // ── Ciclo diario de vencimientos ──────────────────────────────────────

    @Scheduled(cron = "${bunnycure.billing.lifecycle-cron:0 0 9 * * *}", zone = "${bunnycure.scheduler.timezone:America/Santiago}")
    public void runDailyLifecycle() {
        runDailyLifecycle(LocalDate.now(ZoneId.of(timezone)));
    }

    /** Evalúa todos los salones para la fecha dada (testeable con fechas fijas). */
    @Transactional
    public void runDailyLifecycle(LocalDate today) {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                evaluate(tenant, today);
            } catch (Exception e) {
                log.error("[BILLING] Error evaluando la suscripción del salón {}: {}", tenant.getId(), e.getMessage(), e);
            }
        }
    }

    private void evaluate(Tenant tenant, LocalDate today) {
        SubscriptionStatus status = tenant.getSubscriptionStatus();
        LocalDate due = dueDate(tenant);
        if (due == null) {
            return; // sin vencimiento
        }

        switch (status) {
            case TRIALING, ACTIVE -> {
                if (today.isAfter(due)) {
                    tenant.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
                    tenantRepository.save(tenant);
                    notifyOnce(tenant, "PAST_DUE", due,
                            "Tu suscripción venció",
                            "El período de tu salón " + tenant.getName() + " terminó el " + due.format(DATE) + ".\n\n"
                                    + "Tienes " + graceDays + " días para regularizar el pago antes de que se suspenda el acceso.");
                    log.info("[BILLING] Salón {} pasa a PAST_DUE (venció {})", tenant.getId(), due);
                } else {
                    long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, due);
                    if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
                        String what = status == SubscriptionStatus.TRIALING ? "tu prueba gratuita" : "tu suscripción";
                        notifyOnce(tenant, "DUE_IN_" + daysLeft, due,
                                "Recordatorio de pago",
                                "Hola, " + what + " de " + tenant.getName() + " vence el " + due.format(DATE)
                                        + " (en " + daysLeft + (daysLeft == 1 ? " día" : " días") + ").\n\n"
                                        + "Renueva a tiempo para que tu equipo y tus clientas no pierdan el acceso.");
                    }
                }
            }
            case PAST_DUE -> {
                if (today.isAfter(due.plusDays(graceDays))) {
                    tenant.setSubscriptionStatus(SubscriptionStatus.SUSPENDED);
                    tenant.setSuspendedAt(LocalDateTime.now());
                    tenantRepository.save(tenant);
                    notifyOnce(tenant, "SUSPENDED", due,
                            "Tu salón fue suspendido",
                            "No registramos el pago de " + tenant.getName() + " y terminó el período de gracia.\n\n"
                                    + "Tus datos están a salvo. Regulariza el pago para reactivar el acceso de inmediato.");
                    log.info("[BILLING] Salón {} SUSPENDIDO (venció {}, gracia {} días)", tenant.getId(), due, graceDays);
                }
            }
            default -> {
                // PENDING_VERIFICATION, SUSPENDED y CANCELED no cambian solos
            }
        }
    }

    /** La fecha que vence: fin del período pagado o, si no hay, fin de la prueba. */
    private static LocalDate dueDate(Tenant tenant) {
        return tenant.getCurrentPeriodEnd() != null ? tenant.getCurrentPeriodEnd() : tenant.getTrialEndsOn();
    }

    private void notifyOnce(Tenant tenant, String kind, LocalDate due, String subject, String body) {
        String key = kind + ":" + due;
        if (key.equals(tenant.getLastBillingNotice())) {
            return;
        }
        tenant.setLastBillingNotice(key);
        tenantRepository.save(tenant);
        platformMailService.send(tenant.getEmail(), subject, subject, body,
                "Ver mi suscripción", frontendBaseUrl + "/suscripcion?salon=" + tenant.getSlug());
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    public Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Salón no encontrado: " + tenantId));
    }

    private static SubscriptionPaymentDto toDto(SubscriptionPayment p) {
        return new SubscriptionPaymentDto(p.getId(), p.getAmountClp(), p.getPlanTier(), p.getBillingCycle().name(),
                p.getPeriodStart(), p.getPeriodEnd(), p.getProvider().name(), p.getReference(), p.getPaidAt());
    }
}
