package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "service_material_usages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMaterialUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_material_usages_seq_generator")
    @SequenceGenerator(name = "service_material_usages_seq_generator", sequenceName = "service_material_usages_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity; // in consumption unit

    @Column(name = "used_by_user_id")
    private Long usedByUserId;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @PrePersist
    public void prePersist() {
        this.usedAt = OffsetDateTime.now();
    }
}
