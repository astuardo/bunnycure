package cl.bunnycure.domain.model;

import cl.bunnycure.domain.enums.InvoiceOutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_rut", nullable = false, length = 20)
    private String customerRut;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_email", length = 120)
    private String customerEmail;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvoiceOutboxStatus status = InvoiceOutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "folio", length = 50)
    private String folio;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
