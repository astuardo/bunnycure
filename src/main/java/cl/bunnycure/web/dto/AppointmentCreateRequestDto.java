package cl.bunnycure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO para crear una nueva cita.
 * Solo incluye campos necesarios para la creación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para crear una nueva cita")
public class AppointmentCreateRequestDto {
    
    @NotNull(message = "El ID del cliente es requerido")
    @Schema(description = "ID del cliente", example = "1", required = true)
    private Long customerId;
    
    @NotNull(message = "El ID del servicio es requerido")
    @Schema(description = "ID del servicio", example = "1", required = true)
    private Long serviceId;
    
    @NotNull(message = "La fecha de la cita es requerida")
    @Future(message = "La fecha de la cita debe ser futura")
    @Schema(description = "Fecha de la cita", example = "2024-04-15", required = true)
    private LocalDate appointmentDate;
    
    @NotNull(message = "La hora de la cita es requerida")
    @Schema(description = "Hora de la cita", example = "14:30", required = true)
    private LocalTime appointmentTime;
    
    @Schema(description = "Notas adicionales sobre la cita", example = "Cliente prefiere sala privada")
    private String notes;
}
