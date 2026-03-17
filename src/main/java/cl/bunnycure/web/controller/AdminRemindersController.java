package cl.bunnycure.web.controller;

import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.service.AppointmentReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin/reminders")
@RequiredArgsConstructor
public class AdminRemindersController {

    private final AppointmentReminderService reminderService;
    private final AppointmentRepository appointmentRepository;

    /**
     * Página de gestión de recordatorios
     */
    @GetMapping
    public String index(Model model) {
        LocalDate today = LocalDate.now();
        var pendingToday = appointmentRepository.findPendingRemindersFromDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today
        ).stream()
                .sorted(Comparator.comparing(cl.bunnycure.domain.model.Appointment::getAppointmentDate)
                        .thenComparing(cl.bunnycure.domain.model.Appointment::getAppointmentTime))
                .toList();

        model.addAttribute("pendingReminders", pendingToday);
        model.addAttribute("today", today);
        model.addAttribute("totalCount", pendingToday.size());
        model.addAttribute("activeMenu", "reminders");
        
        return "admin/reminders/index";
    }

    /**
     * Envía recordatorios de forma manual para hoy
     */
    @PostMapping("/send-today")
    public String sendTodayReminders(RedirectAttributes ra) {
        try {
            reminderService.sendDailyReminders();
            ra.addFlashAttribute("successMsg", "Recordatorios enviados exitosamente ✅");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Error al enviar recordatorios: " + e.getMessage());
        }
        return "redirect:/admin/reminders";
    }

    /**
     * Envía recordatorio para una cita específica (API)
     */
    @PostMapping("/send/{appointmentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendReminderForAppointment(
            @PathVariable Long appointmentId) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("[ADMIN-REMINDER] Enviando recordatorio para cita ID: {}", appointmentId);
            reminderService.sendManualReminder(appointmentId);
            response.put("success", true);
            response.put("message", "Recordatorio enviado exitosamente ✅");
            log.info("[ADMIN-REMINDER] ✅ Recordatorio enviado exitosamente para cita ID: {}", appointmentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[ADMIN-REMINDER] ❌ Error enviando recordatorio para cita ID: {}", appointmentId, e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(400).body(response);
        }
    }

    /**
     * Obtiene estadísticas de recordatorios
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDate today = LocalDate.now();
        
        var pendingToday = appointmentRepository.findPendingRemindersForDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today
        );

        long sentToday = appointmentRepository.countSentRemindersForDateByStatuses(
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                today
        );

        stats.put("pendingReminders", pendingToday.size());
        stats.put("sentToday", sentToday);
        stats.put("date", today);
        
        return ResponseEntity.ok(stats);
    }
}
