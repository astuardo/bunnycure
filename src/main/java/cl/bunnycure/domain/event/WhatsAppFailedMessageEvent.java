package cl.bunnycure.domain.event;

import cl.bunnycure.domain.enums.OutboxMessageType;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WhatsAppFailedMessageEvent {
    private final Appointment appointment;
    private final Customer customer;
    private final String recipientPhone;
    private final OutboxMessageType messageType;
    private final String templateName;
    private final String languageCode;
    private final String headerParam;
    private final List<String> bodyParams;
    private final String textContent;
    private final String errorMessage;
}
