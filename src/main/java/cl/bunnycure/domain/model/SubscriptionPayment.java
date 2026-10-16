package cl.bunnycure.domain.model;

import cl.bunnycure.domain.enums.BillingCycle;
import cl.bunnycure.domain.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pago de la suscripción de un salón a la plataforma (manual o vía pasarela).
 * Cada pago aprobado extiende el período pagado (tenants.current_period_end).
 */
@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Monto en CLP (sin decimales). */
    @Column(name = "amount_clp", nullable = false)
    private Long amountClp;

    @Column(name = "plan_tier", nullable = false, length = 30)
    private String planTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProvider provider;

    /** Id del pago en la pasarela; único por proveedor para que los webhooks sean idempotentes. */
    @Column(name = "external_payment_id", length = 120)
    private String externalPaymentId;

    /** Referencia libre del pago manual (n° de transferencia, comprobante...). */
    @Column(length = 200)
    private String reference;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
