package cl.bunnycure.domain.model;

import cl.bunnycure.domain.enums.GiftCardPaymentMethod;
import cl.bunnycure.domain.enums.GiftCardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gift_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "pin_hash", nullable = false, length = 255)
    private String pinHash;

    @Column(name = "pin_failed_attempts", nullable = false)
    @Builder.Default
    private Integer pinFailedAttempts = 0;

    @Column(name = "pin_locked_until")
    private LocalDateTime pinLockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GiftCardStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_customer_id", nullable = false)
    private Customer beneficiaryCustomer;

    @Column(name = "beneficiary_name_snapshot", nullable = false, length = 120)
    private String beneficiaryNameSnapshot;

    @Column(name = "beneficiary_phone_snapshot", nullable = false, length = 25)
    private String beneficiaryPhoneSnapshot;

    @Column(name = "beneficiary_email_snapshot", length = 150)
    private String beneficiaryEmailSnapshot;

    @Column(name = "buyer_name", length = 120)
    private String buyerName;

    @Column(name = "buyer_phone", length = 25)
    private String buyerPhone;

    @Column(name = "buyer_email", length = 150)
    private String buyerEmail;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    @Column(name = "paid_amount", nullable = false)
    private Integer paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private GiftCardPaymentMethod paymentMethod;

    @Column(name = "public_url", nullable = false, length = 500)
    private String publicUrl;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "giftCard", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GiftCardItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
