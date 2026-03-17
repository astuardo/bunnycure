package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customers_seq_generator")
    @SequenceGenerator(name = "customers_seq_generator", sequenceName = "customers_seq", allocationSize = 1)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = true, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "emergency_phone", length = 15)
    private String emergencyPhone;

    @Column(name = "health_notes", length = 500)
    private String healthNotes;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Appointment> appointments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Customer(String fullName, String phone, String email) {
        this.fullName = fullName;
        this.phone    = phone;
        this.email    = email;
    }

    @PrePersist
    public void assignPublicIdIfMissing() {
        if (publicId == null || publicId.isBlank()) {
            publicId = UUID.randomUUID().toString();
        }
    }

    /**
     * Extrae el primer nombre del nombre completo
     * @return el primer nombre o el nombre completo si no hay espacios
     */
    public String getFirstName() {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        int spaceIndex = fullName.indexOf(' ');
        return spaceIndex > 0 ? fullName.substring(0, spaceIndex) : fullName;
    }
}