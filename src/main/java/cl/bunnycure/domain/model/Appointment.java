package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import cl.bunnycure.domain.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appointments_seq_generator")
    @SequenceGenerator(name = "appointments_seq_generator", sequenceName = "appointments_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // ✅ Reemplaza el enum ServiceType
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_catalog_id", nullable = false)
    private ServiceCatalog service;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "appointment_services",
            joinColumns = @JoinColumn(name = "appointment_id"),
            inverseJoinColumns = @JoinColumn(name = "service_catalog_id")
    )
    @Builder.Default
    private List<ServiceCatalog> services = new ArrayList<>();

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime appointmentTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(length = 500)
    private String observations;

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationSent = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean reminderSent = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Appointment(Customer customer, ServiceCatalog service,
                       LocalDate appointmentDate, LocalTime appointmentTime) {
        this.customer        = customer;
        this.service         = service;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
    }
}
