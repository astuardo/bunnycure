package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.model.WhatsAppOutboxMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppOutboxMessageDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Long id;
    private Long appointmentId;
    private Long customerId;
    private String customerName;
    private String recipientPhone;
    private OutboxMessageType messageType;
    private String templateName;
    private String languageCode;
    private String headerParam;
    private String bodyParamsJson;
    private String summaryContent;
    private OutboxStatus status;
    private int attemptCount;
    private String lastError;
    private LocalDateTime lastAttemptAt;
    private String formattedLastAttemptAt;
    private LocalDateTime createdAt;
    private String formattedCreatedAt;
    private LocalDateTime sentAt;

    public static WhatsAppOutboxMessageDto fromEntity(WhatsAppOutboxMessage entity) {
        if (entity == null) {
            return null;
        }

        Long customerId = null;
        String customerName = null;
        if (entity.getCustomer() != null) {
            customerId = entity.getCustomer().getId();
            customerName = entity.getCustomer().getFullName();
        } else if (entity.getAppointment() != null && entity.getAppointment().getCustomer() != null) {
            customerId = entity.getAppointment().getCustomer().getId();
            customerName = entity.getAppointment().getCustomer().getFullName();
        }

        Long appointmentId = entity.getAppointment() != null ? entity.getAppointment().getId() : null;

        String summary = "";
        if (entity.getMessageType() == OutboxMessageType.TEMPLATE) {
            summary = "Plantilla: " + (entity.getTemplateName() != null ? entity.getTemplateName() : "-");
            if (entity.getHeaderParam() != null && !entity.getHeaderParam().isBlank()) {
                summary += " | Para: " + entity.getHeaderParam();
            }
            if (entity.getBodyParamsJson() != null && !entity.getBodyParamsJson().isBlank()) {
                summary += " | Datos: " + entity.getBodyParamsJson();
            }
        } else {
            summary = entity.getTextContent() != null ? entity.getTextContent() : "";
        }

        return WhatsAppOutboxMessageDto.builder()
                .id(entity.getId())
                .appointmentId(appointmentId)
                .customerId(customerId)
                .customerName(customerName)
                .recipientPhone(entity.getRecipientPhone())
                .messageType(entity.getMessageType())
                .templateName(entity.getTemplateName())
                .languageCode(entity.getLanguageCode())
                .headerParam(entity.getHeaderParam())
                .bodyParamsJson(entity.getBodyParamsJson())
                .summaryContent(summary)
                .status(entity.getStatus())
                .attemptCount(entity.getAttemptCount())
                .lastError(entity.getLastError())
                .lastAttemptAt(entity.getLastAttemptAt())
                .formattedLastAttemptAt(entity.getLastAttemptAt() != null ? entity.getLastAttemptAt().format(FORMATTER) : "")
                .createdAt(entity.getCreatedAt())
                .formattedCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(FORMATTER) : "")
                .sentAt(entity.getSentAt())
                .build();
    }
}
