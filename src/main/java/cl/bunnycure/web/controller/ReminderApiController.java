package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.service.AppointmentReminderService;
import cl.bunnycure.web.dto.ApiResponse;
import cl.bunnycure.web.dto.ReminderStatsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Recordatorios", description = "API REST para gestión y envío de recordatorios de citas")
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderApiController {

    private final AppointmentReminderService reminderService;
    private final AppointmentRepository appointmentRepository;

    @Operation(summary = "Obtener estadísticas de recordatorios para hoy")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ReminderStatsDto>> getStats() {
        LocalDate today = LocalDate.now();

        var pendingToday = appointmentRepository.findPendingRemindersForDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today
        );

        long sentToday = appointmentRepository.countSentRemindersForDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today
        );

        ReminderStatsDto stats = ReminderStatsDto.builder()
                .pendingReminders(pendingToday.size())
                .sentToday(sentToday)
                .date(today)
                .build();

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @Operation(summary = "Enviar recordatorios de hoy en lote")
    @PostMapping("/send-today")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTodayReminders() {
        log.info("[API-REMINDERS] Iniciando envío de recordatorios para hoy");
        try {
            reminderService.sendDailyReminders();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Recordatorios enviados exitosamente");
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[API-REMINDERS] Error al enviar recordatorios diarios: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error al enviar recordatorios: " + e.getMessage(), "REMINDER_SEND_ERROR"));
        }
    }

    @Operation(summary = "Enviar recordatorio para una cita específica")
    @PostMapping("/send/{appointmentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendReminderForAppointment(
            @Parameter(description = "ID de la cita", required = true)
            @PathVariable Long appointmentId) {
        log.info("[API-REMINDERS] Enviando recordatorio manual para cita ID: {}", appointmentId);
        try {
            reminderService.sendManualReminder(appointmentId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("appointmentId", appointmentId);
            result.put("message", "Recordatorio enviado exitosamente");
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[API-REMINDERS] Error al enviar recordatorio para cita ID {}: {}", appointmentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Error al enviar recordatorio: " + e.getMessage(), "REMINDER_SEND_ERROR"));
        }
    }
}
