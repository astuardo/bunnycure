package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.exception.ValidationException;
import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.NotificationService;
import cl.bunnycure.service.SimpleApiService;
import cl.bunnycure.service.WhatsAppHandoffService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;

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
    private final WhatsAppHandoffService whatsAppHandoffService;
    private final SimpleApiService simpleApiService;
    private final NotificationService notificationService;
    private final cl.bunnycure.service.AppointmentReminderService reminderService;
    private final cl.bunnycure.service.UserService userService;

    @Operation(
            summary = "Listar citas",
            description = """
                    Obtiene lista de citas filtradas por rango de fechas, estado o especialista.
                    Si no se especifican fechas, retorna las citas del rango por defecto.
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
            @RequestParam(required = false) AppointmentStatus status,

            @Parameter(description = "ID opcional de especialista")
            @RequestParam(required = false) Long specialistId,
            
            org.springframework.security.core.Authentication authentication) {
        
        Long effectiveSpecialistId = specialistId;
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isOnlySpecialist = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SPECIALIST") || a.getAuthority().equals("ROLE_STAFF"))
                    && authentication.getAuthorities().stream()
                    .noneMatch(a -> a.getAuthority().equals("ROLE_SALON_ADMIN") || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_RECEPTIONIST"));

            if (isOnlySpecialist) {
                try {
                    cl.bunnycure.domain.model.User user = userService.findByUsername(authentication.getName());
                    effectiveSpecialistId = user.getId();
                } catch (Exception e) {
                    log.warn("[API] No se pudo resolver ID para especialista {}", authentication.getName());
                }
            }
        }
        
        List<Appointment> appointments;
        
        if (status != null) {
            appointments = appointmentService.findByStatus(status);
        } else if (startDate != null && endDate != null) {
            appointments = appointmentService.findByDateRange(startDate, endDate);
        } else if (startDate != null) {
            appointments = appointmentService.findByDateRange(startDate, startDate);
        } else {
            // Por defecto, retornar últimos 3 meses + próximos 3 meses para el calendario
            LocalDate defaultStart = LocalDate.now().minusMonths(3);
            LocalDate defaultEnd = LocalDate.now().plusMonths(3);
            log.debug("[API] No date filters provided, using default range: {} to {}", defaultStart, defaultEnd);
            appointments = appointmentService.findByDateRange(defaultStart, defaultEnd);
        }
        
        final Long filterSpecialistId = effectiveSpecialistId;
        List<AppointmentResponseDto> dtos = appointments.stream()
                .filter(a -> filterSpecialistId == null || (a.getSpecialist() != null && filterSpecialistId.equals(a.getSpecialist().getId())))
                .map(this::toResponseDto)
                .collect(Collectors.toList());
        
        log.debug("[API] Returning {} appointments", dtos.size());
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
        
        log.info("[API] Getting appointment by ID: {}", id);
        Appointment appointment = appointmentService.findById(id);
        AppointmentResponseDto dto = toResponseDto(appointment);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Crear nueva cita",
            description = "Crea una nueva cita con estado PENDING. Notifica al cliente y administradores.")
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

        List<Long> selectedServiceIds = resolveServiceIds(request.getServiceId(), request.getServiceIds());

        // Convertir a AppointmentDto que espera el servicio
        AppointmentDto dto = AppointmentDto.builder()
                .customerId(request.getCustomerId())
                .serviceId(selectedServiceIds.get(0))
                .serviceIds(selectedServiceIds)
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .observations(request.getNotes())
                .status(AppointmentStatus.PENDING)
                .build();

        Appointment created = appointmentService.createAppointment(dto);
        
        if (request.getSpecialistId() != null) {
            try {
                cl.bunnycure.domain.model.User specialist = userService.findById(request.getSpecialistId());
                created.setSpecialist(specialist);
                created = appointmentService.saveAppointment(created);
            } catch (Exception e) {
                log.warn("[API] No se pudo asignar especialista {}: {}", request.getSpecialistId(), e.getMessage());
            }
        }
        
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
        List<Long> currentServiceIds = current.getServices() != null && !current.getServices().isEmpty()
                ? current.getServices().stream().map(s -> s.getId()).toList()
                : (current.getService() != null ? List.of(current.getService().getId()) : List.of());

        List<Long> selectedServiceIds = resolveServiceIds(
                request.getServiceId(),
                request.getServiceIds(),
                currentServiceIds
        );
        
        // Construir DTO con datos actualizados (merge con datos actuales)
        AppointmentDto dto = AppointmentDto.builder()
                .customerId(current.getCustomer().getId())
                .serviceId(selectedServiceIds.get(0))
                .serviceIds(selectedServiceIds)
                .appointmentDate(request.getAppointmentDate() != null ? request.getAppointmentDate() : current.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime() != null ? request.getAppointmentTime() : current.getAppointmentTime())
                .observations(request.getNotes() != null ? request.getNotes() : current.getObservations())
                .status(request.getStatus() != null ? request.getStatus() : current.getStatus())
                .build();
        
        Appointment updated = appointmentService.updateAppointment(id, dto);

        if (request.getSpecialistId() != null) {
            try {
                cl.bunnycure.domain.model.User specialist = userService.findById(request.getSpecialistId());
                updated.setSpecialist(specialist);
                updated = appointmentService.saveAppointment(updated);
            } catch (Exception e) {
                log.warn("[API] No se pudo asignar especialista {}: {}", request.getSpecialistId(), e.getMessage());
            }
        }
        
        // Recuperar la cita actualizada
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
            @RequestParam AppointmentStatus status,

            @Parameter(description = "Generar boleta al completar (default: true)")
            @RequestParam(defaultValue = "true") boolean generateInvoice) {
        
        log.info("[API] Updating appointment {} status to {}", id, status);
        
        Appointment updated = appointmentService.updateStatus(id, status, generateInvoice);
        AppointmentResponseDto dto = toResponseDto(updated);
        
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Obtener cuota mensual de boletas",
            description = "Entrega cuántas boletas se han generado este mes y si conviene generar por defecto.")
    @GetMapping("/invoice-quota")
    public ResponseEntity<ApiResponse<InvoiceQuotaResponseDto>> getInvoiceQuota() {
        long generatedThisMonth = simpleApiService.getGeneratedInvoicesThisMonth();
        int monthlyLimit = simpleApiService.getMonthlyInvoiceLimit();
        long remainingThisMonth = Math.max(0, monthlyLimit - generatedThisMonth);

        InvoiceQuotaResponseDto dto = InvoiceQuotaResponseDto.builder()
                .generatedThisMonth(generatedThisMonth)
                .monthlyLimit(monthlyLimit)
                .remainingThisMonth(remainingThisMonth)
                .generateByDefault(generatedThisMonth < monthlyLimit)
                .build();

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

    @Operation(
            summary = "WhatsApp Handoff - Transferir a agente humano",
            description = "Genera URL de WhatsApp pre-llenada para transferir conversación a agente humano.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "URL generada exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada"
            )
    })
    @PostMapping("/{id}/whatsapp-handoff")
    public ResponseEntity<Map<String, String>> whatsappHandoff(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id) {
        
        log.info("[API] WhatsApp handoff requested for appointment {}", id);
        
        Appointment appointment = appointmentService.findById(id);
        String url = whatsAppHandoffService.buildAdminToCustomerLinkFromAppointment(appointment);
        
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        
        log.info("[API] WhatsApp handoff URL generated: {}", url);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Enviar solicitud de valoración en Google por WhatsApp",
            description = "Despacha la plantilla de WhatsApp valoracion_servicio_google de Meta a la clienta asociada a la cita.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Plantilla de valoración despachada exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "La clienta no posee teléfono registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{id}/whatsapp/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendWhatsAppReview(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id) {
        
        log.info("[API] WhatsApp review template dispatch requested for appointment {}", id);
        
        Appointment appointment = appointmentService.findById(id);
        
        if (appointment.getCustomer() == null || appointment.getCustomer().getPhone() == null || appointment.getCustomer().getPhone().isBlank()) {
            throw new ValidationException("La clienta asociada a la cita no posee número de teléfono registrado");
        }
        
        notificationService.sendAppointmentReviewRequest(appointment);
        
        Map<String, Object> result = new HashMap<>();
        result.put("appointmentId", id);
        result.put("customerName", appointment.getCustomer().getFullName());
        result.put("phone", appointment.getCustomer().getPhone());
        result.put("template", "valoracion_servicio_google");
        result.put("status", "DISPATCHED");
        
        log.info("[API] WhatsApp review template dispatched for appointment {} to {}", id, appointment.getCustomer().getPhone());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "Enviar recordatorio de cita por WhatsApp",
            description = "Despacha recordatorio individual de WhatsApp para la cita indicada.")
    @PostMapping("/{id}/whatsapp/reminder")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendWhatsAppReminder(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long id) {
        log.info("[API] Manual reminder dispatch requested for appointment {}", id);
        reminderService.sendManualReminder(id);
        Map<String, Object> result = new HashMap<>();
        result.put("appointmentId", id);
        result.put("status", "DISPATCHED");
        result.put("message", "Recordatorio enviado correctamente");
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "Obtener citas próximas en ventana de tiempo",
            description = """
                    Obtiene citas confirmadas que ocurrirán en las próximas N horas.
                    Útil para notificaciones push automáticas.
                    Solo retorna citas que aún no tienen reminderSent = true.
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lista de citas próximas obtenida exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AppointmentResponseDto.class))
                    )
            )
    })
    @GetMapping("/upcoming-in-window")
    public ResponseEntity<ApiResponse<List<AppointmentResponseDto>>> getUpcomingInWindow(
            @Parameter(description = "Ventana de tiempo en horas (default: 2)")
            @RequestParam(required = false, defaultValue = "2") int hours) {
        
        log.debug("[API] Getting appointments in next {} hours without reminder sent", hours);
        
        // Obtener citas en la ventana de tiempo especificada
        List<Appointment> upcomingAppointments = appointmentService.findUpcomingAppointmentsInWindow(hours);
        
        List<AppointmentResponseDto> dtos = upcomingAppointments.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
        
        log.info("[API] Found {} appointments in next {} hours", dtos.size(), hours);
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    /**
     * Convierte una entidad Appointment a AppointmentResponseDto.
     */
    private AppointmentResponseDto toResponseDto(Appointment appointment) {
        List<ServiceSummaryDto> selectedServices = appointment.getServices() != null && !appointment.getServices().isEmpty()
                ? appointment.getServices().stream().map(service -> ServiceSummaryDto.builder()
                        .id(service.getId())
                        .name(service.getName())
                        .durationMinutes(service.getDurationMinutes())
                        .price(service.getPrice())
                        .active(service.isActive())
                        .build()).toList()
                : List.of(ServiceSummaryDto.builder()
                        .id(appointment.getService().getId())
                        .name(appointment.getService().getName())
                        .durationMinutes(appointment.getService().getDurationMinutes())
                        .price(appointment.getService().getPrice())
                        .active(appointment.getService().isActive())
                        .build());

        BigDecimal totalPrice = selectedServices.stream()
                .map(ServiceSummaryDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalDuration = selectedServices.stream()
                .map(ServiceSummaryDto::getDurationMinutes)
                .reduce(0, Integer::sum);

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
                .services(selectedServices)
                .totalPrice(totalPrice)
                .totalDurationMinutes(totalDuration)
                .reminderSent(appointment.isReminderSent())
                .whatsAppConfirmationSent(false)
                .specialistId(appointment.getSpecialist() != null ? appointment.getSpecialist().getId() : null)
                .specialistName(appointment.getSpecialist() != null ? appointment.getSpecialist().getFullName() : null)
                .build();
    }

    private List<Long> resolveServiceIds(Long serviceId, List<Long> serviceIds) {
        return resolveServiceIds(serviceId, serviceIds, List.of());
    }

    private List<Long> resolveServiceIds(Long serviceId, List<Long> serviceIds, List<Long> fallbackServiceIds) {
        List<Long> normalized = new ArrayList<>();
        if (serviceIds != null) {
            normalized.addAll(serviceIds.stream().filter(id -> id != null && id > 0).toList());
        }
        if (serviceId != null && serviceId > 0 && !normalized.contains(serviceId)) {
            normalized.add(0, serviceId);
        }
        if (normalized.isEmpty() && fallbackServiceIds != null) {
            normalized.addAll(fallbackServiceIds.stream().filter(id -> id != null && id > 0).toList());
        }

        if (normalized.isEmpty()) {
            throw new ValidationException("Debe seleccionar al menos un servicio");
        }

        return normalized.stream().distinct().toList();
    }
}
