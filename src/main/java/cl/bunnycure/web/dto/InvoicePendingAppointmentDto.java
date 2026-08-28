package cl.bunnycure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePendingAppointmentDto {
    private Long appointmentId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private Long customerId;
    private String customerName;
    private String customerRut;
    private String customerEmail;
    private String customerPhone;
    private String servicesSummary;
    private String specialistName;
    private BigDecimal totalAmount;
    private Long invoiceLogId;
    private String invoiceStatus; // "FAILED", "PENDING", "NOT_ATTEMPTED"
    private String errorMessage;
    private LocalDateTime lastAttemptAt;
    private String rutStatus; // "VALID", "INVALID", "MISSING"
    private boolean canEmit;
}
