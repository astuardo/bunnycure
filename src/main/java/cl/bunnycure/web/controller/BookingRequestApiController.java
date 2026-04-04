package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.BookingRequestStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.service.BookingRequestService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller para gestión de solicitudes de reserva.
 * Expone endpoints para crear, aprobar y rechazar solicitudes del portal público.
 */
@Slf4j
@Tag(name = "Booking Requests", description = "API para gestión de solicitudes de reserva")
@RestController
@RequestMapping("/api/booking-requests")
@RequiredArgsConstructor
public class BookingRequestApiController {

    private final BookingRequestService bookingRequestService;

    @Operation(
            summary = "Listar solicitudes de reserva",
            description = """
                    Obtiene lista de solicitudes de reserva.
                    Por defecto muestra solo solicitudes pendientes.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de solicitudes obtenida exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = BookingRequestResponseDto.class))
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingRequestResponseDto>>> list(
            @Parameter(description = "Mostrar solo solicitudes pendientes")
            @RequestParam(defaultValue = "true") boolean pendingOnly) {
        
        List<BookingRequest> requests;
        
        if (pendingOnly) {
            requests = bookingRequestService.findPending();
        } else {
            requests = bookingRequestService.findAll();
        }
        
        List<BookingRequestResponseDto> dtos = requests.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(
            summary = "Obtener solicitud por ID",
            description = "Obtiene los detalles completos de una solicitud de reserva específica.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Solicitud encontrada",
                    content = @Content(schema = @Schema(implementation = BookingRequestResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Solicitud no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingRequestResponseDto>> getById(
            @Parameter(description = "ID de la solicitud", required = true)
            @PathVariable Long id) {
        
        BookingRequest request = bookingRequestService.findById(id);
        BookingRequestResponseDto dto = toResponseDto(request);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Crear nueva solicitud de reserva",
            description = """
                    Crea una nueva solicitud de reserva desde el portal público.
                    El cliente proporciona sus datos y fecha/hora preferida.
                    Se envían notificaciones al cliente y al administrador.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Solicitud creada exitosamente",
                    content = @Content(schema = @Schema(implementation = BookingRequestResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<BookingRequestResponseDto>> create(
            @Valid @RequestBody BookingRequestDto request) {
        
        log.info("[API] Creating booking request for {}", request.getFullName());
        
        BookingRequest created = bookingRequestService.create(request);
        BookingRequestResponseDto dto = toResponseDto(created);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Aprobar solicitud de reserva",
            description = """
                    Aprueba una solicitud de reserva y crea la cita correspondiente.
                    Se pueden ajustar fecha/hora/servicio si es necesario.
                    Envía notificación de confirmación al cliente.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Solicitud aprobada y cita creada",
                    content = @Content(schema = @Schema(implementation = AppointmentResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Solicitud no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Solicitud ya fue procesada o datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> approve(
            @Parameter(description = "ID de la solicitud", required = true)
            @PathVariable Long id,
            
            @Valid @RequestBody BookingApprovalDto approval) {
        
        log.info("[API] Approving booking request {}", id);
        
        Appointment appointment = bookingRequestService.approve(id, approval);
        AppointmentResponseDto dto = toAppointmentResponseDto(appointment);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Rechazar solicitud de reserva",
            description = """
                    Rechaza una solicitud de reserva.
                    Opcionalmente se puede proporcionar una razón del rechazo.
                    Envía notificación al cliente informando el rechazo.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Solicitud rechazada exitosamente",
                    content = @Content(schema = @Schema(implementation = BookingRequestResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Solicitud no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Solicitud ya fue procesada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BookingRequestResponseDto>> reject(
            @Parameter(description = "ID de la solicitud", required = true)
            @PathVariable Long id,
            
            @Parameter(description = "Razón del rechazo (opcional)")
            @RequestParam(required = false) String reason) {
        
        log.info("[API] Rejecting booking request {}", id);
        
        bookingRequestService.reject(id, reason);
        
        BookingRequest updated = bookingRequestService.findById(id);
        BookingRequestResponseDto dto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * Convierte una entidad BookingRequest a BookingRequestResponseDto.
     */
    private BookingRequestResponseDto toResponseDto(BookingRequest request) {
        return BookingRequestResponseDto.builder()
                .id(request.getId())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .preferredDate(request.getPreferredDate())
                .preferredBlock(request.getPreferredBlock())
                .notes(request.getNotes())
                .status(request.getStatus())
                .service(ServiceSummaryDto.builder()
                        .id(request.getService().getId())
                        .name(request.getService().getName())
                        .durationMinutes(request.getService().getDurationMinutes())
                        .price(request.getService().getPrice())
                        .active(request.getService().isActive())
                        .build())
                .createdAt(request.getCreatedAt())
                .appointmentId(request.getAppointment() != null ? request.getAppointment().getId() : null)
                .build();
    }

    /**
     * Convierte una entidad Appointment a AppointmentResponseDto.
     */
    private AppointmentResponseDto toAppointmentResponseDto(Appointment appointment) {
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
