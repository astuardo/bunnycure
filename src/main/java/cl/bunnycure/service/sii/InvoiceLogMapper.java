package cl.bunnycure.service.sii;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.web.dto.InvoiceIssuedItemDto;

/** Conversión de registros de boletas (InvoiceLog) a su DTO de la API. */
public final class InvoiceLogMapper {

    private InvoiceLogMapper() {
    }

    public static InvoiceIssuedItemDto toIssuedItemDto(InvoiceLog logEntry) {
        Appointment a = logEntry.getAppointment();
        Customer c = logEntry.getCustomer();
        return InvoiceIssuedItemDto.builder()
                .id(logEntry.getId())
                .appointmentId(a != null ? a.getId() : null)
                .appointmentDate(a != null ? a.getAppointmentDate() : null)
                .customerId(c != null ? c.getId() : null)
                .customerName(c != null ? c.getFullName() : "Cliente")
                .customerRut(c != null ? c.getRut() : null)
                .customerEmail(c != null ? c.getEmail() : null)
                .invoiceNumber(logEntry.getInvoiceNumber())
                .siiCode(logEntry.getSiiCode())
                .siiBarcode(logEntry.getSiiBarcode())
                .amountInClp(logEntry.getAmountInClp())
                .status(logEntry.getStatus())
                .emailSent(logEntry.getEmailSent())
                .emailRecipient(logEntry.getEmailRecipient())
                .emailSentAt(logEntry.getEmailSentAt())
                .createdAt(logEntry.getCreatedAt())
                .description(logEntry.getDescription())
                .build();
    }
}
