package cl.bunnycure.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "incoming_whatsapp_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomingWhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120, unique = true)
    private String wamid;

    @Column(name = "from_phone", nullable = false, length = 30)
    private String fromPhone;

    @Column(name = "sender_name", length = 120)
    private String senderName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "message_type", length = 30, nullable = false)
    @Builder.Default
    private String messageType = "text";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
