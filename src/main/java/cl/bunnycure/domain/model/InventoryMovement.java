package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inventory_movements_seq_generator")
    @SequenceGenerator(name = "inventory_movements_seq_generator", sequenceName = "inventory_movements_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    // quantity in consumption unit (always positive)
    @Column(name = "quantity_consumption_unit", precision = 14, scale = 4, nullable = false)
    private BigDecimal quantityConsumptionUnit;

    // If movement was a purchase, store quantity in purchase units
    @Column(name = "quantity_purchase_unit", precision = 14, scale = 4)
    private BigDecimal quantityPurchaseUnit;

    @Column(name = "unit_purchase_price", precision = 12, scale = 4)
    private BigDecimal unitPurchasePrice;

    @Column(name = "reference", length = 300)
    private String reference;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
