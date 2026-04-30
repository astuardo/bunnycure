package cl.bunnycure.domain.model;

import cl.bunnycure.domain.enums.GiftCardAuditActor;
import cl.bunnycure.domain.enums.GiftCardEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "gift_card_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiftCardEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_card_id", nullable = false)
    private GiftCard giftCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_card_item_id")
    private GiftCardItem giftCardItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private GiftCardEventType eventType;

    @Column
    private Integer quantity;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GiftCardAuditActor actor;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 80)
    private String actorUsername;

    @Column(name = "request_ip", length = 80)
    private String requestIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "related_event_id")
    private Long relatedEventId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
