package cl.bunnycure.web.dto;

import cl.bunnycure.domain.model.IncomingWhatsAppMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomingWhatsAppMessageDto {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Long id;
    private String wamid;
    private String fromPhone;
    private String senderName;
    private String content;
    private String messageType;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private boolean isRead;
    private LocalDateTime createdAt;
    private String formattedCreatedAt;
    private String replyUrl;

    public static IncomingWhatsAppMessageDto fromEntity(IncomingWhatsAppMessage entity) {
        if (entity == null) {
            return null;
        }

        Long customerId = null;
        String customerName = null;
        String customerPhone = null;

        if (entity.getCustomer() != null) {
            customerId = entity.getCustomer().getId();
            customerName = entity.getCustomer().getFullName();
            customerPhone = entity.getCustomer().getPhone();
        }

        String rawPhone = entity.getFromPhone() != null ? entity.getFromPhone().replaceAll("\\D", "") : "";
        String phoneForWa = rawPhone.startsWith("56") ? rawPhone : (rawPhone.length() == 9 ? "56" + rawPhone : rawPhone);
        String nameForGreeting = customerName != null && !customerName.isBlank() 
                ? customerName 
                : (entity.getSenderName() != null ? entity.getSenderName() : "Hola");

        String prefillText = "Hola " + nameForGreeting + "! Te escribimos de BunnyCure respecto a tu mensaje: \"" 
                + (entity.getContent() != null ? (entity.getContent().length() > 60 ? entity.getContent().substring(0, 57) + "..." : entity.getContent()) : "") 
                + "\". ¿En qué te podemos ayudar?";

        String replyUrl = !phoneForWa.isBlank() 
                ? "https://wa.me/" + phoneForWa + "?text=" + URLEncoder.encode(prefillText, StandardCharsets.UTF_8)
                : "#";

        return IncomingWhatsAppMessageDto.builder()
                .id(entity.getId())
                .wamid(entity.getWamid())
                .fromPhone(entity.getFromPhone())
                .senderName(entity.getSenderName())
                .content(entity.getContent())
                .messageType(entity.getMessageType())
                .customerId(customerId)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .isRead(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .formattedCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(TIME_FORMATTER) : "")
                .replyUrl(replyUrl)
                .build();
    }
}
