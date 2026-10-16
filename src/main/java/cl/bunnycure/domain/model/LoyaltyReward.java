package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "loyalty_rewards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyReward {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}
