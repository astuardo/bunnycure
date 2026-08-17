package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "service_supplies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_service_supply_product", columnNames = {"service_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings("JpaDataSourceORMInspection")
public class ServiceSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_supplies_seq_generator")
    @SequenceGenerator(name = "service_supplies_seq_generator", sequenceName = "service_supplies_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceCatalog service;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_consumption_unit", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantityConsumptionUnit;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
