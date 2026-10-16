package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(unique = true, length = 120)
    private String customDomain;

    @Column(length = 20)
    private String rut;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 20)
    @Builder.Default
    private String primaryColor = "#d48b70";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String planTier = "STANDARD";

    // ── Suscripción (ver SubscriptionService) ─────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 30)
    @Builder.Default
    private cl.bunnycure.domain.enums.SubscriptionStatus subscriptionStatus = cl.bunnycure.domain.enums.SubscriptionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    @Builder.Default
    private cl.bunnycure.domain.enums.BillingCycle billingCycle = cl.bunnycure.domain.enums.BillingCycle.MONTHLY;

    @Column(name = "trial_ends_on")
    private java.time.LocalDate trialEndsOn;

    /** Último día pagado. null = sin vencimiento (salones existentes / cortesía). */
    @Column(name = "current_period_end")
    private java.time.LocalDate currentPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", nullable = false, length = 30)
    @Builder.Default
    private cl.bunnycure.domain.enums.PaymentProvider paymentProvider = cl.bunnycure.domain.enums.PaymentProvider.MANUAL;

    /** Id de la suscripción en la pasarela (p.ej. preapproval de Mercado Pago). */
    @Column(name = "external_subscription_id", length = 120)
    private String externalSubscriptionId;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    /** Último aviso de cobro enviado (evita correos duplicados del job diario). */
    @Column(name = "last_billing_notice", length = 60)
    private String lastBillingNotice;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "terms_accepted_ip", length = 64)
    private String termsAcceptedIp;

    public cl.bunnycure.domain.enums.SubscriptionPlan getSubscriptionPlan() {
        return cl.bunnycure.domain.enums.SubscriptionPlan.fromString(planTier);
    }

    @Column(length = 100)
    private String whatsappPhoneId;

    @Convert(converter = cl.bunnycure.config.EncryptedStringConverter.class)
    @Column(length = 500)
    private String whatsappToken;

    // Credenciales de Facturación Electrónica SII (Chile)
    @Column(length = 20)
    private String siiRut;

    @Convert(converter = cl.bunnycure.config.EncryptedStringConverter.class)
    @Column(length = 500)
    private String siiPassword;

    @Convert(converter = cl.bunnycure.config.EncryptedStringConverter.class)
    @Column(length = 500)
    private String siiToken;

    @Column(length = 120)
    private String siiActividadEconomica;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
