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

    @Column(length = 100)
    private String whatsappPhoneId;

    @Column(length = 500)
    private String whatsappToken;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
