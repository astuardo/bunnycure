package cl.bunnycure.domain.model;

import cl.bunnycure.domain.enums.BookingRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "booking_requests_seq_generator")
    @SequenceGenerator(name = "booking_requests_seq_generator", sequenceName = "booking_requests_seq", allocationSize = 50)
    private Long id;

    // ── Datos de la solicitante ──────────────────────────────
    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 150)
    private String email;

    // ── Solicitud ────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceCatalog service;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(name = "preferred_block", length = 50)
    private String preferredBlock;   // "Mañana", "Tarde", "Noche"

    @Column(length = 500)
    private String notes;

    // ── Estado ───────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingRequestStatus status = BookingRequestStatus.PENDING;

    // Cita creada al aprobar
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    // Motivo de rechazo (opcional)
    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime resolvedAt;
}
