package cl.bunnycure.web.dto;

import cl.bunnycure.domain.enums.BookingRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO de respuesta para solicitudes de reserva.
 * Incluye datos completos de la solicitud con información del servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta con datos completos de una solicitud de reserva")
public class BookingRequestResponseDto {
    
    @Schema(description = "ID de la solicitud", example = "1")
    private Long id;
    
    @Schema(description = "Nombre completo del cliente", example = "María González")
    private String fullName;
    
    @Schema(description = "Teléfono del cliente", example = "+56912345678")
    private String phone;
    
    @Schema(description = "Email del cliente (opcional)", example = "maria@example.com")
    private String email;
    
    @Schema(description = "Fecha preferida para la cita", example = "2024-04-15")
    private LocalDate preferredDate;
    
    @Schema(description = "Bloque preferido para la cita", example = "Mañana")
    private String preferredBlock;
    
    @Schema(description = "Comentarios del cliente")
    private String notes;
    
    @Schema(description = "Estado de la solicitud")
    private BookingRequestStatus status;
    
    @Schema(description = "Datos del servicio solicitado")
    private ServiceSummaryDto service;
    
    @Schema(description = "Fecha de creación de la solicitud")
    private LocalDateTime createdAt;
    
    @Schema(description = "ID de la cita creada (si fue aprobada)")
    private Long appointmentId;
}
