package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de respuesta para operaciones GET de citas.
 * Incluye datos completos de la cita con información del cliente y servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con datos completos de una cita")
public class AppointmentResponseDto {
    
    @Schema(description = "ID de la cita", example = "1")
    private Long id;
    
    @Schema(description = "Fecha de la cita", example = "2024-04-15")
    private LocalDate appointmentDate;
    
    @Schema(description = "Hora de la cita", example = "14:30")
    private LocalTime appointmentTime;
    
    @Schema(description = "Estado de la cita")
    private AppointmentStatus status;
    
    @Schema(description = "Notas adicionales sobre la cita")
    private String notes;
    
    @Schema(description = "Datos del cliente")
    private CustomerSummary customer;
    
    @Schema(description = "Datos del servicio")
    private ServiceSummaryDto service;
    
    @Schema(description = "Si se envió recordatorio")
    private boolean reminderSent;
    
    @Schema(description = "Si se envió confirmación de WhatsApp")
    private boolean whatsAppConfirmationSent;
}
