package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq_generator")
    @SequenceGenerator(name = "products_seq_generator", sequenceName = "products_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "purchase_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_url", length = 500)
    private String purchaseUrl;

    @Column(name = "observed_price", precision = 12, scale = 2)
    private BigDecimal observedPrice;

    @Column(name = "observed_available")
    private Boolean observedAvailable;

    @Column(name = "last_observed_at")
    private OffsetDateTime lastObservedAt;

    @Column(name = "purchase_unit", nullable = false, length = 60)
    private String purchaseUnit;

    @Column(name = "consumption_unit", nullable = false, length = 60)
    private String consumptionUnit;

    // How many consumption-units are in one purchase unit (e.g., 15.0 ml per bottle)
    @Column(name = "conversion_factor", precision = 14, scale = 4, nullable = false)
    private BigDecimal conversionFactor;

    // Stock stored in consumption unit (allows fractional consumption)
    @Column(name = "stock_consumption_unit", precision = 14, scale = 4, nullable = false)
    private BigDecimal stockConsumptionUnit;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
