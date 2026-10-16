package cl.bunnycure.service.billing;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.enums.BillingCycle;
import cl.bunnycure.domain.enums.SubscriptionStatus;
import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.repository.SubscriptionPaymentRepository;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.web.dto.billing.BillingDtos.ManualPaymentRequest;
import cl.bunnycure.web.dto.billing.BillingDtos.SubscriptionPaymentDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ciclo de cobro con fechas fijas: vencimiento, gracia, suspensión y reactivación por pago. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Ciclo de suscripción: activa → vencida (gracia) → suspendida → pago")
class SubscriptionLifecycleIntegrationTest {

    private static final LocalDate PERIOD_END = LocalDate.of(2026, 11, 30);

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private SubscriptionPaymentRepository paymentRepository;

    private final List<Long> tenantIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : tenantIds) {
            TenantContext.setCurrentTenantId(id);
            paymentRepository.deleteAll(paymentRepository.findAll());
            TenantContext.clear();
            tenantRepository.deleteById(id);
        }
        tenantIds.clear();
    }

    @Test
    @DisplayName("Vence → PAST_DUE durante 7 días de gracia → SUSPENDED")
    void activeSubscription_GoesPastDueThenSuspended() {
        Long id = createTenant(SubscriptionStatus.ACTIVE, PERIOD_END, null);

        subscriptionService.runDailyLifecycle(PERIOD_END);
        assertThat(status(id)).as("el último día pagado sigue activo").isEqualTo(SubscriptionStatus.ACTIVE);

        subscriptionService.runDailyLifecycle(PERIOD_END.plusDays(1));
        assertThat(status(id)).isEqualTo(SubscriptionStatus.PAST_DUE);

        subscriptionService.runDailyLifecycle(PERIOD_END.plusDays(7));
        assertThat(status(id)).as("último día de gracia").isEqualTo(SubscriptionStatus.PAST_DUE);

        subscriptionService.runDailyLifecycle(PERIOD_END.plusDays(8));
        assertThat(status(id)).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(tenantRepository.findById(id).orElseThrow().getSuspendedAt()).isNotNull();
    }

    @Test
    @DisplayName("Prueba gratuita vencida → PAST_DUE")
    void expiredTrial_GoesPastDue() {
        Long id = createTenant(SubscriptionStatus.TRIALING, null, PERIOD_END);
        subscriptionService.runDailyLifecycle(PERIOD_END.plusDays(1));
        assertThat(status(id)).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    @DisplayName("Sin fecha de vencimiento (salones existentes / cortesía): nunca cambia")
    void subscriptionWithoutDueDate_IsNeverTouched() {
        Long id = createTenant(SubscriptionStatus.ACTIVE, null, null);
        subscriptionService.runDailyLifecycle(LocalDate.of(2030, 1, 1));
        assertThat(status(id)).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Pago manual de un salón suspendido: reactiva y el período empieza hoy")
    void manualPayment_ReactivatesSuspendedTenant() {
        Long id = createTenant(SubscriptionStatus.SUSPENDED, PERIOD_END, null);
        LocalDate today = PERIOD_END.plusDays(20);

        SubscriptionPaymentDto payment = subscriptionService.registerManualPayment(id,
                new ManualPaymentRequest("PRO", BillingCycle.MONTHLY, null, "Transferencia 123"), "owner", today);

        Tenant tenant = tenantRepository.findById(id).orElseThrow();
        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(tenant.getSuspendedAt()).isNull();
        assertThat(payment.periodStart()).isEqualTo(today);
        assertThat(payment.periodEnd()).isEqualTo(today.plusMonths(1).minusDays(1));
        assertThat(payment.amountClp()).as("precio de lista de Studio Pro").isEqualTo(49_900);
        assertThat(subscriptionService.paymentsOf(id)).hasSize(1);
    }

    @Test
    @DisplayName("Pago anticipado: el período nuevo empieza al día siguiente del vigente (no se pierden días)")
    void earlyAnnualPayment_ExtendsFromCurrentPeriodEnd() {
        Long id = createTenant(SubscriptionStatus.ACTIVE, PERIOD_END, null);

        SubscriptionPaymentDto payment = subscriptionService.registerManualPayment(id,
                new ManualPaymentRequest(null, BillingCycle.ANNUAL, 400_000L, null), "owner", PERIOD_END.minusDays(10));

        assertThat(payment.periodStart()).isEqualTo(PERIOD_END.plusDays(1));
        assertThat(payment.periodEnd()).isEqualTo(PERIOD_END.plusYears(1));
        assertThat(payment.amountClp()).isEqualTo(400_000L);
        assertThat(tenantRepository.findById(id).orElseThrow().getBillingCycle()).isEqualTo(BillingCycle.ANNUAL);
    }

    @Test
    @DisplayName("Los pagos de un salón no son visibles para otro")
    void payments_AreScopedByTenant() {
        Long a = createTenant(SubscriptionStatus.ACTIVE, PERIOD_END, null);
        Long b = createTenant(SubscriptionStatus.ACTIVE, PERIOD_END, null);
        subscriptionService.registerManualPayment(a, new ManualPaymentRequest("PRO", BillingCycle.MONTHLY, null, null), "owner", PERIOD_END);

        assertThat(subscriptionService.paymentsOf(a)).hasSize(1);
        assertThat(subscriptionService.paymentsOf(b)).isEmpty();
    }

    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Pago registrado dentro de una transacción del salón 1: queda en el salón correcto")
    void paymentInsideForeignTransaction_IsStoredInItsOwnTenant() {
        Long salon = createTenant(SubscriptionStatus.ACTIVE, PERIOD_END, null);

        TenantContext.setCurrentTenantId(1L);
        try {
            // Sesión de Hibernate abierta para el salón 1 (como una petición de la consola de plataforma)
            transactionTemplate.executeWithoutResult(status ->
                    subscriptionService.registerManualPayment(salon,
                            new ManualPaymentRequest("PRO", BillingCycle.MONTHLY, null, null), "owner", PERIOD_END));
        } finally {
            TenantContext.clear();
        }

        assertThat(subscriptionService.paymentsOf(salon)).hasSize(1);
        assertThat(subscriptionService.paymentsOf(1L)).noneMatch(p -> p.periodEnd().equals(PERIOD_END.plusMonths(1)));
    }

    private Long createTenant(SubscriptionStatus status, LocalDate periodEnd, LocalDate trialEnd) {
        Long id = tenantRepository.save(Tenant.builder()
                .name("Salón ciclo").slug("ciclo-" + System.nanoTime()).active(true).planTier("PRO")
                .subscriptionStatus(status).currentPeriodEnd(periodEnd).trialEndsOn(trialEnd).build()).getId();
        tenantIds.add(id);
        return id;
    }

    private SubscriptionStatus status(Long id) {
        return tenantRepository.findById(id).orElseThrow().getSubscriptionStatus();
    }
}
