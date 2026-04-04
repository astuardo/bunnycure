package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO para actualizar una cita existente.
 * Permite actualización parcial de campos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para actualizar una cita existente")
public class AppointmentUpdateRequestDto {
    
    @Schema(description = "ID del cliente (opcional para actualización)")
    private Long customerId;
    
    @Schema(description = "ID del servicio (opcional para actualización)")
    private Long serviceId;
    
    @Schema(description = "Nueva fecha de la cita")
    private LocalDate appointmentDate;
    
    @Schema(description = "Nueva hora de la cita")
    private LocalTime appointmentTime;
    
    @Schema(description = "Nuevo estado de la cita")
    private AppointmentStatus status;
    
    @Schema(description = "Notas actualizadas")
    private String notes;
}
