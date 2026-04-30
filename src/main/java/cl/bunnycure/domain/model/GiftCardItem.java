package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gift_card_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiftCardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_card_id", nullable = false)
    private GiftCard giftCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_catalog_id", nullable = false)
    private ServiceCatalog service;

    @Column(name = "service_name_snapshot", nullable = false, length = 150)
    private String serviceNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false)
    private Integer unitPriceSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "redeemed_quantity", nullable = false)
    @Builder.Default
    private Integer redeemedQuantity = 0;
}
