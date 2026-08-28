package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceIssuedItemDto {
    private Long id;
    private Long appointmentId;
    private LocalDate appointmentDate;
    private Long customerId;
    private String customerName;
    private String customerRut;
    private String customerEmail;
    private String invoiceNumber;
    private String siiCode;
    private String siiBarcode;
    private BigDecimal amountInClp;
    private String status;
    private Boolean emailSent;
    private String emailRecipient;
    private LocalDateTime emailSentAt;
    private LocalDateTime createdAt;
    private String description;
}
