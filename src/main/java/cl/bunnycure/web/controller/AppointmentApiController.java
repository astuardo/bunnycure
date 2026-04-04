package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.exception.ValidationException;
import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller para gestión de citas.
 * Expone endpoints CRUD completos con documentación OpenAPI.
 */
@Slf4j
@Tag(name = "Appointments", description = "API para gestión de citas")
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentApiController {

    private final AppointmentService appointmentService;

    @Operation(
            summary = "Listar citas",
            description = """
                    Obtiene lista de citas filtradas por rango de fechas o estado.
                    Si no se especifican fechas, retorna las citas del día actual.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de citas obtenida exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AppointmentResponseDto.class))
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<AppointmentResponseDto>>> list(
            @Parameter(description = "Fecha de inicio (formato: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "Fecha de fin (formato: yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            
            @Parameter(description = "Filtrar por estado")
            @RequestParam(required = false) AppointmentStatus status) {
        
        List<Appointment> appointments;
        
        if (status != null) {
            appointments = appointmentService.findByStatus(status);
        } else if (startDate != null && endDate != null) {
            appointments = appointmentService.findByDateRange(startDate, endDate);
        } else if (startDate != null) {
            appointments = appointmentService.findByDateRange(startDate, startDate);
        } else {
            // Por defecto, citas de hoy
            appointments = appointmentService.findTodayAppointments();
        }
        
        List<AppointmentResponseDto> dtos = appointments.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(
            summary = "Obtener cita por ID",
            description = "Obtiene los detalles completos de una cita específica.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cita encontrada",
                    content = @Content(schema = @Schema(implementation = AppointmentResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> getById(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id) {
        
        Appointment appointment = appointmentService.findById(id);
        AppointmentResponseDto dto = toResponseDto(appointment);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Crear nueva cita",
            description = """
                    Crea una nueva cita y envía notificaciones al cliente y admin.
                    La cita se crea en estado PENDING por defecto.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Cita creada exitosamente",
                    content = @Content(schema = @Schema(implementation = AppointmentResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> create(
            @Valid @RequestBody AppointmentCreateRequestDto request) {
        
        log.info("[API] Creating appointment for customer {} on {} at {}",
                request.getCustomerId(), request.getAppointmentDate(), request.getAppointmentTime());
        
        // Convertir a AppointmentDto que espera el servicio
        AppointmentDto dto = AppointmentDto.builder()
                .customerId(request.getCustomerId())
                .serviceId(request.getServiceId())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .observations(request.getNotes())
                .status(AppointmentStatus.PENDING)
                .build();
        
        appointmentService.createAppointment(dto);
        
        // Buscar la cita recién creada (el servicio no retorna la entidad)
        List<Appointment> appointments = appointmentService.findByDateRange(
                request.getAppointmentDate(), 
                request.getAppointmentDate()
        );
        
        Appointment created = appointments.stream()
                .filter(a -> a.getCustomer().getId().equals(request.getCustomerId()) &&
                           a.getService().getId().equals(request.getServiceId()) &&
                           a.getAppointmentTime().equals(request.getAppointmentTime()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Error al recuperar la cita creada"));
        
        AppointmentResponseDto responseDto = toResponseDto(created);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(responseDto));
    }

    @Operation(
            summary = "Actualizar cita existente",
            description = "Actualiza los datos de una cita existente. Resetea el recordatorio si cambia fecha u hora.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Cita actualizada exitosamente",
                    content = @Content(schema = @Schema(implementation = AppointmentResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> update(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id,
            
            @Valid @RequestBody AppointmentUpdateRequestDto request) {
        
        log.info("[API] Updating appointment {}", id);
        
        // Obtener la cita actual para combinar datos
        Appointment current = appointmentService.findById(id);
        
        // Construir DTO con datos actualizados (merge con datos actuales)
        AppointmentDto dto = AppointmentDto.builder()
                .customerId(request.getCustomerId() != null ? request.getCustomerId() : current.getCustomer().getId())
                .serviceId(request.getServiceId() != null ? request.getServiceId() : current.getService().getId())
                .appointmentDate(request.getAppointmentDate() != null ? request.getAppointmentDate() : current.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime() != null ? request.getAppointmentTime() : current.getAppointmentTime())
                .observations(request.getNotes() != null ? request.getNotes() : current.getObservations())
                .status(request.getStatus() != null ? request.getStatus() : current.getStatus())
                .build();
        
        appointmentService.updateAppointment(id, dto);
        
        // Recuperar la cita actualizada
        Appointment updated = appointmentService.findById(id);
        AppointmentResponseDto responseDto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(responseDto));
    }

    @Operation(
            summary = "Cambiar estado de cita",
            description = """
                    Cambia el estado de una cita (PENDING, CONFIRMED, COMPLETED, CANCELLED).
                    Si se cancela, envía notificación al cliente.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Estado actualizado exitosamente",
                    content = @Content(schema = @Schema(implementation = AppointmentResponseDto.class))
            )
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> updateStatus(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id,
            
            @Parameter(description = "Nuevo estado", required = true)
            @RequestParam AppointmentStatus status) {
        
        log.info("[API] Updating appointment {} status to {}", id, status);
        
        Appointment updated = appointmentService.updateStatus(id, status);
        AppointmentResponseDto dto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Eliminar cita",
            description = "Elimina permanentemente una cita del sistema.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Cita eliminada exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id) {
        
        log.info("[API] Deleting appointment {}", id);
        
        appointmentService.deleteAppointment(id);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * Convierte una entidad Appointment a AppointmentResponseDto.
     */
    private AppointmentResponseDto toResponseDto(Appointment appointment) {
        return AppointmentResponseDto.builder()
                .id(appointment.getId())
                .appointmentDate(appointment.getAppointmentDate())
                .appointmentTime(appointment.getAppointmentTime())
                .status(appointment.getStatus())
                .notes(appointment.getObservations())
                .customer(new CustomerSummary(appointment.getCustomer(), 0))
                .service(ServiceSummaryDto.builder()
                        .id(appointment.getService().getId())
                        .name(appointment.getService().getName())
                        .durationMinutes(appointment.getService().getDurationMinutes())
                        .price(appointment.getService().getPrice())
                        .active(appointment.getService().isActive())
                        .build())
                .reminderSent(appointment.isReminderSent())
                .whatsAppConfirmationSent(false) // Campo no existe en Appointment, por ahora false
                .build();
    }
}
