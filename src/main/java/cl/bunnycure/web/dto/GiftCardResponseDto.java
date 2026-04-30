package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.GiftCardAuditActor;
import cl.bunnycure.domain.enums.GiftCardEventType;
import cl.bunnycure.domain.enums.GiftCardPaymentMethod;
import cl.bunnycure.domain.enums.GiftCardStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class GiftCardResponseDto {
    private Long id;
    private String code;
    private GiftCardStatus status;
    private String beneficiaryName;
    private String beneficiaryPhone;
    private String beneficiaryEmail;
    private Long beneficiaryCustomerId;
    private String buyerName;
    private String buyerPhone;
    private String buyerEmail;
    private LocalDate expiresOn;
    private LocalDateTime issuedAt;
    private Integer totalAmount;
    private Integer paidAmount;
    private GiftCardPaymentMethod paymentMethod;
    private String publicUrl;
    private List<ItemDto> items;
    private List<EventDto> events;
    private String plainPin;

    @Getter
    @Setter
    @Builder
    public static class ItemDto {
        private Long id;
        private Long serviceId;
        private String serviceName;
        private Integer unitPrice;
        private Integer quantity;
        private Integer redeemedQuantity;
        private Integer remainingQuantity;
    }

    @Getter
    @Setter
    @Builder
    public static class EventDto {
        private Long id;
        private GiftCardEventType eventType;
        private Long giftCardItemId;
        private Integer quantity;
        private String note;
        private GiftCardAuditActor actor;
        private Long actorUserId;
        private String actorUsername;
        private String requestIp;
        private String userAgent;
        private Long relatedEventId;
        private LocalDateTime createdAt;
    }
}
